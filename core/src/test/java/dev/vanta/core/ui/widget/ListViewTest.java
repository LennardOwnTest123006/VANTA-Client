package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class ListViewTest {

    private final UiTestSupport t = new UiTestSupport();

    private static List<String> items(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add("Item " + i);
        }
        return out;
    }

    @Test
    void onlyVisibleRowsAreRendered() {
        ListView<String> list = t.place(new ListView<>(items(100), s -> s), 0, 0, 200, 90);
        assertEquals(0, list.firstVisibleRow());
        assertEquals(4, list.lastVisibleRow());
        TestCanvas c = t.render(list);
        assertEquals(5, c.texts().size(), "virtualised: five rows fit");
        assertTrue(c.hasText("Item 4"));
        assertFalse(c.hasText("Item 5"));
        assertEquals(1800, list.contentHeight());
        list.setScrollY(t.ctx(), 9f, false);
        assertEquals(0, list.firstVisibleRow());
        assertEquals(5, list.lastVisibleRow(), "partially visible rows count");
        assertEquals(6, t.render(list).texts().size());
    }

    @Test
    void selectionByKeyboardScrollsIntoView() {
        t.theme = Theme.DEFAULT.withReducedMotion(true);
        List<String> selected = new ArrayList<>();
        ListView<String> list = t.place(new ListView<>(items(100), s -> s).onSelect(selected::add), 0, 0, 200, 90);
        list.requestFocus(t.ctx());
        assertTrue(t.key(Keys.DOWN));
        assertEquals(0, list.selectedIndex());
        assertEquals(Optional.of("Item 0"), list.selectedItem());
        assertTrue(t.key(Keys.END));
        assertEquals(99, list.selectedIndex());
        assertEquals(95, list.firstVisibleRow(), "last row revealed");
        assertTrue(t.key(Keys.PAGE_UP));
        assertEquals(94, list.selectedIndex());
        assertTrue(t.key(Keys.HOME));
        assertEquals(0, list.selectedIndex());
        assertEquals(0f, list.scrollY(), 1e-6f);
        assertTrue(t.key(Keys.UP));
        assertEquals(0, list.selectedIndex());
        assertEquals(List.of("Item 0", "Item 99", "Item 94", "Item 0"), selected);
        list.select(t.ctx(), 50);
        assertEquals(50 * 18 + 18 - 90, list.scrollY(), 1e-6f);
        list.select(t.ctx(), -5);
        assertTrue(list.selectedItem().isEmpty());
    }

    @Test
    void clickSelectsAndDoubleClickActivates() {
        List<String> activated = new ArrayList<>();
        ListView<String> list = t.place(new ListView<>(items(10), s -> s).onActivate(activated::add), 0, 0, 200, 90);
        assertEquals(2, list.rowAt(45));
        assertEquals(-1, list.rowAt(200));
        assertTrue(list.mouseDown(t.ctx(), 20, 45, Keys.MOUSE_LEFT));
        assertEquals(2, list.selectedIndex());
        assertTrue(activated.isEmpty());
        t.clock.advance(100L);
        list.mouseDown(t.ctx(), 20, 45, Keys.MOUSE_LEFT);
        assertEquals(List.of("Item 2"), activated);
        t.clock.advance(1000L);
        list.mouseDown(t.ctx(), 20, 45, Keys.MOUSE_LEFT);
        assertEquals(1, activated.size(), "slow second click is not a double click");
        list.requestFocus(t.ctx());
        assertTrue(t.key(Keys.ENTER));
        assertEquals(2, activated.size());
    }

    @Test
    void wheelScrollsAndSetItemsKeepsSelection() {
        ListView<String> list = t.place(new ListView<>(items(100), s -> s), 0, 0, 200, 90);
        assertTrue(list.mouseScroll(t.ctx(), 20, 20, 0, -1));
        t.clock.advance(Theme.MOTION_FAST);
        assertEquals(24f, list.scrollY(), 1e-6f);
        list.select(t.ctx(), 3);
        list.setItems(items(50));
        assertEquals(3, list.selectedIndex());
        list.setItems(List.of("x"));
        assertEquals(-1, list.selectedIndex());
        ListView<String> small = t.place(new ListView<>(items(2), s -> s), 0, 0, 200, 90);
        assertFalse(small.mouseScroll(t.ctx(), 20, 20, 0, -1));
        ListView<String> empty = t.place(new ListView<String>(List.of(), s -> s).emptyText("Nothing here"), 0, 0, 200, 90);
        assertTrue(t.render(empty).hasText("Nothing here"));
        assertFalse(empty.keyDown(t.ctx(), Keys.DOWN, 0, 0));
    }

    @Test
    void customRendererAndRowHeight() {
        List<Integer> rendered = new ArrayList<>();
        ListView<String> list = new ListView<>(items(20), (canvas, ctx, item, index, row, selected, hovered) -> rendered.add(index));
        list.rowHeight(30);
        t.place(list, 0, 0, 100, 60);
        t.render(list);
        assertEquals(List.of(0, 1), rendered);
        assertEquals(30, list.rowHeight());
        assertEquals(600, list.contentHeight());
    }
}
