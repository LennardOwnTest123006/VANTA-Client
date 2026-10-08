package dev.vanta.launcher.core.net;

import dev.vanta.launcher.core.util.Sleeper;
import dev.vanta.launcher.testutil.FakeTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The per-request exchange timeout (hours for a multi-gigabyte model) reaches the transport; the default stays 10 minutes. */
class DownloadRequestTimeoutTest {

    @TempDir
    Path tmp;

    @Test
    void theRequestTimeoutReachesTheTransportAndDefaultsToTenMinutes() throws Exception {
        final byte[] body = "model bytes".getBytes(StandardCharsets.UTF_8);
        final FakeTransport transport = new FakeTransport().on("/model.gguf", spec -> HttpResult.of(200, Map.of(), body));
        final Downloader downloader = new Downloader(transport, Sleeper.NONE, 1, 1);
        final Checksum sha = Checksum.sha256(Checksums.hex(body, HashAlgorithm.SHA256));

        final DownloadRequest plain = new DownloadRequest(URI.create("https://files.example/model.gguf"), tmp.resolve("a.gguf"), body.length, sha, "model");
        assertEquals(HttpRequestSpec.DEFAULT_TIMEOUT, plain.timeout());
        assertEquals(Duration.ofMinutes(10), plain.timeout());
        downloader.download(plain, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(Duration.ofMinutes(10), transport.requests().get(0).readTimeout());

        final DownloadRequest longOne = plain.withTimeout(Duration.ofHours(6)).withDescription("Local AI model");
        assertEquals(Duration.ofHours(6), longOne.timeout());
        assertEquals("Local AI model", longOne.description());
        Files.deleteIfExists(tmp.resolve("a.gguf"));
        downloader.download(longOne, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(Duration.ofHours(6), transport.requests().get(1).readTimeout(), "the long timeout is what the transport sees");
        assertArrayEquals(body, Files.readAllBytes(tmp.resolve("a.gguf")));

        final DownloadRequest sixArgs = new DownloadRequest(plain.url(), plain.target(), -1, null, null, Duration.ZERO);
        assertEquals(HttpRequestSpec.DEFAULT_TIMEOUT, sixArgs.timeout(), "a zero timeout falls back to the default");
        assertEquals(HttpRequestSpec.DEFAULT_TIMEOUT, DownloadRequest.of(plain.url(), plain.target(), 1, sha).timeout());
    }
}
