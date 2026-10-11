package dev.vanta.core.timer;

import java.util.Objects;

/**
 * Immutable snapshot of the timer for the HUD: its state, the time left and the player's label. The HUD captures one
 * per client tick, so drawing never asks the service anything.
 *
 * @param state       what the timer is doing
 * @param remainingMs time left in milliseconds (0 while idle or ringing)
 * @param label       the player's label, empty when none was typed
 * @param sample      true for the {@link #SAMPLE} the HUD editor shows while no timer is set (the widget marks it as a
 *                    preview), false for every reading of the real timer
 */
public record TimerReading(TimerState state, long remainingMs, String label, boolean sample) {
    /** No timer. */
    public static final TimerReading IDLE = new TimerReading(TimerState.IDLE, 0L, "");
    /** What the HUD editor preview shows while no timer runs: a running timer at {@code 9:59}. */
    public static final TimerReading SAMPLE = new TimerReading(TimerState.RUNNING, 599_000L, "", true);

    public TimerReading {
        Objects.requireNonNull(state, "state");
        remainingMs = Math.max(0L, remainingMs);
        label = label == null ? "" : label;
    }

    /** A reading of the real timer (not a sample). */
    public TimerReading(TimerState state, long remainingMs, String label) {
        this(state, remainingMs, label, false);
    }

    /** True while a timer runs, is paused or rings. */
    public boolean isActive() {
        return state.isActive();
    }

    /** The remaining time as {@code m:ss} or {@code h:mm:ss} (see {@link TimerFormats#remaining(long)}). */
    public String remainingText() {
        return TimerFormats.remaining(remainingMs);
    }
}
