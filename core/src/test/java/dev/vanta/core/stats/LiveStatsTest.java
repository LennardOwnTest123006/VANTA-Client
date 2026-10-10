package dev.vanta.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeGameBridge;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The live totals ({@link StatsTracker#live()}): stored lifetime plus the running session, without disk writes. */
class LiveStatsTest {
    @TempDir
    Path dir;

    private MutableClock clock;
    private VantaPaths paths;
    private SettingsStore settings;
    private StatsStore store;
    private StatsTracker tracker;
    private FakeGameBridge game;

    @BeforeEach
    void setUp() throws IOException {
        clock = MutableClock.standard();
        paths = new VantaPaths(dir);
        paths.createDirectories();
        JsonStore json = new JsonStore(clock);
        settings = new SettingsStore(VantaSettings.registry(), json, paths.settingsFile(), Optional.empty());
        store = new StatsStore(json, paths);
        store.load();
        tracker = new StatsTracker(store, settings, clock);
        game = new FakeGameBridge();
        game.position = new Vec3d(0, 64, 0);
        game.fps = 144;
        // 2 hours, 1 000 blocks, 50 broken, 20 placed, best 200 fps, one world, one server.
        store.record(new SessionRecord(0, 7_200_000, 7_200_000, 120, 200, List.of("Old World"),
                List.of("play.example.net"), 1000, 50, 20, 0));
        store.save();
    }

    /** One client tick 50 ms later, moving one block along x. */
    private void tick() {
        game.position = new Vec3d(game.position.x() + 1, game.position.y(), game.position.z());
        clock.advance(50);
        tracker.onTick(game);
    }

    private void seconds(int n) {
        for (int i = 0; i < n * 20; i++) {
            tick();
        }
    }

    @Test
    void totalsAreTheStoredValuesPlusTheRunningSession() {
        tracker.onJoinWorld("New World");
        tracker.onTick(game);
        seconds(3);
        tracker.onBlockBroken();
        tracker.onBlockPlaced();
        tracker.onBlockPlaced();
        SessionStats session = tracker.current().orElseThrow();
        LiveStats live = tracker.live();

        assertTrue(live.recording());
        assertEquals(3_000, session.playtimeMs());
        assertEquals(3_000, live.sessionPlaytimeMs());
        assertEquals(store.lifetime().playtimeMs() + session.playtimeMs(), live.playtimeMs(),
                "shown total = stored total + running session");
        assertEquals(7_203_000, live.playtimeMs());
        assertEquals(1060, live.distanceBlocks(), 1e-9);
        assertEquals(51, live.blocksBroken());
        assertEquals(22, live.blocksPlaced());
        assertEquals(200, live.maxFps(), "the stored best is higher than the session's 144");
        assertEquals(2, live.worlds(), "Old World + New World");
        assertEquals(1, live.servers());
        assertEquals(store.lifetime(), live.stored());

        // The totals equal what the store holds once the session is recorded.
        tracker.endSession().orElseThrow();
        assertEquals(live.playtimeMs(), store.lifetime().playtimeMs());
        assertEquals(live.distanceBlocks(), store.lifetime().distanceBlocks(), 1e-9);
        assertEquals(live.blocksPlaced(), store.lifetime().blocksPlaced());
        assertEquals(live.worlds(), store.lifetime().worlds().size());
    }

    @Test
    void playTimeCountsOnlyWhileInAWorld() {
        tracker.onTick(game);
        seconds(2);
        assertEquals(7_202_000, tracker.live().playtimeMs());
        game.inWorld = false;
        seconds(5);
        assertEquals(7_202_000, tracker.live().playtimeMs(), "the title screen does not count");
        assertEquals(2_000, tracker.sessionPlaytimeMs());
        game.inWorld = true;
        tracker.onTick(game);
        seconds(1);
        assertEquals(7_203_000, tracker.live().playtimeMs());
    }

    @Test
    void aBestFpsOfTheRunningSessionRaisesTheTotal() {
        game.fps = 300;
        tracker.onTick(game);
        seconds(2);
        assertEquals(300, tracker.live().maxFps());
        assertEquals(200, store.lifetime().maxFps(), "the store is unchanged until the session ends");
    }

    @Test
    void statisticsOffShowOnlyTheStoredTotals() {
        tracker.onTick(game);
        seconds(2);
        settings.set(VantaSettings.PRIVACY_STATS_ENABLED, false);
        LiveStats off = tracker.live();
        assertFalse(off.recording());
        assertEquals(0, off.sessionPlaytimeMs());
        assertEquals(store.lifetime().playtimeMs(), off.playtimeMs(), "a session that will not be recorded");
        assertEquals(1, off.worlds());
        settings.set(VantaSettings.PRIVACY_STATS_ENABLED, true);
        assertEquals(7_202_000, tracker.live().playtimeMs());
    }

    @Test
    void namesCountOnlyWhileTheirSwitchIsOn() {
        tracker.onJoinWorld("New World");
        tracker.onJoinServer("mc.other.org");
        tracker.onJoinServer("play.example.net:25565");
        tracker.onTick(game);
        seconds(1);
        assertEquals(2, tracker.live().worlds());
        assertEquals(2, tracker.live().servers(), "a known server counts once");
        settings.set(VantaSettings.PRIVACY_STATS_TRACK_WORLDS, false);
        store.forgetNames(true, false);
        assertEquals(0, tracker.live().worlds(), "forgotten names do not come back through the session");
        assertEquals(2, tracker.live().servers());
    }

    @Test
    void namesCollectedBeforeASwitchWasTurnedOffAreNotRecorded() {
        tracker.onJoinWorld("Private World");
        tracker.onJoinServer("secret.example");
        tracker.onTick(game);
        seconds(1);
        settings.set(VantaSettings.PRIVACY_STATS_TRACK_WORLDS, false);
        store.forgetNames(true, false);
        SessionRecord record = tracker.endSession().orElseThrow();
        assertTrue(record.worlds().isEmpty());
        assertEquals(List.of("secret.example"), record.servers());
        assertTrue(store.lifetime().worlds().isEmpty());
        assertEquals(1_000, record.playtimeMs());
    }

    @Test
    void clearAllRestartsTheRunningSessionFromZero() throws IOException {
        tracker.onJoinWorld("New World");
        tracker.onTick(game);
        seconds(4);
        tracker.onBlockBroken();
        Files.delete(paths.statsFile());
        tracker.clearAll();
        assertEquals(LifetimeStats.EMPTY, store.lifetime());
        assertTrue(store.recentSessions().isEmpty());
        assertTrue(Files.exists(paths.statsFile()), "Clear all writes the empty file, as before");
        LiveStats cleared = tracker.live();
        assertEquals(0, cleared.playtimeMs());
        assertEquals(0, cleared.blocksBroken());
        assertEquals(0, cleared.distanceBlocks(), 1e-9);
        assertEquals(0, cleared.worlds());
        assertTrue(tracker.current().isPresent(), "the session keeps running");

        seconds(2);
        assertEquals(2_000, tracker.live().playtimeMs(), "counting goes on from zero");
        SessionRecord record = tracker.endSession().orElseThrow();
        assertEquals(2_000, record.playtimeMs(), "the cleared four seconds are not recorded again");
        assertEquals(0, record.blocksBroken());
        assertEquals(2_000, store.lifetime().playtimeMs());
    }

    @Test
    void tickingForTenMinutesNeverWritesTheStatsFile() throws IOException {
        Files.deleteIfExists(paths.statsFile());
        tracker.onJoinWorld("New World");
        tracker.onTick(game);
        for (int second = 1; second <= 600; second++) {
            seconds(1);
            assertEquals(7_200_000 + second * 1000L, tracker.live().playtimeMs());
        }
        assertFalse(Files.exists(paths.statsFile()), "no write while the session runs");
        assertFalse(store.isDirty());
        tracker.endSession().orElseThrow();
        assertTrue(Files.exists(paths.statsFile()), "one write when the session ends");
        assertEquals(7_800_000, store.lifetime().playtimeMs());
    }
}
