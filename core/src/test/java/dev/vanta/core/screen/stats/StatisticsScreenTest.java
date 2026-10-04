package dev.vanta.core.screen.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.settings.VantaSettings;
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
        assertEquals("0 s", screen.tile("stats.playtime").orElseThrow().value());
        assertEquals("—", screen.tile("stats.avg-fps").orElseThrow().value());
        assertEquals(Lang.tr("vanta.stats.tile.none"), screen.tile("stats.playtime").orElseThrow().caption());
        assertTrue(screen.table().isEmpty());
        assertFalse(screen.clearButton().isEnabled());
        assertFalse(screen.disabledBanner().isVisible());
        assertTrue(screen.enabledToggle().isOn());
        TestCanvas canvas = fx.ui.frame(screen, -1, -1);
        assertTrue(canvas.ops().stream().anyMatch(op -> op instanceof TestCanvas.Text t
                && t.text().equals(Lang.tr("vanta.stats.no_sessions"))));
    }

    @Test
    void populatedStoreFillsTilesAndTable() {
        fx.recordSessions(4, 30, 120);
        fx.game.inWorld = true;
        StatisticsScreen screen = fx.open(ScreenId.STATISTICS);
        assertEquals("2h 0m", screen.tile("stats.playtime").orElseThrow().value());
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

        fx.click(screen, screen.clearButton());
        assertNotNull(fx.topDialog(screen));
        fx.cancelDialog(screen);
        assertEquals(3, fx.services.statsStore().lifetime().sessions());
        fx.click(screen, screen.clearButton());
        fx.clock.advance(2000);
        fx.confirmDialog(screen);
        assertEquals(0, fx.services.statsStore().lifetime().sessions());
        assertTrue(screen.table().isEmpty());
        assertFalse(screen.clearButton().isEnabled());
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
