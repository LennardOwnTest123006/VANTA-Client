package dev.vanta.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimeOfDayTest {
    /** 2026-07-15T06:00:00Z: Berlin is on summer time (UTC+2), so 8:00 AM there. */
    private static final long SUMMER_6_UTC = Instant.parse("2026-07-15T06:00:00Z").toEpochMilli();
    /** 2026-01-15T06:00:00Z: Berlin is on winter time (UTC+1), so 7:00 AM there. */
    private static final long WINTER_6_UTC = Instant.parse("2026-01-15T06:00:00Z").toEpochMilli();
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void theReportedBugSixUtcIsEightInBerlinInSummerAndSevenInWinter() {
        assertEquals("6:00 AM", TimeOfDay.format(SUMMER_6_UTC, ZoneOffset.UTC), "what 1.5.0 showed in Germany");
        assertEquals("8:00 AM", TimeOfDay.format(SUMMER_6_UTC, BERLIN));
        assertEquals("7:00 AM", TimeOfDay.format(WINTER_6_UTC, BERLIN));
        assertEquals("8:00:00 AM", TimeOfDay.format(SUMMER_6_UTC, BERLIN, true));
    }

    @Test
    void daylightSavingSwitchesAtTheRightInstant() {
        // Europe moves the clocks forward on 29 March 2026 at 01:00 UTC (2:00 AM becomes 3:00 AM in Berlin).
        long before = Instant.parse("2026-03-29T00:59:00Z").toEpochMilli();
        long after = Instant.parse("2026-03-29T01:00:00Z").toEpochMilli();
        assertEquals("1:59 AM", TimeOfDay.format(before, BERLIN));
        assertEquals("3:00 AM", TimeOfDay.format(after, BERLIN));
        // And back on 25 October 2026 at 01:00 UTC (3:00 AM becomes 2:00 AM).
        assertEquals("2:59 AM", TimeOfDay.format(Instant.parse("2026-10-25T00:59:00Z").toEpochMilli(), BERLIN));
        assertEquals("2:00 AM", TimeOfDay.format(Instant.parse("2026-10-25T01:00:00Z").toEpochMilli(), BERLIN));
    }

    @Test
    void twelveHourEdgeCases() {
        assertEquals("12:00 AM", TimeOfDay.twelveHour(0, 0, -1), "midnight");
        assertEquals("12:00 PM", TimeOfDay.twelveHour(12, 0, -1), "noon");
        assertEquals("1:05 AM", TimeOfDay.twelveHour(1, 5, -1));
        assertEquals("11:59 AM", TimeOfDay.twelveHour(11, 59, -1));
        assertEquals("1:00 PM", TimeOfDay.twelveHour(13, 0, -1));
        assertEquals("11:59:59 PM", TimeOfDay.twelveHour(23, 59, 59));
        assertEquals("12:00:07 AM", TimeOfDay.twelveHour(0, 0, 7));
    }

    @Test
    void offsetsAreWrittenTheWayPeopleWriteThem() {
        Instant summer = Instant.ofEpochMilli(SUMMER_6_UTC);
        Instant winter = Instant.ofEpochMilli(WINTER_6_UTC);
        assertEquals("UTC+2", TimeOfDay.offset(BERLIN, summer));
        assertEquals("UTC+1", TimeOfDay.offset(BERLIN, winter));
        assertEquals("UTC+0", TimeOfDay.offset(ZoneId.of("Europe/London"), winter));
        assertEquals("UTC+1", TimeOfDay.offset(ZoneId.of("Europe/London"), summer));
        assertEquals("UTC-4", TimeOfDay.offset(ZoneId.of("America/New_York"), summer));
        assertEquals("UTC-5", TimeOfDay.offset(ZoneId.of("America/New_York"), winter));
        assertEquals("UTC+5:30", TimeOfDay.offset(ZoneId.of("Asia/Kolkata"), summer));
        assertEquals("UTC+5:45", TimeOfDay.offset(ZoneId.of("Asia/Kathmandu"), summer));
        assertEquals("UTC-2:30", TimeOfDay.offset(ZoneId.of("America/St_Johns"), summer));
        assertEquals("UTC-3:30", TimeOfDay.offset(ZoneId.of("America/St_Johns"), winter));
        assertEquals("UTC+11", TimeOfDay.offset(ZoneId.of("Australia/Sydney"), winter), "southern summer");
        assertEquals("UTC+0", TimeOfDay.offset(ZoneOffset.UTC, summer));
    }
}
