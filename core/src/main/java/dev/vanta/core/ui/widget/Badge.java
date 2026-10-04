package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/** Small tinted pill with a short label (status, count, "Coming soon"). Not interactive. */
public class Badge extends UiNode {

    /** Color role. */
    public enum Tone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER, INFO }

    /** Pill height. */
    public static final int HEIGHT = 12;
    private static final int PAD_X = 5;

    private String text;
    private Tone tone;

    public Badge(String text) {
        this(text, Tone.NEUTRAL);
    }

    public Badge(String text, Tone tone) {
        this.text = text == null ? "" : text;
        this.tone = tone == null ? Tone.NEUTRAL : tone;
    }

    public Badge setText(String newText) {
        this.text = newText == null ? "" : newText;
        return this;
    }

    public String text() {
        return text;
    }

    public Badge tone(Tone t) {
        this.tone = t == null ? Tone.NEUTRAL : t;
        return this;
    }

    public Tone tone() {
        return tone;
    }

    /** Foreground color of the tone. */
    public int foreground(Theme theme) {
        return switch (tone) {
            case ACCENT -> theme.accentHover();
            case SUCCESS -> theme.success();
            case WARNING -> theme.warning();
            case DANGER -> theme.danger();
            case INFO -> theme.info();
            default -> theme.textSecondary();
        };
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(ctx.textWidth(text, FontKind.UI_BOLD) + PAD_X * 2, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        int fg = foreground(theme);
        int bg = tone == Tone.NEUTRAL ? theme.surface3() : Colors.withAlpha(fg, 0.18f);
        int w = Math.min(b.w(), canvas.textWidth(text, FontKind.UI_BOLD) + PAD_X * 2);
        int h = Math.min(b.h(), HEIGHT);
        int y = b.y() + (b.h() - h) / 2;
        canvas.fillRounded(b.x(), y, w, h, Theme.RADIUS_LG, bg);
        if (tone == Tone.NEUTRAL) {
            canvas.strokeRounded(b.x(), y, w, h, Theme.RADIUS_LG, theme.borderStrong());
        }
        int textY = y + (h - canvas.lineHeight(FontKind.UI_BOLD)) / 2;
        canvas.text(canvas.textClipped(text, w - PAD_X * 2, FontKind.UI_BOLD), b.x() + PAD_X, textY, fg,
                FontKind.UI_BOLD, false);
    }
}
