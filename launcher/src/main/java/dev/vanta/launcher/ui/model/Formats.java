package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.ui.Messages;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;

/**
 * Human readable formatting of bytes, durations and timestamps for the UI.
 */
public final class Formats {

    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;

    private final Messages messages;
    private final ZoneId zone;

    /**
     * @param messages messages
     * @param zone     time zone for timestamps
     */
    public Formats(final Messages messages, final ZoneId zone) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /**
     * @param bytes byte count
     * @return e.g. {@code 1.4 GB}, {@code 231 MB}, {@code 12 KB}
     */
    public String bytes(final long bytes) {
        if (bytes < 0) {
            return messages.get("common.unknown");
        }
        if (bytes >= GB) {
            return messages.format("common.bytes.gb", oneDecimal((double) bytes / GB));
        }
        if (bytes >= MB) {
            return messages.format("common.bytes.mb", Long.toString(Math.round((double) bytes / MB)));
        }
        if (bytes >= KB) {
            return messages.format("common.bytes.kb", Long.toString(Math.round((double) bytes / KB)));
        }
        return messages.format("common.bytes.b", Long.toString(bytes));
    }

    /**
     * @param mb mebibytes
     * @return e.g. {@code 16 GB} or {@code 512 MB}
     */
    public String memory(final long mb) {
        if (mb >= 1024 && mb % 1024 == 0) {
            return messages.format("settings.memory.gb", Long.toString(mb / 1024));
        }
        if (mb >= 1024) {
            return messages.format("settings.memory.gb", oneDecimal(mb / 1024d));
        }
        return messages.format("settings.memory.value", Long.toString(mb));
    }

    /**
     * @param duration duration
     * @return {@code m:ss} countdown text
     */
    public String countdown(final Duration duration) {
        final long seconds = Math.max(0L, duration.getSeconds());
        final long minutes = seconds / 60;
        final long rest = seconds % 60;
        return messages.format("common.duration.minutesSeconds", Long.toString(minutes), String.format(Locale.ROOT, "%02d", rest));
    }

    /**
     * @param instant instant
     * @return localised medium date + short time
     */
    public String dateTime(final Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(messages.locale())
            .withZone(zone).format(instant);
    }

    /**
     * @param instant instant
     * @return localised medium date
     */
    public String date(final Instant instant) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(messages.locale()).withZone(zone).format(instant);
    }

    /**
     * Formats an ISO-8601 instant string leniently.
     *
     * @param iso ISO text (may be malformed or empty)
     * @return formatted date/time or the raw text when unparseable
     */
    public String isoDateTime(final String iso) {
        if (iso == null || iso.isBlank()) {
            return messages.get("common.unknown");
        }
        try {
            return dateTime(Instant.parse(iso));
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    /**
     * @param instant instant
     * @return {@code HH:mm:ss} in the local zone
     */
    public String time(final Instant instant) {
        return DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT).withZone(zone).format(instant);
    }

    private static String oneDecimal(final double value) {
        final String s = String.format(Locale.ROOT, "%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
