package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.hud.render.HudWidgetRenderer;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TextMetrics;

/**
 * Player position. {@code singleLine} shows "X 128  Y 64  Z -221" on one line, otherwise one axis per line;
 * {@code decimals} (0–2) selects block coordinates or fractional ones and {@code showDimension} appends the
 * dimension name.
 */
public final class CoordinatesWidget implements HudWidgetRenderer {

    private static final int SEGMENT_GAP = 6;
    private static final String[] AXIS_KEYS = {"vanta.hud.label.x", "vanta.hud.label.y", "vanta.hud.label.z"};

    @Override
    public HudWidgetType type() {
        return HudWidgetType.COORDINATES;
    }

    @Override
    public Size preferredSize(TextMetrics metrics, HudWidgetState state, HudData data, HudPaint paint) {
        int pad = paint.padding();
        int decimals = state.propInt("decimals");
        boolean dimension = state.propBool("showDimension");
        if (state.propBool("singleLine")) {
            int w = 0;
            for (int axis = 0; axis < 3; axis++) {
                if (axis > 0) {
                    w += SEGMENT_GAP;
                }
                w += segmentWidth(metrics, axis, data.coordinate(axis, decimals));
            }
            if (dimension) {
                w += SEGMENT_GAP + metrics.textWidth(data.dimensionName(), FontKind.UI);
            }
            return new Size(w + pad * 2, pad * 2 + HudPaint.LINE_HEIGHT);
        }
        int w = 0;
        for (int axis = 0; axis < 3; axis++) {
            w = Math.max(w, segmentWidth(metrics, axis, data.coordinate(axis, decimals)));
        }
        int lines = 3;
        if (dimension) {
            w = Math.max(w, metrics.textWidth(data.dimensionName(), FontKind.UI));
            lines++;
        }
        return new Size(w + pad * 2, pad * 2 + lines * HudPaint.LINE_HEIGHT + (lines - 1));
    }

    private static int segmentWidth(TextMetrics metrics, int axis, String value) {
        return metrics.textWidth(Lang.tr(AXIS_KEYS[axis]), FontKind.UI) + HudPaint.GAP
                + metrics.textWidth(value, FontKind.UI_BOLD);
    }

    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        paint.panel(canvas, state);
        int pad = paint.padding();
        int decimals = state.propInt("decimals");
        boolean dimension = state.propBool("showDimension");
        int availW = Math.max(0, state.width() - pad * 2);
        int right = pad + availW;
        if (state.propBool("singleLine")) {
            int y = paint.centeredLineY(state.height());
            int x = pad;
            for (int axis = 0; axis < 3 && x < right; axis++) {
                x = segment(canvas, state, paint, axis, data.coordinate(axis, decimals), x, y, right) + SEGMENT_GAP;
            }
            if (dimension && x < right) {
                String name = canvas.textClipped(data.dimensionName(), right - x, FontKind.UI);
                paint.text(canvas, name, x, y, paint.muted(state));
            }
            return;
        }
        int lines = dimension ? 4 : 3;
        int block = lines * HudPaint.LINE_HEIGHT + (lines - 1);
        int y = Math.max(0, (state.height() - block) / 2);
        for (int axis = 0; axis < 3; axis++) {
            segment(canvas, state, paint, axis, data.coordinate(axis, decimals), pad, y, right);
            y += HudPaint.LINE_HEIGHT + 1;
        }
        if (dimension) {
            paint.text(canvas, canvas.textClipped(data.dimensionName(), availW, FontKind.UI), pad, y,
                    paint.muted(state));
        }
    }

    /** Draws "X value" at (x, y) and returns the x after the value. */
    private static int segment(Canvas canvas, HudWidgetState state, HudPaint paint, int axis, String value, int x,
                               int y, int right) {
        String label = Lang.tr(AXIS_KEYS[axis]);
        int labelW = canvas.textWidth(label, FontKind.UI);
        if (x + labelW > right) {
            return right;
        }
        paint.text(canvas, label, x, y, state.accentColor());
        int vx = x + labelW + HudPaint.GAP;
        String shown = canvas.textClipped(value, Math.max(0, right - vx), FontKind.UI_BOLD);
        paint.value(canvas, shown, vx, y, state.textColor());
        return vx + canvas.textWidth(shown, FontKind.UI_BOLD);
    }
}
