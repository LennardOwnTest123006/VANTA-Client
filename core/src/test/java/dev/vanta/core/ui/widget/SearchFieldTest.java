package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.UiTestSupport;
import org.junit.jupiter.api.Test;

class SearchFieldTest {

    private final UiTestSupport t = new UiTestSupport();

    @Test
    void clearButtonAndEscape() {
        SearchField f = t.place(new SearchField("Search…"), 0, 0, 120, 20);
        assertEquals("Search…", f.placeholder());
        TestCanvas empty = t.render(f);
        int emptyFills = empty.fills().size();
        f.setText("fps");
        TestCanvas filled = t.render(f);
        assertTrue(filled.fills().size() > emptyFills, "clear glyph drawn when there is text");
        Rect clear = f.clearRect();
        assertTrue(f.mouseDown(t.ctx(), clear.centerX(), clear.centerY(), Keys.MOUSE_LEFT));
        assertTrue(f.isEmpty());
        assertTrue(f.isFocused());
        f.setText("abc");
        assertTrue(t.key(Keys.ESCAPE), "Escape clears a non-empty search field");
        assertEquals("", f.text());
        assertFalse(t.key(Keys.ESCAPE), "Escape on an empty field propagates to the screen");
        assertEquals(2, t.host.clicks());
    }

    @Test
    void leadingIconReservesSpace() {
        SearchField f = t.place(new SearchField("Find"), 0, 0, 120, 20);
        assertEquals(TextField.PADDING_X + 9 + 4, f.textRect().x());
        TextField plain = t.place(new TextField(), 0, 0, 120, 20);
        assertEquals(TextField.PADDING_X, plain.textRect().x());
        assertTrue(f.textRect().right() < plain.textRect().right(), "room for the clear button");
        TextField withIcon = new TextField().leadingIcon(Icons.GEAR);
        assertEquals(f.textRect().x(), t.place(withIcon, 0, 0, 120, 20).textRect().x());
    }
}
