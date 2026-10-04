package dev.vanta.core.screen.common;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/**
 * Group label inside a list of rows: bold caption at the left, optional trailing text at the right (a count, a
 * category) and a hairline filling the space between.
 */
public class SectionHeader extends UiNode {
    /** Default height. */
    public static final int HEIGHT = 16;

    private String text;
    private String trailing = "";

    public SectionHeader(String text) {
        this.text = text == null ? "" : text;
    }

    public SectionHeader setText(String newText) {
        this.text = newText == null ? "" : newText;
        return this;
    }

    public String text() {
        return text;
    }

    /** Muted text drawn at the right end. */
    public SectionHeader trailing(String value) {
        this.trailing = value == null ? "" : value;
        return this;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(ctx.textWidth(text, FontKind.UI_BOLD) + Theme.SPACE_6, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        int lh = canvas.lineHeight(FontKind.UI_BOLD);
        int textY = b.y() + (b.h() - lh) / 2;
        int trailingW = trailing.isEmpty() ? 0 : canvas.textWidth(trailing, FontKind.UI) + Theme.SPACE_3;
        String shown = canvas.textClipped(text, Math.max(0, b.w() - trailingW - Theme.SPACE_6), FontKind.UI_BOLD);
        canvas.text(shown, b.x(), textY, theme.textSecondary(), FontKind.UI_BOLD, false);
        int lineStart = b.x() + canvas.textWidth(shown, FontKind.UI_BOLD) + Theme.SPACE_3;
        int lineEnd = b.right() - trailingW;
        if (lineEnd > lineStart) {
            canvas.fill(lineStart, b.centerY(), lineEnd - lineStart, 1, Colors.withAlpha(theme.borderSubtle(), 0.9f));
        }
        if (!trailing.isEmpty()) {
            canvas.textRight(trailing, b.right(), b.y() + (b.h() - canvas.lineHeight(FontKind.UI)) / 2,
                    theme.textMuted(), FontKind.UI, false);
        }
    }
}
