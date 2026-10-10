package dev.vanta.core.stats;

import java.util.Objects;

/**
 * The lifetime totals as they stand right now: the stored {@link LifetimeStats} plus the running session, which
 * {@link StatsTracker#endSession()} adds to {@code stats.json} only when the game closes. Taking a snapshot reads
 * memory only and never touches the disk, so the Statistics screen can show play time counting up live while the
 * file is still written once per session.
 * <p>
 * The running session counts only while it will be recorded (statistics turned on); otherwise its fields are zero and
 * the totals equal the stored ones. World and server names of the running session count only while the matching
 * "remember" switch is on, and the distinct-name counts are capped like the store caps them
 * ({@link LifetimeStats#MAX_NAMES}).
 *
 * @param stored                the aggregates of every recorded session
 * @param recording             true while the running session will be recorded
 * @param sessionPlaytimeMs     play time of the running session (time in a world; 0 when not recording)
 * @param sessionDistanceBlocks distance of the running session
 * @param sessionBlocksBroken   blocks broken in the running session
 * @param sessionBlocksPlaced   blocks placed in the running session
 * @param sessionMaxFps         best fps sample of the running session
 * @param worlds                distinct world names, stored plus the running session's new ones
 * @param servers               distinct server hostnames, stored plus the running session's new ones
 */
public record LiveStats(LifetimeStats stored, boolean recording, long sessionPlaytimeMs, double sessionDistanceBlocks,
                        int sessionBlocksBroken, int sessionBlocksPlaced, int sessionMaxFps, int worlds, int servers) {
    public LiveStats {
        Objects.requireNonNull(stored, "stored");
        sessionPlaytimeMs = Math.max(0L, sessionPlaytimeMs);
        sessionDistanceBlocks = Double.isFinite(sessionDistanceBlocks) ? Math.max(0.0, sessionDistanceBlocks) : 0.0;
    }

    /** Only the stored totals (no running session, or statistics turned off). */
    public static LiveStats storedOnly(LifetimeStats stored, boolean recording) {
        return new LiveStats(stored, recording, 0L, 0.0, 0, 0, 0, stored.worlds().size(), stored.servers().size());
    }

    /** Total play time: stored lifetime play time plus the running session's. */
    public long playtimeMs() {
        return stored.playtimeMs() + sessionPlaytimeMs;
    }

    /** Total distance including the running session. */
    public double distanceBlocks() {
        return stored.distanceBlocks() + sessionDistanceBlocks;
    }

    /** Total blocks broken including the running session. */
    public long blocksBroken() {
        return stored.blocksBroken() + sessionBlocksBroken;
    }

    /** Total blocks placed including the running session. */
    public long blocksPlaced() {
        return stored.blocksPlaced() + sessionBlocksPlaced;
    }

    /** Best fps ever sampled, the running session included. */
    public int maxFps() {
        return Math.max(stored.maxFps(), sessionMaxFps);
    }
}
