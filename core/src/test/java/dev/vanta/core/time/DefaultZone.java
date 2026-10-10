package dev.vanta.core.time;

import java.util.TimeZone;

/**
 * Runs test code with another JVM default time zone (what {@code ZoneId.systemDefault()} returns, so what
 * {@link TimeZoneChoice#AUTO} follows) and always restores the previous default afterwards. Core tests run one after
 * another in one JVM, so a temporary default never leaks into another test.
 */
public final class DefaultZone {
    private DefaultZone() {
    }

    /** Runs {@code body} while the default time zone is {@code zoneId}. */
    public static void with(String zoneId, Runnable body) {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        try {
            body.run();
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
