package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ToggleTest {

    private final UiTestSupport t = new UiTestSupport();

    @Test
    void clickAndKeyboardToggle() {
        List<Boolean> changes = new ArrayList<>();
        Toggle toggle = t.place(new Toggle(false, changes::add), 0, 0, 40, 20);
        t.click(toggle);
        assertTrue(toggle.isOn());
        assertEquals(List.of(true), changes);
        toggle.requestFocus(t.ctx());
        assertTrue(t.key(Keys.SPACE));
        assertFalse(toggle.isOn());
        assertTrue(t.key(Keys.RIGHT));
        assertTrue(toggle.isOn());
        assertTrue(t.key(Keys.RIGHT));
        assertEquals(List.of(true, false, true), changes, "setting the same state is silent");
        assertTrue(t.key(Keys.LEFT));
        assertFalse(toggle.isOn());
        toggle.setOn(true);
        assertEquals(4, changes.size(), "setOn does not notify");
        toggle.setEnabled(false);
        t.click(toggle);
        assertTrue(toggle.isOn());
    }

    @Test
    void geometryAndLabel() {
        Toggle plain = t.place(new Toggle(true, null), 0, 0, 40, 20);
        assertEquals(new Rect(40 - Toggle.TRACK_W, 4, Toggle.TRACK_W, Toggle.TRACK_H), plain.trackRect());
        assertEquals(new Size(Toggle.TRACK_W, Button.HEIGHT), plain.preferredSize(t.ctx()));
        Toggle labelled = new Toggle("Show FPS", true, null);
        assertEquals(new Size(Toggle.TRACK_W + 40 + Theme.SPACE_4, Button.HEIGHT), labelled.preferredSize(t.ctx()));
        t.place(labelled, 0, 0, 120, 20);
        TestCanvas c = t.render(labelled);
        assertTrue(c.hasText("Show FPS"));
        assertTrue(c.fills().stream().anyMatch(f -> f.argb() == Theme.DEFAULT.gradientStart()
                || f.argb() == Theme.DEFAULT.gradientEnd()), "on state uses the accent gradient");
    }

    @Test
    void knobAnimatesBetweenEnds() {
        Toggle toggle = t.place(new Toggle(false, null), 0, 0, 40, 20);
        t.render(toggle);
        toggle.set(t.ctx(), true);
        TestCanvas start = t.render(toggle);
        t.clock.advance(Theme.MOTION_FAST);
        TestCanvas end = t.render(toggle);
        int knobStart = knobX(start);
        int knobEnd = knobX(end);
        assertTrue(knobEnd > knobStart, "knob moves right when turning on");
        assertEquals(toggle.trackRect().right() - 2 - 8, knobEnd);
    }

    private static int knobX(TestCanvas c) {
        return c.fills().stream().filter(f -> f.w() == 8 && f.h() == 1 || f.w() == 8 && f.h() == 8)
                .mapToInt(TestCanvas.Fill::x).max().orElse(-1);
    }
}
