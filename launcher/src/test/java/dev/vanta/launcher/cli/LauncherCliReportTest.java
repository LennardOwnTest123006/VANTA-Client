package dev.vanta.launcher.cli;

import dev.vanta.launcher.core.auth.AuthException;
import dev.vanta.launcher.core.install.InstallException;
import dev.vanta.launcher.core.install.InstallStep;
import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.install.OfficialLauncherNotFoundException;
import dev.vanta.launcher.core.install.ReleasesNotConfiguredException;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.net.NetworkErrors;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit code mapping of {@link LauncherCli#report}: every transport failure, including proxy tunnel refusals that the
 * JDK reports as a plain {@link IOException}, is a network failure; configuration problems are "not configured".
 */
class LauncherCliReportTest {

    private record Reported(ExitCode code, String err) {
    }

    private static Reported report(final Exception e) {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ExitCode code;
        try (PrintStream err = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            code = LauncherCli.report(e, err);
        }
        return new Reported(code, buffer.toString(StandardCharsets.UTF_8));
    }

    private static InstallException wrapped(final InstallStep step, final IOException cause) {
        return new InstallException(step, step.label() + " failed (https://meta.fabricmc.net/v2/x): " + cause.getMessage(), cause);
    }

    @Test
    void everyTransportFailureIsANetworkFailure() {
        final List<Exception> network = List.of(
            new IOException("Tunnel failed, got: 403"),
            new IOException("Unable to tunnel through proxy. Proxy returns \"HTTP/1.1 403 Forbidden\""),
            new ConnectException("Connection refused"),
            new ConnectException(),
            new NoRouteToHostException("No route to host"),
            new UnknownHostException("meta.fabricmc.net"),
            new SSLHandshakeException("PKIX path building failed"),
            new HttpConnectTimeoutException("HTTP connect timed out"),
            new HttpTimeoutException("request timed out"),
            new SocketTimeoutException("Read timed out"),
            new java.net.SocketException("Connection reset"),
            new HttpStatusException(502, URI.create("https://maven.fabricmc.net/x.jar")),
            new IOException("Download failed", new ConnectException("Connection refused")),
            new IOException("outer", new IOException("middle", new IOException("Tunnel failed, got: 407"))),
            wrapped(InstallStep.FABRIC_PROFILE, new IOException("Tunnel failed, got: 403")),
            wrapped(InstallStep.FABRIC_API, new UnknownHostException("maven.fabricmc.net")),
            wrapped(InstallStep.LIBRARIES, new SSLHandshakeException("handshake_failure")));
        for (Exception e : network) {
            final Reported r = report(e);
            assertEquals(ExitCode.NETWORK, r.code(), e.toString());
            assertTrue(r.err().contains("network error: "), r.err());
            assertTrue(r.err().contains("Check the internet connection, proxy and firewall settings"), r.err());
        }
    }

    @Test
    void networkMessagesNameTheStepTheUrlAndTheProblem() {
        final Reported proxy = report(wrapped(InstallStep.FABRIC_PROFILE, new IOException("Tunnel failed, got: 403")));
        assertTrue(proxy.err().contains("Fetching Fabric Loader profile failed (https://meta.fabricmc.net/v2/x): Tunnel failed, got: 403"), proxy.err());
        assertTrue(proxy.err().contains("network error: the HTTPS proxy refused the connection (Tunnel failed, got: 403)"), proxy.err());
        assertTrue(report(new SSLHandshakeException("PKIX")).err().contains("the secure (TLS) connection failed (PKIX)"));
        assertTrue(report(new UnknownHostException("maven.fabricmc.net")).err().contains("unknown host maven.fabricmc.net"));
        assertTrue(report(new HttpConnectTimeoutException("HTTP connect timed out")).err().contains("the connection timed out"));
        assertEquals(NetworkErrors.Kind.PROXY, NetworkErrors.classify(new ConnectException("Unable to tunnel through proxy")).orElseThrow());
        assertEquals(NetworkErrors.Kind.CONNECT, NetworkErrors.classify(new ConnectException("Connection refused")).orElseThrow());
    }

    @Test
    void nonNetworkFailuresKeepTheirCodes() {
        assertEquals(ExitCode.FAILURE, report(new IOException("No space left on device")).code());
        assertFalse(report(new IOException("No space left on device")).err().contains("network error"));
        assertEquals(ExitCode.FAILURE, report(new IOException("Cannot parse settings.json")).code());
        assertEquals(ExitCode.NOT_CONFIGURED, report(new ReleasesNotConfiguredException("ftp://x", "not a valid http(s) URL")).code());
        assertEquals(ExitCode.NOT_CONFIGURED, report(wrapped(InstallStep.VANTA_CLIENT, new ReleasesNotConfiguredException("", "none"))).code());
        assertEquals(ExitCode.NOT_CONFIGURED, report(new OfficialLauncherNotFoundException(Path.of("dotminecraft"))).code());
        assertTrue(report(new OfficialLauncherNotFoundException(Path.of("dotminecraft"))).err()
            .contains("Start the Minecraft Launcher once, then try again"));
        assertEquals(ExitCode.NOT_PUBLISHED, report(new NotPublishedException("client", "1.0.0", "no public download yet")).code());
        assertEquals(ExitCode.NOT_PUBLISHED, report(wrapped(InstallStep.VANTA_CLIENT,
            new NotPublishedException("client", "", "No release manifest at x (HTTP 404)"))).code());
        assertEquals(ExitCode.INTEGRITY, report(new IntegrityException(null, "SHA-256 mismatch")).code());
        assertEquals(ExitCode.AUTH, report(new AuthException("declined")).code());
    }
}
