package dev.vanta.core.screen.accessibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.accessibility.ColorBlindPalette;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.common.ScreenTestSupport;
import dev.vanta.core.screen.common.SettingRow;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.widget.Select;
import dev.vanta.core.ui.widget.Slider;
import dev.vanta.core.ui.widget.Toggle;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccessibilityScreenTest {
    @TempDir
    Path dir;
    private ScreenTestSupport t;

    @BeforeEach
    void setUp() {
        t = ScreenTestSupport.create(dir);
    }

    private static SettingRow row(AccessibilityScreen screen, String id) {
        return screen.rows().stream().filter(r -> r.setting().id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void changesRethemeTheScreenImmediately() {
        AccessibilityScreen screen = t.show(ScreenId.ACCESSIBILITY, 854, 480);
        assertEquals(9, screen.rows().size(), "4 display + 1 motion + 1 colours + 3 vanilla");
        assertFalse(screen.theme().isHighContrast());
        Toggle highContrast = (Toggle) row(screen, "accessibility.highContrast").editor();
        ScreenTestSupport.click(screen, highContrast);
        assertTrue(screen.theme().isHighContrast(), "theme swapped without leaving the screen");

        Toggle motion = (Toggle) row(screen, "accessibility.reducedMotion").editor();
        ScreenTestSupport.click(screen, motion);
        assertTrue(screen.theme().reducedMotion());
        assertTrue(screen.context().animator().isReducedMotion());

        @SuppressWarnings("unchecked")
        Slider<Double> scale = (Slider<Double>) row(screen, "general.uiScale").editor();
        scale.change(1.5);
        assertEquals(1.5f, screen.effectiveScale(), 1e-6f);
        assertEquals(Math.round(854 / 1.5f), screen.width(), "logical width shrinks with the scale");
    }

    @Test
    void palettePreviewFollowsTheColourBlindSetting() {
        AccessibilityScreen screen = t.show(ScreenId.ACCESSIBILITY, 854, 480);
        int before = screen.theme().success();
        @SuppressWarnings("unchecked")
        Select<Object> palette = (Select<Object>) row(screen, "accessibility.colorBlindPalette").editor();
        palette.select(screen.context(), palette.options().indexOf(ColorBlindPalette.TRITANOPIA));
        assertEquals(ColorBlindPalette.TRITANOPIA, t.services.settings().get(VantaSettings.ACCESSIBILITY_COLOR_BLIND_PALETTE));
        assertEquals(ColorBlindPalette.TRITANOPIA.success(), screen.theme().success());
        assertTrue(before != screen.theme().success());
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        var preview = screen.palettePreview().bounds();
        assertTrue(canvas.fills().stream().anyMatch(f -> f.argb() == ColorBlindPalette.TRITANOPIA.success()
                && f.y() >= preview.y() && f.bottom() <= preview.bottom()), "preview swatch uses the palette colour");
        assertTrue(canvas.hasText("Success") && canvas.hasText("Warning") && canvas.hasText("Danger"));
    }

    @Test
    void listsKeyboardShortcutsAndLinksToTheVanillaScreen() {
        AccessibilityScreen screen = t.show(ScreenId.ACCESSIBILITY, 854, 480);
        TestCanvas canvas = t.frame(screen, -1000, -1000);
        assertTrue(canvas.hasText("Keyboard navigation"));
        assertTrue(canvas.hasText("Increase the text size in VANTA screens by 20 %."), "%% collapsed");
        var link = screen.root().findById("accessibility.openVanilla");
        screen.context().focus().focus(screen.context(), link);
        ScreenTestSupport.key(screen, dev.vanta.core.ui.Keys.ENTER);
        assertEquals("openVanillaScreen:ACCESSIBILITY", t.game.actions.get(t.game.actions.size() - 1));
        Toggle vanillaContrast = (Toggle) row(screen, "accessibility.vanillaHighContrast").editor();
        vanillaContrast.toggle(screen.context());
        assertEquals(true, t.options.get(dev.vanta.core.bridge.VanillaOption.HIGH_CONTRAST).orElseThrow());
    }
}
