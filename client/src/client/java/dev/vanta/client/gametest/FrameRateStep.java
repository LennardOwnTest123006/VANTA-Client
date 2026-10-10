package dev.vanta.client.gametest;

import static dev.vanta.client.gametest.VantaClientGameTest.check;
import static dev.vanta.client.gametest.VantaClientGameTest.step;
import static dev.vanta.client.gametest.VantaClientGameTest.warn;

import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.FrameRateUncap;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * The 1.5.0 frame-rate uncap in the real game (runs inside the test world, no screen open):
 * <ol>
 *   <li>the one-time start-up check ({@link FrameRateUncap}, run from {@code VantaRuntime.onClientStarted}) left its
 *       record in {@code frame-rate.json}; what it found is logged;</li>
 *   <li>Fabric's client game-test runner puts back the options it captured when the game read {@code options.txt}
 *       (before VANTA's start-up check) at the start of every test ({@code restoreDefaultGameOptions}), so the step
 *       measures the frame rate with those, then applies the start-up check's own call,
 *       {@code PerformanceCenter.applyFpsLimit(UNLIMITED)}, to the live game;</li>
 *   <li>VSync must be off, Max Framerate 260 (Unlimited) in the game, in VANTA's frame-rate choice and in
 *       {@code options.txt}, and Minecraft's frame-rate limiter must report 260 while no screen is open (vanilla's
 *       "Reduce FPS when: Away from keyboard" is set to "Minimized" for that one reading, because the test player
 *       sends no input, and put back right after);</li>
 *   <li>the measured frame rate is logged before and after. The CI runner renders in software in lock-step with the
 *       test thread, so the numbers are reported, not judged.</li>
 * </ol>
 * Menus without a world stay at vanilla's own 60 FPS menu limit; that is Minecraft's design and not checked here.
 */
final class FrameRateStep {
    /** Ticks the frame rate is measured over (Minecraft updates its FPS counter once per second). */
    static final int MEASURE_TICKS = 60;

    private FrameRateStep() {
    }

    /** VSync, Max Framerate and the limiter's current limit, as the game reports them. */
    private record Live(boolean vsync, int maxFramerate, int limiter) {
        static Live read(Minecraft client) {
            return new Live(client.options.enableVsync().get(), client.options.framerateLimit().get(),
                    client.getFramerateLimitTracker().getFramerateLimit());
        }

        @Override
        public String toString() {
            return "VSync " + (vsync ? "on" : "off") + ", Max Framerate " + maxFramerate + ", limiter " + limiter;
        }
    }

    static void run(ClientGameTestContext context, VantaServices services) {
        context.setScreen(() -> null);
        context.waitForScreen(null);

        Optional<FrameRateUncap.Record> record = context.computeOnClient(client ->
                services.frameRateUncap().record());
        check(record.isPresent(), "frame rate: the start-up check left no record in "
                + services.paths().frameRateFile());
        FrameRateUncap.Record r = record.get();
        step("frame rate: start-up check recorded for client " + r.clientVersion() + " (it found VSync "
                + (r.vsyncBefore() ? "on" : "off") + ", Max Framerate " + r.limitBefore() + "; "
                + (r.changed() ? "uncapped them" : "nothing to change") + ")");

        Live restored = context.computeOnClient(Live::read);
        int fpsRestored = measureFps(context);
        step("frame rate: the game-test runner restored the options captured before the start-up check: " + restored
                + "; " + fpsRestored + " FPS measured");

        context.runOnClient(client -> services.performance().applyFpsLimit(FpsLimitPreset.UNLIMITED));
        context.waitTicks(2);

        OptionsBridge options = services.options();
        boolean vsync = context.computeOnClient(client -> client.options.enableVsync().get());
        int maxFramerate = context.computeOnClient(client -> client.options.framerateLimit().get());
        check(!vsync, "frame rate: VSync is still on after the uncap");
        check(maxFramerate == FpsLimitPreset.VANILLA_UNLIMITED, "frame rate: Max Framerate is " + maxFramerate
                + " after the uncap, expected " + FpsLimitPreset.VANILLA_UNLIMITED + " (Unlimited)");
        FpsLimitPreset choice = context.computeOnClient(client ->
                services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        check(choice == FpsLimitPreset.UNLIMITED, "frame rate: VANTA's frame-rate choice is " + choice
                + ", expected UNLIMITED");
        Path optionsFile = FabricLoader.getInstance().getGameDir().resolve("options.txt");
        List<String> lines = readLines(optionsFile);
        check(lines.contains("enableVsync:false") && lines.contains("maxFps:" + FpsLimitPreset.VANILLA_UNLIMITED),
                "frame rate: options.txt does not hold enableVsync:false and maxFps:260 after the uncap");

        Optional<Object> inactivity = context.computeOnClient(client ->
                options.get(VanillaOption.INACTIVITY_FPS_LIMIT));
        int limiter;
        try {
            context.runOnClient(client -> options.set(VanillaOption.INACTIVITY_FPS_LIMIT, "MINIMIZED"));
            context.waitTick();
            limiter = context.computeOnClient(client -> client.screen == null
                    ? client.getFramerateLimitTracker().getFramerateLimit() : -1);
        } finally {
            inactivity.ifPresent(value -> context.runOnClient(client ->
                    options.set(VanillaOption.INACTIVITY_FPS_LIMIT, value)));
        }
        check(limiter == FpsLimitPreset.VANILLA_UNLIMITED, "frame rate: Minecraft's frame-rate limiter reports "
                + limiter + " in the world with no screen open, expected " + FpsLimitPreset.VANILLA_UNLIMITED
                + " (-1: a screen was open)");

        int fpsUncapped = measureFps(context);
        Live after = context.computeOnClient(Live::read);
        step("frame rate: uncapped in the world (" + after + " with no screen; Reduce FPS when "
                + inactivity.orElse("?") + " restored); " + fpsUncapped + " FPS measured (was " + fpsRestored
                + " with the runner's options; software renderer in lock-step, reported only)");
        if (fpsUncapped < fpsRestored) {
            warn("frame rate: fewer FPS uncapped than capped on the CI renderer (" + fpsUncapped + " < " + fpsRestored
                    + "); not judged, the runner does not stand for a player's PC");
        }
    }

    /** Waits {@value #MEASURE_TICKS} ticks with no screen open and returns Minecraft's FPS counter. */
    private static int measureFps(ClientGameTestContext context) {
        context.waitTicks(MEASURE_TICKS);
        return context.computeOnClient(Minecraft::getFps);
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            warn("frame rate: could not read " + file + ": " + e);
            return List.of();
        }
    }
}
