package dev.vanta.launcher.ui.model;

import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Severity of a log line as shown in the Logs page.
 */
public enum LogLevel {
    /** Fine-grained diagnostics. */
    DEBUG,
    /** Normal operation. */
    INFO,
    /** Something unexpected but handled. */
    WARN,
    /** A failure. */
    ERROR;

    /** Minecraft / log4j shape: {@code [12:00:00] [Render thread/WARN]: text} or {@code [Render thread/WARN] (Src)}. */
    private static final Pattern GAME = Pattern.compile("\\[[^\\]]*/(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\]");
    /** Plain level token near the start of a line: {@code WARN:}, {@code [ERROR]}, {@code SEVERE }. */
    private static final Pattern PLAIN = Pattern.compile("^(?:\\S+\\s+){0,3}\\[?(TRACE|DEBUG|FINE|FINER|FINEST|INFO|WARN|WARNING|ERROR|SEVERE|FATAL)\\]?[:\\s]");
    private static final Pattern EXCEPTION = Pattern.compile("^(?:\\s+at\\s|Caused by:|Exception in thread|\\S+Exception[: ])");

    /**
     * Guesses the level of a game output line.
     *
     * @param text line
     * @return level (INFO when unknown)
     */
    public static LogLevel parse(final String text) {
        if (text == null || text.isEmpty()) {
            return INFO;
        }
        Matcher m = GAME.matcher(text);
        if (m.find()) {
            return fromToken(m.group(1));
        }
        m = PLAIN.matcher(text);
        if (m.find()) {
            return fromToken(m.group(1));
        }
        if (EXCEPTION.matcher(text).find()) {
            return ERROR;
        }
        return INFO;
    }

    /**
     * @param level java.util.logging level
     * @return level
     */
    public static LogLevel fromJul(final Level level) {
        if (level == null) {
            return INFO;
        }
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return ERROR;
        }
        if (level.intValue() >= Level.WARNING.intValue()) {
            return WARN;
        }
        if (level.intValue() >= Level.INFO.intValue()) {
            return INFO;
        }
        return DEBUG;
    }

    private static LogLevel fromToken(final String token) {
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "TRACE", "DEBUG", "FINE", "FINER", "FINEST" -> DEBUG;
            case "WARN", "WARNING" -> WARN;
            case "ERROR", "SEVERE", "FATAL" -> ERROR;
            default -> INFO;
        };
    }
}
