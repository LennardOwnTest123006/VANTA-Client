package dev.vanta.core.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.modrinth.FakeModPlatform;
import dev.vanta.core.modrinth.FakeModrinthApi;
import dev.vanta.core.modrinth.InstallRequest;
import dev.vanta.core.modrinth.ModrinthException;
import dev.vanta.core.modrinth.ModrinthLibrary;
import dev.vanta.core.modrinth.ModrinthService;
import dev.vanta.core.modrinth.PerformancePack;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.settings.VantaSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The one-click "Boost FPS" action: vanilla options, VANTA settings and the Performance pack. */
class BoostFpsTest {
    @TempDir
    Path gameDir;

    private final MutableClock clock = MutableClock.standard();
    private final FakeOptionsBridge options = new FakeOptionsBridge();
    private VantaServices services;

    @BeforeEach
    void setUp() {
        services = VantaServices.create(VantaPaths.inGameDirectory(gameDir), new FakeGameBridge(), options,
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        services.load();
    }

    private Optional<Notification> boostToast() {
        return services.notifications().history().stream().filter(n -> n.title().equals("Boost applied")).findFirst();
    }

    @Test
    void boostWritesTheOptionsAndSettingsWithoutAnyFrameRateCap() {
        assertEquals(120, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1), "vanilla default before");
        assertTrue(options.getBoolean(VanillaOption.VSYNC, false), "vanilla default before");

        assertTrue(services.runAction(ActionEntry.BOOST_FPS));

        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1), "unlimited, never a cap");
        assertFalse(options.getBoolean(VanillaOption.VSYNC, true));
        assertEquals(5, options.getInt(VanillaOption.RENDER_DISTANCE, -1));
        assertEquals(5, options.getInt(VanillaOption.SIMULATION_DISTANCE, -1));
        assertEquals("MINIMAL", options.getEnum(VanillaOption.PARTICLES, ""));
        assertEquals("OFF", options.getEnum(VanillaOption.CLOUDS, ""));
        assertFalse(options.getBoolean(VanillaOption.SMOOTH_LIGHTING, true));
        assertFalse(options.getBoolean(VanillaOption.ENTITY_SHADOWS, true));
        assertEquals(0, options.getInt(VanillaOption.BIOME_BLEND, -1));
        assertEquals(0, options.getInt(VanillaOption.MIPMAP_LEVELS, -1));
        assertEquals(0, options.getInt(VanillaOption.MENU_BLUR, -1));
        assertEquals("FAST", options.getEnum(VanillaOption.GRAPHICS_MODE, ""));
        assertEquals("AFK", options.getEnum(VanillaOption.INACTIVITY_FPS_LIMIT, ""), "left untouched");
        assertTrue(options.saveCount >= 1);

        assertEquals(PerformancePreset.BOOST, services.settings().get(VantaSettings.PERFORMANCE_PRESET));
        assertEquals(FpsLimitPreset.UNLIMITED, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        assertEquals(MenuBackground.SOLID, services.settings().get(VantaSettings.MENU_BACKGROUND));
        assertEquals(MenuParticles.NONE, services.settings().get(VantaSettings.MENU_PARTICLES));
        assertEquals(Optional.of(PerformancePreset.BOOST), services.performance().detectPreset());
        assertFalse(services.performance().isFpsCapped());

        Notification toast = boostToast().orElseThrow();
        assertEquals("Max FPS preset, no frame-rate cap, VSync off, plain menu", toast.body(),
                "without the Modrinth integration nothing is installed and no restart is needed");
        assertFalse(Files.exists(gameDir.resolve("mods")), "no download without Modrinth");
    }

    @Test
    void boostInstallsThePerformancePackWhenMembersAreMissing() {
        FakeModrinthApi api = FakeModrinthApi.standard();
        FakeModPlatform platform = new FakeModPlatform(gameDir);
        ModrinthService modrinth = platform.installInto(services, api);
        assertEquals(PerformancePack.ITEMS.size(), VantaServices.missingPerformancePackMembers(modrinth).size());

        services.boostFps();

        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));
        assertTrue(api.calls().stream().anyMatch(c -> c.startsWith("download:")), api.calls().toString());
        for (String slug : PerformancePack.slugs()) {
            assertTrue(modrinth.installedItems().stream().anyMatch(i -> i.entry().map(e -> e.slug().equals(slug))
                    .orElse(false)), slug);
        }
        assertTrue(modrinth.restartRequired());
        assertTrue(VantaServices.missingPerformancePackMembers(modrinth).isEmpty());
        assertEquals("Restart the game to load the Performance pack", boostToast().orElseThrow().body());
    }

    @Test
    void boostDoesNotReinstallAPackThatIsAlreadyThere() {
        FakeModrinthApi api = FakeModrinthApi.standard();
        FakeModPlatform platform = new FakeModPlatform(gameDir);
        ModrinthService modrinth = platform.installInto(services, api);
        modrinth.installPerformancePack(PerformancePack.slugs(), null);
        int calls = api.calls().size();
        services.notifications().clearHistory();

        services.boostFps();

        assertEquals(calls, api.calls().size(), "every member is installed: Modrinth is not contacted");
        assertEquals("Restart the game to load the Performance pack", boostToast().orElseThrow().body(),
                "the pack installed earlier in this session still waits for a restart");
        assertEquals(5, options.getInt(VanillaOption.RENDER_DISTANCE, -1), "options are still applied");
    }

    @Test
    void modsLoadedByOtherMeansCountAsPresent() {
        FakeModrinthApi api = FakeModrinthApi.standard();
        FakeModPlatform platform = new FakeModPlatform(gameDir);
        for (PerformancePack.Item item : PerformancePack.ITEMS) {
            platform.loaded.add(item.modId());
        }
        ModrinthService modrinth = platform.installInto(services, api);
        assertEquals(List.of(), VantaServices.missingPerformancePackMembers(modrinth));

        services.boostFps();

        assertTrue(api.calls().isEmpty(), "nothing to download when every mod is loaded already");
        assertEquals("Max FPS preset, no frame-rate cap, VSync off, plain menu", boostToast().orElseThrow().body());
    }

    @Test
    void boostInstallsOnlyTheMissingMembers() throws Exception {
        FakeModrinthApi api = FakeModrinthApi.standard();
        FakeModPlatform platform = new FakeModPlatform(gameDir);
        // Sodium runs from a jar Modrinth does not know (CurseForge build); Fabric loaded it all the same.
        Path mods = gameDir.resolve("mods");
        Files.createDirectories(mods);
        Files.write(mods.resolve("sodium-fabric-0.8.14+mc1.21.11.jar"), "unknown bytes".getBytes(StandardCharsets.UTF_8));
        platform.loaded.add("sodium");
        ModrinthService modrinth = platform.installInto(services, api);
        assertEquals(5, VantaServices.missingPerformancePackMembers(modrinth).size());

        services.boostFps();

        assertFalse(api.calls().contains("download:sodium-1.0.0.jar"), "a loaded member is never downloaded again");
        assertFalse(Files.exists(mods.resolve("sodium-1.0.0.jar")), "no duplicate Sodium jar");
        for (String file : List.of("lithium-1.0.0.jar", "ferrite-core-1.0.0.jar", "immediatelyfast-1.0.0.jar",
                "entityculling-1.0.0.jar", "iris-1.0.0.jar")) {
            assertTrue(api.calls().contains("download:" + file), file);
        }
        assertTrue(VantaServices.missingPerformancePackMembers(modrinth).isEmpty());
        assertEquals("Restart the game to load the Performance pack", boostToast().orElseThrow().body());
    }

    @Test
    void secondClickWhileThePackInstallsQueuesNothingAndThereIsOneToast() {
        FakeModrinthApi api = FakeModrinthApi.standard();
        FakeModPlatform platform = new FakeModPlatform(gameDir);
        Deque<Runnable> worker = new ArrayDeque<>();
        ModrinthService modrinth = new ModrinthService(api, new ModrinthLibrary(gameDir, services.jsonStore()),
                platform, services.notifications(), worker::add, Runnable::run, clock);
        services.setModrinth(modrinth);

        services.boostFps();
        assertTrue(modrinth.isBusy(), "the pack is downloading");
        int queued = worker.size();
        services.boostFps();
        assertEquals(queued, worker.size(), "the second click queues no second install");
        List<Notification> history = services.notifications().history();
        assertEquals(1, history.stream().filter(n -> n.title().equals("Download still running")).count());

        while (!worker.isEmpty()) {
            worker.poll().run();
        }
        assertFalse(modrinth.isBusy());
        assertEquals(1, api.calls().stream().filter("project:sodium"::equals).count(), "one plan, one install");
        history = services.notifications().history();
        assertEquals(1, history.stream().filter(n -> n.title().equals("Installing the Performance pack")).count());
        assertTrue(history.stream().noneMatch(n -> n.title().equals("Already installed")), history.toString());
        assertTrue(history.stream().noneMatch(n -> n.title().equals("Preset applied")), "one toast for the boost");
        List<Notification> boosts = history.stream().filter(n -> n.title().equals("Boost applied")).toList();
        assertEquals(1, boosts.size());
        assertEquals("Restart the game to load the Performance pack", boosts.get(0).body());
        assertTrue(modrinth.restartRequired());

        // A later Boost in the same session still mentions the pending restart.
        services.notifications().clearHistory();
        clock.advance(10_000);
        services.notifications().tick();
        services.boostFps();
        assertEquals("Restart the game to load the Performance pack", boostToast().orElseThrow().body());
    }

    @Test
    void boostDuringAnUnrelatedDownloadStillAppliesTheSettingsAndQueuesNoPackInstall() {
        FakeModrinthApi api = FakeModrinthApi.standard();
        FakeModPlatform platform = new FakeModPlatform(gameDir);
        Deque<Runnable> worker = new ArrayDeque<>();
        ModrinthService modrinth = new ModrinthService(api, new ModrinthLibrary(gameDir, services.jsonStore()),
                platform, services.notifications(), worker::add, Runnable::run, clock);
        services.setModrinth(modrinth);
        // The Mods screen is fetching Mod Menu (reached through the command palette, the Boost button is disabled).
        modrinth.install(List.of(InstallRequest.of("modmenu")), "Installing Mod Menu", null);
        assertTrue(modrinth.isBusy());
        assertEquals(120, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));

        assertTrue(services.runAction(ActionEntry.BOOST_FPS));

        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1), "the settings half of Boost runs");
        assertFalse(options.getBoolean(VanillaOption.VSYNC, true));
        assertEquals(PerformancePreset.BOOST, services.settings().get(VantaSettings.PERFORMANCE_PRESET));
        assertEquals(1, worker.size(), "no pack install queued behind the other download");
        List<Notification> history = services.notifications().history();
        assertEquals(1, history.stream().filter(n -> n.title().equals("Download still running")).count());
        assertTrue(history.stream().noneMatch(n -> n.body().contains("still being installed")), history.toString());
        assertTrue(boostToast().isEmpty(), "no claim about the pack while nothing was fetched");

        while (!worker.isEmpty()) {
            worker.poll().run();
        }
        assertFalse(modrinth.isBusy());
        assertFalse(api.calls().contains("download:sodium-1.0.0.jar"));
        assertEquals(1, modrinth.installedProjectIds().size(), "only Mod Menu landed");
    }

    @Test
    void boostDoesNotClaimSuccessWhenNothingWasInstalled() {
        FakeModrinthApi api = FakeModrinthApi.standard()
                .failProjects(new ModrinthException(ModrinthException.Kind.NETWORK, "offline"));
        FakeModPlatform platform = new FakeModPlatform(gameDir);
        platform.installInto(services, api);

        services.boostFps();

        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1), "the options are still applied");
        List<Notification> history = services.notifications().history();
        assertTrue(history.stream().anyMatch(n -> n.title().equals("Nothing was installed")), history.toString());
        assertTrue(boostToast().isEmpty(), "no success toast after the error toast");
    }

    @Test
    void boostIsOfferedInTheCommandPalette() {
        assertTrue(ActionEntry.builtIns().stream().anyMatch(a -> a.id().equals(ActionEntry.BOOST_FPS)));
        assertTrue(services.search().search("boost fps", 5).stream().anyMatch(r ->
                r instanceof dev.vanta.core.search.GlobalSearchResult.ActionResult a
                        && a.action().id().equals(ActionEntry.BOOST_FPS)));
    }
}
