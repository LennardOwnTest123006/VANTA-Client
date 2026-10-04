package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.i18n.Lang;

/** Clicks per second: left button, and the right button too when {@code showRight} is on ("6 | 3"). */
public final class CpsWidget extends AbstractLineWidget {

    @Override
    public HudWidgetType type() {
        return HudWidgetType.CPS;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return state.propBool("showLabel") ? Lang.tr("vanta.hud.label.cps") : null;
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        if (!state.propBool("showRight")) {
            return Integer.toString(data.cpsLeft());
        }
        String cached = data.cached(state.id());
        if (cached == null) {
            cached = data.cache(state.id(), Lang.tr("vanta.hud.label.cps_pair", data.cpsLeft(), data.cpsRight()));
        }
        return cached;
    }
}
