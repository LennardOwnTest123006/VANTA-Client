package dev.vanta.launcher.core.net;

import dev.vanta.launcher.core.util.AtomicFiles;
import dev.vanta.launcher.core.util.Sleeper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verified file downloads.
 *
 * <ul>
 *   <li>Every file is written to a temporary sibling and moved into place atomically.</li>
 *   <li>Size and digest are verified while streaming; a mismatch deletes the file and raises
 *       {@link IntegrityException}.</li>
 *   <li>Transient failures (I/O errors, 5xx, 408, 429) are retried with exponential back-off, three attempts
 *       in total.</li>
 *   <li>A transfer that receives no bytes for {@link #DEFAULT_STALL_TIMEOUT} is aborted and retried; the JDK
 *       request timeout only covers the response headers, not a stalled body.</li>
 *   <li>{@link #downloadAll} runs a bounded number of transfers in parallel and stops at the first failure.</li>
 *   <li>Existing files that already match size and digest are skipped, which makes installs resumable.</li>
 * </ul>
 */
public final class Downloader implements AutoCloseable {

    /** Default number of attempts per file. */
    public static final int DEFAULT_ATTEMPTS = 3;
    /** Default parallelism of {@link #downloadAll}. */
    public static final int DEFAULT_CONCURRENCY = 6;
    /** Base back-off between attempts. */
    public static final Duration BASE_BACKOFF = Duration.ofMillis(500);
    /** Default time without any received bytes after which a transfer is considered stalled. */
    public static final Duration DEFAULT_STALL_TIMEOUT = Duration.ofSeconds(60);

    private static final Logger LOG = Logger.getLogger("VANTA.Downloader");
    private static final int BUFFER = 64 * 1024;
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "vanta-download-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final HttpTransport transport;
    private final Sleeper sleeper;
    private final int attempts;
    private final int concurrency;
    private final Duration stallTimeout;

    /**
     * Production downloader with defaults.
     *
     * @param transport transport
     */
    public Downloader(final HttpTransport transport) {
        this(transport, Sleeper.REAL, DEFAULT_ATTEMPTS, DEFAULT_CONCURRENCY);
    }

    /**
     * @param transport   transport
     * @param sleeper     back-off sleeper
     * @param attempts    attempts per file (&gt;= 1)
     * @param concurrency parallel transfers of {@link #downloadAll} (&gt;= 1)
     */
    public Downloader(final HttpTransport transport, final Sleeper sleeper, final int attempts, final int concurrency) {
        this(transport, sleeper, attempts, concurrency, DEFAULT_STALL_TIMEOUT);
    }

    /**
     * @param transport    transport
     * @param sleeper      back-off sleeper
     * @param attempts     attempts per file (&gt;= 1)
     * @param concurrency  parallel transfers of {@link #downloadAll} (&gt;= 1)
     * @param stallTimeout time without received bytes after which a transfer is aborted and retried
     */
    public Downloader(final HttpTransport transport, final Sleeper sleeper, final int attempts, final int concurrency,
                      final Duration stallTimeout) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.attempts = Math.max(1, attempts);
        this.concurrency = Math.max(1, concurrency);
        this.stallTimeout = Objects.requireNonNull(stallTimeout, "stallTimeout");
    }

    /** @return the transport */
    public HttpTransport transport() {
        return transport;
    }

    /**
     * Checks whether the target already exists and matches the expected size and digest.
     *
     * @param request request
     * @return whether the existing file can be reused
     * @throws IOException on read failure
     */
    public boolean isValid(final DownloadRequest request) throws IOException {
        final Path target = request.target();
        if (!Files.isRegularFile(target)) {
            return false;
        }
        if (request.hasExpectedSize() && Files.size(target) != request.expectedSize()) {
            return false;
        }
        final Optional<Checksum> checksum = request.expectedChecksum();
        if (checksum.isPresent()) {
            return checksum.get().matches(target);
        }
        // Without a digest we can only trust the size; without either, an existing file is accepted.
        return request.hasExpectedSize() || Files.size(target) > 0;
    }

    /**
     * Downloads one file, skipping it when a verified copy exists.
     *
     * @param request  request
     * @param listener progress listener
     * @param token    cancellation token
     * @return result
     * @throws IOException          on failure after all attempts; {@link IntegrityException} for digest mismatches
     * @throws InterruptedException when interrupted
     */
    public DownloadResult download(final DownloadRequest request, final DownloadProgressListener listener,
                                   final CancellationToken token) throws IOException, InterruptedException {
        Objects.requireNonNull(request, "request");
        final DownloadProgressListener l = listener == null ? DownloadProgressListener.NONE : listener;
        final CancellationToken t = token == null ? CancellationToken.NONE : token;
        t.throwIfCancelled();
        if (isValid(request)) {
            l.onComplete(request, true, 0L);
            return new DownloadResult(request, true, 0L);
        }
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            t.throwIfCancelled();
            try {
                final long bytes = transfer(request, l, t);
                l.onComplete(request, false, bytes);
                return new DownloadResult(request, false, bytes);
            } catch (HttpStatusException e) {
                last = e;
                if (!e.isRetryable()) {
                    throw e;
                }
            } catch (IntegrityException e) {
                // A corrupt transfer may be transient (truncated proxy response); retry, then surface it.
                last = e;
            } catch (IOException e) {
                last = e;
            }
            if (attempt < attempts) {
                l.onRetry(request, attempt, last);
                LOG.log(Level.FINE, "Retrying {0} after attempt {1}: {2}", new Object[] {request.url(), attempt, last});
                sleeper.sleep(BASE_BACKOFF.multipliedBy(1L << (attempt - 1)));
            }
        }
        throw last;
    }

    /**
     * Downloads many files with bounded parallelism. The first failure cancels the remaining transfers and is
     * rethrown.
     *
     * @param requests requests
     * @param listener listener (invoked from worker threads)
     * @param token    cancellation token
     * @return results in request order
     * @throws IOException          first failure
     * @throws InterruptedException when interrupted
     */
    public List<DownloadResult> downloadAll(final List<DownloadRequest> requests, final DownloadProgressListener listener,
                                            final CancellationToken token) throws IOException, InterruptedException {
        final CancellationToken outer = token == null ? CancellationToken.NONE : token;
        if (requests.isEmpty()) {
            return List.of();
        }
        final CancellationToken inner = new CancellationToken();
        final AtomicInteger counter = new AtomicInteger();
        final ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency, requests.size()), r -> {
            final Thread th = new Thread(r, "vanta-download-" + counter.incrementAndGet());
            th.setDaemon(true);
            return th;
        });
        try {
            final List<Future<DownloadResult>> futures = new ArrayList<>(requests.size());
            for (DownloadRequest request : requests) {
                futures.add(pool.submit(() -> {
                    if (outer.isCancelled()) {
                        inner.cancel();
                    }
                    inner.throwIfCancelled();
                    return download(request, listener, inner);
                }));
            }
            final List<DownloadResult> results = new ArrayList<>(requests.size());
            IOException failure = null;
            for (Future<DownloadResult> f : futures) {
                try {
                    results.add(f.get());
                } catch (ExecutionException e) {
                    inner.cancel();
                    final Throwable cause = e.getCause();
                    if (failure == null) {
                        if (cause instanceof IOException io) {
                            failure = io;
                        } else if (cause instanceof CancellationException ce) {
                            if (outer.isCancelled()) {
                                throw ce;
                            }
                            // cancelled because of another failure; keep looking for the root cause
                        } else if (cause instanceof InterruptedException) {
                            throw new InterruptedException("Download interrupted");
                        } else {
                            failure = new IOException("Download failed: " + cause, cause);
                        }
                    }
                } catch (CancellationException e) {
                    inner.cancel();
                }
            }
            if (failure != null) {
                throw failure;
            }
            if (outer.isCancelled()) {
                throw new CancellationException("Download cancelled");
            }
            return results;
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Fetches a small text document (manifests, metadata).
     *
     * @param url URL
     * @return body as UTF-8 text
     * @throws IOException          on failure, {@link HttpStatusException} for non-2xx
     * @throws InterruptedException when interrupted
     */
    public String fetchString(final URI url) throws IOException, InterruptedException {
        return new String(fetchBytes(url), StandardCharsets.UTF_8);
    }

    /**
     * Fetches a small document into memory, with the same retry policy as file downloads.
     *
     * @param url URL
     * @return body bytes
     * @throws IOException          on failure, {@link HttpStatusException} for non-2xx
     * @throws InterruptedException when interrupted
     */
    public byte[] fetchBytes(final URI url) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try (HttpResult result = transport.execute(HttpRequestSpec.get(url).withTimeout(Duration.ofSeconds(60)))) {
                if (!result.isSuccess()) {
                    final HttpStatusException e = new HttpStatusException(result.status(), url);
                    if (!e.isRetryable()) {
                        throw e;
                    }
                    last = e;
                } else {
                    return result.bodyAsBytes();
                }
            } catch (HttpStatusException e) {
                throw e;
            } catch (IOException e) {
                last = e;
            }
            if (attempt < attempts) {
                sleeper.sleep(BASE_BACKOFF.multipliedBy(1L << (attempt - 1)));
            }
        }
        throw last;
    }

    private long transfer(final DownloadRequest request, final DownloadProgressListener listener, final CancellationToken token)
        throws IOException, InterruptedException {
        final Path target = request.target();
        final Path dir = target.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        final Path tmp = AtomicFiles.tempSibling(target);
        final AtomicBoolean stalled = new AtomicBoolean();
        try (HttpResult result = transport.execute(HttpRequestSpec.get(request.url()).withTimeout(request.timeout()))) {
            if (!result.isSuccess()) {
                throw new HttpStatusException(result.status(), request.url());
            }
            final long total = request.hasExpectedSize() ? request.expectedSize() : result.contentLength();
            final Optional<Checksum> checksum = request.expectedChecksum();
            final MessageDigest digest = checksum.map(c -> c.algorithm().newDigest()).orElse(null);
            long written = 0;
            final InputStream in = result.body();
            final AtomicLong lastProgress = new AtomicLong(System.nanoTime());
            final ScheduledFuture<?> watchdog = scheduleWatchdog(in, lastProgress, stalled, token);
            try (in;
                 OutputStream file = Files.newOutputStream(tmp);
                 OutputStream out = digest == null ? file : new DigestOutputStream(file, digest)) {
                final byte[] buf = new byte[BUFFER];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    lastProgress.set(System.nanoTime());
                    token.throwIfCancelled();
                    out.write(buf, 0, n);
                    written += n;
                    if (request.hasExpectedSize() && written > request.expectedSize()) {
                        throw new IntegrityException(target, "Downloaded more than the expected " + request.expectedSize()
                            + " bytes for " + request.url());
                    }
                    listener.onProgress(request, written, total);
                }
            } catch (IOException e) {
                if (stalled.get()) {
                    throw new IOException("No data received for " + stallTimeout.toSeconds() + "s while downloading " + request.url(), e);
                }
                throw e;
            } finally {
                watchdog.cancel(false);
            }
            if (request.hasExpectedSize() && written != request.expectedSize()) {
                throw new IntegrityException(target, "Size mismatch for " + request.url() + ": expected "
                    + request.expectedSize() + " bytes but received " + written);
            }
            if (digest != null) {
                final String actual = Checksums.toHex(digest.digest());
                final Checksum expected = checksum.get();
                if (!expected.hex().equals(actual)) {
                    throw new IntegrityException(target, expected.algorithm() + " mismatch for " + request.url()
                        + ": expected " + expected.hex() + " but computed " + actual);
                }
            }
            AtomicFiles.move(tmp, target);
            return written;
        } catch (CancellationException e) {
            Files.deleteIfExists(tmp);
            throw e;
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            if (e instanceof IntegrityException) {
                Files.deleteIfExists(target);
            }
            throw e;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Schedules a task that closes the body stream when no bytes arrived within the stall timeout, or when the
     * token was cancelled while the read is blocked.
     */
    private ScheduledFuture<?> scheduleWatchdog(final InputStream in, final AtomicLong lastProgress, final AtomicBoolean stalled,
                                                final CancellationToken token) {
        final long periodMs = Math.max(20L, stallTimeout.toMillis() / 4);
        return WATCHDOG.scheduleAtFixedRate(() -> {
            final boolean timedOut = System.nanoTime() - lastProgress.get() > stallTimeout.toNanos();
            if (timedOut || token.isCancelled()) {
                if (timedOut) {
                    stalled.set(true);
                }
                try {
                    in.close();
                } catch (IOException ignored) {
                    // closing an already failed stream
                }
            }
        }, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        transport.close();
    }
}
