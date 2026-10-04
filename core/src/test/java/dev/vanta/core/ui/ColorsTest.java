package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColorsTest {

    @Test
    void packsAndUnpacksChannels() {
        int c = Colors.argb(0x80, 0x11, 0x22, 0x33);
        assertEquals(0x80112233, c);
        assertEquals(0x80, Colors.alpha(c));
        assertEquals(0x11, Colors.red(c));
        assertEquals(0x22, Colors.green(c));
        assertEquals(0x33, Colors.blue(c));
        assertEquals(0xFF112233, Colors.rgb(0x11, 0x22, 0x33));
        assertEquals(0xFFFF0000, Colors.argb(300, 999, -5, 0));
    }

    @Test
    void parsesHexVariants() {
        assertEquals(0xFF7C5CFF, Colors.fromHex("#7C5CFF"));
        assertEquals(0xFF7C5CFF, Colors.fromHex("7c5cff"));
        assertEquals(0x337C5CFF, Colors.fromHex("#337C5CFF"));
        assertEquals(0xFFFFFFFF, Colors.fromHex("#fff"));
        assertEquals(0xFF112233, Colors.fromHex("0x112233"));
        assertThrows(IllegalArgumentException.class, () -> Colors.fromHex("#12G"));
        assertThrows(IllegalArgumentException.class, () -> Colors.fromHex("#1234567"));
        assertThrows(IllegalArgumentException.class, () -> Colors.fromHex(null));
        assertEquals(42, Colors.fromHexOr("nope", 42));
        assertTrue(Colors.isValidHex("#ABCDEF"));
        assertFalse(Colors.isValidHex(""));
    }

    @Test
    void parsesCssHexWithTrailingAlpha() {
        assertEquals(0x337C5CFF, Colors.fromCssHex("#7C5CFF33"));
        assertEquals(0xFF7C5CFF, Colors.fromCssHex("#7C5CFF"));
        assertEquals(0xFFFFFFFF, Colors.fromCssHex("#fff"));
        assertThrows(IllegalArgumentException.class, () -> Colors.fromCssHex("#12"));
    }

    @Test
    void formatsHex() {
        assertEquals("#7C5CFF", Colors.toHex(0xFF7C5CFF));
        assertEquals("#337C5CFF", Colors.toHex(0x337C5CFF));
        assertEquals("#FF7C5CFF", Colors.toHexArgb(0xFF7C5CFF));
        assertEquals(0x337C5CFF, Colors.fromHex(Colors.toHex(0x337C5CFF)));
    }

    @Test
    void alphaHelpers() {
        assertEquals(0x80112233, Colors.withAlpha(0xFF112233, 0.5f));
        assertEquals(0x00112233, Colors.withAlpha(0xFF112233, -1f));
        assertEquals(0xFF112233, Colors.withAlpha(0x00112233, 2f));
        assertEquals(0x40112233, Colors.multiplyAlpha(0x80112233, 0.5f));
        assertEquals(0xFF112233, Colors.opaque(0x00112233));
        assertEquals(0.5f, Colors.alphaF(0x80000000), 0.01f);
    }

    @Test
    void lerpInterpolatesEveryChannel() {
        assertEquals(0x80808080, Colors.lerp(0x00000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFF000000, Colors.lerp(0xFF000000, 0xFFFFFFFF, -3f));
        assertEquals(0xFFFFFFFF, Colors.lerp(0xFF000000, 0xFFFFFFFF, 7f));
    }

    @Test
    void lightenDarkenKeepAlpha() {
        assertEquals(0x80FFFFFF, Colors.lighten(0x80000000, 1f));
        assertEquals(0x80000000, Colors.darken(0x80FFFFFF, 1f));
        assertEquals(0xFF808080, Colors.lighten(0xFF000000, 128f / 255f));
        assertEquals(0xFF404040, Colors.scaleRgb(0xFF808080, 0.5f));
    }

    @Test
    void shadowQuartersChannelsLikeMinecraft() {
        assertEquals(0xFF3F3F3F, Colors.shadow(0xFFFFFFFF));
        assertEquals(0x80000000, Colors.shadow(0x80000000));
    }

    @Test
    void overCompositesSourceOver() {
        assertEquals(0xFF808080, Colors.over(0xFF000000, 0x80FFFFFF));
        assertEquals(0xFF123456, Colors.over(0xFF123456, 0x00FFFFFF));
        assertEquals(Colors.TRANSPARENT, Colors.over(0x00000000, 0x00000000));
    }

    @Test
    void luminanceAndContrast() {
        assertTrue(Colors.luminance(0xFFFFFFFF) > Colors.luminance(0xFF808080));
        assertTrue(Colors.isDark(0xFF0B0B10));
        assertFalse(Colors.isDark(0xFFF5F5F7));
        assertEquals(21.0, Colors.contrastRatio(0xFF000000, 0xFFFFFFFF), 0.01);
        assertTrue(Colors.contrastRatio(Theme.DEFAULT.textPrimary(), Theme.DEFAULT.surface1()) > 12.0,
                "primary text on surface must be highly legible");
        assertTrue(Colors.contrastRatio(Theme.DEFAULT.textSecondary(), Theme.DEFAULT.surface1()) > 6.0);
    }
}
