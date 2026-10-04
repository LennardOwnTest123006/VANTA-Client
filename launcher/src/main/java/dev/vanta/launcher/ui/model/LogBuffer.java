package dev.vanta.launcher.ui.model;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe bounded buffer of log lines. Producers (process output pumps, the logging bridge) append from any
 * thread; consumers subscribe with {@link #addListener} and receive every line on the producer's thread, so view
 * models marshal to the UI thread themselves.
 */
public final class LogBuffer {

    /** Default capacity. */
    public static final int DEFAULT_CAPACITY = 5000;

    private final int capacity;
    private final Clock clock;
    private final ArrayDeque<LogLine> lines;
    private final List<Consumer<LogLine>> listeners = new CopyOnWriteArrayList<>();
    private long sequence;

    /**
     * @param capacity maximum lines kept
     * @param clock    clock for timestamps
     */
    public LogBuffer(final int capacity, final Clock clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lines = new ArrayDeque<>(Math.min(capacity, 1024));
    }

    /**
     * Buffer with the default capacity and the system clock.
     *
     * @return buffer
     */
    public static LogBuffer standard() {
        return new LogBuffer(DEFAULT_CAPACITY, Clock.systemUTC());
    }

    /** @return capacity */
    public int capacity() {
        return capacity;
    }

    /**
     * Appends a line, guessing its level from the text.
     *
     * @param text text
     * @return the stored line
     */
    public LogLine append(final String text) {
        return append(LogLevel.parse(text), text);
    }

    /**
     * Appends a line.
     *
     * @param level level
     * @param text  text
     * @return the stored line
     */
    public LogLine append(final LogLevel level, final String text) {
        final LogLine line;
        synchronized (this) {
            line = new LogLine(++sequence, Instant.now(clock), level, text);
            if (lines.size() >= capacity) {
                lines.pollFirst();
            }
            lines.addLast(line);
        }
        for (Consumer<LogLine> l : listeners) {
            try {
                l.accept(line);
            } catch (RuntimeException ignored) {
                // a failing listener must never break logging
            }
        }
        return line;
    }

    /** @return copy of all lines, oldest first */
    public synchronized List<LogLine> snapshot() {
        return new ArrayList<>(lines);
    }

    /**
     * @param count how many
     * @return the newest {@code count} lines, oldest first
     */
    public synchronized List<LogLine> tail(final int count) {
        final int n = Math.max(0, Math.min(count, lines.size()));
        final List<LogLine> out = new ArrayList<>(n);
        final Object[] all = lines.toArray();
        for (int i = all.length - n; i < all.length; i++) {
            out.add((LogLine) all[i]);
        }
        return out;
    }

    /** @return number of lines */
    public synchronized int size() {
        return lines.size();
    }

    /** Removes all lines. */
    public synchronized void clear() {
        lines.clear();
    }

    /**
     * @param listener receives every appended line on the appending thread
     */
    public void addListener(final Consumer<LogLine> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * @param listener listener to remove
     */
    public void removeListener(final Consumer<LogLine> listener) {
        listeners.remove(listener);
    }
}
