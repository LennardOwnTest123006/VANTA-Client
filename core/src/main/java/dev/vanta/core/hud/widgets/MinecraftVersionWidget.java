package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.hud.render.HudPaint;
import dev.vanta.core.i18n.Lang;

/** "Minecraft 1.21.11", with the Fabric Loader version appended when {@code showFabric} is on. */
public final class MinecraftVersionWidget extends AbstractLineWidget {

    @Override
    public HudWidgetType type() {
        return HudWidgetType.MINECRAFT_VERSION;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return null;
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        String cached = data.cached(state.id());
        if (cached == null) {
            String text = state.propBool("showFabric")
                    ? Lang.tr("vanta.hud.label.version_fabric", data.minecraftVersion(), data.fabricLoaderVersion())
                    : Lang.tr("vanta.hud.label.version", data.minecraftVersion());
            cached = data.cache(state.id(), text);
        }
        return cached;
    }

    @Override
    protected int valueColor(HudWidgetState state, HudData data, HudPaint paint) {
        return state.textColor();
    }
}
