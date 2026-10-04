package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AbstractCanvasTest {

    @Test
    void scissorsIntersectInDeviceSpace() {
        TestCanvas c = new TestCanvas();
        assertNull(c.deviceScissor());
        c.pushTranslate(10, 5);
        c.pushScissor(new Rect(0, 0, 10, 10));
        assertEquals(new Rect(10, 5, 10, 10), c.deviceScissor());
        c.pushScissor(new Rect(5, 5, 20, 20));
        assertEquals(new Rect(15, 10, 5, 5), c.deviceScissor());
        c.popScissor();
        assertEquals(new Rect(10, 5, 10, 10), c.deviceScissor());
        c.popScissor();
        assertNull(c.deviceScissor());
        c.pop();
        assertEquals(0, c.transformDepth());
        assertEquals(0, c.scissorDepth());
    }

    @Test
    void scaleAffectsScissorConversion() {
        TestCanvas c = new TestCanvas();
        c.pushTranslate(10, 5);
        c.pushScale(2, 2);
        c.pushScissor(new Rect(0, 0, 10, 10));
        assertEquals(new Rect(10, 5, 20, 20), c.deviceScissor());
        assertTrue(c.isVisible(new Rect(0, 0, 1, 1)));
        assertFalse(c.isVisible(new Rect(50, 50, 1, 1)));
        AbstractCanvas.Transform t = c.transform();
        assertEquals(0.0, t.inverseX(10), 1e-9);
        assertEquals(5.0, t.inverseX(20), 1e-9);
        c.popScissor();
        c.pop();
        c.pop();
    }

    @Test
    void disjointScissorYieldsEmptyClip() {
        TestCanvas c = new TestCanvas();
        c.pushScissor(new Rect(0, 0, 10, 10));
        c.pushScissor(new Rect(50, 50, 10, 10));
        assertTrue(c.deviceScissor().isEmpty());
        assertFalse(c.isVisible(new Rect(0, 0, 100, 100)));
    }

    @Test
    void partialAlphaMultipliesColors() {
        TestCanvas c = new TestCanvas();
        c.setPartialAlpha(0.5f);
        c.fill(0, 0, 1, 1, 0xFF000000);
        assertEquals(0x80000000, c.fills().get(0).argb());
        c.text("x", 0, 0, 0xFF000000, FontKind.UI, false);
        assertEquals(0x80000000, c.texts().get(0).argb());
        c.setPartialAlpha(0f);
        c.fill(0, 0, 1, 1, 0xFF000000);
        assertEquals(1, c.fills().size(), "fully transparent draws are skipped");
        c.setPartialAlpha(5f);
        assertEquals(1f, c.partialAlpha(), 1e-6f);
    }

    @Test
    void skipsEmptyAndInvisibleFills() {
        TestCanvas c = new TestCanvas();
        c.fill(0, 0, 0, 10, 0xFFFFFFFF);
        c.fill(0, 0, 10, -1, 0xFFFFFFFF);
        c.fill(0, 0, 10, 10, 0x00FFFFFF);
        assertTrue(c.fills().isEmpty());
    }

    @Test
    void stackUnderflowsThrow() {
        TestCanvas c = new TestCanvas();
        assertThrows(IllegalStateException.class, c::pop);
        assertThrows(IllegalStateException.class, c::popScissor);
    }

    @Test
    void transformComposition() {
        AbstractCanvas.Transform t = AbstractCanvas.Transform.IDENTITY.scale(2, 2).translate(5, 5);
        assertEquals(new Rect(10, 10, 20, 20), t.apply(new Rect(0, 0, 10, 10)));
        AbstractCanvas.Transform u = AbstractCanvas.Transform.IDENTITY.translate(5, 5).scale(2, 2);
        assertEquals(new Rect(5, 5, 20, 20), u.apply(new Rect(0, 0, 10, 10)));
    }
}
