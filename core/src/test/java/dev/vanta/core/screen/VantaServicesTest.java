package dev.vanta.core.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ai.LocalAiStatus;
import dev.vanta.core.bridge.FakeClipboardBridge;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.FakeScreenshotBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.profiles.BuiltInProfiles;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.settings.VantaSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VantaServicesTest {
    @TempDir
    Path dir;

    @Test
    void compositionRootLoadsTicksSavesAndRunsActions() {
        MutableClock clock = MutableClock.standard();
        VantaPaths paths = VantaPaths.inGameDirectory(dir);
        FakeGameBridge game = new FakeGameBridge();
        FakeOptionsBridge options = new FakeOptionsBridge();
        FakeScreenshotBridge screenshots = new FakeScreenshotBridge();
        VantaServices services = VantaServices.create(paths, game, options, new FakeKeybindBridge(),
                new FakeResourcePackBridge(), screenshots, new FakeClipboardBridge(), clock);
        assertFalse(services.isLoaded());
        services.load();
        assertTrue(services.isLoaded());
        assertTrue(Files.isDirectory(paths.profilesDir()));
        assertEquals(BuiltInProfiles.IDS.size(), services.profiles().size());
        assertTrue(services.settingsRegistry().size() > 50);
        assertTrue(services.search().size() > 100);
        assertTrue(services.stats().current().isPresent());

        for (int i = 0; i < 25; i++) {
            clock.advance(50);
            services.tick();
        }
        assertTrue(services.stats().current().orElseThrow().playtimeMs() > 0);

        assertTrue(services.runAction(ActionEntry.APPLY_BALANCED_PRESET));
        assertEquals(10, options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertEquals(PerformancePreset.BALANCED, services.settings().get(VantaSettings.PERFORMANCE_PRESET));
        assertTrue(services.runAction(ActionEntry.TOGGLE_HUD));
        assertFalse(services.settings().get(VantaSettings.HUD_ENABLED));
        clock.advance(1100);
        assertTrue(services.runAction(ActionEntry.SCREENSHOT_HUD_FREE));
        assertEquals("capture:true", screenshots.actions.get(0));
        assertEquals(1, services.stats().current().orElseThrow().screenshots());
        clock.advance(1100);
        assertTrue(services.runAction(ActionEntry.EXPORT_PROFILE));
        assertTrue(Files.exists(paths.root().resolve("exports").resolve("vanta-profile-default.json")));
        assertTrue(services.runAction(ActionEntry.OPEN_CONFIG_FOLDER));
        assertTrue(game.actions.get(game.actions.size() - 1).startsWith("openUrl:file:"));
        assertTrue(services.runAction("open_vanilla_video"));
        assertEquals("openVanillaScreen:VIDEO", game.actions.get(game.actions.size() - 1));
        assertTrue(services.runAction(ActionEntry.OPEN_WEBSITE));
        assertEquals("openUrl:" + VantaLinks.DOCUMENTATION, game.actions.get(game.actions.size() - 1));
        services.setWebsiteUrl("https://example.invalid");
        services.runAction(ActionEntry.OPEN_WEBSITE);
        assertEquals("openUrl:https://example.invalid", game.actions.get(game.actions.size() - 1));
        assertFalse(services.runAction("not_an_action"));
        clock.advance(1100);
        assertTrue(services.runAction(ActionEntry.RESET_SETTINGS));
        assertTrue(services.settings().get(VantaSettings.HUD_ENABLED));
        assertFalse(services.notifications().visible().isEmpty());

        assertTrue(services.activateProfile("pvp"));
        assertEquals(16, options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertEquals("VANTA Client 1.0.0 · Minecraft 1.21.11 · Fabric Loader 0.19.5", services.versionLine());

        // Vanta Nexus and the Local AI are wired: the test classpath carries the unresolved manifest template, so
        // the Local AI reports an honest FAILED state with a reason instead of offering an install it cannot verify.
        assertNotNull(services.nexus());
        assertNotNull(services.nexusActions());
        assertSame(services.nexusUndo(), services.nexus().undo());
        assertSame(services.nexusTranscript(), services.nexus().transcript());
        assertTrue(services.nexusTranscript().isEmpty());
        assertEquals(LocalAiStatus.FAILED, services.localAi().status());
        assertFalse(services.localAi().statusReason().isEmpty());
        assertFalse(services.localAi().install(), "no install without a resolved manifest");
        assertTrue(services.paths().contains(services.localAi().paths().root()));
        // Background results reach the render thread through mainThread(): queued until the next tick.
        List<String> ran = new ArrayList<>();
        services.mainThread().execute(() -> ran.add("queued"));
        assertTrue(ran.isEmpty(), "nothing runs before the tick");
        services.tick();
        assertEquals(List.of("queued"), ran);
        services.setMainThreadExecutor(Runnable::run);
        services.mainThread().execute(() -> ran.add("direct"));
        assertEquals(List.of("queued", "direct"), ran);
        services.nexusTranscript().append(dev.vanta.core.ai.NexusTranscript.Entry.user("hello", clock.millis()));

        services.shutdown();
        assertTrue(Files.exists(paths.nexusChatFile()), "the Nexus chat is saved with everything else");
        assertFalse(services.isLoaded());
        assertTrue(Files.exists(paths.settingsFile()));
        assertTrue(Files.exists(paths.hudLayoutFile()));
        assertTrue(Files.exists(paths.statsFile()));
        assertEquals(1, services.statsStore().lifetime().sessions());

        VantaServices again = VantaServices.create(paths, game, options, new FakeKeybindBridge(),
                new FakeResourcePackBridge(), clock);
        again.load();
        assertEquals("pvp", again.profiles().activeId().orElseThrow());
        assertEquals(1, again.statsStore().lifetime().sessions());
        assertTrue(again.screenshots().isEmpty());
        assertFalse(again.runAction(ActionEntry.SCREENSHOT_HUD_FREE), "no screenshot bridge configured");
    }

    /**
     * The frame-rate limit preset reaches options.txt from every write path of the real composition root: never
     * at startup, from a raw (Settings screen / search) write, and from a saved profile that is re-activated.
     */
    @Test
    void fpsLimitPresetReachesTheVanillaOptionsFromEveryWritePath() {
        MutableClock clock = MutableClock.standard();
        FakeOptionsBridge options = new FakeOptionsBridge();
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir),
                new FakeGameBridge().onTitleScreen(), options, new FakeKeybindBridge(), new FakeResourcePackBridge(),
                clock);
        services.load();
        assertTrue(options.setOrder.isEmpty(), "startup wrote " + options.setOrder);
        assertEquals(0, options.saveCount, "options.txt untouched at startup");
        assertEquals(120, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));

        assertTrue(services.settings().setRaw(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, "FPS_120"));
        assertEquals(120, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));
        assertFalse(options.getBoolean(VanillaOption.VSYNC, true), "a numeric limit turns VSync off");
        assertEquals(1, options.saveCount);
        assertFalse(services.settings().set(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, FpsLimitPreset.FPS_120));
        assertEquals(1, options.saveCount, "re-setting the same value fires nothing");

        // A profile saved with 60 FPS, left for the Performance profile (unlimited) and re-activated.
        services.performance().applyFpsLimit(FpsLimitPreset.FPS_60);
        Profile mine = services.profiles().updateActiveFromCurrent().orElseThrow();
        assertEquals("fps_60", mine.settings().get("performance.fpsLimitPreset").getAsString());
        assertEquals(60, mine.settings().get("video.framerateLimit").getAsInt());
        assertFalse(mine.settings().get("video.vsync").getAsBoolean());
        assertTrue(services.profiles().activate("performance"));
        assertEquals(FpsLimitPreset.UNLIMITED, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));
        assertFalse(options.getBoolean(VanillaOption.VSYNC, true));
        assertTrue(services.profiles().activate(mine.id()));
        assertEquals(FpsLimitPreset.FPS_60, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        assertEquals(60, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));
        assertFalse(options.getBoolean(VanillaOption.VSYNC, true));
    }

    /** "Reset all settings" ends with the frame-rate choice's default (Unlimited) in the vanilla options too. */
    @Test
    void resetAllLeavesTheVanillaFrameRateMatchingTheDefaultPreset() {
        FakeOptionsBridge options = new FakeOptionsBridge();
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir),
                new FakeGameBridge().onTitleScreen(), options, new FakeKeybindBridge(), new FakeResourcePackBridge(),
                MutableClock.standard());
        services.load();
        services.performance().applyFpsLimit(FpsLimitPreset.FPS_60);
        assertEquals(60, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));

        assertTrue(services.runAction(ActionEntry.RESET_SETTINGS));

        assertEquals(FpsLimitPreset.UNLIMITED, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1), "no cap after a reset");
        assertFalse(options.getBoolean(VanillaOption.VSYNC, true));
    }
}
