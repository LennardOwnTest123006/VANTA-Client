package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.JsonStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModrinthInstallerTest {
    @TempDir
    Path gameDir;

    private FakeModrinthServer server;
    private ModrinthFixtures fx;
    private ModrinthClient client;
    private ModrinthLibrary library;
    private ModrinthInstaller installer;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void start() throws Exception {
        server = new FakeModrinthServer();
        fx = new ModrinthFixtures(server);
        client = server.client(new ArrayList<Duration>());
        library = new ModrinthLibrary(gameDir, new JsonStore(clock));
        installer = new ModrinthInstaller(client, library, clock);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private InstallPlan plan(String... slugs) {
        List<InstallRequest> requests = Stream.of(slugs).map(InstallRequest::of).toList();
        return new InstallPlanner(client).plan(requests, InstallState.of(library.index(), java.util.Set.of(), true));
    }

    private List<String> modsFolder() throws Exception {
        try (Stream<Path> files = Files.list(gameDir.resolve("mods"))) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void installsThePerformancePackAndRecordsItInTheIndex() throws Exception {
        fx.performancePack();
        List<Double> progress = new ArrayList<>();
        InstallResult result = installer.execute(plan(PerformancePack.slugs().toArray(String[]::new)),
                (f, item, i, n) -> progress.add(f));
        assertEquals(5, result.installed().size());
        assertTrue(result.failed().isEmpty());
        assertTrue(result.modsChanged());
        assertEquals(List.of("ImmediatelyFast-Fabric-1.14.3+1.21.11.jar", "entityculling-fabric-1.11.2-mc1.21.11.jar",
                "iris-fabric-1.10.8+mc1.21.11.jar", "lithium-fabric-0.21.4+mc1.21.11.jar",
                "sodium-fabric-0.8.14+mc1.21.11.jar"), modsFolder());
        assertArrayEquals(fx.content("sodium-0.8.14"),
                Files.readAllBytes(gameDir.resolve("mods/sodium-fabric-0.8.14+mc1.21.11.jar")));
        assertEquals(1.0, progress.get(progress.size() - 1));

        ModrinthIndex index = library.index();
        InstalledEntry sodium = index.find("AANobbMI").orElseThrow();
        assertEquals("sodium", sodium.slug());
        assertEquals("Sodium", sodium.title());
        assertEquals("rkdTcxoT", sodium.versionId());
        assertEquals("mc1.21.11-0.8.14-fabric", sodium.versionNumber());
        assertEquals("mod", sodium.type());
        assertEquals("mods/sodium-fabric-0.8.14+mc1.21.11.jar", sodium.file());
        assertEquals(fx.sha512("sodium-0.8.14"), sodium.sha512());
        assertTrue(sodium.enabled());
        assertEquals(List.of("YL57xq9U"), sodium.requiredBy());
        assertEquals("2026-10-05T12:00:00Z", sodium.installedAt());
        assertEquals(1, result.problems().size(), "FerriteCore has no version");
    }

    @Test
    void hashMismatchFailsTheFileAndEverythingThatNeedsIt() throws Exception {
        fx.performancePack();
        // The CDN serves different bytes than the SHA-512 the API published for Sodium 0.8.14.
        server.file("/cdn/data/AANobbMI/versions/rkdTcxoT/sodium-fabric-0.8.14+mc1.21.11.jar",
                "tampered".getBytes(StandardCharsets.UTF_8));
        InstallResult result = installer.execute(plan("iris", "lithium"), null);
        assertEquals(List.of("LITHIUM1"), result.installed().stream().map(PlannedInstall::projectId).toList());
        assertEquals(2, result.failed().size());
        assertEquals(1, result.notes().stream().filter(n -> n.kind() == PlanNote.Kind.DOWNLOAD_FAILED).count());
        assertEquals(1, result.notes().stream().filter(n -> n.kind() == PlanNote.Kind.DEPENDENCY_FAILED).count());
        assertEquals(List.of("lithium-fabric-0.21.4+mc1.21.11.jar"), modsFolder(),
                "no unverified file, no Iris without its Sodium, no staging leftovers");
        assertFalse(library.index().contains("AANobbMI"));
        assertFalse(library.index().contains("YL57xq9U"));
    }

    @Test
    void foreignFileWithTheSameNameIsNeverOverwritten() throws Exception {
        fx.performancePack();
        Path foreign = gameDir.resolve("mods/lithium-fabric-0.21.4+mc1.21.11.jar");
        Files.createDirectories(foreign.getParent());
        Files.writeString(foreign, "my own build");
        InstallResult result = installer.execute(plan("lithium"), null);
        assertTrue(result.installed().isEmpty());
        assertEquals("my own build", Files.readString(foreign));
        assertTrue(result.notes().stream().anyMatch(n -> n.kind() == PlanNote.Kind.DOWNLOAD_FAILED));
        assertFalse(library.index().contains("LITHIUM1"));
    }

    @Test
    void identicalFileAlreadyPresentIsAdoptedWithoutDownloading() throws Exception {
        fx.performancePack();
        Path present = gameDir.resolve("mods/lithium-fabric-0.21.4+mc1.21.11.jar");
        Files.createDirectories(present.getParent());
        Files.write(present, fx.content("lithium-mc1.21.11-0.21.4-fabric"));
        int before = server.requests().size();
        InstallResult result = installer.execute(plan("lithium"), null);
        assertEquals(1, result.installed().size());
        assertTrue(library.index().contains("LITHIUM1"));
        assertTrue(server.requests().subList(before, server.requests().size()).stream()
                .noneMatch(r -> r.path().startsWith("/cdn/")), "no download");
    }

    @Test
    void identicalDisabledFileIsSwitchedBackOnRatherThanRecordedAsInstalledWhileOff() throws Exception {
        fx.performancePack();
        Path enabled = gameDir.resolve("mods/lithium-fabric-0.21.4+mc1.21.11.jar");
        Path disabled = gameDir.resolve("mods/lithium-fabric-0.21.4+mc1.21.11.jar.disabled");
        Files.createDirectories(disabled.getParent());
        Files.write(disabled, fx.content("lithium-mc1.21.11-0.21.4-fabric"));
        int before = server.requests().size();
        InstallResult result = installer.execute(plan("lithium"), null);
        assertEquals(1, result.installed().size());
        assertTrue(result.modsChanged(), "the restart hint is true: the jar now loads");
        assertTrue(Files.exists(enabled), "the hand-disabled copy was switched back on");
        assertFalse(Files.exists(disabled));
        assertTrue(library.index().find("LITHIUM1").orElseThrow().enabled());
        assertTrue(server.requests().subList(before, server.requests().size()).stream()
                .noneMatch(r -> r.path().startsWith("/cdn/")), "no download");
    }

    @Test
    void disabledDependencyIsSwitchedBackOn() throws Exception {
        fx.performancePack();
        installer.execute(plan("sodium"), null);
        library.setEnabled("AANobbMI", false);
        assertTrue(Files.exists(gameDir.resolve("mods/sodium-fabric-0.8.14+mc1.21.11.jar.disabled")));
        InstallResult result = installer.execute(plan("iris"), null);
        assertEquals(List.of("AANobbMI"), result.enabled());
        assertTrue(Files.exists(gameDir.resolve("mods/sodium-fabric-0.8.14+mc1.21.11.jar")));
        InstalledEntry sodium = library.index().find("AANobbMI").orElseThrow();
        assertTrue(sodium.enabled());
        assertEquals(List.of("YL57xq9U"), sodium.requiredBy());
    }

    /** Modrinth serving a Smart FPS Booster-like jar that calls KeyMapping(String, Type, int, String). */
    private FakeModrinthApi apiWithOldMinecraftBuild() {
        byte[] jar = ModJarFixtures.modJar("smartfpsbooster", "Smart FPS Booster", "1.0.0",
                ModJarFixtures.compile(gameDir, ModJarFixtures.OLD_TYPE_MOD));
        return FakeModrinthApi.standard()
                .add("SMARTFPS", "smart-fps-booster", "Smart FPS Booster", "mod", "devxkamlesh",
                        "Boosts FPS.", 1000L, -1)
                .replaceFile("SMARTFPS", "smart-fps-booster-1.0.0+mc1.21.4.jar", jar);
    }

    @Test
    void modBuiltForAnOlderMinecraftIsNotInstalledButTheRestOfThePlanIs() throws Exception {
        FakeModrinthApi api = apiWithOldMinecraftBuild();
        InstallPlan plan = new InstallPlanner(api).plan(
                List.of(InstallRequest.of("smart-fps-booster"), InstallRequest.of("lithium")),
                InstallState.of(library.index(), java.util.Set.of(), true));
        assertEquals(2, plan.installs().size());
        InstallResult result = new ModrinthInstaller(api, library, clock).execute(plan, null);

        assertTrue(api.calls().contains("download:smart-fps-booster-1.0.0+mc1.21.4.jar"), "it was downloaded");
        assertEquals(List.of("LITHIUM1"), result.installed().stream().map(PlannedInstall::projectId).toList());
        assertEquals(List.of("SMARTFPS"), result.failed().stream().map(PlannedInstall::projectId).toList());
        assertEquals(List.of("lithium-1.0.0.jar"), modsFolder(), "nothing of it in mods/, no staging leftover");
        assertFalse(library.index().contains("SMARTFPS"));
        assertTrue(library.index().contains("LITHIUM1"));
        PlanNote note = result.notes().stream().filter(n -> n.kind() == PlanNote.Kind.DOWNLOAD_FAILED)
                .findFirst().orElseThrow();
        assertEquals("Smart FPS Booster", note.subject());
        assertEquals(dev.vanta.core.i18n.Lang.tr(ModrinthException.Kind.INCOMPATIBLE.langKey()), note.detail());
        assertTrue(note.detail().contains("older Minecraft"), note.detail());
        assertEquals(List.of(note), result.problems().stream().filter(n -> n.subject().equals("Smart FPS Booster"))
                .toList());
    }

    @Test
    void handDisabledCopyBuiltForAnOlderMinecraftStaysOffAndIsKept() throws Exception {
        FakeModrinthApi api = apiWithOldMinecraftBuild();
        byte[] jar = api.fileContent("VSMARTFPS");
        Path enabled = gameDir.resolve("mods/smart-fps-booster-1.0.0+mc1.21.4.jar");
        Path disabled = gameDir.resolve("mods/smart-fps-booster-1.0.0+mc1.21.4.jar.disabled");
        Files.createDirectories(disabled.getParent());
        Files.write(disabled, jar);
        InstallPlan plan = new InstallPlanner(api).plan(List.of(InstallRequest.of("smart-fps-booster")),
                InstallState.of(library.index(), java.util.Set.of(), true));
        InstallResult result = new ModrinthInstaller(api, library, clock).execute(plan, null);

        assertTrue(result.installed().isEmpty());
        assertEquals(1, result.failed().size());
        assertFalse(Files.exists(enabled), "not switched on");
        assertArrayEquals(jar, Files.readAllBytes(disabled), "the player's file is kept as it was");
        assertFalse(library.index().contains("SMARTFPS"));
        assertTrue(api.calls().stream().noneMatch(c -> c.startsWith("download:")), "no download");
        assertTrue(result.notes().stream().anyMatch(n -> n.kind() == PlanNote.Kind.DOWNLOAD_FAILED
                && n.detail().equals(dev.vanta.core.i18n.Lang.tr("vanta.mods.error.incompatible"))));
    }

    @Test
    void shaderPacksGoToTheShaderpacksFolder() throws Exception {
        String project = ModrinthFixtures.project("HVnmMxH1", "complementary-reimagined", "Complementary Reimagined",
                "shader");
        server.json("/v2/project/complementary-reimagined", project).json("/v2/project/HVnmMxH1", project);
        server.json("/v2/project/HVnmMxH1/version", ModrinthFixtures.array(fx.version("Bqen1mJX", "HVnmMxH1", "r5.9.3",
                "release", "2026-09-15T06:35:41Z", List.of("iris", "optifine"), "ComplementaryReimagined_r5.9.3.zip",
                "complementary")));
        InstallResult result = installer.execute(plan("complementary-reimagined"), null);
        assertFalse(result.modsChanged(), "shader packs need no restart");
        assertTrue(Files.exists(gameDir.resolve("shaderpacks/ComplementaryReimagined_r5.9.3.zip")));
        assertEquals("shader", library.index().find("HVnmMxH1").orElseThrow().type());
    }
}
