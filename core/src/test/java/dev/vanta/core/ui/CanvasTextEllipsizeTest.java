package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * {@link CanvasText#ellipsize} must return exactly what the original linear scan returned (kept here as
 * {@link #linearEllipsize}) for every string and width, while measuring only {@code O(log n)} prefixes.
 */
class CanvasTextEllipsizeTest {

    /** Monotone metrics with irregular per-code-point advances (some zero-width, spaces narrow). */
    private static final class FakeMetrics implements TextMetrics {
        int measurements;

        @Override
        public int textWidth(String text, FontKind font) {
            measurements++;
            if (text == null) {
                return 0;
            }
            int width = 0;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                width += advance(cp);
                i += Character.charCount(cp);
            }
            return width;
        }

        private static int advance(int cp) {
            if (cp == ' ') {
                return 2;
            }
            if (cp == 0x200B) {
                return 0; // zero width space
            }
            if (Character.isSupplementaryCodePoint(cp)) {
                return 11;
            }
            return 3 + (cp * 7) % 6;
        }
    }

    /** The original implementation: remove one code point at a time from the end and re-measure. */
    private static String linearEllipsize(String text, int maxWidth, FontKind font, TextMetrics metrics) {
        if (text == null) {
            return "";
        }
        if (metrics.textWidth(text, font) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = metrics.textWidth(CanvasText.ELLIPSIS, font);
        if (ellipsisWidth > maxWidth) {
            return "";
        }
        int end = text.length();
        while (end > 0) {
            end = text.offsetByCodePoints(end, -1);
            String prefix = text.substring(0, end).stripTrailing();
            if (metrics.textWidth(prefix, font) + ellipsisWidth <= maxWidth) {
                return prefix + CanvasText.ELLIPSIS;
            }
        }
        return CanvasText.ELLIPSIS;
    }

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFXYZ0123456789 ,.-_:;!?";

    private static String randomString(Random random, int codePoints) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codePoints; i++) {
            int roll = random.nextInt(100);
            if (roll < 4) {
                sb.appendCodePoint(0x1F600 + random.nextInt(60)); // emoji: surrogate pairs
            } else if (roll < 6) {
                sb.append('​');
            } else if (roll < 8) {
                sb.append(CanvasText.ELLIPSIS);
            } else if (roll < 22) {
                sb.append(' ');
            } else {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return sb.toString();
    }

    @Test
    void binarySearchMatchesTheLinearScanForManyStringsAndWidths() {
        Random random = new Random(20261006L);
        FakeMetrics metrics = new FakeMetrics();
        int cases = 0;
        int worstMeasurements = 0;
        for (int round = 0; round < 2_500; round++) {
            String text = randomString(random, random.nextInt(120));
            int full = metrics.textWidth(text, FontKind.UI);
            int[] widths = {-3, 0, 1, 2, 3, 4, 5, random.nextInt(Math.max(1, full + 1)),
                    random.nextInt(Math.max(1, full + 1)), full - 1, full, full + 7};
            for (int maxWidth : widths) {
                String expected = linearEllipsize(text, maxWidth, FontKind.UI, metrics);
                metrics.measurements = 0;
                String actual = CanvasText.ellipsize(text, maxWidth, FontKind.UI, metrics);
                assertEquals(expected, actual, () -> "text=" + text + " maxWidth=" + maxWidth);
                worstMeasurements = Math.max(worstMeasurements, metrics.measurements);
                cases++;
            }
        }
        assertTrue(cases > 20_000);
        // Full text + ellipsis + one per halving step of up to 120 code points (log2(120) < 7).
        assertTrue(worstMeasurements <= 2 + 7, "measurements per clip: " + worstMeasurements);
    }

    @Test
    void fixedWidthExamples() {
        TextMetrics five = TextMetrics.fixed(5);
        assertEquals("Hello world", CanvasText.ellipsize("Hello world", 55, FontKind.UI, five));
        assertEquals("Hello…", CanvasText.ellipsize("Hello world", 34, FontKind.UI, five),
                "trailing space before the cut is stripped");
        assertEquals("Hello w…", CanvasText.ellipsize("Hello world", 40, FontKind.UI, five));
        assertEquals("…", CanvasText.ellipsize("Hello world", 5, FontKind.UI, five));
        assertEquals("", CanvasText.ellipsize("Hello world", 4, FontKind.UI, five), "no room for the ellipsis");
        assertEquals("", CanvasText.ellipsize(null, 100, FontKind.UI, five));
        assertEquals("", CanvasText.ellipsize("", 100, FontKind.UI, five));
    }
}
