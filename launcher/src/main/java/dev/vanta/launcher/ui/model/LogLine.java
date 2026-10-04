package dev.vanta.launcher.ui.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One line of launcher or game output.
 *
 * @param sequence monotonically increasing number within the buffer
 * @param time     when the line was received
 * @param level    severity
 * @param text     text (already redacted)
 */
public record LogLine(long sequence, Instant time, LogLevel level, String text) {

    public LogLine {
        Objects.requireNonNull(time, "time");
        level = level == null ? LogLevel.INFO : level;
        text = text == null ? "" : text;
    }

    /**
     * @param needle lower-case search text
     * @return whether the line contains the text (case-insensitive)
     */
    public boolean matches(final String needle) {
        return needle.isEmpty() || text.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }
}
