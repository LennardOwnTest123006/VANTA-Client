package dev.vanta.core.screen.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.screen.common.SettingRow;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.widget.Select;
import dev.vanta.core.ui.widget.Slider;
import dev.vanta.core.ui.widget.Toggle;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsScreenTest {
    @TempDir
    Path dir;
    private ScreenTestSupport t;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
    }

    @Test
    void showsTheSelectedCategoryAndSwitchesThroughTheRail() {
        SettingsScreen screen = t.show(ScreenId.SETTINGS, 854, 480);
        assertEquals(SettingCategory.GENERAL, screen.category());
        assertEquals(t.services.settingsRegistry().byCategory(SettingCategory.GENERAL).size(),
                screen.visibleRows().size());
        screen.shell().selectRail(screen.context(), "video");
        assertEquals(SettingCategory.VIDEO, screen.category());
        assertTrue(screen.rowFor("video.renderDistance").isPresent());
        assertEquals("video", screen.shell().selectedRail());
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasText("Render distance"));
        assertTrue(canvas.hasTextContaining("Reset Video"));
    }

    @Test
    void searchFiltersAcrossCategoriesAndGroupsResults() {
        SettingsScreen screen = t.show(ScreenId.SETTINGS, 854, 480);
        screen.searchField().requestFocus(screen.context());
        ScreenTestSupport.type(screen, "render distance");
        assertEquals("render distance", screen.query());
        assertTrue(screen.rowFor("video.renderDistance").isPresent());
        assertTrue(screen.rowFor("performance.dynamicRenderDistanceSuggestions").isPresent(),
                "results come from every category");
        assertFalse(screen.rowFor("menu.customMainMenu").isPresent());
        assertFalse(screen.resetCategoryButton().isVisible(), "no category to reset while filtering");
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasText("Search results"));
        assertTrue(canvas.hasText("Video") && canvas.hasText("Performance"), "section headers");

        screen.searchField().setText("zzzz-nothing");
        assertTrue(screen.visibleRows().isEmpty());
        assertTrue(t.frame(screen, -1000, -1000).hasTextContaining("No settings match"));

        screen.searchField().setText("");
        assertEquals(SettingCategory.GENERAL, screen.category());
        assertFalse(screen.visibleRows().isEmpty());
        assertTrue(screen.resetCategoryButton().isVisible());
    }

    @Test
    void vanillaBoundRowsWriteThroughTheOptionsBridgeImmediately() {
        SettingsScreen screen = t.show(ScreenId.SETTINGS, 854, 480);
        screen.selectCategory(SettingCategory.VIDEO);
        t.frame(screen, -1000, -1000);
        SettingRow vsync = screen.rowFor("video.vsync").orElseThrow();
        Toggle toggle = (Toggle) vsync.editor();
        assertTrue(toggle.isOn());
        ScreenTestSupport.click(screen, toggle);
        assertFalse(toggle.isOn());
        assertEquals(false, t.options.get(VanillaOption.VSYNC).orElseThrow());

        SettingRow render = screen.rowFor("video.renderDistance").orElseThrow();
        @SuppressWarnings("unchecked")
        Slider<Integer> slider = (Slider<Integer>) render.editor();
        slider.change(20);
        assertEquals(20, t.options.getInt(VanillaOption.RENDER_DISTANCE, -1));
        assertFalse(render.isDefault());
        assertTrue(render.resetButton().isVisible());
        render.resetToDefault();
        assertEquals(12, t.options.getInt(VanillaOption.RENDER_DISTANCE, -1));
    }

    @Test
    void resetCategoryRestoresDefaultsAndNotifies() {
        SettingsScreen screen = t.show(ScreenId.SETTINGS, 854, 480);
        t.services.settings().set(VantaSettings.GENERAL_UI_SOUNDS, false);
        t.services.settings().set(VantaSettings.MENU_SHOW_VERSION_LABEL, false);
        Toggle sounds = (Toggle) screen.rowFor("general.uiSounds").orElseThrow().editor();
        assertFalse(sounds.isOn(), "external changes refresh the rows");
        assertEquals(2, screen.modifiedCount(SettingCategory.GENERAL));
        ScreenTestSupport.click(screen, screen.resetCategoryButton());
        assertTrue(t.services.settings().get(VantaSettings.GENERAL_UI_SOUNDS));
        assertTrue(sounds.isOn());
        assertEquals(0, screen.modifiedCount(SettingCategory.GENERAL));
        assertFalse(t.services.notifications().visible().isEmpty(), "reset posts a toast");
    }

    @Test
    void resetAllConfirmsBeforeTouchingAnything() {
        SettingsScreen screen = t.show(ScreenId.SETTINGS, 854, 480);
        t.services.settings().set(VantaSettings.HUD_ENABLED, false);
        t.options.set(VanillaOption.RENDER_DISTANCE, 24);
        ScreenTestSupport.click(screen, screen.resetAllButton());
        assertNotNull(ScreenTestSupport.openDialog(screen));
        assertFalse(t.services.settings().get(VantaSettings.HUD_ENABLED));
        ScreenTestSupport.confirmDialog(screen);
        assertTrue(t.services.settings().get(VantaSettings.HUD_ENABLED));
        assertEquals(12, t.options.getInt(VanillaOption.RENDER_DISTANCE, -1));
    }

    @Test
    void deepLinkRevealsFlashesAndFocusesTheRow() {
        t.navigator.stageSetting(VantaSettings.HUD_GLOBAL_SCALE);
        SettingsScreen screen = t.show(ScreenId.SETTINGS, 854, 480);
        assertEquals(SettingCategory.HUD, screen.category());
        SettingRow row = screen.rowFor("hud.globalScale").orElseThrow();
        assertTrue(row.isFlashing(screen.context()));
        assertSame(row.editor(), screen.context().focus().focused());
        assertFalse(t.navigator.hasPendingSettingsTarget(), "target consumed");

        assertTrue(screen.reveal("video.fov"));
        assertEquals(SettingCategory.VIDEO, screen.category());
        assertSame(screen.rowFor("video.fov").orElseThrow().editor(), screen.context().focus().focused());
        assertFalse(screen.reveal("does.notExist"));
    }

    @Test
    void controlFFocusesTheSearchFieldAndEscapeClosesAndSaves() {
        t.services.settings().set(VantaSettings.ACCESSIBILITY_REDUCED_MOTION, true);
        SettingsScreen screen = t.show(ScreenId.SETTINGS, 854, 480);
        assertTrue(ScreenTestSupport.key(screen, Keys.F, Keys.MOD_CONTROL));
        assertSame(screen.searchField(), screen.context().focus().focused());
        t.services.settings().set(VantaSettings.HUD_SNAP, false);
        screen.context().focus().clear(screen.context());
        assertTrue(ScreenTestSupport.key(screen, Keys.ESCAPE));
        assertTrue(screen.isClosing());
        assertTrue(t.host.closed());
        screen.onClose();
        assertTrue(Files.exists(t.services.paths().settingsFile()), "settings saved on close");
    }

    @Test
    void categoriesCarryTheirDeepLinkRows() {
        SettingsScreen screen = t.show(ScreenId.SETTINGS, 854, 480);
        screen.selectCategory(SettingCategory.HUD);
        t.frame(screen, -1000, -1000);
        assertNotNull(screen.root().findById("link.crosshair"));
        ScreenTestSupport.click(screen, screen.root().findById("link.crosshair"));
        assertTrue(t.host.events().contains("openScreen:CROSSHAIR"));
        SettingRow editor = screen.rowFor("hud.openEditor").orElseThrow();
        ScreenTestSupport.click(screen, editor.editor());
        assertTrue(t.host.events().contains("openScreen:HUD_EDITOR"));

        screen.selectCategory(SettingCategory.CONTROLS);
        t.frame(screen, -1000, -1000);
        assertTrue(screen.scrollPanel().isScrollable(), "controls has more rows than fit");
        screen.scrollPanel().setScrollY(screen.context(), screen.scrollPanel().maxScroll(), false);
        t.frame(screen, -1000, -1000);
        ScreenTestSupport.click(screen, screen.root().findById("link.vanilla_controls"));
        assertEquals("openVanillaScreen:CONTROLS", t.game.actions.get(t.game.actions.size() - 1));

        screen.selectCategory(SettingCategory.LANGUAGE);
        t.frame(screen, -1000, -1000);
        ScreenTestSupport.click(screen, screen.rowFor("language.openVanilla").orElseThrow().editor());
        assertEquals("openVanillaScreen:LANGUAGE", t.game.actions.get(t.game.actions.size() - 1));
        ScreenTestSupport.click(screen, screen.openVanillaButton());
        assertEquals("openVanillaScreen:OPTIONS", t.game.actions.get(t.game.actions.size() - 1));
    }

    @Test
    void fitsSmallWindows() {
        SettingsScreen screen = t.show(ScreenId.SETTINGS, 427, 240);
        assertTrue(screen.shell().isCompactRail());
        assertTrue(screen.scrollPanel().isScrollable());
        assertTrue(screen.resetAllButton().bounds().bottom() <= 240 - 16, "footer above the version line");
        assertTrue(screen.scrollPanel().bounds().h() > 60);
    }

    /** Picking a frame-rate limit in the Performance category writes the vanilla limit and VSync right away. */
    @Test
    void fpsLimitPresetRowWritesTheVanillaOptions() {
        SettingsScreen screen = t.show(ScreenId.SETTINGS, 854, 480);
        screen.selectCategory(SettingCategory.PERFORMANCE);
        t.frame(screen, -1000, -1000);
        SettingRow row = screen.rowFor("performance.fpsLimitPreset").orElseThrow();
        @SuppressWarnings("unchecked")
        Select<Object> select = (Select<Object>) row.editor();
        assertEquals(FpsLimitPreset.UNLIMITED, select.value());
        assertEquals(120, t.options.getInt(VanillaOption.FRAMERATE_LIMIT, -1), "vanilla default before");
        assertTrue(t.options.getBoolean(VanillaOption.VSYNC, false));
        int saves = t.options.saveCount;

        select.select(screen.context(), select.options().indexOf(FpsLimitPreset.FPS_144));
        assertEquals(FpsLimitPreset.FPS_144, t.services.settings().get(VantaSettings.PERFORMANCE_FPS_LIMIT_PRESET));
        assertEquals(140, t.options.getInt(VanillaOption.FRAMERATE_LIMIT, -1), "144 FPS is vanilla's 140 step");
        assertFalse(t.options.getBoolean(VanillaOption.VSYNC, true));
        assertEquals(saves + 1, t.options.saveCount, "options.txt saved once");
        assertFalse(row.isDefault());

        screen.selectCategory(SettingCategory.VIDEO);
        t.frame(screen, -1000, -1000);
        Toggle vsync = (Toggle) screen.rowFor("video.vsync").orElseThrow().editor();
        assertFalse(vsync.isOn(), "the Video rows show what the preset wrote");
    }
}
