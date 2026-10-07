package dev.vanta.core.ui.layout;

import dev.vanta.core.ui.Insets;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/**
 * Lays children out vertically. Children with {@code flex() > 0} share the remaining height; others take their
 * preferred height. Cross-axis alignment defaults to {@link Align#STRETCH}.
 */
public class Column extends UiNode {

    private int gap;
    private Align align = Align.STRETCH;
    private Justify justify = Justify.START;
    private Insets padding = Insets.ZERO;

    /** Column with no gap. */
    public Column() {
    }

    /** Column with the given gap between children. */
    public Column(int gap) {
        this.gap = Math.max(0, gap);
    }

    public Column gap(int newGap) {
        this.gap = Math.max(0, newGap);
        return this;
    }

    public Column align(Align newAlign) {
        this.align = newAlign == null ? Align.STRETCH : newAlign;
        return this;
    }

    public Column justify(Justify newJustify) {
        this.justify = newJustify == null ? Justify.START : newJustify;
        return this;
    }

    public Column padding(Insets insets) {
        this.padding = insets == null ? Insets.ZERO : insets;
        return this;
    }

    public Column padding(int all) {
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
        return measure(ctx, -1);
    }

    /** Children are measured against the inner width (the width they get at layout) when it is known. */
    @Override
    protected Size measure(UiContext ctx, int availableWidth) {
        int childWidth = availableWidth < 0 ? -1 : Math.max(0, availableWidth - padding.horizontal());
        int w = 0;
        int h = 0;
        int visible = 0;
        for (UiNode child : children()) {
            if (!child.isVisible()) {
                continue;
            }
            Size s = child.preferredSize(ctx, childWidth);
            w = Math.max(w, s.w());
            h += s.h();
            visible++;
        }
        if (visible > 1) {
            h += gap * (visible - 1);
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
                fixed += child.preferredSize(ctx, inner.w()).h();
            }
        }
        int gaps = visible > 1 ? gap * (visible - 1) : 0;
        int free = Math.max(0, inner.h() - fixed - gaps);
        int y = inner.y();
        int extraGap = 0;
        if (flexTotal <= 0f) {
            switch (justify) {
                case CENTER -> y += free / 2;
                case END -> y += free;
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
            Size pref = child.preferredSize(ctx, inner.w());
            int h;
            if (child.flex() > 0f) {
                flexUsed += child.flex();
                int target = Math.round(free * flexUsed / flexTotal);
                h = target - flexGiven;
                flexGiven = target;
            } else {
                h = pref.h();
            }
            int w;
            int x;
            switch (align) {
                case START -> {
                    w = Math.min(pref.w(), inner.w());
                    x = inner.x();
                }
                case CENTER -> {
                    w = Math.min(pref.w(), inner.w());
                    x = inner.x() + (inner.w() - w) / 2;
                }
                case END -> {
                    w = Math.min(pref.w(), inner.w());
                    x = inner.right() - w;
                }
                default -> {
                    w = inner.w();
                    x = inner.x();
                }
            }
            child.setBounds(x, y, w, h);
            child.layout(ctx);
            y += h + gap + extraGap;
        }
    }
}
