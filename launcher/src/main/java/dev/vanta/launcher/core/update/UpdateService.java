package dev.vanta.launcher.core.update;

import dev.vanta.launcher.core.install.InstalledClient;
import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.install.ReleasesNotConfiguredException;
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
import dev.vanta.launcher.core.settings.ReleasesBaseUrl;
import dev.vanta.launcher.core.util.LauncherPackaging;
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
 *   <li>{@link #checkLauncher()} compares SemVer and returns an {@link UpdateInfo} only when the manifest is
 *       newer. {@link #checkClient(Optional)} does the same for the installed client ({@link InstalledClient}); when no
 *       client is installed it reports the latest release but never an update: the regular install (PLAY,
 *       {@code --install}) or "Use with Minecraft Launcher" installs it.</li>
 *   <li>The launcher asset is chosen per platform and packaging ({@link ReleaseManifest#launcherAssetFor}): the
 *       Windows {@code .msi} for an installed launcher, the {@code -windows-portable.zip} for the portable folder, the
 *       Linux x64 {@code .tar.gz} for the app image, the platform fat jar for {@code java -jar}, the Apple Silicon
 *       macOS jar. Other platforms get an update without a file and the release page instead; the client update is
 *       exactly {@code vanta-client-<version>.jar}.</li>
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
    private final LauncherPackaging packaging;
    private final VantaClientService clientService;

    /**
     * @param downloader      downloader
     * @param paths           paths
     * @param releasesBaseUrl supplier of the base URL in effect (an empty or non-http(s) value means "not configured")
     * @param currentLauncher installed launcher version
     * @param os              host platform (selects installer type)
     * @param packaging       how the running launcher was installed (selects installer, portable zip or jar)
     * @param clientService   client service for client updates/rollback
     */
    public UpdateService(final Downloader downloader, final LauncherPaths paths, final Supplier<String> releasesBaseUrl,
                         final SemVer currentLauncher, final OsInfo os, final LauncherPackaging packaging,
                         final VantaClientService clientService) {
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.json = new JsonHttp(downloader);
        this.paths = Objects.requireNonNull(paths, "paths");
        this.releasesBaseUrl = Objects.requireNonNull(releasesBaseUrl, "releasesBaseUrl");
        this.currentLauncher = Objects.requireNonNull(currentLauncher, "currentLauncher");
        this.os = Objects.requireNonNull(os, "os");
        this.packaging = Objects.requireNonNull(packaging, "packaging");
        this.clientService = Objects.requireNonNull(clientService, "clientService");
    }

    /** @return whether the releases base URL in effect is a usable http(s) URL */
    public boolean isConfigured() {
        return ReleasesBaseUrl.isHttpUrl(releasesBaseUrl.get());
    }

    /** @return the releases base URL in effect (may be unusable, see {@link #isConfigured()}) */
    public String baseUrl() {
        final String base = releasesBaseUrl.get();
        return base == null ? "" : base.trim();
    }

    /**
     * Fetches a manifest by file name.
     *
     * @param fileName manifest file name
     * @return manifest
     * @throws ReleasesNotConfiguredException when the base URL is unusable
     * @throws NotPublishedException          when there is no manifest at the URL (HTTP 404)
     * @throws IOException                    on failure
     * @throws InterruptedException           when interrupted
     */
    public ReleaseManifest fetchManifest(final String fileName) throws IOException, InterruptedException {
        final String base = VantaClientService.requireUsableBaseUrl(releasesBaseUrl.get());
        final String product = LAUNCHER_MANIFEST.equals(fileName) ? ReleaseManifest.PRODUCT_LAUNCHER : ReleaseManifest.PRODUCT_CLIENT;
        return VantaClientService.fetchManifest(json, VantaClientService.manifestUrl(base, fileName), product);
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
     * Checks the client release against the installed client ({@link VantaClientService#installedClient()}).
     *
     * @return latest release and, only when a client is installed and the release is newer, the update
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public ClientCheck checkClient() throws IOException, InterruptedException {
        return checkClient(clientService.installedClient());
    }

    /**
     * Checks the client release against a given installed state. Without an installed client there is nothing to
     * update: the result names the latest release and carries no {@link UpdateInfo}.
     *
     * @param installed installed client (empty when none is installed)
     * @return latest release and the update, if any
     * @throws IOException          on failure (including {@link NotPublishedException} for a missing manifest)
     * @throws InterruptedException when interrupted
     */
    public ClientCheck checkClient(final Optional<InstalledClient> installed) throws IOException, InterruptedException {
        final ReleaseManifest manifest = fetchManifest(VantaClientService.CLIENT_MANIFEST);
        final SemVer latest = validate(ReleaseManifest.PRODUCT_CLIENT, manifest);
        final boolean downloadable = manifest.clientJar().map(ReleaseManifest.ReleaseFile::isPublished).orElse(false);
        if (installed.isEmpty()) {
            return new ClientCheck(installed, latest, downloadable, Optional.empty());
        }
        return new ClientCheck(installed, latest, downloadable,
            compare(ReleaseManifest.PRODUCT_CLIENT, installed.get().comparisonVersion(), manifest));
    }

    /**
     * Result of a client check.
     *
     * @param installed    the installed client the check compared with (empty: none installed)
     * @param latest       version of the latest client release
     * @param downloadable whether the latest release has a download for {@code vanta-client-<version>.jar}
     * @param update       the update; always empty when no client is installed
     */
    public record ClientCheck(Optional<InstalledClient> installed, SemVer latest, boolean downloadable, Optional<UpdateInfo> update) {

        public ClientCheck {
            Objects.requireNonNull(installed, "installed");
            Objects.requireNonNull(latest, "latest");
            Objects.requireNonNull(update, "update");
            if (installed.isEmpty() && update.isPresent()) {
                throw new IllegalArgumentException("no client is installed: there is nothing to update");
            }
        }

        /** @return whether a VANTA client is installed */
        public boolean isInstalled() {
            return installed.isPresent();
        }
    }

    /**
     * Compares a manifest with the installed version.
     *
     * @param product  expected product
     * @param current  installed version
     * @param manifest manifest
     * @return update when newer and on the stable channel (also when not yet downloadable, so the UI can say
     *         "coming soon")
     * @throws IOException when the manifest is for a different product or has an invalid version
     */
    public Optional<UpdateInfo> compare(final String product, final SemVer current, final ReleaseManifest manifest) throws IOException {
        final SemVer latest = validate(product, manifest);
        if (!latest.isNewerThan(current)) {
            return Optional.empty();
        }
        // latest/ only ever follows stable releases; a pre-release manifest copied there by hand is not offered.
        if (!"stable".equals(manifest.channel())) {
            return Optional.empty();
        }
        final ReleaseManifest.ReleaseFile file;
        if (ReleaseManifest.PRODUCT_CLIENT.equals(product)) {
            file = manifest.clientJar().orElse(new ReleaseManifest.ReleaseFile(manifest.clientJarName(), "", 0, ""));
        } else {
            // No asset for this platform: an empty name; the UI points at the release page instead.
            file = manifest.launcherAssetFor(os, packaging).orElse(new ReleaseManifest.ReleaseFile("", "", 0, ""));
        }
        return Optional.of(new UpdateInfo(product, current, latest, manifest.changelog(), file, file.sha256(), manifest,
            manifest.releasePageUrl().orElse("")));
    }

    private static SemVer validate(final String product, final ReleaseManifest manifest) throws IOException {
        if (!product.equals(manifest.product())) {
            throw new IOException("Manifest describes product '" + manifest.product() + "', expected '" + product + "'");
        }
        return SemVer.tryParse(manifest.version())
            .orElseThrow(() -> new IOException("Manifest has an invalid version '" + manifest.version() + "'"));
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
        if (!update.hasPlatformAsset()) {
            throw new NotPublishedException(update.product(), update.latestVersion().toString(),
                capitalize(update.product()) + " " + update.latestVersion() + " has no download for " + describe(os)
                    + (update.releasePage().isEmpty() ? "" : "; get it from " + update.releasePage()));
        }
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
     * Installs a client update (download, verify, keep for rollback, activate in {@code mods/}). Only an installed
     * client is updated ({@link VantaClientService#installedClient()}).
     *
     * @param update   client update
     * @param listener progress
     * @param token    cancellation
     * @return active jar
     * @throws IOException          on failure, or when no client is installed
     * @throws InterruptedException when interrupted
     */
    public Path installClientUpdate(final UpdateInfo update, final DownloadProgressListener listener, final CancellationToken token)
        throws IOException, InterruptedException {
        if (!ReleaseManifest.PRODUCT_CLIENT.equals(update.product())) {
            throw new IOException("Not a client update");
        }
        if (clientService.installedClient().isEmpty()) {
            // An update replaces an installed client; a first install goes through the regular install, which also
            // writes instance.json (or through "Use with Minecraft Launcher").
            throw new IOException("No VANTA Client is installed, so there is nothing to update. Install it with PLAY (--install) "
                + "or \"Use with Minecraft Launcher\" (--install-official-profile).");
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

    /** @return how the running launcher was installed */
    public LauncherPackaging packaging() {
        return packaging;
    }

    /**
     * @param os platform
     * @return e.g. {@code macOS (x64)}
     */
    public static String describe(final OsInfo os) {
        final String name = os.isWindows() ? "Windows" : os.isMac() ? "macOS" : os.isLinux() ? "Linux" : os.name();
        return name + " (" + os.arch() + ")";
    }

    private static String capitalize(final String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
