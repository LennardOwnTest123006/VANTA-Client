package dev.vanta.core.crosshair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The colour names, the vanilla switch and the preset setting value shared by the customizer and Vanta Nexus. */
class CrosshairHelpersTest {
    @Test
    void colourNamesAndHex() {
        assertEquals(List.of("white", "red", "green", "blue", "yellow", "cyan", "magenta", "black", "orange", "purple",
                "pink", "gray"), CrosshairColors.names());
        assertEquals(Optional.of(0xFFFF0000), CrosshairColors.parse("Red"));
        assertEquals(Optional.of(0xFF00FF00), CrosshairColors.parse(" green "), "bright green (CSS lime)");
        assertEquals(Optional.of(0xFF808080), CrosshairColors.parse("grey"));
        assertEquals(Optional.of(0xFF123456), CrosshairColors.parse("#123456"));
        assertEquals(Optional.of(0xFF123456), CrosshairColors.parse("#80123456"), "made opaque like the customizer");
        assertEquals(Optional.empty(), CrosshairColors.parse("sparkly"));
        assertEquals(Optional.empty(), CrosshairColors.parse(null));
        assertEquals("red", CrosshairColors.describe(0xFFFF0000));
        assertEquals("#7C5CFF", CrosshairColors.describe(0xFF7C5CFF));
        for (String name : CrosshairColors.names()) {
            assertEquals(name, CrosshairColors.describe(CrosshairColors.parse(name).orElseThrow()));
        }
    }

    @Test
    void theVanillaSwitchTogglesTheCrosshairWidget() {
        HudLayout layout = HudPresets.defaultPreset().layout();
        assertFalse(CrosshairWidget.isVanilla(layout));
        HudLayout vanilla = CrosshairWidget.withVanilla(layout, true);
        assertTrue(CrosshairWidget.isVanilla(vanilla));
        assertSame(vanilla, CrosshairWidget.withVanilla(vanilla, true), "no change, same layout");
        assertEquals(layout, CrosshairWidget.withVanilla(vanilla, false));

        HudLayout none = layout.without("crosshair");
        assertTrue(CrosshairWidget.isVanilla(none), "no crosshair widget: vanilla draws it");
        assertSame(none, CrosshairWidget.withVanilla(none, true));
        HudLayout added = CrosshairWidget.withVanilla(none, false);
        HudWidgetState crosshair = added.byType(HudWidgetType.CROSSHAIR).get(0);
        assertTrue(crosshair.enabled());
        assertFalse(CrosshairWidget.isVanilla(added));
    }

    @Test
    void thePresetSettingValueNamesTheMatchingPreset() {
        assertEquals("default", CrosshairPresets.settingValueFor(CrosshairStyle.DEFAULT));
        assertEquals("circle", CrosshairPresets.settingValueFor(CrosshairPresets.find("circle").orElseThrow().style()));
        assertEquals(CrosshairPresets.CUSTOM, CrosshairPresets.settingValueFor(
                CrosshairStyle.DEFAULT.withShape(CrosshairShape.SQUARE)));
    }
}
