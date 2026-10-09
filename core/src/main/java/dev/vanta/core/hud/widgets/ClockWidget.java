package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudData;
import dev.vanta.core.i18n.Lang;

/**
 * System time or the in-game time of day, both in 12-hour time with AM/PM (optionally with seconds for the system
 * time), selected by the {@code format} property. A layout saved with the former 24-hour format is read as the system
 * time in AM/PM, because the property no longer offers that value and falls back to its default.
 */
public final class ClockWidget extends AbstractLineWidget {

    /** {@code format} value for the 12-hour system clock. */
    public static final String SYSTEM_12H = "system_12h";
    /** {@code format} value for the in-game time. */
    public static final String GAME_TIME = "game_time";

    @Override
    public HudWidgetType type() {
        return HudWidgetType.CLOCK;
    }

    @Override
    protected String label(HudWidgetState state, HudData data) {
        return Lang.tr("vanta.hud.label.clock");
    }

    @Override
    protected String value(HudWidgetState state, HudData data) {
        String format = state.prop("format");
        boolean seconds = state.propBool("showSeconds");
        return switch (format) {
            case GAME_TIME -> data.gameClock();
            default -> data.clock12(seconds);
        };
    }
}
