package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.TextMetrics;

import java.util.Locale;

/**
 * Frames per second with an optional frame time ({@code showFrameTime}). When the widget is tall enough for two
 * lines and frame history exists, the second line shows the 1 % low frame rate (the stutter indicator).
 */
public final class FpsWidget extends AbstractLineWidget {

    @Override
    public HudWidgetType type() {
        return HudWidgetType.FPS;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return state.propBool("showLabel") ? Lang.tr("vanta.hud.label.fps") : null;
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        return Integer.toString(data.fps());
    }

    @Override
    protected String secondary(HudWidgetState state, HudData data) {
        if (!state.propBool("showFrameTime")) {
            return null;
        }
        String cached = data.cached(state.id());
        if (cached == null) {
            cached = data.cache(state.id(),
                    Lang.tr("vanta.hud.label.frame_time", String.format(Locale.ROOT, "%.1f", data.frameTimeMs())));
        }
        return cached;
    }

    /** The 1 % low line is shown when the widget has room for two lines and frame history exists. */
    @Override
    protected int extraLines(HudWidgetState state, HudData data) {
        boolean room = state.height() >= HudPaint.LINE_HEIGHT * 2 + 1 + 2;
        return room && data.onePercentLowFps() > 0 ? 1 : 0;
    }

    @Override
    protected void renderExtra(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint, int x, int y,
                               int availW) {
        String text = canvas.textClipped(lowLine(data), availW, FontKind.UI);
        paint.text(canvas, text, x, y, paint.muted(state));
    }

    @Override
    protected int extraWidth(TextMetrics metrics, HudWidgetState state, HudData data) {
        return extraLines(state, data) > 0 ? metrics.textWidth(lowLine(data), FontKind.UI) : 0;
    }

    private static String lowLine(HudData data) {
        return Lang.tr("vanta.hud.label.one_percent_low", Integer.toString((int) Math.round(data.onePercentLowFps())));
    }
}
