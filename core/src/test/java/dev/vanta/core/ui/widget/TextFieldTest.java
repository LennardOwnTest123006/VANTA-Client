package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class TextFieldTest {

    private final UiTestSupport t = new UiTestSupport();

    @Test
    void insertDeleteAndCaret() {
        TextField f = new TextField();
        f.insert("hello");
        assertEquals("hello", f.text());
        assertEquals(5, f.caret());
        f.setCaret(2, false);
        f.insert("X");
        assertEquals("heXllo", f.text());
        f.deleteBackward();
        assertEquals("hello", f.text());
        f.deleteForward();
        assertEquals("helo", f.text());
        f.setCaret(99, false);
        assertEquals(4, f.caret());
        f.deleteForward();
        assertEquals("helo", f.text(), "nothing after the caret");
        f.setCaret(0, false);
        f.deleteBackward();
        assertEquals("helo", f.text());
        f.moveCaret(1, false);
        assertEquals(1, f.caret());
        f.moveCaret(-5, false);
        assertEquals(0, f.caret());
    }

    @Test
    void selectionReplacesAndCollapses() {
        TextField f = new TextField("hello world");
        f.setCaret(0, false);
        f.setCaret(5, true);
        assertTrue(f.hasSelection());
        assertEquals("hello", f.selectedText());
        f.insert("J");
        assertEquals("J world", f.text());
        assertFalse(f.hasSelection());
        f.selectAll();
        assertEquals(0, f.selectionStart());
        assertEquals(7, f.selectionEnd());
        f.moveCaret(-1, false);
        assertEquals(0, f.caret(), "left collapses to the selection start");
        f.selectAll();
        f.moveCaret(1, false);
        assertEquals(7, f.caret());
        f.setCaret(2, false);
        f.setCaret(4, true);
        f.deleteBackward();
        assertEquals("J rld", f.text());
        f.setCaret(1, false);
        f.setCaret(3, true);
        f.deleteSelection();
        assertEquals("Jld", f.text());
    }

    @Test
    void wordNavigationAndDeletion() {
        TextField f = new TextField("one two  three");
        assertEquals(14, f.caret());
        f.moveWord(-1, false);
        assertEquals(9, f.caret());
        f.moveWord(-1, false);
        assertEquals(4, f.caret());
        f.moveWord(1, false);
        assertEquals(9, f.caret());
        f.setCaret(14, false);
        f.deleteWordBackward();
        assertEquals("one two  ", f.text());
        f.setCaret(0, false);
        f.deleteWordForward();
        assertEquals("two  ", f.text());
        assertEquals(0, f.previousWordBoundary(0));
        assertEquals(5, f.nextWordBoundary(0));
    }

    @Test
    void maxLengthFilterAndValidator() {
        TextField f = new TextField().maxLength(5);
        f.setText("abcdefgh");
        assertEquals("abcde", f.text());
        f.insert("z");
        assertEquals("abcde", f.text());
        TextField digits = new TextField().charFilter(cp -> Character.isDigit(cp));
        digits.insert("a1b2\n3");
        assertEquals("123", digits.text());
        TextField hex = new TextField("#12G").validator(Colors::isValidHex);
        assertFalse(hex.isValid());
        hex.setText("#123456");
        assertTrue(hex.isValid());
        assertTrue(new TextField().isValid());
        assertEquals(5, f.maxLength());
    }

    @Test
    void clipboardThroughHost() {
        TextField f = t.place(new TextField("secret"), 0, 0, 100, 20);
        f.selectAll();
        f.copy(t.ctx());
        assertEquals("secret", t.host.getClipboard());
        f.cut(t.ctx());
        assertEquals("", f.text());
        t.host.primeClipboard("pasted");
        f.paste(t.ctx());
        assertEquals("pasted", f.text());
        f.requestFocus(t.ctx());
        assertTrue(t.key(Keys.A, Keys.MOD_CONTROL));
        assertTrue(f.hasSelection());
        assertTrue(t.key(Keys.X, Keys.MOD_CONTROL));
        assertEquals("", f.text());
        assertTrue(t.key(Keys.V, Keys.MOD_CONTROL));
        assertEquals("pasted", f.text());
        assertTrue(t.key(Keys.C, Keys.MOD_SUPER), "Command works like Control");
    }

    @Test
    void keyRoutingAndCharTyped() {
        List<String> submitted = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        TextField f = t.place(new TextField(), 0, 0, 100, 20);
        f.onSubmit(submitted::add).onChange(changed::add);
        f.requestFocus(t.ctx());
        t.type("ab");
        assertEquals("ab", f.text());
        assertEquals(List.of("a", "ab"), changed);
        assertTrue(t.key(Keys.LEFT));
        assertEquals(1, f.caret());
        assertTrue(t.key(Keys.HOME));
        assertTrue(t.key(Keys.END, Keys.MOD_SHIFT));
        assertEquals("ab", f.selectedText());
        assertTrue(t.key(Keys.BACKSPACE));
        assertEquals("", f.text());
        t.type("x y");
        assertTrue(t.key(Keys.BACKSPACE, Keys.MOD_CONTROL));
        assertEquals("x ", f.text());
        assertTrue(t.key(Keys.ENTER));
        assertEquals(List.of("x "), submitted);
        assertTrue(t.key(Keys.A), "printable keys are swallowed so screens never treat them as shortcuts");
        assertFalse(t.key(Keys.TAB), "Tab is left to focus navigation");
        assertFalse(t.key(Keys.ESCAPE));
        assertFalse(f.charTyped(t.ctx(), 7, 0), "control characters ignored");
        f.setEnabled(false);
        assertFalse(f.charTyped(t.ctx(), 'q', 0));
    }

    @Test
    void mouseCaretPlacementAndSelection() {
        TextField f = t.place(new TextField("abcdef"), 0, 0, 100, 20);
        int left = f.textRect().x();
        assertEquals(0, f.indexAt(t.ctx(), left - 10));
        assertEquals(2, f.indexAt(t.ctx(), left + 12));
        assertEquals(3, f.indexAt(t.ctx(), left + 13));
        assertEquals(6, f.indexAt(t.ctx(), left + 500));
        assertTrue(f.mouseDown(t.ctx(), left + 12, 10, Keys.MOUSE_LEFT));
        assertEquals(2, f.caret());
        assertTrue(f.isFocused());
        f.mouseDrag(t.ctx(), left + 23, 10, Keys.MOUSE_LEFT, 11, 0);
        assertEquals("cde", f.selectedText());
        f.mouseUp(t.ctx(), left + 23, 10, Keys.MOUSE_LEFT);
        assertTrue(t.ctx().mouseCapture() == null);
    }

    @Test
    void rendersPlaceholderCaretAndSelection() {
        TextField empty = t.place(new TextField().placeholder("Name"), 0, 0, 100, 20);
        TestCanvas c = t.render(empty);
        assertTrue(c.hasText("Name"));
        assertEquals(Theme.DEFAULT.textMuted(), c.texts().get(0).argb());
        assertTrue(c.fills().stream().anyMatch(f -> f.argb() == Theme.DEFAULT.borderSubtle()));

        TextField focused = t.place(new TextField("abc"), 0, 0, 100, 20);
        focused.requestFocus(t.ctx());
        focused.setCaret(1, false);
        focused.setCaret(3, true);
        TestCanvas fc = t.render(focused);
        assertTrue(fc.fills().stream().anyMatch(f -> f.argb() == Theme.DEFAULT.borderFocus()), "focus border");
        assertTrue(fc.fills().stream().anyMatch(f -> f.argb() == Colors.withAlpha(Theme.DEFAULT.accent(), 0.4f)
                && f.w() == 10), "selection highlight spans two characters");
        assertTrue(fc.fills().stream().anyMatch(f -> f.w() == 1 && f.h() == 12), "caret visible at phase 0");
        t.clock.advance(TextField.BLINK_MS / 2);
        TestCanvas blink = t.render(focused);
        assertFalse(blink.fills().stream().anyMatch(f -> f.w() == 1 && f.h() == 12 && f.argb() == Theme.DEFAULT.accentHover()),
                "caret hidden in the second half of the blink period");

        TextField invalid = t.place(new TextField("#12G").validator(Colors::isValidHex), 0, 0, 100, 20);
        assertTrue(t.render(invalid).fills().stream().anyMatch(f -> f.argb() == Theme.DEFAULT.danger()));
    }

    @Test
    void longTextScrollsToKeepTheCaretVisible() {
        TextField f = t.place(new TextField("0123456789012345678901234567890123456789"), 0, 0, 60, 20);
        f.requestFocus(t.ctx());
        TestCanvas c = t.render(f);
        TestCanvas.Text drawn = c.texts().get(0);
        assertTrue(drawn.x() < f.textRect().x(), "text shifted left so the caret at the end is visible");
    }
}
