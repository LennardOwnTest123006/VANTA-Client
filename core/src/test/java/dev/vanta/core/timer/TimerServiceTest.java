package dev.vanta.core.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.MutableClock;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The countdown timer on a manual clock: start, count down, pause and resume, cancel, ring, stop and the ranges. */
class TimerServiceTest {
    private MutableClock clock;
    private int sounds;
    private int windows;
    private TimerService timer;

    @BeforeEach
    void setUp() {
        clock = MutableClock.standard();
        sounds = 0;
        windows = 0;
        timer = new TimerService(clock, () -> sounds++);
        timer.setAlarmWindow(() -> windows++);
    }

    /** Advances the clock in client ticks (50 ms) and ticks the timer after each one. */
    private void run(long millis) {
        for (long t = 0; t < millis; t += 50L) {
            clock.advance(50L);
            timer.tick();
        }
    }

    @Test
    void startsIdleWithNothingToShow() {
        assertEquals(TimerState.IDLE, timer.state());
        assertFalse(timer.isActive());
        assertEquals(0L, timer.remainingMillis());
        assertEquals(0L, timer.durationMillis());
        assertEquals("", timer.label());
        assertEquals(TimerReading.IDLE, timer.reading());
        assertTrue(timer.endsAt().isEmpty());
        assertTrue(timer.rangAt().isEmpty());
        assertTrue(timer.lastRangAt().isEmpty());
        timer.tick();
        assertEquals(0, sounds);
        assertEquals(0, windows);
    }

    @Test
    void startCountsDownOnTheServicesClock() {
        long start = clock.millis();
        assertTrue(timer.startMinutes(10, "Tea"));
        assertEquals(TimerState.RUNNING, timer.state());
        assertTrue(timer.isActive());
        assertEquals(600_000L, timer.durationMillis());
        assertEquals(600_000L, timer.remainingMillis());
        assertEquals("Tea", timer.label());
        assertEquals(OptionalLong.of(start + 600_000L), timer.endsAt());
        assertEquals("10:00", timer.reading().remainingText());

        run(1_000L);
        assertEquals(599_000L, timer.remainingMillis());
        assertEquals("9:59", timer.reading().remainingText());
        assertEquals(new TimerReading(TimerState.RUNNING, 599_000L, "Tea"), timer.reading());

        run(298_000L);
        assertEquals(301_000L, timer.remainingMillis());
        assertEquals(TimerState.RUNNING, timer.state());
        assertEquals(0, sounds, "no sound before the time is up");
        assertEquals(0, windows, "no window before the time is up");
    }

    @Test
    void pauseFreezesTheRemainingTimeAndResumeContinues() {
        timer.start(60_000L, "");
        run(10_000L);
        assertTrue(timer.pause());
        assertEquals(TimerState.PAUSED, timer.state());
        assertEquals(50_000L, timer.remainingMillis());
        assertTrue(timer.endsAt().isEmpty());
        assertFalse(timer.pause(), "already paused");

        run(120_000L);
        assertEquals(TimerState.PAUSED, timer.state(), "a paused timer never rings");
        assertEquals(50_000L, timer.remainingMillis());
        assertEquals(0, sounds);

        assertTrue(timer.resume());
        assertEquals(TimerState.RUNNING, timer.state());
        assertFalse(timer.resume(), "already running");
        assertEquals(50_000L, timer.remainingMillis());
        run(20_000L);
        assertEquals(30_000L, timer.remainingMillis());

        assertTrue(timer.togglePause());
        assertEquals(TimerState.PAUSED, timer.state());
        assertTrue(timer.togglePause());
        assertEquals(TimerState.RUNNING, timer.state());
        run(30_000L);
        assertEquals(TimerState.RINGING, timer.state());
        assertFalse(timer.togglePause(), "a ringing timer cannot be paused");
    }

    @Test
    void cancelDropsARunningOrPausedTimer() {
        assertFalse(timer.cancel(), "nothing to cancel");
        timer.startMinutes(5, "Oven");
        run(1_000L);
        assertTrue(timer.cancel());
        assertEquals(TimerState.IDLE, timer.state());
        assertEquals("", timer.label());
        assertEquals(0L, timer.remainingMillis());
        run(400_000L);
        assertEquals(0, sounds, "a cancelled timer never rings");
        assertTrue(timer.lastRangAt().isEmpty(), "a cancelled timer did not ring");

        timer.startMinutes(5, "");
        timer.pause();
        assertTrue(timer.cancel());
        assertEquals(TimerState.IDLE, timer.state());
    }

    @Test
    void ringsWhenTheTimeIsUpAndSoundsOncePerSecond() {
        long start = clock.millis();
        timer.start(2_000L, "Eggs");
        run(1_950L);
        assertEquals(TimerState.RUNNING, timer.state());
        assertEquals(0, sounds);

        run(50L);
        assertEquals(TimerState.RINGING, timer.state());
        assertTrue(timer.isRinging());
        assertEquals(1, sounds, "the alarm sounds at once");
        assertEquals(1, windows, "the alarm window is asked for at once");
        assertEquals(OptionalLong.of(start + 2_000L), timer.rangAt());
        assertEquals(0L, timer.remainingMillis());
        assertEquals("Eggs", timer.label());
        assertEquals(new TimerReading(TimerState.RINGING, 0L, "Eggs"), timer.reading());

        run(950L);
        assertEquals(1, sounds, "not again before a second has passed");
        run(50L);
        assertEquals(2, sounds, "again one second later");
        run(3_000L);
        assertEquals(5, sounds, "one sound per second while ringing");
        assertEquals(5, timer.soundsPlayed());
        assertTrue(windows > 5, "the window is asked for on every tick while ringing, so it reopens when replaced");
    }

    @Test
    void aStalledGameRingsOnceNotOncePerMissedSecond() {
        timer.start(1_000L, "");
        run(1_000L);
        assertEquals(1, sounds);
        clock.advance(10_000L);
        timer.tick();
        assertEquals(2, sounds, "one sound after a 10 s stall");
        run(950L);
        assertEquals(2, sounds);
        run(50L);
        assertEquals(3, sounds);
    }

    @Test
    void stopEndsTheRingingAndRemembersTheAlarm() {
        timer.start(1_000L, "Brew");
        run(1_000L);
        long rang = timer.rangAt().orElseThrow();
        assertTrue(timer.stop());
        assertEquals(TimerState.IDLE, timer.state());
        assertFalse(timer.isRinging());
        assertEquals(OptionalLong.of(rang), timer.lastRangAt());
        assertEquals("Brew", timer.lastLabel());
        assertEquals(0, timer.soundsPlayed());
        int soundsAtStop = sounds;
        int windowsAtStop = windows;
        run(5_000L);
        assertEquals(soundsAtStop, sounds, "no sound after Stop");
        assertEquals(windowsAtStop, windows, "no window after Stop");
        assertFalse(timer.stop(), "nothing rings any more");
    }

    @Test
    void cancelWhileRingingStopsTheAlarm() {
        timer.start(1_000L, "");
        run(1_000L);
        assertTrue(timer.cancel());
        assertEquals(TimerState.IDLE, timer.state());
        assertTrue(timer.lastRangAt().isPresent());
    }

    @Test
    void startingANewTimerWhileRingingStopsTheOldAlarm() {
        timer.start(1_000L, "First");
        run(1_000L);
        assertTrue(timer.isRinging());
        assertTrue(timer.start(5_000L, "Second"));
        assertEquals(TimerState.RUNNING, timer.state());
        assertEquals("Second", timer.label());
        assertEquals("First", timer.lastLabel());
        int before = sounds;
        run(4_950L);
        assertEquals(before, sounds, "the new timer is silent until its own end");
        run(50L);
        assertEquals(before + 1, sounds);
    }

    @Test
    void durationsOutsideOneSecondTo24HoursAreRefused() {
        assertFalse(timer.start(0L, ""));
        assertFalse(timer.start(999L, ""));
        assertFalse(timer.start(TimerService.MAX_DURATION_MS + 1L, ""));
        assertFalse(timer.startMinutes(0, ""));
        assertFalse(timer.startMinutes(TimerService.MAX_MINUTES + 1, ""));
        assertEquals(TimerState.IDLE, timer.state(), "a refused start changes nothing");

        assertTrue(timer.start(TimerService.MIN_DURATION_MS, ""));
        assertTrue(timer.start(TimerService.MAX_DURATION_MS, ""));
        assertEquals("24:00:00", timer.reading().remainingText());
        assertTrue(timer.startMinutes(TimerService.MAX_MINUTES, ""));
        assertEquals(1440, TimerService.MAX_MINUTES);
        assertEquals(java.util.List.of(1, 5, 10, 15, 30, 60), TimerService.PRESET_MINUTES);

        timer.startMinutes(1, "Kept");
        assertFalse(timer.start(0L, "Lost"));
        assertEquals("Kept", timer.label(), "a refused start keeps the running timer");
    }

    @Test
    void labelsAreTrimmedCleanedAndCut() {
        assertEquals("", TimerService.cleanLabel(null));
        assertEquals("", TimerService.cleanLabel("   "));
        assertEquals("Tea", TimerService.cleanLabel("  Tea  "));
        assertEquals("Tea time", TimerService.cleanLabel("Tea\u0007 time\n"));
        String longLabel = "x".repeat(50);
        assertEquals(TimerService.MAX_LABEL_LENGTH, TimerService.cleanLabel(longLabel).length());
        // Code points, not chars: an emoji counts once and is never cut in half.
        String emoji = "🔔".repeat(40);
        String cleaned = TimerService.cleanLabel(emoji);
        assertEquals(TimerService.MAX_LABEL_LENGTH, cleaned.codePointCount(0, cleaned.length()));
        timer.start(1_000L, "  Pie \t ");
        assertEquals("Pie", timer.label());
    }

    @Test
    void aThrowingSoundOrWindowNeverStopsTheTimer() {
        TimerService broken = new TimerService(clock, () -> {
            throw new IllegalStateException("no sound device");
        });
        broken.setAlarmWindow(() -> {
            throw new IllegalStateException("no window");
        });
        broken.start(1_000L, "");
        clock.advance(1_000L);
        broken.tick();
        assertEquals(TimerState.RINGING, broken.state());
        assertEquals(1, broken.soundsPlayed());
        clock.advance(1_000L);
        broken.tick();
        assertEquals(2, broken.soundsPlayed());
        assertTrue(broken.stop());
    }
}
