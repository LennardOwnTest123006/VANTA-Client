package dev.vanta.core.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.UiTestSupport;
import dev.vanta.core.ui.widget.Button;
import org.junit.jupiter.api.Test;

class ScrollPanelTest {

    private final UiTestSupport t = new UiTestSupport();

    private Column tallContent(int rows) {
        Column col = new Column();
        for (int i = 0; i < rows; i++) {
            col.add(Spacer.fixed(10, 20).id("row" + i));
        }
        return col;
    }

    @Test
    void clampsAndLaysOutContentAtTheOffset() {
        Column content = tallContent(50);
        ScrollPanel panel = new ScrollPanel(content);
        t.place(panel, 0, 0, 100, 100);
        assertEquals(1000, panel.contentHeight());
        assertEquals(900f, panel.maxScroll(), 1e-6f);
        assertTrue(panel.isScrollable());
        assertEquals(100 - Scrollbar.reservedWidth(), content.bounds().w(), "scrollbar column reserved");
        panel.setScrollY(t.ctx(), 5000f, false);
        assertEquals(900f, panel.scrollY(), 1e-6f);
        assertEquals(-900, content.bounds().y());
        panel.setScrollY(t.ctx(), 100f, false);
        assertEquals(0, content.findById("row5").bounds().y(), "row 5 sits at the viewport top");
        panel.setScrollY(t.ctx(), -10f, false);
        assertEquals(0f, panel.scrollY(), 1e-6f);
    }

    @Test
    void wheelScrollsByStepsAndAnimates() {
        ScrollPanel panel = new ScrollPanel(tallContent(50));
        t.place(panel, 0, 0, 100, 100);
        assertTrue(panel.mouseScroll(t.ctx(), 50, 50, 0, -1));
        assertEquals(Scrollbar.WHEEL_STEP, panel.targetScrollY(), 1e-6f);
        assertEquals(0f, panel.scrollY(), 1e-6f, "smooth: no movement yet at the same instant");
        t.clock.advance(Theme.MOTION_FAST);
        assertEquals(Scrollbar.WHEEL_STEP, panel.scrollY(), 1e-6f);
        panel.mouseScroll(t.ctx(), 50, 50, 0, 2);
        t.clock.advance(Theme.MOTION_FAST);
        assertEquals(0f, panel.scrollY(), 1e-6f, "clamped at the top");
        ScrollPanel small = new ScrollPanel(tallContent(2));
        t.place(small, 0, 0, 100, 100);
        assertFalse(small.isScrollable());
        assertFalse(small.mouseScroll(t.ctx(), 50, 50, 0, -1), "unscrollable panels let the event through");
    }

    @Test
    void keyboardPagingAndHomeEnd() {
        ScrollPanel panel = new ScrollPanel(tallContent(50));
        panel.setFocusable(true);
        t.place(panel, 0, 0, 100, 100);
        assertTrue(panel.keyDown(t.ctx(), Keys.PAGE_DOWN, 0, 0));
        assertEquals(100 - Theme.SPACE_6, panel.targetScrollY(), 1e-6f);
        assertTrue(panel.keyDown(t.ctx(), Keys.END, 0, Keys.MOD_CONTROL));
        assertEquals(900f, panel.targetScrollY(), 1e-6f);
        assertTrue(panel.keyDown(t.ctx(), Keys.PAGE_UP, 0, 0));
        assertEquals(900f - (100 - Theme.SPACE_6), panel.targetScrollY(), 1e-6f);
        assertTrue(panel.keyDown(t.ctx(), Keys.HOME, 0, Keys.MOD_CONTROL));
        assertEquals(0f, panel.targetScrollY(), 1e-6f);
        assertFalse(panel.keyDown(t.ctx(), Keys.DOWN, 0, 0), "arrows only when focused");
        panel.requestFocus(t.ctx());
        assertTrue(panel.keyDown(t.ctx(), Keys.DOWN, 0, 0));
        assertEquals(Scrollbar.WHEEL_STEP, panel.targetScrollY(), 1e-6f);
    }

    @Test
    void thumbDragMapsToContent() {
        ScrollPanel panel = new ScrollPanel(tallContent(50));
        t.place(panel, 0, 0, 100, 100);
        Rect track = Scrollbar.track(panel.bounds());
        Rect thumb = Scrollbar.thumb(track, panel.contentHeight(), 0f);
        double x = track.centerX();
        assertTrue(panel.mouseDown(t.ctx(), x, thumb.centerY(), Keys.MOUSE_LEFT));
        assertTrue(panel.isDraggingThumb());
        assertEquals(panel, t.ctx().mouseCapture());
        panel.mouseDrag(t.ctx(), x, thumb.centerY() + 44, Keys.MOUSE_LEFT, 0, 44);
        assertEquals(450f, panel.scrollY(), 1e-6f, "half the track range = half the content range");
        panel.mouseUp(t.ctx(), x, 80, Keys.MOUSE_LEFT);
        assertFalse(panel.isDraggingThumb());
        // Click on the track below the thumb pages down.
        panel.setScrollY(t.ctx(), 0f, false);
        panel.mouseDown(t.ctx(), x, 95, Keys.MOUSE_LEFT);
        assertEquals(100f, panel.targetScrollY(), 1e-6f);
        panel.mouseUp(t.ctx(), x, 95, Keys.MOUSE_LEFT);
    }

    @Test
    void scrollIntoViewRevealsFocusedChildren() {
        Column content = new Column();
        Button[] buttons = new Button[30];
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = content.add(new Button("b" + i, null));
        }
        ScrollPanel panel = new ScrollPanel(content);
        UiScreen screen = t.screen(panel, 100, 100);
        UiContext ctx = screen.context();
        ctx.focus().focus(ctx, buttons[10]);
        t.frame(screen, 0, 0);
        t.clock.advance(Theme.MOTION_FAST);
        t.frame(screen, 0, 0);
        Rect view = panel.bounds();
        Rect b = buttons[10].bounds();
        assertTrue(b.y() >= view.y() && b.bottom() <= view.bottom(), "focused button visible: " + b);
        assertTrue(panel.scrollY() > 0f);
        ctx.focus().focus(ctx, buttons[0]);
        t.frame(screen, 0, 0);
        t.clock.advance(Theme.MOTION_FAST);
        t.frame(screen, 0, 0);
        assertEquals(0f, panel.scrollY(), 1e-6f);
    }

    @Test
    void rendersInsideAScissorAndClipsHitTesting() {
        ScrollPanel panel = new ScrollPanel(tallContent(50));
        t.place(panel, 10, 10, 100, 100);
        TestCanvas canvas = t.render(panel);
        assertEquals(new Rect(10, 10, 100, 100), canvas.scissors().get(0).deviceClip());
        assertEquals(null, canvas.scissors().get(canvas.scissors().size() - 1).deviceClip());
        UiNode hit = panel.hitTest(50, 150);
        assertEquals(null, hit, "content below the viewport is not hit");
        assertTrue(panel.hitTest(50, 50) != null);
        assertEquals(new dev.vanta.core.ui.Size(10 + Scrollbar.reservedWidth(), 1000), panel.preferredSize(t.ctx()));
    }
}
