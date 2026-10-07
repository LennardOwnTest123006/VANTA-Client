package dev.vanta.core.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Insets;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.UiTestSupport;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Label;
import org.junit.jupiter.api.Test;

/**
 * A wrapped {@link Label} without an explicit width must report its wrapped height in the first layout pass:
 * containers measure it against the width they are about to hand it, so a following sibling never lands on top
 * of its text (finding #11).
 */
class WrappedLabelLayoutTest {

    private final UiTestSupport t = new UiTestSupport();

    /** 30 words of 20 px: four per 100 px line (4 * 20 + 3 * 5 = 95), eight lines at 9 px. */
    private static String longText() {
        return "word ".repeat(30).trim();
    }

    private static boolean overlap(Rect a, Rect b) {
        return a.y() < b.bottom() && b.y() < a.bottom() && a.x() < b.right() && b.x() < a.right();
    }

    /**
     * The area the label's text is drawn in: its lines from the top of its bounds. Before the fix the bounds
     * were one line high while eight lines were drawn, so a bounds check alone would not catch the overlap.
     */
    private static Rect textBlock(Label label) {
        Rect b = label.bounds();
        return new Rect(b.x(), b.y(), b.w(), label.lines().size() * 9);
    }

    @Test
    void columnGivesWrappedLabelItsFullHeightInOnePass() {
        Column col = new Column(4);
        Label label = col.add(new Label(longText()).wrap(true));
        Button button = col.add(new Button("OK", null));
        t.place(col, 0, 0, 100, 200);
        assertEquals(8, label.lines().size());
        assertEquals(new Rect(0, 0, 100, 72), label.bounds(), "eight lines of 9 px after a single layout");
        assertEquals(76, button.bounds().y(), "the button follows the wrapped text");
        assertFalse(overlap(textBlock(label), button.bounds()), "the drawn text never runs through the button");
        assertEquals(72 + 4 + button.preferredSize(t.ctx()).h(), col.preferredSize(t.ctx(), 100).h(),
                "measuring the column for a known width wraps its label the same way");
        Label fresh = new Label(longText()).wrap(true);
        assertEquals(9, fresh.preferredSize(t.ctx()).h(), "without any width hint a fresh wrapped label measures as one line");
        assertEquals(new Size(95, 72), fresh.preferredSize(t.ctx(), 100), "offered width: wrapped, natural line width");
    }

    @Test
    void columnAlignmentKeepsShortWrappedLabelsAtTheirTextWidth() {
        Column col = new Column(4).align(Align.START);
        Label label = col.add(new Label("ab cd").wrap(true));
        t.place(col, 0, 0, 100, 50);
        assertEquals(new Rect(0, 0, 25, 9), label.bounds(), "fits on one line: natural width, no wrap");
        Column padded = new Column(0).padding(Insets.of(0, 10));
        Label inner = padded.add(new Label(longText()).wrap(true));
        t.place(padded, 0, 0, 120, 200);
        assertEquals(new Rect(10, 0, 100, 72), inner.bounds(), "padding is taken off the offered width");
    }

    @Test
    void rowMeasuresFlexWrappedLabelAgainstItsShare() {
        Row row = new Row(0).align(Align.START);
        Label label = row.add(new Label(longText()).wrap(true));
        label.flex(1f);
        Button button = row.add(new Button("OK", null));
        int buttonW = button.preferredSize(t.ctx()).w();
        assertEquals(button.preferredSize(t.ctx()).h(), row.preferredSize(t.ctx()).h(),
                "without a width a fresh row keeps the single-line estimate: the 20 px button is the tallest child");
        int rowH = row.preferredSize(t.ctx(), 100 + buttonW).h();
        assertEquals(72, rowH, "the row is as tall as the label wrapped to its flex share");
        t.place(row, 0, 0, 100 + buttonW, rowH);
        assertEquals(new Rect(0, 0, 100, 72), label.bounds());
        assertEquals(8, label.lines().size());
    }

    @Test
    void scrollPanelAccountsForTheScrollbarColumnInOnePass() {
        Column content = new Column(4);
        Label label = content.add(new Label(longText()).wrap(true));
        for (int i = 0; i < 3; i++) {
            content.add(Spacer.fixed(10, 30));
        }
        ScrollPanel panel = new ScrollPanel(content);
        t.place(panel, 0, 0, 100, 100);
        int contentW = 100 - Scrollbar.reservedWidth();
        assertEquals(contentW, content.bounds().w());
        assertEquals(label.lines().size() * 9, label.bounds().h(), "label height matches its lines at the narrow width");
        assertEquals(label.bounds().h() + 3 * 34, panel.contentHeight(), "content height was measured at the narrow width");
        assertTrue(panel.isScrollable());
    }

    @Test
    void cardAndPanelPassTheirWidthDown() {
        Card card = new Card();
        Label label = card.add(new Label(longText()).wrap(true));
        Button button = card.add(new Button("OK", null));
        t.place(card, 0, 0, 124, 300);
        assertEquals(8, label.lines().size(), "12 px body padding leaves 100 px");
        assertFalse(overlap(textBlock(label), button.bounds()), "the drawn text never runs through the button");
        assertEquals(card.body().preferredSize(t.ctx(), 124).h(), card.preferredSize(t.ctx(), 124).h());

        Panel panel = new Panel().padding(Insets.of(0, 12));
        Column col = panel.add(new Column(4));
        Label inner = col.add(new Label(longText()).wrap(true));
        Button b2 = col.add(new Button("OK", null));
        t.place(panel, 0, 0, 124, 300);
        assertEquals(new Rect(12, 0, 100, 72), inner.bounds());
        assertFalse(overlap(textBlock(inner), b2.bounds()));
        assertEquals(72 + 4 + b2.preferredSize(t.ctx()).h(), panel.preferredSize(t.ctx(), 124).h());
    }

    @Test
    void explicitWidthStillWinsOverTheOfferedWidth() {
        Column col = new Column(4);
        Label label = col.add(new Label("aaaa bbbb cccc").wrap(true));
        label.width(50);
        t.place(col, 0, 0, 200, 100);
        assertEquals(new Size(50, 18), label.preferredSize(t.ctx(), 200));
        assertEquals(18, label.bounds().h());
    }
}
