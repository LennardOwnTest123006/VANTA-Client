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
import java.util.Locale;

/**
 * Vanta Lab "Frame time HUD graph": the last {@link HudWidgetType#FRAMETIME_GRAPH_SAMPLES} frame times as a bar
 * sparkline (newest at the right) with the p50 and p99 frame times as labels. Frames slower than
 * {@value #HITCH_MS} ms are drawn in the danger colour. The samples come from the client's FrameTimer through
 * {@code PerformanceCenter.frameTimes()} and are captured once per tick into {@link HudData}; nothing is estimated
 * and an empty history shows "n/a".
 * <p>
 * The widget is drawn only while {@code LabFeature.FRAMETIME_GRAPH} is on; {@code HudRenderer.isDrawable} skips it
 * otherwise.
 */
public final class FrametimeGraphWidget implements HudWidgetRenderer {
    /** Frames slower than this count as hitches (and are drawn in the danger colour). */
    public static final double HITCH_MS = 50.0;
    /** Smallest graph height in unscaled pixels. */
    public static final int MIN_GRAPH_H = 12;
    /** The scale never shows less than this (a 60 fps frame is then half the height). */
    public static final double MIN_RANGE_MS = 1000.0 / 30.0;

    @Override
    public HudWidgetType type() {
        return HudWidgetType.FRAMETIME_GRAPH;
    }

    private static boolean labels(HudWidgetState state) {
        return state.propBool("showLabels");
    }

    private static String p50Text(HudData data) {
        String cached = data.cached("frametime.p50");
        return cached != null ? cached : data.cache("frametime.p50", ms(data.frameTimeP50()));
    }

    private static String p99Text(HudData data) {
        String cached = data.cached("frametime.p99");
        return cached != null ? cached : data.cache("frametime.p99", ms(data.frameTimeP99()));
    }

    private static String ms(double value) {
        return Lang.tr("vanta.hud.label.frame_time", String.format(Locale.ROOT, "%.1f", value));
    }

    @Override
    public Size preferredSize(TextMetrics metrics, HudWidgetState state, HudData data, HudPaint paint) {
        int pad = paint.padding();
        int w = HudWidgetType.FRAMETIME_GRAPH.defaultWidth() - pad * 2;
        int h = HudWidgetType.FRAMETIME_GRAPH.defaultHeight() - pad * 2;
        if (labels(state)) {
            int labelW = metrics.textWidth(Lang.tr("vanta.hud.label.p50"), FontKind.UI) + HudPaint.GAP
                    + metrics.textWidth(p50Text(data), FontKind.UI_BOLD) + HudPaint.GAP * 2
                    + metrics.textWidth(Lang.tr("vanta.hud.label.p99"), FontKind.UI) + HudPaint.GAP
                    + metrics.textWidth(p99Text(data), FontKind.UI_BOLD);
            w = Math.max(w, labelW);
        }
        return new Size(w + pad * 2, h + pad * 2);
    }

    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        paint.panel(canvas, state);
        int pad = paint.padding();
        int x = pad;
        int y = pad;
        int availW = Math.max(0, state.width() - pad * 2);
        int availH = Math.max(0, state.height() - pad * 2);
        boolean showLabels = labels(state) && availH >= HudPaint.LINE_HEIGHT + 1 + MIN_GRAPH_H;
        if (showLabels) {
            String p50Label = Lang.tr("vanta.hud.label.p50");
            String p50 = p50Text(data);
            String p99Label = Lang.tr("vanta.hud.label.p99");
            String p99 = p99Text(data);
            int rightW = canvas.textWidth(p99Label, FontKind.UI) + HudPaint.GAP + canvas.textWidth(p99, FontKind.UI_BOLD);
            paint.text(canvas, p50Label, x, y, state.accentColor());
            paint.value(canvas, p50, x + canvas.textWidth(p50Label, FontKind.UI) + HudPaint.GAP, y, state.textColor());
            if (rightW <= availW / 2) {
                int rx = x + availW - rightW;
                paint.text(canvas, p99Label, rx, y, state.accentColor());
                paint.value(canvas, p99, rx + canvas.textWidth(p99Label, FontKind.UI) + HudPaint.GAP, y,
                        data.frameTimeP99() > HITCH_MS ? paint.danger() : state.textColor());
            }
            y += HudPaint.LINE_HEIGHT + 1;
            availH -= HudPaint.LINE_HEIGHT + 1;
        }
        if (availW <= 1 || availH <= 1) {
            return;
        }
        int count = data.frameTimeSampleCount();
        if (count == 0) {
            paint.text(canvas, canvas.textClipped(Lang.tr("vanta.common.not_available"), availW, FontKind.UI), x,
                    y + Math.max(0, (availH - HudPaint.LINE_HEIGHT) / 2), paint.muted(state));
            return;
        }
        double max = MIN_RANGE_MS;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, data.frameTimeSample(i));
        }
        int baseline = y + availH;
        int track = paint.track(state);
        canvas.fill(x, baseline - 1, availW, 1, track);
        // One column per pixel, newest sample at the right edge; a column shows the slowest sample it covers so
        // single hitches never disappear when more samples than pixels exist.
        int columns = Math.min(availW, count);
        int start = x + availW - columns;
        for (int col = 0; col < columns; col++) {
            int from = (int) Math.floor(col * (double) count / columns);
            int to = Math.max(from + 1, (int) Math.floor((col + 1) * (double) count / columns));
            double value = 0;
            for (int i = from; i < to && i < count; i++) {
                value = Math.max(value, data.frameTimeSample(i));
            }
            int h = (int) Math.round(Math.min(1.0, value / max) * (availH - 1));
            if (h <= 0) {
                continue;
            }
            int color = value > HITCH_MS ? paint.danger() : state.accentColor();
            canvas.fill(start + col, baseline - 1 - h, 1, h, color);
        }
    }
}
