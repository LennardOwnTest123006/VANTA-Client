package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

import java.util.List;
import java.util.Objects;

/**
 * Static text. Variants pick font and color from the theme; {@link #wrap(boolean)} enables word wrapping to
 * the laid-out width. Containers that know the width they will hand out ({@code Column}, {@code Panel},
 * {@code Card}, {@code ScrollPanel}, a flex child of a {@code Row}) measure a wrapped label against it, so its
 * wrapped height is known in the first layout pass; set an explicit width where no such width exists.
 */
public class Label extends UiNode {

    /** Typographic role. */
    public enum Variant {
        /** Bold body font, primary color. */
        TITLE,
        /** Body font, primary color. */
        BODY,
        /** Body font, secondary color. */
        CAPTION,
        /** Body font, muted color. */
        MUTED,
        /** Display font, primary color (screen titles). */
        DISPLAY
    }

    /** Horizontal alignment inside the bounds. */
    public enum HAlign { LEFT, CENTER, RIGHT }

    /** Vertical alignment inside the bounds. */
    public enum VAlign { TOP, CENTER, BOTTOM }

    private String text;
    private Variant variant;
    private Integer color;
    private FontKind fontOverride;
    private HAlign hAlign = HAlign.LEFT;
    private VAlign vAlign = VAlign.CENTER;
    private boolean wrap;
    private boolean shadow;
    private int maxLines = Integer.MAX_VALUE;
    private List<String> lines = List.of();

    /** Body label. */
    public Label(String text) {
        this(text, Variant.BODY);
    }

    /** Label with a variant. */
    public Label(String text, Variant variant) {
        this.text = text == null ? "" : text;
        this.variant = variant == null ? Variant.BODY : variant;
    }

    public String text() {
        return text;
    }

    public Label setText(String newText) {
        this.text = newText == null ? "" : newText;
        return this;
    }

    public Variant variant() {
        return variant;
    }

    public Label variant(Variant v) {
        this.variant = Objects.requireNonNull(v);
        return this;
    }

    /** Explicit color (overrides the variant color). */
    public Label color(int argb) {
        this.color = argb;
        return this;
    }

    /** Explicit font (overrides the variant font). */
    public Label font(FontKind font) {
        this.fontOverride = font;
        return this;
    }

    public Label align(HAlign h) {
        this.hAlign = h == null ? HAlign.LEFT : h;
        return this;
    }

    public Label valign(VAlign v) {
        this.vAlign = v == null ? VAlign.CENTER : v;
        return this;
    }

    /** Enables word wrapping; wrapped labels align to the top by default. */
    public Label wrap(boolean on) {
        this.wrap = on;
        if (on) {
            this.vAlign = VAlign.TOP;
        }
        return this;
    }

    /** Draws Minecraft-style text shadow (used over the world, not inside panels). */
    public Label shadow(boolean on) {
        this.shadow = on;
        return this;
    }

    /** Limits wrapped output; the last line is ellipsized. */
    public Label maxLines(int n) {
        this.maxLines = Math.max(1, n);
        return this;
    }

    /** The font in use. */
    public FontKind font() {
        if (fontOverride != null) {
            return fontOverride;
        }
        return switch (variant) {
            case TITLE -> FontKind.UI_BOLD;
            case DISPLAY -> FontKind.DISPLAY;
            default -> FontKind.UI;
        };
    }

    /** The resolved text color for the theme. */
    public int color(Theme theme) {
        if (color != null) {
            return color;
        }
        return switch (variant) {
            case CAPTION -> theme.textSecondary();
            case MUTED -> theme.textMuted();
            default -> theme.textPrimary();
        };
    }

    /** Lines computed by the last layout (a single line when not wrapping). */
    public List<String> lines() {
        return lines;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return measure(ctx, -1);
    }

    /**
     * Wrapped labels measure against, in this order, their explicit width, the width the parent offers and the
     * width of the last layout; a fresh label without any of those measures as a single line.
     */
    @Override
    protected Size measure(UiContext ctx, int availableWidth) {
        FontKind f = font();
        int lh = ctx.lineHeight(f);
        if (wrap && explicitWidth() > 0) {
            List<String> wrapped = CanvasText.wrap(text, explicitWidth(), f, ctx.metrics());
            int n = Math.min(wrapped.size(), maxLines);
            return new Size(explicitWidth(), n * lh);
        }
        int width = availableWidth > 0 ? availableWidth : bounds().w();
        if (wrap && width > 0) {
            List<String> wrapped = CanvasText.wrap(text, width, f, ctx.metrics());
            int n = Math.min(wrapped.size(), maxLines);
            return new Size(Math.min(width, CanvasText.maxLineWidth(wrapped, f, ctx.metrics())), n * lh);
        }
        return new Size(ctx.textWidth(text, f), lh);
    }

    @Override
    public void layout(UiContext ctx) {
        FontKind f = font();
        if (wrap) {
            List<String> wrapped = CanvasText.wrap(text, Math.max(1, bounds().w()), f, ctx.metrics());
            if (wrapped.size() > maxLines) {
                wrapped = wrapped.subList(0, maxLines);
                String last = wrapped.get(maxLines - 1);
                wrapped.set(maxLines - 1, CanvasText.ellipsize(last + CanvasText.ELLIPSIS, bounds().w(), f, ctx.metrics()));
            }
            lines = List.copyOf(wrapped);
        } else {
            lines = List.of(CanvasText.ellipsize(text, Math.max(0, bounds().w()), f, ctx.metrics()));
        }
        super.layout(ctx);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (lines.isEmpty()) {
            return;
        }
        Theme theme = ctx.theme();
        FontKind f = font();
        int lh = canvas.lineHeight(f);
        Rect b = bounds();
        int blockH = lines.size() * lh;
        int y = switch (vAlign) {
            case TOP -> b.y();
            case BOTTOM -> b.bottom() - blockH;
            default -> b.y() + (b.h() - blockH) / 2;
        };
        int argb = isEffectivelyEnabled() ? color(theme) : theme.textMuted();
        for (String line : lines) {
            switch (hAlign) {
                case CENTER -> canvas.textCentered(line, b.centerX(), y, argb, f, shadow);
                case RIGHT -> canvas.textRight(line, b.right(), y, argb, f, shadow);
                default -> canvas.text(line, b.x(), y, argb, f, shadow);
            }
            y += lh;
        }
    }
}
