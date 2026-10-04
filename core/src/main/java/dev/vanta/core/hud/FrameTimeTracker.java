package dev.vanta.core.hud;

import java.util.Arrays;

/**
 * Ring buffer of frame durations with average, maximum, percentile and "1 % low" statistics for the performance
 * widgets and the Performance Center graph.
 */
public final class FrameTimeTracker {
    /** Default number of frames kept (about 4 s at 60 fps). */
    public static final int DEFAULT_CAPACITY = 240;

    private final double[] samples;
    private int head;
    private int size;

    public FrameTimeTracker() {
        this(DEFAULT_CAPACITY);
    }

    public FrameTimeTracker(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("capacity must be >= 2");
        }
        this.samples = new double[capacity];
    }

    /** Records one frame duration in milliseconds; non-positive or non-finite values are ignored. */
    public void record(double frameTimeMs) {
        if (!(frameTimeMs > 0) || !Double.isFinite(frameTimeMs)) {
            return;
        }
        samples[head] = frameTimeMs;
        head = (head + 1) % samples.length;
        if (size < samples.length) {
            size++;
        }
    }

    /** Number of samples stored. */
    public int size() {
        return size;
    }

    /** Buffer capacity. */
    public int capacity() {
        return samples.length;
    }

    /** True when no sample exists. */
    public boolean isEmpty() {
        return size == 0;
    }

    /** Mean frame time in ms (0 when empty). */
    public double average() {
        if (size == 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < size; i++) {
            sum += samples[i];
        }
        return sum / size;
    }

    /** Largest frame time in ms (0 when empty). */
    public double max() {
        double max = 0;
        for (int i = 0; i < size; i++) {
            max = Math.max(max, samples[i]);
        }
        return max;
    }

    /** Smallest frame time in ms (0 when empty). */
    public double min() {
        if (size == 0) {
            return 0;
        }
        double min = Double.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            min = Math.min(min, samples[i]);
        }
        return min;
    }

    /**
     * Frame time at the given percentile (0–100) using nearest-rank; 99 gives the frame time only 1 % of frames
     * exceed.
     */
    public double percentile(double p) {
        if (size == 0) {
            return 0;
        }
        double[] sorted = sortedSamples();
        double clamped = Math.max(0, Math.min(100, p));
        int rank = (int) Math.ceil(clamped / 100.0 * sorted.length);
        int index = Math.max(0, Math.min(sorted.length - 1, rank - 1));
        return sorted[index];
    }

    /**
     * "1 % low" frame rate: the fps equivalent of the mean of the slowest 1 % of frames (at least one frame). This
     * is the stutter indicator shown next to the average.
     */
    public double onePercentLowFps() {
        if (size == 0) {
            return 0;
        }
        double[] sorted = sortedSamples();
        int count = Math.max(1, (int) Math.ceil(sorted.length * 0.01));
        double sum = 0;
        for (int i = sorted.length - count; i < sorted.length; i++) {
            sum += sorted[i];
        }
        double mean = sum / count;
        return mean > 0 ? 1000.0 / mean : 0;
    }

    /** Mean of the slowest 1 % of frames in ms. */
    public double onePercentLowFrameTime() {
        double fps = onePercentLowFps();
        return fps > 0 ? 1000.0 / fps : 0;
    }

    /** Frame rate implied by {@link #average()}. */
    public double averageFps() {
        double avg = average();
        return avg > 0 ? 1000.0 / avg : 0;
    }

    /** Samples oldest first (for graphs). */
    public double[] samplesOldestFirst() {
        double[] out = new double[size];
        int start = size < samples.length ? 0 : head;
        for (int i = 0; i < size; i++) {
            out[i] = samples[(start + i) % samples.length];
        }
        return out;
    }

    /** Clears every sample. */
    public void clear() {
        head = 0;
        size = 0;
    }

    private double[] sortedSamples() {
        double[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        return sorted;
    }
}
