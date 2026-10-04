package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.MavenCoordinate;
import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.net.DownloadRequest;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A library that applies to the current platform, with everything needed to download and reference it.
 *
 * @param coordinate   Maven coordinate
 * @param relativePath path below the libraries directory (forward slashes)
 * @param url          download URL ({@code null} when the library is expected to exist locally)
 * @param checksum     expected digest ({@code null} when unknown)
 * @param size         expected size, {@code -1} when unknown
 * @param nativeJar    whether this is a natives jar
 */
public record ResolvedLibrary(MavenCoordinate coordinate, String relativePath, String url, Checksum checksum, long size,
                              boolean nativeJar) {

    public ResolvedLibrary {
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(relativePath, "relativePath");
        if (size < 0) {
            size = -1L;
        }
    }

    /** @return digest when known */
    public Optional<Checksum> expectedChecksum() {
        return Optional.ofNullable(checksum);
    }

    /** @return download URL when known */
    public Optional<String> downloadUrl() {
        return Optional.ofNullable(url);
    }

    /**
     * @param librariesDir libraries root
     * @return absolute file location
     */
    public Path file(final Path librariesDir) {
        return librariesDir.resolve(relativePath.replace('/', librariesDir.getFileSystem().getSeparator().charAt(0)));
    }

    /**
     * Builds a download request for this library.
     *
     * @param librariesDir libraries root
     * @return request
     * @throws IllegalStateException when no URL is known
     */
    public DownloadRequest downloadRequest(final Path librariesDir) {
        if (url == null) {
            throw new IllegalStateException("No download URL for " + coordinate);
        }
        return new DownloadRequest(URI.create(url), file(librariesDir), size, checksum, coordinate.toString());
    }

    /**
     * Copy with a checksum.
     *
     * @param newChecksum digest
     * @return copy
     */
    public ResolvedLibrary withChecksum(final Checksum newChecksum) {
        return new ResolvedLibrary(coordinate, relativePath, url, newChecksum, size, nativeJar);
    }
}
