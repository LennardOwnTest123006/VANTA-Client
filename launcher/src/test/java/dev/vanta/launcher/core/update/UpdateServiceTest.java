package dev.vanta.launcher.core.update;

import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.HashAlgorithm;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.net.JdkHttpTransport;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.core.util.Sleeper;
import dev.vanta.launcher.testutil.FakeHttpServer;
import dev.vanta.launcher.testutil.FakeWorld;
import dev.vanta.launcher.testutil.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {

    @TempDir
    Path tmp;
    private FakeHttpServer server;
    private Downloader downloader;
    private LauncherPaths paths;
    private VantaClientService clientService;
    private byte[] installer;

    @BeforeEach
    void start() throws IOException {
        server = new FakeHttpServer();
        downloader = new Downloader(new JdkHttpTransport("test"), Sleeper.NONE, 2, 2);
        paths = new LauncherPaths(tmp.resolve("data"));
        clientService = new VantaClientService(downloader, paths, () -> server.url("rel/").toString());
        installer = FakeWorld.synthetic("msi installer", 5_000);
        final com.google.gson.JsonObject launcher = Fixtures.json("release/launcher-latest.json").getAsJsonObject();
        for (com.google.gson.JsonElement f : launcher.getAsJsonArray("files")) {
            final com.google.gson.JsonObject file = f.getAsJsonObject();
            file.addProperty("downloadUrl", server.url("rel/" + file.get("name").getAsString()).toString());
            file.addProperty("sha256", Checksums.hex(installer, HashAlgorithm.SHA256));
            file.addProperty("size", installer.length);
            server.add("rel/" + file.get("name").getAsString(), installer);
        }
        server.addJson("rel/launcher-latest.json", launcher.toString());
        server.addJson("rel/client-latest.json", Fixtures.read("release/client-unpublished.json"));
    }

    @AfterEach
    void stop() {
        downloader.close();
        server.close();
    }

    private UpdateService service(final String current, final OsInfo os) {
        return new UpdateService(downloader, paths, () -> server.url("rel/").toString(), SemVer.parse(current), os, clientService);
    }

    @Test
    void notConfiguredWithoutBaseUrl() {
        final UpdateService unconfigured = new UpdateService(downloader, paths, () -> "", SemVer.of(1, 0, 0), new OsInfo("linux", "x64", ""), clientService);
        assertFalse(unconfigured.isConfigured());
        assertThrows(NotPublishedException.class, unconfigured::checkLauncher);
    }

    @Test
    void launcherUpdateDetectedWithPlatformFile() throws Exception {
        final UpdateService windows = service("1.0.0", new OsInfo("windows", "x64", "10.0"));
        final UpdateInfo update = windows.checkLauncher().orElseThrow();
        assertEquals("launcher", update.product());
        assertEquals(SemVer.parse("1.1.0"), update.latestVersion());
        assertEquals(SemVer.parse("1.0.0"), update.currentVersion());
        assertEquals("VANTA-Launcher-1.1.0.msi", update.file().name());
        assertTrue(update.isNewer());
        assertTrue(update.isDownloadable());
        assertEquals("website/content/changelog/launcher-1.1.0.md", update.changelog());

        final UpdateService linux = service("1.0.0", new OsInfo("linux", "x64", ""));
        assertEquals("vanta-launcher-1.1.0-all.jar", linux.checkLauncher().orElseThrow().file().name());

        assertTrue(service("1.1.0", new OsInfo("linux", "x64", "")).checkLauncher().isEmpty(), "same version is up to date");
        assertTrue(service("1.2.0", new OsInfo("linux", "x64", "")).checkLauncher().isEmpty(), "newer local build is up to date");
        assertTrue(service("1.1.0-beta.1", new OsInfo("linux", "x64", "")).checkLauncher().isPresent(), "release beats its pre-release");
    }

    @Test
    void downloadVerifyAndPrepareInstaller() throws Exception {
        final UpdateService windows = service("1.0.0", new OsInfo("windows", "x64", "10.0"));
        final UpdateInfo update = windows.checkLauncher().orElseThrow();
        final Path file = windows.downloadUpdate(update, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(paths.updatesCacheDir().resolve("1.1.0-VANTA-Launcher-1.1.0.msi"), file);
        assertEquals(Checksums.hex(installer, HashAlgorithm.SHA256), Checksums.sha256Hex(file));
        assertEquals(file, windows.prepareInstaller(update));

        Files.writeString(file, "tampered after download");
        assertThrows(IntegrityException.class, () -> windows.prepareInstaller(update));
        assertFalse(Files.exists(file), "tampered installer deleted");
        assertThrows(IOException.class, () -> windows.prepareInstaller(update));
    }

    @Test
    void mismatchingChecksumInManifestFailsDownload() throws Exception {
        final ReleaseManifest manifest = Json.parse(Fixtures.read("release/launcher-latest.json"), ReleaseManifest.class);
        final ReleaseManifest.ReleaseFile file = new ReleaseManifest.ReleaseFile("VANTA-Launcher-1.1.0.msi",
            server.url("rel/VANTA-Launcher-1.1.0.msi").toString(), installer.length, "0".repeat(64));
        final UpdateInfo bad = new UpdateInfo("launcher", SemVer.of(1, 0, 0), SemVer.parse("1.1.0"), "", file, file.sha256(), manifest);
        assertThrows(IntegrityException.class, () -> service("1.0.0", new OsInfo("windows", "x64", "")).downloadUpdate(bad, DownloadProgressListener.NONE, CancellationToken.NONE));
        final ReleaseManifest.ReleaseFile noHash = new ReleaseManifest.ReleaseFile("x.msi", server.url("rel/VANTA-Launcher-1.1.0.msi").toString(), 1, "");
        final UpdateInfo unverifiable = new UpdateInfo("launcher", SemVer.of(1, 0, 0), SemVer.parse("1.1.0"), "", noHash, "", manifest);
        assertThrows(IntegrityException.class, () -> service("1.0.0", new OsInfo("windows", "x64", "")).downloadUpdate(unverifiable, DownloadProgressListener.NONE, CancellationToken.NONE));
    }

    @Test
    void clientUpdateAnnouncedButNotDownloadable() throws Exception {
        final UpdateService s = service("1.0.0", new OsInfo("linux", "x64", ""));
        final UpdateInfo update = s.checkClient(Optional.of(SemVer.of(1, 0, 0))).orElseThrow();
        assertEquals(SemVer.parse("1.1.0"), update.latestVersion());
        assertFalse(update.isDownloadable());
        final NotPublishedException e = assertThrows(NotPublishedException.class, () -> s.downloadUpdate(update, DownloadProgressListener.NONE, CancellationToken.NONE));
        assertTrue(e.getMessage().contains("not downloadable yet"));
        assertTrue(s.checkClient(Optional.of(SemVer.parse("1.1.0"))).isEmpty());
        assertTrue(s.checkClient(Optional.empty()).isPresent(), "no client installed: any version is an update");
    }

    @Test
    void wrongProductOrVersionInManifestIsRejected() throws Exception {
        final UpdateService s = service("1.0.0", new OsInfo("linux", "x64", ""));
        final ReleaseManifest client = Json.parse(Fixtures.read("release/client-latest.json"), ReleaseManifest.class);
        assertThrows(IOException.class, () -> s.compare("launcher", SemVer.of(1, 0, 0), client));
        final ReleaseManifest badVersion = new ReleaseManifest(1, "launcher", "latest", "1.21.11", "0.19.5", "x", 21, "", "stable", java.util.List.of(), "");
        assertThrows(IOException.class, () -> s.compare("launcher", SemVer.of(1, 0, 0), badVersion));
        assertThrows(IOException.class, () -> s.installClientUpdate(s.checkLauncher().orElseThrow(), DownloadProgressListener.NONE, CancellationToken.NONE));
    }

    @Test
    void clientUpdateInstallGoesThroughClientService() throws Exception {
        final byte[] jar = FakeWorld.synthetic("client 1.1.0", 900);
        server.add("rel/vanta-client-1.1.0.jar", jar);
        final ReleaseManifest manifest = new ReleaseManifest(1, "client", "1.1.0", "1.21.11", "0.19.5", "0.141.6+1.21.11", 21, "2026-11-01", "stable",
            java.util.List.of(new ReleaseManifest.ReleaseFile("vanta-client-1.1.0.jar", server.url("rel/vanta-client-1.1.0.jar").toString(), jar.length,
                Checksums.hex(jar, HashAlgorithm.SHA256))), "notes");
        server.addJson("rel/client-latest.json", Json.toJson(manifest));
        final UpdateService s = service("1.0.0", new OsInfo("linux", "x64", ""));
        final UpdateInfo update = s.checkClient(Optional.of(SemVer.of(1, 0, 0))).orElseThrow();
        final Path active = s.installClientUpdate(update, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(paths.modsDir().resolve("vanta-client-1.1.0.jar"), active);
        assertEquals(1, s.listClientVersions().size());
        assertEquals(active, s.rollbackClient("1.1.0"));
        assertEquals(SemVer.of(1, 0, 0), s.currentLauncher());
    }
}
