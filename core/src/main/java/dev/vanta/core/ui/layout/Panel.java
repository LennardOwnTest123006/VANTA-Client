package dev.vanta.core.ui.layout;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.Insets;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/**
 * A box with optional background, 1 px border, rounded corners and padding. Children fill the padded area
 * (stacked on top of each other); wrap a {@link Column} or {@link Row} for flow layouts.
 */
public class Panel extends UiNode {

    private int background = Colors.TRANSPARENT;
    private int border = Colors.TRANSPARENT;
    private int radius;
    private Insets padding = Insets.ZERO;
    private boolean clip;

    /** Empty transparent panel. */
    public Panel() {
    }

    /** Panel wrapping one child. */
    public Panel(UiNode child) {
        add(child);
    }

    /** Surface card look: {@code surface.1} background, subtle border, large radius, 12 px padding. */
    public static Panel card(Theme theme) {
        Panel p = new Panel();
        p.background = theme.panelBackground();
        p.border = theme.borderSubtle();
        p.radius = Theme.RADIUS_LG;
        p.padding = Insets.of(Theme.SPACE_5);
        return p;
    }

    /** Raised surface: {@code surface.2} background, strong border, medium radius. */
    public static Panel raised(Theme theme) {
        Panel p = new Panel();
        p.background = Colors.withAlpha(theme.surface2(), Math.min(1f, theme.panelAlpha() + 0.1f));
        p.border = theme.borderStrong();
        p.radius = Theme.RADIUS_MD;
        p.padding = Insets.of(Theme.SPACE_4);
        return p;
    }

    public Panel background(int argb) {
        this.background = argb;
        return this;
    }

    public Panel border(int argb) {
        this.border = argb;
        return this;
    }

    public Panel radius(int r) {
        this.radius = Math.max(0, r);
        return this;
    }

    public Panel padding(Insets insets) {
        this.padding = insets == null ? Insets.ZERO : insets;
        return this;
    }

    public Panel padding(int all) {
        return padding(Insets.of(all));
    }

    /** Whether children are clipped to the padded area. */
    public Panel clip(boolean clipChildren) {
        this.clip = clipChildren;
        return this;
    }

    public int background() {
        return background;
    }

    public int border() {
        return border;
    }

    public int radius() {
        return radius;
    }

    public Insets padding() {
        return padding;
    }

    /** The padded content area. */
    public Rect contentBounds() {
        return bounds().inset(padding);
    }

    @Override
    protected Size measure(UiContext ctx) {
        return measure(ctx, -1);
    }

    @Override
    protected Size measure(UiContext ctx, int availableWidth) {
        int childWidth = availableWidth < 0 ? -1 : Math.max(0, availableWidth - padding.horizontal());
        Size max = Size.ZERO;
        for (UiNode child : children()) {
            if (child.isVisible()) {
                max = max.max(child.preferredSize(ctx, childWidth));
            }
        }
        return max.plus(padding);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect inner = contentBounds();
        for (UiNode child : children()) {
            child.setBounds(inner);
            child.layout(ctx);
        }
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Rect b = bounds();
        if (Colors.alpha(background) > 0) {
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), radius, background);
        }
        if (Colors.alpha(border) > 0) {
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), radius, border);
        }
    }

    @Override
    protected void renderChildren(Canvas canvas, UiContext ctx) {
        if (clip) {
            canvas.pushScissor(contentBounds());
            super.renderChildren(canvas, ctx);
            canvas.popScissor();
        } else {
            super.renderChildren(canvas, ctx);
        }
    }
}
