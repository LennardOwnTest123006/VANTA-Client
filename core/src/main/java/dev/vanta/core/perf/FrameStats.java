package dev.vanta.core.perf;

import java.util.Arrays;

/**
 * Allocation-free frame time statistics for one measurement window: a fixed histogram of
 * {@value #BUCKETS} buckets of {@value #BUCKET_MS} ms (0 to 100 ms) plus an overflow count, the sum, the worst frame
 * and the number of hitches (frames longer than {@value #HITCH_MS} ms).
 * <p>
 * {@link #record(double)} is called once per frame and never sorts or allocates; percentiles walk the 400 buckets
 * only when a window is evaluated. Unlike an average, the median and the 99th percentile frame time show stutter:
 * a window with good average FPS and a few 80 ms chunk-build spikes has a poor 1 % low and a hitch count.
 */
public final class FrameStats {
    /** Width of one bucket in milliseconds. */
    public static final double BUCKET_MS = 0.25;
    /** Number of buckets (covering 0 to 100 ms). */
    public static final int BUCKETS = 400;
    /** A frame longer than this is a hitch (a visible stop). */
    public static final double HITCH_MS = 50.0;

    private final int[] counts = new int[BUCKETS];
    /** Sum of the frame times per bucket, so a percentile reports the bucket's mean rather than its edge. */
    private final double[] sums = new double[BUCKETS];
    private int overflow;
    private int count;
    private double sumMs;
    private double maxMs;
    private int hitches;

    /** Records one frame time in milliseconds; non-positive and non-finite values are ignored. */
    public void record(double frameMs) {
        if (!(frameMs > 0.0) || Double.isInfinite(frameMs)) {
            return;
        }
        int bucket = (int) (frameMs / BUCKET_MS);
        if (bucket >= BUCKETS) {
            overflow++;
        } else {
            counts[bucket]++;
            sums[bucket] += frameMs;
        }
        count++;
        sumMs += frameMs;
        if (frameMs > maxMs) {
            maxMs = frameMs;
        }
        if (frameMs > HITCH_MS) {
            hitches++;
        }
    }

    /** Frames recorded. */
    public int count() {
        return count;
    }

    /** Sum of the recorded frame times: the gameplay time this window covers. */
    public double totalMs() {
        return sumMs;
    }

    /** Longest recorded frame (0 when empty). */
    public double maxMs() {
        return maxMs;
    }

    /** Frames longer than {@value #HITCH_MS} ms. */
    public int hitchCount() {
        return hitches;
    }

    /** Mean frame rate (frames / time), 0 when empty. */
    public double averageFps() {
        return sumMs <= 0.0 ? 0.0 : count * 1000.0 / sumMs;
    }

    /**
     * Frame time at quantile {@code q} (0 &lt; q ≤ 1): the mean of the 0.25 ms bucket that holds the q-th frame, or the
     * worst frame when it lies beyond the histogram. 0 when empty.
     */
    public double percentileFrameMs(double q) {
        if (count == 0) {
            return 0.0;
        }
        double clamped = Math.max(0.0, Math.min(1.0, q));
        long rank = Math.max(1L, (long) Math.ceil(clamped * count));
        long seen = 0;
        for (int i = 0; i < BUCKETS; i++) {
            seen += counts[i];
            if (seen >= rank) {
                return sums[i] / counts[i];
            }
        }
        return maxMs;
    }

    /** Median frame rate (from the median frame time), 0 when empty. */
    public double p50Fps() {
        return toFps(percentileFrameMs(0.5));
    }

    /** 99th percentile frame time in milliseconds. */
    public double p99FrameMs() {
        return percentileFrameMs(0.99);
    }

    /** "1 % low" frame rate: the frame rate of the 99th percentile frame time, 0 when empty. */
    public double onePercentLowFps() {
        return toFps(p99FrameMs());
    }

    /** Clears the window. */
    public void reset() {
        Arrays.fill(counts, 0);
        Arrays.fill(sums, 0.0);
        overflow = 0;
        count = 0;
        sumMs = 0.0;
        maxMs = 0.0;
        hitches = 0;
    }

    /** Frames beyond the histogram (over 100 ms). */
    public int overflowCount() {
        return overflow;
    }

    private static double toFps(double frameMs) {
        return frameMs <= 0.0 ? 0.0 : 1000.0 / frameMs;
    }
}
