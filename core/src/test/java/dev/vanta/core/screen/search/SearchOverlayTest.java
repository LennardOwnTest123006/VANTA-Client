package dev.vanta.core.screen.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.search.GlobalSearchResult;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.TestCanvas;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchOverlayTest {
    @TempDir
    Path dir;
    private ScreenTestSupport t;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
    }

    @Test
    void emptyQueryListsEveryScreenAndFocusesTheField() {
        SearchOverlay overlay = t.show(ScreenId.SEARCH, 854, 480);
        assertSame(overlay.field(), overlay.context().focus().focused());
        List<SearchOverlay.Entry> entries = overlay.entries();
        assertTrue(entries.get(0).isHeader());
        assertEquals(SearchOverlay.Group.SCREENS, entries.get(0).group());
        assertEquals(13, overlay.results().size(), "one result per VANTA screen action");
        assertEquals(1, overlay.selectedIndex());
        assertFalse(overlay.isPauseScreen());
    }

    @Test
    void typingFiltersSynchronouslyAndGroupsResults() {
        SearchOverlay overlay = t.show(ScreenId.SEARCH, 854, 480);
        ScreenTestSupport.type(overlay, "render");
        assertEquals("render", overlay.query());
        List<GlobalSearchResult> results = overlay.results();
        assertFalse(results.isEmpty());
        assertInstanceOf(GlobalSearchResult.SettingResult.class, results.get(0));
        assertEquals("Render distance", results.get(0).title());
        assertFalse(results.get(0).highlights().isEmpty(), "matched range for highlighting");
        TestCanvas canvas = t.frame(overlay, -1000, -1000);
        assertTrue(canvas.hasText("Render"), "highlighted prefix drawn separately");
        assertTrue(canvas.hasText("Settings"), "group header");

        overlay.field().setText("hud");
        List<SearchOverlay.Entry> entries = overlay.entries();
        int headers = (int) entries.stream().filter(SearchOverlay.Entry::isHeader).count();
        assertTrue(headers >= 2, "settings and screen/action groups");
        for (int i = 1; i < entries.size(); i++) {
            if (!entries.get(i).isHeader() && !entries.get(i - 1).isHeader()) {
                assertEquals(entries.get(i - 1).group(), entries.get(i).group(), "results stay inside their group");
            }
        }
    }

    @Test
    void arrowsMoveTheSelectionAndSkipHeaders() {
        SearchOverlay overlay = t.show(ScreenId.SEARCH, 854, 480);
        overlay.field().setText("hud");
        int first = overlay.selectedIndex();
        assertFalse(overlay.entries().get(first).isHeader());
        ScreenTestSupport.key(overlay, Keys.DOWN);
        assertNotEquals(first, overlay.selectedIndex());
        assertFalse(overlay.entries().get(overlay.selectedIndex()).isHeader());
        int steps = 0;
        int previous = -1;
        while (overlay.selectedIndex() != previous && steps < 50) {
            previous = overlay.selectedIndex();
            ScreenTestSupport.key(overlay, Keys.DOWN);
            steps++;
        }
        assertEquals(overlay.entries().size() - 1, overlay.selectedIndex(), "clamps at the last result");
        ScreenTestSupport.key(overlay, Keys.UP);
        assertFalse(overlay.entries().get(overlay.selectedIndex()).isHeader());
        for (int i = 0; i < 60; i++) {
            ScreenTestSupport.key(overlay, Keys.UP);
        }
        assertEquals(first, overlay.selectedIndex(), "clamps at the first result");
    }

    @Test
    void enterOnASettingOpensTheSettingsScreenAtThatRow() {
        SearchOverlay overlay = t.show(ScreenId.SEARCH, 854, 480);
        ScreenTestSupport.type(overlay, "render distance");
        ScreenTestSupport.key(overlay, Keys.ENTER);
        GlobalSearchResult activated = overlay.lastActivated().orElseThrow();
        assertInstanceOf(GlobalSearchResult.SettingResult.class, activated);
        assertTrue(t.host.events().contains("openScreen:SETTINGS"));
        assertEquals("video.renderDistance", t.navigator.takePendingSetting().orElseThrow());
        assertFalse(overlay.isClosing(), "the host replaces the screen; the overlay does not close itself");
    }

    @Test
    void screenActionsOpenScreensAndCommandsRunAndClose() {
        SearchOverlay overlay = t.show(ScreenId.SEARCH, 854, 480);
        overlay.activate(overlay.results().get(2));
        assertTrue(t.host.events().contains("openScreen:PERFORMANCE"));

        SearchOverlay second = t.show(ScreenId.SEARCH, 854, 480);
        second.field().setText("toggle hud");
        GlobalSearchResult command = second.results().stream()
                .filter(r -> r instanceof GlobalSearchResult.ActionResult a && a.action().id().equals("toggle_hud"))
                .findFirst().orElseThrow();
        assertTrue(t.services.settings().get(VantaSettings.HUD_ENABLED));
        second.activate(command);
        assertFalse(t.services.settings().get(VantaSettings.HUD_ENABLED));
        assertTrue(second.isClosing(), "commands close the palette");
    }

    @Test
    void keybindResultsOpenTheKeybindsScreenWithTheMappingStaged() {
        SearchOverlay overlay = t.show(ScreenId.SEARCH, 854, 480);
        overlay.field().setText("sprint");
        GlobalSearchResult binding = overlay.results().stream()
                .filter(r -> r instanceof GlobalSearchResult.KeybindResult).findFirst().orElseThrow();
        overlay.activate(binding);
        assertTrue(t.host.events().contains("openScreen:KEYBINDS"));
        assertEquals("key.sprint", t.navigator.takePendingKeybind().orElseThrow());
    }

    @Test
    void escapeClosesEvenWithTextAndClickActivatesRows() {
        t.services.settings().set(VantaSettings.ACCESSIBILITY_REDUCED_MOTION, true);
        SearchOverlay overlay = t.show(ScreenId.SEARCH, 854, 480);
        ScreenTestSupport.type(overlay, "fps");
        assertTrue(ScreenTestSupport.key(overlay, Keys.ESCAPE));
        assertTrue(overlay.isClosing());
        assertTrue(t.host.closed());

        SearchOverlay again = t.show(ScreenId.SEARCH, 854, 480);
        t.frame(again, -1000, -1000);
        var list = again.root().findById("search.results");
        int rowY = list.bounds().y() + SearchOverlay.HEADER_H + SearchOverlay.ROW_H + SearchOverlay.ROW_H / 2;
        again.mouseDown(list.bounds().centerX(), rowY, Keys.MOUSE_LEFT);
        assertEquals(again.results().get(1), again.lastActivated().orElseThrow(), "second row clicked");
    }

    @Test
    void noResultsStateAndPanelFitSmallScreens() {
        SearchOverlay overlay = t.show(ScreenId.SEARCH, 427, 240);
        overlay.field().setText("qwertyuiop");
        assertTrue(overlay.results().isEmpty());
        TestCanvas canvas = t.frame(overlay, -1000, -1000);
        assertTrue(canvas.hasTextContaining("No results"));
        overlay.field().setText("a");
        t.frame(overlay, -1000, -1000);
        assertTrue(overlay.panelRect().bottom() <= 240 - 8, "panel stays inside the screen");
        assertTrue(overlay.panelRect().w() <= 427 - 16);
    }

    @Test
    void flattenKeepsGroupOrder() {
        SearchOverlay overlay = t.show(ScreenId.SEARCH, 854, 480);
        List<GlobalSearchResult> mixed = t.services.search().search("open", 20);
        List<SearchOverlay.Entry> entries = SearchOverlay.flatten(mixed);
        SearchOverlay.Group last = null;
        for (SearchOverlay.Entry e : entries) {
            if (e.isHeader()) {
                assertTrue(last == null || e.group().ordinal() > last.ordinal(), "groups in enum order");
                last = e.group();
            } else {
                assertEquals(last, e.group());
            }
        }
        assertFalse(overlay.entries().isEmpty());
    }
}
