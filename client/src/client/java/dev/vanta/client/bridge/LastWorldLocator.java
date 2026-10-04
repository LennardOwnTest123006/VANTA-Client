package dev.vanta.client.bridge;

import dev.vanta.client.VantaClient;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * Finds the most recently played singleplayer world for the main menu's "Continue" button.
 * <p>
 * Level summaries are read from disk asynchronously (exactly like the vanilla world list) and cached for a few
 * seconds, so the main menu never blocks the render thread while it polls {@link #lastWorldName()} every frame.
 */
public final class LastWorldLocator {
    /** How long a cached answer is reused before the saves folder is scanned again. */
    static final long REFRESH_INTERVAL_MS = 3000L;

    /**
     * The chosen world.
     *
     * @param levelId    folder name used by {@code WorldOpenFlows.openWorld}
     * @param name       display name
     * @param lastPlayed epoch millis
     */
    record LastWorld(String levelId, String name, long lastPlayed) {
    }

    private volatile Optional<LastWorld> latest = Optional.empty();
    private volatile long refreshedAt;
    private volatile boolean loading;

    /** Display name of the most recently played world, if the saves folder has one. */
    public Optional<String> lastWorldName() {
        refreshIfStale();
        return latest.map(LastWorld::name);
    }

    /**
     * Opens the most recently played world.
     *
     * @return false when no world is known yet (the caller should open the world list instead)
     */
    public boolean continueLastWorld() {
        refreshIfStale();
        Optional<LastWorld> world = latest;
        if (world.isEmpty()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.createWorldOpenFlows().openWorld(world.get().levelId(),
                () -> minecraft.setScreen(new SelectWorldScreen(new TitleScreen())));
        return true;
    }

    private void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (loading || now - refreshedAt < REFRESH_INTERVAL_MS) {
            return;
        }
        loading = true;
        try {
            LevelStorageSource source = Minecraft.getInstance().getLevelSource();
            LevelStorageSource.LevelCandidates candidates = source.findLevelCandidates();
            source.loadLevelSummaries(candidates).whenComplete((summaries, error) -> {
                if (error != null) {
                    VantaClient.LOGGER.debug("Could not read level summaries", error);
                } else {
                    latest = pickLatest(summaries);
                }
                refreshedAt = System.currentTimeMillis();
                loading = false;
            });
        } catch (RuntimeException e) {
            VantaClient.LOGGER.debug("Could not list level candidates", e);
            latest = Optional.empty();
            refreshedAt = now;
            loading = false;
        }
    }

    /** The playable world with the newest {@code lastPlayed} stamp. */
    static Optional<LastWorld> pickLatest(List<LevelSummary> summaries) {
        LastWorld best = null;
        for (LevelSummary summary : summaries) {
            if (summary.isLocked() || summary.isDisabled() || summary.requiresManualConversion()) {
                continue;
            }
            if (best == null || summary.getLastPlayed() > best.lastPlayed()) {
                best = new LastWorld(summary.getLevelId(), summary.getLevelName(), summary.getLastPlayed());
            }
        }
        return Optional.ofNullable(best);
    }
}
