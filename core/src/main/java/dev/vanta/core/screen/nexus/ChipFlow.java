package dev.vanta.core.screen.nexus;

import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import java.util.ArrayList;
import java.util.List;

/**
 * A row of chips (compact buttons) that wraps onto further lines when the width runs out, so every chip stays
 * clickable in a narrow window. Children keep their preferred size. Like {@code ResponsiveGrid} it measures against
 * the width the parent offers and asks for another layout pass when the number of lines changed.
 */
public final class ChipFlow extends UiNode {
    private final int gapX;
    private final int gapY;
    private int lastLines = -1;

    /** Flow with the standard gaps. */
    public ChipFlow() {
        this(Theme.SPACE_3, Theme.SPACE_2);
    }

    public ChipFlow(int gapX, int gapY) {
        this.gapX = gapX;
        this.gapY = gapY;
    }

    private List<UiNode> visibleChildren() {
        List<UiNode> out = new ArrayList<>();
        for (UiNode child : children()) {
            if (child.isVisible()) {
                out.add(child);
            }
        }
        return out;
    }

    private int widthHint(UiContext ctx, int availableWidth) {
        if (explicitWidth() > 0) {
            return explicitWidth();
        }
        if (availableWidth > 0) {
            return availableWidth;
        }
        return bounds().w() > 0 ? bounds().w() : ctx.screenWidth();
    }

    /** Positions of the children for a width: one rectangle per visible child, relative to (0, 0). */
    private List<Rect> arrange(UiContext ctx, int width, List<UiNode> visible) {
        List<Rect> out = new ArrayList<>(visible.size());
        int x = 0;
        int y = 0;
        int lineH = 0;
        for (UiNode child : visible) {
            Size s = child.preferredSize(ctx);
            int w = Math.min(s.w(), Math.max(1, width));
            if (x > 0 && x + w > width) {
                x = 0;
                y += lineH + gapY;
                lineH = 0;
            }
            out.add(new Rect(x, y, w, s.h()));
            x += w + gapX;
            lineH = Math.max(lineH, s.h());
        }
        return out;
    }

    private static int linesOf(List<Rect> rects) {
        int lines = 0;
        int lastY = Integer.MIN_VALUE;
        for (Rect r : rects) {
            if (r.y() != lastY) {
                lines++;
                lastY = r.y();
            }
        }
        return lines;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return measure(ctx, -1);
    }

    @Override
    protected Size measure(UiContext ctx, int availableWidth) {
        List<UiNode> visible = visibleChildren();
        int w = widthHint(ctx, availableWidth);
        if (visible.isEmpty()) {
            return new Size(w, 0);
        }
        int h = 0;
        for (Rect r : arrange(ctx, w, visible)) {
            h = Math.max(h, r.bottom());
        }
        return new Size(w, h);
    }

    @Override
    public void layout(UiContext ctx) {
        List<UiNode> visible = visibleChildren();
        Rect b = bounds();
        List<Rect> rects = arrange(ctx, b.w(), visible);
        int lines = linesOf(rects);
        if (lines != lastLines) {
            ctx.requestLayout();
        }
        lastLines = lines;
        for (int i = 0; i < visible.size(); i++) {
            Rect r = rects.get(i);
            UiNode child = visible.get(i);
            child.setBounds(b.x() + r.x(), b.y() + r.y(), r.w(), r.h());
            child.layout(ctx);
        }
    }
}
