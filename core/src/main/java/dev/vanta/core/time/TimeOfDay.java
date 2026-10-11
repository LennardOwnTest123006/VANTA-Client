package dev.vanta.core.time;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * The one 12-hour time-of-day format VANTA shows everywhere ({@code 8:00 AM}, {@code 12:05:09 PM}): no leading zero
 * on the hour, 12 for noon and midnight, {@code AM}/{@code PM} after a space. The zone is always explicit; callers
 * pass {@code VantaServices.timeZone()}, which is the player's choice or, by default, this PC's time zone.
 */
public final class TimeOfDay {
    private TimeOfDay() {
    }

    /** {@code h:mm AM} of an instant in {@code zone}. */
    public static String format(long epochMillis, ZoneId zone) {
        return format(epochMillis, zone, false);
    }

    /** {@code h:mm AM}, or {@code h:mm:ss AM} with {@code seconds}, of an instant in {@code zone}. */
    public static String format(long epochMillis, ZoneId zone, boolean seconds) {
        LocalTime time = LocalTime.ofInstant(Instant.ofEpochMilli(epochMillis), Objects.requireNonNull(zone, "zone"));
        return twelveHour(time.getHour(), time.getMinute(), seconds ? time.getSecond() : -1);
    }

    /**
     * {@code h:mm AM} (or {@code h:mm:ss PM} when {@code second} is not negative) from a 24-hour {@code hour} (0 to
     * 23): 12-hour time, 12 for noon and midnight.
     */
    public static String twelveHour(int hour, int minute, int second) {
        StringBuilder sb = new StringBuilder(11);
        int h = hour % 12;
        sb.append(h == 0 ? 12 : h);
        sb.append(':');
        pad2(sb, minute);
        if (second >= 0) {
            sb.append(':');
            pad2(sb, second);
        }
        sb.append(hour < 12 ? " AM" : " PM");
        return sb.toString();
    }

    /**
     * The UTC offset of {@code zone} at {@code at} as people write it: {@code UTC+2}, {@code UTC-5}, {@code UTC+0},
     * {@code UTC+5:30}, {@code UTC-2:30}. Daylight saving time is included, so Berlin reads {@code UTC+2} in summer
     * and {@code UTC+1} in winter.
     */
    public static String offset(ZoneId zone, Instant at) {
        ZoneOffset offset = Objects.requireNonNull(zone, "zone").getRules().getOffset(Objects.requireNonNull(at, "at"));
        int total = offset.getTotalSeconds();
        int abs = Math.abs(total);
        int hours = abs / 3600;
        int minutes = (abs % 3600) / 60;
        StringBuilder sb = new StringBuilder(9).append("UTC").append(total < 0 ? '-' : '+').append(hours);
        if (minutes != 0) {
            sb.append(':');
            pad2(sb, minutes);
        }
        return sb.toString();
    }

    private static void pad2(StringBuilder sb, int value) {
        if (value < 10) {
            sb.append('0');
        }
        sb.append(value);
    }
}
