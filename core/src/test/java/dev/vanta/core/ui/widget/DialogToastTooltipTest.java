package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.UiTestSupport;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import org.junit.jupiter.api.Test;

class DialogToastTooltipTest {

    private final UiTestSupport t = new UiTestSupport();

    private static Button button(Dialog d, String label) {
        for (UiNode n : d.buttonRow().children()) {
            if (n instanceof Button b && b.label().equals(label)) {
                return b;
            }
        }
        throw new AssertionError("no button " + label);
    }

    @Test
    void confirmDialogRunsCallbackOnlyOnConfirm() {
        Column root = new Column();
        root.add(new Button("behind", null));
        UiScreen screen = t.screen(root, 400, 300);
        UiContext ctx = screen.context();
        int[] confirmed = {0};
        int[] dismissed = {0};
        Dialog d = Dialog.confirm(ctx, "Delete profile?", "This cannot be undone.", "Delete", "Cancel", true,
                () -> confirmed[0]++).onDismiss(() -> dismissed[0]++);
        assertTrue(ctx.popups().isOpen());
        assertTrue(ctx.popups().top().modal());
        assertSame(button(d, "Cancel"), ctx.focus().focused(), "danger dialogs focus the safe action");
        assertEquals(Dialog.WIDTH, d.bounds().w());
        assertEquals((400 - Dialog.WIDTH) / 2, d.bounds().x());
        Button cancel = button(d, "Cancel");
        UiTestSupport.click(screen, cancel.bounds().centerX(), cancel.bounds().centerY());
        assertFalse(ctx.popups().isOpen());
        assertEquals(0, confirmed[0]);
        assertEquals(1, dismissed[0]);

        Dialog d2 = Dialog.confirm(ctx, "Reset?", "All settings return to defaults.", "Reset", "Cancel", false,
                () -> confirmed[0]++);
        assertSame(button(d2, "Reset"), ctx.focus().focused(), "neutral dialogs focus the primary action");
        assertTrue(screen.keyDown(Keys.ENTER, 0, 0));
        assertEquals(1, confirmed[0]);
        assertFalse(ctx.popups().isOpen());
    }

    @Test
    void infoDialogEscapeAndOutsideClicks() {
        UiScreen screen = t.screen(new Column(), 400, 300);
        UiContext ctx = screen.context();
        Dialog d = Dialog.info(ctx, "Saved", "Your profile was written to disk.", "OK");
        assertEquals("Saved", d.title());
        assertTrue(screen.mouseDown(2, 2, Keys.MOUSE_LEFT));
        assertTrue(ctx.popups().isOpen(), "modal swallows outside clicks");
        assertTrue(screen.keyDown(Keys.ESCAPE, 0, 0));
        assertFalse(ctx.popups().isOpen());
        assertFalse(screen.isClosing());
        t.clock.advance(1000L);
        Dialog.info(ctx, "Title here", "Body here", "OK");
        TestCanvas c = t.frame(screen, 0, 0);
        assertTrue(c.hasText("Title here"));
        assertTrue(c.hasText("Body here"));
        assertTrue(c.hasText("OK"));
    }

    private static void assertCentred(Dialog d, int screenW, int screenH) {
        Rect b = d.bounds();
        assertEquals((screenW - b.w()) / 2, b.x(), "centred horizontally on " + screenW + "x" + screenH + ": " + b);
        assertEquals((screenH - b.h()) / 2, b.y(), "centred vertically on " + screenW + "x" + screenH + ": " + b);
    }

    @Test
    void dialogFollowsWindowResizes() {
        UiScreen screen = t.screen(new Column(), 800, 600);
        UiContext ctx = screen.context();
        int[] confirmed = {0};
        Dialog d = Dialog.confirm(ctx, "Quit?", "Really quit?", "Quit", "Cancel", true, () -> confirmed[0]++);
        assertCentred(d, 800, 600);
        Rect opened = d.bounds();

        screen.resize(320, 240);
        Rect small = new Rect(0, 0, 320, 240);
        assertCentred(d, 320, 240);
        assertTrue(d.bounds().y() < opened.y(), "moved with the window: " + d.bounds());
        Button cancel = button(d, "Cancel");
        Button quit = button(d, "Quit");
        assertTrue(small.contains(cancel.bounds()), "cancel on screen: " + cancel.bounds());
        assertTrue(small.contains(quit.bounds()), "quit on screen: " + quit.bounds());

        screen.resize(1920, 1080);
        assertCentred(d, 1920, 1080);
        assertTrue(ctx.popups().isOpen());
        UiTestSupport.click(screen, cancel.bounds().centerX(), cancel.bounds().centerY());
        assertFalse(ctx.popups().isOpen(), "the re-centred cancel button closes the dialog");
        assertEquals(0, confirmed[0]);

        Dialog d2 = Dialog.confirm(ctx, "Quit?", "Really quit?", "Quit", "Cancel", true, () -> confirmed[0]++);
        screen.resize(320, 240);
        Button quit2 = button(d2, "Quit");
        UiTestSupport.click(screen, quit2.bounds().centerX(), quit2.bounds().centerY());
        assertFalse(ctx.popups().isOpen());
        assertEquals(1, confirmed[0], "the re-centred confirm button runs the action");
    }

    @Test
    void dialogFollowsThemeChanges() {
        UiScreen screen = t.screen(new Column(), 1600, 1200);
        UiContext ctx = screen.context();
        int[] confirmed = {0};
        Dialog d = Dialog.confirm(ctx, "Reset?", "All settings return to defaults.", "Reset", "Cancel", false,
                () -> confirmed[0]++);
        assertCentred(d, 1600, 1200);

        screen.setTheme(Theme.DEFAULT.withScale(2f));
        assertEquals(800, screen.width());
        assertEquals(600, screen.height());
        assertCentred(d, 800, 600);

        screen.setTheme(Theme.DEFAULT.withScale(2f).withLargeText(true));
        assertCentred(d, screen.width(), screen.height());
        Button reset = button(d, "Reset");
        assertTrue(new Rect(0, 0, screen.width(), screen.height()).contains(reset.bounds()));
        float s = screen.effectiveScale();
        UiTestSupport.click(screen, reset.bounds().centerX() * s, reset.bounds().centerY() * s);
        assertFalse(ctx.popups().isOpen());
        assertEquals(1, confirmed[0]);
    }

    @Test
    void toastLayoutAndKinds() {
        Toast toast = new Toast(Toast.Kind.SUCCESS, "Profile saved", "Written to config/vanta/profiles.").progress(0.5f);
        Size size = toast.preferredSize(t.ctx());
        assertEquals(Toast.WIDTH, size.w());
        t.place(toast, 0, 0, size.w(), size.h());
        TestCanvas c = t.render(toast);
        assertTrue(c.hasText("Profile saved"));
        assertTrue(c.hasTextContaining("Written to"));
        assertTrue(c.fills().stream().anyMatch(f -> f.argb() == Theme.DEFAULT.success() && f.h() == 1), "progress line");
        assertEquals(Icons.SUCCESS, toast.icon());
        assertEquals(Theme.DEFAULT.danger(), new Toast(Toast.Kind.ERROR, "x", "").accent(Theme.DEFAULT));
        assertEquals(Icons.WARNING, new Toast(Toast.Kind.WARNING, "x", "").icon());
        assertEquals(Icons.STAR, new Toast(Toast.Kind.INFO, "x", "").icon(Icons.STAR).icon());
        Toast noBody = new Toast(Toast.Kind.INFO, "Only title", "");
        assertTrue(noBody.preferredSize(t.ctx()).h() < size.h());
        Toast longBody = new Toast(Toast.Kind.INFO, "t", "word ".repeat(80));
        t.place(longBody, 0, 0, Toast.WIDTH, longBody.preferredSize(t.ctx()).h());
        assertEquals(1 + 3, t.render(longBody).texts().size(), "body capped at three lines");
        assertEquals(0.5f, toast.progress(), 1e-6f);
    }

    @Test
    void tooltipAppearsAfterDelayAndStaysOnScreen() {
        Column root = new Column().align(Align.START);
        Button b = root.add(new Button("Hover me", null));
        b.size(60, 20);
        b.setTooltip("Resets the layout");
        UiScreen screen = t.screen(root, 400, 300);
        t.clock.advance(1000L);
        TestCanvas before = t.frame(screen, 10, 10);
        assertFalse(before.hasText("Resets the layout"));
        t.clock.advance(Tooltip.DELAY_MS - 1);
        assertFalse(t.frame(screen, 10, 10).hasText("Resets the layout"));
        t.clock.advance(1);
        TestCanvas shown = t.frame(screen, 10, 10);
        assertTrue(shown.hasText("Resets the layout"));
        t.frame(screen, 200, 200);
        assertFalse(t.frame(screen, 200, 200).hasText("Resets the layout"));
        UiContext ctx = screen.context();
        Rect nearEdge = Tooltip.layout(ctx, "Resets the layout", 395, 295);
        assertTrue(nearEdge.right() <= 400 && nearEdge.bottom() <= 300, "flipped inside the screen: " + nearEdge);
        Rect normal = Tooltip.layout(ctx, "Hi", 10, 10);
        assertEquals(18, normal.x());
        assertEquals(22, normal.y());
        Rect wrapped = Tooltip.layout(ctx, "word ".repeat(60), 0, 0);
        assertTrue(wrapped.w() <= Tooltip.MAX_WIDTH + 12);
        assertTrue(wrapped.h() > 9 * 3);
    }
}
