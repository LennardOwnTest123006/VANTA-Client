package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RectTest {

    @Test
    void containsUsesExclusiveRightAndBottomEdges() {
        Rect r = new Rect(10, 20, 30, 40);
        assertTrue(r.contains(10, 20));
        assertTrue(r.contains(39, 59));
        assertFalse(r.contains(40, 20));
        assertFalse(r.contains(10, 60));
        assertTrue(r.contains(39.9, 59.9));
        assertFalse(r.contains(40.0, 30.0));
    }

    @Test
    void intersectAndIntersects() {
        Rect a = new Rect(0, 0, 10, 10);
        Rect b = new Rect(5, 5, 10, 10);
        assertTrue(a.intersects(b));
        assertEquals(new Rect(5, 5, 5, 5), a.intersect(b));
        Rect far = new Rect(20, 20, 5, 5);
        assertFalse(a.intersects(far));
        assertTrue(a.intersect(far).isEmpty());
        assertFalse(a.intersects(new Rect(10, 0, 5, 5)), "touching edges do not intersect");
    }

    @Test
    void insetClampsAtZeroSize() {
        Rect r = new Rect(0, 0, 10, 10);
        assertEquals(new Rect(2, 2, 6, 6), r.inset(2));
        assertEquals(new Rect(8, 8, 0, 0), r.inset(8));
        assertEquals(new Rect(-3, -3, 16, 16), r.expand(3));
        assertEquals(new Rect(1, 2, 6, 4), r.inset(Insets.of(1, 2, 3, 4)));
    }

    @Test
    void unionAndCorners() {
        Rect a = new Rect(0, 0, 10, 10);
        Rect b = new Rect(20, 5, 5, 20);
        assertEquals(new Rect(0, 0, 25, 25), a.union(b));
        assertEquals(b, Rect.EMPTY.union(b));
        assertEquals(new Rect(2, 3, 8, 7), Rect.fromCorners(10, 10, 2, 3));
    }

    @Test
    void centeredAndStrips() {
        Rect outer = new Rect(0, 0, 100, 50);
        assertEquals(new Rect(40, 20, 20, 10), Rect.centered(outer, 20, 10));
        assertEquals(new Rect(0, 0, 100, 10), outer.top(10));
        assertEquals(new Rect(0, 40, 100, 10), outer.bottomStrip(10));
        assertEquals(new Rect(0, 0, 10, 50), outer.left(10));
        assertEquals(new Rect(90, 0, 10, 50), outer.rightStrip(10));
        assertEquals(50, outer.centerX());
        assertEquals(25, outer.centerY());
    }

    @Test
    void rejectsNegativeSizes() {
        assertThrows(IllegalArgumentException.class, () -> new Rect(0, 0, -1, 0));
    }

    @Test
    void scaleRoundsToPixels() {
        assertEquals(new Rect(2, 3, 5, 8), new Rect(1, 2, 3, 5).scale(1.5f));
    }

    @Test
    void insetsAndSizes() {
        Insets i = Insets.of(1, 2);
        assertEquals(4, i.horizontal());
        assertEquals(2, i.vertical());
        assertEquals(new Insets(3, 3, 3, 3), Insets.of(3));
        assertEquals(new Size(12, 7), new Size(10, 5).plus(Insets.of(1, 1)));
        assertEquals(new Size(10, 9), new Size(10, 5).max(new Size(3, 9)));
        assertEquals(new Size(0, 2), new Size(1, 1).plus(-5, 1));
    }
}
