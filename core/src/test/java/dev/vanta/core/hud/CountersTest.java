package dev.vanta.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CountersTest {
    @Test
    void cpsCountsClicksInTheLastSecond() {
        CpsCounter counter = new CpsCounter();
        assertEquals(0, counter.cps(1000));
        for (int i = 0; i < 8; i++) {
            counter.click(1000 + i * 100);
        }
        assertEquals(8, counter.cps(1700));
        assertEquals(8, counter.cps(1999));
        assertEquals(7, counter.cps(2000), "click at t=1000 falls out of the (1000, 2000] window");
        assertEquals(1, counter.cps(2650));
        assertEquals(0, counter.cps(5000));
        assertEquals(8, counter.peak());
        counter.reset();
        assertEquals(0, counter.peak());
        assertEquals(0, counter.size());
    }

    @Test
    void cpsRingBufferOverflowsSafely() {
        CpsCounter counter = new CpsCounter();
        for (int i = 0; i < 1000; i++) {
            counter.click(i * 2L);
        }
        assertTrue(counter.cps(1999) > 0);
        assertEquals(256, counter.size());
    }

    @Test
    void frameTimeStatistics() {
        FrameTimeTracker tracker = new FrameTimeTracker(100);
        assertTrue(tracker.isEmpty());
        assertEquals(0, tracker.average());
        for (int i = 0; i < 99; i++) {
            tracker.record(10.0);
        }
        tracker.record(100.0);
        tracker.record(-5);
        tracker.record(Double.NaN);
        assertEquals(100, tracker.size());
        assertEquals(10.9, tracker.average(), 1e-9);
        assertEquals(100.0, tracker.max());
        assertEquals(10.0, tracker.min());
        assertEquals(10.0, tracker.percentile(50));
        assertEquals(100.0, tracker.percentile(100));
        assertEquals(10.0, tracker.percentile(98));
        assertEquals(100.0, tracker.percentile(99.5));
        assertEquals(10.0, tracker.onePercentLowFps(), 1e-9, "slowest 1 % = the 100 ms frame → 10 fps");
        assertEquals(100.0, tracker.onePercentLowFrameTime(), 1e-9);
        assertEquals(1000.0 / 10.9, tracker.averageFps(), 1e-9);
    }

    @Test
    void frameTimeRingBufferKeepsOrder() {
        FrameTimeTracker tracker = new FrameTimeTracker(4);
        for (int i = 1; i <= 6; i++) {
            tracker.record(i);
        }
        assertEquals(4, tracker.size());
        double[] samples = tracker.samplesOldestFirst();
        assertEquals(3.0, samples[0]);
        assertEquals(6.0, samples[3]);
        assertEquals(6.0, tracker.max());
        tracker.clear();
        assertEquals(0, tracker.size());
        assertEquals(FrameTimeTracker.DEFAULT_CAPACITY, new FrameTimeTracker().capacity());
    }
}
