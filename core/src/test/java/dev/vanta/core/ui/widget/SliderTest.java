package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.UiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class SliderTest {

    private final UiTestSupport t = new UiTestSupport();

    private Slider<Integer> percent() {
        return t.place(Slider.ofInt(0, 100, 5, 35).formatter(v -> v + "%"), 0, 0, 200, 20);
    }

    @Test
    void valuePositionMappingIsInvertible() {
        Slider<Integer> s = percent();
        Rect track = s.trackRect();
        assertEquals(new Rect(4, 9, 154, 3), track);
        assertEquals(track.x(), s.valueToX(0));
        assertEquals(track.right(), s.valueToX(100));
        assertEquals(track.x() + 77, s.valueToX(50));
        assertEquals(50.0, s.xToValue(track.x() + 77), 1e-9);
        assertEquals(0.0, s.xToValue(-100), 1e-9);
        assertEquals(100.0, s.xToValue(1000), 1e-9);
        for (int v = 0; v <= 100; v += 5) {
            assertEquals(v, s.xToValue(s.valueToX(v)), 1e-9, "round trip " + v);
        }
        assertEquals(0.35f, s.fraction(), 1e-6f);
    }

    @Test
    void snapsToStepsAndClamps() {
        Slider<Integer> s = percent();
        assertEquals(35.0, s.snap(37), 1e-9);
        assertEquals(40.0, s.snap(38), 1e-9);
        assertEquals(100.0, s.snap(250), 1e-9);
        assertEquals(0.0, s.snap(-3), 1e-9);
        Slider<Double> d = Slider.ofDouble(0, 1, 0.05, 0.6);
        assertEquals(0.6, d.snap(0.62), 1e-9);
        assertEquals(0.65, d.snap(0.63), 1e-9);
        assertEquals(0.3, d.snap(0.3), 1e-12, "no floating point drift");
        assertEquals("0.60", d.formattedValue());
        assertEquals(0.6, d.value(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Slider.ofInt(5, 5, 1, 5));
        assertThrows(IllegalArgumentException.class, () -> Slider.ofInt(0, 5, 0, 5));
    }

    @Test
    void keyboardSteps() {
        List<Integer> changes = new ArrayList<>();
        Slider<Integer> s = percent();
        s.onChange(changes::add);
        s.requestFocus(t.ctx());
        assertTrue(t.key(Keys.RIGHT));
        assertEquals(40, s.value());
        assertTrue(t.key(Keys.RIGHT, Keys.MOD_SHIFT));
        assertEquals(90, s.value());
        assertTrue(t.key(Keys.LEFT));
        assertEquals(85, s.value());
        assertTrue(t.key(Keys.HOME));
        assertEquals(0, s.value());
        assertTrue(t.key(Keys.END));
        assertEquals(100, s.value());
        assertTrue(t.key(Keys.RIGHT));
        assertEquals(100, s.value());
        assertEquals(List.of(40, 90, 85, 0, 100), changes, "no event when clamped at the end");
        assertFalse(t.key(Keys.UP), "vertical arrows are left to focus navigation");
    }

    @Test
    void clickToSetAndDrag() {
        List<Integer> changes = new ArrayList<>();
        Slider<Integer> s = percent();
        s.onChange(changes::add);
        Rect track = s.trackRect();
        assertTrue(s.mouseDown(t.ctx(), track.x() + 77, 10, Keys.MOUSE_LEFT));
        assertEquals(50, s.value());
        assertTrue(s.isDragging());
        assertEquals(s, t.ctx().mouseCapture());
        s.mouseDrag(t.ctx(), track.right() + 50, 10, Keys.MOUSE_LEFT, 5, 0);
        assertEquals(100, s.value());
        s.mouseDrag(t.ctx(), track.x(), 10, Keys.MOUSE_LEFT, -5, 0);
        assertEquals(0, s.value());
        s.mouseUp(t.ctx(), track.x(), 10, Keys.MOUSE_LEFT);
        assertFalse(s.isDragging());
        assertEquals(List.of(50, 100, 0), changes);
        assertEquals(1, t.host.clicks(), "one click sound when the drag ends");
        s.setEnabled(false);
        assertFalse(s.mouseDown(t.ctx(), track.x() + 77, 10, Keys.MOUSE_LEFT));
        assertEquals(0, s.value());
    }

    @Test
    void rendersValueLabelAndKnob() {
        Slider<Integer> s = percent();
        TestCanvas c = t.render(s);
        assertTrue(c.hasText("35%"));
        int knobX = s.valueToX(35) - Slider.KNOB_W / 2;
        assertTrue(c.fills().stream().anyMatch(f -> f.x() == knobX || f.x() == knobX + 1 || f.x() == knobX + 2),
                "knob drawn at the value position");
        Slider<Integer> noLabel = t.place(Slider.ofInt(0, 10, 1, 5).valueWidth(0), 0, 0, 100, 20);
        assertTrue(t.render(noLabel).texts().isEmpty());
        assertEquals(new Rect(4, 9, 92, 3), noLabel.trackRect());
    }
}
