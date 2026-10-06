package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The cached rectangle replay of {@link Icons#draw} must cover exactly the pixels the stroke-by-stroke
 * {@link Icons#paint} covers, for every icon and the sizes the UI uses, while issuing far fewer fills overall.
 */
class IconRasterTest {
    private static final int[] SIZES = {9, 10, 11, 12, 14, 16, 20, 22};

    private static Set<Long> pixels(TestCanvas canvas) {
        Set<Long> pixels = new HashSet<>();
        for (TestCanvas.Fill fill : canvas.fills()) {
            for (int py = fill.y(); py < fill.bottom(); py++) {
                for (int px = fill.x(); px < fill.right(); px++) {
                    pixels.add(((long) px << 32) | (py & 0xFFFFFFFFL));
                }
            }
        }
        return pixels;
    }

    @Test
    void rectanglesCoverExactlyTheStrokePixels() {
        int comparisons = 0;
        long strokeFills = 0;
        long cachedFills = 0;
        for (Icons icon : Icons.values()) {
            for (int size : SIZES) {
                TestCanvas strokes = new TestCanvas(64, 64);
                icon.paint(strokes, 7, 5, size, 0xFF8040C0);
                TestCanvas cached = new TestCanvas(64, 64);
                icon.draw(cached, 7, 5, size, 0xFF8040C0);
                Set<Long> expected = pixels(strokes);
                assertEquals(expected, pixels(cached), icon + " at " + size + " px");
                assertFalse(cached.fills().isEmpty(), icon + " draws something at " + size + " px");
                long covered = 0;
                for (TestCanvas.Fill fill : cached.fills()) {
                    assertEquals(0xFF8040C0, fill.argb(), "the caller's colour is used");
                    covered += (long) fill.w() * fill.h();
                }
                assertEquals(expected.size(), covered, icon + " at " + size + " px: every pixel is drawn exactly once");
                // Strokes may overlap, rectangles never do, so a glyph made of a few long bars (a plus, a bin,
                // the sliders) can cost a few fills more; every curve or diagonal costs far fewer.
                assertTrue(cached.fills().size() <= 2 * strokes.fills().size() + 1,
                        icon + " at " + size + " px: " + cached.fills().size() + " vs " + strokes.fills().size());
                strokeFills += strokes.fills().size();
                cachedFills += cached.fills().size();
                comparisons++;
            }
        }
        assertEquals(Icons.values().length * SIZES.length, comparisons);
        // Measured 2026-10: 3 453 rectangles vs 5 762 stroke fills over every icon at these eight sizes (-40 %);
        // the saving grows with the icon size because stroke fills grow per pixel and rectangles per feature.
        assertTrue(cachedFills * 4 < strokeFills * 3, "cached " + cachedFills + " vs stroked " + strokeFills);
    }

    @Test
    void replayIsTranslationInvariantAndHonoursTheCanvasAlpha() {
        TestCanvas origin = new TestCanvas(64, 64);
        Icons.GEAR.draw(origin, 0, 0, 16, 0xFFFFFFFF);
        TestCanvas moved = new TestCanvas(64, 64);
        Icons.GEAR.draw(moved, 20, 30, 16, 0xFFFFFFFF);
        Set<Long> shifted = new HashSet<>();
        for (long p : pixels(origin)) {
            shifted.add((((p >> 32) + 20) << 32) | (((p & 0xFFFFFFFFL) + 30) & 0xFFFFFFFFL));
        }
        assertEquals(shifted, pixels(moved));

        TestCanvas faded = new TestCanvas(64, 64);
        faded.setPartialAlpha(0.5f);
        Icons.GEAR.draw(faded, 0, 0, 16, 0xFFFFFFFF);
        assertTrue(faded.fills().stream().allMatch(f -> (f.argb() >>> 24) < 0xFF), "partial alpha applied per fill");

        TestCanvas invisible = new TestCanvas(64, 64);
        Icons.GEAR.draw(invisible, 0, 0, 16, 0x00FFFFFF);
        assertTrue(invisible.fills().isEmpty(), "fully transparent icons are skipped");
    }
}
