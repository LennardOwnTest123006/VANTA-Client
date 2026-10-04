package dev.vanta.core.screen.cosmetics;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import java.util.List;

/**
 * Honest "nothing here" placeholder: an icon tile, a title and a wrapped hint, centered in the available area. An
 * optional action node (usually a button) sits under the hint.
 */
public class EmptyState extends UiNode {
    private static final int TILE = 28;
    private static final int ICON = 12;
    private static final int MAX_TEXT_W = 260;

    private Icons icon;
    private String title;
    private String hint;
    private UiNode action;
    private List<String> hintLines = List.of();

    public EmptyState(Icons icon, String title, String hint) {
        this.icon = icon == null ? Icons.INFO : icon;
        this.title = title == null ? "" : title;
        this.hint = hint == null ? "" : hint;
    }

    public EmptyState title(String newTitle) {
        this.title = newTitle == null ? "" : newTitle;
        return this;
    }

    public EmptyState hint(String newHint) {
        this.hint = newHint == null ? "" : newHint;
        return this;
    }

    public EmptyState icon(Icons newIcon) {
        this.icon = newIcon == null ? Icons.INFO : newIcon;
        return this;
    }

    /** Node shown under the hint (for example a button); {@code null} removes it. */
    public EmptyState action(UiNode node) {
        if (action != null) {
            remove(action);
        }
        action = node;
        if (node != null) {
            add(node);
        }
        return this;
    }

    public String title() {
        return title;
    }

    public String hint() {
        return hint;
    }

    private int textWidth(int available) {
        return Math.max(40, Math.min(MAX_TEXT_W, available - Theme.SPACE_6 * 2));
    }

    private int blockHeight(UiContext ctx, List<String> lines) {
        int h = TILE + Theme.SPACE_3 + ctx.lineHeight(FontKind.UI_BOLD);
        if (!lines.isEmpty()) {
            h += Theme.SPACE_2 + lines.size() * ctx.lineHeight(FontKind.UI);
        }
        if (action != null) {
            h += Theme.SPACE_4 + action.preferredSize(ctx).h();
        }
        return h;
    }

    @Override
    protected Size measure(UiContext ctx) {
        int w = explicitWidth() > 0 ? explicitWidth() : Math.max(bounds().w(), 200);
        List<String> lines = hint.isEmpty() ? List.of() : CanvasText.wrap(hint, textWidth(w), FontKind.UI, ctx.metrics());
        return new Size(w, blockHeight(ctx, lines) + Theme.SPACE_6 * 2);
    }

    @Override
    public void layout(UiContext ctx) {
        hintLines = hint.isEmpty() ? List.of()
                : CanvasText.wrap(hint, textWidth(bounds().w()), FontKind.UI, ctx.metrics());
        if (action != null) {
            Rect b = bounds();
            int blockH = blockHeight(ctx, hintLines);
            int top = b.y() + Math.max(Theme.SPACE_4, (b.h() - blockH) / 2);
            Size s = action.preferredSize(ctx);
            action.setBounds(b.centerX() - s.w() / 2, top + blockH - s.h(), s.w(), s.h());
            action.layout(ctx);
        }
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        int blockH = blockHeight(ctx, hintLines);
        int y = b.y() + Math.max(Theme.SPACE_4, (b.h() - blockH) / 2);
        int cx = b.centerX();
        canvas.fillRounded(cx - TILE / 2, y, TILE, TILE, Theme.RADIUS_LG, theme.surface2());
        canvas.strokeRounded(cx - TILE / 2, y, TILE, TILE, Theme.RADIUS_LG, theme.borderSubtle());
        icon.draw(canvas, cx - ICON / 2, y + (TILE - ICON) / 2, ICON, Colors.withAlpha(theme.textSecondary(), 0.9f));
        y += TILE + Theme.SPACE_3;
        canvas.textCentered(canvas.textClipped(title, b.w() - Theme.SPACE_4 * 2, FontKind.UI_BOLD), cx, y,
                theme.textPrimary(), FontKind.UI_BOLD, false);
        y += canvas.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_2;
        for (String line : hintLines) {
            canvas.textCentered(line, cx, y, theme.textMuted(), FontKind.UI, false);
            y += canvas.lineHeight(FontKind.UI);
        }
    }
}
