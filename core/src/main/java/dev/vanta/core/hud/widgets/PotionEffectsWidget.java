package dev.vanta.core.hud.widgets;

import dev.vanta.core.bridge.EffectInfo;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.hud.render.HudWidgetRenderer;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.TextMetrics;

import java.util.List;

/**
 * Active status effects, one row each: a dot in the effect colour, the name with its level ("Speed II") and the
 * remaining time ({@code showDuration}; infinite effects show ∞). {@code compact} drops the dot and tightens the
 * rows. Rows that do not fit are cut off; no effects shows "No effects".
 */
public final class PotionEffectsWidget implements HudWidgetRenderer {

    /** Row height with the colour dot. */
    public static final int ROW_HEIGHT = 11;
    /** Row height in compact mode. */
    public static final int COMPACT_ROW_HEIGHT = 9;
    private static final int DOT = 5;

    @Override
    public HudWidgetType type() {
        return HudWidgetType.POTION_EFFECTS;
    }

    private static int rowHeight(HudWidgetState state) {
        return state.propBool("compact") ? COMPACT_ROW_HEIGHT : ROW_HEIGHT;
    }

    private static String name(EffectInfo effect) {
        String level = effect.levelLabel();
        return level.isEmpty() ? effect.name() : Lang.tr("vanta.hud.label.effect_level", effect.name(), level);
    }

    @Override
    public Size preferredSize(TextMetrics metrics, HudWidgetState state, HudData data, HudPaint paint) {
        int pad = paint.padding();
        List<EffectInfo> effects = data.effects();
        if (effects.isEmpty()) {
            return new Size(metrics.textWidth(Lang.tr("vanta.hud.label.no_effects"), FontKind.UI) + pad * 2,
                    pad * 2 + HudPaint.LINE_HEIGHT);
        }
        boolean compact = state.propBool("compact");
        boolean duration = state.propBool("showDuration");
        int w = 0;
        for (EffectInfo effect : effects) {
            int rowW = (compact ? 0 : DOT + HudPaint.GAP) + metrics.textWidth(name(effect), FontKind.UI);
            if (duration) {
                rowW += HudPaint.GAP + metrics.textWidth(effect.formatDuration(), FontKind.UI_BOLD);
            }
            w = Math.max(w, rowW);
        }
        int h = effects.size() * rowHeight(state) - (compact ? 0 : 2);
        return new Size(w + pad * 2, Math.max(HudPaint.LINE_HEIGHT, h) + pad * 2);
    }

    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        paint.panel(canvas, state);
        int pad = paint.padding();
        int availW = Math.max(0, state.width() - pad * 2);
        int right = pad + availW;
        List<EffectInfo> effects = data.effects();
        if (effects.isEmpty()) {
            int y = paint.centeredLineY(state.height());
            paint.text(canvas, canvas.textClipped(Lang.tr("vanta.hud.label.no_effects"), availW, FontKind.UI), pad, y,
                    paint.muted(state));
            return;
        }
        boolean compact = state.propBool("compact");
        boolean duration = state.propBool("showDuration");
        int rowH = rowHeight(state);
        int y = pad;
        int bottom = state.height() - pad;
        for (EffectInfo effect : effects) {
            if (y + HudPaint.LINE_HEIGHT > bottom + 1) {
                break;
            }
            int x = pad;
            if (!compact) {
                canvas.fillRounded(x, y + (HudPaint.LINE_HEIGHT - DOT) / 2 + 1, DOT, DOT, 2,
                        Colors.opaque(effect.color()));
                x += DOT + HudPaint.GAP;
            }
            int timeW = 0;
            if (duration) {
                String time = effect.formatDuration();
                timeW = canvas.textWidth(time, FontKind.UI_BOLD) + HudPaint.GAP;
                paint.valueRight(canvas, time, right, y, effect.infinite() ? paint.muted(state) : state.textColor());
            }
            String name = canvas.textClipped(name(effect), Math.max(0, right - x - timeW), FontKind.UI);
            paint.text(canvas, name, x, y, state.textColor());
            y += rowH;
        }
    }
}
