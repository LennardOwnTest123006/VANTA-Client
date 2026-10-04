package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.hud.render.HudWidgetRenderer;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TextMetrics;

/**
 * Base for the "label value secondary" widgets (FPS, ping, CPS, memory, …): an accent-coloured label, a bold value
 * and an optional muted secondary string flow left to right on one line, vertically centred in the panel.
 * <p>
 * When the content is wider than the widget, the secondary text is dropped first, then the label, and finally the
 * value is ellipsised. Subclasses may contribute extra lines below the first one (see {@link #extraLines}).
 */
public abstract class AbstractLineWidget implements HudWidgetRenderer {

    /** Label text, or {@code null} to show none. */
    protected abstract String label(HudWidgetState state, HudData data);

    /** The main value; never {@code null}. */
    protected abstract String value(HudWidgetState state, HudData data);

    /** Secondary (muted) text after the value, or {@code null}. */
    protected String secondary(HudWidgetState state, HudData data) {
        return null;
    }

    /** Colour of the value text (default: the widget text colour). */
    protected int valueColor(HudWidgetState state, HudData data, HudPaint paint) {
        return state.textColor();
    }

    /** Colour of the label text (default: the widget accent colour). */
    protected int labelColor(HudWidgetState state, HudPaint paint) {
        return state.accentColor();
    }

    /** Number of additional text lines drawn below the main line for the given state (default none). */
    protected int extraLines(HudWidgetState state, HudData data) {
        return 0;
    }

    /**
     * Draws the extra lines; {@code y} is the top of the first extra line, {@code availW} the inner width.
     */
    protected void renderExtra(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint, int x, int y,
                               int availW) {
    }

    /** Width of the extra lines in unscaled pixels (for {@link #preferredSize}); default 0. */
    protected int extraWidth(TextMetrics metrics, HudWidgetState state, HudData data) {
        return 0;
    }

    @Override
    public Size preferredSize(TextMetrics metrics, HudWidgetState state, HudData data, HudPaint paint) {
        int pad = paint.padding();
        int w = 0;
        String label = label(state, data);
        if (label != null && !label.isEmpty()) {
            w += metrics.textWidth(label, FontKind.UI) + HudPaint.GAP;
        }
        w += metrics.textWidth(value(state, data), FontKind.UI_BOLD);
        String secondary = secondary(state, data);
        if (secondary != null && !secondary.isEmpty()) {
            w += HudPaint.GAP + metrics.textWidth(secondary, FontKind.UI);
        }
        w = Math.max(w, extraWidth(metrics, state, data));
        int lines = 1 + Math.max(0, extraLines(state, data));
        int h = pad * 2 + lines * HudPaint.LINE_HEIGHT + (lines - 1);
        return new Size(w + pad * 2, h);
    }

    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        paint.panel(canvas, state);
        int pad = paint.padding();
        int availW = Math.max(0, state.width() - pad * 2);
        int lines = 1 + Math.max(0, extraLines(state, data));
        int block = lines * HudPaint.LINE_HEIGHT + (lines - 1);
        int y = Math.max(0, (state.height() - block) / 2);
        renderLine(canvas, state, data, paint, pad, y, availW);
        if (lines > 1) {
            renderExtra(canvas, state, data, paint, pad, y + HudPaint.LINE_HEIGHT + 1, availW);
        }
    }

    /** Draws the main line at {@code (x, y)} inside {@code availW} pixels. */
    protected void renderLine(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint, int x, int y,
                              int availW) {
        String label = label(state, data);
        if (label != null && label.isEmpty()) {
            label = null;
        }
        String value = value(state, data);
        String secondary = secondary(state, data);
        if (secondary != null && secondary.isEmpty()) {
            secondary = null;
        }
        int labelW = label == null ? 0 : canvas.textWidth(label, FontKind.UI) + HudPaint.GAP;
        int valueW = canvas.textWidth(value, FontKind.UI_BOLD);
        int secondaryW = secondary == null ? 0 : HudPaint.GAP + canvas.textWidth(secondary, FontKind.UI);
        if (labelW + valueW + secondaryW > availW && secondary != null) {
            secondary = null;
            secondaryW = 0;
        }
        if (labelW + valueW + secondaryW > availW && label != null) {
            label = null;
            labelW = 0;
        }
        int cursor = x;
        if (label != null) {
            paint.text(canvas, label, cursor, y, labelColor(state, paint));
            cursor += labelW;
        }
        int valueMax = Math.max(0, availW - labelW - secondaryW);
        if (valueW > valueMax) {
            value = canvas.textClipped(value, valueMax, FontKind.UI_BOLD);
            valueW = canvas.textWidth(value, FontKind.UI_BOLD);
        }
        paint.value(canvas, value, cursor, y, valueColor(state, data, paint));
        cursor += valueW;
        if (secondary != null) {
            paint.text(canvas, secondary, cursor + HudPaint.GAP, y, paint.muted(state));
        }
    }
}
