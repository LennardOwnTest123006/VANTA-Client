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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatsTest {
    @TempDir
    Path dir;

    private MutableClock clock;
    private VantaPaths paths;
    private JsonStore json;
    private SettingsStore settings;
    private StatsStore store;
    private StatsTracker tracker;
    private FakeGameBridge game;

    @BeforeEach
    void setUp() throws IOException {
        clock = MutableClock.standard();
        paths = new VantaPaths(dir);
        paths.createDirectories();
        json = new JsonStore(clock);
        settings = new SettingsStore(VantaSettings.registry(), json, paths.settingsFile(), Optional.empty());
        store = new StatsStore(json, paths);
        store.load();
        tracker = new StatsTracker(store, settings, clock);
        game = new FakeGameBridge();
        game.position = new Vec3d(0, 64, 0);
    }

    private void tick(double dx, double dz) {
        game.position = new Vec3d(game.position.x() + dx, game.position.y(), game.position.z() + dz);
        clock.advance(50);
        tracker.onTick(game);
    }

    @Test
    void distanceIgnoresTeleportsAndDimensionChanges() {
        tracker.onJoinWorld("Survival World");
        tracker.onTick(game);
        for (int i = 0; i < 10; i++) {
            tick(1.0, 0);
        }
        assertEquals(10.0, tracker.current().orElseThrow().distanceBlocks(), 1e-9);
        tick(500, 0);
        assertEquals(10.0, tracker.current().orElseThrow().distanceBlocks(), 1e-9, "teleport > 50 ignored");
        tick(3, 4);
        assertEquals(15.0, tracker.current().orElseThrow().distanceBlocks(), 1e-9);
        game.dimensionId = Optional.of("minecraft:the_nether");
        tick(40, 0);
        assertEquals(15.0, tracker.current().orElseThrow().distanceBlocks(), 1e-9, "dimension change ignored");
        tick(2, 0);
        assertEquals(17.0, tracker.current().orElseThrow().distanceBlocks(), 1e-9);
        assertEquals(50L * 14, tracker.current().orElseThrow().playtimeMs());
    }

    @Test
    void sessionLifecycleAndPersistence() throws IOException {
        tracker.onJoinWorld("Creative Build");
        tracker.onTick(game);
        for (int i = 0; i < 40; i++) {
            tick(0.5, 0);
        }
        tracker.onBlockBroken();
        tracker.onBlockBroken();
        tracker.onBlockPlaced();
        tracker.onScreenshot();
        tracker.onLeave();
        tracker.onJoinServer("Play.Example.NET:25565");
        tracker.onTick(game);
        tick(1, 0);
        SessionRecord record = tracker.endSession().orElseThrow();
        assertEquals(List.of("Creative Build"), record.worlds());
        assertEquals(List.of("play.example.net"), record.servers(), "hostname only, lower-case");
        assertEquals(2, record.blocksBroken());
        assertEquals(1, record.blocksPlaced());
        assertEquals(1, record.screenshots());
        assertEquals(21.0, record.distanceBlocks(), 1e-9, "distance resets across the join");
        assertEquals(120.0, record.averageFps(), 1e-9);
        assertTrue(tracker.current().isEmpty());

        assertEquals(1, store.lifetime().sessions());
        assertEquals(1, store.recentSessions().size());
        assertTrue(Files.exists(paths.statsFile()));
        StatsStore reloaded = new StatsStore(json, paths);
        reloaded.load();
        assertEquals(store.lifetime(), reloaded.lifetime());
        assertEquals(record, reloaded.recentSessions().get(0));

        Path export = dir.resolve("export.json");
        reloaded.exportTo(export);
        assertTrue(Files.readString(export).contains("play.example.net"));
        reloaded.forgetNames(false, true);
        assertTrue(reloaded.lifetime().servers().isEmpty());
        assertEquals(Set.of("Creative Build"), reloaded.lifetime().worlds());
        reloaded.clearAll();
        assertEquals(LifetimeStats.EMPTY, reloaded.lifetime());
        assertTrue(reloaded.recentSessions().isEmpty());
    }

    @Test
    void recentSessionsAreBoundedToThirty() {
        for (int i = 0; i < 35; i++) {
            store.record(new SessionRecord(i, i + 1, 1000, 60, 60, List.of(), List.of(), 1, 0, 0, 0));
        }
        assertEquals(StatsStore.RECENT_SESSIONS, store.recentSessions().size());
        assertEquals(34, store.recentSessions().get(0).startedAt(), "newest first");
        assertEquals(35, store.lifetime().sessions());
        assertEquals(35_000, store.lifetime().playtimeMs());
    }

    @Test
    void privacyFlagsAreHonoured() {
        settings.set(VantaSettings.PRIVACY_STATS_TRACK_SERVERS, false);
        settings.set(VantaSettings.PRIVACY_STATS_TRACK_WORLDS, false);
        tracker.onJoinWorld("Secret");
        tracker.onJoinServer("hidden.example");
        tracker.onTick(game);
        tick(1, 0);
        SessionRecord record = tracker.endSession().orElseThrow();
        assertTrue(record.worlds().isEmpty());
        assertTrue(record.servers().isEmpty());
        assertEquals(1.0, record.distanceBlocks(), 1e-9);

        settings.set(VantaSettings.PRIVACY_STATS_ENABLED, false);
        tracker.onJoinWorld("Nothing");
        tracker.onTick(game);
        tick(5, 0);
        tracker.onBlockBroken();
        assertTrue(tracker.current().isEmpty(), "nothing recorded while disabled");
        assertTrue(tracker.endSession().isEmpty());
        assertEquals(1, store.lifetime().sessions());
        assertEquals(StatsPrivacy.NONE.enabled(), tracker.privacy().enabled());
    }

    @Test
    void emptySessionsAreNotRecorded() {
        tracker.startSession();
        assertTrue(tracker.endSession().isEmpty());
        assertEquals(0, store.lifetime().sessions());
    }

    @Test
    void hostnameNormalisation() {
        assertEquals(Optional.of("mc.hypixel.net"), StatsTracker.hostname("MC.Hypixel.net:25565"));
        assertEquals(Optional.of("::1"), StatsTracker.hostname("[::1]:25565"));
        assertEquals(Optional.of("example.org"), StatsTracker.hostname("user@example.org/path"));
        assertTrue(StatsTracker.hostname("  ").isEmpty());
        assertTrue(StatsTracker.hostname(null).isEmpty());
    }

    @Test
    void sessionRecordJsonIsTolerant() {
        SessionRecord record = SessionRecord.fromJson(com.google.gson.JsonParser.parseString(
                "{\"startedAt\":\"x\",\"worlds\":[1,\"A\"],\"distanceBlocks\":\"NaN\"}").getAsJsonObject());
        assertEquals(0, record.startedAt());
        assertEquals(List.of("1", "A"), record.worlds());
        assertEquals(0.0, record.distanceBlocks());
        assertFalse(record.toJson().get("worlds").getAsJsonArray().isEmpty());
    }
}
