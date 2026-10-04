package dev.vanta.core.stats;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mutable accumulator for the running session. {@link StatsTracker} owns it and converts it to a
 * {@link SessionRecord} when the session ends.
 */
public final class SessionStats {
    private final long startedAt;
    private long playtimeMs;
    private long fpsSampleCount;
    private double fpsSum;
    private int maxFps;
    private final Set<String> worlds = new LinkedHashSet<>();
    private final Set<String> servers = new LinkedHashSet<>();
    private double distanceBlocks;
    private int blocksBroken;
    private int blocksPlaced;
    private int screenshots;

    public SessionStats(long startedAt) {
        this.startedAt = startedAt;
    }

    public long startedAt() {
        return startedAt;
    }

    public long playtimeMs() {
        return playtimeMs;
    }

    public void addPlaytime(long deltaMs) {
        if (deltaMs > 0) {
            playtimeMs += deltaMs;
        }
    }

    public void addFpsSample(int fps) {
        if (fps <= 0) {
            return;
        }
        fpsSampleCount++;
        fpsSum += fps;
        maxFps = Math.max(maxFps, fps);
    }

    public double averageFps() {
        return fpsSampleCount == 0 ? 0 : fpsSum / fpsSampleCount;
    }

    public int maxFps() {
        return maxFps;
    }

    public Set<String> worlds() {
        return worlds;
    }

    public Set<String> servers() {
        return servers;
    }

    public double distanceBlocks() {
        return distanceBlocks;
    }

    public void addDistance(double blocks) {
        if (blocks > 0 && Double.isFinite(blocks)) {
            distanceBlocks += blocks;
        }
    }

    public int blocksBroken() {
        return blocksBroken;
    }

    public void incrementBlocksBroken() {
        blocksBroken++;
    }

    public int blocksPlaced() {
        return blocksPlaced;
    }

    public void incrementBlocksPlaced() {
        blocksPlaced++;
    }

    public int screenshots() {
        return screenshots;
    }

    public void incrementScreenshots() {
        screenshots++;
    }

    /** Immutable snapshot ending at {@code endedAt}. */
    public SessionRecord toRecord(long endedAt) {
        return new SessionRecord(startedAt, endedAt, playtimeMs, averageFps(), maxFps, List.copyOf(worlds),
                List.copyOf(servers), distanceBlocks, blocksBroken, blocksPlaced, screenshots);
    }
}
