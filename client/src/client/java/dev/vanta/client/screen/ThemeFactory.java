package dev.vanta.client.screen;

import dev.vanta.core.accessibility.AccessibilityState;
import dev.vanta.core.accessibility.ColorBlindPalette;
import dev.vanta.core.cosmetics.UiThemeDefinition;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Theme;
import java.util.Map;

/**
 * Builds the {@link Theme} every VANTA screen renders with from the active cosmetic theme and the accessibility
 * settings. Pure composition over core types; called whenever either source changes.
 */
public final class ThemeFactory {
    private ThemeFactory() {
    }

    /** The theme matching the current cosmetics selection and accessibility state. */
    public static Theme build(VantaServices services) {
        AccessibilityState accessibility = services.accessibility().state();
        UiThemeDefinition definition = services.cosmetics().activeTheme();
        Theme theme = Theme.DEFAULT
                .withOverrides(definition.tokenOverrides())
                .withIdentity(definition.id(), Lang.tr(definition.langKey()))
                .withScale((float) accessibility.uiScale())
                .withReducedMotion(accessibility.reducedMotion())
                .withReducedTransparency(accessibility.reducedTransparency())
                .withLargeText(accessibility.largeText())
                .withHighContrast(accessibility.highContrast());
        if (accessibility.palette() != ColorBlindPalette.NONE) {
            theme = theme.withOverrides(Map.of(
                    "state.success", accessibility.successColor(),
                    "state.warning", accessibility.warningColor(),
                    "state.danger", accessibility.dangerColor()));
        }
        return theme;
    }
}
