package dev.vanta.core.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** The countdown text, the 12-hour time the alarm rang and the custom minutes and seconds input. */
class TimerFormatsTest {

    @Test
    void remainingUsesMinutesAndSecondsBelowAnHourAndHoursAbove() {
        assertEquals("0:00", TimerFormats.remaining(0L));
        assertEquals("0:00", TimerFormats.remaining(-5_000L));
        assertEquals("0:01", TimerFormats.remaining(1L), "partial seconds round up");
        assertEquals("0:01", TimerFormats.remaining(1_000L));
        assertEquals("0:02", TimerFormats.remaining(1_001L));
        assertEquals("9:59", TimerFormats.remaining(599_000L));
        assertEquals("10:00", TimerFormats.remaining(600_000L));
        assertEquals("59:59", TimerFormats.remaining(3_599_000L));
        assertEquals("1:00:00", TimerFormats.remaining(3_600_000L));
        assertEquals("1:05:09", TimerFormats.remaining(3_909_000L));
        assertEquals("24:00:00", TimerFormats.remaining(TimerService.MAX_DURATION_MS));
    }

    @Test
    void timeOfDayIsTwelveHourWithAmPm() {
        ZoneId utc = ZoneOffset.UTC;
        long afternoon = ZonedDateTime.of(2026, 10, 4, 15, 42, 30, 0, utc).toInstant().toEpochMilli();
        assertEquals("3:42 PM", TimerFormats.timeOfDay(afternoon, utc));
        long midnight = ZonedDateTime.of(2026, 10, 4, 0, 5, 0, 0, utc).toInstant().toEpochMilli();
        assertEquals("12:05 AM", TimerFormats.timeOfDay(midnight, utc));
        long noon = ZonedDateTime.of(2026, 10, 4, 12, 0, 0, 0, utc).toInstant().toEpochMilli();
        assertEquals("12:00 PM", TimerFormats.timeOfDay(noon, utc));
        assertEquals("5:42 PM", TimerFormats.timeOfDay(afternoon, ZoneOffset.ofHours(2)), "the zone is applied");
    }

    @Test
    void minutesAndSecondsPartsSplitADuration() {
        assertEquals(2L, TimerFormats.minutesPart(150_000L));
        assertEquals(30L, TimerFormats.secondsPart(150_000L));
        assertEquals(90L, TimerFormats.minutesPart(5_400_000L));
        assertEquals(0L, TimerFormats.secondsPart(5_400_000L));
        assertEquals(0L, TimerFormats.minutesPart(-1L));
    }

    @Test
    void customDurationParsesMinutesAndSeconds() {
        assertDuration(150_000L, TimerFormats.parseDuration("2", "30"));
        assertDuration(600_000L, TimerFormats.parseDuration(" 10 ", ""));
        assertDuration(45_000L, TimerFormats.parseDuration("", "45"));
        assertDuration(1_000L, TimerFormats.parseDuration("0", "1"));
        assertDuration(TimerService.MAX_DURATION_MS, TimerFormats.parseDuration("1440", "0"));
        assertDuration(TimerService.MAX_DURATION_MS, TimerFormats.parseDuration("001440", "00"));
    }

    @Test
    void customDurationExplainsWhatIsWrong() {
        assertError(TimerFormats.Duration.NOT_A_NUMBER, TimerFormats.parseDuration("abc", ""));
        assertError(TimerFormats.Duration.NOT_A_NUMBER, TimerFormats.parseDuration("-1", ""));
        assertError(TimerFormats.Duration.NOT_A_NUMBER, TimerFormats.parseDuration("1.5", ""));
        assertError(TimerFormats.Duration.NOT_A_NUMBER, TimerFormats.parseDuration("1", "x"));
        assertError(TimerFormats.Duration.SECONDS_RANGE, TimerFormats.parseDuration("1", "60"));
        assertError(TimerFormats.Duration.SECONDS_RANGE, TimerFormats.parseDuration("1", "100"));
        assertError(TimerFormats.Duration.TOO_SHORT, TimerFormats.parseDuration("", ""));
        assertError(TimerFormats.Duration.TOO_SHORT, TimerFormats.parseDuration("0", "0"));
        assertError(TimerFormats.Duration.TOO_SHORT, TimerFormats.parseDuration(null, null));
        assertError(TimerFormats.Duration.TOO_LONG, TimerFormats.parseDuration("1440", "1"));
        assertError(TimerFormats.Duration.TOO_LONG, TimerFormats.parseDuration("1441", ""));
        assertError(TimerFormats.Duration.TOO_LONG, TimerFormats.parseDuration("99999999999999999999", ""),
                "huge input never overflows");
    }

    private static void assertDuration(long millis, TimerFormats.Duration duration) {
        assertTrue(duration.ok(), () -> "valid: " + duration);
        assertEquals(millis, duration.millis());
        assertEquals("", duration.errorKey());
    }

    private static void assertError(String key, TimerFormats.Duration duration) {
        assertError(key, duration, key);
    }

    private static void assertError(String key, TimerFormats.Duration duration, String message) {
        assertFalse(duration.ok(), message);
        assertEquals(key, duration.errorKey(), message);
        assertEquals(0L, duration.millis());
    }
}
