package dev.vanta.launcher.core.net;

/**
 * Progress callbacks of the {@link Downloader}. Methods may be invoked from worker threads.
 */
public interface DownloadProgressListener {

    /** Listener that ignores everything. */
    DownloadProgressListener NONE = new DownloadProgressListener() {
    };

    /**
     * Bytes were received for a request.
     *
     * @param request    the request
     * @param bytesDone  bytes written so far
     * @param bytesTotal total bytes or {@code -1} when unknown
     */
    default void onProgress(final DownloadRequest request, final long bytesDone, final long bytesTotal) {
    }

    /**
     * A request finished successfully.
     *
     * @param request the request
     * @param skipped whether an existing verified file was reused
     * @param bytes   bytes transferred (0 when skipped)
     */
    default void onComplete(final DownloadRequest request, final boolean skipped, final long bytes) {
    }

    /**
     * An attempt failed and will be retried.
     *
     * @param request the request
     * @param attempt attempt number (1-based) that failed
     * @param error   failure
     */
    default void onRetry(final DownloadRequest request, final int attempt, final Exception error) {
    }
}
