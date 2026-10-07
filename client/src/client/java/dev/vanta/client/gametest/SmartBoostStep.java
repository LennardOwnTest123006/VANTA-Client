package dev.vanta.client.gametest;

import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.perf.PerformanceCenter;
import dev.vanta.core.perf.SmartBoostState;
import dev.vanta.core.perf.SmartBoostTuner;
import dev.vanta.core.screen.VantaServices;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Smart Boost in the real game (runs inside the test world, on the CI software renderer):
 * <ol>
 *   <li>Re-tune with a shortened measurement ({@value #WINDOW_MS} ms windows) must produce a result, write its render
 *       distance and {@code smart-boost.json}, and leave the graphics preset, the frame-rate limit and VSync exactly as
 *       they were;</li>
 *   <li>the render distance is then changed by hand (as in Video Settings) and a second, automatic-rules run must leave
 *       it alone;</li>
 *   <li>Undo restores the values from before the first run (the simulation distance is checked).</li>
 * </ol>
 * The automatic run itself is off for the whole game test ({@code -Dvanta.gametest=true}); the player here stands
 * still and the window may not have the focus, so the gate is relaxed to "a world is loaded" through the core's
 * test hook while this step runs.
 */
final class SmartBoostStep {
    /** Gameplay per measurement window. */
    static final long WINDOW_MS = 3_000L;
    /** Frames a window needs at least (the software renderer is slow). */
    static final int MIN_FRAMES = 20;
    /** Frames discarded after each change. */
    static final long WARMUP_MS = 500L;
    /** Ticks one run may take (90 s). */
    static final int RUN_TIMEOUT_TICKS = 1_800;

    private SmartBoostStep() {
    }

    private record Choices(Optional<Object> graphics, Optional<Object> framerateLimit, Optional<Object> vsync,
                           Optional<Object> simulationDistance) {
        static Choices read(OptionsBridge options) {
            return new Choices(options.get(VanillaOption.GRAPHICS_MODE), options.get(VanillaOption.FRAMERATE_LIMIT),
                    options.get(VanillaOption.VSYNC), options.get(VanillaOption.SIMULATION_DISTANCE));
        }
    }

    static void run(ClientGameTestContext context, VantaServices services) {
        SmartBoostTuner tuner = services.smartBoost();
        PerformanceCenter performance = services.performance();
        OptionsBridge options = services.options();
        context.setScreen(() -> null);
        context.waitForScreen(null);
        boolean gate = context.computeOnClient(client -> services.game().isGameplayActive());
        String gpu = context.computeOnClient(client -> services.game().gpuRenderer().orElse("unknown"));
        VantaClientGameTest.step("smart boost: GPU renderer " + gpu + ", gameplay gate (no screen, not paused, "
                + "focused) " + gate + ", starting guess " + context.computeOnClient(client -> tuner.hardwareGuess()));
        Choices before = context.computeOnClient(client -> Choices.read(options));
        try {
            context.runOnClient(client -> {
                tuner.configureTimingForTesting(WINDOW_MS, MIN_FRAMES, WARMUP_MS, 0L);
                performance.relaxGameplayGateForTesting(true);
                tuner.retune();
            });
            awaitRun(context, tuner, "Re-tune");
            SmartBoostState.Result result = context.computeOnClient(client -> tuner.state().result()).orElse(null);
            VantaClientGameTest.check(result != null, "Smart Boost finished without a result");
            int distance = context.computeOnClient(client -> client.options.renderDistance().get());
            VantaClientGameTest.check(distance == result.preset().renderDistance(), "Smart Boost picked "
                    + result.preset() + " but the render distance is " + distance);
            Choices after = context.computeOnClient(client -> Choices.read(options));
            VantaClientGameTest.check(after.graphics().equals(before.graphics())
                            && after.framerateLimit().equals(before.framerateLimit())
                            && after.vsync().equals(before.vsync()),
                    "Smart Boost changed the player's choices: before " + before + ", after " + after);
            Path file = services.paths().smartBoostFile();
            VantaClientGameTest.check(Files.isRegularFile(file) && read(file).contains("\"owned\""),
                    "smart-boost.json was not written to " + file);
            VantaClientGameTest.step("smart boost: picked " + result.preset() + " (median "
                    + Math.round(result.p50Fps()) + " FPS, 1% low " + Math.round(result.onePercentLowFps())
                    + " FPS, target " + result.targetFps() + "), render distance " + distance
                    + "; graphics, frame-rate limit and VSync untouched");

            int byHand = distance == 12 ? 14 : 12;
            context.runOnClient(client -> client.options.renderDistance().set(byHand));
            context.waitTicks(2);
            boolean released = context.computeOnClient(client ->
                    tuner.state().isReleased(VanillaOption.RENDER_DISTANCE));
            VantaClientGameTest.check(released, "a hand-changed render distance was not released by Smart Boost");
            context.runOnClient(client -> tuner.requestAutomaticRun());
            awaitRun(context, tuner, "automatic run after a hand change");
            int kept = context.computeOnClient(client -> client.options.renderDistance().get());
            VantaClientGameTest.check(kept == byHand, "Smart Boost overwrote the hand-set render distance "
                    + byHand + " with " + kept);
            VantaClientGameTest.step("smart boost: hand-set render distance " + byHand + " left alone by a second run");

            boolean graphicsChosen = context.computeOnClient(client -> tuner.state().graphicsPresetChosen());
            if (graphicsChosen) {
                // The game reported a different Fast / Fancy / Fabulous after the hand change; Smart Boost then lets
                // go of every option and Undo has nothing left to restore.
                VantaClientGameTest.warn("smart boost: the game reported a new graphics preset after the hand "
                        + "change; Undo check skipped");
                return;
            }
            context.runOnClient(client -> tuner.undo());
            context.waitTick();
            Optional<Object> simulation = context.computeOnClient(client ->
                    options.get(VanillaOption.SIMULATION_DISTANCE));
            VantaClientGameTest.check(simulation.equals(before.simulationDistance()), "Undo should restore the "
                    + "simulation distance " + before.simulationDistance() + " but it is " + simulation);
            VantaClientGameTest.step("smart boost: Undo restored the earlier video options");
        } finally {
            context.runOnClient(client -> {
                performance.relaxGameplayGateForTesting(false);
                tuner.configureTimingForTesting(SmartBoostTuner.WINDOW_MS, SmartBoostTuner.MIN_WINDOW_FRAMES,
                        SmartBoostTuner.WARMUP_MS, SmartBoostTuner.AUTO_START_GAMEPLAY_MS);
            });
        }
    }

    private static void awaitRun(ClientGameTestContext context, SmartBoostTuner tuner, String what) {
        boolean done = VantaClientGameTest.pollFor(context, client -> !tuner.isRunning() && !tuner.isPending(),
                RUN_TIMEOUT_TICKS);
        VantaClientGameTest.check(done, "Smart Boost " + what + " did not finish within " + RUN_TIMEOUT_TICKS
                + " ticks (measured " + tuner.measuredMs() + " ms of the current window)");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return "";
        }
    }
}
