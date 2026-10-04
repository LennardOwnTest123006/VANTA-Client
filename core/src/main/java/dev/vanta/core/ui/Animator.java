package dev.vanta.core.ui;

import java.util.Objects;

/**
 * Factory and time source for {@link AnimatedValue}s. One animator exists per screen; it carries the clock
 * and the reduced-motion flag from the active {@link Theme}.
 */
public final class Animator {

    private final Clock clock;
    private boolean reducedMotion;
    private long defaultDurationMillis = Theme.MOTION_FAST;

    /** Creates an animator on the given clock. */
    public Animator(Clock clock) {
        this(clock, false);
    }

    /** Creates an animator on the given clock with an initial reduced-motion flag. */
    public Animator(Clock clock, boolean reducedMotion) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reducedMotion = reducedMotion;
    }

    /** Current time from the clock. */
    public long now() {
        return clock.nowMillis();
    }

    /** The underlying clock. */
    public Clock clock() {
        return clock;
    }

    /** Whether animations snap instead of easing. */
    public boolean isReducedMotion() {
        return reducedMotion;
    }

    /** Toggles reduced motion (mirrors {@link Theme#reducedMotion()}). */
    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }

    /** Duration used by {@link AnimatedValue#animateTo(float)}. */
    public long defaultDurationMillis() {
        return defaultDurationMillis;
    }

    /** Changes the default duration (must be positive). */
    public void setDefaultDurationMillis(long millis) {
        if (millis <= 0L) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        this.defaultDurationMillis = millis;
    }

    /** Creates a value starting at {@code initial}. */
    public AnimatedValue value(float initial) {
        return new AnimatedValue(this, initial);
    }

    /** Creates a 0/1 value from a boolean. */
    public AnimatedValue value(boolean on) {
        return new AnimatedValue(this, on ? 1f : 0f);
    }

    /**
     * Normalised, eased progress of a one-shot animation that started at {@code startMillis}. Returns 1 when
     * reduced motion is active or the duration has elapsed.
     */
    public float progress(long startMillis, long durationMillis, Easing easing) {
        if (reducedMotion || durationMillis <= 0L) {
            return 1f;
        }
        long elapsed = now() - startMillis;
        if (elapsed <= 0L) {
            return 0f;
        }
        if (elapsed >= durationMillis) {
            return 1f;
        }
        return easing.apply((float) elapsed / (float) durationMillis);
    }

    /**
     * A periodic 0..1 saw-tooth with the given period; used for caret blinking and indeterminate progress.
     * Returns 0 under reduced motion so blinking elements stay visible.
     */
    public float phase(long periodMillis) {
        if (periodMillis <= 0L || reducedMotion) {
            return 0f;
        }
        return (float) (Math.floorMod(now(), periodMillis)) / (float) periodMillis;
    }
}
