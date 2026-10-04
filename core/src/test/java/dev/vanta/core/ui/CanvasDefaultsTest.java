package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

/** Geometry of the composed shapes on the recording canvas. */
class CanvasDefaultsTest {

    private static Set<Long> pixels(TestCanvas c) {
        Set<Long> set = new HashSet<>();
        for (TestCanvas.Fill f : c.fills()) {
            for (int y = f.y(); y < f.bottom(); y++) {
                for (int x = f.x(); x < f.right(); x++) {
                    set.add(((long) x << 32) | (y & 0xFFFFFFFFL));
                }
            }
        }
        return set;
    }

    private static boolean has(Set<Long> px, int x, int y) {
        return px.contains(((long) x << 32) | (y & 0xFFFFFFFFL));
    }

    @Test
    void fillRoundedRemovesCornerPixelsSymmetrically() {
        int w = 20;
        int h = 10;
        for (int r = 0; r <= 5; r++) {
            TestCanvas c = new TestCanvas();
            c.fillRounded(0, 0, w, h, r, 0xFFFFFFFF);
            Set<Long> px = pixels(c);
            int effective = Math.min(r, Canvas.MAX_RADIUS);
            int[] insets = RoundedCorners.insets(effective);
            int removed = 0;
            for (int inset : insets) {
                removed += inset;
            }
            assertEquals(w * h - 4 * removed, px.size(), "pixel count for radius " + r);
            assertTrue(has(px, w / 2, h / 2));
            for (int row = 0; row < insets.length; row++) {
                int inset = insets[row];
                for (int x = 0; x < w; x++) {
                    boolean expected = x >= inset && x < w - inset;
                    assertEquals(expected, has(px, x, row), "r=" + r + " top row " + row + " x=" + x);
                    assertEquals(expected, has(px, x, h - 1 - row), "r=" + r + " bottom row " + row + " x=" + x);
                }
            }
            if (effective > 0) {
                assertFalse(has(px, 0, 0));
                assertFalse(has(px, w - 1, h - 1));
            }
        }
    }

    @Test
    void fillRoundedClampsRadiusToHalfSize() {
        TestCanvas c = new TestCanvas();
        c.fillRounded(0, 0, 10, 2, 4, 0xFFFFFFFF);
        assertEquals(20 - 4, pixels(c).size(), "radius clamped to 1 for a 2 px tall bar");
        TestCanvas none = new TestCanvas();
        none.fillRounded(0, 0, 0, 5, 2, 0xFFFFFFFF);
        assertTrue(none.fills().isEmpty());
    }

    @Test
    void strokeRoundedOutlinesTheMatchingFill() {
        for (int r = 1; r <= 4; r++) {
            TestCanvas fill = new TestCanvas();
            fill.fillRounded(0, 0, 24, 12, r, 0xFFFFFFFF);
            Set<Long> inside = pixels(fill);
            TestCanvas stroke = new TestCanvas();
            stroke.strokeRounded(0, 0, 24, 12, r, 0xFFFFFFFF);
            Set<Long> outline = pixels(stroke);
            assertFalse(outline.isEmpty());
            for (long p : outline) {
                int x = (int) (p >> 32);
                int y = (int) p;
                assertTrue(inside.contains(p), "stroke pixel outside fill at " + x + "," + y + " r=" + r);
                boolean edge = !has(inside, x - 1, y) || !has(inside, x + 1, y) || !has(inside, x, y - 1)
                        || !has(inside, x, y + 1);
                assertTrue(edge, "stroke pixel not on the boundary at " + x + "," + y + " r=" + r);
            }
            // Every boundary pixel of the fill must be stroked.
            for (long p : inside) {
                int x = (int) (p >> 32);
                int y = (int) p;
                boolean edge = !has(inside, x - 1, y) || !has(inside, x + 1, y) || !has(inside, x, y - 1)
                        || !has(inside, x, y + 1);
                if (edge) {
                    assertTrue(outline.contains(p), "boundary pixel missing from stroke at " + x + "," + y + " r=" + r);
                }
            }
        }
    }

    @Test
    void roundedGradientMatchesRoundedFillShape() {
        TestCanvas fill = new TestCanvas();
        fill.fillRounded(0, 0, 30, 14, 3, 0xFFFFFFFF);
        TestCanvas gradient = new TestCanvas();
        gradient.fillRoundedGradientH(0, 0, 30, 14, 3, 0xFF000000, 0xFFFFFFFF);
        assertEquals(pixels(fill), pixels(gradient));
        assertEquals(0xFF000000, gradient.fills().get(0).argb());
        assertEquals(0xFFFFFFFF, gradient.fills().get(gradient.fills().size() - 1).argb());
    }

    @Test
    void gradientsEmitOneStripPerPixel() {
        TestCanvas c = new TestCanvas();
        c.fillGradientV(0, 0, 10, 5, 0xFF000000, 0xFFFFFFFF);
        assertEquals(5, c.fills().size());
        assertEquals(0xFF000000, c.fills().get(0).argb());
        assertEquals(0xFFFFFFFF, c.fills().get(4).argb());
        assertEquals(0xFF808080, c.fills().get(2).argb());
        c.clear();
        c.fillGradientH(0, 0, 3, 10, 0xFF000000, 0xFFFFFFFF);
        assertEquals(3, c.fills().size());
        assertEquals(new TestCanvas.Fill(1, 0, 1, 10, 0xFF808080), c.fills().get(1));
        c.clear();
        c.fillGradientH(0, 0, 3, 10, 0xFF112233, 0xFF112233);
        assertEquals(1, c.fills().size(), "flat gradient collapses to one fill");
    }

    @Test
    void strokeRectDrawsFourEdges() {
        TestCanvas c = new TestCanvas();
        c.strokeRect(0, 0, 10, 6, 1, 0xFFFFFFFF);
        Set<Long> px = pixels(c);
        assertEquals(10 * 6 - 8 * 4, px.size());
        assertTrue(has(px, 0, 0) && has(px, 9, 5) && has(px, 5, 0) && has(px, 0, 3));
        assertFalse(has(px, 5, 3));
        c.clear();
        c.strokeRect(0, 0, 10, 6, 2, 0xFFFFFFFF);
        assertEquals(10 * 6 - 6 * 2, pixels(c).size());
    }

    @Test
    void linesAndCircles() {
        TestCanvas c = new TestCanvas();
        c.line(2, 5, 8, 5, 0xFFFFFFFF);
        assertEquals(new TestCanvas.Fill(2, 5, 7, 1, 0xFFFFFFFF), c.fills().get(0));
        c.clear();
        c.line(3, 9, 3, 1, 0xFFFFFFFF);
        assertEquals(new TestCanvas.Fill(3, 1, 1, 9, 0xFFFFFFFF), c.fills().get(0));
        c.clear();
        c.line(0, 0, 4, 4, 0xFFFFFFFF);
        assertEquals(5, c.fills().size(), "diagonal steps once per pixel");
        c.clear();
        c.circle(10, 10, 3, 0xFFFFFFFF);
        Set<Long> px = pixels(c);
        assertTrue(has(px, 10, 10));
        assertTrue(has(px, 13, 10) && has(px, 7, 10) && has(px, 10, 13) && has(px, 10, 7));
        assertFalse(has(px, 13, 13));
        for (long p : px) {
            int x = (int) (p >> 32);
            int y = (int) p;
            assertTrue(has(px, 20 - x, y) && has(px, x, 20 - y), "circle symmetric");
        }
        c.clear();
        c.circle(5, 5, 0, 0xFFFFFFFF);
        assertEquals(1, pixels(c).size());
    }

    @Test
    void triangleCoversItsInterior() {
        TestCanvas c = new TestCanvas();
        c.triangle(0, 0, 10, 0, 0, 10, 0xFFFFFFFF);
        Set<Long> px = pixels(c);
        assertTrue(has(px, 1, 1));
        assertTrue(has(px, 2, 5));
        assertFalse(has(px, 9, 9));
    }

    @Test
    void textHelpersUseMetrics() {
        TestCanvas c = new TestCanvas();
        c.textCentered("abcd", 50, 0, 0xFFFFFFFF, FontKind.UI, false);
        assertEquals(40, c.texts().get(0).x());
        c.textRight("abcd", 50, 0, 0xFFFFFFFF, FontKind.UI, false);
        assertEquals(30, c.texts().get(1).x());
        assertEquals("abc…", c.textClipped("abcdefgh", 20, FontKind.UI));
        c.text("", 0, 0, 0xFFFFFFFF, FontKind.UI, false);
        assertEquals(2, c.texts().size(), "empty text is not drawn");
    }

    @Test
    void imageDefaultCoversWholeTexture() {
        TestCanvas c = new TestCanvas();
        c.image(TextureRef.VANTA_LOGO, 1, 2, 3, 4);
        assertEquals(new TestCanvas.Image(TextureRef.VANTA_LOGO, 1, 2, 3, 4), c.images().get(0));
        assertEquals("vanta:textures/gui/logo.png", TextureRef.VANTA_LOGO.toString());
        assertEquals(new TextureRef("minecraft", "x.png"), TextureRef.parse("x.png"));
        assertEquals(new TextureRef("vanta", "a/b.png"), TextureRef.parse("vanta:a/b.png"));
    }
}
