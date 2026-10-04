package dev.vanta.core.screen.stats;

import dev.vanta.core.i18n.Lang;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Human-readable formatting of statistics values: durations ("2h 14m"), distances ("12.4 km"), large counts
 * ("12.3k"), frame rates, dates and relative times ("3 days ago"). Shared by the statistics and profiles screens.
 * All methods are deterministic (ROOT locale, explicit zone) so previews and tests render the same text everywhere.
 */
public final class StatFormats {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private StatFormats() {
    }

    /**
     * Duration: {@code 45 s} below a minute, {@code 14 min} below an hour, {@code 2h 14m} below a day, {@code 3d 4h}
     * from one day on. Negative values are treated as zero.
     */
    public static String duration(long millis) {
        long ms = Math.max(0L, millis);
        if (ms < MINUTE) {
            return Lang.tr("vanta.common.seconds", ms / 1000L);
        }
        if (ms < HOUR) {
            return Lang.tr("vanta.stats.minutes", ms / MINUTE);
        }
        if (ms < DAY) {
            return Lang.tr("vanta.stats.hours_minutes", ms / HOUR, (ms % HOUR) / MINUTE);
        }
        return Lang.tr("vanta.stats.days_hours", ms / DAY, (ms % DAY) / HOUR);
    }

    /** Compact duration for tight columns: {@code 0:45}, {@code 14:03}, {@code 2:14:03}. */
    public static String clock(long millis) {
        long s = Math.max(0L, millis) / 1000L;
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        if (h > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    /** Distance: {@code 842 blocks} below 1 000, {@code 12.4 km} from there on (1 km = 1 000 blocks). */
    public static String distance(double blocks) {
        double b = Math.max(0.0, Double.isFinite(blocks) ? blocks : 0.0);
        if (b < 1000.0) {
            return Lang.tr("vanta.stats.distance_value", Math.round(b));
        }
        return Lang.tr("vanta.stats.km", oneDecimal(b / 1000.0));
    }

    /**
     * Counts: grouped digits below 10 000 ({@code 9,999}), {@code 12.3k} below a million, {@code 1.2M} above.
     */
    public static String count(long value) {
        long v = Math.max(0L, value);
        if (v < 10_000L) {
            return String.format(Locale.ROOT, "%,d", v);
        }
        if (v < 1_000_000L) {
            return oneDecimal(v / 1000.0) + "k";
        }
        return oneDecimal(v / 1_000_000.0) + "M";
    }

    /** Frame rate rounded to a whole number; a dash when nothing was sampled. */
    public static String fps(double value) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            return "—";
        }
        return Long.toString(Math.round(value));
    }

    /** {@code 4 Oct 2026}. */
    public static String date(long epochMillis, ZoneId zone) {
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /** {@code 4 Oct 2026, 12:00}. */
    public static String dateTime(long epochMillis, ZoneId zone) {
        return DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /** {@code 12:00}. */
    public static String time(long epochMillis, ZoneId zone) {
        return TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /**
     * Relative time of a past instant: {@code just now} below a minute, {@code 5 min ago}, {@code 3 h ago},
     * {@code 2 days ago} below a month, otherwise the date.
     */
    public static String relative(long epochMillis, long nowMillis, ZoneId zone) {
        long delta = nowMillis - epochMillis;
        if (delta < MINUTE) {
            return Lang.tr("vanta.stats.ago.just_now");
        }
        if (delta < HOUR) {
            return Lang.tr("vanta.stats.ago.minutes", delta / MINUTE);
        }
        if (delta < DAY) {
            return Lang.tr("vanta.stats.ago.hours", delta / HOUR);
        }
        if (delta < 30 * DAY) {
            return Lang.tr("vanta.stats.ago.days", delta / DAY);
        }
        return date(epochMillis, zone);
    }

    /** One decimal with a dot and no trailing {@code .0} ({@code 12.4}, {@code 3}). */
    public static String oneDecimal(double value) {
        String s = String.format(Locale.ROOT, "%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
