package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.UiTestSupport;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ColorFieldTest {

    private final UiTestSupport t = new UiTestSupport();
    private final List<Integer> changes = new ArrayList<>();

    @Test
    void hexFieldDrivesTheColor() {
        ColorField f = t.place(new ColorField(0xFF7C5CFF, false, changes::add), 0, 0, 120, 20);
        assertEquals("#7C5CFF", f.hexField().text());
        f.hexField().setText("#4F8DFF");
        assertEquals(0xFF4F8DFF, f.color());
        assertEquals(List.of(0xFF4F8DFF), changes);
        f.hexField().setText("#12G");
        assertFalse(f.hexField().isValid());
        assertEquals(0xFF4F8DFF, f.color(), "invalid input leaves the color alone");
        f.hexField().setText("#80FFFFFF");
        assertFalse(f.hexField().isValid(), "alpha digits rejected when alpha is not allowed");
        assertEquals(1, changes.size());
        f.setColor(0xFF112233);
        assertEquals("#112233", f.hexField().text());
        assertEquals(1, changes.size(), "setColor is silent");
        f.setColor(0x80112233);
        assertEquals(0xFF112233, f.color(), "alpha stripped when not allowed");
    }

    @Test
    void alphaAllowed() {
        ColorField f = t.place(new ColorField(0x993DDC97, true, changes::add), 0, 0, 120, 20);
        assertEquals("#993DDC97", f.hexField().text());
        f.hexField().setText("#80FFFFFF");
        assertEquals(0x80FFFFFF, f.color());
        assertTrue(f.allowsAlpha());
        assertEquals(new Rect(0, 2, ColorField.SWATCH, ColorField.SWATCH), f.swatchRect());
        assertEquals(ColorField.SWATCH + 6, f.hexField().bounds().x());
        TestCanvas c = t.render(f);
        assertTrue(c.fills().stream().anyMatch(fill -> fill.argb() == 0xFF2A2A36), "checkerboard behind translucent swatch");
    }

    @Test
    void palettePopupPicksColors() {
        Column root = new Column().align(Align.START);
        ColorField f = root.add(new ColorField(0xFF7C5CFF, false, changes::add));
        f.size(120, 20);
        UiScreen screen = t.screen(root, 400, 300);
        Rect swatch = f.swatchRect();
        UiTestSupport.click(screen, swatch.centerX(), swatch.centerY());
        assertTrue(f.isPopupOpen());
        assertTrue(screen.keyDown(Keys.RIGHT, 0, 0), "highlight starts on the current swatch (violet, index 4)");
        assertTrue(screen.keyDown(Keys.ENTER, 0, 0));
        assertEquals(ColorField.PALETTE.get(5), f.color());
        assertEquals(List.of(ColorField.PALETTE.get(5)), changes);
        assertFalse(f.isPopupOpen(), "opaque pick closes the popup");
        assertEquals(Colors.toHex(ColorField.PALETTE.get(5)), f.hexField().text());
        UiTestSupport.click(screen, swatch.centerX(), swatch.centerY());
        assertTrue(f.isPopupOpen());
        UiTestSupport.click(screen, swatch.centerX(), swatch.centerY());
        assertFalse(f.isPopupOpen(), "clicking the swatch again closes");
        f.openPopup(screen.context());
        screen.mouseDown(390, 290, Keys.MOUSE_LEFT);
        assertFalse(f.isPopupOpen());
    }

    @Test
    void alphaPopupKeepsOpenAndHasSlider() {
        Column root = new Column().align(Align.START);
        ColorField f = root.add(new ColorField(0x80FF0000, true, changes::add));
        f.size(120, 20);
        UiScreen screen = t.screen(root, 400, 300);
        f.openPopup(screen.context());
        assertTrue(f.isPopupOpen());
        assertTrue(screen.keyDown(Keys.ENTER, 0, 0));
        assertTrue(f.isPopupOpen(), "stays open so the alpha can be adjusted");
        assertEquals(0x80, Colors.alpha(f.color()), "alpha preserved when picking a swatch");
        assertEquals(ColorField.PALETTE.get(0) & 0xFFFFFF, f.color() & 0xFFFFFF);
        assertEquals(12, ColorField.PALETTE.size());
    }
}
