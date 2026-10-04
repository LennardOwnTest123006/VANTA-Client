package dev.vanta.core.hud.widgets;

import dev.vanta.core.bridge.Cardinal;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TextMetrics;

import java.util.Optional;

/**
 * Cardinal direction with the yaw ({@code showYaw}) and an optional axis hint ({@code showAxis}). When the widget
 * is tall enough for a second row, a scrolling compass strip (N E S W with the facing marker) is drawn above the
 * text: resize the widget in the editor to get the strip variant.
 */
public final class DirectionWidget extends AbstractLineWidget {

    /** Height of the compass strip in GUI pixels. */
    public static final int STRIP_HEIGHT = 11;
    /** Degrees visible across the strip. */
    public static final float STRIP_DEGREES = 180f;
    private static final Cardinal[] CARDINALS = Cardinal.values();
    /** Yaw at which each cardinal is straight ahead (Minecraft convention: 0 = south, 90 = west). */
    private static final float[] CARDINAL_YAW = {180f, 270f, 0f, 90f};

    @Override
    public HudWidgetType type() {
        return HudWidgetType.DIRECTION;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return Lang.tr("vanta.hud.label.direction");
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        Optional<Cardinal> facing = data.facing();
        return facing.map(c -> Lang.tr(c.langKey())).orElseGet(() -> Lang.tr("vanta.hud.label.unknown"));
    }

    @Override
    protected String secondary(HudWidgetState state, HudData data) {
        if (!state.propBool("showYaw") || !data.inWorld()) {
            return null;
        }
        String cached = data.cached(state.id());
        if (cached == null) {
            long yaw = Math.round(Cardinal.normalizeYaw(data.yaw()));
            cached = data.cache(state.id(), Lang.tr("vanta.hud.label.yaw", yaw));
        }
        return cached;
    }

    @Override
    protected int extraLines(HudWidgetState state, HudData data) {
        return state.propBool("showAxis") && data.facing().isPresent() ? 1 : 0;
    }

    @Override
    protected void renderExtra(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint, int x, int y,
                               int availW) {
        String axis = canvas.textClipped(Lang.tr(data.facing().orElseThrow().axisKey()), availW, FontKind.UI);
        paint.text(canvas, axis, x, y, paint.muted(state));
    }

    @Override
    protected int extraWidth(TextMetrics metrics, HudWidgetState state, HudData data) {
        return extraLines(state, data) > 0 ? metrics.textWidth(Lang.tr(data.facing().orElseThrow().axisKey()),
                FontKind.UI) : 0;
    }

    /** Whether the widget is tall enough to show the compass strip above the text. */
    public boolean hasStrip(HudWidgetState state, HudData data, HudPaint paint) {
        int lines = 1 + extraLines(state, data);
        int text = lines * HudPaint.LINE_HEIGHT + (lines - 1);
        return state.height() >= paint.padding() * 2 + STRIP_HEIGHT + 2 + text && state.width() >= 40;
    }

    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        if (!hasStrip(state, data, paint)) {
            super.render(canvas, state, data, paint);
            return;
        }
        paint.panel(canvas, state);
        int pad = paint.padding();
        int availW = Math.max(0, state.width() - pad * 2);
        int lines = 1 + extraLines(state, data);
        int textBlock = lines * HudPaint.LINE_HEIGHT + (lines - 1);
        int total = STRIP_HEIGHT + 2 + textBlock;
        int top = Math.max(0, (state.height() - total) / 2);
        strip(canvas, state, data, paint, pad, top, availW);
        int y = top + STRIP_HEIGHT + 2;
        renderLine(canvas, state, data, paint, pad, y, availW);
        if (lines > 1) {
            renderExtra(canvas, state, data, paint, pad, y + HudPaint.LINE_HEIGHT + 1, availW);
        }
    }

    private static void strip(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint, int x, int y,
                              int w) {
        int muted = paint.muted(state);
        int faint = Colors.withAlpha(state.textColor(), 0.28f);
        // Baseline and centre marker.
        canvas.fill(x, y + STRIP_HEIGHT - 1, w, 1, faint);
        int cx = x + w / 2;
        canvas.fill(cx, y, 1, STRIP_HEIGHT, state.accentColor());
        if (!data.inWorld()) {
            return;
        }
        float pxPerDegree = w / STRIP_DEGREES;
        canvas.pushScissor(new Rect(x, y, w, STRIP_HEIGHT));
        for (int step = 0; step < 8; step++) {
            float angle = step * 45f;
            float delta = (float) Cardinal.normalizeYaw(angle - data.yaw());
            int px = cx + Math.round(delta * pxPerDegree);
            if (step % 2 == 1) {
                canvas.fill(px, y + STRIP_HEIGHT - 3, 1, 2, faint);
            }
        }
        for (int i = 0; i < CARDINALS.length; i++) {
            float delta = (float) Cardinal.normalizeYaw(CARDINAL_YAW[i] - data.yaw());
            int px = cx + Math.round(delta * pxPerDegree);
            String letter = CARDINALS[i].letter();
            int lw = canvas.textWidth(letter, FontKind.UI_BOLD);
            boolean facing = Math.abs(delta) < 22.5f;
            canvas.text(letter, px - lw / 2, y, facing ? state.textColor() : muted, FontKind.UI_BOLD,
                    paint.textShadow());
        }
        canvas.popScissor();
    }
}
