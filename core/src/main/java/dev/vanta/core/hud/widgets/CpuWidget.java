package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.i18n.Lang;

/** Process CPU load in percent, or "n/a" (muted) when the platform does not expose it. */
public final class CpuWidget extends AbstractLineWidget {

    @Override
    public HudWidgetType type() {
        return HudWidgetType.CPU;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return state.propBool("showLabel") ? Lang.tr("vanta.hud.label.cpu") : null;
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        if (data.cpuLoad().isEmpty()) {
            return Lang.tr("vanta.common.not_available");
        }
        String cached = data.cached(state.id());
        if (cached == null) {
            int percent = (int) Math.round(Math.max(0.0, Math.min(1.0, data.cpuLoad().getAsDouble())) * 100.0);
            cached = data.cache(state.id(), Lang.tr("vanta.hud.label.cpu_value", percent));
        }
        return cached;
    }

    @Override
    protected int valueColor(HudWidgetState state, HudData data, HudPaint paint) {
        return data.cpuLoad().isEmpty() ? paint.muted(state) : state.textColor();
    }
}
