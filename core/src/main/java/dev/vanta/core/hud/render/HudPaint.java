package dev.vanta.core.hud.render;

import dev.vanta.core.accessibility.AccessibilityState;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.cosmetics.HudThemeStyle;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;

import java.util.Objects;

/**
 * Per-frame styling shared by every widget renderer: the active {@link HudTheme}, the text-shadow setting, the
 * accessibility flags, the status colours and the crosshair style. Immutable; {@link HudRenderer} rebuilds it once
 * per tick so the render pass never reads settings.
 * <p>
 * Widgets draw in their own unscaled coordinate space: {@code (0, 0)} is the top-left corner of the widget and
 * {@link HudWidgetState#width()} × {@link HudWidgetState#height()} its size.
 */
public final class HudPaint {

    /** Alpha of {@link HudWidgetState#DEFAULT_BACKGROUND}; theme alphas are defined relative to it. */
    public static final float DEFAULT_BACKGROUND_ALPHA = 0.70f;
    /** Line height of the HUD fonts in GUI pixels. */
    public static final int LINE_HEIGHT = 9;
    /** Space between a label and its value. */
    public static final int GAP = 4;

    private final HudTheme theme;
    private final boolean textShadow;
    private final boolean reducedTransparency;
    private final int success;
    private final int warning;
    private final int danger;
    private final CrosshairStyle crosshair;

    private HudPaint(HudTheme theme, boolean textShadow, boolean reducedTransparency, int success, int warning,
                     int danger, CrosshairStyle crosshair) {
        this.theme = Objects.requireNonNull(theme, "theme");
        this.textShadow = textShadow;
        this.reducedTransparency = reducedTransparency;
        this.success = success;
        this.warning = warning;
        this.danger = danger;
        this.crosshair = Objects.requireNonNull(crosshair, "crosshair");
    }

    /** CLEAN theme, text shadow on, default status colours, default crosshair. */
    public static HudPaint defaults() {
        return of(HudTheme.CLEAN, true, AccessibilityState.DEFAULT, CrosshairStyle.DEFAULT);
    }

    /** Paint for a theme with the user's accessibility state and crosshair style. */
    public static HudPaint of(HudTheme theme, boolean textShadow, AccessibilityState accessibility,
                              CrosshairStyle crosshair) {
        Objects.requireNonNull(accessibility, "accessibility");
        return new HudPaint(theme, textShadow && theme.style().textShadow(), accessibility.reducedTransparency(),
                accessibility.successColor(), accessibility.warningColor(), accessibility.dangerColor(), crosshair);
    }

    /** Copy with another HUD theme. */
    public HudPaint withTheme(HudTheme newTheme) {
        return new HudPaint(newTheme, textShadow, reducedTransparency, success, warning, danger, crosshair);
    }

    /** Copy with the text shadow forced on or off (the theme may still disable it). */
    public HudPaint withTextShadow(boolean on) {
        return new HudPaint(theme, on && theme.style().textShadow(), reducedTransparency, success, warning, danger,
                crosshair);
    }

    /** Copy with another crosshair style. */
    public HudPaint withCrosshair(CrosshairStyle style) {
        return new HudPaint(theme, textShadow, reducedTransparency, success, warning, danger, style);
    }

    // ---- accessors --------------------------------------------------------------------------------------------------

    public HudTheme theme() {
        return theme;
    }

    public HudThemeStyle style() {
        return theme.style();
    }

    /** Inner padding of the theme in GUI pixels. */
    public int padding() {
        return theme.style().padding();
    }

    /** Whether text is drawn with Minecraft's 1 px shadow. */
    public boolean textShadow() {
        return textShadow;
    }

    public boolean reducedTransparency() {
        return reducedTransparency;
    }

    /** Font for labels and secondary text. */
    public FontKind font() {
        return FontKind.UI;
    }

    /** Font for values. */
    public FontKind valueFont() {
        return FontKind.UI_BOLD;
    }

    public int lineHeight() {
        return LINE_HEIGHT;
    }

    public int success() {
        return success;
    }

    public int warning() {
        return warning;
    }

    public int danger() {
        return danger;
    }

    /** Active crosshair style (for the crosshair widget preview). */
    public CrosshairStyle crosshair() {
        return crosshair;
    }

    // ---- colours ----------------------------------------------------------------------------------------------------

    /**
     * Panel colour of a widget: the widget's background RGB with the theme alpha, scaled by how far the user moved
     * the widget's own alpha away from the default (so "100 % opaque" stays opaque in every theme). Reduced
     * transparency makes every panel opaque. Transparent when the widget or the theme has no panel.
     */
    public int panelColor(HudWidgetState state) {
        HudThemeStyle style = theme.style();
        if (!state.backgroundEnabled() || !style.hasPanel()) {
            return Colors.TRANSPARENT;
        }
        if (style.backgroundAlpha() <= 0.0) {
            return Colors.TRANSPARENT;
        }
        if (reducedTransparency) {
            return Colors.opaque(state.backgroundColor());
        }
        float relative = Colors.alphaF(state.backgroundColor()) / DEFAULT_BACKGROUND_ALPHA;
        float alpha = Math.min(1f, (float) style.backgroundAlpha() * relative);
        return Colors.withAlpha(state.backgroundColor(), alpha);
    }

    /**
     * Border colour: the OUTLINE theme follows the widget's accent colour, the other themes use their fixed border;
     * transparent when the theme has no border or the widget's panel is off.
     */
    public int borderColor(HudWidgetState state) {
        HudThemeStyle style = theme.style();
        if (!state.backgroundEnabled() || !style.border()) {
            return Colors.TRANSPARENT;
        }
        if (theme == HudTheme.OUTLINE) {
            return Colors.withAlpha255(state.accentColor(), Colors.alpha(style.borderColor()));
        }
        return style.borderColor();
    }

    /** Secondary text: the widget text colour at 62 %. */
    public int muted(HudWidgetState state) {
        return Colors.withAlpha(state.textColor(), 0.62f * Colors.alphaF(state.textColor()));
    }

    /** Track colour for bars: the widget text colour at 14 %. */
    public int track(HudWidgetState state) {
        return Colors.withAlpha(state.textColor(), 0.14f);
    }

    /** Colour for a 0–1 health-like fraction: success above 50 %, warning above 20 %, danger below. */
    public int fractionColor(double fraction) {
        if (fraction > 0.5) {
            return success;
        }
        return fraction > 0.2 ? warning : danger;
    }

    // ---- drawing helpers ----------------------------------------------------------------------------------------------

    /** Draws the theme panel (background + border) behind a widget of the state's size. */
    public void panel(Canvas canvas, HudWidgetState state) {
        int w = state.width();
        int h = state.height();
        int radius = theme.style().radius();
        int bg = panelColor(state);
        if (Colors.alpha(bg) > 0) {
            canvas.fillRounded(0, 0, w, h, radius, bg);
        }
        int border = borderColor(state);
        if (Colors.alpha(border) > 0) {
            canvas.strokeRounded(0, 0, w, h, radius, border);
        }
    }

    /** Draws label text in the label font. */
    public void text(Canvas canvas, String text, int x, int y, int argb) {
        canvas.text(text, x, y, argb, FontKind.UI, textShadow);
    }

    /** Draws value text in the value font. */
    public void value(Canvas canvas, String text, int x, int y, int argb) {
        canvas.text(text, x, y, argb, FontKind.UI_BOLD, textShadow);
    }

    /** Draws value text right-aligned to {@code rightX}. */
    public void valueRight(Canvas canvas, String text, int rightX, int y, int argb) {
        canvas.text(text, rightX - canvas.textWidth(text, FontKind.UI_BOLD), y, argb, FontKind.UI_BOLD, textShadow);
    }

    /** Draws label text right-aligned to {@code rightX}. */
    public void textRight(Canvas canvas, String text, int rightX, int y, int argb) {
        canvas.text(text, rightX - canvas.textWidth(text, FontKind.UI), y, argb, FontKind.UI, textShadow);
    }

    /** Draws value text horizontally centred on {@code cx}. */
    public void valueCentered(Canvas canvas, String text, int cx, int y, int argb) {
        canvas.text(text, cx - canvas.textWidth(text, FontKind.UI_BOLD) / 2, y, argb, FontKind.UI_BOLD, textShadow);
    }

    /** Draws a thin horizontal bar: track plus a fraction fill. */
    public void bar(Canvas canvas, int x, int y, int w, int h, double fraction, int trackColor, int fillColor) {
        if (w <= 0 || h <= 0) {
            return;
        }
        canvas.fillRounded(x, y, w, h, Math.min(1, h / 2), trackColor);
        int fill = (int) Math.round(w * Math.max(0.0, Math.min(1.0, fraction)));
        if (fill > 0) {
            canvas.fillRounded(x, y, fill, h, Math.min(1, h / 2), fillColor);
        }
    }

    /** Top y of a single text line vertically centred in the widget. */
    public int centeredLineY(int height) {
        return (height - LINE_HEIGHT) / 2;
    }
}
