package dev.vanta.launcher.core.java;

import com.google.gson.reflect.TypeToken;
import dev.vanta.launcher.core.model.AdoptiumAsset;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Installs an Eclipse Temurin 21 JRE from the Adoptium API into {@code runtimes/temurin-21-<release>}.
 *
 * <p>The archive is verified with the SHA-256 published by the API, extracted with path-traversal protection and
 * then probed like any other runtime. Nothing from the archive is executed except the resulting {@code java}
 * binary, and only once it has been verified.</p>
 */
public final class AdoptiumService {

    /** Adoptium API base. */
    public static final URI API_BASE = URI.create("https://api.adoptium.net/v3/");
    /** Java feature version installed by the launcher. */
    public static final int FEATURE_VERSION = 21;

    private final Downloader downloader;
    private final JsonHttp json;
    private final LauncherPaths paths;
    private final OsInfo os;
    private final URI apiBase;
    private final JavaProbe probe;

    /**
     * Production endpoints.
     *
     * @param downloader downloader
     * @param paths      paths
     * @param os         platform
     * @param probe      probe for the installed runtime
     */
    public AdoptiumService(final Downloader downloader, final LauncherPaths paths, final OsInfo os, final JavaProbe probe) {
        this(downloader, paths, os, probe, API_BASE);
    }

    /**
     * @param downloader downloader
     * @param paths      paths
     * @param os         platform
     * @param probe      probe
     * @param apiBase    API base (ending with a slash)
     */
    public AdoptiumService(final Downloader downloader, final LauncherPaths paths, final OsInfo os, final JavaProbe probe,
                           final URI apiBase) {
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.json = new JsonHttp(downloader);
        this.paths = Objects.requireNonNull(paths, "paths");
        this.os = Objects.requireNonNull(os, "os");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.apiBase = Objects.requireNonNull(apiBase, "apiBase");
    }

    /**
     * @return URL of the "latest Temurin 21 JRE" query for this platform
     */
    public URI latestAssetsUrl() {
        final String query = "os=" + enc(os.adoptiumOs()) + "&architecture=" + enc(os.adoptiumArch())
            + "&image_type=jre&vendor=eclipse";
        return apiBase.resolve("assets/latest/" + FEATURE_VERSION + "/hotspot?" + query);
    }

    /**
     * Queries the latest release.
     *
     * @return asset for this platform
     * @throws IOException          when the API fails or lists no matching asset
     * @throws InterruptedException when interrupted
     */
    public AdoptiumAsset findLatest() throws IOException, InterruptedException {
        final List<AdoptiumAsset> assets = json.get(latestAssetsUrl(), new TypeToken<List<AdoptiumAsset>>() {
        });
        return assets.stream()
            .filter(a -> a.binary() != null && a.binary().pkg() != null && a.binary().pkg().link() != null)
            .filter(a -> os.adoptiumOs().equals(a.binary().os()) && os.adoptiumArch().equals(a.binary().architecture()))
            .filter(a -> "jre".equals(a.binary().imageType()))
            .findFirst()
            .orElseThrow(() -> new IOException("Adoptium lists no Temurin " + FEATURE_VERSION + " JRE for "
                + os.adoptiumOs() + "/" + os.adoptiumArch()));
    }

    /**
     * Target directory for a release.
     *
     * @param asset asset
     * @return {@code runtimes/temurin-21-<release>}
     */
    public Path runtimeDir(final AdoptiumAsset asset) {
        final String release = asset.releaseName() == null ? "latest" : asset.releaseName().replaceAll("[^A-Za-z0-9._+-]", "_");
        return paths.runtimesDir().resolve("temurin-" + FEATURE_VERSION + "-" + release);
    }

    /**
     * Downloads, verifies and extracts the latest Temurin 21 JRE and probes it.
     *
     * @param listener download progress
     * @param token    cancellation
     * @return the installed runtime
     * @throws IOException          on failure, {@link IntegrityException} on checksum mismatch,
     *                              {@link UnsafeArchiveException} on a malicious archive
     * @throws InterruptedException when interrupted
     */
    public JavaInstall install(final DownloadProgressListener listener, final CancellationToken token) throws IOException, InterruptedException {
        final AdoptiumAsset asset = findLatest();
        return install(asset, listener, token);
    }

    /**
     * Installs a specific asset.
     *
     * @param asset    asset from {@link #findLatest()}
     * @param listener download progress
     * @param token    cancellation
     * @return the installed runtime
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public JavaInstall install(final AdoptiumAsset asset, final DownloadProgressListener listener, final CancellationToken token)
        throws IOException, InterruptedException {
        final AdoptiumAsset.Package pkg = asset.binary().pkg();
        if (pkg.checksum() == null || pkg.checksum().isBlank()) {
            throw new IntegrityException(null, "Adoptium did not publish a SHA-256 for " + pkg.name() + "; refusing to install");
        }
        final Path target = runtimeDir(asset);
        final Optional<JavaInstall> existing = probeRuntime(target);
        if (existing.isPresent()) {
            return existing.get();
        }
        Files.createDirectories(paths.cacheDir());
        final Path archive = paths.cacheDir().resolve(pkg.name());
        final DownloadRequest request = new DownloadRequest(URI.create(pkg.link()), archive, pkg.size() > 0 ? pkg.size() : -1L,
            Checksum.sha256(pkg.checksum()), pkg.name());
        downloader.download(request, listener, token);
        token.throwIfCancelled();
        final Path staging = target.resolveSibling(target.getFileName() + ".extracting");
        deleteRecursively(staging);
        try {
            ArchiveExtractor.extract(archive, staging);
            deleteRecursively(target);
            Files.move(staging, target);
        } finally {
            deleteRecursively(staging);
            Files.deleteIfExists(archive);
        }
        return probeRuntime(target).orElseThrow(() -> new IOException("The extracted runtime at " + target
            + " does not contain a working Java " + FEATURE_VERSION + " executable"));
    }

    /**
     * Locates and probes the Java executable inside an extracted runtime directory (handles the top-level
     * {@code jdk-21.0.x+y-jre/} folder and macOS {@code Contents/Home}).
     *
     * @param runtimeDir runtime directory
     * @return runtime when valid
     * @throws IOException on I/O failure
     */
    public Optional<JavaInstall> probeRuntime(final Path runtimeDir) throws IOException {
        if (!Files.isDirectory(runtimeDir)) {
            return Optional.empty();
        }
        final Optional<Path> exe = findExecutable(runtimeDir);
        return exe.flatMap(probe::probe).filter(j -> j.major() >= FEATURE_VERSION);
    }

    /**
     * Finds {@code bin/java} within a few directory levels.
     *
     * @param root root directory
     * @return executable when present
     * @throws IOException on I/O failure
     */
    public Optional<Path> findExecutable(final Path root) throws IOException {
        final String exe = os.javaExecutableName();
        try (Stream<Path> walk = Files.walk(root, 5)) {
            return walk.filter(p -> p.getFileName() != null && p.getFileName().toString().equals(exe))
                .filter(p -> p.getParent() != null && "bin".equals(p.getParent().getFileName().toString()))
                .filter(Files::isRegularFile)
                .min(Comparator.comparingInt(Path::getNameCount));
        }
    }

    private static void deleteRecursively(final Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static String enc(final String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
