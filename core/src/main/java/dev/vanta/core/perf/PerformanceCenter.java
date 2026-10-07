package dev.vanta.core.perf;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.hud.FrameTimeTracker;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service behind the Performance Center screen and the performance HUD widgets: samples frame times, memory and CPU,
 * applies presets through the vanilla options and runs the render distance advisor.
 * <p>
 * Nothing here changes rendering internals; every change is a vanilla option the user could set in Video Settings.
 */
public final class PerformanceCenter {
    /** Baseline target when the frame rate is unlimited. */
    public static final int DEFAULT_TARGET_FPS = 60;

    private final GameBridge game;
    private final OptionsBridge options;
    private final SettingsStore settings;
    private final NotificationCenter notifications;
    private final Clock clock;
    private final FrameTimeTracker frameTimes;
    private final MemorySampler memory;
    private final CpuSampler cpu;
    private final RenderDistanceAdvisor advisor = new RenderDistanceAdvisor();
    private RenderDistanceAdvisor.Suggestion pendingSuggestion;
    private boolean applyingFpsLimit;

    public PerformanceCenter(GameBridge game, OptionsBridge options, SettingsStore settings,
                             NotificationCenter notifications, Clock clock) {
        this(game, options, settings, notifications, clock, new FrameTimeTracker(), new MemorySampler(),
                new CpuSampler());
    }

    public PerformanceCenter(GameBridge game, OptionsBridge options, SettingsStore settings,
                             NotificationCenter notifications, Clock clock, FrameTimeTracker frameTimes,
                             MemorySampler memory, CpuSampler cpu) {
        this.game = Objects.requireNonNull(game, "game");
        this.options = Objects.requireNonNull(options, "options");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.frameTimes = Objects.requireNonNull(frameTimes, "frameTimes");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.cpu = Objects.requireNonNull(cpu, "cpu");
        // The frame-rate limit setting is also edited in the Settings screen and by profiles; a change there has to
        // reach the vanilla options too, or picking "144 FPS" would only rewrite settings.json. Loading
        // settings.json does not fire listeners, so the user's options.txt is never overridden at startup.
        settings.onChange(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, (old, preset) -> syncFpsLimit(preset));
    }

    /** Frame time history. */
    public FrameTimeTracker frameTimes() {
        return frameTimes;
    }

    /** The advisor. */
    public RenderDistanceAdvisor advisor() {
        return advisor;
    }

    /** Records one rendered frame (call from the HUD render pass). */
    public void onFrame(double frameTimeMs) {
        frameTimes.record(frameTimeMs);
    }

    /**
     * Once per client tick: samples fps for the advisor and, when the suggestion setting is on and the player is in a
     * world, evaluates it. With auto-apply enabled the suggestion is applied immediately; otherwise it is surfaced as a
     * notification and kept in {@link #pendingSuggestion()} until applied or dismissed.
     */
    public void tick() {
        if (!game.isInWorld()) {
            return;
        }
        long now = clock.millis();
        advisor.sample(game.fps(), now);
        if (!settings.get(VantaSettings.PERFORMANCE_RENDER_DISTANCE_SUGGESTIONS)) {
            return;
        }
        int current = game.renderDistance();
        Optional<RenderDistanceAdvisor.Suggestion> suggestion = advisor.evaluate(current, targetFps(), isFpsCapped(),
                now);
        if (suggestion.isEmpty()) {
            return;
        }
        if (settings.get(VantaSettings.PERFORMANCE_AUTO_APPLY_RENDER_DISTANCE)) {
            applySuggestion(suggestion.get());
        } else {
            pendingSuggestion = suggestion.get();
            notifications.renderDistanceSuggestion(suggestion.get().from(), suggestion.get().to());
        }
    }

    /** Frame rate the advisor aims for: the fps limit when set, else {@value #DEFAULT_TARGET_FPS}. */
    public int targetFps() {
        int limit = options.getInt(VanillaOption.FRAMERATE_LIMIT, FpsLimitPreset.VANILLA_UNLIMITED);
        return limit >= FpsLimitPreset.VANILLA_UNLIMITED ? DEFAULT_TARGET_FPS : limit;
    }

    /** True when vsync or the fps limit caps the frame rate (so headroom cannot be measured). */
    public boolean isFpsCapped() {
        int limit = options.getInt(VanillaOption.FRAMERATE_LIMIT, FpsLimitPreset.VANILLA_UNLIMITED);
        return options.getBoolean(VanillaOption.VSYNC, false) || limit < FpsLimitPreset.VANILLA_UNLIMITED;
    }

    /** Suggestion waiting for the user's decision. */
    public Optional<RenderDistanceAdvisor.Suggestion> pendingSuggestion() {
        return Optional.ofNullable(pendingSuggestion);
    }

    /** Applies the pending suggestion. */
    public boolean acceptSuggestion() {
        if (pendingSuggestion == null) {
            return false;
        }
        RenderDistanceAdvisor.Suggestion s = pendingSuggestion;
        pendingSuggestion = null;
        applySuggestion(s);
        return true;
    }

    /** Discards the pending suggestion. */
    public void dismissSuggestion() {
        pendingSuggestion = null;
    }

    private void applySuggestion(RenderDistanceAdvisor.Suggestion suggestion) {
        if (options.set(VanillaOption.RENDER_DISTANCE, suggestion.to())) {
            options.save();
            advisor.reset();
            notifications.renderDistanceApplied(suggestion.from(), suggestion.to());
        }
    }

    /** Applies every option of a preset that the running game supports, saves options and notifies. */
    public void applyPreset(PerformancePreset preset) {
        applyPreset(preset, true);
    }

    /**
     * {@link #applyPreset(PerformancePreset)} with the "Preset applied" toast optional, for callers that post their
     * own summary (the one-click Boost).
     */
    public void applyPreset(PerformancePreset preset, boolean notify) {
        Objects.requireNonNull(preset, "preset");
        Map<VanillaOption, Object> values = preset.optionValues();
        // Minecraft 1.21.11's graphics preset (Fast / Fancy / Fabulous) is a bundle: applying it also rewrites the
        // render and simulation distance, clouds, particles and more. Apply it first so the preset's explicit values
        // below are what the player ends up with; the game then reports the graphics preset as "custom".
        Object graphics = values.get(VanillaOption.GRAPHICS_MODE);
        if (graphics != null && options.supports(VanillaOption.GRAPHICS_MODE)) {
            options.set(VanillaOption.GRAPHICS_MODE, graphics);
        }
        for (Map.Entry<VanillaOption, Object> entry : values.entrySet()) {
            if (entry.getKey() != VanillaOption.GRAPHICS_MODE && options.supports(entry.getKey())) {
                options.set(entry.getKey(), entry.getValue());
            }
        }
        options.save();
        settings.set(VantaSettings.PERFORMANCE_PRESET, preset);
        advisor.reset();
        if (notify) {
            notifications.performancePresetApplied(Lang.tr(preset.langKey()));
        }
    }

    /** Applies a frame-rate limit choice (limit + vsync) to the vanilla options and records it in the setting. */
    public void applyFpsLimit(FpsLimitPreset preset) {
        Objects.requireNonNull(preset, "preset");
        writeFpsLimit(preset);
        applyingFpsLimit = true;
        try {
            settings.set(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET, preset);
        } finally {
            applyingFpsLimit = false;
        }
    }

    /**
     * Listener side of {@link #applyFpsLimit}: a change of the setting from elsewhere (Settings screen, profile,
     * search action) writes the options it stands for. Nothing happens while {@link #applyFpsLimit} itself is
     * storing the setting, or when the options already match.
     */
    private void syncFpsLimit(FpsLimitPreset preset) {
        if (applyingFpsLimit || preset == null || matchesOptions(preset)) {
            return;
        }
        writeFpsLimit(preset);
    }

    private void writeFpsLimit(FpsLimitPreset preset) {
        options.set(VanillaOption.FRAMERATE_LIMIT, preset.framerateLimit());
        options.set(VanillaOption.VSYNC, preset.vsync());
        options.save();
        advisor.reset();
    }

    /** True when both vanilla options already hold the preset's values (unsupported options count as matching). */
    private boolean matchesOptions(FpsLimitPreset preset) {
        boolean limit = !options.supports(VanillaOption.FRAMERATE_LIMIT)
                || options.getInt(VanillaOption.FRAMERATE_LIMIT, -1) == preset.framerateLimit();
        boolean vsync = !options.supports(VanillaOption.VSYNC)
                || options.get(VanillaOption.VSYNC).map(v -> v.equals(preset.vsync())).orElse(false);
        return limit && vsync;
    }

    /** The preset whose supported options all match the current values, if any. */
    public Optional<PerformancePreset> detectPreset() {
        outer:
        for (PerformancePreset preset : PerformancePreset.values()) {
            for (Map.Entry<VanillaOption, Object> entry : preset.optionValues().entrySet()) {
                if (!options.supports(entry.getKey())) {
                    continue;
                }
                Optional<Object> current = options.get(entry.getKey());
                Optional<Object> expected = entry.getKey().normalize(entry.getValue());
                if (entry.getKey() == VanillaOption.GRAPHICS_MODE && current.isEmpty()) {
                    // The game reports a graphics preset outside Fast / Fancy / Fabulous ("custom") as soon as any
                    // bundled option differs, which is exactly the state every VANTA preset leaves behind.
                    continue;
                }
                if (current.isEmpty() || expected.isEmpty() || !valuesEqual(current.get(), expected.get())) {
                    continue outer;
                }
            }
            return Optional.of(preset);
        }
        return Optional.empty();
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Math.abs(x.doubleValue() - y.doubleValue()) < 1e-6;
        }
        return Objects.equals(a, b);
    }

    /** Current readings for the UI. */
    public PerformanceSnapshot snapshot() {
        return new PerformanceSnapshot(game.fps(), frameTimes.average(), frameTimes.onePercentLowFrameTime(),
                frameTimes.max(), frameTimes.onePercentLowFps(), memory.sample(), game.renderDistance(),
                game.simulationDistance(), game.entityCount(), cpu.sample(), detectPreset(), pendingSuggestion());
    }
}
