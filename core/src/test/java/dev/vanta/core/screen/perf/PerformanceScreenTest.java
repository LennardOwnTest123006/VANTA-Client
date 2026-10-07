package dev.vanta.core.screen.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.modrinth.FakeModPlatform;
import dev.vanta.core.modrinth.FakeModrinthApi;
import dev.vanta.core.modrinth.ModrinthLibrary;
import dev.vanta.core.modrinth.ModrinthService;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.screen.common.SettingRow;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.widget.Toggle;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerformanceScreenTest {
    @TempDir
    Path dir;
    private ScreenTestSupport t;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
    }

    @Test
    void presetDiffListsExactlyWhatChanges() {
        PresetDiff diff = PresetDiff.compute(PerformancePreset.BALANCED, t.options);
        assertEquals(PerformancePreset.BALANCED.optionValues().size(), diff.rows().size());
        PresetDiff.Row render = row(diff, VanillaOption.RENDER_DISTANCE);
        assertTrue(render.changes());
        assertEquals(12, render.current().orElseThrow());
        assertEquals(10, render.target());
        assertTrue(diff.rows().stream().noneMatch(r -> r.option() == VanillaOption.FRAMERATE_LIMIT
                || r.option() == VanillaOption.VSYNC), "presets never touch the frame-rate limit or VSync");
        PresetDiff.Row particles = row(diff, VanillaOption.PARTICLES);
        assertTrue(particles.changes());
        assertEquals(diff.rows().stream().filter(PresetDiff.Row::changes).count(), diff.changeCount());
        assertFalse(diff.isNoop());

        t.services.performance().applyPreset(PerformancePreset.BALANCED);
        assertTrue(PresetDiff.compute(PerformancePreset.BALANCED, t.options).isNoop());

        t.options.withoutSupportFor(VanillaOption.BIOME_BLEND);
        PresetDiff partial = PresetDiff.compute(PerformancePreset.ULTRA, t.options);
        PresetDiff.Row biome = row(partial, VanillaOption.BIOME_BLEND);
        assertFalse(biome.supported());
        assertFalse(biome.changes(), "unsupported options never count as changes");
        assertTrue(biome.current().isEmpty());
    }

    private static PresetDiff.Row row(PresetDiff diff, VanillaOption option) {
        return diff.rows().stream().filter(r -> r.option() == option).findFirst().orElseThrow();
    }

    @Test
    void selectingAPresetShowsItsDiffAndApplyingWritesVanillaOptions() {
        PerformanceScreen screen = t.show(ScreenId.PERFORMANCE, 854, 480);
        assertEquals(PerformancePreset.BALANCED, screen.selectedPreset(), "falls back to the setting's default");
        screen.presetTabs().select(screen.context(), PerformancePreset.LOW.ordinal());
        assertEquals(PerformancePreset.LOW, screen.selectedPreset());
        assertTrue(screen.applyPresetButton().isEnabled());
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasText("6 chunks"), "diff table shows the target value");
        assertTrue(canvas.hasTextContaining("Custom"), "no preset matches the vanilla defaults");

        screen.scrollPanel().scrollIntoView(screen.context(), screen.applyPresetButton().bounds());
        t.advance(screen, 500);
        assertTrue(screen.scrollPanel().scrollY() > 0, "the preset card sits below the fold");
        assertTrue(screen.applyPresetButton().bounds().bottom() <= screen.scrollPanel().bounds().bottom());
        ScreenTestSupport.click(screen, screen.applyPresetButton());
        assertEquals(6, t.options.getInt(VanillaOption.RENDER_DISTANCE, -1));
        assertEquals("MINIMAL", t.options.getEnum(VanillaOption.PARTICLES, ""));
        assertEquals(true, t.options.get(VanillaOption.VSYNC).orElseThrow(), "VSync is left as it was");
        assertEquals(120, t.options.getInt(VanillaOption.FRAMERATE_LIMIT, -1), "no cap came with the preset");
        assertEquals(PerformancePreset.LOW, t.services.settings().get(VantaSettings.PERFORMANCE_PRESET));
        assertTrue(screen.selectedDiff().isNoop());
        assertFalse(screen.applyPresetButton().isEnabled(), "nothing left to apply");
        assertTrue(t.services.notifications().visible().stream().anyMatch(n -> n.title().equals("Preset applied")));
        assertTrue(t.frame(screen, -1000, -1000).hasTextContaining("Current preset: Low"));
    }

    @Test
    void boostButtonAppliesMaxFpsWithoutACapAndSelectsTheBoostTab() {
        PerformanceScreen screen = t.show(ScreenId.PERFORMANCE, 854, 480);
        assertEquals("Boost FPS", screen.boostFpsButton().label());
        screen.scrollPanel().scrollIntoView(screen.context(), screen.boostFpsButton().bounds());
        t.advance(screen, 500);
        ScreenTestSupport.click(screen, screen.boostFpsButton());
        assertEquals(5, t.options.getInt(VanillaOption.RENDER_DISTANCE, -1));
        assertEquals(260, t.options.getInt(VanillaOption.FRAMERATE_LIMIT, -1), "cap removed");
        assertEquals(false, t.options.get(VanillaOption.VSYNC).orElseThrow());
        assertEquals(PerformancePreset.BOOST, screen.selectedPreset());
        assertEquals(PerformancePreset.BOOST.ordinal(), screen.presetTabs().selected());
        assertTrue(screen.selectedDiff().isNoop());
        assertTrue(t.services.notifications().visible().stream().anyMatch(n -> n.title().equals("Boost applied")));
        assertTrue(t.frame(screen, -1000, -1000).hasTextContaining("Current preset: Max FPS"));
    }

    @Test
    void boostButtonWaitsWhileThePackInstalls() {
        Deque<Runnable> worker = new ArrayDeque<>();
        FakeModPlatform platform = new FakeModPlatform(dir);
        ModrinthService queued = new ModrinthService(FakeModrinthApi.standard(),
                new ModrinthLibrary(dir, t.services.jsonStore()), platform, t.services.notifications(), worker::add,
                Runnable::run, t.clock);
        t.services.setModrinth(queued);
        PerformanceScreen screen = t.show(ScreenId.PERFORMANCE, 854, 480);
        assertTrue(screen.boostFpsButton().isEnabled());

        screen.boostFps();
        assertTrue(queued.isBusy(), "the pack is downloading");
        assertFalse(screen.boostFpsButton().isEnabled(), "no second click while the install runs");
        screen.tick();
        assertFalse(screen.boostFpsButton().isEnabled());

        while (!worker.isEmpty()) {
            worker.poll().run();
        }
        screen.tick();
        assertTrue(screen.boostFpsButton().isEnabled(), "unlocked once the install finished");
    }

    @Test
    void quickOptionRowsAreBoundToTheOptionsBridge() {
        PerformanceScreen screen = t.show(ScreenId.PERFORMANCE, 854, 480);
        List<SettingRow> rows = screen.quickRows();
        assertEquals(12, rows.size(), "two distance rows plus ten quick options");
        SettingRow shadows = rows.stream().filter(r -> r.setting().id().equals("video.entityShadows")).findFirst()
                .orElseThrow();
        ((Toggle) shadows.editor()).toggle(screen.context());
        assertEquals(false, t.options.get(VanillaOption.ENTITY_SHADOWS).orElseThrow());
        assertTrue(rows.get(0).isCompact() && rows.get(0).isStacked(), "distance sliders use the stacked layout");
    }

    @Test
    void ticksSampleTheFrameRateIntoASixtySampleHistory() {
        PerformanceScreen screen = t.show(ScreenId.PERFORMANCE, 854, 480);
        assertEquals(0, screen.fpsHistory().length, "no frame samples recorded yet");
        for (int i = 0; i < 70; i++) {
            t.game.fps = 100 + i;
            screen.tick();
        }
        int[] history = screen.fpsHistory();
        assertEquals(PerformanceScreen.HISTORY, history.length);
        assertEquals(110, history[0], "oldest kept sample");
        assertEquals(169, history[history.length - 1]);
        assertEquals(169, screen.snapshot().fps());
    }

    @Test
    void seedsTheGraphFromRecordedFrameTimes() {
        for (int i = 0; i < 30; i++) {
            t.services.performance().onFrame(10.0);
        }
        PerformanceScreen screen = t.show(ScreenId.PERFORMANCE, 854, 480);
        assertEquals(30, screen.fpsHistory().length);
        assertEquals(100, screen.fpsHistory()[0]);
    }

    @Test
    void renderDistanceSuggestionIsShownAndAppliedOnlyOnRequest() {
        PerformanceScreen screen = t.show(ScreenId.PERFORMANCE, 854, 480);
        assertTrue(screen.suggestionBanner().isEmpty());
        t.game.fps = 30;
        for (int i = 0; i < 30; i++) {
            t.clock.advance(500);
            t.game.look(); // the advisor only samples gameplay (an idle player is ignored)
            t.services.performance().tick();
        }
        assertTrue(t.services.performance().pendingSuggestion().isPresent(), "advisor suggests a lower distance");
        assertEquals(12, t.options.getInt(VanillaOption.RENDER_DISTANCE, -1), "never applied automatically");
        screen.tick();
        assertTrue(screen.suggestionBanner().isPresent());
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasTextContaining("Lower render distance to 10 chunks"),
                () -> "banner " + screen.suggestionBanner().orElseThrow().bounds() + " texts " + canvas.texts());
        assertTrue(canvas.hasText("Apply suggestion"), () -> "texts " + canvas.texts());
        var banner = screen.suggestionBanner().orElseThrow();
        var apply = (dev.vanta.core.ui.widget.Button) banner.actionRow().children().get(0);
        ScreenTestSupport.click(screen, apply);
        assertEquals(10, t.options.getInt(VanillaOption.RENDER_DISTANCE, -1), () -> "apply=" + apply.bounds()
                + " banner=" + banner.bounds() + " row=" + banner.actionRow().bounds() + " hit="
                + screen.root().hitTest(apply.bounds().centerX(), apply.bounds().centerY()) + " pending="
                + t.services.performance().pendingSuggestion());
        assertTrue(screen.suggestionBanner().isEmpty());
    }

    @Test
    void titleScreenShowsAnHonestNoWorldStateAndSmallWindowsStack() {
        t.game.onTitleScreen();
        PerformanceScreen screen = t.show(ScreenId.PERFORMANCE, 854, 480);
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasText("Join a world to see live metrics"));
        assertTrue(canvas.hasText("n/a"), "entity count unknown outside a world");
        assertTrue(canvas.hasText("Vanilla options only"));

        PerformanceScreen small = t.show(ScreenId.PERFORMANCE, 427, 240);
        var fps = small.root().findById("perf.fps");
        var memory = small.root().findById("perf.memory");
        assertTrue(memory.bounds().y() > fps.bounds().y(), "cards wrap onto more rows on narrow screens");
    }

    @Test
    void smartBoostCardShowsTheResultInPlainWordsAndItsButtonsWork() {
        PerformanceScreen screen = t.show(ScreenId.PERFORMANCE, 854, 480);
        assertTrue(screen.root().findById("perf.smartBoost") != null, "Smart Boost card present");
        assertTrue(screen.smartBoostStatus().startsWith("Not tuned yet"), screen.smartBoostStatus());
        assertFalse(screen.smartBoostUndoButton().isEnabled(), "nothing to undo yet");
        assertEquals(List.of(VantaSettings.PERFORMANCE_SMART_BOOST, VantaSettings.PERFORMANCE_SMART_BOOST_ADAPTIVE),
                screen.smartBoostRows().stream().map(SettingRow::setting).toList());
        assertEquals("Re-tune (overrides your manual video choices)", screen.smartBoostRetuneButton().label());

        screen.scrollPanel().scrollIntoView(screen.context(), screen.smartBoostRetuneButton().bounds());
        t.advance(screen, 500);
        ScreenTestSupport.click(screen, screen.smartBoostRetuneButton());
        assertTrue(t.services.smartBoost().isPending());
        assertEquals("Starts when you play", screen.smartBoostStatus());

        // A short measurement of a steady 125 FPS (the options keep vanilla's 120 FPS limit with VSync on).
        t.services.smartBoost().configureTimingForTesting(2_000, 20, 0, 0);
        for (int i = 0; i < 200 && (t.services.smartBoost().isRunning() || t.services.smartBoost().isPending()); i++) {
            t.game.look();
            t.services.performance().tick();
            for (int f = 0; f < 6; f++) {
                t.services.performance().onFrame(8.0);
            }
            t.clock.advance(50);
        }
        screen.tick();
        assertTrue(screen.smartBoostStatus().startsWith("Smart Boost picked Balanced: about 125 FPS measured "
                + "(target 60 FPS)"), screen.smartBoostStatus());
        assertEquals(10, t.options.getInt(VanillaOption.RENDER_DISTANCE, -1));
        assertTrue(screen.smartBoostUndoButton().isEnabled());
        assertTrue(t.frame(screen, -1000, -1000).hasTextContaining("Smart Boost picked Balanced"));

        t.options.set(VanillaOption.PARTICLES, "ALL"); // the player changes it by hand
        t.services.performance().tick();
        screen.tick();
        assertTrue(t.frame(screen, -1000, -1000).hasTextContaining("Left alone (you changed them): Particles"));

        screen.scrollPanel().scrollIntoView(screen.context(), screen.smartBoostUndoButton().bounds());
        t.advance(screen, 500);
        ScreenTestSupport.click(screen, screen.smartBoostUndoButton());
        assertEquals(12, t.options.getInt(VanillaOption.RENDER_DISTANCE, -1), "Undo restored the distance");
        assertEquals("ALL", t.options.getEnum(VanillaOption.PARTICLES, ""), "the player's own change stays");
        assertFalse(t.services.settings().get(VantaSettings.PERFORMANCE_SMART_BOOST), "automatic tuning off");
        assertFalse(screen.smartBoostUndoButton().isEnabled());
    }
}
