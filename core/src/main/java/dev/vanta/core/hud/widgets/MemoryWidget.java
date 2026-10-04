package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.Canvas;

/**
 * Java heap usage: percentage ({@code showPercent}) or "used / allocated MB" ({@code showMax} compares with the
 * maximum heap instead), plus a 1 px usage bar along the bottom edge of the panel.
 */
public final class MemoryWidget extends AbstractLineWidget {

    @Override
    public HudWidgetType type() {
        return HudWidgetType.MEMORY;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return Lang.tr("vanta.hud.label.memory");
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        String cached = data.cached(state.id());
        if (cached != null) {
            return cached;
        }
        String text;
        if (state.propBool("showPercent")) {
            text = Lang.tr("vanta.hud.label.memory_percent", data.memoryPercent());
        } else {
            text = usageText(state, data);
        }
        return data.cache(state.id(), text);
    }

    @Override
    protected String secondary(HudWidgetState state, HudData data) {
        if (!state.propBool("showPercent")) {
            return null;
        }
        String key = state.id() + ".secondary";
        String cached = data.cached(key);
        if (cached == null) {
            cached = data.cache(key, usageText(state, data));
        }
        return cached;
    }

    private static String usageText(HudWidgetState state, HudData data) {
        long reference = state.propBool("showMax") ? data.memoryMax() : data.memoryAllocated();
        return Lang.tr("vanta.hud.label.memory_value", HudData.toMiB(data.memoryUsed()), HudData.toMiB(reference));
    }

    @Override
    public void render(Canvas canvas, HudWidgetState state, HudData data, HudPaint paint) {
        super.render(canvas, state, data, paint);
        int pad = paint.padding();
        int barY = state.height() - Math.max(1, pad) ;
        int barX = pad;
        int barW = state.width() - pad * 2;
        if (barW > 4 && barY > HudPaint.LINE_HEIGHT) {
            double fraction = data.memoryFraction();
            int color = fraction > 0.9 ? paint.danger() : fraction > 0.75 ? paint.warning() : state.accentColor();
            paint.bar(canvas, barX, barY - 1, barW, 1, fraction, paint.track(state), color);
        }
    }
}
