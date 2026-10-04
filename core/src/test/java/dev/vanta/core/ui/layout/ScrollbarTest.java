package dev.vanta.core.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import org.junit.jupiter.api.Test;

class ScrollbarTest {

    @Test
    void clampAndMax() {
        assertEquals(900f, Scrollbar.maxScroll(100, 1000), 1e-6f);
        assertEquals(0f, Scrollbar.maxScroll(100, 50), 1e-6f);
        assertEquals(900f, Scrollbar.clamp(5000f, 100, 1000), 1e-6f);
        assertEquals(0f, Scrollbar.clamp(-5f, 100, 1000), 1e-6f);
        assertTrue(Scrollbar.needed(100, 101));
        assertFalse(Scrollbar.needed(100, 100));
        assertEquals(Scrollbar.WIDTH + Scrollbar.GUTTER, Scrollbar.reservedWidth());
    }

    @Test
    void thumbGeometryAndInverseMapping() {
        Rect track = Scrollbar.track(new Rect(0, 0, 100, 100));
        assertEquals(new Rect(100 - Scrollbar.WIDTH, 0, Scrollbar.WIDTH, 100), track);
        Rect top = Scrollbar.thumb(track, 1000, 0f);
        assertEquals(Scrollbar.MIN_THUMB, top.h(), "thumb never smaller than the minimum");
        assertEquals(0, top.y());
        Rect bottom = Scrollbar.thumb(track, 1000, 900f);
        assertEquals(100 - Scrollbar.MIN_THUMB, bottom.y());
        Rect half = Scrollbar.thumb(track, 200, 50f);
        assertEquals(50, half.h());
        assertEquals(25, half.y());
        assertEquals(50f, Scrollbar.scrollForThumbTop(track, 200, 25), 1e-6f);
        assertEquals(900f, Scrollbar.scrollForThumbTop(track, 1000, 500), 1e-6f, "clamped to the end");
        assertEquals(0f, Scrollbar.scrollForThumbTop(track, 1000, -50), 1e-6f);
        assertEquals(track, Scrollbar.thumb(track, 50, 0f), "no overflow: thumb spans the track");
        assertEquals(0f, Scrollbar.scrollForThumbTop(track, 50, 10), 1e-6f);
    }

    @Test
    void renderDrawsTrackAndThumbOnlyWhenNeeded() {
        TestCanvas c = new TestCanvas();
        Rect track = Scrollbar.track(new Rect(0, 0, 100, 100));
        Scrollbar.render(c, Theme.DEFAULT, track, 50, 0f, false);
        assertTrue(c.fills().isEmpty());
        Scrollbar.render(c, Theme.DEFAULT, track, 1000, 0f, true);
        assertFalse(c.fills().isEmpty());
        assertTrue(c.fills().stream().anyMatch(f -> f.argb() == Theme.DEFAULT.scrollThumbActive()));
    }
}
