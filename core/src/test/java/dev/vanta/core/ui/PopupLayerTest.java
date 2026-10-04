package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Panel;
import dev.vanta.core.ui.widget.Button;
import org.junit.jupiter.api.Test;

class PopupLayerTest {

    private final UiTestSupport t = new UiTestSupport();

    private Panel popupWithButton(Button[] out) {
        Panel popup = new Panel();
        out[0] = popup.add(new Button("inside", null));
        popup.setBounds(50, 50, 100, 60);
        return popup;
    }

    @Test
    void nonModalPopupTakesFocusAndClosesOnOutsideClick() {
        Column root = new Column();
        Button a = root.add(new Button("a", null));
        UiScreen screen = t.screen(root, 300, 200);
        UiContext ctx = screen.context();
        ctx.focus().focus(ctx, a);
        Button[] inner = new Button[1];
        Panel popup = popupWithButton(inner);
        boolean[] closed = {false};
        ctx.popups().open(ctx, popup, false, () -> closed[0] = true);
        assertTrue(ctx.popups().isOpen());
        assertTrue(ctx.popups().isOpen(popup));
        assertSame(inner[0], ctx.focus().focused(), "focus moves into the popup");
        assertEquals(1, ctx.focus().traversal().size(), "traversal scoped to the popup");
        assertTrue(screen.mouseDown(5, 5, Keys.MOUSE_LEFT), "outside click is consumed");
        assertFalse(ctx.popups().isOpen());
        assertTrue(closed[0]);
        assertSame(a, ctx.focus().focused(), "previous focus restored");
    }

    @Test
    void modalPopupSwallowsOutsideInputAndClosesOnEscape() {
        Column root = new Column();
        Button a = root.add(new Button("a", null));
        UiScreen screen = t.screen(root, 300, 200);
        UiContext ctx = screen.context();
        Button[] inner = new Button[1];
        Panel popup = popupWithButton(inner);
        ctx.popups().open(ctx, popup, true, null);
        assertTrue(screen.mouseDown(5, 5, Keys.MOUSE_LEFT));
        assertTrue(ctx.popups().isOpen(), "modal stays open");
        assertTrue(ctx.popups().top().modal());
        assertSame(popup, ctx.popups().hitTest(5, 5), "modal captures hit testing everywhere");
        assertTrue(screen.keyDown(Keys.ESCAPE, 0, 0));
        assertFalse(ctx.popups().isOpen());
        assertFalse(screen.isClosing(), "Escape closed the popup, not the screen");
        assertSame(a, a);
    }

    @Test
    void clicksInsideAreForwardedAndStackIsOrdered() {
        Column root = new Column();
        root.add(new Button("a", null));
        UiScreen screen = t.screen(root, 300, 200);
        UiContext ctx = screen.context();
        int[] clicks = {0};
        Panel first = new Panel();
        first.add(new Button("one", () -> clicks[0]++));
        first.setBounds(0, 0, 100, 20);
        Panel second = new Panel();
        second.setBounds(100, 100, 50, 50);
        ctx.popups().open(ctx, first, false, null);
        ctx.popups().open(ctx, second, false, null);
        assertEquals(2, ctx.popups().popups().size());
        assertSame(second, ctx.popups().top().node());
        assertTrue(ctx.popups().closeTop(ctx));
        assertSame(first, ctx.popups().top().node());
        UiTestSupport.click(screen, 10, 10);
        assertEquals(1, clicks[0]);
        ctx.popups().closeAll(ctx);
        assertFalse(ctx.popups().isOpen());
        assertNull(ctx.popups().top());
        assertFalse(ctx.popups().closeTop(ctx));
    }

    @Test
    void renderDimsBehindModal() {
        Column root = new Column();
        UiScreen screen = t.screen(root, 300, 200);
        UiContext ctx = screen.context();
        Panel popup = new Panel().background(0xFF111111);
        popup.setBounds(10, 10, 20, 20);
        ctx.popups().open(ctx, popup, true, null);
        t.clock.advance(1000L);
        TestCanvas canvas = t.frame(screen, 0, 0);
        assertTrue(canvas.fills().stream().anyMatch(f -> f.argb() == ctx.theme().modalDim() && f.w() == 300));
        assertTrue(canvas.fills().stream().anyMatch(f -> f.argb() == 0xFF111111));
    }
}
