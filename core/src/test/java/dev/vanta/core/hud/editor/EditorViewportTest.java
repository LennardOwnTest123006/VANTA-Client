package dev.vanta.core.hud.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.hud.HudRect;
import dev.vanta.core.ui.Rect;
import org.junit.jupiter.api.Test;

class EditorViewportTest {

    @Test
    void fitsExactlyWhenTheFreeAreaIsLargeEnough() {
        EditorViewport vp = EditorViewport.fit(new Rect(0, 0, 900, 500), 854, 480);
        assertTrue(vp.isExact());
        assertEquals(1f, vp.scale());
        assertEquals(new Rect(23, 10, 854, 480), vp.area());
        assertEquals(100, vp.toHostX(123));
        assertEquals(50, vp.toHostY(60));
        assertEquals(new Rect(23 + 10, 10 + 20, 44, 14), vp.toLogical(new HudRect(10, 20, 44, 14)));
    }

    @Test
    void scalesDownKeepingTheAspectRatioAndCentres() {
        EditorViewport vp = EditorViewport.fit(new Rect(100, 50, 400, 400), 800, 400);
        assertFalse(vp.isExact());
        assertEquals(0.5f, vp.scale(), 1e-6);
        assertEquals(new Rect(100, 50 + 100, 400, 200), vp.area());
        assertEquals(400, vp.toHostX(300));
        assertEquals(200, vp.toHostY(250));
        assertEquals(300, vp.toLogicalX(400));
        Rect logical = vp.toLogical(new HudRect(0, 0, 1, 1));
        assertEquals(1, logical.w(), "tiny widgets stay at least one pixel");
        assertTrue(vp.contains(300, 200));
        assertFalse(vp.contains(50, 50));
        assertTrue(vp.containsHost(0, 0));
        assertFalse(vp.containsHost(800, 0));
    }

    @Test
    void neverUpscalesAndRoundTripsCoordinates() {
        EditorViewport vp = EditorViewport.fit(new Rect(0, 0, 2000, 2000), 427, 240);
        assertEquals(1f, vp.scale());
        for (int host = 0; host < 427; host += 7) {
            assertEquals(host, vp.toHostX(vp.toLogicalX(host)));
        }
        EditorViewport small = EditorViewport.fit(new Rect(0, 0, 200, 100), 854, 480);
        for (int host = 0; host < 854; host += 50) {
            int back = small.toHostX(small.toLogicalX(host));
            assertTrue(Math.abs(back - host) <= 1 / small.scale() + 1, "lossy by at most one logical pixel");
        }
    }
}
