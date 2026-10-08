package dev.vanta.launcher.core.net;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
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
 * @param timeout      timeout for the whole HTTP exchange of one attempt ({@link HttpRequestSpec#DEFAULT_TIMEOUT} when
 *                     {@code null}); a multi-gigabyte file on a slow connection needs hours, the stall watchdog of the
 *                     {@link Downloader} still aborts a transfer that stops delivering bytes
 */
public record DownloadRequest(URI url, Path target, long expectedSize, Checksum checksum, String description, Duration timeout) {

    public DownloadRequest {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(target, "target");
        description = description == null || description.isBlank() ? target.getFileName().toString() : description;
        if (expectedSize < 0) {
            expectedSize = -1L;
        }
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? HttpRequestSpec.DEFAULT_TIMEOUT : timeout;
    }

    /**
     * Request with the default exchange timeout.
     *
     * @param url          source URL
     * @param target       destination file
     * @param expectedSize expected size ({@code -1} unknown)
     * @param checksum     expected digest (may be null)
     * @param description  label
     */
    public DownloadRequest(final URI url, final Path target, final long expectedSize, final Checksum checksum, final String description) {
        this(url, target, expectedSize, checksum, description, null);
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
        return new DownloadRequest(url, target, size, checksum, null, null);
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
        return new DownloadRequest(url, target, expectedSize, checksum, newDescription, timeout);
    }

    /**
     * @param newTimeout exchange timeout per attempt
     * @return copy with a different timeout
     */
    public DownloadRequest withTimeout(final Duration newTimeout) {
        return new DownloadRequest(url, target, expectedSize, checksum, description, newTimeout);
    }
}
