package dev.vanta.core.cosmetics;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/**
 * Visual style applied to every HUD widget. Each theme is a fixed set of style properties
 * ({@link HudThemeStyle}) consumed by the HUD renderer.
 */
public enum HudTheme implements LangKeyed {
    /** Solid graphite panels with rounded corners (default). */
    CLEAN(new HudThemeStyle(0.70, false, 0x00000000, 3, 3, true, true)),
    /** Translucent glass panels with a subtle border and blur. */
    GLASS(new HudThemeStyle(0.40, true, 0x40FFFFFF, 4, 3, true, true)),
    /** Transparent panels with a 1px accent outline. */
    OUTLINE(new HudThemeStyle(0.15, true, 0xCC7C5CFF, 2, 3, true, false)),
    /** Text only, no panel. */
    MINIMAL(new HudThemeStyle(0.0, false, 0x00000000, 0, 1, true, false));

    private final HudThemeStyle style;

    HudTheme(HudThemeStyle style) {
        this.style = style;
    }

    /** Style properties of the theme. */
    public HudThemeStyle style() {
        return style;
    }

    /** Lower-case id used in JSON. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.cosmetics.hudtheme." + id();
    }

    /** Looks up a theme by id, falling back to {@link #CLEAN}. */
    public static HudTheme fromId(String id) {
        for (HudTheme theme : values()) {
            if (theme.id().equalsIgnoreCase(id)) {
                return theme;
            }
        }
        return CLEAN;
    }
}
