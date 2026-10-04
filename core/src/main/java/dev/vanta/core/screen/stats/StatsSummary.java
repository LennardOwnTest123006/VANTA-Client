package dev.vanta.core.screen.stats;

import dev.vanta.core.stats.LifetimeStats;
import dev.vanta.core.stats.SessionRecord;
import dev.vanta.core.stats.StatsStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Everything the statistics dashboard shows, derived once from the {@link StatsStore}: the lifetime aggregates, the
 * recent sessions oldest-first (for the mini bar charts), the playtime-weighted average frame rate and per-session
 * series for every tile.
 *
 * @param lifetime   lifetime aggregates
 * @param sessions   recent sessions, oldest first
 * @param averageFps playtime-weighted mean of the recent sessions' average fps (0 when none)
 */
public record StatsSummary(LifetimeStats lifetime, List<SessionRecord> sessions, double averageFps) {

    public StatsSummary {
        Objects.requireNonNull(lifetime, "lifetime");
        sessions = List.copyOf(sessions);
    }

    /** Builds the summary from a store. */
    public static StatsSummary of(StatsStore store) {
        List<SessionRecord> oldestFirst = new ArrayList<>(store.recentSessions());
        Collections.reverse(oldestFirst);
        return new StatsSummary(store.lifetime(), oldestFirst, weightedAverageFps(oldestFirst));
    }

    /** Average fps weighted by playtime; sessions without fps samples are ignored. */
    public static double weightedAverageFps(List<SessionRecord> sessions) {
        double weighted = 0;
        double weight = 0;
        int plain = 0;
        double plainSum = 0;
        for (SessionRecord s : sessions) {
            if (s.averageFps() <= 0) {
                continue;
            }
            plain++;
            plainSum += s.averageFps();
            if (s.playtimeMs() > 0) {
                weighted += s.averageFps() * s.playtimeMs();
                weight += s.playtimeMs();
            }
        }
        if (weight > 0) {
            return weighted / weight;
        }
        return plain == 0 ? 0 : plainSum / plain;
    }

    /** True when nothing has been recorded yet. */
    public boolean isEmpty() {
        return lifetime.sessions() == 0 && sessions.isEmpty();
    }

    /** The most recent session, if any. */
    public Optional<SessionRecord> lastSession() {
        return sessions.isEmpty() ? Optional.empty() : Optional.of(sessions.get(sessions.size() - 1));
    }

    /** Per-session series (oldest first). */
    public float[] series(ToDoubleFunction<SessionRecord> value) {
        float[] out = new float[sessions.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) value.applyAsDouble(sessions.get(i));
        }
        return out;
    }

    public float[] playtimeSeries() {
        return series(SessionRecord::playtimeMs);
    }

    public float[] averageFpsSeries() {
        return series(SessionRecord::averageFps);
    }

    public float[] maxFpsSeries() {
        return series(SessionRecord::maxFps);
    }

    public float[] distanceSeries() {
        return series(SessionRecord::distanceBlocks);
    }

    public float[] blocksBrokenSeries() {
        return series(SessionRecord::blocksBroken);
    }

    public float[] blocksPlacedSeries() {
        return series(SessionRecord::blocksPlaced);
    }

    /** Number of distinct worlds across the recent sessions (0 when names are not recorded). */
    public float[] worldsSeries() {
        return series(s -> s.worlds().size());
    }

    /** Number of distinct servers across the recent sessions. */
    public float[] serversSeries() {
        return series(s -> s.servers().size());
    }

    /** Longest recent session in milliseconds (0 when none). */
    public long longestSessionMs() {
        long max = 0;
        for (SessionRecord s : sessions) {
            max = Math.max(max, s.playtimeMs());
        }
        return max;
    }

    /** Mean playtime of the recent sessions in milliseconds (0 when none). */
    public long averageSessionMs() {
        if (sessions.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (SessionRecord s : sessions) {
            total += s.playtimeMs();
        }
        return total / sessions.size();
    }
}
