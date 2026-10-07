package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Insets;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Column;

/**
 * Surface container with an optional header (title + caption + accent bar) and a {@link Column} body.
 * Add content with {@link #add(UiNode)}; it goes into the body.
 */
public class Card extends UiNode {

    /** Header height when a title is set. */
    public static final int HEADER_H = 24;
    private static final Insets BODY_PADDING = Insets.of(Theme.SPACE_5);

    private final Column body;
    private String title;
    private String caption;
    private boolean accentBar = true;
    private boolean clip;
    private UiNode headerSlot;

    /** Card without a header. */
    public Card() {
        this(null);
    }

    /** Card with a header title. */
    public Card(String title) {
        this.title = title;
        this.body = new Column(Theme.SPACE_4).padding(BODY_PADDING);
        super.add(body);
    }

    public Card title(String t) {
        this.title = t;
        return this;
    }

    public String title() {
        return title;
    }

    /** Secondary text under the title. */
    public Card caption(String c) {
        this.caption = c;
        return this;
    }

    /** Whether the gradient bar is drawn at the left of the header. */
    public Card accentBar(boolean on) {
        this.accentBar = on;
        return this;
    }

    /**
     * Whether the body and header slot are clipped to the card. Content that overflows the body is then cut off at
     * the card's edge instead of being drawn over the neighbours; a node drawn outside its parent can never be
     * clicked anyway, so clipping makes the overflow visible as what it is.
     */
    public Card clip(boolean clipChildren) {
        this.clip = clipChildren;
        return this;
    }

    /** Whether the children are clipped to the card. */
    public boolean clip() {
        return clip;
    }

    /** Node shown at the right end of the header (e.g. a toggle or badge). */
    public Card headerSlot(UiNode node) {
        if (headerSlot != null) {
            super.remove(headerSlot);
        }
        this.headerSlot = node;
        if (node != null) {
            super.add(node);
        }
        return this;
    }

    /** The body column. */
    public Column body() {
        return body;
    }

    /** Adds a node to the body. */
    @Override
    public <T extends UiNode> T add(T child) {
        return body.add(child);
    }

    private boolean hasHeader() {
        return title != null && !title.isEmpty();
    }

    private int headerHeight(UiContext ctx) {
        if (!hasHeader()) {
            return 0;
        }
        int h = HEADER_H;
        if (caption != null && !caption.isEmpty()) {
            h += ctx.lineHeight(FontKind.UI);
        }
        return h;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return measure(ctx, -1);
    }

    /** The body is measured at the card's width (it spans the card at layout). */
    @Override
    protected Size measure(UiContext ctx, int availableWidth) {
        Size b = body.preferredSize(ctx, availableWidth);
        int w = b.w();
        if (hasHeader()) {
            int titleW = ctx.textWidth(title, FontKind.UI_BOLD) + Theme.SPACE_5 * 2 + (accentBar ? Theme.SPACE_2 : 0);
            if (headerSlot != null) {
                titleW += headerSlot.preferredSize(ctx).w() + Theme.SPACE_3;
            }
            w = Math.max(w, titleW);
        }
        return new Size(w, b.h() + headerHeight(ctx));
    }

    @Override
    public void layout(UiContext ctx) {
        Rect r = bounds();
        int hh = headerHeight(ctx);
        if (headerSlot != null) {
            headerSlot.setVisible(hasHeader());
            Size s = headerSlot.preferredSize(ctx);
            headerSlot.setBounds(r.right() - Theme.SPACE_5 - s.w(), r.y() + (HEADER_H - s.h()) / 2 + Theme.SPACE_1,
                    s.w(), s.h());
            headerSlot.layout(ctx);
        }
        body.setBounds(r.x(), r.y() + hh, r.w(), Math.max(0, r.h() - hh));
        body.layout(ctx);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect r = bounds();
        canvas.fillRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_LG, theme.panelBackground());
        canvas.strokeRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_LG, theme.borderSubtle());
        if (!hasHeader()) {
            return;
        }
        int hh = headerHeight(ctx);
        int x = r.x() + Theme.SPACE_5;
        int titleY = r.y() + Theme.SPACE_2 + (HEADER_H - Theme.SPACE_2 * 2 - canvas.lineHeight(FontKind.UI_BOLD)) / 2 + 1;
        if (accentBar) {
            canvas.fillGradientV(x, titleY - 1, 2, canvas.lineHeight(FontKind.UI_BOLD) + 1, theme.gradientStart(),
                    theme.gradientEnd());
            x += Theme.SPACE_3;
        }
        int maxW = r.right() - Theme.SPACE_5 - x - (headerSlot != null ? headerSlot.bounds().w() + Theme.SPACE_3 : 0);
        canvas.text(canvas.textClipped(title, maxW, FontKind.UI_BOLD), x, titleY, theme.textPrimary(), FontKind.UI_BOLD,
                false);
        if (caption != null && !caption.isEmpty()) {
            canvas.text(canvas.textClipped(caption, maxW, FontKind.UI), x, titleY + canvas.lineHeight(FontKind.UI_BOLD),
                    theme.textMuted(), FontKind.UI, false);
        }
        canvas.fill(r.x() + 1, r.y() + hh, r.w() - 2, 1, Colors.withAlpha(theme.borderSubtle(), 0.9f));
    }

    @Override
    protected void renderChildren(Canvas canvas, UiContext ctx) {
        if (clip) {
            canvas.pushScissor(bounds());
            super.renderChildren(canvas, ctx);
            canvas.popScissor();
        } else {
            super.renderChildren(canvas, ctx);
        }
    }
}
