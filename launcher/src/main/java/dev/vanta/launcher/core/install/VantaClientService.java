package dev.vanta.launcher.core.install;

import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.net.JsonHttp;
import dev.vanta.launcher.core.paths.LauncherPaths;
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
 *   <li>Published releases come from the release manifest ({@code <releasesBaseUrl>/client-latest.json}); the jar is
 *       verified with its SHA-256 and a copy is kept under {@code versions/vanta-client/<version>/} for rollback
 *       (the {@value #KEEP_VERSIONS} highest versions are retained).</li>
 *   <li>A manifest with an empty {@code downloadUrl} raises {@link NotPublishedException}.</li>
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

    private final Downloader downloader;
    private final JsonHttp json;
    private final LauncherPaths paths;
    private final Supplier<String> releasesBaseUrl;

    /**
     * @param downloader      downloader
     * @param paths           paths
     * @param releasesBaseUrl supplier of the configured releases base URL (empty string = not configured)
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
     * Fetches the client release manifest.
     *
     * @return manifest
     * @throws NotPublishedException when no releases base URL is configured
     * @throws IOException           on failure
     * @throws InterruptedException  when interrupted
     */
    public ReleaseManifest fetchClientManifest() throws IOException, InterruptedException {
        final String base = releasesBaseUrl.get();
        if (base == null || base.isBlank()) {
            throw new NotPublishedException(ReleaseManifest.PRODUCT_CLIENT, "",
                "No releases URL is configured. Set \"releasesBaseUrl\" in the launcher settings to the location of "
                    + "client-latest.json, or install a local build with --client-jar <path>.");
        }
        final ReleaseManifest manifest = json.get(manifestUrl(base, CLIENT_MANIFEST), ReleaseManifest.class);
        if (!ReleaseManifest.PRODUCT_CLIENT.equals(manifest.product())) {
            throw new IOException("Manifest at " + base + " describes product '" + manifest.product() + "', expected 'client'");
        }
        return manifest;
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
        final ReleaseManifest.ReleaseFile file = manifest.fileWithExtension(".jar")
            .orElseThrow(() -> new NotPublishedException(ReleaseManifest.PRODUCT_CLIENT, manifest.version(),
                "The client release manifest lists no jar file"));
        if (!file.isPublished()) {
            throw new NotPublishedException(ReleaseManifest.PRODUCT_CLIENT, manifest.version(),
                "VANTA Client " + manifest.version() + " has no public download yet");
        }
        if (!file.hasSha256()) {
            throw new IntegrityException(null, "The release manifest for VANTA Client " + manifest.version()
                + " has no SHA-256; refusing to install an unverifiable file");
        }
        final Path versionDir = paths.clientVersionsDir().resolve(manifest.version());
        final Path kept = versionDir.resolve(file.name());
        final DownloadRequest request = new DownloadRequest(URI.create(file.downloadUrl()), kept,
            file.size() > 0 ? file.size() : -1L, Checksum.sha256(file.sha256()), file.name());
        downloader.download(request, listener, token);
        Json.write(versionDir.resolve("manifest.json"), manifest);
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
        if (!Files.isRegularFile(jar)) {
            throw new IOException("Client jar not found: " + jar);
        }
        final String name = jar.getFileName().toString();
        if (!name.endsWith(".jar")) {
            throw new IOException("Not a jar file: " + jar);
        }
        final String targetName = name.startsWith(JAR_PREFIX) ? name : JAR_PREFIX + DEV_VERSION + ".jar";
        return activate(jar, targetName, DEV_VERSION);
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
        final Path manifestFile = versionDir.resolve("manifest.json");
        if (!Files.isRegularFile(manifestFile)) {
            throw new IOException("VANTA Client " + version + " is not available locally for rollback");
        }
        final ReleaseManifest manifest = Json.read(manifestFile, ReleaseManifest.class);
        final ReleaseManifest.ReleaseFile file = manifest.fileWithExtension(".jar")
            .orElseThrow(() -> new IOException("Kept manifest for " + version + " lists no jar"));
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
                final Path manifestFile = dir.resolve("manifest.json");
                if (!Files.isDirectory(dir) || !Files.isRegularFile(manifestFile)) {
                    continue;
                }
                try {
                    final ReleaseManifest manifest = Json.read(manifestFile, ReleaseManifest.class);
                    final Optional<ReleaseManifest.ReleaseFile> jar = manifest.fileWithExtension(".jar");
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
