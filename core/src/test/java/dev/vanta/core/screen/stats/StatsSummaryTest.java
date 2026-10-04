package dev.vanta.core.screen.stats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.stats.SessionRecord;
import dev.vanta.core.stats.StatsStore;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatsSummaryTest {
    @TempDir
    Path dir;

    private static SessionRecord session(long start, long playtime, double avg, int max, double distance, int broken) {
        return new SessionRecord(start, start + playtime, playtime, avg, max, List.of("w"), List.of(), distance, broken,
                broken / 2, 0);
    }

    @Test
    void emptyStoreGivesAnEmptySummary() {
        StatsStore store = new StatsStore(new JsonStore(MutableClock.standard()), new VantaPaths(dir));
        StatsSummary summary = StatsSummary.of(store);
        assertTrue(summary.isEmpty());
        assertEquals(0.0, summary.averageFps());
        assertEquals(0, summary.playtimeSeries().length);
        assertTrue(summary.lastSession().isEmpty());
        assertEquals(0, summary.averageSessionMs());
        assertEquals(0, summary.longestSessionMs());
    }

    @Test
    void aggregatesOldestFirstWithPlaytimeWeightedAverage() {
        StatsStore store = new StatsStore(new JsonStore(MutableClock.standard()), new VantaPaths(dir));
        store.record(session(1_000, 60_000, 100, 150, 500, 10));
        store.record(session(2_000, 180_000, 200, 260, 1500, 30));
        store.record(session(3_000, 0, 0, 0, 0, 0));
        StatsSummary summary = StatsSummary.of(store);
        assertEquals(3, summary.sessions().size());
        assertEquals(1_000, summary.sessions().get(0).startedAt(), "oldest first");
        assertEquals(3_000, summary.lastSession().orElseThrow().startedAt());
        assertEquals(175.0, summary.averageFps(), 1e-9, "weighted by playtime, zero-fps session ignored");
        assertArrayEquals(new float[] {60_000f, 180_000f, 0f}, summary.playtimeSeries());
        assertArrayEquals(new float[] {100f, 200f, 0f}, summary.averageFpsSeries());
        assertArrayEquals(new float[] {150f, 260f, 0f}, summary.maxFpsSeries());
        assertArrayEquals(new float[] {500f, 1500f, 0f}, summary.distanceSeries());
        assertArrayEquals(new float[] {10f, 30f, 0f}, summary.blocksBrokenSeries());
        assertArrayEquals(new float[] {5f, 15f, 0f}, summary.blocksPlacedSeries());
        assertArrayEquals(new float[] {1f, 1f, 1f}, summary.worldsSeries());
        assertEquals(180_000, summary.longestSessionMs());
        assertEquals(80_000, summary.averageSessionMs());
        assertEquals(3, summary.lifetime().sessions());
        assertEquals(260, summary.lifetime().maxFps());

        assertEquals(150.0, StatsSummary.weightedAverageFps(List.of(session(0, 0, 100, 0, 0, 0),
                session(0, 0, 200, 0, 0, 0))), 1e-9, "plain mean when no playtime");
    }
}
