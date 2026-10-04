package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.i18n.Lang;

/**
 * Current server: the saved name with the address as secondary text. Turning {@code showAddress} off hides the
 * address (and shows the name only), which streamers use to keep private servers private. Singleplayer worlds show
 * "Singleplayer".
 */
public final class ServerWidget extends AbstractLineWidget {

    @Override
    public HudWidgetType type() {
        return HudWidgetType.SERVER;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return Lang.tr("vanta.hud.label.server");
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        if (data.singleplayer() || (data.serverName().isEmpty() && data.serverAddress().isEmpty())) {
            return Lang.tr("vanta.hud.label.singleplayer");
        }
        if (data.serverName().isPresent() && !data.serverName().get().isBlank()) {
            return data.serverName().get();
        }
        if (state.propBool("showAddress")) {
            return data.serverAddress().orElse(Lang.tr("vanta.hud.label.unknown"));
        }
        return Lang.tr("vanta.hud.label.server_hidden");
    }

    @Override
    protected String secondary(HudWidgetState state, HudData data) {
        if (data.singleplayer() || !state.propBool("showAddress")) {
            return null;
        }
        boolean nameShown = data.serverName().isPresent() && !data.serverName().get().isBlank();
        return nameShown ? data.serverAddress().orElse(null) : null;
    }

    @Override
    protected int valueColor(HudWidgetState state, HudData data, HudPaint paint) {
        return data.singleplayer() ? paint.muted(state) : state.textColor();
    }
}
