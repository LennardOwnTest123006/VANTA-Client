package dev.vanta.core.screen.common;

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
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.widget.Button;
import java.util.List;

/**
 * Tinted message box with an icon, a bold title, wrapped body text and up to two action buttons. Actions sit at the
 * right on wide rows and move below the text on narrow ones.
 */
public class InfoBanner extends UiNode {
    /** Colour role. */
    public enum Tone { INFO, SUCCESS, WARNING, DANGER, NEUTRAL }

    private static final int PAD = Theme.SPACE_4;
    private static final int ICON = 10;
    private static final int NARROW = 300;

    private final Row actions = new Row(Theme.SPACE_3);
    private Tone tone;
    private Icons icon;
    private String title;
    private String body;
    private List<String> bodyLines = List.of();

    public InfoBanner(Tone tone, String title, String body) {
        this.tone = tone == null ? Tone.INFO : tone;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        add(actions);
    }

    public InfoBanner tone(Tone t) {
        this.tone = t == null ? Tone.INFO : t;
        return this;
    }

    /** Icon override (defaults to the tone's icon). */
    public InfoBanner icon(Icons i) {
        this.icon = i;
        return this;
    }

    public InfoBanner setTitle(String t) {
        this.title = t == null ? "" : t;
        return this;
    }

    public InfoBanner setBody(String b) {
        this.body = b == null ? "" : b;
        return this;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    /** Adds an action button (at most two look good). */
    public InfoBanner action(Button button) {
        actions.add(button);
        return this;
    }

    /** The action row (tests). */
    public Row actionRow() {
        return actions;
    }

    /** Accent colour for the tone. */
    public int accent(Theme theme) {
        return switch (tone) {
            case SUCCESS -> theme.success();
            case WARNING -> theme.warning();
            case DANGER -> theme.danger();
            case NEUTRAL -> theme.textSecondary();
            default -> theme.info();
        };
    }

    private Icons effectiveIcon() {
        if (icon != null) {
            return icon;
        }
        return switch (tone) {
            case SUCCESS -> Icons.SUCCESS;
            case WARNING -> Icons.WARNING;
            case DANGER -> Icons.ERROR;
            default -> Icons.INFO;
        };
    }

    private boolean hasActions() {
        return !actions.children().isEmpty();
    }

    private boolean stacked(int width) {
        return hasActions() && width < NARROW;
    }

    private int textLeft() {
        return PAD + 2 + ICON + Theme.SPACE_3;
    }

    private int textWidth(UiContext ctx, int width) {
        int w = width - textLeft() - PAD;
        if (hasActions() && !stacked(width)) {
            w -= actions.preferredSize(ctx).w() + Theme.SPACE_4;
        }
        return Math.max(20, w);
    }

    private int textHeight(UiContext ctx, List<String> lines) {
        int h = title.isEmpty() ? 0 : ctx.lineHeight(FontKind.UI_BOLD);
        if (!lines.isEmpty()) {
            h += (title.isEmpty() ? 0 : Theme.SPACE_1) + lines.size() * ctx.lineHeight(FontKind.UI);
        }
        return h;
    }

    @Override
    protected Size measure(UiContext ctx) {
        int w = explicitWidth() > 0 ? explicitWidth() : Math.max(bounds().w(), 240);
        List<String> lines = body.isEmpty() ? List.of()
                : CanvasText.wrap(body, textWidth(ctx, w), FontKind.UI, ctx.metrics());
        int textH = textHeight(ctx, lines);
        int h;
        if (!hasActions()) {
            h = textH;
        } else if (stacked(w)) {
            h = textH + Theme.SPACE_3 + actions.preferredSize(ctx).h();
        } else {
            h = Math.max(textH, actions.preferredSize(ctx).h());
        }
        return new Size(w, h + PAD * 2);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        bodyLines = body.isEmpty() ? List.of()
                : CanvasText.wrap(body, textWidth(ctx, b.w()), FontKind.UI, ctx.metrics());
        actions.setVisible(hasActions());
        if (hasActions()) {
            Size a = actions.preferredSize(ctx);
            if (stacked(b.w())) {
                actions.setBounds(b.x() + textLeft(), b.bottom() - PAD - a.h(), Math.max(0, b.w() - textLeft() - PAD), a.h());
            } else {
                actions.setBounds(b.right() - PAD - a.w(), b.y() + (b.h() - a.h()) / 2, a.w(), a.h());
            }
            actions.layout(ctx);
        }
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        int accent = accent(theme);
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG,
                tone == Tone.NEUTRAL ? theme.surface2() : Colors.withAlpha(accent, 0.10f));
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG,
                tone == Tone.NEUTRAL ? theme.borderSubtle() : Colors.withAlpha(accent, 0.35f));
        canvas.fill(b.x() + 1, b.y() + Theme.RADIUS_LG, 2, b.h() - Theme.RADIUS_LG * 2, accent);
        int textH = textHeight(ctx, bodyLines);
        int y = stacked(b.w()) || !hasActions() ? b.y() + PAD : b.y() + (b.h() - textH) / 2;
        effectiveIcon().draw(canvas, b.x() + PAD + 2, y, ICON, accent);
        int x = b.x() + textLeft();
        int textW = textWidth(ctx, b.w());
        if (!title.isEmpty()) {
            canvas.text(canvas.textClipped(title, textW, FontKind.UI_BOLD), x, y, theme.textPrimary(), FontKind.UI_BOLD,
                    false);
            y += canvas.lineHeight(FontKind.UI_BOLD) + (bodyLines.isEmpty() ? 0 : Theme.SPACE_1);
        }
        for (String line : bodyLines) {
            canvas.text(line, x, y, theme.textSecondary(), FontKind.UI, false);
            y += canvas.lineHeight(FontKind.UI);
        }
    }
}
