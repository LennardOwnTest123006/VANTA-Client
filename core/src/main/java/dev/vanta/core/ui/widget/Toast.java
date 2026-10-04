package dev.vanta.core.ui.widget;

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
 * Notification card: kind icon, title, wrapped body (up to three lines) and a progress line showing the
 * remaining display time. The notification center positions and stacks toasts.
 */
public class Toast extends UiNode {

    /** Semantic kind; picks icon and accent color. */
    public enum Kind { INFO, SUCCESS, WARNING, ERROR }

    /** Default width. */
    public static final int WIDTH = 180;
    private static final int PAD = 8;
    private static final int ICON = 10;
    private static final int MAX_BODY_LINES = 3;

    private String title;
    private String body;
    private Kind kind;
    private float progress = -1f;
    private Icons iconOverride;
    private List<String> bodyLines = List.of();

    public Toast(Kind kind, String title, String body) {
        this.kind = kind == null ? Kind.INFO : kind;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
    }

    public Toast title(String t) {
        this.title = t == null ? "" : t;
        return this;
    }

    public Toast body(String b) {
        this.body = b == null ? "" : b;
        return this;
    }

    public Toast kind(Kind k) {
        this.kind = k == null ? Kind.INFO : k;
        return this;
    }

    /** Remaining-time fraction 0..1, or negative to hide the progress line. */
    public Toast progress(float fraction) {
        this.progress = fraction;
        return this;
    }

    /** Overrides the kind icon. */
    public Toast icon(Icons icon) {
        this.iconOverride = icon;
        return this;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public Kind kind() {
        return kind;
    }

    public float progress() {
        return progress;
    }

    /** Accent color for the kind. */
    public int accent(Theme theme) {
        return switch (kind) {
            case SUCCESS -> theme.success();
            case WARNING -> theme.warning();
            case ERROR -> theme.danger();
            default -> theme.info();
        };
    }

    /** Icon for the kind. */
    public Icons icon() {
        if (iconOverride != null) {
            return iconOverride;
        }
        return switch (kind) {
            case SUCCESS -> Icons.SUCCESS;
            case WARNING -> Icons.WARNING;
            case ERROR -> Icons.ERROR;
            default -> Icons.INFO;
        };
    }

    private int textLeft() {
        return PAD + 2 + ICON + Theme.SPACE_3;
    }

    private List<String> wrapBody(UiContext ctx, int width) {
        if (body.isEmpty()) {
            return List.of();
        }
        List<String> lines = CanvasText.wrap(body, Math.max(1, width), FontKind.UI, ctx.metrics());
        if (lines.size() > MAX_BODY_LINES) {
            lines = new java.util.ArrayList<>(lines.subList(0, MAX_BODY_LINES));
            String last = lines.get(MAX_BODY_LINES - 1);
            lines.set(MAX_BODY_LINES - 1, CanvasText.ellipsize(last + CanvasText.ELLIPSIS, width, FontKind.UI, ctx.metrics()));
        }
        return lines;
    }

    @Override
    protected Size measure(UiContext ctx) {
        int w = explicitWidth() > 0 ? explicitWidth() : WIDTH;
        int textW = w - textLeft() - PAD;
        List<String> lines = wrapBody(ctx, textW);
        int h = PAD + ctx.lineHeight(FontKind.UI_BOLD) + lines.size() * ctx.lineHeight(FontKind.UI)
                + (lines.isEmpty() ? 0 : Theme.SPACE_1) + PAD;
        return new Size(w, h);
    }

    @Override
    public void layout(UiContext ctx) {
        bodyLines = wrapBody(ctx, bounds().w() - textLeft() - PAD);
        super.layout(ctx);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        int accent = accent(theme);
        canvas.fillRounded(b.x() + 1, b.y() + 2, b.w(), b.h(), Theme.RADIUS_LG, Colors.withAlpha(theme.bgVoid(), 0.5f));
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG,
                Colors.withAlpha(theme.surface2(), Math.min(1f, theme.panelAlpha() + 0.12f)));
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.borderSubtle());
        canvas.fill(b.x() + 1, b.y() + Theme.RADIUS_LG + 1, 2, b.h() - Theme.RADIUS_LG * 2 - 2, accent);
        icon().draw(canvas, b.x() + PAD + 2, b.y() + PAD, ICON, accent);
        int x = b.x() + textLeft();
        int y = b.y() + PAD;
        canvas.text(canvas.textClipped(title, b.right() - PAD - x, FontKind.UI_BOLD), x, y, theme.textPrimary(),
                FontKind.UI_BOLD, false);
        y += canvas.lineHeight(FontKind.UI_BOLD) + (bodyLines.isEmpty() ? 0 : Theme.SPACE_1);
        for (String line : bodyLines) {
            canvas.text(line, x, y, theme.textSecondary(), FontKind.UI, false);
            y += canvas.lineHeight(FontKind.UI);
        }
        if (progress >= 0f) {
            int trackX = b.x() + Theme.RADIUS_LG;
            int trackW = b.w() - Theme.RADIUS_LG * 2;
            int fill = Math.round(trackW * Math.min(1f, progress));
            canvas.fill(trackX, b.bottom() - 2, trackW, 1, Colors.withAlpha(accent, 0.2f));
            if (fill > 0) {
                canvas.fill(trackX, b.bottom() - 2, fill, 1, accent);
            }
        }
    }
}
