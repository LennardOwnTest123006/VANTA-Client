package dev.vanta.launcher.core.install;

import dev.vanta.launcher.core.model.MavenCoordinate;
import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.paths.LauncherPaths;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fabric API mod jar from the Fabric Maven repository, verified with the {@code .sha256} sidecar (falling back to
 * {@code .sha1}). Older Fabric API jars in {@code mods/} are removed so exactly one version is loaded.
 */
public final class FabricApiService {

    /** Maven coordinate prefix. */
    public static final String GROUP = "net.fabricmc.fabric-api";
    /** Maven artifact id. */
    public static final String ARTIFACT = "fabric-api";

    private final FabricService fabric;
    private final LauncherPaths paths;
    private final URI mavenBase;

    /**
     * Production repository.
     *
     * @param fabric Fabric service (for sidecar checksums)
     * @param paths  paths
     */
    public FabricApiService(final FabricService fabric, final LauncherPaths paths) {
        this(fabric, paths, FabricService.MAVEN_BASE);
    }

    /**
     * @param fabric    Fabric service
     * @param paths     paths
     * @param mavenBase Maven base URL (ending with a slash)
     */
    public FabricApiService(final FabricService fabric, final LauncherPaths paths, final URI mavenBase) {
        this.fabric = Objects.requireNonNull(fabric, "fabric");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.mavenBase = Objects.requireNonNull(mavenBase, "mavenBase");
    }

    /**
     * @param version Fabric API version (e.g. {@code 0.141.6+1.21.11})
     * @return Maven coordinate
     */
    public static MavenCoordinate coordinate(final String version) {
        return new MavenCoordinate(GROUP, ARTIFACT, version, "", "jar");
    }

    /**
     * @param version Fabric API version
     * @return download URL of the jar
     */
    public URI url(final String version) {
        return mavenBase.resolve(coordinate(version).path());
    }

    /**
     * @param version Fabric API version
     * @return target jar in {@code mods/}
     */
    public Path targetJar(final String version) {
        return paths.modsDir().resolve(coordinate(version).fileName());
    }

    /**
     * Builds the verified download request for a Fabric API version.
     *
     * @param version Fabric API version
     * @return request
     * @throws IOException          when no checksum sidecar exists
     * @throws InterruptedException when interrupted
     */
    public DownloadRequest request(final String version) throws IOException, InterruptedException {
        final MavenCoordinate coordinate = coordinate(version);
        final URI url = url(version);
        final Path target = targetJar(version);
        // An existing jar is verified against the sidecar as well (cheap, and protects against a corrupt mods dir).
        final Checksum checksum = fabric.fetchSidecarChecksum(url);
        return new DownloadRequest(url, target, -1L, checksum, coordinate.fileName());
    }

    /**
     * Deletes other Fabric API jars from {@code mods/}.
     *
     * @param keepVersion version to keep
     * @return deleted files
     * @throws IOException on failure
     */
    public List<Path> pruneOtherVersions(final String keepVersion) throws IOException {
        final List<Path> deleted = new ArrayList<>();
        for (Path p : otherVersions(keepVersion)) {
            Files.deleteIfExists(p);
            deleted.add(p);
        }
        return deleted;
    }

    /**
     * Lists the Fabric API jars in {@code mods/} that {@link #pruneOtherVersions} would delete. Changes nothing.
     *
     * @param keepVersion version to keep
     * @return other Fabric API jars, sorted by name
     * @throws IOException on failure
     */
    public List<Path> otherVersions(final String keepVersion) throws IOException {
        final List<Path> others = new ArrayList<>();
        final Path keep = targetJar(keepVersion);
        if (!Files.isDirectory(paths.modsDir())) {
            return others;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.modsDir(), ARTIFACT + "-*.jar")) {
            for (Path p : stream) {
                if (!p.getFileName().equals(keep.getFileName())) {
                    others.add(p);
                }
            }
        }
        others.sort(null);
        return others;
    }
}
