package dev.vanta.core.screen.common;

import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Equal-width card grid whose column count follows the available width: as many columns as fit
 * {@code minCellWidth} (capped at {@code maxColumns}), children flowing left-to-right, every row as tall as its
 * tallest child. The first measurement happens before the width is known, so the grid remeasures once it has
 * bounds and asks for another layout pass when the column count changed.
 */
public class ResponsiveGrid extends UiNode {
    private final int minCellWidth;
    private final int maxColumns;
    private int gapX;
    private int gapY;
    private int lastColumns = -1;

    /**
     * @param minCellWidth narrowest acceptable cell
     * @param maxColumns   most columns ever used (1 or more)
     * @param gap          gap between cells (both axes)
     */
    public ResponsiveGrid(int minCellWidth, int maxColumns, int gap) {
        this.minCellWidth = Math.max(1, minCellWidth);
        this.maxColumns = Math.max(1, maxColumns);
        this.gapX = Math.max(0, gap);
        this.gapY = Math.max(0, gap);
    }

    public ResponsiveGrid gap(int gx, int gy) {
        this.gapX = Math.max(0, gx);
        this.gapY = Math.max(0, gy);
        return this;
    }

    /** Columns used for a width. */
    public int columnsFor(int width) {
        int fit = (width + gapX) / (minCellWidth + gapX);
        return Math.max(1, Math.min(maxColumns, fit));
    }

    /** Columns used by the last layout (or -1). */
    public int columns() {
        return lastColumns;
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

    private int widthHint(UiContext ctx) {
        if (explicitWidth() > 0) {
            return explicitWidth();
        }
        if (bounds().w() > 0) {
            return bounds().w();
        }
        return Math.max(minCellWidth, ctx.screenWidth());
    }

    private int[] rowHeights(UiContext ctx, List<UiNode> visible, int columns, int cellW) {
        int rows = (visible.size() + columns - 1) / columns;
        int[] heights = new int[rows];
        for (int i = 0; i < visible.size(); i++) {
            UiNode child = visible.get(i);
            // Children measure against the cell width (wrapped text, nested grids).
            int previous = child.explicitWidth();
            Size pref;
            if (previous < 0 && child.bounds().w() != cellW) {
                child.setBounds(new Rect(child.bounds().x(), child.bounds().y(), cellW, child.bounds().h()));
            }
            pref = child.preferredSize(ctx);
            heights[i / columns] = Math.max(heights[i / columns], pref.h());
        }
        return heights;
    }

    @Override
    protected Size measure(UiContext ctx) {
        List<UiNode> visible = visibleChildren();
        int w = widthHint(ctx);
        if (visible.isEmpty()) {
            return new Size(w, 0);
        }
        int columns = columnsFor(w);
        int cellW = Math.max(0, (w - gapX * (columns - 1)) / columns);
        int[] heights = rowHeights(ctx, visible, columns, cellW);
        int h = 0;
        for (int rh : heights) {
            h += rh;
        }
        h += gapY * Math.max(0, heights.length - 1);
        return new Size(w, h);
    }

    @Override
    public void layout(UiContext ctx) {
        List<UiNode> visible = visibleChildren();
        Rect b = bounds();
        int columns = columnsFor(b.w());
        if (lastColumns >= 0 && lastColumns != columns) {
            ctx.requestLayout();
        }
        lastColumns = columns;
        if (visible.isEmpty()) {
            return;
        }
        int cellW = Math.max(0, (b.w() - gapX * (columns - 1)) / columns);
        int[] heights = rowHeights(ctx, visible, columns, cellW);
        int y = b.y();
        for (int i = 0; i < visible.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            if (col == 0 && row > 0) {
                y += heights[row - 1] + gapY;
            }
            UiNode child = visible.get(i);
            child.setBounds(b.x() + col * (cellW + gapX), y, cellW, heights[row]);
            child.layout(ctx);
        }
    }
}
