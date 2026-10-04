package dev.vanta.core.hud;

/**
 * Clicks-per-second counter for one mouse button: a ring buffer of click timestamps, counted over the last second.
 * Fed by the client from the same input events the game already receives; it never generates clicks.
 */
public final class CpsCounter {
    /** Window length in milliseconds. */
    public static final long WINDOW_MS = 1000L;
    private static final int CAPACITY = 256;

    private final long[] stamps = new long[CAPACITY];
    private int head;
    private int size;
    private int peak;

    /** Records a click at {@code nowMs}. */
    public void click(long nowMs) {
        stamps[head] = nowMs;
        head = (head + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }
        peak = Math.max(peak, cps(nowMs));
    }

    /** Clicks within the last second before {@code nowMs}. */
    public int cps(long nowMs) {
        long cutoff = nowMs - WINDOW_MS;
        int count = 0;
        for (int i = 0; i < size; i++) {
            long stamp = stamps[(head - 1 - i + CAPACITY) % CAPACITY];
            if (stamp > cutoff && stamp <= nowMs) {
                count++;
            } else if (stamp <= cutoff) {
                break;
            }
        }
        return count;
    }

    /** Highest CPS observed since the last reset. */
    public int peak() {
        return peak;
    }

    /** Total clicks remembered (bounded by the buffer size). */
    public int size() {
        return size;
    }

    /** Clears all clicks and the peak. */
    public void reset() {
        head = 0;
        size = 0;
        peak = 0;
    }
}
