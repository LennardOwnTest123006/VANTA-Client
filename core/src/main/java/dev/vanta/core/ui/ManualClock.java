package dev.vanta.core.ui;

/**
 * A {@link Clock} that only moves when told to. Used by unit tests and by the preview renderer to capture
 * animation frames at exact timestamps.
 */
public final class ManualClock implements Clock {

    private long now;

    /** Starts at zero. */
    public ManualClock() {
        this(0L);
    }

    /** Starts at the given time. */
    public ManualClock(long startMillis) {
        this.now = startMillis;
    }

    @Override
    public long nowMillis() {
        return now;
    }

    /** Moves the clock forward. */
    public void advance(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("Cannot advance by a negative amount");
        }
        now += millis;
    }

    /** Sets an absolute time. */
    public void set(long millis) {
        now = millis;
    }
}
