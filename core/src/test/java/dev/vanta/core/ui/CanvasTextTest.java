package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;

class CanvasTextTest {

    private final TextMetrics metrics = TestCanvas.metrics();

    @Test
    void wrapsOnWordBoundaries() {
        assertEquals(List.of("The quick", "brown fox"), CanvasText.wrap("The quick brown fox", 50, FontKind.UI, metrics));
        assertEquals(List.of("The quick brown fox"), CanvasText.wrap("The quick brown fox", 500, FontKind.UI, metrics));
    }

    @Test
    void splitsWordsLongerThanTheWidth() {
        assertEquals(List.of("abcdefghij", "klmnop"), CanvasText.wrap("abcdefghijklmnop", 50, FontKind.UI, metrics));
        assertEquals(List.of("ab", "abcdefghij", "kl"), CanvasText.wrap("ab abcdefghijkl", 50, FontKind.UI, metrics));
    }

    @Test
    void honoursExplicitNewlinesAndEmptyInput() {
        assertEquals(List.of("a", "", "b"), CanvasText.wrap("a\n\nb", 100, FontKind.UI, metrics));
        assertEquals(List.of(""), CanvasText.wrap("", 100, FontKind.UI, metrics));
        assertEquals(List.of(""), CanvasText.wrap(null, 100, FontKind.UI, metrics));
    }

    @Test
    void ellipsizesToFit() {
        assertEquals("abcdef", CanvasText.ellipsize("abcdef", 30, FontKind.UI, metrics));
        assertEquals("abc…", CanvasText.ellipsize("abcdefgh", 20, FontKind.UI, metrics));
        assertEquals("aaaa…", CanvasText.ellipsize("aaaa bbbb", 30, FontKind.UI, metrics), "trailing space trimmed");
        assertEquals("…", CanvasText.ellipsize("abcdef", 5, FontKind.UI, metrics));
        assertEquals("", CanvasText.ellipsize("abcdef", 4, FontKind.UI, metrics));
        assertEquals("", CanvasText.ellipsize(null, 40, FontKind.UI, metrics));
    }

    @Test
    void blockMetrics() {
        List<String> lines = List.of("ab", "abcd");
        assertEquals(20, CanvasText.maxLineWidth(lines, FontKind.UI, metrics));
        assertEquals(18, CanvasText.blockHeight(2, FontKind.UI, metrics));
        assertEquals(28, CanvasText.blockHeight(2, FontKind.DISPLAY, metrics));
    }
}
