package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VantaMarkTest {

    @Test
    void drawsAVWithNotchAndDiamondInsideTheBox() {
        TestCanvas c = new TestCanvas();
        VantaMark.draw(c, 0, 0, 64, Theme.DEFAULT);
        assertFalse(c.fills().isEmpty());
        assertTrue(new Rect(0, 0, 64, 64).contains(c.fillBounds()));
        // The arms start at SVG y=9 and end at y=58.
        assertFalse(c.isFilled(32, 4));
        assertTrue(c.isFilled(10, 12), "left arm");
        assertTrue(c.isFilled(53, 12), "right arm");
        assertFalse(c.isFilled(32, 20), "notch between the arms");
        assertFalse(c.isFilled(32, 40), "diamond cut-out");
        assertTrue(c.isFilled(32, 56), "tip of the V");
    }

    @Test
    void gradientRunsTopToBottom() {
        TestCanvas c = new TestCanvas();
        VantaMark.draw(c, 0, 0, 32, 0xFF000000, 0xFFFFFFFF);
        int firstRowColor = c.fills().get(0).argb();
        int lastRowColor = c.fills().get(c.fills().size() - 1).argb();
        assertTrue(Colors.red(firstRowColor) < Colors.red(lastRowColor));
    }

    @Test
    void smallSizesStillRender() {
        for (int size : new int[] {8, 12, 16}) {
            TestCanvas c = new TestCanvas();
            VantaMark.draw(c, 5, 5, size, 0xFFFFFFFF);
            assertFalse(c.fills().isEmpty(), "size " + size);
            assertTrue(new Rect(5, 5, size, size).contains(c.fillBounds()));
        }
        TestCanvas none = new TestCanvas();
        VantaMark.draw(none, 0, 0, 0, 0xFFFFFFFF);
        assertTrue(none.fills().isEmpty());
    }
}
