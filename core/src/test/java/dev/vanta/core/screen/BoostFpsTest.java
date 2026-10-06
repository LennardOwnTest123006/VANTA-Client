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
import dev.vanta.core.modrinth.ModrinthService;
import dev.vanta.core.modrinth.PerformancePack;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.settings.VantaSettings;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertEquals("Max FPS preset, no frame-rate cap, VSync off, plain menu", boostToast().orElseThrow().body());
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
    void boostIsOfferedInTheCommandPalette() {
        assertTrue(ActionEntry.builtIns().stream().anyMatch(a -> a.id().equals(ActionEntry.BOOST_FPS)));
        assertTrue(services.search().search("boost fps", 5).stream().anyMatch(r ->
                r instanceof dev.vanta.core.search.GlobalSearchResult.ActionResult a
                        && a.action().id().equals(ActionEntry.BOOST_FPS)));
    }
}
