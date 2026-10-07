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

    @Test
    void nestedColumnsTakeTheirPaddingOffTheOfferedWidth() {
        Column outer = new Column(2).padding(Insets.of(10));
        Column mid = outer.add(new Column(2).padding(Insets.of(5)));
        Column inner = mid.add(new Column(2));
        Label deep = inner.add(new Label(longText()).wrap(true));
        Button b1 = inner.add(new Button("A", null));
        Label middle = mid.add(new Label(longText()).wrap(true));
        Button b2 = outer.add(new Button("B", null));
        t.place(outer, 0, 0, 130, 400);
        assertEquals(new Rect(15, 15, 100, 72), deep.bounds(), "10 + 5 px of padding on each side");
        assertEquals(15 + 72 + 2, b1.bounds().y());
        assertEquals(8, middle.lines().size());
        assertEquals(middle.lines().size() * 9, middle.bounds().h());
        assertFalse(overlap(textBlock(deep), b1.bounds()));
        assertFalse(overlap(textBlock(middle), b1.bounds()));
        assertFalse(overlap(textBlock(middle), b2.bounds()));
        assertEquals(b2.bounds().bottom() + 10, outer.preferredSize(t.ctx(), 130).h());
    }

    @Test
    void flexChildOfAColumnGetsWhatIsLeftAfterTheWrappedLabel() {
        Column col = new Column(0);
        Label label = col.add(new Label(longText()).wrap(true));
        Spacer grow = col.add(Spacer.grow());
        Button button = col.add(new Button("OK", null));
        t.place(col, 0, 0, 100, 200);
        assertEquals(new Rect(0, 0, 100, 72), label.bounds());
        assertEquals(new Rect(0, 72, 100, 108), grow.bounds(), "the free height is what remains after eight lines");
        assertEquals(180, button.bounds().y());
    }

    @Test
    void relayoutAtAnotherWidthRewrapsInsteadOfReusingTheOldBounds() {
        Column col = new Column(4);
        Label label = col.add(new Label(longText()).wrap(true));
        Button button = col.add(new Button("OK", null));
        t.place(col, 0, 0, 200, 400);
        assertEquals(4, label.lines().size());
        t.place(col, 0, 0, 100, 400);
        assertEquals(8, label.lines().size());
        assertEquals(76, button.bounds().y());
        t.place(col, 0, 0, 300, 400);
        assertEquals(3, label.lines().size());
        assertEquals(new Rect(0, 0, 300, 27), label.bounds());
        assertEquals(31, button.bounds().y());
    }

    @Test
    void scrollPanelKeepsTheFullWidthWhenTheContentFits() {
        Column content = new Column(0);
        Label label = content.add(new Label("word ".repeat(8).trim()).wrap(true));
        ScrollPanel panel = new ScrollPanel(content);
        t.place(panel, 0, 0, 100, 100);
        assertFalse(panel.isScrollable());
        assertEquals(100, content.bounds().w());
        assertEquals(2, label.lines().size());
        // 44 words are 11 lines of 9 px at 100 px: they fit the 100 px view exactly, so no scrollbar is reserved
        // and a second pass gives the same answer.
        Column edge = new Column(0);
        Label edgeLabel = edge.add(new Label("word ".repeat(44).trim()).wrap(true));
        ScrollPanel edgePanel = new ScrollPanel(edge);
        t.place(edgePanel, 0, 0, 100, 100);
        assertFalse(edgePanel.isScrollable());
        assertEquals(new Rect(0, 0, 100, 99), edgeLabel.bounds());
        edgePanel.layout(t.ctx());
        assertFalse(edgePanel.isScrollable());
        assertEquals(new Rect(0, 0, 100, 99), edgeLabel.bounds());
    }

    @Test
    void scrollPanelAlwaysReservingTheScrollbarMeasuresAtTheNarrowWidth() {
        Column content = new Column(0);
        Label label = content.add(new Label(longText()).wrap(true));
        ScrollPanel panel = new ScrollPanel(content).alwaysReserveScrollbar(true);
        t.place(panel, 0, 0, 100 + Scrollbar.reservedWidth(), 300);
        assertEquals(new Rect(0, 0, 100, 72), label.bounds());
    }

    @Test
    void cardInsideAScrollPanelInsideAColumnWrapsInOnePass() {
        // The About screen's shape: a page column with a flex scroll panel over cards of wrapped captions.
        Column page = new Column(0);
        Column content = new Column(8);
        ScrollPanel panel = page.add(new ScrollPanel(content));
        panel.flex(1f);
        Card card = content.add(new Card("Title"));
        Label first = card.add(new Label("word ".repeat(60).trim()).wrap(true));
        Label second = card.add(new Label("word ".repeat(60).trim()).wrap(true));
        Button button = card.add(new Button("OK", null));
        Button footer = page.add(new Button("Back", null));
        t.place(page, 0, 0, 124 + Scrollbar.reservedWidth(), 150);
        assertTrue(panel.isScrollable());
        assertEquals(15, first.lines().size(), "60 words, four per 100 px line");
        assertEquals(new Rect(12, 36, 100, 135), first.bounds());
        assertEquals(15 * 9, second.bounds().h());
        assertFalse(overlap(textBlock(first), second.bounds()));
        assertFalse(overlap(textBlock(second), button.bounds()));
        assertEquals(130, footer.bounds().y(), "the footer keeps its place below the scroll panel");
    }

    @Test
    void largeTextLongWordsNewlinesAndMaxLinesMeasureLikeTheyWrap() {
        Column col = new Column(4);
        Label big = col.add(new Label("word ".repeat(2000).trim()).wrap(true));
        Label hugeWord = col.add(new Label("x".repeat(100)).wrap(true));
        Label paragraphs = col.add(new Label("a\n\nb").wrap(true));
        Label empty = col.add(new Label("").wrap(true));
        Label capped = col.add(new Label(longText()).wrap(true).maxLines(2));
        Button button = col.add(new Button("OK", null));
        t.place(col, 0, 0, 100, 10000);
        assertEquals(500, big.lines().size());
        assertEquals(4500, big.bounds().h());
        assertEquals(5, hugeWord.lines().size(), "a 500 px word is split by character into 100 px lines");
        assertEquals(45, hugeWord.bounds().h());
        assertEquals(3, paragraphs.lines().size());
        assertEquals(27, paragraphs.bounds().h());
        assertEquals(9, empty.bounds().h());
        assertEquals(2, capped.lines().size());
        assertEquals(18, capped.bounds().h(), "maxLines caps the measured height too");
        assertEquals(4500 + 4 + 45 + 4 + 27 + 4 + 9 + 4 + 18 + 4, button.bounds().y());
    }

    @Test
    void gridMeasuresEveryCellAtTheCellWidth() {
        Grid grid = new Grid(2).gap(10, 4);
        Label a = grid.add(new Label(longText()).wrap(true));
        Label b = grid.add(new Label("short").wrap(true));
        Button c = grid.add(new Button("OK", null));
        t.place(grid, 0, 0, 210, 400);
        assertEquals(new Rect(0, 0, 100, 72), a.bounds());
        assertEquals(new Rect(110, 0, 100, 72), b.bounds(), "the row is as tall as its wrapped cell");
        assertEquals(76, c.bounds().y());
        assertEquals(96, grid.preferredSize(t.ctx(), 210).h());
    }

    @Test
    void stackPassesItsInnerWidthDown() {
        Stack stack = new Stack().padding(Insets.of(0, 10));
        stack.add(new Label(longText()).wrap(true));
        assertEquals(new Size(115, 72), stack.preferredSize(t.ctx(), 120));
    }

    @Test
    void rowSharesTheWidthBetweenFlexLabels() {
        Row row = new Row(0).align(Align.START);
        Label a = row.add(new Label(longText()).wrap(true));
        a.flex(1f);
        Label b = row.add(new Label(longText()).wrap(true));
        b.flex(1f);
        int h = row.preferredSize(t.ctx(), 200).h();
        assertEquals(72, h);
        t.place(row, 0, 0, 200, h);
        assertEquals(new Rect(0, 0, 100, 72), a.bounds());
        assertEquals(new Rect(100, 0, 100, 72), b.bounds());

        Row padded = new Row(4).align(Align.START).padding(Insets.of(0, 3));
        Button button = padded.add(new Button("OK", null));
        Label label = padded.add(new Label(longText()).wrap(true));
        label.flex(1f);
        int buttonW = button.preferredSize(t.ctx()).w();
        int width = 6 + buttonW + 4 + 100;
        assertEquals(72, padded.preferredSize(t.ctx(), width).h(), "padding, the fixed sibling and the gap come off first");
        t.place(padded, 0, 0, width, 72);
        assertEquals(new Rect(3 + buttonW + 4, 0, 100, 72), label.bounds());
    }

    @Test
    void fixedRowChildStillNeedsAnExplicitWidthToWrap() {
        // A fixed (non-flex) Row child is measured at its natural width, as before the fix: a wrapped label there
        // is one line wide. With an explicit width it wraps and the row grows with it.
        Row natural = new Row(4);
        Label single = natural.add(new Label(longText()).wrap(true));
        t.place(natural, 0, 0, 100, 40);
        assertEquals(1, single.lines().size());
        Row row = new Row(4).align(Align.START);
        Label label = row.add(new Label(longText()).wrap(true));
        label.width(60);
        Button button = row.add(new Button("OK", null));
        int h = row.preferredSize(t.ctx(), 100).h();
        assertEquals(15 * 9, h, "two 20 px words per 60 px line");
        t.place(row, 0, 0, 100, h);
        assertEquals(new Rect(0, 0, 60, 135), label.bounds());
        assertFalse(overlap(textBlock(label), button.bounds()));
    }
}
