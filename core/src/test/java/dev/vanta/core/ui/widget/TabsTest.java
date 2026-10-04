package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class TabsTest {

    private final UiTestSupport t = new UiTestSupport();

    @Test
    void clickAndKeyboardSelection() {
        List<Integer> selected = new ArrayList<>();
        Tabs tabs = t.place(new Tabs(List.of("General", "Video", "HUD"), 0).onSelect(selected::add), 0, 0, 300, 22);
        Rect video = tabs.tabRect(t.ctx(), 1);
        assertEquals(new Rect(5 * 7 + 20 + 2, 0, 5 * 5 + 20, 21), video);
        assertTrue(tabs.mouseDown(t.ctx(), video.centerX(), video.centerY(), Keys.MOUSE_LEFT));
        assertEquals(1, tabs.selected());
        assertTrue(t.key(Keys.RIGHT));
        assertEquals(2, tabs.selected());
        assertTrue(t.key(Keys.RIGHT));
        assertEquals(2, tabs.selected(), "clamped at the last tab");
        assertTrue(t.key(Keys.HOME));
        assertEquals(0, tabs.selected());
        assertTrue(t.key(Keys.END));
        assertTrue(t.key(Keys.LEFT));
        assertEquals(1, tabs.selected());
        assertEquals(List.of(1, 2, 0, 2, 1), selected);
        assertEquals(new Size((7 + 5 + 3) * 5 + 60 + 4, Tabs.HEIGHT), tabs.preferredSize(t.ctx()));
    }

    @Test
    void indicatorAnimatesToTheActiveTab() {
        Tabs tabs = t.place(new Tabs(List.of("A", "B"), 0), 0, 0, 100, 22);
        t.render(tabs);
        tabs.select(t.ctx(), 1);
        t.render(tabs);
        t.clock.advance(Theme.MOTION_BASE);
        TestCanvas c = t.render(tabs);
        Rect active = tabs.tabRect(t.ctx(), 1);
        assertTrue(c.fills().stream().anyMatch(f -> f.h() == 2 && f.y() == 20 && f.x() >= active.x()),
                "indicator under the active tab");
        assertTrue(c.hasText("A") && c.hasText("B"));
    }
}
