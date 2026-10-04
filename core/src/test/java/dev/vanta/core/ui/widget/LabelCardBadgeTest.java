package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

class LabelCardBadgeTest {

    private final UiTestSupport t = new UiTestSupport();

    @Test
    void labelVariantsPickFontAndColor() {
        assertEquals(FontKind.UI_BOLD, new Label("x", Label.Variant.TITLE).font());
        assertEquals(FontKind.DISPLAY, new Label("x", Label.Variant.DISPLAY).font());
        assertEquals(FontKind.UI, new Label("x").font());
        assertEquals(Theme.DEFAULT.textMuted(), new Label("x", Label.Variant.MUTED).color(Theme.DEFAULT));
        assertEquals(Theme.DEFAULT.textSecondary(), new Label("x", Label.Variant.CAPTION).color(Theme.DEFAULT));
        assertEquals(0xFF123456, new Label("x").color(0xFF123456).color(Theme.DEFAULT));
        assertEquals(new Size(20, 9), new Label("abcd").preferredSize(t.ctx()));
        assertEquals(new Size(32, 14), new Label("abcd", Label.Variant.DISPLAY).preferredSize(t.ctx()));
    }

    @Test
    void labelWrapsAndEllipsizes() {
        Label wrapped = new Label("aaaa bbbb cccc").wrap(true);
        wrapped.width(50);
        assertEquals(new Size(50, 18), wrapped.preferredSize(t.ctx()));
        t.place(wrapped, 0, 0, 50, 18);
        assertEquals(List.of("aaaa bbbb", "cccc"), wrapped.lines());
        TestCanvas c = t.render(wrapped);
        assertEquals(2, c.texts().size());
        assertEquals(0, c.texts().get(0).y(), "wrapped labels align to the top");
        assertEquals(9, c.texts().get(1).y());
        wrapped.maxLines(1);
        t.place(wrapped, 0, 0, 50, 9);
        assertEquals(1, wrapped.lines().size());
        assertTrue(wrapped.lines().get(0).endsWith("…"));

        Label clipped = t.place(new Label("aaaa bbbb cccc"), 0, 0, 30, 20);
        assertEquals(List.of("aaaa…"), clipped.lines());
        TestCanvas cc = t.render(clipped);
        assertEquals((20 - 9) / 2, cc.texts().get(0).y(), "single lines center vertically");
        Label right = t.place(new Label("ab").align(Label.HAlign.RIGHT), 0, 0, 100, 9);
        assertEquals(90, t.render(right).texts().get(0).x());
        Label center = t.place(new Label("ab").align(Label.HAlign.CENTER), 0, 0, 100, 9);
        assertEquals(45, t.render(center).texts().get(0).x());
        Label disabled = t.place(new Label("ab"), 0, 0, 100, 9);
        disabled.setEnabled(false);
        assertEquals(Theme.DEFAULT.textMuted(), t.render(disabled).texts().get(0).argb());
    }

    @Test
    void cardHeaderAndBody() {
        Card card = new Card("Profiles").caption("Manage presets");
        Label body = card.add(new Label("Body"));
        Toggle slot = new Toggle(true, null);
        card.headerSlot(slot);
        t.place(card, 0, 0, 200, 100);
        int headerH = Card.HEADER_H + 9;
        assertEquals(new Rect(0, headerH, 200, 100 - headerH), card.body().bounds());
        assertEquals(12, body.bounds().x(), "body padding");
        assertEquals(200 - Theme.SPACE_5 - Toggle.TRACK_W, slot.bounds().x());
        TestCanvas c = t.render(card);
        assertTrue(c.hasText("Profiles"));
        assertTrue(c.hasText("Manage presets"));
        assertTrue(c.hasText("Body"));
        assertTrue(c.fills().stream().anyMatch(f -> f.w() == 2 && f.argb() == Theme.DEFAULT.gradientStart()), "accent bar");
        assertTrue(card.preferredSize(t.ctx()).h() >= headerH + 9 + 24);
        Card plain = new Card();
        plain.add(new Label("x"));
        t.place(plain, 0, 0, 100, 50);
        assertEquals(new Rect(0, 0, 100, 50), plain.body().bounds());
        assertFalse(t.render(plain).hasText("Profiles"));
        card.headerSlot(null);
        assertFalse(card.children().contains(slot));
    }

    @Test
    void badgesTonesAndSize() {
        Badge badge = t.place(new Badge("Beta", Badge.Tone.INFO), 0, 0, 60, 20);
        assertEquals(new Size(20 + 10, Badge.HEIGHT), badge.preferredSize(t.ctx()));
        TestCanvas c = t.render(badge);
        assertTrue(c.hasText("Beta"));
        assertEquals(Theme.DEFAULT.info(), c.texts().get(0).argb());
        assertTrue(c.fills().stream().anyMatch(f -> f.argb() == Colors.withAlpha(Theme.DEFAULT.info(), 0.18f)));
        assertEquals(Theme.DEFAULT.textSecondary(), new Badge("x").foreground(Theme.DEFAULT));
        assertEquals(Theme.DEFAULT.danger(), new Badge("x", Badge.Tone.DANGER).foreground(Theme.DEFAULT));
        assertEquals(Theme.DEFAULT.success(), new Badge("x").tone(Badge.Tone.SUCCESS).foreground(Theme.DEFAULT));
        assertEquals("y", badge.setText("y").text());
    }

    @Test
    void dividerAndProgressBar() {
        Divider h = t.place(new Divider(), 0, 10, 100, 1);
        assertEquals(new TestCanvas.Fill(0, 10, 100, 1, Theme.DEFAULT.borderSubtle()), t.render(h).fills().get(0));
        Divider v = t.place(new Divider(true).color(0xFF112233), 5, 0, 1, 40);
        assertEquals(new TestCanvas.Fill(5, 0, 1, 40, 0xFF112233), t.render(v).fills().get(0));
        assertEquals(new Size(0, 1), new Divider().preferredSize(t.ctx()));

        ProgressBar bar = t.place(new ProgressBar(0.5f), 0, 0, 100, 4);
        TestCanvas c = t.render(bar);
        assertTrue(c.fills().stream().anyMatch(f -> f.argb() == Theme.DEFAULT.surface3() && f.w() == 100));
        assertEquals(50, c.fills().stream().filter(f -> f.argb() != Theme.DEFAULT.surface3()).mapToInt(TestCanvas.Fill::right).max().orElse(0));
        bar.setFraction(2f);
        assertEquals(1f, bar.fraction(), 1e-6f);
        t.render(bar);
        t.clock.advance(Theme.MOTION_BASE);
        TestCanvas full = t.render(bar);
        assertEquals(100, full.fills().stream().filter(f -> f.argb() != Theme.DEFAULT.surface3()).mapToInt(TestCanvas.Fill::right).max().orElse(0));
        ProgressBar spinner = t.place(ProgressBar.indeterminate(), 0, 0, 100, 4);
        assertTrue(spinner.isIndeterminate());
        TestCanvas s1 = t.render(spinner);
        t.clock.advance(700L);
        TestCanvas s2 = t.render(spinner);
        assertFalse(s1.fills().equals(s2.fills()), "indeterminate segment moves");
        ProgressBar colored = t.place(new ProgressBar(0.25f).color(Theme.DEFAULT.success()), 0, 0, 100, 4);
        assertTrue(t.render(colored).fills().stream().anyMatch(f -> f.argb() == Theme.DEFAULT.success()));
    }
}
