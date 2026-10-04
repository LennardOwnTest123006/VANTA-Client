package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.widget.Button;
import org.junit.jupiter.api.Test;

import java.util.List;

class FocusManagerTest {

    private final UiTestSupport t = new UiTestSupport();

    @Test
    void traversalOrderWrapsAndSkipsDisabled() {
        Column root = new Column();
        Button a = root.add(new Button("a", null));
        Button b = root.add(new Button("b", null));
        Button c = root.add(new Button("c", null));
        c.setEnabled(false);
        t.place(root, 0, 0, 100, 100);
        FocusManager focus = t.ctx().focus();
        focus.setScope(() -> root);
        assertEquals(List.of(a, b), focus.traversal());
        assertNull(focus.focused());
        assertTrue(focus.focusNext(t.ctx()));
        assertSame(a, focus.focused());
        assertTrue(a.isFocused());
        focus.focusNext(t.ctx());
        assertSame(b, focus.focused());
        assertFalse(a.isFocused());
        focus.focusNext(t.ctx());
        assertSame(a, focus.focused(), "wraps around");
        focus.focusPrev(t.ctx());
        assertSame(b, focus.focused());
        focus.focus(t.ctx(), c);
        assertSame(b, focus.focused(), "disabled nodes cannot take focus");
        focus.clear(t.ctx());
        assertNull(focus.focused());
        focus.focusPrev(t.ctx());
        assertSame(b, focus.focused(), "prev from nothing starts at the end");
    }

    @Test
    void validateDropsFocusWhenNodeBecomesUnfocusable() {
        Column root = new Column();
        Button a = root.add(new Button("a", null));
        t.place(root, 0, 0, 100, 100);
        FocusManager focus = t.ctx().focus();
        focus.setScope(() -> root);
        focus.focusFirst(t.ctx());
        assertSame(a, focus.focused());
        long changed = focus.changedAt();
        a.setVisible(false);
        focus.validate(t.ctx());
        assertNull(focus.focused());
        assertEquals(changed, focus.changedAt());
        a.setVisible(true);
        focus.focus(t.ctx(), a);
        root.remove(a);
        focus.validate(t.ctx());
        assertNull(focus.focused(), "nodes outside the scope lose focus");
    }

    @Test
    void emptyScopeHasNothingToFocus() {
        FocusManager focus = t.ctx().focus();
        focus.setScope(() -> null);
        assertFalse(focus.focusNext(t.ctx()));
        assertFalse(focus.focusFirst(t.ctx()));
        assertTrue(focus.traversal().isEmpty());
        assertNull(focus.scopeRoot());
    }
}
