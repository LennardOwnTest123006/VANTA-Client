package dev.vanta.core.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.MutableClock;
import dev.vanta.core.i18n.Lang;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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

    @Test
    void executeNamesTheChatLineAndTheTimeOfDayItRingsAt() {
        // 2026-07-01 13:40 UTC = 3:40 PM in Berlin (summer time).
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T13:40:00Z").toEpochMilli());
        TimerService timer = new TimerService(clock, () -> { });
        ZoneId berlin = ZoneId.of("Europe/Berlin");

        TimerCommand.Reply nothing = TimerCommand.execute("stop", timer, berlin);
        assertEquals(new TimerCommand.Reply(TimerCommand.NOTHING, List.of(), false), nothing);

        TimerCommand.Reply started = TimerCommand.execute("2:30", timer, berlin);
        assertEquals(new TimerCommand.Reply(TimerCommand.STARTED, List.of("2:30", "3:42 PM"), false), started);
        assertEquals("Timer started: 2:30, rings at 3:42 PM", Lang.tr(started.key(), started.argsArray()));
        assertEquals(TimerState.RUNNING, timer.state());

        TimerCommand.Reply labelled = TimerCommand.execute("90 Pizza", timer, ZoneOffset.UTC);
        assertEquals(new TimerCommand.Reply(TimerCommand.STARTED_LABELLED, List.of("Pizza", "1:30:00", "3:10 PM"),
                false), labelled);
        assertEquals("Timer \u201cPizza\u201d started: 1:30:00, rings at 3:10 PM",
                Lang.tr(labelled.key(), labelled.argsArray()));

        assertEquals(new TimerCommand.Reply(TimerCommand.CANCELLED, List.of(), false),
                TimerCommand.execute("cancel", timer, berlin));
        assertEquals(TimerState.IDLE, timer.state());

        TimerCommand.execute("0:01", timer, berlin);
        clock.advance(1_000L);
        timer.tick();
        assertTrue(timer.isRinging());
        assertEquals(new TimerCommand.Reply(TimerCommand.STOPPED, List.of(), false),
                TimerCommand.execute("stop", timer, berlin));
        assertFalse(timer.isActive());
    }

    @Test
    void executeReportsInvalidInputAsAnErrorAndChangesNothing() {
        TimerService timer = new TimerService(MutableClock.standard(), () -> { });
        timer.startMinutes(5, "Tea");
        TimerCommand.Reply bad = TimerCommand.execute("soon", timer, ZoneOffset.UTC);
        assertTrue(bad.error());
        assertEquals(TimerCommand.INVALID, bad.key());
        assertTrue(Lang.tr(bad.key()).startsWith("Use /vanta timer"), Lang.tr(bad.key()));
        TimerCommand.Reply tooLong = TimerCommand.execute("2000", timer, ZoneOffset.UTC);
        assertEquals(new TimerCommand.Reply(TimerFormats.Duration.TOO_LONG, List.of(), true), tooLong);
        assertEquals("The longest timer is 24 hours (1440 minutes).", Lang.tr(tooLong.key()));
        assertEquals("Tea", timer.label(), "the running timer is untouched");
        assertEquals(300_000L, timer.durationMillis());
    }
}
