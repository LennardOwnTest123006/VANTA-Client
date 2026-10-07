package dev.vanta.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The HUD FPS widget asks the tracker for the "1 % low" figure every client tick. The statistics are computed without
 * copying and sorting the samples each time; they must give exactly what the sort-based definition gives.
 */
class FrameTimeTrackerStatsTest {

    /** The previous implementation: sort a copy, average the slowest ceil(1 %) frames. */
    private static double referenceOnePercentLowFps(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int count = Math.max(1, (int) Math.ceil(sorted.length * 0.01));
        double sum = 0;
        for (int i = sorted.length - count; i < sorted.length; i++) {
            sum += sorted[i];
        }
        double mean = sum / count;
        return mean > 0 ? 1000.0 / mean : 0;
    }

    private static double referencePercentile(double[] values, double p) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double clamped = Math.max(0, Math.min(100, p));
        int rank = (int) Math.ceil(clamped / 100.0 * sorted.length);
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank - 1))];
    }

    @Test
    void matchesTheSortBasedDefinitionExactly() {
        Random random = new Random(42);
        for (int capacity : new int[] {2, 3, 99, 100, 101, 240, 1024}) {
            FrameTimeTracker tracker = new FrameTimeTracker(capacity);
            for (int n = 1; n <= capacity * 3; n++) {
                // Mostly smooth frames with spikes and repeated values, like a real frame time trace.
                double value = random.nextInt(10) == 0 ? 20 + random.nextInt(200) : 6 + random.nextInt(4) * 0.5;
                tracker.record(value);
                double[] kept = tracker.samplesOldestFirst();
                assertEquals(referenceOnePercentLowFps(kept), tracker.onePercentLowFps(), 0.0,
                        "capacity " + capacity + " after " + n + " frames");
                if (n % 7 == 0) {
                    for (double p : new double[] {0, 1, 50, 95, 99, 99.9, 100}) {
                        assertEquals(referencePercentile(kept, p), tracker.percentile(p), 0.0, "p" + p);
                    }
                }
            }
        }
    }

    @Test
    void emptyAndClearedTrackersReportZero() {
        FrameTimeTracker tracker = new FrameTimeTracker();
        assertEquals(0, tracker.onePercentLowFps());
        assertEquals(0, tracker.percentile(99));
        tracker.record(10);
        assertEquals(100, tracker.onePercentLowFps(), 1e-9);
        tracker.clear();
        assertEquals(0, tracker.onePercentLowFps());
    }

    @Test
    void onePercentLowDoesNotAllocatePerCall() {
        if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean threads)
                || !threads.isThreadAllocatedMemorySupported()) {
            return; // not measurable on this JVM
        }
        threads.setThreadAllocatedMemoryEnabled(true);
        FrameTimeTracker tracker = new FrameTimeTracker();
        for (int i = 0; i < FrameTimeTracker.DEFAULT_CAPACITY; i++) {
            tracker.record(5 + (i % 17));
        }
        double sink = 0;
        for (int i = 0; i < 2_000; i++) { // warm up
            sink += tracker.onePercentLowFps();
        }
        long thread = Thread.currentThread().threadId();
        long before = threads.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 10_000; i++) {
            sink += tracker.onePercentLowFps();
        }
        long allocated = threads.getThreadAllocatedBytes(thread) - before;
        assertTrue(sink > 0);
        // The copy-and-sort version allocated a 240-sample copy (about 2 KB) per call, 20 MB here.
        assertTrue(allocated < 256 * 1024, "allocated " + allocated + " bytes over 10 000 calls");
    }
}
