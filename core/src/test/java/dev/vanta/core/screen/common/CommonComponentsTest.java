package dev.vanta.core.screen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.Screens1;
import dev.vanta.core.screen.about.AboutScreen;
import dev.vanta.core.screen.accessibility.AccessibilityScreen;
import dev.vanta.core.screen.menu.MainMenuScreen;
import dev.vanta.core.screen.packs.ResourcePackScreen;
import dev.vanta.core.screen.perf.PerformanceScreen;
import dev.vanta.core.screen.search.SearchOverlay;
import dev.vanta.core.screen.settings.SettingsScreen;
import dev.vanta.core.search.MatchRange;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiTestSupport;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Spacer;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Slider;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommonComponentsTest {
    @TempDir
    Path dir;

    @Test
    void screens1RegistersEveryScreenWithTheRightClass() {
        ScreenTestSupport t = ScreenTestSupport.create(dir);
        assertTrue(t.services.screens().registered().containsAll(Screens1.SCREENS));
        assertInstance(t, ScreenId.MAIN_MENU, MainMenuScreen.class);
        assertInstance(t, ScreenId.SETTINGS, SettingsScreen.class);
        assertInstance(t, ScreenId.SEARCH, SearchOverlay.class);
        assertInstance(t, ScreenId.PERFORMANCE, PerformanceScreen.class);
        assertInstance(t, ScreenId.ACCESSIBILITY, AccessibilityScreen.class);
        assertInstance(t, ScreenId.RESOURCE_PACKS, ResourcePackScreen.class);
        assertInstance(t, ScreenId.ABOUT, AboutScreen.class);
        for (ScreenId id : Screens1.SCREENS) {
            VantaUiScreen screen = t.create(id);
            assertEquals(id, screen.screenId());
            assertFalse(screen.title().startsWith("vanta."), "title translated for " + id);
        }
    }

    private static void assertInstance(ScreenTestSupport t, ScreenId id, Class<?> type) {
        assertTrue(type.isInstance(t.services.screens().create(id).orElseThrow()), id + " -> " + type.getSimpleName());
    }

    @Test
    void navigatorIsSharedPerServicesAndStagesTargetsOnce() {
        ScreenTestSupport t = ScreenTestSupport.create(dir);
        assertSame(ScreenNavigator.forServices(t.services), ScreenNavigator.forServices(t.services));
        ScreenNavigator nav = t.navigator;
        nav.stageCategory(SettingCategory.AUDIO);
        assertTrue(nav.hasPendingSettingsTarget());
        assertEquals(SettingCategory.AUDIO, nav.takePendingCategory().orElseThrow());
        assertTrue(nav.takePendingSetting().isEmpty());
        assertFalse(nav.hasPendingSettingsTarget());
        nav.stageSetting(VantaSettings.ZOOM_FACTOR);
        assertEquals(SettingCategory.CONTROLS, nav.takePendingCategory().orElseThrow());
        assertEquals("zoom.factor", nav.takePendingSetting().orElseThrow());
    }

    @Test
    void actionDispatcherResolvesScreensAndConfirmsDestructiveCommands() {
        assertEquals(ScreenId.HUD_EDITOR, ActionDispatcher.screenOf("open_hud_editor").orElseThrow());
        assertEquals(ScreenId.CROSSHAIR, ActionDispatcher.screenOf("open_crosshair").orElseThrow());
        assertTrue(ActionDispatcher.screenOf("toggle_hud").isEmpty());
        assertTrue(ActionDispatcher.isDestructive("reset_settings"));
        assertTrue(ActionDispatcher.isDestructive("clear_statistics"));
        assertFalse(ActionDispatcher.isDestructive("toggle_hud"));
    }

    @Test
    void themeFactoryAppliesAccessibilityAndCosmetics() {
        ScreenTestSupport t = ScreenTestSupport.create(dir);
        Theme base = ThemeFactory.themeFor(t.services);
        assertEquals(1f, base.scale(), 1e-6f);
        t.services.settings().set(VantaSettings.GENERAL_UI_SCALE, 1.25);
        t.services.settings().set(VantaSettings.ACCESSIBILITY_LARGE_TEXT, true);
        t.services.settings().set(VantaSettings.ACCESSIBILITY_REDUCED_TRANSPARENCY, true);
        Theme next = ThemeFactory.themeFor(t.services);
        assertEquals(1.25f, next.scale(), 1e-6f);
        assertTrue(next.largeText());
        assertEquals(1f, next.panelAlpha(), 1e-6f);
        assertFalse(next.wantsBlur());
        assertTrue(t.services.cosmetics().selectTheme("midnight"));
        assertEquals("midnight", ThemeFactory.themeFor(t.services).id());
    }

    @Test
    void highlightedTextClipsMergesAndDrawsRanges() {
        List<MatchRange> ranges = List.of(new MatchRange(4, 8), new MatchRange(0, 3), new MatchRange(2, 5),
                new MatchRange(20, 30));
        List<MatchRange> clipped = HighlightedText.clip(ranges, 10);
        assertEquals(List.of(new MatchRange(0, 8)), clipped);
        TestCanvas canvas = new TestCanvas();
        int width = HighlightedText.draw(canvas, "Render distance", List.of(new MatchRange(0, 6)), 0, 0, 200,
                FontKind.UI_BOLD, 0xFFFFFFFF, 0xFF00FF00);
        assertEquals(TestCanvas.metrics().textWidth("Render distance", FontKind.UI_BOLD), width);
        assertTrue(canvas.texts().stream().anyMatch(x -> x.text().equals("Render") && x.argb() == 0xFF00FF00));
        assertTrue(canvas.texts().stream().anyMatch(x -> x.text().equals(" distance") && x.argb() == 0xFFFFFFFF));
        assertFalse(canvas.fills().isEmpty(), "underline");
        assertEquals(0, HighlightedText.draw(canvas, "", ranges, 0, 0, 100, FontKind.UI, 0, 0));
    }

    @Test
    void sparklineRangeIncludesZeroAndTheReference() {
        Sparkline line = new Sparkline().setValues(new int[] {60, 90, 120});
        assertEquals(0f, line.rangeMin(), 1e-6f);
        assertTrue(line.rangeMax() > 120f);
        line.reference(144f);
        assertTrue(line.rangeMax() > 144f);
        line.baselineAtZero(false);
        assertTrue(line.rangeMin() < 60f && line.rangeMin() > 0f);
        UiTestSupport ui = new UiTestSupport();
        ui.place(line, 0, 0, 96, 26);
        TestCanvas canvas = ui.render(line);
        assertTrue(canvas.fills().size() > 96, "one column per pixel plus frame");
        Sparkline empty = new Sparkline();
        assertEquals(0f, empty.rangeMin(), 1e-6f);
        assertEquals(1f, empty.rangeMax(), 1e-6f);
    }

    @Test
    void responsiveGridPicksColumnsFromTheWidth() {
        ResponsiveGrid grid = new ResponsiveGrid(168, 3, 8);
        assertEquals(1, grid.columnsFor(200));
        assertEquals(2, grid.columnsFor(360));
        assertEquals(3, grid.columnsFor(600));
        assertEquals(3, grid.columnsFor(2000), "capped");
        for (int i = 0; i < 5; i++) {
            grid.add(Spacer.fixed(10, 20));
        }
        UiTestSupport ui = new UiTestSupport();
        ui.place(grid, 0, 0, 600, 100);
        assertEquals(3, grid.columns());
        assertEquals(grid.children().get(0).bounds().y(), grid.children().get(2).bounds().y());
        assertTrue(grid.children().get(3).bounds().y() > grid.children().get(0).bounds().y());
        assertEquals(48, grid.preferredSize(ui.ctx()).h(), "two rows of 20 plus the gap");
        ui.place(grid, 0, 0, 200, 100);
        assertEquals(1, grid.columns());
        assertEquals(5 * 20 + 4 * 8, grid.preferredSize(ui.ctx()).h());
    }

    @Test
    void settingRowCompactModeStacksSliders() {
        ScreenTestSupport t = ScreenTestSupport.create(dir);
        SettingRow slider = new SettingRow(VantaSettings.GENERAL_UI_SCALE, t.services.settings(), id -> { });
        SettingRow toggle = new SettingRow(VantaSettings.HUD_ENABLED, t.services.settings(), id -> { });
        assertEquals(SettingRow.HEIGHT, slider.rowHeight());
        slider.compact(true);
        toggle.compact(true);
        assertTrue(slider.isStacked());
        assertFalse(toggle.isStacked());
        assertEquals(SettingRow.HEIGHT_STACKED, slider.rowHeight());
        assertEquals(SettingRow.HEIGHT_COMPACT, toggle.rowHeight());
        UiTestSupport ui = new UiTestSupport();
        Column column = new Column(0);
        column.add(slider);
        column.add(toggle);
        ui.place(column, 0, 0, 200, 100);
        assertEquals(SettingRow.HEIGHT_STACKED, slider.bounds().h());
        assertTrue(slider.editor() instanceof Slider<?>);
        assertTrue(slider.editor().bounds().w() > 150, "slider spans the row in stacked mode");
        assertTrue(slider.editor().bounds().y() > slider.bounds().y() + 10, "below the title");
        TestCanvas canvas = ui.render(column);
        assertTrue(canvas.hasText("Interface scale"));
        assertFalse(canvas.hasTextContaining("Size multiplier"), "description hidden in compact mode");
        assertEquals("Increase the text size in VANTA screens by 20 %.",
                new SettingRow(VantaSettings.ACCESSIBILITY_LARGE_TEXT, t.services.settings(), null).description());
    }

    /**
     * A fresh banner in a column measures at the width the column offers, so in the first pass it is already as
     * tall as its wrapped body plus the stacked action, also below the 240 px it used to assume as a minimum (the
     * Mods list column in a 320x240 window is 124 px wide).
     */
    @Test
    void infoBannerTakesItsRealHeightInTheFirstPassOfANarrowColumn() {
        UiTestSupport ui = new UiTestSupport();
        Column col = new Column(4);
        InfoBanner banner = col.add(new InfoBanner(InfoBanner.Tone.INFO, "Title", "word ".repeat(40).trim()));
        Button action = Button.primary("Act", () -> { });
        banner.action(action);
        Button next = col.add(new Button("Next", () -> { }));
        assertEquals(240, banner.preferredSize(ui.ctx()).w(), "nothing known yet: the old 240 px assumption");
        ui.place(col, 0, 0, 140, 400);
        // 106 px of text (four 20 px words per line, ten lines), the title, the stacked action and the padding.
        int textBottom = banner.bounds().y() + Theme.SPACE_4 + 9 + Theme.SPACE_1 + 10 * 9;
        assertEquals(16 + 101 + Theme.SPACE_3 + 20, banner.bounds().h());
        assertEquals(140, banner.bounds().w());
        assertTrue(action.bounds().y() >= textBottom, "the stacked action sits below the body text");
        assertEquals(banner.bounds().bottom() + 4, next.bounds().y());
        ui.place(col, 0, 0, 140, 400);
        assertEquals(143, banner.bounds().h(), "a second pass changes nothing");
        assertTrue(banner.preferredSize(ui.ctx(), 400).h() < 143, "a wider offer wraps to fewer lines");
    }
}
