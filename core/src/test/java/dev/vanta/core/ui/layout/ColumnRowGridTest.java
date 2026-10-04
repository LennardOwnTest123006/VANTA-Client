package dev.vanta.core.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vanta.core.ui.Insets;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.UiTestSupport;
import org.junit.jupiter.api.Test;

class ColumnRowGridTest {

    private final UiTestSupport t = new UiTestSupport();

    private static Spacer box(int w, int h) {
        return Spacer.fixed(w, h);
    }

    @Test
    void columnStacksWithGapsAndFlex() {
        Column col = new Column(4);
        Spacer a = col.add(box(30, 10));
        Spacer b = col.add(box(50, 20));
        Spacer grow = col.add(Spacer.grow());
        t.place(col, 0, 0, 100, 100);
        assertEquals(new Rect(0, 0, 100, 10), a.bounds(), "stretch fills the width");
        assertEquals(new Rect(0, 14, 100, 20), b.bounds());
        assertEquals(new Rect(0, 38, 100, 62), grow.bounds());
        assertEquals(new Size(50, 30 + 8), col.preferredSize(t.ctx()), "flex children measure as zero but keep their gap");
    }

    @Test
    void columnAlignmentAndJustify() {
        Column col = new Column(4).align(Align.START);
        Spacer a = col.add(box(30, 10));
        t.place(col, 10, 10, 100, 100);
        assertEquals(new Rect(10, 10, 30, 10), a.bounds());
        col.align(Align.CENTER);
        col.layout(t.ctx());
        assertEquals(new Rect(45, 10, 30, 10), a.bounds());
        col.align(Align.END);
        col.layout(t.ctx());
        assertEquals(new Rect(80, 10, 30, 10), a.bounds());
        col.justify(Justify.END);
        col.layout(t.ctx());
        assertEquals(100, a.bounds().y());
        col.justify(Justify.CENTER);
        col.layout(t.ctx());
        assertEquals(55, a.bounds().y());
        Spacer b = col.add(box(30, 10));
        col.justify(Justify.SPACE_BETWEEN);
        col.layout(t.ctx());
        assertEquals(10, a.bounds().y());
        assertEquals(100, b.bounds().y());
    }

    @Test
    void columnSkipsInvisibleChildrenAndHonoursPadding() {
        Column col = new Column(2).padding(Insets.of(5));
        Spacer a = col.add(box(10, 10));
        Spacer hidden = col.add(box(10, 10));
        hidden.setVisible(false);
        Spacer b = col.add(box(10, 10));
        t.place(col, 0, 0, 50, 50);
        assertEquals(new Rect(5, 5, 40, 10), a.bounds());
        assertEquals(new Rect(5, 17, 40, 10), b.bounds());
        assertEquals(new Size(20, 32), col.preferredSize(t.ctx()));
    }

    @Test
    void rowFlowsHorizontallyAndSharesFlex() {
        Row row = new Row(6);
        Spacer a = row.add(box(20, 10));
        Spacer f1 = row.add(Spacer.grow(1f));
        Spacer f2 = row.add(Spacer.grow(3f));
        t.place(row, 0, 0, 100, 30);
        assertEquals(new Rect(0, 10, 20, 10), a.bounds(), "center aligned on the cross axis");
        assertEquals(new Rect(26, 15, 17, 0), f1.bounds());
        assertEquals(new Rect(49, 15, 51, 0), f2.bounds());
        row.align(Align.STRETCH);
        row.layout(t.ctx());
        assertEquals(new Rect(0, 0, 20, 30), a.bounds());
        row.align(Align.END);
        row.layout(t.ctx());
        assertEquals(20, a.bounds().y());
        assertEquals(new Size(20 + 12, 10), row.preferredSize(t.ctx()));
    }

    @Test
    void rowJustifyWithoutFlex() {
        Row row = new Row(0).justify(Justify.END);
        Spacer a = row.add(box(20, 10));
        t.place(row, 0, 0, 100, 10);
        assertEquals(80, a.bounds().x());
        row.justify(Justify.CENTER);
        row.layout(t.ctx());
        assertEquals(40, a.bounds().x());
    }

    @Test
    void gridFillsColumnsThenRows() {
        Grid grid = new Grid(3).gap(2, 4);
        Spacer[] cells = new Spacer[5];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = grid.add(box(10, 10));
        }
        t.place(grid, 0, 0, 100, 60);
        int cellW = (100 - 4) / 3;
        assertEquals(new Rect(0, 0, cellW, 10), cells[0].bounds());
        assertEquals(new Rect(cellW + 2, 0, cellW, 10), cells[1].bounds());
        assertEquals(new Rect(0, 14, cellW, 10), cells[3].bounds());
        assertEquals(new Size(10 * 3 + 4, 24), grid.preferredSize(t.ctx()));
        grid.rowHeight(20);
        grid.layout(t.ctx());
        assertEquals(20, cells[0].bounds().h());
        assertEquals(24, cells[3].bounds().y());
        assertEquals(3, grid.columns());
    }

    @Test
    void stackAndPanelFillChildren() {
        Stack stack = new Stack().padding(Insets.of(2));
        Spacer a = stack.add(box(10, 10));
        Spacer b = stack.add(box(30, 5));
        t.place(stack, 0, 0, 50, 50);
        assertEquals(new Rect(2, 2, 46, 46), a.bounds());
        assertEquals(a.bounds(), b.bounds());
        assertEquals(new Size(34, 14), stack.preferredSize(t.ctx()));

        Panel panel = new Panel().padding(Insets.of(3, 7));
        Spacer c = panel.add(box(10, 10));
        t.place(panel, 0, 0, 50, 50);
        assertEquals(new Rect(7, 3, 36, 44), c.bounds());
        assertEquals(new Rect(7, 3, 36, 44), panel.contentBounds());
        assertEquals(new Size(24, 16), panel.preferredSize(t.ctx()));
    }
}
