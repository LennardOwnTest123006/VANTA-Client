package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.UiTestSupport;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class SelectTest {

    private final UiTestSupport t = new UiTestSupport();
    private final List<String> changes = new ArrayList<>();
    private Select<String> select;
    private UiScreen screen;

    private void host() {
        Column root = new Column().align(Align.START);
        select = root.add(new Select<>(List.of("Low", "Balanced", "High", "Ultra"), "Balanced"));
        select.size(120, 20);
        select.onChange(changes::add);
        screen = t.screen(root, 400, 300);
    }

    @Test
    void opensOnClickAndSelectsWithKeyboard() {
        host();
        assertEquals(1, select.selectedIndex());
        UiTestSupport.click(screen, 60, 10);
        assertTrue(select.isOpen());
        UiContext ctx = screen.context();
        assertNotNull(select.popupNode());
        assertEquals(new Rect(0, 22, 120, 4 * Select.ROW_H + 4), select.popupNode().bounds());
        assertSame(select.popupNode(), ctx.focus().focused(), "popup takes focus");
        assertEquals(1, select.highlightedIndex());
        assertTrue(screen.keyDown(Keys.DOWN, 0, 0));
        assertEquals(2, select.highlightedIndex());
        assertTrue(screen.keyDown(Keys.ENTER, 0, 0));
        assertFalse(select.isOpen());
        assertEquals("High", select.value());
        assertEquals(List.of("High"), changes);
        assertSame(select, ctx.focus().focused(), "focus returns to the select");
    }

    @Test
    void escapeAndOutsideClickCloseWithoutChanging() {
        host();
        UiTestSupport.click(screen, 60, 10);
        assertTrue(screen.keyDown(Keys.ESCAPE, 0, 0));
        assertFalse(select.isOpen());
        assertFalse(screen.isClosing());
        screen.context().focus().focus(screen.context(), select);
        assertTrue(screen.keyDown(Keys.DOWN, 0, 0), "Down opens a focused select");
        assertTrue(select.isOpen());
        screen.mouseDown(300, 250, Keys.MOUSE_LEFT);
        assertFalse(select.isOpen());
        assertEquals("Balanced", select.value());
        assertTrue(changes.isEmpty());
    }

    @Test
    void typeAheadJumpsToMatchingOption() {
        host();
        UiTestSupport.click(screen, 60, 10);
        assertTrue(screen.charTyped('u', 0));
        assertEquals(3, select.highlightedIndex());
        t.clock.advance(1000L);
        assertTrue(screen.charTyped('l', 0));
        assertEquals(0, select.highlightedIndex(), "buffer resets after the timeout");
        assertTrue(screen.charTyped('o', 0));
        assertEquals(0, select.highlightedIndex());
        assertTrue(screen.keyDown(Keys.END, 0, 0));
        assertEquals(3, select.highlightedIndex());
        assertTrue(screen.keyDown(Keys.HOME, 0, 0));
        assertEquals(0, select.highlightedIndex());
        assertTrue(screen.keyDown(Keys.SPACE, 0, 0));
        assertTrue(select.isOpen(), "Space right after typing continues the type-ahead");
        t.clock.advance(1000L);
        assertTrue(screen.keyDown(Keys.SPACE, 0, 0));
        assertEquals("Low", select.value());
    }

    @Test
    void mouseSelectionInPopup() {
        host();
        UiTestSupport.click(screen, 60, 10);
        Rect popup = select.popupNode().bounds();
        int rowY = popup.y() + 2 + Select.ROW_H * 3 + Select.ROW_H / 2;
        UiTestSupport.click(screen, 60, rowY);
        assertEquals("Ultra", select.value());
        assertFalse(select.isOpen());
        UiTestSupport.click(screen, 60, 10);
        UiTestSupport.click(screen, 60, 10);
        assertFalse(select.isOpen(), "clicking the select again closes it");
    }

    @Test
    void rendersCurrentValueAndMeasures() {
        host();
        t.clock.advance(1000L);
        TestCanvas c = t.frame(screen, 0, 0);
        assertTrue(c.hasText("Balanced"));
        assertEquals(8 * 5 + 16 + 9 + 4, new Select<>(List.of("Balanced"), "Balanced").preferredSize(t.ctx()).w());
        select.setOptions(List.of("High", "Balanced"));
        assertEquals(1, select.selectedIndex(), "selection kept by equality");
        select.setValue("High");
        assertEquals("High", select.value());
        select.setEnabled(false);
        select.open(screen.context());
        assertFalse(select.isOpen());
        assertTrue(new Select<>(List.of(), (String) null).value() == null);
    }

    @Test
    void popupScrollsWhenThereAreManyOptions() {
        Column root = new Column().align(Align.START);
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add("Option " + i);
        }
        Select<String> big = root.add(new Select<>(many, "Option 0"));
        big.size(120, 20);
        UiScreen s = t.screen(root, 400, 300);
        UiTestSupport.click(s, 60, 10);
        assertEquals(Select.MAX_VISIBLE * Select.ROW_H + 4, big.popupNode().bounds().h());
        for (int i = 0; i < 19; i++) {
            s.keyDown(Keys.DOWN, 0, 0);
        }
        assertEquals(19, big.highlightedIndex());
        t.clock.advance(1000L);
        TestCanvas c = t.frame(s, 0, 0);
        assertTrue(c.hasText("Option 19"), "highlighted row scrolled into view");
        assertFalse(c.hasText("Option 1"), "rows above the viewport are not drawn");
    }
}
