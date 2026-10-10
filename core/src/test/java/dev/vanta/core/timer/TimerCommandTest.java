package dev.vanta.core.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vanta.core.config.MutableClock;
import org.junit.jupiter.api.Test;

/** The arguments of {@code /vanta timer}: minutes, m:ss, an optional label and stop. */
class TimerCommandTest {

    @Test
    void minutesWithAnOptionalLabel() {
        assertEquals(new TimerCommand.Start(600_000L, ""), TimerCommand.parse("10"));
        assertEquals(new TimerCommand.Start(600_000L, "Tea time"), TimerCommand.parse("  10   Tea time "));
        assertEquals(new TimerCommand.Start(60_000L, ""), TimerCommand.parse("1"));
        assertEquals(new TimerCommand.Start(TimerService.MAX_DURATION_MS, ""), TimerCommand.parse("1440"));
    }

    @Test
    void minutesAndSeconds() {
        assertEquals(new TimerCommand.Start(150_000L, ""), TimerCommand.parse("2:30"));
        assertEquals(new TimerCommand.Start(2_000L, "Quick"), TimerCommand.parse("0:02 Quick"));
        assertEquals(new TimerCommand.Start(5_400_000L, ""), TimerCommand.parse("90:00"));
    }

    @Test
    void stopAndCancel() {
        assertInstanceOf(TimerCommand.Stop.class, TimerCommand.parse("stop"));
        assertInstanceOf(TimerCommand.Stop.class, TimerCommand.parse(" STOP "));
        assertInstanceOf(TimerCommand.Stop.class, TimerCommand.parse("cancel"));
        assertEquals(new TimerCommand.Invalid(TimerCommand.INVALID), TimerCommand.parse("stop now"));
    }

    @Test
    void invalidInputNamesTheReason() {
        for (String bad : new String[] {"", "   ", "abc", "ten", "-5", "1.5", "2:3", "2:300", ":30", "2:", "1:2:3",
                "a:30", null}) {
            assertEquals(new TimerCommand.Invalid(TimerCommand.INVALID), TimerCommand.parse(bad), String.valueOf(bad));
        }
        assertEquals(new TimerCommand.Invalid(TimerFormats.Duration.TOO_SHORT), TimerCommand.parse("0"));
        assertEquals(new TimerCommand.Invalid(TimerFormats.Duration.TOO_SHORT), TimerCommand.parse("0:00"));
        assertEquals(new TimerCommand.Invalid(TimerFormats.Duration.SECONDS_RANGE), TimerCommand.parse("1:75"));
        assertEquals(new TimerCommand.Invalid(TimerFormats.Duration.TOO_LONG), TimerCommand.parse("1441"));
        assertEquals(new TimerCommand.Invalid(TimerFormats.Duration.TOO_LONG), TimerCommand.parse("1440:01"));
    }

    @Test
    void labelsAreCleaned() {
        TimerCommand.Start start = (TimerCommand.Start) TimerCommand.parse("5 " + "y".repeat(60));
        assertEquals(TimerService.MAX_LABEL_LENGTH, start.label().length());
    }

    @Test
    void runStartsStopsAndCancels() {
        MutableClock clock = MutableClock.standard();
        TimerService timer = new TimerService(clock, () -> { });

        assertEquals(TimerCommand.Outcome.NOTHING_TO_STOP, TimerCommand.run(TimerCommand.parse("stop"), timer));

        assertEquals(TimerCommand.Outcome.STARTED, TimerCommand.run(TimerCommand.parse("0:02 Eggs"), timer));
        assertEquals(TimerState.RUNNING, timer.state());
        assertEquals(2_000L, timer.durationMillis());
        assertEquals("Eggs", timer.label());

        assertEquals(TimerCommand.Outcome.CANCELLED, TimerCommand.run(TimerCommand.parse("stop"), timer));
        assertEquals(TimerState.IDLE, timer.state());

        TimerCommand.run(TimerCommand.parse("0:01"), timer);
        clock.advance(1_000L);
        timer.tick();
        assertEquals(TimerState.RINGING, timer.state());
        assertEquals(TimerCommand.Outcome.STOPPED, TimerCommand.run(TimerCommand.parse("stop"), timer));
        assertEquals(TimerState.IDLE, timer.state());

        assertThrows(IllegalArgumentException.class, () -> TimerCommand.run(TimerCommand.parse("nope"), timer));
        assertThrows(IllegalArgumentException.class, () -> TimerCommand.run(new TimerCommand.Start(0L, ""), timer));
    }
}
