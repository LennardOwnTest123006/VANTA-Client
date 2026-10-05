package dev.vanta.launcher.core.update;

import dev.vanta.launcher.core.install.InstalledClient;
import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.install.ReleasesNotConfiguredException;
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
import dev.vanta.launcher.core.util.LauncherPackaging;
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

    /** A packaged launcher (installer / app image): the classic self-update files. */
    private UpdateService service(final String current, final OsInfo os) {
        return service(current, os, LauncherPackaging.packaged(tmp.resolve("app")));
    }

    private UpdateService service(final String current, final OsInfo os, final LauncherPackaging packaging) {
        return new UpdateService(downloader, paths, () -> server.url("rel/").toString(), SemVer.parse(current), os, packaging, clientService);
    }

    /** Puts {@code vanta-client-<version>.jar} into mods/ and returns the installed state the service reads. */
    private Optional<InstalledClient> installed(final String version) throws IOException {
        Files.createDirectories(paths.modsDir());
        Files.write(paths.modsDir().resolve("vanta-client-" + version + ".jar"), FakeWorld.synthetic("client " + version, 100));
        final Optional<InstalledClient> client = clientService.installedClient();
        assertEquals(version, client.orElseThrow().version());
        return client;
    }

    @Test
    void notConfiguredWithoutUsableBaseUrl() {
        for (String url : java.util.List.of("", "  ", "file:///tmp/releases", "releases.example/vanta")) {
            final UpdateService unconfigured = new UpdateService(downloader, paths, () -> url, SemVer.of(1, 0, 0), new OsInfo("linux", "x64", ""),
                LauncherPackaging.PLAIN_JAR, clientService);
            assertFalse(unconfigured.isConfigured(), url);
            assertThrows(ReleasesNotConfiguredException.class, unconfigured::checkLauncher, url);
        }
    }

    @Test
    void missingManifestMeansNotPublished() throws Exception {
        final UpdateService s = new UpdateService(downloader, paths, () -> server.url("nothing-here/").toString(), SemVer.of(1, 0, 0),
            new OsInfo("linux", "x64", ""), LauncherPackaging.PLAIN_JAR, clientService);
        assertTrue(s.isConfigured());
        final NotPublishedException e = assertThrows(NotPublishedException.class, s::checkLauncher);
        assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());
        assertEquals("launcher", e.product());
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

        assertEquals(UpdateInfo.AssetKind.INSTALLER, update.assetKind());
        assertEquals("", update.releasePage(), "files served from the fake server are not GitHub release assets");

        final UpdateInfo linux = service("1.0.0", new OsInfo("linux", "x64", "")).checkLauncher().orElseThrow();
        assertEquals("VANTA-Launcher-1.1.0-linux-x64.tar.gz", linux.file().name());
        assertEquals(UpdateInfo.AssetKind.ARCHIVE, linux.assetKind());
        assertTrue(linux.isDownloadable());

        final UpdateInfo macArm = service("1.0.0", new OsInfo("osx", "arm64", "15.0")).checkLauncher().orElseThrow();
        assertEquals("vanta-launcher-1.1.0-macos-aarch64-all.jar", macArm.file().name());
        assertEquals(UpdateInfo.AssetKind.JAR, macArm.assetKind());

        assertTrue(service("1.1.0", new OsInfo("linux", "x64", "")).checkLauncher().isEmpty(), "same version is up to date");
        assertTrue(service("1.2.0", new OsInfo("linux", "x64", "")).checkLauncher().isEmpty(), "newer local build is up to date");
        assertTrue(service("1.1.0-beta.1", new OsInfo("linux", "x64", "")).checkLauncher().isPresent(), "release beats its pre-release");
    }

    @Test
    void intelMacHasNoAssetAndGetsTheReleasePage() throws Exception {
        // The fixture as published: GitHub release asset URLs, from which the release page is derived.
        server.addJson("rel/launcher-latest.json", Fixtures.read("release/launcher-latest.json"));
        final UpdateService intel = service("1.0.0", new OsInfo("osx", "x64", "13.6"));
        final UpdateInfo update = intel.checkLauncher().orElseThrow();
        assertEquals(SemVer.parse("1.1.0"), update.latestVersion());
        assertFalse(update.hasPlatformAsset());
        assertFalse(update.isDownloadable());
        assertEquals(UpdateInfo.AssetKind.NONE, update.assetKind());
        assertEquals("https://github.com/vanta-client/vanta/releases/tag/launcher-v1.1.0", update.releasePage());
        assertEquals(java.net.URI.create(update.releasePage()), update.releasePageUri().orElseThrow());
        final NotPublishedException e = assertThrows(NotPublishedException.class,
            () -> intel.downloadUpdate(update, DownloadProgressListener.NONE, CancellationToken.NONE));
        assertTrue(e.getMessage().contains("has no download for macOS (x64)"), e.getMessage());
        assertTrue(e.getMessage().contains("releases/tag/launcher-v1.1.0"), e.getMessage());
    }

    @Test
    void linuxArchiveDownloadsAndVerifies() throws Exception {
        final UpdateService linux = service("1.0.0", new OsInfo("linux", "x64", "6.8"));
        final UpdateInfo update = linux.checkLauncher().orElseThrow();
        final Path file = linux.downloadUpdate(update, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(paths.updatesCacheDir().resolve("1.1.0-VANTA-Launcher-1.1.0-linux-x64.tar.gz"), file);
        assertEquals(file, linux.prepareInstaller(update));
    }

    @Test
    void clientUpdateNeverPicksTheBundleOrFabricApi() throws Exception {
        final byte[] jar = FakeWorld.synthetic("client 1.2.0", 900);
        server.add("rel/vanta-client-1.2.0.jar", jar);
        final ReleaseManifest manifest = new ReleaseManifest(1, "client", "1.2.0", "1.21.11", "0.19.5", "0.141.6+1.21.11", 21, "2026-12-01", "stable",
            java.util.List.of(
                new ReleaseManifest.ReleaseFile("fabric-api-0.141.6+1.21.11.jar", server.url("rel/fabric-api.jar").toString(), 5, "ab".repeat(32)),
                new ReleaseManifest.ReleaseFile("vanta-client-1.2.0-mods.zip", server.url("rel/mods.zip").toString(), 5, "cd".repeat(32)),
                new ReleaseManifest.ReleaseFile("vanta-client-1.2.0.jar", server.url("rel/vanta-client-1.2.0.jar").toString(), jar.length,
                    Checksums.hex(jar, HashAlgorithm.SHA256))), "notes");
        server.addJson("rel/client-latest.json", Json.toJson(manifest));
        final UpdateService s = service("1.0.0", new OsInfo("windows", "x64", "10.0"));
        final UpdateInfo update = s.checkClient(installed("1.0.0")).update().orElseThrow();
        assertEquals("vanta-client-1.2.0.jar", update.file().name());
        assertEquals(paths.modsDir().resolve("vanta-client-1.2.0.jar"), s.installClientUpdate(update, DownloadProgressListener.NONE, CancellationToken.NONE));
    }

    @Test
    void preReleaseManifestIsNeverOfferedAsAnUpdate() throws Exception {
        final ReleaseManifest beta = new ReleaseManifest(1, "client", "1.3.0-beta.1", "1.21.11", "0.19.5", "0.141.6+1.21.11", 21,
            "2026-12-01", "beta", java.util.List.of(new ReleaseManifest.ReleaseFile("vanta-client-1.3.0-beta.1.jar",
                server.url("rel/beta.jar").toString(), 5, "ab".repeat(32))), "notes");
        server.addJson("rel/client-latest.json", Json.toJson(beta));
        final UpdateService s = service("1.0.0", new OsInfo("windows", "x64", "10.0"));
        assertEquals(Optional.empty(), s.checkClient(installed("1.0.0")).update());
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
        final UpdateService.ClientCheck check = s.checkClient(installed("1.0.0"));
        assertFalse(check.downloadable());
        final UpdateInfo update = check.update().orElseThrow();
        assertEquals(SemVer.parse("1.1.0"), update.latestVersion());
        assertFalse(update.isDownloadable());
        final NotPublishedException e = assertThrows(NotPublishedException.class, () -> s.downloadUpdate(update, DownloadProgressListener.NONE, CancellationToken.NONE));
        assertTrue(e.getMessage().contains("not downloadable yet"));
        Files.delete(paths.modsDir().resolve("vanta-client-1.0.0.jar"));
        assertTrue(s.checkClient(installed("1.1.0")).update().isEmpty());
    }

    @Test
    void noInstalledClientMeansNoUpdateOnlyTheLatestRelease() throws Exception {
        final byte[] jar = FakeWorld.synthetic("client 1.0.1", 900);
        server.add("rel/vanta-client-1.0.1.jar", jar);
        final ReleaseManifest manifest = new ReleaseManifest(1, "client", "1.0.1", "1.21.11", "0.19.5", "0.141.6+1.21.11", 21, "2026-10-05", "stable",
            java.util.List.of(new ReleaseManifest.ReleaseFile("vanta-client-1.0.1.jar", server.url("rel/vanta-client-1.0.1.jar").toString(), jar.length,
                Checksums.hex(jar, HashAlgorithm.SHA256))), "notes");
        server.addJson("rel/client-latest.json", Json.toJson(manifest));
        final UpdateService s = service("1.0.0", new OsInfo("linux", "x64", ""));

        // Fresh launcher: no instance.json, nothing in mods/.
        final UpdateService.ClientCheck fresh = s.checkClient();
        assertFalse(fresh.isInstalled());
        assertEquals(SemVer.parse("1.0.1"), fresh.latest());
        assertTrue(fresh.downloadable());
        assertTrue(fresh.update().isEmpty(), "nothing installed, nothing to update");
        assertThrows(IllegalArgumentException.class, () -> new UpdateService.ClientCheck(Optional.empty(), SemVer.of(1, 0, 1), true,
            Optional.of(s.compare("client", SemVer.of(0, 0, 0), manifest).orElseThrow())), "an update needs an installed client");

        // An update cannot be forced into mods/ of a launcher that has no client (the 1.0.0 bug).
        final UpdateInfo forced = s.compare("client", SemVer.of(0, 0, 0), manifest).orElseThrow();
        final IOException refused = assertThrows(IOException.class,
            () -> s.installClientUpdate(forced, DownloadProgressListener.NONE, CancellationToken.NONE));
        assertTrue(refused.getMessage().contains("nothing to update"), refused.getMessage());
        assertFalse(Files.exists(paths.modsDir().resolve("vanta-client-1.0.1.jar")));

        // instance.json without a client (--without-client): still not installed.
        Files.createDirectories(paths.instanceDir());
        Json.write(paths.instanceFile(), new dev.vanta.launcher.core.model.InstanceInfo(1, "vanta-1.21.11", "1.21.11", "0.19.5",
            "0.141.6+1.21.11", "", "", "1.21.11", "fabric-loader-0.19.5-1.21.11", "main", "26", 21, "2026-10-05T10:00:00Z"));
        assertFalse(s.checkClient().isInstalled());

        // instance.json records 1.0.0 (jar missing, the next PLAY restores it): installed, update offered.
        Json.write(paths.instanceFile(), new dev.vanta.launcher.core.model.InstanceInfo(1, "vanta-1.21.11", "1.21.11", "0.19.5",
            "0.141.6+1.21.11", "1.0.0", "vanta-client-1.0.0.jar", "1.21.11", "fabric-loader-0.19.5-1.21.11", "main", "26", 21,
            "2026-10-05T10:00:00Z"));
        final UpdateService.ClientCheck recorded = s.checkClient();
        assertEquals(InstalledClient.Source.INSTANCE, recorded.installed().orElseThrow().source());
        assertEquals("1.0.1", recorded.update().orElseThrow().latestVersion().toString());
        assertEquals(paths.modsDir().resolve("vanta-client-1.0.1.jar"),
            s.installClientUpdate(recorded.update().orElseThrow(), DownloadProgressListener.NONE, CancellationToken.NONE));
        assertEquals("1.0.1", clientService.installedClient().orElseThrow().version(), "instance.json and mods/ agree after the update");
        assertTrue(s.checkClient().update().isEmpty());
    }

    @Test
    void developmentJarIsComparedAsZeroLikeBefore() throws Exception {
        final UpdateService s = service("1.0.0", new OsInfo("linux", "x64", ""));
        final Optional<InstalledClient> dev = installed("dev");
        assertTrue(dev.orElseThrow().isDevelopmentBuild());
        assertEquals(SemVer.parse("1.1.0"), s.checkClient(dev).update().orElseThrow().latestVersion());
    }

    @Test
    void launcherAssetFollowsHowTheLauncherWasInstalled() throws Exception {
        final OsInfo windows = new OsInfo("windows", "x64", "10.0");
        final OsInfo linux = new OsInfo("linux", "x64", "6.8");
        final Path portableDir = tmp.resolve("portable/VANTA Launcher");
        final UpdateInfo portable = service("1.0.0", windows, LauncherPackaging.portable(portableDir)).checkLauncher().orElseThrow();
        assertEquals("VANTA-Launcher-1.1.0-windows-portable.zip", portable.file().name());
        assertEquals(UpdateInfo.AssetKind.PORTABLE, portable.assetKind());
        final UpdateService portableService = service("1.0.0", windows, LauncherPackaging.portable(portableDir));
        final Path zip = portableService.downloadUpdate(portable, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(paths.updatesCacheDir().resolve("1.1.0-VANTA-Launcher-1.1.0-windows-portable.zip"), zip, "downloaded and verified only");
        assertEquals(zip, portableService.prepareInstaller(portable));
        assertEquals(Optional.of(portableDir), portableService.packaging().appDirectory());

        final UpdateInfo windowsJar = service("1.0.0", windows, LauncherPackaging.PLAIN_JAR).checkLauncher().orElseThrow();
        assertEquals("vanta-launcher-1.1.0-windows-all.jar", windowsJar.file().name());
        assertEquals(UpdateInfo.AssetKind.JAR, windowsJar.assetKind());
        final UpdateInfo linuxJar = service("1.0.0", linux, LauncherPackaging.PLAIN_JAR).checkLauncher().orElseThrow();
        assertEquals("vanta-launcher-1.1.0-linux-all.jar", linuxJar.file().name());
        assertEquals("VANTA-Launcher-1.1.0-linux-x64.tar.gz", service("1.0.0", linux).checkLauncher().orElseThrow().file().name());
        assertEquals("VANTA-Launcher-1.1.0.msi", service("1.0.0", windows).checkLauncher().orElseThrow().file().name());
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
        final UpdateInfo update = s.checkClient(installed("1.0.0")).update().orElseThrow();
        final Path active = s.installClientUpdate(update, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(paths.modsDir().resolve("vanta-client-1.1.0.jar"), active);
        assertEquals(1, s.listClientVersions().size());
        assertEquals(active, s.rollbackClient("1.1.0"));
        assertEquals(SemVer.of(1, 0, 0), s.currentLauncher());
    }
}
