package dev.vanta.launcher.core.net;

/**
 * Outcome of a successful download.
 *
 * @param request the request
 * @param skipped whether an existing verified file was reused
 * @param bytes   bytes transferred (0 when skipped)
 */
public record DownloadResult(DownloadRequest request, boolean skipped, long bytes) {
}
