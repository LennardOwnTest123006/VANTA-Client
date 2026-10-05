package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.auth.AuthException;
import dev.vanta.launcher.core.auth.AuthNotConfiguredException;
import dev.vanta.launcher.core.auth.NoGameOwnershipException;
import dev.vanta.launcher.core.auth.XboxAuthException;
import dev.vanta.launcher.core.install.InstallException;
import dev.vanta.launcher.core.install.InstallStep;
import dev.vanta.launcher.core.install.InsufficientDiskSpaceException;
import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.java.UnsafeArchiveException;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.ui.Messages;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.ZoneOffset;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorMessagesTest {

    private final Messages m = Messages.english();
    private final Formats f = new Formats(m, ZoneOffset.UTC);
    private final ErrorMessages errors = new ErrorMessages(m, f);

    @Test
    void authErrors() {
        assertEquals(m.get("error.notConfigured"), errors.describe(new AuthNotConfiguredException()));
        assertEquals(m.get("error.auth.noXbox"), errors.describe(new XboxAuthException(XboxAuthException.XERR_NO_XBOX_ACCOUNT, null)));
        assertEquals(m.get("error.auth.region"), errors.describe(new XboxAuthException(XboxAuthException.XERR_REGION_UNAVAILABLE, null)));
        assertEquals(m.get("error.auth.adult"), errors.describe(new XboxAuthException(XboxAuthException.XERR_ADULT_VERIFICATION, null)));
        assertEquals(m.get("error.auth.child"), errors.describe(new XboxAuthException(XboxAuthException.XERR_CHILD_ACCOUNT, null)));
        assertEquals("Xbox Live refused the sign-in (XErr 42).", errors.describe(new XboxAuthException(42, "raw")));
        assertEquals("Xbox Live refused the sign-in.", errors.describe(new XboxAuthException(0, "")));
        assertEquals(m.get("error.auth.noGame"), errors.describe(new NoGameOwnershipException("x")));
        assertEquals(m.get("error.auth.declined"), errors.describe(new AuthException("Sign-in was declined in the browser.")));
        assertEquals(m.get("error.auth.expired"), errors.describe(new AuthException("The sign-in code expired before it was used. Start the sign-in again.")));
        assertEquals("Sign-in failed: Minecraft services did not return an access token.",
            errors.describe(new AuthException("Minecraft services did not return an access token.")));
    }

    @Test
    void installAndNetworkErrors() {
        assertEquals("Not enough free disk space: 2.7 GB needed, 943.7 MB available.",
            errors.describe(new InsufficientDiskSpaceException(2_684_354_560L, 943_718_400L)));
        assertEquals(m.get("error.integrity"), errors.describe(new IntegrityException(null, "mismatch")));
        assertEquals(m.get("error.unsafeArchive"), errors.describe(new UnsafeArchiveException("../evil")));
        assertEquals("The server answered with HTTP 503.", errors.describe(new HttpStatusException(503, URI.create("https://x/y"))));
        assertEquals(m.get("error.network.offline"), errors.describe(new UnknownHostException("piston-meta.mojang.com")));
        assertEquals(m.get("error.network.offline"), errors.describe(new ConnectException("refused")));
        assertEquals(m.get("error.network.timeout"), errors.describe(new HttpTimeoutException("slow")));
        assertEquals("No public release is available yet: VANTA Client 1.0.0 has no public download yet",
            errors.describe(new NotPublishedException("client", "1.0.0", "VANTA Client 1.0.0 has no public download yet")));
        final InstallException wrapped = new InstallException(InstallStep.LIBRARIES, "Downloading libraries failed: x", new UnknownHostException("h"));
        assertEquals("Downloading libraries failed. " + m.get("error.network.offline"), errors.describe(wrapped));
        assertEquals("Fetching version manifest failed. boom",
            errors.describe(new InstallException(InstallStep.MANIFEST, "boom")));
        assertEquals(m.get("error.cancelled"), errors.describe(new CancellationException()));
        assertEquals(m.get("error.interrupted"), errors.describe(new InterruptedException()));
        assertEquals(m.get("error.network.offline"), errors.describe(new ExecutionException(new ConnectException("x"))));
        assertEquals("Unexpected error: NullPointerException", errors.describe(new NullPointerException()));
        assertTrue(errors.describe(null).startsWith("Unexpected error"));
    }

    @Test
    void formats() {
        // Decimal units, one decimal place, like the website and the release notes (1 MB = 1,000,000 bytes).
        assertEquals("1.4 MB", f.bytes(1_400_000L));
        assertEquals("66.9 MB", f.bytes(66_912_345L));
        assertEquals("1.5 GB", f.bytes(1_503_238_553L));
        assertEquals("242.2 MB", f.bytes(242_221_056L));
        assertEquals("4.2 MB", f.bytes(4_194_304L));
        assertEquals("12.3 kB", f.bytes(12_288));
        assertEquals("1.0 kB", f.bytes(1_000));
        assertEquals("999 B", f.bytes(999));
        assertEquals("512 B", f.bytes(512));
        assertEquals("0 B", f.bytes(0));
        assertEquals(m.get("common.unknown"), f.bytes(-1));
        assertEquals("16 GB", f.memory(16384));
        assertEquals("6 GB", f.memory(6144));
        assertEquals("2.5 GB", f.memory(2560));
        assertEquals("512 MB", f.memory(512));
        assertEquals("1:05", f.countdown(java.time.Duration.ofSeconds(65)));
        assertEquals("0:00", f.countdown(java.time.Duration.ofSeconds(-5)));
        assertEquals("12:00:00", f.time(java.time.Instant.parse("2026-10-04T12:00:00Z")));
        assertEquals("not-a-date", f.isoDateTime("not-a-date"));
        assertEquals(m.get("common.unknown"), f.isoDateTime(""));
        assertTrue(f.isoDateTime("2026-10-03T18:42:00Z").contains("2026"));
    }

    @Test
    void releasesOfficialLauncherAndProxyErrors() {
        assertEquals(m.format("error.releasesUrl.invalid", "ftp://x"),
            errors.describe(new dev.vanta.launcher.core.install.ReleasesNotConfiguredException("ftp://x", "invalid")));
        assertEquals(m.get("error.releasesUrl.missing"), errors.describe(new dev.vanta.launcher.core.install.ReleasesNotConfiguredException("", "none")));
        final java.nio.file.Path dir = java.nio.file.Path.of("dotminecraft");
        assertEquals(m.format("error.official.notFound", dir.toString()),
            errors.describe(new dev.vanta.launcher.core.install.OfficialLauncherNotFoundException(dir)));
        assertEquals(m.format("error.network.proxy", "Tunnel failed, got: 403"), errors.describe(new java.io.IOException("Tunnel failed, got: 403")));
        assertEquals("Downloading Fabric API failed. " + m.format("error.network.proxy", "Tunnel failed, got: 403"),
            errors.describe(new InstallException(InstallStep.FABRIC_API, "x", new java.io.IOException("wrapped", new java.io.IOException("Tunnel failed, got: 403")))));
        assertEquals(m.format("error.network.tls", "PKIX path building failed"),
            errors.describe(new javax.net.ssl.SSLHandshakeException("PKIX path building failed")));
        assertEquals(m.get("error.network.offline"), errors.describe(new ConnectException("Connection refused")));
    }
}
