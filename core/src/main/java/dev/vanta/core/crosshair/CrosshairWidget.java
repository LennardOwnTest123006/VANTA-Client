package dev.vanta.core.crosshair;

import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import java.util.Objects;

/**
 * The switch between Minecraft's crosshair and VANTA's: the custom crosshair is drawn while the HUD layout has an
 * enabled {@link HudWidgetType#CROSSHAIR} widget (what {@code CrosshairRenderer.isCustomEnabled()} reads), and vanilla
 * draws its own otherwise. Shared by the crosshair customizer's "Use vanilla crosshair" toggle and the Vanta Nexus
 * action {@code crosshair.set}.
 */
public final class CrosshairWidget {
    private CrosshairWidget() {
    }

    /** True when the layout has no enabled crosshair widget, so vanilla draws the crosshair. */
    public static boolean isVanilla(HudLayout layout) {
        Objects.requireNonNull(layout, "layout");
        for (HudWidgetState widget : layout.widgets()) {
            if (widget.type() == HudWidgetType.CROSSHAIR) {
                return !widget.enabled();
            }
        }
        return true;
    }

    /**
     * The layout with the first crosshair widget switched: {@code vanilla} disables it, otherwise it is enabled (and
     * added with its defaults when the layout has none). Returns the same layout when nothing changes.
     */
    public static HudLayout withVanilla(HudLayout layout, boolean vanilla) {
        Objects.requireNonNull(layout, "layout");
        HudWidgetState crosshair = null;
        for (HudWidgetState widget : layout.widgets()) {
            if (widget.type() == HudWidgetType.CROSSHAIR) {
                crosshair = widget;
                break;
            }
        }
        if (crosshair == null) {
            if (vanilla) {
                return layout;
            }
            crosshair = HudWidgetState.defaults(layout.nextId(HudWidgetType.CROSSHAIR), HudWidgetType.CROSSHAIR);
        } else if (crosshair.enabled() == !vanilla) {
            return layout;
        }
        return layout.with(crosshair.withEnabled(!vanilla));
    }
}
