package dev.vanta.launcher.core.install;

import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.net.JsonHttp;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.ReleasesBaseUrl;
import dev.vanta.launcher.core.util.AtomicFiles;
import dev.vanta.launcher.core.util.Json;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Installs the VANTA client mod jar into {@code mods/}.
 *
 * <ul>
 *   <li>Published releases come from the release manifest ({@code <releasesBaseUrl>/client-latest.json}, see
 *       {@link dev.vanta.launcher.core.settings.ReleasesBaseUrl} for how the base URL is chosen). Exactly
 *       {@code vanta-client-<version>.jar} is installed (never the {@code -mods.zip} bundle, the Fabric API jar or a
 *       sources jar listed in the same manifest); it is verified with its SHA-256 and a copy is kept under
 *       {@code versions/vanta-client/<version>/} for rollback (the {@value #KEEP_VERSIONS} highest versions are
 *       retained).</li>
 *   <li>A manifest with an empty {@code downloadUrl}, or no manifest at the URL at all (HTTP 404), raises
 *       {@link NotPublishedException}.</li>
 *   <li>An unusable base URL raises {@link ReleasesNotConfiguredException}.</li>
 *   <li>For development a local jar can be installed instead ({@code --client-jar <path>}).</li>
 * </ul>
 */
public final class VantaClientService {

    /** File name of the client manifest below the releases base URL. */
    public static final String CLIENT_MANIFEST = "client-latest.json";
    /** Number of client versions kept for rollback. */
    public static final int KEEP_VERSIONS = 3;
    /** Prefix of the VANTA client jar file name. */
    public static final String JAR_PREFIX = "vanta-client-";
    /** Version recorded for a local development jar. */
    public static final String DEV_VERSION = "dev";
    /** File name of the release manifest kept next to a rollback copy. */
    public static final String KEPT_MANIFEST = "manifest.json";

    private final Downloader downloader;
    private final JsonHttp json;
    private final LauncherPaths paths;
    private final Supplier<String> releasesBaseUrl;

    /**
     * @param downloader      downloader
     * @param paths           paths
     * @param releasesBaseUrl supplier of the releases base URL in effect (an empty or non-http(s) value is reported
     *                        as {@link ReleasesNotConfiguredException})
     */
    public VantaClientService(final Downloader downloader, final LauncherPaths paths, final Supplier<String> releasesBaseUrl) {
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.json = new JsonHttp(downloader);
        this.paths = Objects.requireNonNull(paths, "paths");
        this.releasesBaseUrl = Objects.requireNonNull(releasesBaseUrl, "releasesBaseUrl");
    }

    /**
     * Builds the manifest URL for a product.
     *
     * @param baseUrl  releases base URL
     * @param fileName manifest file name
     * @return URL
     */
    public static URI manifestUrl(final String baseUrl, final String fileName) {
        final String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return URI.create(base + fileName);
    }

    /**
     * Checks that a releases base URL can be used.
     *
     * @param base base URL in effect
     * @return the trimmed URL
     * @throws ReleasesNotConfiguredException when it is empty or not an absolute http(s) URL
     */
    public static String requireUsableBaseUrl(final String base) throws ReleasesNotConfiguredException {
        if (base == null || base.isBlank()) {
            throw new ReleasesNotConfiguredException("", "No releases URL is configured. Set \"releasesBaseUrl\" in the launcher "
                + "settings (or " + LauncherSettings.RELEASES_BASE_URL_ENV + ") to the location of client-latest.json, or install a "
                + "local build with --client-jar <path>.");
        }
        if (!ReleasesBaseUrl.isHttpUrl(base)) {
            throw new ReleasesNotConfiguredException(base.trim(), "The releases URL '" + base.trim() + "' is not a valid http(s) URL. "
                + "Correct \"releasesBaseUrl\" in the launcher settings (Settings > Releases base URL > Reset to default) or "
                + LauncherSettings.RELEASES_BASE_URL_ENV + ", or install a local build with --client-jar <path>.");
        }
        return base.trim();
    }

    /**
     * @return the URL of {@code client-latest.json} in effect
     * @throws ReleasesNotConfiguredException when the base URL is unusable
     */
    public URI clientManifestUrl() throws ReleasesNotConfiguredException {
        return manifestUrl(requireUsableBaseUrl(releasesBaseUrl.get()), CLIENT_MANIFEST);
    }

    /**
     * Fetches the client release manifest.
     *
     * @return manifest
     * @throws ReleasesNotConfiguredException when the releases base URL is unusable
     * @throws NotPublishedException          when there is no manifest at the URL (HTTP 404)
     * @throws IOException                    on failure
     * @throws InterruptedException           when interrupted
     */
    public ReleaseManifest fetchClientManifest() throws IOException, InterruptedException {
        final URI url = clientManifestUrl();
        final ReleaseManifest manifest = fetchManifest(json, url, ReleaseManifest.PRODUCT_CLIENT);
        if (!ReleaseManifest.PRODUCT_CLIENT.equals(manifest.product())) {
            throw new IOException("Manifest at " + url + " describes product '" + manifest.product() + "', expected 'client'");
        }
        return manifest;
    }

    /**
     * GETs a release manifest; HTTP 404 means nothing has been published at that location yet.
     *
     * @param json    JSON helper
     * @param url     manifest URL
     * @param product product (for the error)
     * @return manifest
     * @throws NotPublishedException on HTTP 404
     * @throws IOException          on other failures
     * @throws InterruptedException when interrupted
     */
    public static ReleaseManifest fetchManifest(final JsonHttp json, final URI url, final String product) throws IOException, InterruptedException {
        try {
            return json.get(url, ReleaseManifest.class);
        } catch (HttpStatusException e) {
            if (e.status() == 404) {
                throw new NotPublishedException(product, "", "No release manifest at " + url + " (HTTP 404): nothing has been "
                    + "published there yet, or the releases URL points to the wrong place.");
            }
            throw e;
        }
    }

    /**
     * Downloads the client jar described by a manifest, verifies it, keeps a rollback copy and activates it in
     * {@code mods/}.
     *
     * @param manifest client manifest
     * @param listener progress listener
     * @param token    cancellation token
     * @return the active jar in {@code mods/}
     * @throws NotPublishedException when the manifest has no download URL
     * @throws IOException           on failure or when the manifest lacks a SHA-256
     * @throws InterruptedException  when interrupted
     */
    public Path installFromManifest(final ReleaseManifest manifest, final DownloadProgressListener listener,
                                    final CancellationToken token) throws IOException, InterruptedException {
        final ReleaseManifest.ReleaseFile file = installableJar(manifest);
        final Path versionDir = paths.clientVersionsDir().resolve(manifest.version());
        final Path kept = versionDir.resolve(file.name());
        final DownloadRequest request = new DownloadRequest(URI.create(file.downloadUrl()), kept,
            file.size() > 0 ? file.size() : -1L, Checksum.sha256(file.sha256()), file.name());
        downloader.download(request, listener, token);
        Json.write(versionDir.resolve(KEPT_MANIFEST), manifest);
        final Path active = activate(kept, file.name(), manifest.version());
        pruneKeptVersions();
        return active;
    }

    /**
     * Installs a local development jar.
     *
     * @param jar local jar
     * @return the active jar in {@code mods/}
     * @throws IOException when the file is missing or not a jar
     */
    public Path installLocalJar(final Path jar) throws IOException {
        checkLocalJar(jar);
        return activate(jar, localTargetName(jar), DEV_VERSION);
    }

    /**
     * The client jar entry a release manifest installs, after the checks {@link #installFromManifest} makes before
     * downloading anything.
     *
     * @param manifest client manifest
     * @return the {@code vanta-client-<version>.jar} entry
     * @throws NotPublishedException when the manifest lists no client jar or the jar has no download URL
     * @throws IntegrityException    when the jar has no SHA-256
     */
    public static ReleaseManifest.ReleaseFile installableJar(final ReleaseManifest manifest) throws IOException {
        final ReleaseManifest.ReleaseFile file = manifest.clientJar()
            .orElseThrow(() -> new NotPublishedException(ReleaseManifest.PRODUCT_CLIENT, manifest.version(),
                "The client release manifest for " + manifest.version() + " lists no " + manifest.clientJarName()));
        if (!file.isPublished()) {
            throw new NotPublishedException(ReleaseManifest.PRODUCT_CLIENT, manifest.version(),
                "VANTA Client " + manifest.version() + " has no public download yet");
        }
        if (!file.hasSha256()) {
            throw new IntegrityException(null, "The release manifest for VANTA Client " + manifest.version()
                + " has no SHA-256; refusing to install an unverifiable file");
        }
        return file;
    }

    /**
     * @param jar local client jar
     * @return the name it gets in {@code mods/}: its own name when it starts with {@value #JAR_PREFIX}, else
     *         {@code vanta-client-dev.jar}
     */
    public static String localTargetName(final Path jar) {
        final String name = jar.getFileName().toString();
        return name.startsWith(JAR_PREFIX) ? name : JAR_PREFIX + DEV_VERSION + ".jar";
    }

    /**
     * What installing a published release changes in the VANTA data directory, computed without changing anything
     * (the same checks as {@link #installFromManifest} are made first).
     *
     * @param manifest client manifest
     * @return changes
     * @throws NotPublishedException when the release has no downloadable client jar
     * @throws IOException           when the jar has no SHA-256 or a directory cannot be read
     */
    public ClientChanges changesForRelease(final ReleaseManifest manifest) throws IOException {
        final ReleaseManifest.ReleaseFile file = installableJar(manifest);
        final Path versionDir = paths.clientVersionsDir().resolve(manifest.version());
        return changes(file.name(), List.of(versionDir.resolve(file.name()), versionDir.resolve(KEPT_MANIFEST)),
            prunedAfterInstalling(manifest.version()));
    }

    /**
     * What installing a local jar changes in the VANTA data directory, computed without changing anything.
     *
     * @param jar local client jar
     * @return changes
     * @throws IOException when the file is missing or not a jar
     */
    public ClientChanges changesForLocalJar(final Path jar) throws IOException {
        checkLocalJar(jar);
        return changes(localTargetName(jar), List.of(), List.of());
    }

    private ClientChanges changes(final String targetName, final List<Path> keptFiles, final List<Path> pruned) throws IOException {
        final List<Path> replaced = new ArrayList<>();
        if (Files.isDirectory(paths.modsDir())) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.modsDir(), JAR_PREFIX + "*.jar")) {
                for (Path p : stream) {
                    if (!p.getFileName().toString().equals(targetName)) {
                        replaced.add(p);
                    }
                }
            }
        }
        replaced.sort(null);
        final Optional<Path> instance = Files.isRegularFile(paths.instanceFile()) ? Optional.of(paths.instanceFile()) : Optional.empty();
        return new ClientChanges(paths.modsDir().resolve(targetName), keptFiles, instance, replaced, pruned);
    }

    /** Kept version directories {@link #pruneKeptVersions} deletes once {@code version} is installed. */
    private List<Path> prunedAfterInstalling(final String version) throws IOException {
        final List<KeptVersion> kept = new ArrayList<>(listKeptVersions());
        kept.removeIf(k -> k.version().equals(version));
        kept.add(new KeptVersion(version, null, null, Instant.MAX));
        kept.sort(KeptVersion.NEWEST_FIRST);
        final List<Path> out = new ArrayList<>();
        for (int i = KEEP_VERSIONS; i < kept.size(); i++) {
            out.add(paths.clientVersionsDir().resolve(kept.get(i).version()));
        }
        return out;
    }

    private static void checkLocalJar(final Path jar) throws IOException {
        if (!Files.isRegularFile(jar)) {
            throw new IOException("Client jar not found: " + jar);
        }
        if (!jar.getFileName().toString().endsWith(".jar")) {
            throw new IOException("Not a jar file: " + jar);
        }
    }

    /**
     * Activates a kept version (rollback).
     *
     * @param version client version
     * @return the active jar
     * @throws IOException when the version is not kept locally or fails verification
     */
    public Path rollback(final String version) throws IOException {
        final Path versionDir = paths.clientVersionsDir().resolve(version);
        final Path manifestFile = versionDir.resolve(KEPT_MANIFEST);
        if (!Files.isRegularFile(manifestFile)) {
            throw new IOException("VANTA Client " + version + " is not available locally for rollback");
        }
        final ReleaseManifest manifest = Json.read(manifestFile, ReleaseManifest.class);
        final ReleaseManifest.ReleaseFile file = manifest.clientJar()
            .orElseThrow(() -> new IOException("Kept manifest for " + version + " lists no " + manifest.clientJarName()));
        final Path kept = versionDir.resolve(file.name());
        if (!Files.isRegularFile(kept)) {
            throw new IOException("Kept jar missing for VANTA Client " + version);
        }
        if (file.hasSha256() && !Checksum.sha256(file.sha256()).matches(kept)) {
            Files.deleteIfExists(kept);
            throw new IntegrityException(kept, "Kept jar for VANTA Client " + version + " failed verification and was removed");
        }
        return activate(kept, file.name(), version);
    }

    /**
     * Lists the client versions kept for rollback, newest first by install time.
     *
     * @return versions
     * @throws IOException on failure
     */
    public List<KeptVersion> listKeptVersions() throws IOException {
        final List<KeptVersion> out = new ArrayList<>();
        if (!Files.isDirectory(paths.clientVersionsDir())) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.clientVersionsDir())) {
            for (Path dir : stream) {
                final Path manifestFile = dir.resolve(KEPT_MANIFEST);
                if (!Files.isDirectory(dir) || !Files.isRegularFile(manifestFile)) {
                    continue;
                }
                try {
                    final ReleaseManifest manifest = Json.read(manifestFile, ReleaseManifest.class);
                    final Optional<ReleaseManifest.ReleaseFile> jar = manifest.clientJar();
                    final Path kept = jar.map(f -> dir.resolve(f.name())).orElse(null);
                    out.add(new KeptVersion(manifest.version(), manifest, kept != null && Files.isRegularFile(kept) ? kept : null,
                        Files.getLastModifiedTime(manifestFile).toInstant()));
                } catch (IOException ignored) {
                    // skip unreadable entries
                }
            }
        }
        out.sort(KeptVersion.NEWEST_FIRST);
        return out;
    }

    /**
     * @return the active VANTA client jar in {@code mods/} when present
     * @throws IOException on failure
     */
    public Optional<Path> activeJar() throws IOException {
        if (!Files.isDirectory(paths.modsDir())) {
            return Optional.empty();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.modsDir(), JAR_PREFIX + "*.jar")) {
            for (Path p : stream) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * The client that is actually installed: the single source for "is a VANTA client installed, and which version"
     * used by the UI and {@code --check-update}.
     *
     * <ol>
     *   <li>A {@code vanta-client-*.jar} in {@code mods/}: its version is the one {@code instance.json} records for
     *       that file name, else the version in the file name ({@code vanta-client-1.0.1.jar} is 1.0.1).</li>
     *   <li>No jar, but {@code instance.json} records a client: that version (the jar is restored by the next
     *       install or verify).</li>
     *   <li>Otherwise nothing is installed; no client update is offered then, only the regular install.</li>
     * </ol>
     *
     * @return installed client, or empty when none is installed
     * @throws IOException when {@code instance.json} or {@code mods/} cannot be read
     */
    public Optional<InstalledClient> installedClient() throws IOException {
        final Optional<InstanceInfo> instance = Files.isRegularFile(paths.instanceFile())
            ? Optional.of(Json.read(paths.instanceFile(), InstanceInfo.class)) : Optional.empty();
        final Optional<Path> jar = activeJar();
        if (jar.isPresent()) {
            final String name = jar.get().getFileName().toString();
            final Optional<String> recorded = instance.filter(InstanceInfo::hasVantaClient)
                .filter(i -> i.vantaClientJar().equals(name))
                .map(InstanceInfo::vantaClientVersion)
                .filter(v -> !v.isBlank());
            if (recorded.isPresent()) {
                return Optional.of(new InstalledClient(recorded.get(), jar, InstalledClient.Source.INSTANCE));
            }
            return Optional.of(new InstalledClient(versionFromJarName(name), jar, InstalledClient.Source.MODS_JAR));
        }
        return instance.filter(InstanceInfo::hasVantaClient)
            .map(i -> new InstalledClient(i.vantaClientVersion(), Optional.empty(), InstalledClient.Source.INSTANCE));
    }

    /**
     * @param jarName file name such as {@code vanta-client-1.0.1.jar}
     * @return the version part ({@code 1.0.1}, {@code dev}); empty when the name has none
     */
    public static String versionFromJarName(final String jarName) {
        String v = jarName;
        if (v.startsWith(JAR_PREFIX)) {
            v = v.substring(JAR_PREFIX.length());
        }
        if (v.endsWith(".jar")) {
            v = v.substring(0, v.length() - ".jar".length());
        }
        return v;
    }

    /**
     * Records the client version in {@code instance.json} when the instance exists.
     *
     * @param version client version
     * @param jarName jar file name
     * @throws IOException on failure
     */
    public void recordInInstance(final String version, final String jarName) throws IOException {
        if (Files.isRegularFile(paths.instanceFile())) {
            final InstanceInfo info = Json.read(paths.instanceFile(), InstanceInfo.class);
            Json.write(paths.instanceFile(), info.withVantaClient(version, jarName));
        }
    }

    private Path activate(final Path source, final String targetName, final String version) throws IOException {
        Files.createDirectories(paths.modsDir());
        final Path target = paths.modsDir().resolve(targetName);
        // Remove other VANTA client jars so only one is loaded.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.modsDir(), JAR_PREFIX + "*.jar")) {
            for (Path p : stream) {
                if (!p.getFileName().toString().equals(targetName)) {
                    Files.deleteIfExists(p);
                }
            }
        }
        if (!Files.isRegularFile(target) || !Checksums.sha256Hex(target).equals(Checksums.sha256Hex(source))) {
            final Path tmp = AtomicFiles.tempSibling(target);
            Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
            AtomicFiles.move(tmp, target);
        }
        recordInInstance(version, targetName);
        return target;
    }

    private void pruneKeptVersions() throws IOException {
        final List<KeptVersion> kept = listKeptVersions();
        for (int i = KEEP_VERSIONS; i < kept.size(); i++) {
            deleteRecursively(paths.clientVersionsDir().resolve(kept.get(i).version()));
        }
    }

    private static void deleteRecursively(final Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            final List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : all) {
                Files.deleteIfExists(p);
            }
        }
    }

    /**
     * What installing a client jar changes in the VANTA data directory.
     *
     * @param activeJar      the jar written to {@code mods/}
     * @param keptFiles      the rollback copy and its manifest under {@code versions/vanta-client/<version>/} (empty
     *                       for a local jar)
     * @param instanceFile   {@code instance.json}, which records the client version, when it exists
     * @param replacedJars   other VANTA client jars removed from {@code mods/} so only one is loaded
     * @param prunedVersions rollback copies removed because only the {@value #KEEP_VERSIONS} newest are kept
     */
    public record ClientChanges(Path activeJar, List<Path> keptFiles, Optional<Path> instanceFile, List<Path> replacedJars,
                                List<Path> prunedVersions) {

        public ClientChanges {
            Objects.requireNonNull(activeJar, "activeJar");
            keptFiles = List.copyOf(keptFiles);
            Objects.requireNonNull(instanceFile, "instanceFile");
            replacedJars = List.copyOf(replacedJars);
            prunedVersions = List.copyOf(prunedVersions);
        }
    }

    /**
     * A client version kept for rollback.
     *
     * @param version     version
     * @param manifest    its manifest
     * @param jar         kept jar (null when missing)
     * @param installedAt install time
     */
    public record KeptVersion(String version, ReleaseManifest manifest, Path jar, Instant installedAt) {

        /** Newest version first (SemVer when parseable, then install time). */
        public static final Comparator<KeptVersion> NEWEST_FIRST = Comparator
            .comparing((KeptVersion k) -> dev.vanta.launcher.core.update.SemVer.tryParse(k.version()).orElse(dev.vanta.launcher.core.update.SemVer.of(0, 0, 0)))
            .thenComparing(KeptVersion::installedAt)
            .reversed();

        /** @return whether the jar exists */
        public boolean isAvailable() {
            return jar != null;
        }
    }
}
