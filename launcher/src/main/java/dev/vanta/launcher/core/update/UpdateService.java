package dev.vanta.launcher.core.update;

import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.net.JsonHttp;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Update checks for the launcher and the client against the release manifests at
 * {@code <releasesBaseUrl>/launcher-latest.json} and {@code <releasesBaseUrl>/client-latest.json}.
 *
 * <ul>
 *   <li>{@link #checkLauncher()} / {@link #checkClient(SemVer)} compare SemVer and return an {@link UpdateInfo}
 *       only when the manifest is newer.</li>
 *   <li>{@link #downloadUpdate} fetches the file to {@code cache/updates/} with SHA-256 verification.</li>
 *   <li>{@link #prepareInstaller} re-verifies the downloaded installer and returns its path; the UI asks the user
 *       before opening it. The launcher never runs the installer itself.</li>
 *   <li>Client updates go through {@link VantaClientService}, which keeps the last versions for rollback.</li>
 * </ul>
 */
public final class UpdateService {

    /** Launcher manifest file name below the releases base URL. */
    public static final String LAUNCHER_MANIFEST = "launcher-latest.json";

    private final Downloader downloader;
    private final JsonHttp json;
    private final LauncherPaths paths;
    private final Supplier<String> releasesBaseUrl;
    private final SemVer currentLauncher;
    private final OsInfo os;
    private final VantaClientService clientService;

    /**
     * @param downloader      downloader
     * @param paths           paths
     * @param releasesBaseUrl supplier of the configured base URL (empty = not configured)
     * @param currentLauncher installed launcher version
     * @param os              host platform (selects installer type)
     * @param clientService   client service for client updates/rollback
     */
    public UpdateService(final Downloader downloader, final LauncherPaths paths, final Supplier<String> releasesBaseUrl,
                         final SemVer currentLauncher, final OsInfo os, final VantaClientService clientService) {
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.json = new JsonHttp(downloader);
        this.paths = Objects.requireNonNull(paths, "paths");
        this.releasesBaseUrl = Objects.requireNonNull(releasesBaseUrl, "releasesBaseUrl");
        this.currentLauncher = Objects.requireNonNull(currentLauncher, "currentLauncher");
        this.os = Objects.requireNonNull(os, "os");
        this.clientService = Objects.requireNonNull(clientService, "clientService");
    }

    /** @return whether a releases base URL is configured */
    public boolean isConfigured() {
        final String base = releasesBaseUrl.get();
        return base != null && !base.isBlank();
    }

    /**
     * Fetches a manifest by file name.
     *
     * @param fileName manifest file name
     * @return manifest
     * @throws NotPublishedException when no base URL is configured
     * @throws IOException           on failure
     * @throws InterruptedException  when interrupted
     */
    public ReleaseManifest fetchManifest(final String fileName) throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new NotPublishedException("", "", "No releases URL is configured, so updates cannot be checked. "
                + "Set \"releasesBaseUrl\" in the launcher settings.");
        }
        return json.get(VantaClientService.manifestUrl(releasesBaseUrl.get(), fileName), ReleaseManifest.class);
    }

    /**
     * Checks for a launcher update.
     *
     * @return update when the manifest is newer than the running launcher
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public Optional<UpdateInfo> checkLauncher() throws IOException, InterruptedException {
        final ReleaseManifest manifest = fetchManifest(LAUNCHER_MANIFEST);
        return compare(ReleaseManifest.PRODUCT_LAUNCHER, currentLauncher, manifest);
    }

    /**
     * Checks for a client update.
     *
     * @param currentClient installed client version (empty when none installed)
     * @return update when the manifest is newer
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public Optional<UpdateInfo> checkClient(final Optional<SemVer> currentClient) throws IOException, InterruptedException {
        final ReleaseManifest manifest = fetchManifest(VantaClientService.CLIENT_MANIFEST);
        return compare(ReleaseManifest.PRODUCT_CLIENT, currentClient.orElse(SemVer.of(0, 0, 0)), manifest);
    }

    /**
     * Compares a manifest with the installed version.
     *
     * @param product  expected product
     * @param current  installed version
     * @param manifest manifest
     * @return update when newer (also when not yet downloadable, so the UI can say "coming soon")
     * @throws IOException when the manifest is for a different product or has an invalid version
     */
    public Optional<UpdateInfo> compare(final String product, final SemVer current, final ReleaseManifest manifest) throws IOException {
        if (!product.equals(manifest.product())) {
            throw new IOException("Manifest describes product '" + manifest.product() + "', expected '" + product + "'");
        }
        final SemVer latest = SemVer.tryParse(manifest.version())
            .orElseThrow(() -> new IOException("Manifest has an invalid version '" + manifest.version() + "'"));
        if (!latest.isNewerThan(current)) {
            return Optional.empty();
        }
        final ReleaseManifest.ReleaseFile file = manifest.preferredFile(os.isWindows())
            .orElse(new ReleaseManifest.ReleaseFile(product + "-" + manifest.version(), "", 0, ""));
        return Optional.of(new UpdateInfo(product, current, latest, manifest.changelog(), file, file.sha256(), manifest));
    }

    /**
     * Downloads an update file to {@code cache/updates/} and verifies its SHA-256.
     *
     * @param update   update
     * @param listener progress
     * @param token    cancellation
     * @return verified file
     * @throws NotPublishedException when the file has no download URL
     * @throws IOException           on failure or when the manifest lacks a SHA-256
     * @throws InterruptedException  when interrupted
     */
    public Path downloadUpdate(final UpdateInfo update, final DownloadProgressListener listener, final CancellationToken token)
        throws IOException, InterruptedException {
        final ReleaseManifest.ReleaseFile file = update.file();
        if (!file.isPublished()) {
            throw new NotPublishedException(update.product(), update.latestVersion().toString(),
                capitalize(update.product()) + " " + update.latestVersion() + " is announced but not downloadable yet");
        }
        if (!file.hasSha256()) {
            throw new IntegrityException(null, "The release manifest has no SHA-256 for " + file.name() + "; refusing to download");
        }
        Files.createDirectories(paths.updatesCacheDir());
        final Path target = paths.updatesCacheDir().resolve(update.latestVersion() + "-" + file.name());
        downloader.download(new DownloadRequest(URI.create(file.downloadUrl()), target, file.size() > 0 ? file.size() : -1L,
            Checksum.sha256(file.sha256()), file.name()), listener, token);
        return target;
    }

    /**
     * Re-verifies a downloaded launcher installer and returns its path. The caller shows a confirmation and only
     * then opens the file with the operating system; this method never executes anything.
     *
     * @param update update that was downloaded
     * @return verified installer path
     * @throws IOException when the file is missing or fails verification (it is deleted in that case)
     */
    public Path prepareInstaller(final UpdateInfo update) throws IOException {
        final Path target = paths.updatesCacheDir().resolve(update.latestVersion() + "-" + update.file().name());
        if (!Files.isRegularFile(target)) {
            throw new IOException("The update has not been downloaded yet");
        }
        if (!update.file().hasSha256() || !Checksum.sha256(update.file().sha256()).matches(target)) {
            Files.deleteIfExists(target);
            throw new IntegrityException(target, "The downloaded installer failed verification and was deleted");
        }
        return target;
    }

    /**
     * Installs a client update (download, verify, keep for rollback, activate in {@code mods/}).
     *
     * @param update   client update
     * @param listener progress
     * @param token    cancellation
     * @return active jar
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public Path installClientUpdate(final UpdateInfo update, final DownloadProgressListener listener, final CancellationToken token)
        throws IOException, InterruptedException {
        if (!ReleaseManifest.PRODUCT_CLIENT.equals(update.product())) {
            throw new IOException("Not a client update");
        }
        return clientService.installFromManifest(update.manifest(), listener, token);
    }

    /**
     * Activates a previously installed client version.
     *
     * @param version version to activate
     * @return active jar
     * @throws IOException when the version is not kept locally
     */
    public Path rollbackClient(final String version) throws IOException {
        return clientService.rollback(version);
    }

    /**
     * @return client versions kept for rollback (newest first)
     * @throws IOException on failure
     */
    public List<VantaClientService.KeptVersion> listClientVersions() throws IOException {
        return clientService.listKeptVersions();
    }

    /** @return the running launcher version */
    public SemVer currentLauncher() {
        return currentLauncher;
    }

    private static String capitalize(final String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
