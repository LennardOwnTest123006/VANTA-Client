package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IconsTest {

    @Test
    void everyIconDrawsInsideItsBoxAtCommonSizes() {
        for (Icons icon : Icons.values()) {
            for (int size : new int[] {8, 10, 12, 16, 24}) {
                TestCanvas c = new TestCanvas();
                icon.draw(c, 100, 100, size, 0xFFFFFFFF);
                assertFalse(c.fills().isEmpty(), icon + " draws nothing at " + size);
                Rect box = new Rect(99, 99, size + 2, size + 2);
                assertTrue(box.contains(c.fillBounds()), icon + " at " + size + " overflows: " + c.fillBounds());
            }
        }
    }

    @Test
    void iconsAreVisuallyDistinct() {
        java.util.Set<String> signatures = new java.util.HashSet<>();
        for (Icons icon : Icons.values()) {
            TestCanvas c = new TestCanvas();
            icon.draw(c, 0, 0, 16, 0xFFFFFFFF);
            StringBuilder sig = new StringBuilder();
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    sig.append(c.isFilled(x, y) ? '#' : '.');
                }
            }
            assertTrue(signatures.add(sig.toString()), icon + " renders identically to another icon");
        }
        assertEquals(Icons.values().length, signatures.size());
    }

    @Test
    void transparentOrEmptyDrawsAreSkipped() {
        TestCanvas c = new TestCanvas();
        Icons.CLOSE.draw(c, 0, 0, 10, 0x00FFFFFF);
        Icons.CLOSE.draw(c, 0, 0, 0, 0xFFFFFFFF);
        assertTrue(c.fills().isEmpty());
    }

    @Test
    void chevronsPointTheRightWay() {
        TestCanvas left = new TestCanvas();
        Icons.CHEVRON_LEFT.draw(left, 0, 0, 16, 0xFFFFFFFF);
        TestCanvas right = new TestCanvas();
        Icons.CHEVRON_RIGHT.draw(right, 0, 0, 16, 0xFFFFFFFF);
        assertTrue(left.fillBounds().x() < right.fillBounds().x() || left.isFilled(5, 8));
        assertTrue(right.isFilled(11, 8));
        assertTrue(left.isFilled(5, 8));
    }
}
