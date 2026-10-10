package dev.vanta.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.vanta.core.ai.NexusActions;
import dev.vanta.core.ai.NexusAssistant;
import dev.vanta.core.ai.NexusUndo;
import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.profiles.BuiltInProfiles;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Nothing VANTA does by itself caps the frame rate again once it is uncapped: no performance preset, no built-in
 * profile, no Smart Boost step (not even Undo of a hand-edited {@code smart-boost.json}), not the render distance
 * advisor and no Nexus action that is not an explicit frame-rate request. VANTA's own "Unlimited" (the frame-rate
 * row and the Max Framerate slider) always means VSync off.
 */
class NoAutomaticFrameRateCapTest {
    @TempDir
    Path dir;

    private final MutableClock clock = MutableClock.standard();
    private final FakeGameBridge game = new FakeGameBridge();
    private final FakeOptionsBridge options = new FakeOptionsBridge();
    private VantaServices services;

    @BeforeEach
    void setUp() {
        services = start();
    }

    private VantaServices start() {
        VantaServices s = VantaServices.create(VantaPaths.inGameDirectory(dir), game, options,
                new FakeKeybindBridge(), new FakeResourcePackBridge(), clock);
        s.load();
        return s;
    }

    private void uncap() {
        services.performance().applyFpsLimit(FpsLimitPreset.UNLIMITED);
        assertUncapped("after the uncap");
    }

    private void assertUncapped(String after) {
        assertFalse(options.getBoolean(VanillaOption.VSYNC, true), "VSync turned on " + after);
        assertEquals(FpsLimitPreset.VANILLA_UNLIMITED, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1),
                "a frame-rate limit was set " + after);
    }

    @Test
    void noPerformancePresetOrBoostTouchesTheLimitOrVsync() {
        uncap();
        for (PerformancePreset preset : PerformancePreset.values()) {
            assertFalse(preset.optionValues().containsKey(VanillaOption.FRAMERATE_LIMIT), preset.name());
            assertFalse(preset.optionValues().containsKey(VanillaOption.VSYNC), preset.name());
            services.performance().applyPreset(preset);
            assertUncapped("by preset " + preset);
        }
        services.boostFps();
        assertUncapped("by Boost FPS");
    }

    @Test
    void everyBuiltInProfileIsUncapped() {
        for (String id : BuiltInProfiles.IDS) {
            Profile profile = services.profiles().find(id).orElseThrow();
            assertEquals("unlimited", profile.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET.id())
                    .getAsString(), id);
            assertEquals(260, profile.settings().get(VantaSettings.VIDEO_FRAMERATE_LIMIT.id()).getAsInt(), id);
            assertFalse(profile.settings().get(VantaSettings.VIDEO_VSYNC.id()).getAsBoolean(), id);
        }
        // Activated from Minecraft's defaults (VSync on, 120): every built-in removes the cap.
        for (String id : BuiltInProfiles.IDS) {
            options.setFromGame(VanillaOption.VSYNC, true);
            options.setFromGame(VanillaOption.FRAMERATE_LIMIT, 120);
            assertTrue(services.activateProfile(id), id);
            assertUncapped("by the built-in profile " + id);
        }
    }

    @Test
    void smartBoostNeverWritesTheLimitOrVsyncNotEvenThroughAHandEditedUndo() throws IOException {
        // A hand-edited smart-boost.json that claims Smart Boost wrote VSync and a 60 FPS limit.
        Files.createDirectories(services.paths().smartBoostFile().getParent());
        Files.writeString(services.paths().smartBoostFile(), "{\"schemaVersion\":1,"
                + "\"owned\":{\"vsync\":false,\"framerate_limit\":260,\"render_distance\":12},"
                + "\"undo\":{\"vsync\":true,\"framerate_limit\":60,\"render_distance\":8}}");
        services = start();
        uncap();
        assertTrue(services.smartBoost().state().isOwned(VanillaOption.VSYNC), "the forged state loaded");
        services.smartBoost().undo();
        assertUncapped("by Smart Boost's Undo");
        assertEquals(8, options.getInt(VanillaOption.RENDER_DISTANCE, -1), "a real tuned option is restored");

        Map<VanillaOption, Object> values = Map.of(VanillaOption.VSYNC, true, VanillaOption.FRAMERATE_LIMIT, 60,
                VanillaOption.GRAPHICS_MODE, "FAST");
        assertTrue(services.performance().applyOptions(values, java.util.Set.of()).isEmpty());
        assertUncapped("by Smart Boost's writer");
        assertTrue(SmartBoostTuner.NEVER_WRITTEN.contains(VanillaOption.VSYNC));
        assertTrue(SmartBoostTuner.NEVER_WRITTEN.contains(VanillaOption.FRAMERATE_LIMIT));
    }

    @Test
    void theRenderDistanceAdvisorOnlyMovesTheRenderDistance() {
        uncap();
        services.settings().set(VantaSettings.PERFORMANCE_SMART_BOOST, false);
        services.settings().set(VantaSettings.PERFORMANCE_AUTO_APPLY_RENDER_DISTANCE, true);
        game.fps = 20;
        options.set(VanillaOption.RENDER_DISTANCE, 12);
        game.renderDistance = 12;
        options.setOrder.clear();
        for (int i = 0; i < 220; i++) {
            game.look();
            services.performance().tick();
            clock.advance(50);
        }
        assertEquals(10, options.getInt(VanillaOption.RENDER_DISTANCE, -1), "the advisor acted");
        assertTrue(options.setOrder.stream().allMatch(VanillaOption.RENDER_DISTANCE::equals), "" + options.setOrder);
        assertUncapped("by the render distance advisor");
    }

    @Test
    void nexusActionsThatAreNotAFrameRateRequestLeaveTheCapAlone() {
        uncap();
        NexusActions actions = services.nexusActions();
        NexusUndo undo = services.nexusUndo();
        for (String json : new String[] {
            "{\"type\":\"perf.preset\",\"preset\":\"LOW\"}",
            "{\"type\":\"perf.preset\",\"preset\":\"ULTRA\"}",
            "{\"type\":\"perf.smartBoost\",\"run\":true}",
            "{\"type\":\"hud.preset\",\"preset\":\"minimal\"}",
            "{\"type\":\"profile.switch\",\"name\":\"Recording\"}",
            "{\"type\":\"profile.switch\",\"name\":\"Default\"}",
            "{\"type\":\"setting.set\",\"id\":\"video.renderDistance\",\"value\":8}",
        }) {
            JsonArray array = new JsonArray();
            array.add(JsonParser.parseString(json));
            NexusUndo.Turn turn = undo.begin();
            NexusActions.Outcome outcome = actions.execute(array, turn);
            undo.commit(turn, "test");
            assertTrue(outcome.rejected().isEmpty(), json + " " + outcome);
            assertUncapped("by the Nexus action " + json);
        }
        String prompt = services.nexus().systemPrompt();
        assertTrue(prompt.contains(NexusAssistant.FRAME_RATE_RULE), "the assistant is told never to cap by itself");
        assertTrue(NexusAssistant.FRAME_RATE_RULE.contains("video.vsync"));
    }

    @Test
    void vantasMaxFramerateSliderAtUnlimitedTurnsVsyncOff() {
        assertTrue(options.getBoolean(VanillaOption.VSYNC, false), "Minecraft's default: VSync on");
        int saves = options.saveCount;

        assertTrue(services.settings().setRaw(VantaSettings.VIDEO_FRAMERATE_LIMIT, 260));

        assertUncapped("by the Max Framerate slider");
        assertEquals(FpsLimitPreset.UNLIMITED, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        assertEquals(saves, options.saveCount, "options.txt is saved with the screen's changes, not right away");
        services.saveAll();
        assertEquals(saves + 1, options.saveCount);

        // VSync back on next to an unlimited Max Framerate: the player's choice, nothing turns it off again.
        assertTrue(services.settings().setRaw(VantaSettings.VIDEO_VSYNC, true));
        assertTrue(options.getBoolean(VanillaOption.VSYNC, false));
        assertTrue(services.settings().setRaw(VantaSettings.VIDEO_FRAMERATE_LIMIT, 140));
        assertTrue(options.getBoolean(VanillaOption.VSYNC, false), "a numeric limit leaves VSync alone");
    }

    @Test
    void theFrameRateRowsUnlimitedTurnsVsyncOffAndItsVsyncTurnsItOn() {
        assertTrue(services.settings().set(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, FpsLimitPreset.VSYNC));
        assertTrue(options.getBoolean(VanillaOption.VSYNC, false));
        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));
        assertTrue(services.settings().set(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, FpsLimitPreset.UNLIMITED));
        assertUncapped("check: the row's Unlimited");
    }

    @Test
    void aProfileSavedWithVsyncStillRestoresVsync() {
        services.performance().applyFpsLimit(FpsLimitPreset.VSYNC);
        Profile withVsync = services.profiles().createFromCurrent("Tearing-free", "profile");
        assertTrue(services.activateProfile(BuiltInProfiles.PERFORMANCE));
        assertUncapped("by the Performance profile");
        assertTrue(services.activateProfile(withVsync.id()));
        assertTrue(options.getBoolean(VanillaOption.VSYNC, false), "the player's saved VSync comes back");
        assertEquals(260, options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));
        assertEquals(FpsLimitPreset.VSYNC, services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
    }
}
