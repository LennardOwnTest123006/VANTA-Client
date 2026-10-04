package dev.vanta.client.screen;

import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Theme;

/**
 * Builds the {@link Theme} every VANTA screen renders with from the active cosmetic theme and the accessibility
 * settings. Pure composition over core types; called whenever either source changes.
 */
public final class ThemeFactory {
    private ThemeFactory() {
    }

    /** The theme matching the current cosmetics selection and accessibility state (built by the core). */
    public static Theme build(VantaServices services) {
        return dev.vanta.core.screen.common.ThemeFactory.themeFor(services);
    }
}
