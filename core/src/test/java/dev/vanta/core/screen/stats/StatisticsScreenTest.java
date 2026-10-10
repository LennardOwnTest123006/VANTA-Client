package dev.vanta.core.screen.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.stats.SessionStats;
import dev.vanta.core.ui.TestCanvas;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatisticsScreenTest {
    @TempDir
    Path dir;
    private ServicesFixture fx;

    @BeforeEach
    void setUp() {
        fx = new ServicesFixture(dir);
    }

    @Test
    void freshInstallShowsHonestEmptyStates() {
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        assertEquals(9, screen.tiles().size());
        assertEquals("0:00", screen.tile("stats.playtime").orElseThrow().value(), "h:mm:ss / m:ss with seconds");
        assertEquals("—", screen.tile("stats.avg-fps").orElseThrow().value());
        assertEquals(Lang.tr("vanta.stats.tile.none"), screen.tile("stats.playtime").orElseThrow().caption());
        assertTrue(screen.table().isEmpty());
        assertFalse(screen.clearButton().isEnabled());
        assertFalse(screen.disabledBanner().isVisible());
        assertTrue(screen.enabledToggle().isOn());
        assertNotNull(screen.root().findById("stats.empty"));
        StatisticsScreen tall = fx.open(ScreenId.STATISTICS, 854, 1200);
        TestCanvas canvas = fx.ui.frame(tall, -1, -1);
        assertTrue(canvas.ops().stream().anyMatch(op -> op instanceof TestCanvas.Text t
                && t.text().equals(Lang.tr("vanta.stats.no_sessions"))));
    }

    @Test
    void populatedStoreFillsTilesAndTable() {
        fx.recordSessions(4, 30, 120);
        fx.game.inWorld = true;
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        assertEquals("2:00:00", screen.tile("stats.playtime").orElseThrow().value());
        assertEquals("4", screen.tile("stats.sessions").orElseThrow().value());
        assertEquals("120", screen.tile("stats.avg-fps").orElseThrow().value());
        assertEquals("170", screen.tile("stats.max-fps").orElseThrow().value());
        assertEquals("4", screen.tile("stats.worlds").orElseThrow().value());
        assertEquals("4.6 km", screen.tile("stats.distance").orElseThrow().value());
        assertEquals("46", screen.tile("stats.broken").orElseThrow().value());
        assertEquals(4, screen.tile("stats.broken").orElseThrow().series().length);
        assertEquals(Lang.tr("vanta.stats.tile.last_session", "30 min"),
                screen.tile("stats.playtime").orElseThrow().caption());
        SessionTable table = screen.table().orElseThrow();
        assertEquals(4, table.sessions().size());
        assertEquals("World 0", table.whereText(table.sessions().get(0)));
        assertTrue(screen.clearButton().isEnabled());
        List<CurrentSessionCard.Fact> facts = screen.currentCard().facts();
        assertEquals(6, facts.size());
        assertEquals("Survival World", facts.get(5).value());
    }

    @Test
    void disabledStatisticsShowTheBannerAndTurnOnWorks() {
        fx.services.settings().set(VantaSettings.PRIVACY_STATS_ENABLED, false);
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        assertTrue(screen.disabledBanner().isVisible());
        assertFalse(screen.enabledToggle().isOn());
        assertTrue(screen.currentCard().facts().isEmpty());
        fx.click(screen, screen.root().findById("stats.turn-on"));
        assertTrue(fx.services.settings().get(VantaSettings.PRIVACY_STATS_ENABLED));
        assertFalse(screen.disabledBanner().isVisible());
        assertTrue(screen.enabledToggle().isOn());
    }

    @Test
    void privacyTogglesWriteSettingsAndForgetNames() {
        fx.recordSessions(2, 10, 90);
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        assertEquals(2, fx.services.statsStore().lifetime().worlds().size());
        screen.worldsToggle().set(screen.context(), false);
        assertFalse(fx.services.settings().get(VantaSettings.PRIVACY_STATS_TRACK_WORLDS));
        assertEquals(0, fx.services.statsStore().lifetime().worlds().size());
        assertEquals(Lang.tr("vanta.stats.names_hidden"), screen.tile("stats.worlds").orElseThrow().caption());
        SessionTable table = screen.table().orElseThrow();
        assertEquals("—", table.whereText(table.sessions().get(0)), "servers are still tracked, so a dash");
        screen.serversToggle().set(screen.context(), false);
        SessionTable again = screen.table().orElseThrow();
        assertEquals(Lang.tr("vanta.stats.names_hidden"), again.whereText(again.sessions().get(0)));
    }

    @Test
    void exportWritesFileAndClipboardAndClearAsksForConfirmation() throws Exception {
        fx.recordSessions(3, 20, 100);
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        fx.clock.advance(2000);
        Path file = screen.exportJson().orElseThrow();
        assertTrue(Files.isRegularFile(file));
        assertTrue(file.getFileName().toString().startsWith("vanta-statistics-"));
        assertTrue(fx.clipboard.get().contains("recentSessions"));
        var toasts = fx.services.notifications().visible();
        assertEquals(Lang.tr("vanta.stats.export"), toasts.get(toasts.size() - 1).title());
        assertTrue(toasts.get(toasts.size() - 1).body().contains("config/vanta/exports/"));

        // The privacy card sits below the fold at 854x480, so press the button directly.
        screen.clearButton().click(screen.context());
        fx.frame(screen);
        assertNotNull(fx.topDialog(screen));
        fx.cancelDialog(screen);
        assertEquals(3, fx.services.statsStore().lifetime().sessions());
        screen.clearButton().click(screen.context());
        fx.frame(screen);
        fx.clock.advance(2000);
        fx.confirmDialog(screen);
        assertEquals(0, fx.services.statsStore().lifetime().sessions());
        assertTrue(screen.table().isEmpty());
        assertFalse(screen.clearButton().isEnabled());
    }

    /** One client tick as the game runs it: 50 ms later, the services tick (the tracker) and the screen tick. */
    private void tick(StatisticsScreen screen) {
        fx.clock.advance(50);
        fx.services.tick();
        screen.tick();
    }

    private void seconds(StatisticsScreen screen, int n) {
        for (int i = 0; i < n * 20; i++) {
            tick(screen);
        }
    }

    private static String playtime(StatisticsScreen screen) {
        return screen.tile("stats.playtime").orElseThrow().value();
    }

    private static String sessionTime(StatisticsScreen screen) {
        return screen.currentCard().facts().get(0).value();
    }

    @Test
    void playTimeCountsUpLiveEverySecondWhileTheScreenIsOpen() {
        fx.recordSessions(1, 30, 100);
        fx.game.inWorld = true;
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        assertEquals("30:00", playtime(screen));
        assertEquals(Lang.tr("vanta.stats.playtime"), screen.currentCard().facts().get(0).label());
        assertEquals("0:00", sessionTime(screen));
        assertEquals(Lang.tr("vanta.stats.recording"), screen.currentCard().heading());
        // The tracker starts counting on the first tick in a world.
        fx.services.tick();
        int refreshes = screen.liveRefreshes();

        seconds(screen, 1);
        assertEquals("30:01", playtime(screen), "stored 30 min + 1 s of this session");
        assertEquals("0:01", sessionTime(screen));
        for (int s = 2; s <= 75; s++) {
            seconds(screen, 1);
            SessionStats session = fx.services.stats().current().orElseThrow();
            long total = fx.services.statsStore().lifetime().playtimeMs() + session.playtimeMs();
            assertEquals(StatFormats.clock(total), playtime(screen), "shown total = stored + session at " + s + " s");
            assertEquals(StatFormats.clock(session.playtimeMs()), sessionTime(screen));
        }
        assertEquals("31:15", playtime(screen));
        assertEquals("1:15", sessionTime(screen));
        assertEquals(refreshes + 75, screen.liveRefreshes(), "exactly one refresh per second, not one per tick");

        // Half a second shows nothing new: the text changes only when the second does.
        for (int i = 0; i < 10; i++) {
            tick(screen);
        }
        assertEquals("31:15", playtime(screen));
        assertEquals(refreshes + 75, screen.liveRefreshes());
        for (int i = 0; i < 10; i++) {
            tick(screen);
        }
        assertEquals("31:16", playtime(screen));
    }

    @Test
    void anHourOfPlayShowsHoursMinutesAndSeconds() {
        fx.game.inWorld = true;
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        fx.services.tick();
        seconds(screen, 3725);
        assertEquals("1:02:05", playtime(screen));
        assertEquals("1:02:05", sessionTime(screen));
    }

    @Test
    void outsideAWorldTheTimeStandsStillAndTheScreenSaysWhy() {
        fx.recordSessions(1, 30, 100);
        fx.game.inWorld = true;
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        fx.services.tick();
        seconds(screen, 5);
        assertEquals("30:05", playtime(screen));
        fx.game.inWorld = false;
        int refreshes = screen.liveRefreshes();
        seconds(screen, 10);
        assertEquals("30:05", playtime(screen), "play time counts only in a world");
        assertEquals("0:05", sessionTime(screen));
        assertEquals(Lang.tr("vanta.stats.recording.outside_world"), screen.currentCard().heading());
        assertEquals(Lang.tr("vanta.stats.not_in_world"), screen.currentCard().facts().get(5).value());
        int outside = screen.liveRefreshes() - refreshes;
        assertTrue(outside >= 9 && outside <= 10, "about once per second outside a world, was " + outside);
        TestCanvas canvas = fx.ui.frame(screen, -1, -1);
        assertTrue(canvas.ops().stream().anyMatch(op -> op instanceof TestCanvas.Text t
                && t.text().contains("30:05")), "the frame draws the live total");
    }

    @Test
    void sessionTilesFollowTheRunningSession() {
        fx.recordSessions(4, 30, 120);
        fx.game.inWorld = true;
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        assertEquals("46", screen.tile("stats.broken").orElseThrow().value());
        assertEquals("26", screen.tile("stats.placed").orElseThrow().value());
        assertEquals("170", screen.tile("stats.max-fps").orElseThrow().value());
        assertEquals("4", screen.tile("stats.worlds").orElseThrow().value());
        fx.services.stats().onBlockBroken();
        fx.services.stats().onBlockPlaced();
        fx.services.stats().onJoinWorld("Brand New World");
        fx.game.fps = 400;
        fx.services.tick();
        seconds(screen, 2);
        assertEquals("47", screen.tile("stats.broken").orElseThrow().value());
        assertEquals("27", screen.tile("stats.placed").orElseThrow().value());
        assertEquals("400", screen.tile("stats.max-fps").orElseThrow().value());
        assertEquals("5", screen.tile("stats.worlds").orElseThrow().value());
        assertEquals("4", screen.tile("stats.sessions").orElseThrow().value(), "the running session is not recorded yet");
    }

    @Test
    void theOpenScreenNeverWritesTheStatsFile() throws Exception {
        fx.recordSessions(2, 10, 90);
        fx.services.statsStore().save();
        Path file = fx.services.paths().statsFile();
        Files.delete(file);
        fx.game.inWorld = true;
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        fx.services.tick();
        for (int s = 0; s < 120; s++) {
            seconds(screen, 1);
            fx.frame(screen);
        }
        assertEquals(StatFormats.clock(20 * 60_000L + 120_000L), playtime(screen));
        assertFalse(Files.exists(file), "two minutes of live play time, no write");
        screen.onClose();
        assertFalse(Files.exists(file), "closing the screen writes only dirty stores; the session is not one");
        fx.services.shutdown();
        assertTrue(Files.exists(file), "the session is written once, when it ends");
        assertEquals(20 * 60_000L + 120_000L, fx.services.statsStore().lifetime().playtimeMs());
    }

    @Test
    void clearAllShowsZeroAndCountsOnFromThere() {
        fx.recordSessions(3, 20, 100);
        fx.game.inWorld = true;
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        fx.services.tick();
        seconds(screen, 7);
        assertEquals("1:00:07", playtime(screen));
        screen.clearButton().click(screen.context());
        fx.frame(screen);
        fx.clock.advance(2000);
        fx.confirmDialog(screen);
        assertEquals("0:00", playtime(screen));
        assertEquals("0:00", sessionTime(screen));
        seconds(screen, 3);
        assertEquals("0:03", playtime(screen));
        assertEquals("0:03", sessionTime(screen));
    }

    @Test
    void statisticsTurnedOffKeepTheStoredTotal() {
        fx.recordSessions(1, 30, 100);
        fx.game.inWorld = true;
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        fx.services.tick();
        seconds(screen, 4);
        assertEquals("30:04", playtime(screen));
        screen.enabledToggle().set(screen.context(), false);
        assertEquals("30:00", playtime(screen), "a session that will not be recorded is not added");
        seconds(screen, 3);
        assertEquals("30:00", playtime(screen));
        assertTrue(screen.currentCard().facts().isEmpty());
    }

    @Test
    void liveStatisticsStayUsableInTheSmallestWindow() {
        fx.recordSessions(3, 20, 100);
        fx.game.inWorld = true;
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS, 427, 240);
        fx.services.tick();
        seconds(screen, 2);
        fx.frame(screen);
        assertEquals("1:00:02", playtime(screen));
        StatTile tile = screen.tile("stats.playtime").orElseThrow();
        assertTrue(tile.bounds().right() <= 427);
        TestCanvas canvas = fx.ui.frame(screen, -1, -1);
        assertTrue(canvas.ops().stream().anyMatch(op -> op instanceof TestCanvas.Text t
                && t.text().equals("1:00:02")), "the live total fits the tile at 427x240 without clipping");
    }

    @Test
    void compactLayoutUsesTwoTileColumns() {
        fx.recordSessions(3, 20, 100);
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS, 427, 240);
        int firstX = screen.tiles().get(0).bounds().x();
        assertEquals(firstX, screen.tiles().get(2).bounds().x());
        assertTrue(screen.tiles().get(1).bounds().x() > firstX);
        assertTrue(screen.tiles().get(1).bounds().right() <= 427);
    }
}
