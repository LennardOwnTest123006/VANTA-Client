package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.i18n.Lang;

/** The biome the player stands in, prettified from its id; {@code showNamespace} appends the namespace. */
public final class BiomeWidget extends AbstractLineWidget {

    @Override
    public HudWidgetType type() {
        return HudWidgetType.BIOME;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return Lang.tr("vanta.hud.label.biome");
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        return data.biomeName();
    }

    @Override
    protected String secondary(HudWidgetState state, HudData data) {
        if (!state.propBool("showNamespace")) {
            return null;
        }
        String namespace = data.biomeNamespace();
        return namespace.isEmpty() ? null : namespace;
    }
}
