package dev.vanta.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PerformanceTest {
    @TempDir
    Path dir;

    @Test
    void presetValuesMatchTheDocumentedTable() {
        assertEquals(6, PerformancePreset.LOW.renderDistance());
        assertEquals(10, PerformancePreset.BALANCED.renderDistance());
        assertEquals(16, PerformancePreset.HIGH.renderDistance());
        assertEquals(24, PerformancePreset.ULTRA.renderDistance());
        assertEquals(6, PerformancePreset.LOW.simulationDistance());
        assertEquals(8, PerformancePreset.BALANCED.simulationDistance());
        assertEquals(12, PerformancePreset.HIGH.simulationDistance());
        assertEquals(16, PerformancePreset.ULTRA.simulationDistance());
        Map<VanillaOption, Object> low = PerformancePreset.LOW.optionValues();
        assertEquals(60, low.get(VanillaOption.FRAMERATE_LIMIT));
        assertEquals("MINIMAL", low.get(VanillaOption.PARTICLES));
        assertEquals("OFF", low.get(VanillaOption.CLOUDS));
        assertEquals(false, low.get(VanillaOption.SMOOTH_LIGHTING));
        assertEquals(false, low.get(VanillaOption.ENTITY_SHADOWS));
        assertEquals(0.5, low.get(VanillaOption.ENTITY_DISTANCE_SCALING));
        assertEquals(0, low.get(VanillaOption.BIOME_BLEND));
        assertEquals(0, low.get(VanillaOption.MIPMAP_LEVELS));
        assertEquals("FAST", low.get(VanillaOption.GRAPHICS_MODE));
        Map<VanillaOption, Object> balanced = PerformancePreset.BALANCED.optionValues();
        assertEquals(120, balanced.get(VanillaOption.FRAMERATE_LIMIT));
        assertEquals("DECREASED", balanced.get(VanillaOption.PARTICLES));
        assertEquals("FAST", balanced.get(VanillaOption.CLOUDS));
        assertEquals(2, balanced.get(VanillaOption.BIOME_BLEND));
        assertEquals(2, balanced.get(VanillaOption.MIPMAP_LEVELS));
        Map<VanillaOption, Object> high = PerformancePreset.HIGH.optionValues();
        assertEquals(260, high.get(VanillaOption.FRAMERATE_LIMIT));
        assertEquals("ALL", high.get(VanillaOption.PARTICLES));
        assertEquals(4, high.get(VanillaOption.BIOME_BLEND));
        Map<VanillaOption, Object> ultra = PerformancePreset.ULTRA.optionValues();
        assertEquals(1.25, ultra.get(VanillaOption.ENTITY_DISTANCE_SCALING));
        assertEquals(6, ultra.get(VanillaOption.BIOME_BLEND));
        assertEquals("FANCY", ultra.get(VanillaOption.GRAPHICS_MODE), "ULTRA deliberately avoids FABULOUS");
        for (PerformancePreset preset : PerformancePreset.values()) {
            assertEquals(false, preset.optionValues().get(VanillaOption.VSYNC));
            for (Map.Entry<VanillaOption, Object> e : preset.optionValues().entrySet()) {
                assertTrue(e.getKey().normalize(e.getValue()).isPresent(), preset + " " + e.getKey());
            }
            assertTrue(preset.simulationDistance() <= preset.renderDistance());
        }
        assertEquals(Optional.of(PerformancePreset.ULTRA), PerformancePreset.fromId("ultra"));
    }

    @Test
    void fpsLimitPresets() {
        assertEquals(260, FpsLimitPreset.UNLIMITED.framerateLimit());
        assertTrue(FpsLimitPreset.UNLIMITED.isUnlimited());
        assertTrue(FpsLimitPreset.VSYNC.vsync());
        assertEquals(140, FpsLimitPreset.FPS_144.framerateLimit(), "vanilla steps of 10");
        assertEquals(0, FpsLimitPreset.VSYNC.targetFps());
        assertEquals(60, FpsLimitPreset.FPS_60.targetFps());
    }

    @Test
    void advisorSuggestsLowerAfterTenSecondsBelowTarget() {
        RenderDistanceAdvisor advisor = new RenderDistanceAdvisor();
        long t = 0;
        for (int i = 0; i < 100; i++) {
            advisor.sample(40, t);
            if (i < 85) {
                assertTrue(advisor.evaluate(16, 120, false, t).isEmpty(), "needs a full window first");
            }
            t += 100;
        }
        assertEquals(Optional.of(40.0), advisor.windowAverage(t - 100));
        RenderDistanceAdvisor.Suggestion s = advisor.evaluate(16, 120, false, t).orElseThrow();
        assertEquals(RenderDistanceAdvisor.Direction.LOWER, s.direction());
        assertEquals(16, s.from());
        assertEquals(14, s.to());
        assertTrue(advisor.inCooldown(t + 1000));
        for (int i = 0; i < 200; i++) {
            advisor.sample(40, t);
            t += 100;
        }
        assertTrue(advisor.evaluate(14, 120, false, t).isEmpty(), "cooldown of 60 s");
        t += RenderDistanceAdvisor.COOLDOWN_MS;
        for (int i = 0; i < 110; i++) {
            advisor.sample(40, t);
            t += 100;
        }
        assertEquals(12, advisor.evaluate(14, 120, false, t).orElseThrow().to());
    }

    @Test
    void advisorSuggestsHigherOnlyWhenUncapped() {
        RenderDistanceAdvisor advisor = new RenderDistanceAdvisor();
        long t = 0;
        for (int i = 0; i < 110; i++) {
            advisor.sample(240, t);
            t += 100;
        }
        assertTrue(advisor.evaluate(12, 120, true, t).isEmpty(), "capped frame rate hides headroom");
        RenderDistanceAdvisor.Suggestion s = advisor.evaluate(12, 120, false, t).orElseThrow();
        assertEquals(RenderDistanceAdvisor.Direction.HIGHER, s.direction());
        assertEquals(14, s.to());
        assertEquals(Optional.of(s), advisor.lastSuggestion());
        RenderDistanceAdvisor floor = new RenderDistanceAdvisor();
        for (int i = 0; i < 110; i++) {
            floor.sample(10, i * 100L);
        }
        assertTrue(floor.evaluate(RenderDistanceAdvisor.MIN_DISTANCE, 60, false, 11_000).isEmpty(), "never below min");
        assertTrue(floor.evaluate(8, 0, false, 11_000).isEmpty(), "no target → no suggestion");
    }

    @Test
    void centerAppliesPresetsDetectsThemAndRunsAdvisor() {
        MutableClock clock = MutableClock.standard();
        FakeGameBridge game = new FakeGameBridge();
        FakeOptionsBridge options = new FakeOptionsBridge().withoutSupportFor(VanillaOption.PANORAMA_SPEED);
        SettingsStore settings = new SettingsStore(VantaSettings.registry(), new JsonStore(clock),
                dir.resolve("settings.json"), Optional.of(options));
        NotificationCenter notifications = new NotificationCenter(clock);
        PerformanceCenter center = new PerformanceCenter(game, options, settings, notifications, clock,
                new dev.vanta.core.hud.FrameTimeTracker(), new MemorySampler(() -> new MemorySampler.MemorySample(
                        512L << 20, 1024L << 20, 2048L << 20)), new CpuSampler(() -> OptionalDouble.of(0.25)));

        assertTrue(center.detectPreset().isEmpty(), "vanilla defaults match no preset");
        center.applyPreset(PerformancePreset.LOW);
        assertEquals(6, options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertEquals("FAST", options.getEnum(VanillaOption.GRAPHICS_MODE, ""));
        assertEquals(1, options.saveCount);
        assertEquals(PerformancePreset.LOW, settings.get(VantaSettings.PERFORMANCE_PRESET));
        assertEquals(Optional.of(PerformancePreset.LOW), center.detectPreset());
        assertEquals("Preset applied", notifications.visible().get(0).title());
        assertEquals(60, center.targetFps());
        assertTrue(center.isFpsCapped());

        center.applyFpsLimit(FpsLimitPreset.UNLIMITED);
        assertFalse(center.isFpsCapped());
        assertEquals(PerformanceCenter.DEFAULT_TARGET_FPS, center.targetFps());

        center.onFrame(16.0);
        center.onFrame(33.0);
        PerformanceSnapshot snapshot = center.snapshot();
        assertEquals(24.5, snapshot.frameTimeAvgMs(), 1e-9);
        assertEquals(512L << 20, snapshot.memory().used());
        assertEquals(OptionalDouble.of(0.25), snapshot.cpuLoad());
        assertEquals(0.25, snapshot.memory().usedFraction());

        game.fps = 20;
        game.renderDistance = 6;
        options.set(VanillaOption.RENDER_DISTANCE, 12);
        game.renderDistance = 12;
        for (int i = 0; i < 220; i++) {
            center.tick();
            clock.advance(50);
        }
        RenderDistanceAdvisor.Suggestion pending = center.pendingSuggestion().orElseThrow();
        assertEquals(10, pending.to());
        assertEquals(12, options.getInt(VanillaOption.RENDER_DISTANCE, 0), "suggestion only, nothing applied");
        assertTrue(notifications.visible().stream().anyMatch(n -> n.title().equals("Render distance tip")));
        assertTrue(center.acceptSuggestion());
        assertEquals(10, options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertTrue(center.pendingSuggestion().isEmpty());

        settings.set(VantaSettings.PERFORMANCE_AUTO_APPLY_RENDER_DISTANCE, true);
        game.renderDistance = 10;
        clock.advance(RenderDistanceAdvisor.COOLDOWN_MS);
        for (int i = 0; i < 220; i++) {
            center.tick();
            clock.advance(50);
        }
        assertEquals(8, options.getInt(VanillaOption.RENDER_DISTANCE, 0), "auto-apply enabled");
        assertTrue(center.pendingSuggestion().isEmpty());

        settings.set(VantaSettings.PERFORMANCE_RENDER_DISTANCE_SUGGESTIONS, false);
        game.renderDistance = 8;
        clock.advance(RenderDistanceAdvisor.COOLDOWN_MS);
        for (int i = 0; i < 220; i++) {
            center.tick();
            clock.advance(50);
        }
        assertEquals(8, options.getInt(VanillaOption.RENDER_DISTANCE, 0), "disabled: no change");
    }

    @Test
    void samplersAndSystemInfo() {
        MemorySampler.MemorySample sample = new MemorySampler().sample();
        assertTrue(sample.max() > 0);
        assertTrue(sample.used() <= sample.allocated());
        assertEquals(1, MemorySampler.MemorySample.toMiB(1 << 20));
        OptionalDouble cpu = new CpuSampler().sample();
        cpu.ifPresent(v -> assertTrue(v >= 0 && v <= 1));
        SystemInfo info = SystemInfo.current();
        assertTrue(info.cpuCount() >= 1);
        assertTrue(info.javaLabel().startsWith("Java "));
        assertTrue(info.machineLabel().contains("threads"));
    }
}
