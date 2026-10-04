package dev.vanta.launcher.core.net;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One file to download and verify.
 *
 * @param url          source URL
 * @param target       destination file
 * @param expectedSize expected size in bytes, {@code -1} when unknown
 * @param checksum     expected digest, {@code null} when none is available (strongly discouraged)
 * @param description  human readable label for progress UI
 */
public record DownloadRequest(URI url, Path target, long expectedSize, Checksum checksum, String description) {

    public DownloadRequest {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(target, "target");
        description = description == null || description.isBlank() ? target.getFileName().toString() : description;
        if (expectedSize < 0) {
            expectedSize = -1L;
        }
    }

    /**
     * Request with a known digest.
     *
     * @param url      source
     * @param target   destination
     * @param size     expected size ({@code -1} unknown)
     * @param checksum digest
     * @return request
     */
    public static DownloadRequest of(final URI url, final Path target, final long size, final Checksum checksum) {
        return new DownloadRequest(url, target, size, checksum, null);
    }

    /** @return digest when known */
    public Optional<Checksum> expectedChecksum() {
        return Optional.ofNullable(checksum);
    }

    /** @return whether a size is known */
    public boolean hasExpectedSize() {
        return expectedSize >= 0;
    }

    /**
     * @param newDescription label
     * @return copy with a new description
     */
    public DownloadRequest withDescription(final String newDescription) {
        return new DownloadRequest(url, target, expectedSize, checksum, newDescription);
    }
}
