package dev.vanta.launcher.core.net;

import dev.vanta.launcher.core.util.Sleeper;
import dev.vanta.launcher.testutil.FakeHttpServer;
import dev.vanta.launcher.testutil.RawHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloaderTest {

    private static final byte[] CONTENT = "the quick brown fox jumps over the lazy dog\n".repeat(400).getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tmp;
    private FakeHttpServer server;
    private Downloader downloader;

    @BeforeEach
    void start() throws IOException {
        server = new FakeHttpServer();
        server.add("files/lib.jar", CONTENT);
        downloader = new Downloader(new JdkHttpTransport("VANTA-Launcher/test"), Sleeper.NONE, 3, 4);
    }

    @AfterEach
    void stop() {
        downloader.close();
        server.close();
    }

    private DownloadRequest request(final String name, final Checksum checksum, final long size) {
        return new DownloadRequest(server.url("files/lib.jar"), tmp.resolve(name), size, checksum, name);
    }

    @Test
    void downloadsVerifiesAndReportsProgress() throws Exception {
        final AtomicLong last = new AtomicLong();
        final List<Long> totals = new ArrayList<>();
        final DownloadResult result = downloader.download(request("a.jar", Checksum.sha1(Checksums.hex(CONTENT, HashAlgorithm.SHA1)), CONTENT.length),
            new DownloadProgressListener() {
                @Override
                public void onProgress(final DownloadRequest r, final long done, final long total) {
                    last.set(done);
                    totals.add(total);
                }
            }, CancellationToken.NONE);
        assertFalse(result.skipped());
        assertEquals(CONTENT.length, result.bytes());
        assertEquals(CONTENT.length, last.get());
        assertTrue(totals.stream().allMatch(t -> t == CONTENT.length));
        assertArrayEquals(CONTENT, Files.readAllBytes(tmp.resolve("a.jar")));
        assertNoTempFiles();
    }

    @Test
    void sha256AndUnknownSizeAccepted() throws Exception {
        final DownloadResult result = downloader.download(request("b.jar", Checksum.sha256(Checksums.hex(CONTENT, HashAlgorithm.SHA256)), -1),
            DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(CONTENT.length, result.bytes());
    }

    @Test
    void existingVerifiedFileIsSkipped() throws Exception {
        final DownloadRequest r = request("c.jar", Checksum.sha1(Checksums.hex(CONTENT, HashAlgorithm.SHA1)), CONTENT.length);
        downloader.download(r, DownloadProgressListener.NONE, CancellationToken.NONE);
        final DownloadResult second = downloader.download(r, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertTrue(second.skipped());
        assertEquals(1, server.hits("files/lib.jar"), "no second request");
    }

    @Test
    void existingCorruptFileIsReplaced() throws Exception {
        final DownloadRequest r = request("d.jar", Checksum.sha1(Checksums.hex(CONTENT, HashAlgorithm.SHA1)), CONTENT.length);
        Files.write(r.target(), new byte[CONTENT.length]);
        assertFalse(downloader.isValid(r));
        downloader.download(r, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertArrayEquals(CONTENT, Files.readAllBytes(r.target()));
    }

    @Test
    void checksumMismatchDeletesFileAndThrowsAfterRetries() throws IOException {
        final DownloadRequest r = request("e.jar", Checksum.sha1("0000000000000000000000000000000000000000"), CONTENT.length);
        final IntegrityException e = assertThrows(IntegrityException.class,
            () -> downloader.download(r, DownloadProgressListener.NONE, CancellationToken.NONE));
        assertTrue(e.getMessage().contains("SHA1 mismatch"));
        assertFalse(Files.exists(r.target()));
        assertEquals(3, server.hits("files/lib.jar"), "integrity failures are retried up to the attempt limit");
        assertNoTempFiles();
    }

    @Test
    void sizeMismatchIsAnIntegrityFailure() {
        final DownloadRequest r = request("f.jar", null, CONTENT.length - 10);
        final IntegrityException e = assertThrows(IntegrityException.class,
            () -> downloader.download(r, DownloadProgressListener.NONE, CancellationToken.NONE));
        assertTrue(e.getMessage().contains("expected"));
        assertFalse(Files.exists(r.target()));
    }

    @Test
    void retriesOnServerErrorsThenSucceeds() throws Exception {
        server.failFirst("files/lib.jar", 2, 503);
        final AtomicInteger retries = new AtomicInteger();
        final DownloadResult result = downloader.download(request("g.jar", Checksum.sha1(Checksums.hex(CONTENT, HashAlgorithm.SHA1)), CONTENT.length),
            new DownloadProgressListener() {
                @Override
                public void onRetry(final DownloadRequest r, final int attempt, final Exception error) {
                    retries.incrementAndGet();
                }
            }, CancellationToken.NONE);
        assertEquals(CONTENT.length, result.bytes());
        assertEquals(2, retries.get());
        assertEquals(3, server.hits("files/lib.jar"));
    }

    @Test
    void recoversFromTruncatedResponse() throws Exception {
        try (RawHttpServer raw = new RawHttpServer(CONTENT, List.of(RawHttpServer.Behaviour.TRUNCATE, RawHttpServer.Behaviour.FULL))) {
            final DownloadRequest r = new DownloadRequest(raw.url(), tmp.resolve("h.jar"), CONTENT.length,
                Checksum.sha1(Checksums.hex(CONTENT, HashAlgorithm.SHA1)), "h");
            final DownloadResult result = downloader.download(r, DownloadProgressListener.NONE, CancellationToken.NONE);
            assertEquals(CONTENT.length, result.bytes());
            assertEquals(2, raw.connections(), "first attempt truncated, second succeeded");
            assertArrayEquals(CONTENT, Files.readAllBytes(r.target()));
            assertNoTempFiles();
        }
    }

    @Test
    void stalledBodyIsAbortedAndRetried() throws Exception {
        try (RawHttpServer raw = new RawHttpServer(CONTENT, List.of(RawHttpServer.Behaviour.STALL, RawHttpServer.Behaviour.FULL))) {
            final Downloader impatient = new Downloader(new JdkHttpTransport("test"), Sleeper.NONE, 3, 2, Duration.ofMillis(400));
            final DownloadRequest r = new DownloadRequest(raw.url(), tmp.resolve("stall.jar"), CONTENT.length,
                Checksum.sha1(Checksums.hex(CONTENT, HashAlgorithm.SHA1)), "stall");
            final long start = System.nanoTime();
            final List<Exception> retryErrors = new ArrayList<>();
            final DownloadResult result = impatient.download(r, new DownloadProgressListener() {
                @Override
                public void onRetry(final DownloadRequest request, final int attempt, final Exception error) {
                    retryErrors.add(error);
                }
            }, CancellationToken.NONE);
            assertEquals(CONTENT.length, result.bytes());
            assertEquals(1, retryErrors.size());
            assertTrue(retryErrors.get(0).getMessage().contains("No data received"), retryErrors.get(0).getMessage());
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 30, "stall detected quickly");
            assertNoTempFiles();
        }
    }

    @Test
    void permanentStallFailsAfterAllAttempts() throws Exception {
        try (RawHttpServer raw = new RawHttpServer(CONTENT, List.of(RawHttpServer.Behaviour.STALL))) {
            final Downloader impatient = new Downloader(new JdkHttpTransport("test"), Sleeper.NONE, 2, 2, Duration.ofMillis(300));
            final DownloadRequest r = new DownloadRequest(raw.url(), tmp.resolve("stall2.jar"), CONTENT.length, null, "stall2");
            final IOException e = assertThrows(IOException.class, () -> impatient.download(r, DownloadProgressListener.NONE, CancellationToken.NONE));
            assertTrue(e.getMessage().contains("No data received"));
            assertEquals(2, raw.connections());
            assertFalse(Files.exists(r.target()));
            assertNoTempFiles();
        }
    }

    @Test
    void givesUpAfterThreeServerErrors() {
        server.failFirst("files/lib.jar", 5, 500);
        final HttpStatusException e = assertThrows(HttpStatusException.class,
            () -> downloader.download(request("i.jar", null, -1), DownloadProgressListener.NONE, CancellationToken.NONE));
        assertEquals(500, e.status());
        assertEquals(3, server.hits("files/lib.jar"));
    }

    @Test
    void notFoundIsNotRetried() {
        final DownloadRequest r = new DownloadRequest(server.url("files/missing.jar"), tmp.resolve("j.jar"), -1, null, "j");
        final HttpStatusException e = assertThrows(HttpStatusException.class,
            () -> downloader.download(r, DownloadProgressListener.NONE, CancellationToken.NONE));
        assertEquals(404, e.status());
        assertEquals(1, server.hits("files/missing.jar"));
        assertFalse(Files.exists(r.target()));
    }

    @Test
    void downloadAllRunsInParallelAndStopsOnFirstFailure() throws Exception {
        for (int i = 0; i < 12; i++) {
            server.add("many/" + i, ("file " + i).getBytes(StandardCharsets.UTF_8));
        }
        final List<DownloadRequest> ok = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            final byte[] c = ("file " + i).getBytes(StandardCharsets.UTF_8);
            ok.add(new DownloadRequest(server.url("many/" + i), tmp.resolve("many/" + i), c.length, Checksum.sha1(Checksums.hex(c, HashAlgorithm.SHA1)), "f" + i));
        }
        final List<DownloadResult> results = downloader.downloadAll(ok, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(12, results.size());
        assertTrue(results.stream().noneMatch(DownloadResult::skipped));

        final List<DownloadRequest> withBad = new ArrayList<>(ok);
        withBad.add(3, new DownloadRequest(server.url("many/missing"), tmp.resolve("many/missing"), -1, null, "bad"));
        final IOException e = assertThrows(IOException.class, () -> downloader.downloadAll(withBad, DownloadProgressListener.NONE, CancellationToken.NONE));
        assertTrue(e instanceof HttpStatusException);
    }

    @Test
    void cancellationStopsTransfer() {
        final CancellationToken token = new CancellationToken();
        token.cancel();
        assertThrows(CancellationException.class,
            () -> downloader.download(request("k.jar", null, -1), DownloadProgressListener.NONE, token));
        assertFalse(Files.exists(tmp.resolve("k.jar")));
        CancellationToken.NONE.cancel();
        assertFalse(CancellationToken.NONE.isCancelled(), "the shared NONE token can never be cancelled");
    }

    @Test
    void fetchStringRetriesAndFailsOn404() throws Exception {
        server.addText("text/hello.txt", "hello");
        server.failFirst("text/hello.txt", 1, 502);
        assertEquals("hello", downloader.fetchString(server.url("text/hello.txt")));
        final HttpStatusException e = assertThrows(HttpStatusException.class, () -> downloader.fetchString(server.url("text/none")));
        assertEquals(404, e.status());
    }

    @Test
    void userAgentIsSent() throws Exception {
        downloader.fetchString(server.url("files/lib.jar"));
        assertEquals(1, server.hits("files/lib.jar"));
    }

    private void assertNoTempFiles() throws IOException {
        try (Stream<Path> files = Files.walk(tmp)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), "temp files must be cleaned up");
        }
    }
}
