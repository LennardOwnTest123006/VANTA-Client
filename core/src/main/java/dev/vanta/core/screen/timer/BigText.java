package dev.vanta.core.screen.timer;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import java.util.Objects;

/**
 * One line of large text, centred in its bounds and drawn scaled up to {@code maxScale}; it shrinks (never below
 * half size) when the window is too narrow. The height is reserved for the largest scale, so a changing text never
 * moves the nodes below it.
 */
public final class BigText extends UiNode {
    private static final float MIN_SCALE = 0.5f;

    private final FontKind font;
    private final float maxScale;
    private String text;
    private Integer color;

    public BigText(String text, FontKind font, float maxScale) {
        this.text = text == null ? "" : text;
        this.font = Objects.requireNonNull(font, "font");
        this.maxScale = Math.max(1f, maxScale);
    }

    public String text() {
        return text;
    }

    /** Changes the text; nothing is re-laid out because the height does not depend on it. */
    public BigText setText(String newText) {
        this.text = newText == null ? "" : newText;
        return this;
    }

    /** Fixed colour (ARGB); without one the theme's primary text colour is used. */
    public BigText color(int argb) {
        this.color = argb;
        return this;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return measure(ctx, -1);
    }

    @Override
    protected Size measure(UiContext ctx, int availableWidth) {
        int textW = ctx.textWidth(text, font);
        int w = availableWidth > 0 ? availableWidth : Math.round(textW * maxScale);
        return new Size(w, (int) Math.ceil(ctx.lineHeight(font) * maxScale) + Theme.SPACE_2);
    }

    /** The scale the text is drawn at inside {@code width} pixels. */
    float scaleFor(int textWidth, int width) {
        if (textWidth <= 0 || width <= 0) {
            return maxScale;
        }
        return Math.max(MIN_SCALE, Math.min(maxScale, width / (float) textWidth));
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (text.isEmpty()) {
            return;
        }
        Rect b = bounds();
        int textW = ctx.textWidth(text, font);
        float scale = scaleFor(textW, b.w());
        float x = b.x() + (b.w() - textW * scale) / 2f;
        float y = b.y() + (b.h() - ctx.lineHeight(font) * scale) / 2f;
        int argb = color != null ? color : ctx.theme().textPrimary();
        canvas.pushTranslate(x, y);
        canvas.pushScale(scale, scale);
        canvas.text(text, 0, 0, argb, font, false);
        canvas.pop();
        canvas.pop();
    }
}
