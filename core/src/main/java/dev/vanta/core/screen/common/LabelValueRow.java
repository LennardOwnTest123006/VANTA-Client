package dev.vanta.core.screen.common;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/**
 * One line of "label … value": muted label at the left, primary value right-aligned (ellipsized when it does not
 * fit). Used by the About screen and the Performance Center's system card.
 */
public class LabelValueRow extends UiNode {
    /** Default height. */
    public static final int HEIGHT = 14;

    private String label;
    private String value;
    private Integer valueColor;

    public LabelValueRow(String label, String value) {
        this.label = label == null ? "" : label;
        this.value = value == null ? "" : value;
    }

    public LabelValueRow setValue(String newValue) {
        this.value = newValue == null ? "" : newValue;
        return this;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    /** Explicit value color (defaults to {@code text.primary}). */
    public LabelValueRow valueColor(Integer argb) {
        this.valueColor = argb;
        return this;
    }

    @Override
    protected Size measure(UiContext ctx) {
        int w = ctx.textWidth(label, FontKind.UI) + Theme.SPACE_5 + ctx.textWidth(value, FontKind.UI);
        return new Size(w, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        int lh = canvas.lineHeight(FontKind.UI);
        int textY = b.y() + (b.h() - lh) / 2;
        int labelW = Math.min(canvas.textWidth(label, FontKind.UI), b.w() / 2);
        canvas.text(canvas.textClipped(label, labelW, FontKind.UI), b.x(), textY, theme.textMuted(), FontKind.UI, false);
        int valueW = Math.max(0, b.w() - labelW - Theme.SPACE_4);
        canvas.textRight(canvas.textClipped(value, valueW, FontKind.UI), b.right(), textY,
                valueColor != null ? valueColor : theme.textPrimary(), FontKind.UI, false);
    }
}
