package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/** 1 px separator line, horizontal by default. Takes the full cross size of its container. */
public class Divider extends UiNode {

    private final boolean vertical;
    private Integer color;

    /** Horizontal divider. */
    public Divider() {
        this(false);
    }

    /** Horizontal or vertical divider. */
    public Divider(boolean vertical) {
        this.vertical = vertical;
    }

    /** Explicit color (defaults to {@code border.subtle}). */
    public Divider color(int argb) {
        this.color = argb;
        return this;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return vertical ? new Size(1, 0) : new Size(0, 1);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Rect b = bounds();
        int c = color != null ? color : ctx.theme().borderSubtle();
        if (vertical) {
            canvas.fill(b.x(), b.y(), 1, b.h(), c);
        } else {
            canvas.fill(b.x(), b.y(), b.w(), 1, c);
        }
    }
}
