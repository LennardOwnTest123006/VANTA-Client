package dev.vanta.core.screen.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vanta.core.time.DefaultZone;
import dev.vanta.core.time.TimeZoneChoice;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StatFormatsTest {
    private static final long T0 = 1_791_115_200_000L; // 2026-10-04T12:00:00Z

    @Test
    void durations() {
        assertEquals("45 s", StatFormats.duration(45_000));
        assertEquals("0 s", StatFormats.duration(-5));
        assertEquals("14 min", StatFormats.duration(14 * 60_000L + 59_000));
        assertEquals("2h 14m", StatFormats.duration(2 * 3_600_000L + 14 * 60_000L));
        assertEquals("3d 4h", StatFormats.duration(3 * 86_400_000L + 4 * 3_600_000L));
        assertEquals("0:45", StatFormats.clock(45_000));
        assertEquals("14:03", StatFormats.clock(14 * 60_000L + 3_000));
        assertEquals("2:14:03", StatFormats.clock(2 * 3_600_000L + 14 * 60_000L + 3_000));
    }

    @Test
    void distancesAndCounts() {
        assertEquals("842 blocks", StatFormats.distance(841.7));
        assertEquals("12.4 km", StatFormats.distance(12_400));
        assertEquals("1 km", StatFormats.distance(1000));
        assertEquals("0 blocks", StatFormats.distance(Double.NaN));
        assertEquals("9,999", StatFormats.count(9_999));
        assertEquals("12.3k", StatFormats.count(12_345));
        assertEquals("100k", StatFormats.count(100_000));
        assertEquals("1.2M", StatFormats.count(1_234_567));
        assertEquals("0", StatFormats.count(-3));
        assertEquals("118", StatFormats.fps(117.6));
        assertEquals("—", StatFormats.fps(0));
        assertEquals("3", StatFormats.oneDecimal(3.0));
        assertEquals("3.5", StatFormats.oneDecimal(3.456));
    }

    @Test
    void datesAndRelativeTimes() {
        assertEquals("4 Oct 2026", StatFormats.date(T0, ZoneOffset.UTC));
        assertEquals("4 Oct 2026, 12:00 PM", StatFormats.dateTime(T0, ZoneOffset.UTC));
        assertEquals("12:00 PM", StatFormats.time(T0, ZoneOffset.UTC));
        assertEquals("just now", StatFormats.relative(T0 - 30_000, T0, ZoneOffset.UTC));
        assertEquals("5 min ago", StatFormats.relative(T0 - 5 * 60_000L, T0, ZoneOffset.UTC));
        assertEquals("3 h ago", StatFormats.relative(T0 - 3 * 3_600_000L, T0, ZoneOffset.UTC));
        assertEquals("yesterday", StatFormats.relative(T0 - 86_400_000L, T0, ZoneOffset.UTC));
        assertEquals("2 days ago", StatFormats.relative(T0 - 2 * 86_400_000L, T0, ZoneOffset.UTC));
        assertEquals("5 Aug 2026", StatFormats.relative(T0 - 60L * 86_400_000L, T0, ZoneOffset.UTC));
    }

    @Test
    void timesOfDayUseTheChosenZoneInTwelveHourTime() {
        long summer = Instant.parse("2026-07-15T06:00:00Z").toEpochMilli();
        long winter = Instant.parse("2026-01-15T06:00:00Z").toEpochMilli();
        ZoneId berlin = TimeZoneChoice.EUROPE_BERLIN.zone();
        assertEquals("8:00 AM", StatFormats.time(summer, berlin));
        assertEquals("7:00 AM", StatFormats.time(winter, berlin));
        assertEquals("15 Jul 2026, 8:00 AM", StatFormats.dateTime(summer, berlin));
        assertEquals("15 Jan 2026, 7:00 AM", StatFormats.dateTime(winter, berlin));
        assertEquals("15 Jul 2026, 6:00 AM", StatFormats.dateTime(summer, TimeZoneChoice.UTC.zone()));
        // Late evening UTC is already the next day in Berlin.
        long late = Instant.parse("2026-10-04T23:30:00Z").toEpochMilli();
        assertEquals("4 Oct 2026", StatFormats.date(late, ZoneOffset.UTC));
        assertEquals("5 Oct 2026", StatFormats.date(late, berlin));
        assertEquals("1:30 AM", StatFormats.time(late, berlin));
        DefaultZone.with("Europe/Berlin", () ->
                assertEquals("8:00 AM", StatFormats.time(summer, TimeZoneChoice.AUTO.zone())));
    }
}
