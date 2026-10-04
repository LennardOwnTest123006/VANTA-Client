package dev.vanta.core.screen.stats;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;

/**
 * Summary card of one statistic: muted title, large value in the display font, a caption and a mini bar chart of
 * the per-session series (newest at the right, up to {@value #MAX_BARS} bars). An empty series draws a flat
 * baseline so fresh installs look intentional rather than broken.
 */
public final class StatTile extends UiNode {
    /** Tile height. */
    public static final int HEIGHT = 64;
    /** Most bars drawn (the store keeps 30 sessions). */
    public static final int MAX_BARS = 30;
    private static final int PAD = Theme.SPACE_4;
    private static final int BARS_H = 14;

    private final String title;
    private String value;
    private String caption;
    private float[] series;
    private boolean accentBlue;

    public StatTile(String title, String value, String caption, float[] series) {
        this.title = title == null ? "" : title;
        this.value = value == null ? "" : value;
        this.caption = caption == null ? "" : caption;
        this.series = series == null ? new float[0] : series.clone();
    }

    public String title() {
        return title;
    }

    public String value() {
        return value;
    }

    public String caption() {
        return caption;
    }

    public StatTile setValue(String v) {
        this.value = v == null ? "" : v;
        return this;
    }

    public StatTile setCaption(String c) {
        this.caption = c == null ? "" : c;
        return this;
    }

    /** Uses the blue accent for the bars instead of violet. */
    public StatTile blue(boolean on) {
        this.accentBlue = on;
        return this;
    }

    public float[] series() {
        return series.clone();
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(140, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.panelBackground());
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.borderSubtle());
        int x = b.x() + PAD;
        int innerW = b.w() - PAD * 2;
        int y = b.y() + PAD - 1;
        canvas.text(canvas.textClipped(title, innerW, FontKind.UI), x, y, theme.textMuted(), FontKind.UI, false);
        y += canvas.lineHeight(FontKind.UI) + 1;
        canvas.text(canvas.textClipped(value, innerW, FontKind.DISPLAY), x, y, theme.textPrimary(), FontKind.DISPLAY,
                false);
        int captionY = y + canvas.lineHeight(FontKind.DISPLAY) + 1;
        canvas.text(canvas.textClipped(caption, innerW, FontKind.UI), x, captionY, theme.textSecondary(), FontKind.UI,
                false);
        Rect bars = new Rect(x, b.bottom() - PAD - BARS_H + 2, innerW, BARS_H - 2);
        drawBars(canvas, theme, bars);
    }

    private void drawBars(Canvas canvas, Theme theme, Rect r) {
        int accent = accentBlue ? theme.accentBlue() : theme.accent();
        canvas.fill(r.x(), r.bottom() - 1, r.w(), 1, Colors.withAlpha(theme.borderStrong(), 0.9f));
        if (series.length == 0 || r.w() < MAX_BARS) {
            return;
        }
        float max = 0f;
        for (float v : series) {
            max = Math.max(max, v);
        }
        if (max <= 0f) {
            return;
        }
        int n = Math.min(MAX_BARS, series.length);
        int slot = Math.max(2, r.w() / MAX_BARS);
        int barW = Math.max(1, slot - 1);
        int right = r.right();
        for (int i = 0; i < n; i++) {
            float v = series[series.length - 1 - i];
            int h = v <= 0f ? 0 : Math.max(1, Math.round((r.h() - 1) * (v / max)));
            int bx = right - (i + 1) * slot + 1;
            if (bx < r.x()) {
                break;
            }
            float age = 1f - i / (float) MAX_BARS;
            int color = Colors.withAlpha(accent, 0.35f + 0.6f * age);
            if (h > 0) {
                canvas.fill(bx, r.bottom() - 1 - h, barW, h, color);
            }
        }
    }
}
