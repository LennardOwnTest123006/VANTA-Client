package dev.vanta.core.timer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Canvas-free formatting and input parsing of the timer: the countdown text ({@code 9:59}, {@code 1:00:00}), the
 * time of day the alarm rang ({@code 3:42 PM}) and the custom duration typed as minutes and seconds. Deterministic
 * (ROOT/English locale, explicit zone) so screens, the HUD and tests show the same text everywhere.
 */
public final class TimerFormats {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private TimerFormats() {
    }

    /**
     * The countdown text: {@code m:ss} below an hour, {@code h:mm:ss} from one hour on. Partial seconds round up, so a
     * fresh 10 minute timer reads {@code 10:00}, the last second reads {@code 0:01} and {@code 0:00} only shows once
     * the time is up. Negative values read as zero.
     */
    public static String remaining(long millis) {
        long ms = Math.max(0L, millis);
        long totalSeconds = ms / 1000L + (ms % 1000L == 0L ? 0L : 1L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder sb = new StringBuilder(8);
        if (hours > 0) {
            sb.append(hours).append(':');
            pad2(sb, minutes);
        } else {
            sb.append(minutes);
        }
        sb.append(':');
        pad2(sb, seconds);
        return sb.toString();
    }

    /** Time of day in 12-hour time with AM/PM, {@code 3:42 PM}. */
    public static String timeOfDay(long epochMillis, ZoneId zone) {
        return TIME.format(Instant.ofEpochMilli(epochMillis).atZone(Objects.requireNonNull(zone, "zone")));
    }

    /**
     * The zone the Timer screens show times of day in: the PC's time zone, read on every call, so a change of the
     * PC's zone or the switch to or from daylight saving time is followed without a restart. The services clock
     * stays in UTC; only the formatting uses this zone.
     */
    public static ZoneId localZone() {
        return ZoneId.systemDefault();
    }

    /**
     * Minutes for the minutes field when a duration is shown in the custom fields (whole minutes, seconds dropped).
     */
    public static long minutesPart(long millis) {
        return Math.max(0L, millis) / 60_000L;
    }

    /** Seconds (0 to 59) for the seconds field. */
    public static long secondsPart(long millis) {
        return (Math.max(0L, millis) / 1000L) % 60L;
    }

    /**
     * The custom duration typed into the minutes and seconds fields. Blank fields count as zero; only digits are
     * accepted; seconds go from 0 to 59; the total must be at least {@link TimerService#MIN_DURATION_MS} and at most
     * {@link TimerService#MAX_DURATION_MS} (24 hours).
     *
     * @param millis   the duration when {@link #ok()}, otherwise 0
     * @param errorKey translation key of the reason when not {@link #ok()}, otherwise empty
     */
    public record Duration(long millis, String errorKey) {
        /** Reason: something other than digits was typed. */
        public static final String NOT_A_NUMBER = "vanta.timer.error.not_a_number";
        /** Reason: seconds above 59. */
        public static final String SECONDS_RANGE = "vanta.timer.error.seconds_range";
        /** Reason: zero in both fields. */
        public static final String TOO_SHORT = "vanta.timer.error.too_short";
        /** Reason: more than 24 hours. */
        public static final String TOO_LONG = "vanta.timer.error.too_long";

        public Duration {
            errorKey = errorKey == null ? "" : errorKey;
        }

        /** True when the input is a valid duration. */
        public boolean ok() {
            return errorKey.isEmpty();
        }

        static Duration error(String key) {
            return new Duration(0L, key);
        }
    }

    /** Parses the custom minutes and seconds fields (see {@link Duration}). */
    public static Duration parseDuration(String minutesText, String secondsText) {
        String minutes = minutesText == null ? "" : minutesText.trim();
        String seconds = secondsText == null ? "" : secondsText.trim();
        if (!digits(minutes) || !digits(seconds)) {
            return Duration.error(Duration.NOT_A_NUMBER);
        }
        // More digits than any valid value: reject before parsing so huge inputs cannot overflow.
        if (minutes.length() > 6) {
            return Duration.error(Duration.TOO_LONG);
        }
        if (seconds.length() > 2) {
            return Duration.error(Duration.SECONDS_RANGE);
        }
        long m = minutes.isEmpty() ? 0L : Long.parseLong(minutes);
        long s = seconds.isEmpty() ? 0L : Long.parseLong(seconds);
        if (s > 59L) {
            return Duration.error(Duration.SECONDS_RANGE);
        }
        long total = (m * 60L + s) * 1000L;
        if (total < TimerService.MIN_DURATION_MS) {
            return Duration.error(Duration.TOO_SHORT);
        }
        if (total > TimerService.MAX_DURATION_MS) {
            return Duration.error(Duration.TOO_LONG);
        }
        return new Duration(total, "");
    }

    /** True for an empty string or ASCII digits only. */
    static boolean digits(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static void pad2(StringBuilder sb, long value) {
        if (value < 10) {
            sb.append('0');
        }
        sb.append(value);
    }
}
