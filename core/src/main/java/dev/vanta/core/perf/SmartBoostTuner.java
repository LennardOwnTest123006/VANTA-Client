package dev.vanta.core.perf;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Smart Boost: local, rule-based tuning of the vanilla video options to this PC. No model, no network: it measures
 * real gameplay frames and walks the {@link PerformancePreset} ladder.
 * <h2>Measuring</h2>
 * Frames count only while {@link PerformanceCenter} reports gameplay (in a world, no screen open, not paused, window
 * focused, the player moved or looked around in the last 30 s), so the pause menu, an unfocused window and vanilla's
 * AFK frame limiter never look like a slow PC. After every option change and after joining a world the first
 * {@value #WARMUP_MS} ms of frames are discarded (chunk rebuilds). A window is {@value #WINDOW_MS} ms of gameplay with
 * at least {@value #MIN_WINDOW_FRAMES} frames, summarised by an allocation-free {@link FrameStats}.
 * <h2>Choosing</h2>
 * Target: 60 FPS when the frame rate is unlimited or VSync is on, otherwise the player's limit clamped to 60–144 (or
 * the limit itself below 60). A window passes when the median reaches 1.1 × target (0.95 × target while the limit or
 * VSync pins the frame rate), the 1 % low 0.6 × target and there is at most one hitch per 10 s. It shows headroom when the frame rate is uncapped, the median reaches 1.6 × target
 * and the 1 % low the target. The run starts at the {@link HardwareTier} guess: a pass with headroom tries one preset
 * higher (kept when it passes, reverted otherwise); a fail steps down until a preset passes or Max FPS is reached; at
 * most {@value #MAX_WINDOWS} windows. One toast reports the result.
 * <h2>What it writes</h2>
 * Only {@link #TUNED_OPTIONS}, through {@link PerformanceCenter#applyOptions}. Never the graphics preset, the
 * frame-rate limit or VSync ({@link #NEVER_WRITTEN}): the player's Fast / Fancy / Fabulous choice and cap stay as
 * they are.
 * <h2>Ownership</h2>
 * Every written value is recorded in {@link SmartBoostState}. Before each step and every tick the live values are
 * compared with it; a difference means the player (Video Settings, Sodium's screen, a profile, a VANTA preset)
 * changed the option, which then belongs to the player for good: automatic runs skip it, only the explicit Re-tune
 * takes it back. A different Fast / Fancy / Fabulous than the one recorded after the last write releases everything.
 * <h2>When</h2>
 * Never at start-up and never in a menu: {@link #tick} acts only on gameplay. Automatically once per installed client
 * version (install and every update) after 20 s of gameplay, when {@link VantaSettings#PERFORMANCE_SMART_BOOST} is on
 * and the host allows it; on demand through {@link #retune()}. The optional continuous mode
 * ({@link VantaSettings#PERFORMANCE_SMART_BOOST_ADAPTIVE}) moves the render distance alone by 2 chunks, never below
 * {@value #ADAPTIVE_FLOOR} or above the tuned distance, at most once per 3 minutes and not up within 5 minutes of a
 * step down.
 */
public final class SmartBoostTuner {
    /** Gameplay per measurement window. */
    public static final long WINDOW_MS = 25_000L;
    /** Frames a window needs at least. */
    public static final int MIN_WINDOW_FRAMES = 600;
    /** Frames discarded after an option change or joining a world. */
    public static final long WARMUP_MS = 8_000L;
    /** Frames discarded after gameplay resumes (closing a screen, focusing the window). */
    public static final long RESUME_SETTLE_MS = 1_000L;
    /** Gameplay in the current world before the automatic run starts. */
    public static final long AUTO_START_GAMEPLAY_MS = 20_000L;
    /** Windows one run may measure. */
    public static final int MAX_WINDOWS = 3;
    /** Target when the frame rate is unlimited or VSync is on. */
    public static final int BASE_TARGET_FPS = 60;
    /** Highest target derived from a frame-rate limit. */
    public static final int MAX_TARGET_FPS = 144;

    /** Continuous mode: gameplay per window. */
    public static final long ADAPTIVE_WINDOW_MS = 20_000L;
    /** Continuous mode: frames a window needs at least. */
    public static final int ADAPTIVE_MIN_FRAMES = 200;
    /** Continuous mode: chunks per change. */
    public static final int ADAPTIVE_STEP = 2;
    /** Continuous mode: lowest render distance. */
    public static final int ADAPTIVE_FLOOR = 6;
    /** Continuous mode: highest render distance. */
    public static final int ADAPTIVE_MAX = 32;
    /** Continuous mode: at most one change per this interval. */
    public static final long ADAPTIVE_MIN_INTERVAL_MS = 180_000L;
    /** Continuous mode: no step up within this time after a step down. */
    public static final long ADAPTIVE_UP_AFTER_DOWN_MS = 300_000L;
    /** Continuous mode: consecutive slow windows before a step down. */
    public static final int ADAPTIVE_SUSTAIN_WINDOWS = 2;

    /**
     * JVM property: {@code -Dvanta.smartBoost.auto=false} turns the automatic run off for this game (joined from parts
     * so the translation-key scan does not take it for a lang key).
     */
    public static final String AUTO_PROPERTY = String.join(".", "vanta", "smartBoost", "auto");

    /** Options Smart Boost may write. */
    public static final Set<VanillaOption> TUNED_OPTIONS = Collections.unmodifiableSet(EnumSet.of(
            VanillaOption.RENDER_DISTANCE, VanillaOption.SIMULATION_DISTANCE, VanillaOption.PARTICLES,
            VanillaOption.CLOUDS, VanillaOption.SMOOTH_LIGHTING, VanillaOption.ENTITY_SHADOWS,
            VanillaOption.ENTITY_DISTANCE_SCALING, VanillaOption.BIOME_BLEND, VanillaOption.MIPMAP_LEVELS,
            VanillaOption.MENU_BLUR));
    /** Options Smart Boost never writes. */
    public static final Set<VanillaOption> NEVER_WRITTEN = Collections.unmodifiableSet(EnumSet.of(
            VanillaOption.GRAPHICS_MODE, VanillaOption.FRAMERATE_LIMIT, VanillaOption.VSYNC));

    /** Kind of run. */
    public enum RunKind {
        /** Once per client version; skips options the player took over. */
        AUTOMATIC,
        /** The Re-tune button: takes every option back first. */
        RETUNE
    }

    private final PerformanceCenter perf;
    private final GameBridge game;
    private final OptionsBridge options;
    private final SettingsStore settings;
    private final NotificationCenter notifications;
    private final JsonStore store;
    private final Path file;
    private final SystemInfo system;
    private final boolean autoAllowed;
    private final FrameStats stats = new FrameStats();
    private final Map<PerformancePreset, SmartBoostState.Result> measured = new EnumMap<>(PerformancePreset.class);

    private SmartBoostState state = new SmartBoostState();
    private boolean dirty;

    private long windowMs = WINDOW_MS;
    private int minFrames = MIN_WINDOW_FRAMES;
    private long warmupMs = WARMUP_MS;
    private long autoStartMs = AUTO_START_GAMEPLAY_MS;
    private long adaptiveWindowMs = ADAPTIVE_WINDOW_MS;
    private int adaptiveMinFrames = ADAPTIVE_MIN_FRAMES;

    private RunKind pending;
    private RunKind runKind;
    private boolean running;
    private HardwareTier.Guess guess;
    private PerformancePreset current;
    private PerformancePreset best;
    private int windows;
    private int direction;

    private double warmupLeftMs;
    private boolean inWorld;
    private boolean gated = true;
    private long lastTickAt = Long.MIN_VALUE;
    private long sessionGameplayMs;

    private int slowStreak;
    private long lastAdaptiveChangeAt = Long.MIN_VALUE;
    private long lastAdaptiveDownAt = Long.MIN_VALUE;

    /**
     * @param perf          applies the options ({@link PerformanceCenter#applyOptions}) and knows the frame-rate cap
     * @param store         JSON persistence
     * @param file          {@code smart-boost.json}
     * @param system        threads and heap for the starting guess
     * @param autoAllowed   false when the host forbids the automatic run (game test, {@value #AUTO_PROPERTY}=false)
     */
    public SmartBoostTuner(PerformanceCenter perf, GameBridge game, OptionsBridge options, SettingsStore settings,
                           NotificationCenter notifications, JsonStore store, Path file, SystemInfo system,
                           boolean autoAllowed) {
        this.perf = Objects.requireNonNull(perf, "perf");
        this.game = Objects.requireNonNull(game, "game");
        this.options = Objects.requireNonNull(options, "options");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.store = Objects.requireNonNull(store, "store");
        this.file = Objects.requireNonNull(file, "file");
        this.system = Objects.requireNonNull(system, "system");
        this.autoAllowed = autoAllowed;
    }

    /** False when {@code -Dvanta.smartBoost.auto=false} is set. */
    public static boolean autoAllowedByProperty() {
        return !"false".equalsIgnoreCase(System.getProperty(AUTO_PROPERTY, "true").trim());
    }

    // ---- persistence -------------------------------------------------------------------------------------------

    /** Reads {@code smart-boost.json}. Writes nothing (no option is touched at start-up). */
    public void load() {
        state = SmartBoostState.load(store, file);
        dirty = false;
    }

    /** Writes the state when it changed. */
    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private void save() {
        state.save(store, file);
        dirty = false;
    }

    /** The remembered state (read-only use). */
    public SmartBoostState state() {
        return state;
    }

    // ---- per frame / per tick ----------------------------------------------------------------------------------

    /** One gameplay frame (the center calls this only while its gameplay gate is open). */
    public void onFrame(double frameMs) {
        if (!(frameMs > 0.0) || !isMeasuring()) {
            return;
        }
        if (warmupLeftMs > 0.0) {
            warmupLeftMs -= frameMs;
            return;
        }
        stats.record(frameMs);
    }

    /**
     * Once per client tick.
     *
     * @param now        epoch millis
     * @param inWorldNow a world is loaded
     * @param gameplay   the center's gameplay gate (in a world, playing, recently active)
     */
    public void tick(long now, boolean inWorldNow, boolean gameplay) {
        checkOwnership(now);
        long dt = lastTickAt == Long.MIN_VALUE ? 0L : Math.max(0L, Math.min(1_000L, now - lastTickAt));
        lastTickAt = now;
        if (!inWorldNow) {
            if (inWorld) {
                leaveWorld();
            }
            inWorld = false;
            gated = true;
            return;
        }
        if (!inWorld) {
            inWorld = true;
            sessionGameplayMs = 0L;
            stats.reset();
            warmupLeftMs = warmupMs;
            slowStreak = 0;
        }
        if (!gameplay) {
            gated = true;
            return;
        }
        if (gated) {
            gated = false;
            warmupLeftMs = Math.max(warmupLeftMs, Math.min(warmupMs, RESUME_SETTLE_MS));
        }
        sessionGameplayMs += dt;
        if (!running) {
            if (pending == null && automaticRunDue()) {
                pending = RunKind.AUTOMATIC;
            }
            if (pending != null && (pending == RunKind.RETUNE || sessionGameplayMs >= autoStartMs)) {
                startRun(now);
                return;
            }
        }
        if (running) {
            if (windowComplete(windowMs, minFrames)) {
                evaluateRunWindow(now);
            }
        } else if (adaptiveActive() && windowComplete(adaptiveWindowMs, adaptiveMinFrames)) {
            evaluateAdaptive(now);
        }
    }

    private boolean windowComplete(long ms, int frames) {
        return stats.totalMs() >= ms && stats.count() >= frames;
    }

    private boolean automaticRunDue() {
        return autoAllowed && settings.get(VantaSettings.PERFORMANCE_SMART_BOOST)
                && !state.lastRunClientVersion().map(v -> v.equals(game.clientVersion())).orElse(false);
    }

    private void leaveWorld() {
        if (running) {
            // Aborted: the values already written stay (and stay owned); the run starts over next session.
            running = false;
            if (pending == null) {
                pending = runKind;
            }
            CoreLog.info("Smart Boost: left the world during a measurement; it starts over next time");
        }
        stats.reset();
        if (dirty) {
            save();
        }
    }

    // ---- explicit actions --------------------------------------------------------------------------------------

    /**
     * Re-tune: takes back every option the player changed and measures again, starting with the next gameplay frames.
     */
    public void retune() {
        state.clearReleased();
        dirty = true;
        running = false;
        pending = RunKind.RETUNE;
        save();
    }

    /**
     * Arms a run with the automatic rules (options the player changed are left alone), as after an update. Used by
     * the client game test.
     */
    public void requestAutomaticRun() {
        if (!running) {
            pending = RunKind.AUTOMATIC;
        }
    }

    /**
     * Undo Smart Boost: restores the value from before Smart Boost's first write for every option it still owns, then
     * turns the automatic run off.
     *
     * @return true when an option was restored
     */
    public boolean undo() {
        running = false;
        pending = null;
        Map<VanillaOption, Object> restore = new EnumMap<>(VanillaOption.class);
        for (Map.Entry<VanillaOption, Object> entry : state.undo().entrySet()) {
            if (state.isOwned(entry.getKey()) && liveMatches(entry.getKey(), state.owned().get(entry.getKey()))) {
                restore.put(entry.getKey(), entry.getValue());
            }
        }
        Set<VanillaOption> written = restore.isEmpty() ? Set.of() : perf.applyOptions(restore, Set.of());
        for (VanillaOption option : List.copyOf(state.owned().keySet())) {
            state.forget(option);
        }
        state.setGraphicsAtWrite(null);
        state.setAdaptiveCeiling(0);
        dirty = true;
        save();
        settings.set(VantaSettings.PERFORMANCE_SMART_BOOST, false);
        notifications.smartBoostUndone();
        return !written.isEmpty();
    }

    /**
     * The player applied something explicit (a VANTA preset, Boost FPS): every listed option now belongs to the
     * player (whether Smart Boost wrote it before or not, so a later automatic run never overrides that choice), and a
     * running or armed measurement stops.
     */
    public void releaseAll(Collection<VanillaOption> optionsTouched) {
        boolean changed = false;
        for (VanillaOption option : optionsTouched) {
            if (TUNED_OPTIONS.contains(option) && !state.isReleased(option)) {
                state.release(option);
                changed = true;
            }
        }
        if (running || pending != null) {
            stopRun(false);
            changed = true;
        }
        if (changed) {
            dirty = true;
            save();
        }
    }

    /**
     * Compares the live value of every owned option with the value Smart Boost wrote; a difference releases the
     * option. A Fast / Fancy / Fabulous different from the one recorded after the last write releases everything.
     */
    public void checkOwnership(long now) {
        if (state.owned().isEmpty()) {
            return;
        }
        boolean changed = false;
        Optional<String> recorded = state.graphicsAtWrite();
        Optional<Object> graphics = options.supports(VanillaOption.GRAPHICS_MODE)
                ? options.get(VanillaOption.GRAPHICS_MODE) : Optional.empty();
        if (recorded.isPresent() && graphics.isPresent() && !graphics.get().equals(recorded.get())) {
            for (VanillaOption option : List.copyOf(state.owned().keySet())) {
                state.release(option);
            }
            state.setGraphicsPresetChosen(true);
            changed = true;
        }
        for (Map.Entry<VanillaOption, Object> entry : List.copyOf(state.owned().entrySet())) {
            Optional<Object> live = options.get(entry.getKey());
            if (live.isPresent() && !valuesEqual(live.get(), entry.getValue())) {
                state.release(entry.getKey());
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        dirty = true;
        if (running) {
            stopRun(true);
        }
        save();
    }

    /** Ends a run early: the values written stay, the version counts as tuned. */
    private void stopRun(boolean notify) {
        boolean wasRunning = running;
        running = false;
        pending = null;
        stats.reset();
        if (wasRunning) {
            state.completeRun(game.clientVersion(), lastTickAt == Long.MIN_VALUE ? game.currentTimeMillis()
                    : lastTickAt, null);
            dirty = true;
            if (notify) {
                notifications.smartBoostStopped();
            }
        }
    }

    // ---- the run -----------------------------------------------------------------------------------------------

    private void startRun(long now) {
        runKind = pending;
        pending = null;
        if (runKind == RunKind.RETUNE) {
            state.clearReleased();
        }
        Set<VanillaOption> eligible = EnumSet.copyOf(TUNED_OPTIONS);
        eligible.removeAll(state.released());
        eligible.removeIf(o -> !options.supports(o));
        // Menu blur alone is no video setting worth measuring (a VANTA preset other than Max FPS leaves it out).
        eligible.remove(VanillaOption.MENU_BLUR);
        if (runKind == RunKind.AUTOMATIC && !state.graphicsPresetChosen() && videoSettingsChosenByPlayer()) {
            // A Fast / Fabulous pick or the player's own custom options from before: the automatic run never rewrites
            // them (that would look exactly like "my graphics setting was set back"). Only Re-tune takes over.
            state.setGraphicsPresetChosen(true);
            CoreLog.info("Smart Boost: the video settings are the player's own (graphics preset {}); left alone",
                    currentGraphics().isEmpty() ? "custom" : currentGraphics());
        }
        if (eligible.isEmpty() || runKind == RunKind.AUTOMATIC && state.graphicsPresetChosen()) {
            // The player took over every video option; there is nothing left to tune.
            state.completeRun(game.clientVersion(), now, null);
            dirty = true;
            save();
            return;
        }
        guess = HardwareTier.classify(system, game.gpuRenderer());
        running = true;
        windows = 0;
        direction = 0;
        best = null;
        measured.clear();
        CoreLog.info("Smart Boost: {} run starts at {} (GPU {}, cap {})", runKind, guess.start(), guess.gpu(),
                guess.cap());
        notifications.smartBoostStarted();
        applyStep(guess.start());
    }

    /** Writes the preset's tuned options (skipping the player's), records them as owned and starts a window. */
    private void applyStep(PerformancePreset preset) {
        current = preset;
        Map<VanillaOption, Object> values = new EnumMap<>(VanillaOption.class);
        Set<VanillaOption> restoring = EnumSet.noneOf(VanillaOption.class);
        for (Map.Entry<VanillaOption, Object> entry : preset.optionValues().entrySet()) {
            if (TUNED_OPTIONS.contains(entry.getKey())) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        // An option an earlier step wrote that this preset does not set (menu blur after Max FPS) goes back.
        for (VanillaOption option : state.owned().keySet()) {
            if (!values.containsKey(option) && state.undo().containsKey(option)) {
                values.put(option, state.undo().get(option));
                restoring.add(option);
            }
        }
        Map<VanillaOption, Object> before = new EnumMap<>(VanillaOption.class);
        for (VanillaOption option : values.keySet()) {
            options.get(option).ifPresent(v -> before.put(option, v));
        }
        Set<VanillaOption> written = perf.applyOptions(values, state.released());
        for (VanillaOption option : written) {
            if (restoring.contains(option)) {
                state.forget(option);
                continue;
            }
            Optional<Object> live = options.get(option);
            Object recordedValue = live.orElseGet(() -> option.normalize(values.get(option)).orElse(null));
            if (recordedValue != null) {
                state.own(option, recordedValue, before.get(option));
            }
        }
        state.setGraphicsAtWrite(currentGraphics());
        dirty = true;
        save();
        stats.reset();
        warmupLeftMs = warmupMs;
    }

    /**
     * True when the live video settings are the player's own choice. Before Smart Boost ever wrote: anything but the
     * untouched default Fancy (a Fast or Fabulous pick, or the game's "custom" after hand changes, a VANTA preset or
     * an upgrade from an older Minecraft). Afterwards: a Fast / Fancy / Fabulous different from the one the game
     * reported after Smart Boost's last write (its own writes leave "custom"; per-option changes are tracked by
     * {@link #checkOwnership}).
     */
    private boolean videoSettingsChosenByPlayer() {
        if (!options.supports(VanillaOption.GRAPHICS_MODE)) {
            return false;
        }
        String live = currentGraphics();
        Optional<String> recorded = state.graphicsAtWrite();
        if (recorded.isPresent()) {
            return !live.isEmpty() && !live.equals(recorded.get());
        }
        return !live.equals(String.valueOf(VanillaOption.GRAPHICS_MODE.vanillaDefault()));
    }

    private String currentGraphics() {
        if (!options.supports(VanillaOption.GRAPHICS_MODE)) {
            return "";
        }
        return options.get(VanillaOption.GRAPHICS_MODE).map(Object::toString).orElse("");
    }

    private void evaluateRunWindow(long now) {
        int target = targetFps();
        boolean capped = perf.isFpsCapped();
        SmartBoostState.Result result = new SmartBoostState.Result(current, target, stats.p50Fps(),
                stats.onePercentLowFps());
        measured.put(current, result);
        boolean pass = passes(stats, target, capped);
        boolean headroom = !capped && stats.p50Fps() >= 1.6 * target && stats.onePercentLowFps() >= target;
        windows++;
        CoreLog.info("Smart Boost: {} measured p50 {} FPS, 1% low {} FPS, {} hitches over {} ms (target {}) -> {}{}",
                current, Math.round(stats.p50Fps()), Math.round(stats.onePercentLowFps()), stats.hitchCount(),
                Math.round(stats.totalMs()), target, pass ? "pass" : "fail", headroom ? " with headroom" : "");
        PerformancePreset next = null;
        PerformancePreset finish = null;
        if (direction == 0) {
            if (pass) {
                best = current;
                if (headroom && guess.allowStepUp() && current.ordinal() < guess.cap().ordinal()
                        && windows < MAX_WINDOWS) {
                    direction = 1;
                    next = PerformancePreset.values()[current.ordinal() + 1];
                } else {
                    finish = current;
                }
            } else if (current != PerformancePreset.BOOST && windows < MAX_WINDOWS) {
                direction = -1;
                next = PerformancePreset.values()[current.ordinal() - 1];
            } else {
                finish = current;
            }
        } else if (direction > 0) {
            finish = pass ? current : best;
        } else if (pass || current == PerformancePreset.BOOST || windows >= MAX_WINDOWS) {
            finish = current;
        } else {
            next = PerformancePreset.values()[current.ordinal() - 1];
        }
        if (next != null) {
            applyStep(next);
        } else {
            finishRun(finish, now);
        }
    }

    private void finishRun(PerformancePreset preset, long now) {
        if (preset != current) {
            applyStep(preset);
        }
        running = false;
        stats.reset();
        SmartBoostState.Result result = measured.getOrDefault(preset,
                new SmartBoostState.Result(preset, targetFps(), 0.0, 0.0));
        state.completeRun(game.clientVersion(), now, result);
        state.setAdaptiveCeiling(state.isOwned(VanillaOption.RENDER_DISTANCE) ? preset.renderDistance()
                : options.getInt(VanillaOption.RENDER_DISTANCE, preset.renderDistance()));
        lastAdaptiveChangeAt = now;
        dirty = true;
        save();
        CoreLog.info("Smart Boost picked {}: about {} FPS (target {})", preset, Math.round(result.p50Fps()),
                result.targetFps());
        notifications.smartBoostApplied(Lang.tr(preset.langKey()), Math.round(result.p50Fps()), result.targetFps());
    }

    /**
     * Pass rule of a window: median at least 1.1 × target (0.95 × target while a limit or VSync pins the frame rate at
     * the target, where no margin can show), 1 % low at least 0.6 × target, at most one hitch per 10 s.
     */
    static boolean passes(FrameStats window, int target, boolean capped) {
        int allowedHitches = Math.max(1, (int) (window.totalMs() / 10_000.0));
        double median = capped ? 0.95 : 1.1;
        return window.p50Fps() >= median * target && window.onePercentLowFps() >= 0.6 * target
                && window.hitchCount() <= allowedHitches;
    }

    /** Frame rate Smart Boost aims for with the current limit and VSync. */
    public int targetFps() {
        int limit = options.getInt(VanillaOption.FRAMERATE_LIMIT, FpsLimitPreset.VANILLA_UNLIMITED);
        boolean vsync = options.getBoolean(VanillaOption.VSYNC, false);
        return targetFps(limit, vsync);
    }

    /** 60 when unlimited or VSync; else the limit clamped to 60–144, or the limit itself below 60. */
    public static int targetFps(int limit, boolean vsync) {
        if (vsync || limit >= FpsLimitPreset.VANILLA_UNLIMITED) {
            return BASE_TARGET_FPS;
        }
        if (limit < BASE_TARGET_FPS) {
            return Math.max(1, limit);
        }
        return Math.min(limit, MAX_TARGET_FPS);
    }

    // ---- continuous mode ---------------------------------------------------------------------------------------

    /** True when the continuous mode adjusts the render distance (setting on, no run, render distance owned). */
    public boolean adaptiveActive() {
        return !running && settings.get(VantaSettings.PERFORMANCE_SMART_BOOST_ADAPTIVE)
                && state.isOwned(VanillaOption.RENDER_DISTANCE);
    }

    /** True while Smart Boost drives the render distance (a run or the continuous mode); the advisor stays quiet. */
    public boolean controlsRenderDistance() {
        return running || adaptiveActive();
    }

    private void evaluateAdaptive(long now) {
        int target = targetFps();
        boolean capped = perf.isFpsCapped();
        double p50 = stats.p50Fps();
        double low = stats.onePercentLowFps();
        stats.reset();
        int distance = options.getInt(VanillaOption.RENDER_DISTANCE, -1);
        if (distance < 0) {
            return;
        }
        boolean slow = p50 < 0.8 * target || low < 0.5 * target;
        slowStreak = slow ? slowStreak + 1 : 0;
        boolean intervalOk = lastAdaptiveChangeAt == Long.MIN_VALUE
                || now - lastAdaptiveChangeAt >= ADAPTIVE_MIN_INTERVAL_MS;
        int to = distance;
        if (slow && slowStreak >= ADAPTIVE_SUSTAIN_WINDOWS && intervalOk && distance > ADAPTIVE_FLOOR) {
            to = Math.max(ADAPTIVE_FLOOR, distance - ADAPTIVE_STEP);
        } else if (!slow && !capped && p50 > 1.6 * target && low >= target && intervalOk
                && (lastAdaptiveDownAt == Long.MIN_VALUE || now - lastAdaptiveDownAt >= ADAPTIVE_UP_AFTER_DOWN_MS)) {
            int ceiling = state.adaptiveCeiling() > 0 ? Math.min(state.adaptiveCeiling(), ADAPTIVE_MAX) : ADAPTIVE_MAX;
            if (distance < ceiling) {
                to = Math.min(ceiling, distance + ADAPTIVE_STEP);
            }
        }
        if (to == distance) {
            return;
        }
        Set<VanillaOption> written = perf.applyOptions(Map.of(VanillaOption.RENDER_DISTANCE, to), Set.of());
        if (!written.contains(VanillaOption.RENDER_DISTANCE)) {
            return;
        }
        Object live = options.get(VanillaOption.RENDER_DISTANCE).orElse(to);
        state.own(VanillaOption.RENDER_DISTANCE, live, distance);
        state.setGraphicsAtWrite(currentGraphics());
        lastAdaptiveChangeAt = now;
        if (to < distance) {
            lastAdaptiveDownAt = now;
            slowStreak = 0;
        }
        warmupLeftMs = warmupMs;
        dirty = true;
        save();
        notifications.smartBoostDistance(distance, to);
    }

    // ---- status for the UI -------------------------------------------------------------------------------------

    /** True while a measurement run is in progress. */
    public boolean isRunning() {
        return running;
    }

    /** True when a run is armed but waits for gameplay. */
    public boolean isPending() {
        return pending != null;
    }

    /** True while frames are counted (a run or the continuous mode). */
    public boolean isMeasuring() {
        return running || adaptiveActive();
    }

    /** True while the gameplay gate is closed (menu, pause, unfocused, idle, no world). */
    public boolean isGated() {
        return gated;
    }

    /** Gameplay measured in the current window. */
    public long measuredMs() {
        return Math.round(stats.totalMs());
    }

    /** Length of a run window. */
    public long windowMs() {
        return windowMs;
    }

    /** Preset being measured, when running. */
    public Optional<PerformancePreset> measuringPreset() {
        return running ? Optional.ofNullable(current) : Optional.empty();
    }

    /** The starting guess for this machine (computed live; the GPU string appears once the game started). */
    public HardwareTier.Guess hardwareGuess() {
        return HardwareTier.classify(system, game.gpuRenderer());
    }

    /** Options the player took over, in a stable order. */
    public List<VanillaOption> releasedOptions() {
        List<VanillaOption> out = new ArrayList<>(state.released());
        Collections.sort(out);
        return out;
    }

    /** True when Undo has something to restore. */
    public boolean canUndo() {
        return !state.undo().isEmpty();
    }

    /**
     * Test hook for the client game test and unit tests: shorter windows on a slow software renderer.
     *
     * @param window   gameplay per window (run and continuous mode)
     * @param frames   frames per window at least
     * @param warmup   frames discarded after a change
     * @param autoStart gameplay before the automatic run
     */
    public void configureTimingForTesting(long window, int frames, long warmup, long autoStart) {
        this.windowMs = Math.max(1L, window);
        this.adaptiveWindowMs = Math.max(1L, window);
        this.minFrames = Math.max(1, frames);
        this.adaptiveMinFrames = Math.max(1, frames);
        this.warmupMs = Math.max(0L, warmup);
        this.autoStartMs = Math.max(0L, autoStart);
    }

    private boolean liveMatches(VanillaOption option, Object recorded) {
        Optional<Object> live = options.get(option);
        return live.isEmpty() || recorded == null || valuesEqual(live.get(), recorded);
    }

    static boolean valuesEqual(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Math.abs(x.doubleValue() - y.doubleValue()) < 1e-6;
        }
        return Objects.equals(a, b);
    }
}
