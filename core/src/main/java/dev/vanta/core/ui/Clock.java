package dev.vanta.core.ui;

/**
 * Source of monotonic time in milliseconds. The UI kit never reads the system clock directly so that
 * animations, caret blinking and tooltips are deterministic in tests and previews.
 */
@FunctionalInterface
public interface Clock {

    /** Current time in milliseconds. Only differences between calls are meaningful. */
    long nowMillis();

    /** A clock backed by {@link System#nanoTime()}. */
    static Clock system() {
        final long origin = System.nanoTime();
        return () -> (System.nanoTime() - origin) / 1_000_000L;
    }
}
