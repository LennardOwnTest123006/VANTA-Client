package dev.vanta.core.screen.common;

import dev.vanta.core.accessibility.AccessibilityState;
import dev.vanta.core.accessibility.ColorBlindPalette;
import dev.vanta.core.cosmetics.UiThemeDefinition;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Theme;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the {@link Theme} every VANTA screen renders with from the domain state: the active cosmetic UI theme
 * (token overrides), the accessibility preferences (scale, reduced motion, high contrast, large text, reduced
 * transparency) and the colour-blind palette (replaces the success / warning / danger tokens).
 * <p>
 * The client builds its {@link dev.vanta.core.ui.UiEnvironment} with {@link #themeFor(VantaServices)}; screens call it
 * again whenever one of those inputs changes so the change applies immediately.
 */
public final class ThemeFactory {
    private ThemeFactory() {
    }

    /** The theme for the current settings. */
    public static Theme themeFor(VantaServices services) {
        UiThemeDefinition definition = services.cosmetics().activeTheme();
        AccessibilityState state = services.accessibility().state();
        return build(definition, state);
    }

    /** Builds a theme from explicit inputs. */
    public static Theme build(UiThemeDefinition definition, AccessibilityState state) {
        Theme theme = Theme.DEFAULT.withOverrides(definition.tokenOverrides())
                .withIdentity(definition.id(), Lang.tr(definition.langKey()));
        theme = theme.withScale((float) state.uiScale())
                .withReducedMotion(state.reducedMotion())
                .withLargeText(state.largeText())
                .withReducedTransparency(state.reducedTransparency())
                .withHighContrast(state.highContrast());
        if (state.palette() != ColorBlindPalette.NONE) {
            Map<String, Integer> status = new LinkedHashMap<>();
            status.put("state.success", state.successColor());
            status.put("state.warning", state.warningColor());
            status.put("state.danger", state.dangerColor());
            theme = theme.withOverrides(status);
        }
        return theme;
    }
}
