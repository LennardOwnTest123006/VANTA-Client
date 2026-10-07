package dev.vanta.core.perf;

import java.util.Optional;

/**
 * Watches the rolling frame rate against a target and suggests a lower or higher render distance.
 * <p>
 * Rules: samples cover at least {@value #WINDOW_MS} ms; the average must stay below {@value #LOW_RATIO} × target to
 * suggest −2 chunks, or above {@value #HIGH_RATIO} × target (and the frame rate must not be capped) to suggest +2;
 * after any suggestion the advisor waits {@value #COOLDOWN_MS} ms. It only suggests — applying is the caller's
 * decision.
 */
public final class RenderDistanceAdvisor {
    /** Averaging window. */
    public static final long WINDOW_MS = 10_000L;
    /** Pause after a suggestion. */
    public static final long COOLDOWN_MS = 60_000L;
    /** Below this fraction of the target the distance is too high. */
    public static final double LOW_RATIO = 0.75;
    /** Above this fraction of the target there is headroom. */
    public static final double HIGH_RATIO = 1.6;
    /** Chunks per step. */
    public static final int STEP = 2;
    /** Smallest distance ever suggested. */
    public static final int MIN_DISTANCE = 6;
    /** Largest distance ever suggested. */
    public static final int MAX_DISTANCE = 32;
    private static final int CAPACITY = 1024;

    /** Direction of a suggestion. */
    public enum Direction {
        LOWER, HIGHER
    }

    /**
     * A suggestion.
     *
     * @param direction  lower or higher
     * @param from       current distance
     * @param to         suggested distance
     * @param averageFps measured average
     * @param targetFps  target used
     * @param createdAt  epoch millis
     */
    public record Suggestion(Direction direction, int from, int to, double averageFps, int targetFps, long createdAt) {
    }

    private final int[] fps = new int[CAPACITY];
    private final long[] times = new long[CAPACITY];
    private int head;
    private int size;
    private long cooldownUntil;
    private Suggestion last;

    /** Records one fps reading. */
    public void sample(int fpsValue, long nowMs) {
        fps[head] = Math.max(0, fpsValue);
        times[head] = nowMs;
        head = (head + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }
    }

    /** Average fps over the window ending at {@code nowMs}, or empty when the window is not yet covered. */
    public Optional<Double> windowAverage(long nowMs) {
        long cutoff = nowMs - WINDOW_MS;
        long oldest = Long.MAX_VALUE;
        long sum = 0;
        int count = 0;
        for (int i = 0; i < size; i++) {
            int index = (head - 1 - i + CAPACITY) % CAPACITY;
            if (times[index] < cutoff) {
                break;
            }
            oldest = Math.min(oldest, times[index]);
            sum += fps[index];
            count++;
        }
        if (count < 2 || nowMs - oldest < WINDOW_MS * 0.9) {
            return Optional.empty();
        }
        return Optional.of(sum / (double) count);
    }

    /**
     * Evaluates the window.
     *
     * @param currentDistance current render distance
     * @param targetFps       frame rate the user wants (fps limit, or 60 when unlimited)
     * @param fpsCapped       true when the frame rate is limited by vsync or the fps limit, which hides headroom
     * @param nowMs           now
     */
    public Optional<Suggestion> evaluate(int currentDistance, int targetFps, boolean fpsCapped, long nowMs) {
        if (targetFps <= 0 || nowMs < cooldownUntil) {
            return Optional.empty();
        }
        Optional<Double> average = windowAverage(nowMs);
        if (average.isEmpty()) {
            return Optional.empty();
        }
        double avg = average.get();
        Suggestion suggestion = null;
        if (avg < targetFps * LOW_RATIO && currentDistance > MIN_DISTANCE) {
            int to = Math.max(MIN_DISTANCE, currentDistance - STEP);
            suggestion = new Suggestion(Direction.LOWER, currentDistance, to, avg, targetFps, nowMs);
        } else if (!fpsCapped && avg > targetFps * HIGH_RATIO && currentDistance < MAX_DISTANCE) {
            int to = Math.min(MAX_DISTANCE, currentDistance + STEP);
            suggestion = new Suggestion(Direction.HIGHER, currentDistance, to, avg, targetFps, nowMs);
        }
        if (suggestion != null) {
            last = suggestion;
            cooldownUntil = nowMs + COOLDOWN_MS;
            reset();
        }
        return Optional.ofNullable(suggestion);
    }

    /** Most recent suggestion. */
    public Optional<Suggestion> lastSuggestion() {
        return Optional.ofNullable(last);
    }

    /** Clears samples (call after the distance changed so stale readings do not count). */
    public void reset() {
        head = 0;
        size = 0;
    }

    /** True while the advisor waits after a suggestion. */
    public boolean inCooldown(long nowMs) {
        return nowMs < cooldownUntil;
    }
}
