package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.i18n.Lang;

/** Number of entities loaded in the client world. */
public final class EntityCountWidget extends AbstractLineWidget {

    @Override
    public HudWidgetType type() {
        return HudWidgetType.ENTITY_COUNT;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return state.propBool("showLabel") ? Lang.tr("vanta.hud.label.entities") : null;
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        return Integer.toString(data.entityCount());
    }
}
