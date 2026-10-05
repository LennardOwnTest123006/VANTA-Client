package dev.vanta.launcher.core.install;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.model.FabricProfileJson;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.model.VersionJson;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.testutil.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallerTest {

    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;
    private FakeWorld world;

    @BeforeEach
    void start() throws IOException {
        world = new FakeWorld(false);
    }

    @AfterEach
    void stop() {
        world.close();
    }

    @Test
    void fullInstallIsCompleteVerifiedAndIdempotent() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        final List<InstallProgress> progress = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        final InstallListener listener = new InstallListener() {
            @Override
            public void onProgress(final InstallProgress p) {
                progress.add(p);
            }

            @Override
            public void onLog(final String message) {
                logs.add(message);
            }
        };

        final InstanceInfo info = wired.installer().install(InstallRequest.standard(), listener, new CancellationToken());

        // instance metadata
        assertEquals("vanta-1.21.11", info.instanceId());
        assertEquals("1.21.11", info.minecraftVersion());
        assertEquals("0.19.5", info.fabricLoaderVersion());
        assertEquals(LauncherVersion.FABRIC_API, info.fabricApiVersion());
        assertEquals("1.0.0", info.vantaClientVersion());
        assertEquals("vanta-client-1.0.0.jar", info.vantaClientJar());
        assertEquals("fabric-loader-0.19.5-1.21.11", info.fabricProfileId());
        assertEquals(FabricProfileJson.KNOT_CLIENT, info.mainClass());
        assertEquals("26", info.assetIndexId());
        assertEquals(21, info.javaMajor());
        assertEquals("2026-10-04T12:00:00Z", info.installedAt());
        assertEquals(info, Json.read(paths.instanceFile(), InstanceInfo.class));

        // files on disk
        assertTrue(Files.isRegularFile(paths.versionJson("1.21.11")));
        assertTrue(Files.isRegularFile(paths.versionJson("fabric-loader-0.19.5-1.21.11")));
        assertArrayEquals(world.blob("mojang/objects/client.jar"), Files.readAllBytes(paths.versionJar("1.21.11")));
        assertTrue(Files.isRegularFile(paths.librariesDir().resolve("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar")));
        assertTrue(Files.isRegularFile(paths.librariesDir().resolve("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar")));
        assertFalse(Files.exists(paths.librariesDir().resolve("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar")), "other platforms are not downloaded");
        assertTrue(Files.isRegularFile(paths.librariesDir().resolve("net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar")));
        assertTrue(Files.isRegularFile(paths.librariesDir().resolve("net/fabricmc/intermediary/1.21.11/intermediary-1.21.11.jar")));
        assertTrue(Files.isRegularFile(paths.librariesDir().resolve("org/ow2/asm/asm/9.9/asm-9.9.jar")));
        assertTrue(Files.isRegularFile(paths.assetIndexesDir().resolve("26.json")));
        try (Stream<Path> objects = Files.walk(paths.assetObjectsDir())) {
            assertEquals(8, objects.filter(Files::isRegularFile).count(), "duplicate hashes downloaded once");
        }
        assertTrue(Files.isRegularFile(paths.logConfigsDir().resolve("client-1.12.xml")));
        assertTrue(Files.isRegularFile(paths.modsDir().resolve("fabric-api-" + LauncherVersion.FABRIC_API + ".jar")));
        assertArrayEquals(world.blob("releases/vanta-client-1.0.0.jar"), Files.readAllBytes(paths.modsDir().resolve("vanta-client-1.0.0.jar")));
        assertTrue(Files.isRegularFile(paths.clientVersionsDir().resolve("1.0.0/vanta-client-1.0.0.jar")), "rollback copy kept");
        assertTrue(Files.isRegularFile(paths.clientVersionsDir().resolve("1.0.0/manifest.json")));
        try (Stream<Path> all = Files.walk(paths.dataDir())) {
            assertTrue(all.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }

        // progress covered every step in order
        final List<InstallStep> steps = progress.stream().map(InstallProgress::step).distinct().toList();
        assertEquals(InstallPlan.stepsFor(InstallRequest.standard()), steps);
        assertTrue(progress.get(progress.size() - 1).fraction() >= 0.99);
        assertTrue(logs.stream().anyMatch(l -> l.startsWith("Install plan")));
        assertTrue(logs.stream().anyMatch(l -> l.startsWith("Installation complete")));

        // Fabric library checksum fallback used the sidecars exactly once each
        assertEquals(1, world.server().hits("fabric/maven/net/fabricmc/intermediary/1.21.11/intermediary-1.21.11.jar.sha1"));
        assertEquals(1, world.server().hits("fabric/maven/net/fabricmc/intermediary/1.21.11/intermediary-1.21.11.jar.sha256"), "sha256 tried first, 404");
        assertEquals(1, world.server().hits("fabric/maven/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar.sha256"));

        // Second run: everything verified, nothing downloaded again (only metadata fetched)
        final int hitsBefore = world.server().totalHits();
        final List<String> secondLogs = new ArrayList<>();
        final InstanceInfo again = wired.installer().install(InstallRequest.standard(), new InstallListener() {
            @Override
            public void onLog(final String message) {
                secondLogs.add(message);
            }
        }, new CancellationToken());
        assertEquals(info, again);
        final List<String> newRequests = world.server().requests().subList(hitsBefore, world.server().totalHits());
        assertTrue(newRequests.stream().noneMatch(r -> r.contains("/mojang/libraries/") || r.contains("/assets/") || r.contains("/mojang/objects/")),
            "no library, asset or jar re-download: " + newRequests);
        assertTrue(secondLogs.stream().anyMatch(l -> l.startsWith("Installation complete: 0 KB")));
        assertTrue(wired.installer().findInstalled(InstallRequest.standard()).isPresent());
    }

    @Test
    void resumesAfterCorruptedAndMissingFiles() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        wired.installer().install(InstallRequest.standard(), InstallListener.NONE, new CancellationToken());
        final Path lwjgl = paths.librariesDir().resolve("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar");
        Files.writeString(lwjgl, "corrupt");
        Files.delete(paths.versionJar("1.21.11"));
        wired.installer().install(InstallRequest.standard(), InstallListener.NONE, new CancellationToken());
        assertEquals(Checksums.sha1Hex(lwjgl), Checksums.hex(world.blob("mojang/libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"), dev.vanta.launcher.core.net.HashAlgorithm.SHA1));
        assertTrue(Files.isRegularFile(paths.versionJar("1.21.11")));
    }

    @Test
    void localClientJarOverrideAndNoAssets() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final Path local = tmp.resolve("build/vanta-client-1.0.0-SNAPSHOT.jar");
        Files.createDirectories(local.getParent());
        Files.write(local, FakeWorld.synthetic("local dev jar", 500));
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        final InstallRequest request = new InstallRequest("1.21.11", "0.19.5", LauncherVersion.FABRIC_API, local, true, false);
        final InstanceInfo info = wired.installer().install(request, InstallListener.NONE, new CancellationToken());
        assertEquals("dev", info.vantaClientVersion());
        assertEquals("vanta-client-1.0.0-SNAPSHOT.jar", info.vantaClientJar());
        assertArrayEquals(Files.readAllBytes(local), Files.readAllBytes(paths.modsDir().resolve("vanta-client-1.0.0-SNAPSHOT.jar")));
        assertEquals(0, world.server().hits("releases/client-latest.json"), "release manifest not consulted");
        assertFalse(Files.exists(paths.assetIndexesDir().resolve("26.json")), "assets skipped");
        assertFalse(InstallPlan.stepsFor(request).contains(InstallStep.ASSETS));
    }

    @Test
    void withoutClientProducesPlainFabricInstance() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        final InstanceInfo info = wired.installer().install(new InstallRequest("1.21.11", "0.19.5", LauncherVersion.FABRIC_API, null, false, false),
            InstallListener.NONE, new CancellationToken());
        assertFalse(info.hasVantaClient());
        assertTrue(wired.installer().findInstalled(InstallRequest.standard()).isEmpty(), "standard request wants the client");
    }

    @Test
    void unpublishedReleaseFailsWithNotPublished() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        world.server().addJson("releases/client-latest.json", dev.vanta.launcher.testutil.Fixtures.read("release/client-unpublished.json"));
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        final InstallException e = assertThrows(InstallException.class,
            () -> wired.installer().install(new InstallRequest("1.21.11", "0.19.5", LauncherVersion.FABRIC_API, null, true, false), InstallListener.NONE, new CancellationToken()));
        assertEquals(InstallStep.VANTA_CLIENT, e.step());
        assertTrue(e.getCause() instanceof NotPublishedException);
        assertTrue(e.getMessage().contains("no public download yet"));
        assertFalse(Files.exists(paths.instanceFile()), "instance.json only written on success");
    }

    @Test
    void missingOrInvalidReleasesUrlIsReportedAsNotConfigured() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final FakeWorld.Wired base = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        for (String url : List.of("", "ftp://releases.example/vanta", "not a url")) {
            final VantaClientService unusable = new VantaClientService(base.downloader(), paths, () -> url);
            final Installer installer = new Installer(paths, LINUX, base.downloader(), base.mojang(), base.fabric(), base.fabricApi(), unusable,
                Optional.empty(), CLOCK);
            final InstallException e = assertThrows(InstallException.class,
                () -> installer.install(new InstallRequest("1.21.11", "0.19.5", LauncherVersion.FABRIC_API, null, true, false), InstallListener.NONE,
                    new CancellationToken()));
            assertEquals(InstallStep.VANTA_CLIENT, e.step());
            assertTrue(e.getCause() instanceof ReleasesNotConfiguredException, url + ": " + e.getCause());
            assertTrue(e.getMessage().contains(url.isEmpty() ? "No releases URL is configured" : "is not a valid http(s) URL"), e.getMessage());
        }
    }

    @Test
    void tamperedLibraryFailsIntegrity() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        world.server().add("mojang/libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar", "evil".getBytes(StandardCharsets.UTF_8));
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        final InstallException e = assertThrows(InstallException.class,
            () -> wired.installer().install(new InstallRequest("1.21.11", "0.19.5", LauncherVersion.FABRIC_API, null, false, false), InstallListener.NONE, new CancellationToken()));
        assertEquals(InstallStep.LIBRARIES, e.step());
        assertTrue(e.getCause() instanceof IntegrityException);
        assertFalse(Files.exists(paths.librariesDir().resolve("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar")));
    }

    @Test
    void unknownVersionIsReported() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        final InstallException e = assertThrows(InstallException.class,
            () -> wired.installer().install(new InstallRequest("1.21.99", "0.19.5", LauncherVersion.FABRIC_API, null, false, false), InstallListener.NONE, new CancellationToken()));
        assertEquals(InstallStep.MANIFEST, e.step());
        assertTrue(e.getMessage().contains("does not list Minecraft 1.21.99"));
    }

    @Test
    void sharedOfficialFilesAreReusedInsteadOfDownloaded() throws Exception {
        final Path official = tmp.resolve(".minecraft");
        final String libPath = "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar";
        final Path officialLib = official.resolve("libraries").resolve(libPath);
        Files.createDirectories(officialLib.getParent());
        Files.write(officialLib, world.blob("mojang/libraries/" + libPath));
        final Path bogus = official.resolve("libraries/org/lwjgl/lwjgl-glfw/3.3.3/lwjgl-glfw-3.3.3.jar");
        Files.createDirectories(bogus.getParent());
        Files.writeString(bogus, "wrong content");

        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.of(new SharedFileSource(official)), CLOCK);
        wired.installer().install(new InstallRequest("1.21.11", "0.19.5", LauncherVersion.FABRIC_API, null, false, false), InstallListener.NONE, new CancellationToken());
        assertEquals(0, world.server().hits("mojang/libraries/" + libPath), "copied from .minecraft, not downloaded");
        assertEquals(1, world.server().hits("mojang/libraries/org/lwjgl/lwjgl-glfw/3.3.3/lwjgl-glfw-3.3.3.jar"), "mismatching official file is ignored");
        assertArrayEquals(world.blob("mojang/libraries/" + libPath), Files.readAllBytes(paths.librariesDir().resolve(libPath)));
        assertEquals("wrong content", Files.readString(bogus), "official directory is never modified");
    }

    @Test
    void cancellationAbortsInstall() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        final CancellationToken token = new CancellationToken();
        final InstallListener cancelAtLibraries = new InstallListener() {
            @Override
            public void onProgress(final InstallProgress p) {
                if (p.step() == InstallStep.LIBRARIES) {
                    token.cancel();
                }
            }
        };
        assertThrows(java.util.concurrent.CancellationException.class,
            () -> wired.installer().install(InstallRequest.standard(), cancelAtLibraries, token));
        assertFalse(Files.exists(paths.instanceFile()));
    }

    @Test
    void planEstimatesAndDiskCheck() throws Exception {
        final InstallPlan plan = new InstallPlan(InstallPlan.stepsFor(InstallRequest.standard()), 10_000);
        assertEquals(10_000 + InstallPlan.SAFETY_MARGIN_BYTES, plan.requiredBytes());
        plan.checkDiskSpace(tmp);
        final InstallPlan huge = new InstallPlan(List.of(InstallStep.MANIFEST), Long.MAX_VALUE / 2);
        final InsufficientDiskSpaceException e = assertThrows(InsufficientDiskSpaceException.class, () -> huge.checkDiskSpace(tmp));
        assertTrue(e.requiredBytes() > e.availableBytes());
        assertTrue(e.getMessage().contains("Not enough disk space"));
        final VersionJson v = Json.parse(world.versionJson().toString(), VersionJson.class);
        assertTrue(v.assetIndex().totalSize() > 0);
    }
}
