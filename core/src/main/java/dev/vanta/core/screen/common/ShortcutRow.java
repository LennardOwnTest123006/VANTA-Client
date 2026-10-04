package dev.vanta.core.screen.common;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import java.util.List;

/**
 * One keyboard shortcut line: key caps drawn as small pills ({@code Ctrl} {@code K}) followed by what they do.
 * Used by the accessibility screen's keyboard section and by help footers.
 */
public class ShortcutRow extends UiNode {
    /** Row height. */
    public static final int HEIGHT = 18;
    private static final int CAP_H = 12;
    private static final int CAP_PAD = 4;
    private static final int CAP_GAP = 3;

    private final List<String> keys;
    private final String description;

    /**
     * @param keys        key cap labels in press order (e.g. {@code "Ctrl", "K"})
     * @param description translated explanation
     */
    public ShortcutRow(List<String> keys, String description) {
        this.keys = List.copyOf(keys);
        this.description = description == null ? "" : description;
    }

    /** Key caps. */
    public List<String> keys() {
        return keys;
    }

    /** Explanation text. */
    public String description() {
        return description;
    }

    private int capsWidth(UiContext ctx) {
        int w = 0;
        for (String k : keys) {
            w += ctx.textWidth(k, FontKind.UI_BOLD) + CAP_PAD * 2 + CAP_GAP;
        }
        return Math.max(0, w - CAP_GAP);
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(capsWidth(ctx) + Theme.SPACE_4 + ctx.textWidth(description, FontKind.UI), HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        int x = b.x();
        int capY = b.y() + (b.h() - CAP_H) / 2;
        int lh = canvas.lineHeight(FontKind.UI_BOLD);
        for (String k : keys) {
            int w = canvas.textWidth(k, FontKind.UI_BOLD) + CAP_PAD * 2;
            canvas.fillRounded(x, capY + 1, w, CAP_H, Theme.RADIUS_SM, Colors.withAlpha(theme.bgVoid(), 0.6f));
            canvas.fillRounded(x, capY, w, CAP_H, Theme.RADIUS_SM, theme.surface3());
            canvas.strokeRounded(x, capY, w, CAP_H, Theme.RADIUS_SM, theme.borderStrong());
            canvas.text(k, x + CAP_PAD, capY + (CAP_H - lh) / 2, theme.textPrimary(), FontKind.UI_BOLD, false);
            x += w + CAP_GAP;
        }
        int textX = b.x() + capsWidth(ctx) + Theme.SPACE_4;
        int textY = b.y() + (b.h() - canvas.lineHeight(FontKind.UI)) / 2;
        canvas.text(canvas.textClipped(description, Math.max(0, b.right() - textX), FontKind.UI), textX, textY,
                theme.textSecondary(), FontKind.UI, false);
    }
}
