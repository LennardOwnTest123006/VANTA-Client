package dev.vanta.core.ui.layout;

import dev.vanta.core.ui.Insets;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/**
 * Lays children out horizontally. Children with {@code flex() > 0} share the remaining width; others take their
 * preferred width. Cross-axis alignment defaults to {@link Align#CENTER}.
 */
public class Row extends UiNode {

    private int gap;
    private Align align = Align.CENTER;
    private Justify justify = Justify.START;
    private Insets padding = Insets.ZERO;

    /** Row with no gap. */
    public Row() {
    }

    /** Row with the given gap between children. */
    public Row(int gap) {
        this.gap = Math.max(0, gap);
    }

    public Row gap(int newGap) {
        this.gap = Math.max(0, newGap);
        return this;
    }

    public Row align(Align newAlign) {
        this.align = newAlign == null ? Align.CENTER : newAlign;
        return this;
    }

    public Row justify(Justify newJustify) {
        this.justify = newJustify == null ? Justify.START : newJustify;
        return this;
    }

    public Row padding(Insets insets) {
        this.padding = insets == null ? Insets.ZERO : insets;
        return this;
    }

    public Row padding(int all) {
        return padding(Insets.of(all));
    }

    public int gap() {
        return gap;
    }

    public Align align() {
        return align;
    }

    @Override
    protected Size measure(UiContext ctx) {
        int w = 0;
        int h = 0;
        int visible = 0;
        for (UiNode child : children()) {
            if (!child.isVisible()) {
                continue;
            }
            Size s = child.preferredSize(ctx);
            h = Math.max(h, s.h());
            w += s.w();
            visible++;
        }
        if (visible > 1) {
            w += gap * (visible - 1);
        }
        return new Size(w, h).plus(padding);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect inner = bounds().inset(padding);
        int fixed = 0;
        float flexTotal = 0f;
        int visible = 0;
        for (UiNode child : children()) {
            if (!child.isVisible()) {
                continue;
            }
            visible++;
            if (child.flex() > 0f) {
                flexTotal += child.flex();
            } else {
                fixed += child.preferredSize(ctx).w();
            }
        }
        int gaps = visible > 1 ? gap * (visible - 1) : 0;
        int free = Math.max(0, inner.w() - fixed - gaps);
        int x = inner.x();
        int extraGap = 0;
        if (flexTotal <= 0f) {
            switch (justify) {
                case CENTER -> x += free / 2;
                case END -> x += free;
                case SPACE_BETWEEN -> extraGap = visible > 1 ? free / (visible - 1) : 0;
                default -> {
                }
            }
        }
        float flexUsed = 0f;
        int flexGiven = 0;
        for (UiNode child : children()) {
            if (!child.isVisible()) {
                continue;
            }
            Size pref = child.preferredSize(ctx);
            int w;
            if (child.flex() > 0f) {
                flexUsed += child.flex();
                int target = Math.round(free * flexUsed / flexTotal);
                w = target - flexGiven;
                flexGiven = target;
            } else {
                w = pref.w();
            }
            int h;
            int y;
            switch (align) {
                case START -> {
                    h = Math.min(pref.h(), inner.h());
                    y = inner.y();
                }
                case CENTER -> {
                    h = Math.min(pref.h(), inner.h());
                    y = inner.y() + (inner.h() - h) / 2;
                }
                case END -> {
                    h = Math.min(pref.h(), inner.h());
                    y = inner.bottom() - h;
                }
                default -> {
                    h = inner.h();
                    y = inner.y();
                }
            }
            child.setBounds(x, y, w, h);
            child.layout(ctx);
            x += w + gap + extraGap;
        }
    }
}
