package dev.vanta.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.FakeOptionsBridge;
import dev.vanta.core.bridge.FakeResourcePackBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Smart Boost with simulated machines: the frame time of every frame is a function of the options currently set,
 * fed through {@link PerformanceCenter#onFrame} exactly like the HUD does in game, with a tick every 50 ms.
 */
class SmartBoostTunerTest {
    private static final long GIB = HardwareTier.GIB;
    private static final SystemInfo STRONG = new SystemInfo("21", "t", "Windows", "11", "x64", 16, 8 * GIB, 32 * GIB);
    private static final SystemInfo MID = new SystemInfo("21", "t", "Windows", "11", "x64", 8, 4 * GIB, 16 * GIB);
    private static final SystemInfo WEAK = new SystemInfo("21", "t", "Windows", "10", "x64", 4, 2 * GIB, 8 * GIB);
    private static final String RTX = "NVIDIA GeForce RTX 3060/PCIe/SSE2";
    private static final String IRIS = "Intel(R) Iris(R) Xe Graphics";
    private static final String UHD = "Mesa Intel(R) UHD Graphics 620 (KBL GT2)";

    @TempDir
    Path dir;
    private int rigs;

    /** A machine whose frame rate is {@code k / renderDistance}, capped at 500 FPS. */
    private static ToDoubleFunction<FakeOptionsBridge> fpsOverDistance(double k) {
        return o -> 1000.0 / Math.min(500.0, k / Math.max(1, o.getInt(VanillaOption.RENDER_DISTANCE, 12)));
    }

    private final class Rig {
        final MutableClock clock = MutableClock.standard();
        final FakeGameBridge game = new FakeGameBridge();
        final FakeOptionsBridge options = new FakeOptionsBridge();
        final JsonStore store = new JsonStore(clock);
        final SettingsStore settings;
        final NotificationCenter notifications = new NotificationCenter(clock);
        final PerformanceCenter center;
        final SmartBoostTuner tuner;
        final Path file = dir.resolve("rig" + (++rigs)).resolve("smart-boost.json");
        ToDoubleFunction<FakeOptionsBridge> machine = fpsOverDistance(1000);
        boolean looking = true;
        private double frameDebt;

        Rig(SystemInfo system, String gpu, boolean autoAllowed) {
            settings = new SettingsStore(VantaSettings.registry(), store, file.resolveSibling("settings.json"),
                    Optional.of(options));
            center = new PerformanceCenter(game, options, settings, notifications, clock);
            tuner = new SmartBoostTuner(center, game, options, settings, notifications, store, file, system,
                    autoAllowed);
            center.attachSmartBoost(tuner);
            tuner.load();
            game.gpuRenderer = Optional.ofNullable(gpu);
            // Unlimited frame rate, VSync off: headroom is visible.
            options.set(VanillaOption.FRAMERATE_LIMIT, 260);
            options.set(VanillaOption.VSYNC, false);
            options.setOrder.clear();
            options.saveCount = 0;
        }

        /** Simulates {@code seconds} of play: one tick per 50 ms and the frames rendered in between. */
        void play(double seconds) {
            long ticks = Math.round(seconds * 20);
            for (long i = 0; i < ticks; i++) {
                if (looking) {
                    game.look();
                }
                center.tick();
                frameDebt += 50.0;
                while (frameDebt > 0) {
                    double frame = machine.applyAsDouble(options);
                    center.onFrame(frame);
                    frameDebt -= frame;
                }
                clock.advance(50);
            }
        }

        /** Plays until the run finished (or the time limit). */
        void playUntilDone(double maxSeconds) {
            for (int i = 0; i < maxSeconds && (tuner.isRunning() || tuner.isPending()
                    || !tuner.state().lastRunClientVersion().equals(Optional.of(game.clientVersion))); i++) {
                play(1);
            }
            assertFalse(tuner.isRunning() || tuner.isPending(), "the run did not finish in " + maxSeconds + " s");
        }

        long writes(VanillaOption option) {
            return options.setOrder.stream().filter(option::equals).count();
        }

        int distance() {
            return options.getInt(VanillaOption.RENDER_DISTANCE, -1);
        }

        Optional<Notification> toast(String title) {
            return notifications.history().stream().filter(n -> n.title().equals(title)).findFirst();
        }

        PerformancePreset result() {
            return tuner.state().result().orElseThrow().preset();
        }
    }

    private void assertNeverWroteTheChoicesOfThePlayer(Rig rig) {
        for (VanillaOption option : SmartBoostTuner.NEVER_WRITTEN) {
            assertEquals(0, rig.writes(option), option + " must never be written by Smart Boost");
        }
    }

    // ---- when it runs ------------------------------------------------------------------------------------------

    @Test
    void neverWritesOnTheTitleScreenInMenusOrWhileIdle() {
        Rig rig = new Rig(MID, IRIS, true);
        rig.game.onTitleScreen();
        rig.play(60);
        rig.game.inWorld = true;
        rig.looking = false; // standing still: vanilla's AFK frame limiter would kick in after a minute
        rig.play(90);
        rig.looking = true;
        rig.game.gameplayActive = false; // pause menu / screen open / window unfocused
        rig.play(60);
        rig.game.gameplayActive = true;
        assertTrue(rig.options.setOrder.isEmpty(), () -> "wrote " + rig.options.setOrder);
        assertEquals(0, rig.options.saveCount);
        assertFalse(Files.exists(rig.file), "nothing to remember yet");
        assertFalse(rig.tuner.isRunning());

        rig.looking = true;
        rig.play(19);
        assertTrue(rig.options.setOrder.isEmpty(), "20 s of gameplay first");
        rig.play(2);
        assertTrue(rig.tuner.isRunning(), "the automatic run starts after 20 s of gameplay");
        assertTrue(rig.toast("Smart Boost is testing settings").isPresent());
    }

    @Test
    void loadingTheServicesWritesNoOption() throws Exception {
        FakeOptionsBridge options = new FakeOptionsBridge();
        FakeGameBridge game = new FakeGameBridge();
        VantaPaths paths = VantaPaths.inGameDirectory(dir);
        Files.createDirectories(paths.root());
        Files.writeString(paths.smartBoostFile(), "{\"schemaVersion\":1,\"lastRunClientVersion\":\"0.9.0\","
                + "\"owned\":{\"render_distance\":6,\"particles\":\"MINIMAL\"},\"undo\":{\"render_distance\":12}}");
        VantaServices services = VantaServices.create(paths, game, options, new FakeKeybindBridge(),
                new FakeResourcePackBridge(), MutableClock.standard());
        services.load();
        assertTrue(options.setOrder.isEmpty(), () -> "start-up wrote " + options.setOrder);
        assertEquals(0, options.saveCount);
        assertEquals(6, services.smartBoost().state().owned().get(VanillaOption.RENDER_DISTANCE));
        services.saveAll();
        assertTrue(options.setOrder.isEmpty());
    }

    @Test
    void hostCanForbidTheAutomaticRunButReTuneStillWorks() {
        Rig rig = new Rig(MID, IRIS, false);
        rig.play(120);
        assertTrue(rig.options.setOrder.isEmpty(), "no automatic run");
        rig.tuner.retune();
        rig.playUntilDone(200);
        assertEquals(PerformancePreset.BALANCED, rig.result());
    }

    // ---- choosing ----------------------------------------------------------------------------------------------

    @Test
    void fastMachineStepsUpToUltra() {
        Rig rig = new Rig(STRONG, RTX, true);
        rig.options.graphicsPresetBundle = true;
        rig.machine = fpsOverDistance(2400); // HIGH 150 FPS, ULTRA 100 FPS
        rig.playUntilDone(300);
        assertEquals(PerformancePreset.ULTRA, rig.result());
        assertEquals(24, rig.distance());
        assertEquals(16, rig.options.getInt(VanillaOption.SIMULATION_DISTANCE, -1));
        assertEquals(Optional.of(rig.game.clientVersion), rig.tuner.state().lastRunClientVersion());
        Notification applied = rig.toast("Smart Boost applied").orElseThrow();
        assertTrue(applied.body().contains("Ultra") && applied.body().contains("100"), applied.body());
        assertNeverWroteTheChoicesOfThePlayer(rig);
        assertEquals(260, rig.options.getInt(VanillaOption.FRAMERATE_LIMIT, -1));
        assertEquals(false, rig.options.get(VanillaOption.VSYNC).orElseThrow());
        assertTrue(Files.exists(rig.file));
        assertFalse(rig.tuner.isRunning());
    }

    @Test
    void mediumMachineKeepsBalanced() {
        Rig rig = new Rig(MID, IRIS, true);
        rig.machine = fpsOverDistance(900); // BALANCED 90 FPS: passes, no headroom
        rig.playUntilDone(300);
        assertEquals(PerformancePreset.BALANCED, rig.result());
        assertEquals(10, rig.distance());
        assertEquals("DECREASED", rig.options.getEnum(VanillaOption.PARTICLES, ""));
        assertEquals("FANCY", rig.options.getEnum(VanillaOption.GRAPHICS_MODE, ""), "graphics preset untouched");
        assertNeverWroteTheChoicesOfThePlayer(rig);
    }

    @Test
    void slowMachineEndsOnMaxFps() {
        Rig rig = new Rig(WEAK, UHD, true);
        rig.machine = fpsOverDistance(240); // LOW 40 FPS, BOOST 48 FPS: nothing passes
        rig.playUntilDone(300);
        assertEquals(PerformancePreset.BOOST, rig.result());
        assertEquals(5, rig.distance());
        assertEquals(0, rig.options.getInt(VanillaOption.MENU_BLUR, -1));
        assertNeverWroteTheChoicesOfThePlayer(rig);
    }

    @Test
    void failingGuessStepsDownUntilAPresetPasses() {
        Rig rig = new Rig(MID, null, true); // unknown GPU: BALANCED
        rig.machine = fpsOverDistance(600); // BALANCED 60 FPS (fails 66), LOW 100 FPS
        rig.playUntilDone(300);
        assertEquals(PerformancePreset.LOW, rig.result());
        assertEquals(6, rig.distance());
    }

    @Test
    void failedStepUpIsReverted() {
        Rig rig = new Rig(STRONG, RTX, true);
        rig.machine = fpsOverDistance(1550); // HIGH 97 FPS (headroom), ULTRA 65 FPS (fails)
        rig.playUntilDone(300);
        assertEquals(PerformancePreset.HIGH, rig.result());
        assertEquals(16, rig.distance());
        assertEquals(12, rig.options.getInt(VanillaOption.SIMULATION_DISTANCE, -1));
    }

    @Test
    void hitchesFailAWindowEvenWithAGoodAverage() {
        Rig rig = new Rig(STRONG, RTX, true);
        int[] frame = {0};
        rig.machine = o -> {
            frame[0]++;
            int distance = o.getInt(VanillaOption.RENDER_DISTANCE, 12);
            // Far distances stall on chunk builds: one 90 ms frame per 200 frames.
            return distance >= 16 && frame[0] % 200 == 0 ? 90.0 : 5.0;
        };
        rig.playUntilDone(300);
        assertEquals(PerformancePreset.BALANCED, rig.result(), "HIGH stutters, BALANCED does not");
    }

    @Test
    void aFrameRateCapHidesHeadroomSoItNeverStepsUp() {
        Rig rig = new Rig(STRONG, RTX, true);
        rig.options.set(VanillaOption.FRAMERATE_LIMIT, 120);
        rig.options.setOrder.clear();
        rig.machine = o -> 1000.0 / 120.0; // pinned at the cap
        rig.playUntilDone(300);
        assertEquals(PerformancePreset.HIGH, rig.result(), "stays at the guess");
        assertEquals(120, rig.tuner.state().result().orElseThrow().targetFps());
        assertEquals(120, rig.options.getInt(VanillaOption.FRAMERATE_LIMIT, -1), "the cap is the player's");
        assertNeverWroteTheChoicesOfThePlayer(rig);

        Rig vsync = new Rig(MID, IRIS, true);
        vsync.options.set(VanillaOption.VSYNC, true);
        vsync.options.setOrder.clear();
        vsync.machine = o -> 1000.0 / 60.0; // a 60 Hz monitor
        vsync.playUntilDone(300);
        assertEquals(PerformancePreset.BALANCED, vsync.result(), "60 FPS under VSync is a pass, not a slow PC");
        assertEquals(true, vsync.options.get(VanillaOption.VSYNC).orElseThrow());
        assertNeverWroteTheChoicesOfThePlayer(vsync);
    }

    @Test
    void targetFollowsTheLimitAndVsync() {
        assertEquals(60, SmartBoostTuner.targetFps(260, false));
        assertEquals(60, SmartBoostTuner.targetFps(144, true));
        assertEquals(120, SmartBoostTuner.targetFps(120, false));
        assertEquals(144, SmartBoostTuner.targetFps(240, false));
        assertEquals(60, SmartBoostTuner.targetFps(60, false));
        assertEquals(30, SmartBoostTuner.targetFps(30, false));
    }

    // ---- gating during a run -----------------------------------------------------------------------------------

    @Test
    void pausedAndAfkFramesAreIgnored() {
        Rig rig = new Rig(STRONG, RTX, true);
        rig.machine = fpsOverDistance(2400);
        rig.play(21 + 10); // run started, warm-up over, a few seconds measured
        assertTrue(rig.tuner.isRunning());
        long measured = rig.tuner.measuredMs();
        assertTrue(measured > 0);

        // Pause menu: the frames are slow (and do not count).
        rig.game.gameplayActive = false;
        ToDoubleFunction<FakeOptionsBridge> fast = rig.machine;
        rig.machine = o -> 200.0;
        rig.play(60);
        assertEquals(measured, rig.tuner.measuredMs(), "paused frames never count");
        assertTrue(rig.tuner.isGated());

        // Idle: normal frames for a minute, then vanilla's AFK limiter (30 FPS) - all after the 30 s activity window.
        rig.game.gameplayActive = true;
        rig.looking = false;
        long idleStart = rig.clock.millis();
        rig.machine = o -> rig.clock.millis() - idleStart < 60_000 ? fast.applyAsDouble(o) : 1000.0 / 30.0;
        rig.play(120);
        rig.looking = true;
        rig.machine = fast;
        rig.playUntilDone(300);
        assertEquals(PerformancePreset.ULTRA, rig.result(), "neither the pause menu nor AFK frames lowered it");
    }

    @Test
    void leavingTheWorldAbortsAndTheRunStartsOverNextSession() {
        Rig rig = new Rig(MID, IRIS, true);
        rig.machine = fpsOverDistance(900);
        rig.play(25);
        assertTrue(rig.tuner.isRunning());
        rig.game.onTitleScreen();
        rig.play(5);
        assertFalse(rig.tuner.isRunning());
        assertTrue(rig.tuner.state().lastRunClientVersion().isEmpty(), "not finished");
        assertTrue(rig.tuner.state().isOwned(VanillaOption.RENDER_DISTANCE), "written values stay recorded");
        rig.game.inWorld = true;
        rig.playUntilDone(300);
        assertEquals(PerformancePreset.BALANCED, rig.result());
    }

    // ---- ownership ---------------------------------------------------------------------------------------------

    @Test
    void handChangedOptionsAreReleasedAndNeverRewrittenUntilReTune() {
        Rig rig = new Rig(MID, IRIS, true);
        rig.machine = fpsOverDistance(900);
        rig.playUntilDone(300);
        assertEquals(10, rig.distance());

        rig.options.set(VanillaOption.RENDER_DISTANCE, 20); // the player, in Video Settings or Sodium's screen
        rig.play(1);
        assertTrue(rig.tuner.state().isReleased(VanillaOption.RENDER_DISTANCE));
        assertEquals(List.of(VanillaOption.RENDER_DISTANCE), rig.tuner.releasedOptions());

        rig.game.clientVersion = "1.0.1"; // an update: the automatic run happens again
        rig.options.setOrder.clear();
        rig.playUntilDone(400);
        assertEquals(Optional.of("1.0.1"), rig.tuner.state().lastRunClientVersion());
        assertEquals(0, rig.writes(VanillaOption.RENDER_DISTANCE), "never rewritten");
        assertEquals(20, rig.distance());
        assertTrue(rig.writes(VanillaOption.PARTICLES) > 0, "the options still owned were tuned");

        rig.tuner.retune();
        assertTrue(rig.tuner.releasedOptions().isEmpty(), "Re-tune takes every option back");
        rig.playUntilDone(400);
        assertFalse(rig.tuner.isRunning());
        assertTrue(rig.writes(VanillaOption.RENDER_DISTANCE) > 0);
        assertEquals(rig.result().renderDistance(), rig.distance());
    }

    @Test
    void changingAnOptionDuringARunStopsIt() {
        Rig rig = new Rig(MID, IRIS, true);
        rig.machine = fpsOverDistance(900);
        rig.play(25);
        assertTrue(rig.tuner.isRunning());
        rig.options.set(VanillaOption.PARTICLES, "ALL");
        rig.play(1);
        assertFalse(rig.tuner.isRunning());
        assertTrue(rig.toast("Smart Boost stopped").isPresent());
        assertTrue(rig.tuner.state().isReleased(VanillaOption.PARTICLES));
        assertEquals(Optional.of(rig.game.clientVersion), rig.tuner.state().lastRunClientVersion(),
                "the player took over; no automatic retry for this version");
        rig.options.setOrder.clear();
        rig.play(120);
        assertTrue(rig.options.setOrder.isEmpty());
    }

    @Test
    void explicitPresetsAndGraphicsChoicesBelongToThePlayer() {
        Rig rig = new Rig(MID, IRIS, true);
        rig.machine = fpsOverDistance(900);
        rig.playUntilDone(300);
        rig.center.applyPreset(PerformancePreset.LOW);
        EnumSet<VanillaOption> expected = EnumSet.copyOf(SmartBoostTuner.TUNED_OPTIONS);
        expected.remove(VanillaOption.MENU_BLUR); // LOW does not set it
        assertTrue(rig.tuner.state().released().containsAll(expected), () -> "" + rig.tuner.state().released());

        rig.game.clientVersion = "1.0.1";
        rig.options.setOrder.clear();
        rig.play(200);
        assertTrue(rig.options.setOrder.isEmpty(), () -> "overrode the preset: " + rig.options.setOrder);
        assertEquals(6, rig.distance());
    }

    @Test
    void pickingAGraphicsPresetReleasesEverything() {
        Rig rig = new Rig(MID, IRIS, true);
        rig.options.graphicsPresetBundle = true;
        rig.machine = fpsOverDistance(900);
        rig.playUntilDone(300);
        assertEquals(Optional.of("CUSTOM"), rig.tuner.state().graphicsAtWrite(), "the game reports custom now");

        rig.options.set(VanillaOption.GRAPHICS_MODE, "FAST"); // the player picks Fast in Video Settings
        rig.play(1);
        assertTrue(rig.tuner.state().graphicsPresetChosen());
        assertTrue(rig.tuner.state().owned().isEmpty());

        rig.game.clientVersion = "1.0.1";
        rig.options.setOrder.clear();
        rig.play(200);
        assertTrue(rig.options.setOrder.isEmpty(), () -> "wrote " + rig.options.setOrder);
        assertEquals("FAST", rig.options.getEnum(VanillaOption.GRAPHICS_MODE, ""), "Fast stays Fast");
    }

    @Test
    void aGraphicsPresetPickedBeforeSmartBoostEverRanIsNeverOverridden() {
        // The reported bug: the player picks Fast in Video Settings, presses Esc, plays, and later finds the game no
        // longer on Fast. The automatic run after an install or update must not rewrite Fast's bundled options.
        Rig rig = new Rig(STRONG, RTX, true);
        rig.options.graphicsPresetBundle = true;
        rig.options.set(VanillaOption.GRAPHICS_MODE, "FAST"); // before Smart Boost ever wrote anything
        rig.options.setOrder.clear();
        rig.play(200);
        assertTrue(rig.options.setOrder.isEmpty(), () -> "overrode the player's Fast: " + rig.options.setOrder);
        assertEquals("FAST", rig.options.getEnum(VanillaOption.GRAPHICS_MODE, ""), "Fast stays Fast");
        assertTrue(rig.tuner.state().graphicsPresetChosen(), "the card says why nothing was tuned");
        assertEquals(Optional.of(rig.game.clientVersion), rig.tuner.state().lastRunClientVersion());

        rig.game.clientVersion = "1.0.1"; // an update: still the player's Fast
        rig.play(200);
        assertTrue(rig.options.setOrder.isEmpty(), () -> "overrode the player's Fast: " + rig.options.setOrder);

        rig.tuner.retune(); // only the explicit Re-tune takes over
        rig.playUntilDone(400);
        assertTrue(rig.writes(VanillaOption.RENDER_DISTANCE) > 0);
        assertNeverWroteTheChoicesOfThePlayer(rig);
    }

    @Test
    void customVideoSettingsFromBeforeSmartBoostAreNeverOverridden() {
        // A player who applied a VANTA preset or changed options in VANTA 1.2 (before Smart Boost existed): the game
        // reports the graphics preset as custom and smart-boost.json does not exist yet.
        Rig rig = new Rig(STRONG, RTX, true);
        rig.options.graphicsPresetBundle = true;
        rig.options.set(VanillaOption.RENDER_DISTANCE, 5);
        rig.options.set(VanillaOption.PARTICLES, "MINIMAL");
        rig.options.setOrder.clear();
        rig.play(200);
        assertTrue(rig.options.setOrder.isEmpty(), () -> "overrode the player's options: " + rig.options.setOrder);
        assertEquals(5, rig.distance());
    }

    @Test
    void theCustomStateSmartBoostLeftItselfDoesNotBlockTheNextUpdate() {
        Rig rig = new Rig(MID, IRIS, true);
        rig.options.graphicsPresetBundle = true; // untouched default Fancy: the automatic run may tune
        rig.machine = fpsOverDistance(900);
        rig.playUntilDone(300);
        assertTrue(rig.writes(VanillaOption.RENDER_DISTANCE) > 0, "the default Fancy is tuned");
        assertEquals(Optional.of("CUSTOM"), rig.tuner.state().graphicsAtWrite(), "the game reports custom now");

        rig.options.set(VanillaOption.PARTICLES, "ALL"); // the player takes one option over
        rig.play(1);
        rig.game.clientVersion = "1.0.1";
        rig.options.setOrder.clear();
        rig.playUntilDone(400);
        assertEquals(Optional.of("1.0.1"), rig.tuner.state().lastRunClientVersion());
        assertTrue(rig.writes(VanillaOption.SIMULATION_DISTANCE) > 0, "Smart Boost's own custom state is tuned again");
        assertEquals(0, rig.writes(VanillaOption.PARTICLES), "the player's particles stay");
        assertFalse(rig.tuner.state().graphicsPresetChosen());
    }

    @Test
    void undoRestoresTheEarlierValuesAndTurnsAutomaticTuningOff() {
        Rig rig = new Rig(WEAK, UHD, true);
        rig.machine = fpsOverDistance(240);
        rig.playUntilDone(300);
        assertEquals(PerformancePreset.BOOST, rig.result());
        assertTrue(rig.tuner.canUndo());

        assertTrue(rig.tuner.undo());
        assertEquals(12, rig.distance());
        assertEquals(12, rig.options.getInt(VanillaOption.SIMULATION_DISTANCE, -1));
        assertEquals("ALL", rig.options.getEnum(VanillaOption.PARTICLES, ""));
        assertEquals("FANCY", rig.options.getEnum(VanillaOption.CLOUDS, ""));
        assertEquals(5, rig.options.getInt(VanillaOption.MENU_BLUR, -1));
        assertEquals(4, rig.options.getInt(VanillaOption.MIPMAP_LEVELS, -1));
        assertEquals(true, rig.options.get(VanillaOption.SMOOTH_LIGHTING).orElseThrow());
        assertFalse(rig.settings.get(VantaSettings.PERFORMANCE_SMART_BOOST));
        assertFalse(rig.tuner.canUndo());
        assertTrue(rig.toast("Smart Boost undone").isPresent());

        rig.game.clientVersion = "1.0.1";
        rig.options.setOrder.clear();
        rig.play(200);
        assertTrue(rig.options.setOrder.isEmpty(), "automatic tuning is off after Undo");
    }

    @Test
    void searchActionsReachTheTuner() {
        VantaServices services = VantaServices.create(VantaPaths.inGameDirectory(dir), new FakeGameBridge(),
                new FakeOptionsBridge(), new FakeKeybindBridge(), new FakeResourcePackBridge(),
                MutableClock.standard());
        services.load();
        assertTrue(ActionEntry.builtIns().stream().anyMatch(a -> a.id().equals(ActionEntry.SMART_BOOST_RETUNE)));
        assertTrue(services.runAction(ActionEntry.SMART_BOOST_RETUNE));
        assertTrue(services.smartBoost().isPending());
        assertTrue(services.runAction(ActionEntry.SMART_BOOST_UNDO));
        assertFalse(services.smartBoost().isPending());
    }

    // ---- continuous mode ---------------------------------------------------------------------------------------

    @Test
    void continuousModeRespectsFloorCeilingAndHysteresis() {
        Rig rig = new Rig(MID, IRIS, true);
        rig.machine = fpsOverDistance(900);
        rig.playUntilDone(300);
        assertEquals(10, rig.distance());
        assertEquals(10, rig.tuner.state().adaptiveCeiling());
        rig.settings.set(VantaSettings.PERFORMANCE_SMART_BOOST_ADAPTIVE, true);
        assertTrue(rig.tuner.controlsRenderDistance());
        rig.center.dismissSuggestion();
        rig.game.fps = 30; // what the advisor reads: far below target, it would suggest a lower distance

        List<long[]> changes = new ArrayList<>(); // {millis, distance}
        int last = rig.distance();
        rig.machine = fpsOverDistance(300); // a heavy area: 30 FPS at 10 chunks
        for (int s = 0; s < 15 * 60; s++) {
            rig.play(1);
            if (rig.distance() != last) {
                last = rig.distance();
                changes.add(new long[] {rig.clock.millis(), last});
            }
        }
        assertEquals(List.of(8L, 6L), changes.stream().map(c -> c[1]).toList(), "down in 2-chunk steps to 6");
        assertTrue(changes.get(1)[0] - changes.get(0)[0] >= SmartBoostTuner.ADAPTIVE_MIN_INTERVAL_MS);

        long lastDown = changes.get(1)[0];
        rig.machine = fpsOverDistance(6000); // back in a quiet area: lots of headroom
        changes.clear();
        for (int s = 0; s < 20 * 60; s++) {
            rig.play(1);
            if (rig.distance() != last) {
                last = rig.distance();
                changes.add(new long[] {rig.clock.millis(), last});
            }
        }
        assertEquals(List.of(8L, 10L), changes.stream().map(c -> c[1]).toList(), "up again, never above 10");
        assertTrue(changes.get(0)[0] - lastDown >= SmartBoostTuner.ADAPTIVE_UP_AFTER_DOWN_MS,
                "no step up within 5 minutes of a step down");
        assertTrue(changes.get(1)[0] - changes.get(0)[0] >= SmartBoostTuner.ADAPTIVE_MIN_INTERVAL_MS);
        assertTrue(rig.center.pendingSuggestion().isEmpty(), "the advisor stays quiet while Smart Boost drives it");
        assertTrue(rig.toast("Render distance adjusted").isPresent());
        assertNeverWroteTheChoicesOfThePlayer(rig);

        rig.options.set(VanillaOption.RENDER_DISTANCE, 14); // the player takes over
        rig.play(1);
        assertFalse(rig.tuner.controlsRenderDistance());
        rig.machine = fpsOverDistance(300);
        rig.play(10 * 60);
        assertEquals(14, rig.distance(), "a released distance is never adjusted");
    }
}
