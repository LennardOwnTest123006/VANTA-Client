package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.i18n.Lang;

/**
 * Latency to the server, colour-coded: green up to {@value #GOOD_MS} ms, amber up to {@value #FAIR_MS} ms, red
 * above. Singleplayer worlds show "Local", an unknown latency shows a dash; both in the muted colour.
 */
public final class PingWidget extends AbstractLineWidget {

    /** Highest latency shown in the success colour. */
    public static final int GOOD_MS = 80;
    /** Highest latency shown in the warning colour. */
    public static final int FAIR_MS = 150;

    @Override
    public HudWidgetType type() {
        return HudWidgetType.PING;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return state.propBool("showLabel") ? Lang.tr("vanta.hud.label.ping") : null;
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        if (data.singleplayer()) {
            return Lang.tr("vanta.hud.label.ping_local");
        }
        if (data.pingMillis().isEmpty()) {
            return Lang.tr("vanta.hud.label.ping_unavailable");
        }
        String cached = data.cached(state.id());
        if (cached == null) {
            cached = data.cache(state.id(), Lang.tr("vanta.hud.label.ping_value", data.pingMillis().getAsInt()));
        }
        return cached;
    }

    @Override
    protected int valueColor(HudWidgetState state, HudData data, HudPaint paint) {
        if (data.singleplayer() || data.pingMillis().isEmpty()) {
            return paint.muted(state);
        }
        return colorFor(data.pingMillis().getAsInt(), paint);
    }

    /** Status colour for a latency. */
    public static int colorFor(int pingMs, HudPaint paint) {
        if (pingMs <= GOOD_MS) {
            return paint.success();
        }
        return pingMs <= FAIR_MS ? paint.warning() : paint.danger();
    }
}
