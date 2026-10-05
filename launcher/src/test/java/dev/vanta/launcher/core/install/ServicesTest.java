package dev.vanta.launcher.core.install;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.model.FabricLoaderVersion;
import dev.vanta.launcher.core.model.FabricProfileJson;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.model.VersionJson;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.HashAlgorithm;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.testutil.FakeWorld;
import dev.vanta.launcher.testutil.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service-level tests for Mojang, Fabric, Fabric API and VANTA client services against the fake world.
 */
class ServicesTest {

    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8");

    @TempDir
    Path tmp;
    private FakeWorld world;
    private LauncherPaths paths;
    private FakeWorld.Wired wired;

    @BeforeEach
    void start() throws IOException {
        world = new FakeWorld(false);
        paths = new LauncherPaths(tmp.resolve("data"));
        wired = world.wire(paths, LINUX, Optional.empty(), Clock.systemUTC());
    }

    @AfterEach
    void stop() {
        world.close();
    }

    @Test
    void mojangResolvesVersionAndCachesJson() throws Exception {
        final VersionJson v = wired.mojang().resolveVersion("1.21.11");
        assertEquals("1.21.11", v.id());
        assertTrue(Files.isRegularFile(paths.versionJson("1.21.11")));
        assertEquals(1, world.server().hits("mojang/1.21.11.json"));
        wired.mojang().resolveVersion("1.21.11");
        assertEquals(1, world.server().hits("mojang/1.21.11.json"), "cached JSON verified by sha1 and reused");
        assertTrue(wired.mojang().loadCachedVersion("1.21.11").isPresent());
        assertTrue(wired.mojang().loadCachedVersion("1.8.9").isEmpty());
        final IOException e = assertThrows(IOException.class, () -> wired.mojang().resolveVersion("9.9.9"));
        assertTrue(e.getMessage().contains("does not list"));

        final DownloadRequest client = wired.mojang().clientJarRequest(v);
        assertEquals(paths.versionJar("1.21.11"), client.target());
        assertTrue(client.expectedChecksum().isPresent());
        final List<DownloadRequest> libs = wired.mojang().libraryRequests(v, LINUX);
        assertTrue(libs.stream().allMatch(r -> r.expectedChecksum().isPresent() && r.hasExpectedSize()));
        assertTrue(libs.stream().anyMatch(r -> r.target().endsWith("lwjgl-3.3.3-natives-linux.jar")));
        assertTrue(wired.mojang().logConfigRequest(v).isPresent());
        assertEquals(8, wired.mojang().assetRequests(wired.mojang().fetchAssetIndex(v)).size());
    }

    @Test
    void fabricListsLoadersAndFetchesProfile() throws Exception {
        final List<FabricLoaderVersion> loaders = wired.fabric().listLoaders("1.21.11");
        assertEquals(2, loaders.size());
        assertEquals("0.19.5", loaders.get(0).loader().version());
        assertTrue(loaders.get(0).loader().stable());
        final FabricProfileJson profile = wired.fabric().fetchProfile("1.21.11", "0.19.5");
        assertEquals("fabric-loader-0.19.5-1.21.11", profile.id());
        assertEquals("1.21.11", profile.inheritsFrom());
        assertTrue(Files.isRegularFile(paths.versionJson("fabric-loader-0.19.5-1.21.11")));
        wired.fabric().fetchProfile("1.21.11", "0.19.5");
        assertEquals(1, world.server().hits("fabric/meta/v2/versions/loader/1.21.11/0.19.5/profile/json"), "profile cached");
        final List<DownloadRequest> libs = wired.fabric().libraryRequests(profile, LINUX);
        assertEquals(8, libs.size());
        assertTrue(libs.stream().allMatch(r -> r.expectedChecksum().isPresent()), "every Fabric library ends up with a checksum");
        assertEquals(FabricService.profileId("1.21.11", "0.19.5"), profile.id());
    }

    @Test
    void sidecarChecksumFallbackAndFailure() throws Exception {
        final java.net.URI jar = world.server().url("fabric/maven/net/fabricmc/intermediary/1.21.11/intermediary-1.21.11.jar");
        assertEquals(HashAlgorithm.SHA1, wired.fabric().fetchSidecarChecksum(jar).algorithm());
        final java.net.URI loader = world.server().url("fabric/maven/" + FakeWorld.FABRIC_LOADER_PATH);
        assertEquals(HashAlgorithm.SHA256, wired.fabric().fetchSidecarChecksum(loader).algorithm());
        final IntegrityException e = assertThrows(IntegrityException.class,
            () -> wired.fabric().fetchSidecarChecksum(world.server().url("fabric/maven/none/none/1/none-1.jar")));
        assertTrue(e.getMessage().contains("No checksum available"));
        world.server().addText("fabric/maven/bad/bad/1/bad-1.jar.sha256", "not-a-hash");
        assertThrows(IntegrityException.class, () -> wired.fabric().fetchSidecarChecksum(world.server().url("fabric/maven/bad/bad/1/bad-1.jar")));
    }

    @Test
    void fabricApiRequestAndPrune() throws Exception {
        final DownloadRequest request = wired.fabricApi().request(LauncherVersion.FABRIC_API);
        assertEquals(paths.modsDir().resolve("fabric-api-" + LauncherVersion.FABRIC_API + ".jar"), request.target());
        assertEquals(HashAlgorithm.SHA256, request.expectedChecksum().orElseThrow().algorithm());
        wired.downloader().download(request, DownloadProgressListener.NONE, CancellationToken.NONE);
        Files.writeString(paths.modsDir().resolve("fabric-api-0.100.0+1.21.jar"), "old");
        Files.writeString(paths.modsDir().resolve("other-mod.jar"), "keep");
        final List<Path> deleted = wired.fabricApi().pruneOtherVersions(LauncherVersion.FABRIC_API);
        assertEquals(1, deleted.size());
        assertFalse(Files.exists(paths.modsDir().resolve("fabric-api-0.100.0+1.21.jar")));
        assertTrue(Files.exists(paths.modsDir().resolve("other-mod.jar")));
        assertTrue(Files.exists(request.target()));
    }

    @Test
    void vantaClientInstallRollbackAndPrune() throws Exception {
        final VantaClientService vanta = wired.vanta();
        final ReleaseManifest manifest = vanta.fetchClientManifest();
        assertEquals("1.0.0", manifest.version());
        final Path active = vanta.installFromManifest(manifest, DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(paths.modsDir().resolve("vanta-client-1.0.0.jar"), active);
        assertEquals(active, vanta.activeJar().orElseThrow());

        // Publish two more versions and install them; only three are kept.
        for (String v : List.of("1.0.1", "1.0.2", "1.0.3")) {
            final byte[] jar = FakeWorld.synthetic("vanta " + v, 700);
            world.server().add("releases/vanta-client-" + v + ".jar", jar);
            final ReleaseManifest m = new ReleaseManifest(1, "client", v, "1.21.11", "0.19.5", LauncherVersion.FABRIC_API, 21, "2026-11-01", "stable",
                List.of(new ReleaseManifest.ReleaseFile("vanta-client-" + v + ".jar", world.server().url("releases/vanta-client-" + v + ".jar").toString(),
                    jar.length, Checksums.hex(jar, HashAlgorithm.SHA256))), "changelog " + v);
            vanta.installFromManifest(m, DownloadProgressListener.NONE, CancellationToken.NONE);
            Thread.sleep(5); // distinct modification times for ordering
        }
        assertEquals(paths.modsDir().resolve("vanta-client-1.0.3.jar"), vanta.activeJar().orElseThrow());
        assertFalse(Files.exists(paths.modsDir().resolve("vanta-client-1.0.0.jar")), "only one client jar in mods");
        final List<VantaClientService.KeptVersion> kept = vanta.listKeptVersions();
        assertEquals(3, kept.size());
        assertEquals(List.of("1.0.3", "1.0.2", "1.0.1"), kept.stream().map(VantaClientService.KeptVersion::version).toList());
        assertTrue(kept.stream().allMatch(VantaClientService.KeptVersion::isAvailable));
        assertFalse(Files.exists(paths.clientVersionsDir().resolve("1.0.0")), "oldest pruned");

        // Rollback to 1.0.1 swaps the jar in mods/
        final Path rolledBack = vanta.rollback("1.0.1");
        assertEquals(paths.modsDir().resolve("vanta-client-1.0.1.jar"), rolledBack);
        assertEquals(rolledBack, vanta.activeJar().orElseThrow());
        assertThrows(IOException.class, () -> vanta.rollback("1.0.0"));

        // Tampered kept jar is refused and removed
        Files.writeString(paths.clientVersionsDir().resolve("1.0.2/vanta-client-1.0.2.jar"), "tampered");
        assertThrows(IntegrityException.class, () -> vanta.rollback("1.0.2"));
        assertFalse(Files.exists(paths.clientVersionsDir().resolve("1.0.2/vanta-client-1.0.2.jar")));
    }

    @Test
    void vantaClientInstallsExactlyTheClientJar() throws Exception {
        final byte[] jar = FakeWorld.synthetic("vanta 1.0.0", 700);
        world.server().add("releases/exact/vanta-client-1.0.0.jar", jar);
        // Fabric API first, then the bundle and a sources jar: none of them may end up as the client jar.
        final ReleaseManifest m = new ReleaseManifest(1, "client", "1.0.0", "1.21.11", "0.19.5", LauncherVersion.FABRIC_API, 21, "2026-10-04", "stable",
            List.of(new ReleaseManifest.ReleaseFile("fabric-api-0.141.6+1.21.11.jar", world.server().url("releases/exact/fapi.jar").toString(), 10, "ab".repeat(32)),
                new ReleaseManifest.ReleaseFile("vanta-client-1.0.0-mods.zip", world.server().url("releases/exact/mods.zip").toString(), 10, "cd".repeat(32)),
                new ReleaseManifest.ReleaseFile("vanta-client-1.0.0-sources.jar", world.server().url("releases/exact/src.jar").toString(), 10, "ef".repeat(32)),
                new ReleaseManifest.ReleaseFile("vanta-client-1.0.0.jar", world.server().url("releases/exact/vanta-client-1.0.0.jar").toString(), jar.length,
                    Checksums.hex(jar, HashAlgorithm.SHA256))), "");
        world.server().addJson("releases/client-latest.json", Json.toJson(m));
        final Path active = wired.vanta().installFromManifest(wired.vanta().fetchClientManifest(), DownloadProgressListener.NONE, CancellationToken.NONE);
        assertEquals(paths.modsDir().resolve("vanta-client-1.0.0.jar"), active);
        assertEquals(Checksums.hex(jar, HashAlgorithm.SHA256), Checksums.sha256Hex(active));
        assertEquals(0, world.server().hits("releases/exact/fapi.jar") + world.server().hits("releases/exact/mods.zip")
            + world.server().hits("releases/exact/src.jar"));
        assertEquals("1.0.0", wired.vanta().listKeptVersions().get(0).version());
        assertTrue(wired.vanta().listKeptVersions().get(0).isAvailable());

        final ReleaseManifest noClientJar = new ReleaseManifest(1, "client", "1.0.1", "1.21.11", "0.19.5", LauncherVersion.FABRIC_API, 21, "", "stable",
            List.of(m.files().get(0), m.files().get(1)), "");
        final NotPublishedException e = assertThrows(NotPublishedException.class,
            () -> wired.vanta().installFromManifest(noClientJar, DownloadProgressListener.NONE, CancellationToken.NONE));
        assertTrue(e.getMessage().contains("lists no vanta-client-1.0.1.jar"), e.getMessage());
    }

    @Test
    void missingClientManifestIsNotPublished() {
        world.server().remove("releases/client-latest.json");
        final NotPublishedException e = assertThrows(NotPublishedException.class, () -> wired.vanta().fetchClientManifest());
        assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());
    }

    @Test
    void vantaClientRefusesManifestWithoutSha256() {
        final ReleaseManifest m = new ReleaseManifest(1, "client", "2.0.0", "1.21.11", "0.19.5", LauncherVersion.FABRIC_API, 21, "2026-11-01", "stable",
            List.of(new ReleaseManifest.ReleaseFile("vanta-client-2.0.0.jar", world.server().url("releases/vanta-client-1.0.0.jar").toString(), 0, "")), "");
        assertThrows(IntegrityException.class, () -> wired.vanta().installFromManifest(m, DownloadProgressListener.NONE, CancellationToken.NONE));
    }

    @Test
    void vantaClientRejectsWrongProductAndLocalJarValidation() throws Exception {
        world.server().addJson("releases/client-latest.json", Fixtures.read("release/launcher-latest.json"));
        assertTrue(assertThrows(IOException.class, () -> wired.vanta().fetchClientManifest()).getMessage().contains("expected 'client'"));
        assertThrows(IOException.class, () -> wired.vanta().installLocalJar(tmp.resolve("missing.jar")));
        final Path notJar = tmp.resolve("x.txt");
        Files.writeString(notJar, "x");
        assertThrows(IOException.class, () -> wired.vanta().installLocalJar(notJar));
        final Path custom = tmp.resolve("my-build.jar");
        Files.write(custom, "jar".getBytes(StandardCharsets.UTF_8));
        assertEquals(paths.modsDir().resolve("vanta-client-dev.jar"), wired.vanta().installLocalJar(custom));
        Json.write(paths.instanceFile(), new dev.vanta.launcher.core.model.InstanceInfo(1, paths.instanceId(), "1.21.11", "0.19.5",
            LauncherVersion.FABRIC_API, "", "", "1.21.11", "fabric-loader-0.19.5-1.21.11", FabricProfileJson.KNOT_CLIENT, "26", 21, "now"));
        wired.vanta().installLocalJar(custom);
        assertEquals("dev", Json.read(paths.instanceFile(), dev.vanta.launcher.core.model.InstanceInfo.class).vantaClientVersion());
    }
}
