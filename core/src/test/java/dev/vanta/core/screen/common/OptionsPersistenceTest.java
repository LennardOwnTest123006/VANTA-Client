package dev.vanta.core.screen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.settings.SettingsScreen;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.widget.Select;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The player's graphics choice survives every VANTA screen: opening, ticking and closing the main menu, Settings
 * (Video) and the Performance Center, and a second start of the services, write no vanilla option and save no
 * {@code options.txt}. Only a VANTA control the player uses changes an option.
 */
class OptionsPersistenceTest {
    @TempDir
    Path dir;

    private static void openTickClose(ScreenTestSupport t, UiScreen screen) {
        t.show(screen, 960, 540);
        for (int i = 0; i < 60; i++) {
            screen.tick();
        }
        t.frame(screen, -1000, -1000);
        screen.onClose();
    }

    private static void visitEveryScreen(ScreenTestSupport t) {
        openTickClose(t, t.create(ScreenId.MAIN_MENU));
        SettingsScreen settings = t.create(ScreenId.SETTINGS);
        t.show(settings, 960, 540);
        settings.selectCategory(SettingCategory.VIDEO);
        for (int i = 0; i < 60; i++) {
            settings.tick();
        }
        t.frame(settings, -1000, -1000);
        settings.onClose();
        openTickClose(t, t.create(ScreenId.PERFORMANCE));
        t.services.saveAll();
    }

    @Test
    void fastSurvivesEveryVantaScreenAndARestart() {
        ScreenTestSupport t = ScreenTestSupport.create(dir);
        t.options.graphicsPresetBundle = true;
        t.options.setFromGame(VanillaOption.GRAPHICS_MODE, "FAST"); // picked in vanilla Video Settings

        visitEveryScreen(t);
        assertTrue(t.options.setOrder.isEmpty(), "VANTA wrote vanilla options: " + t.options.setOrder);
        assertEquals(0, t.options.saveCount, "VANTA saved options.txt without a player action");
        assertEquals("FAST", t.options.values().get(VanillaOption.GRAPHICS_MODE));

        // A second start on the same files (what a restart does once options.txt was read back).
        VantaServices again = VantaServices.create(t.services.paths(), t.game, t.options, t.keybinds, t.packs,
                t.clock);
        again.load();
        again.saveAll();
        assertTrue(t.options.setOrder.isEmpty(), "start-up wrote vanilla options: " + t.options.setOrder);
        assertEquals("FAST", again.settings().get(VantaSettings.VIDEO_GRAPHICS_MODE));
    }

    @Test
    void customSurvivesEveryVantaScreenAndIsShownAsCustom() {
        ScreenTestSupport t = ScreenTestSupport.create(dir);
        t.options.graphicsPresetBundle = true;
        t.options.setFromGame(VanillaOption.GRAPHICS_MODE, "FAST");
        t.options.setFromGame(VanillaOption.CLOUDS, "OFF"); // e.g. Sodium Apply on one quality option

        visitEveryScreen(t);
        assertTrue(t.options.setOrder.isEmpty(), "VANTA wrote vanilla options: " + t.options.setOrder);
        assertEquals(0, t.options.saveCount);
        assertEquals("CUSTOM", t.options.values().get(VanillaOption.GRAPHICS_MODE));
        assertEquals("OFF", t.options.values().get(VanillaOption.CLOUDS));

        SettingsScreen settings = t.create(ScreenId.SETTINGS);
        t.show(settings, 960, 540);
        settings.selectCategory(SettingCategory.VIDEO);
        SettingRow row = settings.rowFor(VantaSettings.VIDEO_GRAPHICS_MODE.id()).orElseThrow();
        assertEquals("CUSTOM", ((Select<?>) row.editor()).value());
        settings.onClose();
    }

    @Test
    void choosingFastInTheVantaRowPersistsThroughClosingTheScreen() {
        ScreenTestSupport t = ScreenTestSupport.create(dir);
        t.options.graphicsPresetBundle = true;
        SettingsScreen settings = t.create(ScreenId.SETTINGS);
        t.show(settings, 960, 540);
        settings.selectCategory(SettingCategory.VIDEO);
        SettingRow row = settings.rowFor(VantaSettings.VIDEO_GRAPHICS_MODE.id()).orElseThrow();
        @SuppressWarnings("unchecked")
        Select<Object> select = (Select<Object>) row.editor();
        select.select(settings.context(), select.options().indexOf("FAST"));
        assertEquals("FAST", t.options.values().get(VanillaOption.GRAPHICS_MODE));
        settings.onClose(); // Escape / Done
        assertEquals(1, t.options.saveCount, "options.txt written once when the screen closes");
        int writes = t.options.setOrder.size();

        visitEveryScreen(t);
        assertEquals(writes, t.options.setOrder.size(), "nothing written after the choice");
        assertEquals("FAST", t.options.values().get(VanillaOption.GRAPHICS_MODE));
    }
}
