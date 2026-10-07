package dev.vanta.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameStatsTest {

    @Test
    void emptyWindowReportsZeros() {
        FrameStats stats = new FrameStats();
        assertEquals(0, stats.count());
        assertEquals(0.0, stats.p50Fps());
        assertEquals(0.0, stats.onePercentLowFps());
        assertEquals(0.0, stats.averageFps());
        assertEquals(0, stats.hitchCount());
    }

    @Test
    void steadySixtyFpsReadsAsSixty() {
        FrameStats stats = new FrameStats();
        for (int i = 0; i < 600; i++) {
            stats.record(1000.0 / 60.0);
        }
        assertEquals(600, stats.count());
        assertEquals(10_000.0, stats.totalMs(), 1e-6);
        assertEquals(60.0, stats.p50Fps(), 0.6);
        assertEquals(60.0, stats.onePercentLowFps(), 0.6);
        assertEquals(60.0, stats.averageFps(), 1e-6);
        assertEquals(0, stats.hitchCount());
    }

    @Test
    void spikesHurtTheOnePercentLowAndCountAsHitchesButNotTheMedian() {
        FrameStats stats = new FrameStats();
        for (int i = 0; i < 1000; i++) {
            stats.record(i % 50 == 0 ? 80.0 : 5.0); // 2 % of the frames are 80 ms chunk-build spikes
        }
        assertEquals(200.0, stats.p50Fps(), 5.0, "the median ignores the spikes");
        assertEquals(1000.0 / 80.0, stats.onePercentLowFps(), 0.5, "the 1 % low sees them");
        assertEquals(20, stats.hitchCount());
        assertEquals(80.0, stats.maxMs());
        assertTrue(stats.averageFps() < stats.p50Fps());
    }

    @Test
    void framesBeyondTheHistogramUseTheWorstFrame() {
        FrameStats stats = new FrameStats();
        stats.record(250.0);
        stats.record(400.0);
        assertEquals(2, stats.overflowCount());
        assertEquals(400.0, stats.p99FrameMs());
        assertEquals(2.5, stats.onePercentLowFps(), 1e-9);
    }

    @Test
    void invalidSamplesAreIgnoredAndResetClears() {
        FrameStats stats = new FrameStats();
        stats.record(0.0);
        stats.record(-3.0);
        stats.record(Double.NaN);
        stats.record(Double.POSITIVE_INFINITY);
        assertEquals(0, stats.count());
        stats.record(10.0);
        stats.record(60.0);
        stats.reset();
        assertEquals(0, stats.count());
        assertEquals(0, stats.hitchCount());
        assertEquals(0.0, stats.totalMs());
        assertEquals(0.0, stats.maxMs());
    }
}
