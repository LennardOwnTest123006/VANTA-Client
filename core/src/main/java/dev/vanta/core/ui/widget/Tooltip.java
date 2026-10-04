package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;

import java.util.List;

/**
 * Tooltip painter. Screens call {@link #render} after the hover delay elapsed; the box is wrapped to
 * {@link #MAX_WIDTH} and flipped to stay inside the screen.
 */
public final class Tooltip {

    /** Hover time before a tooltip appears. */
    public static final long DELAY_MS = 400L;
    /** Widest text block. */
    public static final int MAX_WIDTH = 160;
    private static final int PAD_X = 6;
    private static final int PAD_Y = 4;
    private static final int OFFSET_X = 8;
    private static final int OFFSET_Y = 12;

    private Tooltip() {
    }

    /** Size of the tooltip box for {@code text}. */
    public static Rect layout(UiContext ctx, String text, int anchorX, int anchorY) {
        List<String> lines = CanvasText.wrap(text, MAX_WIDTH, FontKind.UI, ctx.metrics());
        int w = CanvasText.maxLineWidth(lines, FontKind.UI, ctx.metrics()) + PAD_X * 2;
        int h = lines.size() * ctx.lineHeight(FontKind.UI) + PAD_Y * 2;
        int x = anchorX + OFFSET_X;
        int y = anchorY + OFFSET_Y;
        if (x + w > ctx.screenWidth() - 2) {
            x = Math.max(2, anchorX - OFFSET_X - w);
        }
        if (y + h > ctx.screenHeight() - 2) {
            y = Math.max(2, anchorY - OFFSET_Y - h);
        }
        return new Rect(x, y, w, h);
    }

    /** Draws the tooltip near the anchor (usually the mouse). */
    public static void render(Canvas canvas, UiContext ctx, String text, int anchorX, int anchorY) {
        if (text == null || text.isBlank()) {
            return;
        }
        Theme theme = ctx.theme();
        List<String> lines = CanvasText.wrap(text, MAX_WIDTH, FontKind.UI, canvas);
        Rect box = layout(ctx, text, anchorX, anchorY);
        canvas.fillRounded(box.x() + 1, box.y() + 2, box.w(), box.h(), Theme.RADIUS_MD,
                dev.vanta.core.ui.Colors.withAlpha(theme.bgVoid(), 0.55f));
        canvas.fillRounded(box.x(), box.y(), box.w(), box.h(), Theme.RADIUS_MD, theme.surface2());
        canvas.strokeRounded(box.x(), box.y(), box.w(), box.h(), Theme.RADIUS_MD, theme.borderStrong());
        int y = box.y() + PAD_Y;
        int lh = canvas.lineHeight(FontKind.UI);
        for (String line : lines) {
            canvas.text(line, box.x() + PAD_X, y, theme.textPrimary(), FontKind.UI, false);
            y += lh;
        }
    }
}
