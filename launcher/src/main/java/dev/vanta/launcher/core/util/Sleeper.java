package dev.vanta.launcher.core.util;

import java.time.Duration;

/**
 * Abstraction over {@link Thread#sleep(long)} so retry back-off and OAuth polling are testable without waiting.
 */
@FunctionalInterface
public interface Sleeper {

    /** Real sleeper backed by {@link Thread#sleep(long)}. */
    Sleeper REAL = duration -> Thread.sleep(Math.max(0L, duration.toMillis()));

    /** Sleeper that returns immediately (tests). */
    Sleeper NONE = duration -> {
    };

    /**
     * Blocks for the given duration.
     *
     * @param duration how long to sleep
     * @throws InterruptedException when interrupted
     */
    void sleep(Duration duration) throws InterruptedException;
}
