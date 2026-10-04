package dev.vanta.core.ui.layout;

import dev.vanta.core.ui.Insets;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Equal-width column grid. Children flow left-to-right, top-to-bottom; each row is as tall as its tallest
 * child unless a fixed row height is set.
 */
public class Grid extends UiNode {

    private final int columns;
    private int gapX;
    private int gapY;
    private int rowHeight = -1;
    private Insets padding = Insets.ZERO;

    /** Grid with {@code columns} columns (at least 1). */
    public Grid(int columns) {
        this.columns = Math.max(1, columns);
    }

    public Grid gap(int gx, int gy) {
        this.gapX = Math.max(0, gx);
        this.gapY = Math.max(0, gy);
        return this;
    }

    public Grid gap(int all) {
        return gap(all, all);
    }

    /** Fixed row height, or -1 to use the tallest child in each row. */
    public Grid rowHeight(int h) {
        this.rowHeight = h;
        return this;
    }

    public Grid padding(Insets insets) {
        this.padding = insets == null ? Insets.ZERO : insets;
        return this;
    }

    public int columns() {
        return columns;
    }

    private List<UiNode> visibleChildren() {
        List<UiNode> out = new ArrayList<>();
        for (UiNode c : children()) {
            if (c.isVisible()) {
                out.add(c);
            }
        }
        return out;
    }

    private int[] rowHeights(UiContext ctx, List<UiNode> visible) {
        int rows = (visible.size() + columns - 1) / columns;
        int[] heights = new int[rows];
        for (int i = 0; i < visible.size(); i++) {
            int row = i / columns;
            int h = rowHeight >= 0 ? rowHeight : visible.get(i).preferredSize(ctx).h();
            heights[row] = Math.max(heights[row], h);
        }
        return heights;
    }

    @Override
    protected Size measure(UiContext ctx) {
        List<UiNode> visible = visibleChildren();
        if (visible.isEmpty()) {
            return Size.ZERO.plus(padding);
        }
        int cellW = 0;
        for (UiNode c : visible) {
            cellW = Math.max(cellW, c.preferredSize(ctx).w());
        }
        int[] heights = rowHeights(ctx, visible);
        int h = 0;
        for (int rh : heights) {
            h += rh;
        }
        h += gapY * Math.max(0, heights.length - 1);
        int w = cellW * columns + gapX * (columns - 1);
        return new Size(w, h).plus(padding);
    }

    @Override
    public void layout(UiContext ctx) {
        List<UiNode> visible = visibleChildren();
        if (visible.isEmpty()) {
            return;
        }
        Rect inner = bounds().inset(padding);
        int cellW = Math.max(0, (inner.w() - gapX * (columns - 1)) / columns);
        int[] heights = rowHeights(ctx, visible);
        int y = inner.y();
        for (int i = 0; i < visible.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            if (col == 0 && row > 0) {
                y += heights[row - 1] + gapY;
            }
            int x = inner.x() + col * (cellW + gapX);
            UiNode child = visible.get(i);
            child.setBounds(x, y, cellW, heights[row]);
            child.layout(ctx);
        }
    }
}
