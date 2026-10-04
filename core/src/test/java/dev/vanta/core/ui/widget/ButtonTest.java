package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiTestSupport;
import org.junit.jupiter.api.Test;

class ButtonTest {

    private final UiTestSupport t = new UiTestSupport();

    @Test
    void firesOnReleaseInsideOnly() {
        int[] clicks = {0};
        Button b = t.place(new Button("Save", () -> clicks[0]++), 0, 0, 80, 20);
        t.click(b);
        assertEquals(1, clicks[0]);
        assertEquals(1, t.host.clicks(), "click sound played");
        assertTrue(b.isFocused(), "clicking focuses");
        b.mouseDown(t.ctx(), 10, 10, Keys.MOUSE_LEFT);
        assertTrue(b.isPressed());
        assertSame(b, t.ctx().mouseCapture());
        b.mouseUp(t.ctx(), 200, 200, Keys.MOUSE_LEFT);
        assertFalse(b.isPressed());
        assertNull(t.ctx().mouseCapture());
        assertEquals(1, clicks[0], "release outside does not fire");
        assertFalse(b.mouseDown(t.ctx(), 10, 10, Keys.MOUSE_RIGHT), "right click ignored");
    }

    @Test
    void keyboardActivationAndDisabledState() {
        int[] clicks = {0};
        Button b = t.place(new Button("Save", Button.Variant.PRIMARY, () -> clicks[0]++), 0, 0, 80, 20);
        b.requestFocus(t.ctx());
        assertTrue(t.key(Keys.ENTER));
        assertTrue(t.key(Keys.SPACE));
        assertFalse(t.key(Keys.A));
        assertEquals(2, clicks[0]);
        b.setEnabled(false);
        t.click(b);
        assertFalse(t.key(Keys.ENTER) && clicks[0] > 2);
        assertEquals(2, clicks[0]);
        assertFalse(b.canFocus());
    }

    @Test
    void measuresFromLabelIconAndPadding() {
        assertEquals(new Size(20 + Button.PADDING_X * 2, Button.HEIGHT), new Button("Save", null).preferredSize(t.ctx()));
        assertEquals(new Size(20 + Button.PADDING_X * 2 + Button.ICON_SIZE + 5, Button.HEIGHT),
                new Button("Save", null).icon(Icons.PLUS).preferredSize(t.ctx()));
        assertEquals(new Size(20 + Theme.SPACE_3 * 2, Button.HEIGHT), new Button("Save", null).compact(true).preferredSize(t.ctx()));
        assertEquals(Button.PADDING_X * 2, new Button("", null).preferredSize(t.ctx()).w(), "padding only for an empty label");
        assertEquals(Button.HEIGHT, new Button("", null).compact(true).preferredSize(t.ctx()).w(), "never narrower than tall");
    }

    @Test
    void rendersVariantsWithExpectedColors() {
        Button primary = t.place(Button.primary("Go", null), 0, 0, 60, 20);
        TestCanvas c = t.render(primary);
        assertTrue(c.hasText("Go"));
        assertEquals(Colors.WHITE, c.texts().get(0).argb());
        assertTrue(c.fills().stream().anyMatch(f -> f.argb() == Theme.DEFAULT.gradientStart()), "gradient starts violet");

        Button danger = t.place(Button.danger("Delete", null), 0, 0, 60, 20);
        TestCanvas d = t.render(danger);
        assertEquals(Theme.DEFAULT.danger(), d.texts().get(0).argb());

        Button ghost = t.place(Button.ghost("More", null), 0, 0, 60, 20);
        TestCanvas g = t.render(ghost);
        assertTrue(g.fills().isEmpty(), "ghost has no surface until hovered");
        assertEquals(Theme.DEFAULT.textSecondary(), g.texts().get(0).argb());

        Button disabled = t.place(Button.secondary("Off", null), 0, 0, 60, 20);
        disabled.setEnabled(false);
        TestCanvas off = t.render(disabled);
        assertTrue(Colors.alpha(off.texts().get(0).argb()) < 255, "disabled content is translucent");

        Button focusedBtn = t.place(Button.secondary("F", null), 10, 10, 60, 20);
        focusedBtn.requestFocus(t.ctx());
        TestCanvas f = t.render(focusedBtn);
        assertTrue(f.fills().stream().anyMatch(fill -> fill.x() == 8 && fill.y() == 8 + 3), "focus ring 2 px outside");
    }

    @Test
    void longLabelsAreEllipsized() {
        Button b = t.place(new Button("An extremely long label", null), 0, 0, 60, 20);
        TestCanvas c = t.render(b);
        assertTrue(c.texts().get(0).text().endsWith("…"));
    }

    @Test
    void iconButtonBehavesLikeAButton() {
        int[] clicks = {0};
        IconButton ib = t.place(new IconButton(Icons.CLOSE, () -> clicks[0]++), 0, 0, 20, 20);
        assertEquals(new Size(IconButton.SIZE, IconButton.SIZE), ib.preferredSize(t.ctx()));
        t.click(ib);
        assertEquals(1, clicks[0]);
        ib.requestFocus(t.ctx());
        assertTrue(t.key(Keys.SPACE));
        assertEquals(2, clicks[0]);
        TestCanvas c = t.render(ib.active(true));
        assertTrue(c.fills().stream().anyMatch(f -> Colors.alpha(f.argb()) < 255 && (f.argb() & 0xFFFFFF) == (Theme.DEFAULT.accent() & 0xFFFFFF)));
        assertTrue(ib.isActive());
        ib.sizes(16, 8);
        assertEquals(new Size(16, 16), ib.preferredSize(t.ctx()));
    }
}
