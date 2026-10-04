package dev.vanta.core.screen.cosmetics;

import dev.vanta.core.accessibility.AccessibilityState;
import dev.vanta.core.accessibility.ColorBlindPalette;
import dev.vanta.core.cosmetics.UiThemeDefinition;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.ui.Theme;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the {@link Theme} a VANTA screen renders with from the domain state: the active cosmetic UI theme (its
 * token overrides on top of the default palette), the accessibility preferences (UI scale, reduced motion, high
 * contrast, large text, reduced transparency) and the colour-blind palette, which replaces the success / warning /
 * danger tokens.
 * <p>
 * The cosmetics screen calls {@link #themeFor(VantaServices)} again after every selection change so a new theme
 * applies instantly; {@link #preview(UiThemeDefinition, Theme)} derives the look of a theme that is not selected
 * (for the theme cards).
 */
public final class ThemeResolver {
    private ThemeResolver() {
    }

    /** The theme for the current cosmetics selection and accessibility state. */
    public static Theme themeFor(VantaServices services) {
        Objects.requireNonNull(services, "services");
        return build(services.cosmetics().activeTheme(), services.accessibility().state());
    }

    /** Builds a theme from explicit inputs. */
    public static Theme build(UiThemeDefinition definition, AccessibilityState state) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(state, "state");
        Theme theme = Theme.DEFAULT.withOverrides(definition.tokenOverrides())
                .withIdentity(definition.id(), Lang.tr(definition.langKey()))
                .withScale((float) state.uiScale())
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

    /**
     * The colours {@code definition} would produce, keeping the accessibility flags of {@code current}. Used to paint
     * palette swatches of themes that are not active.
     */
    public static Theme preview(UiThemeDefinition definition, Theme current) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(current, "current");
        Theme theme = Theme.DEFAULT.withOverrides(definition.tokenOverrides())
                .withIdentity(definition.id(), Lang.tr(definition.langKey()))
                .withScale(current.scale())
                .withReducedMotion(current.reducedMotion())
                .withLargeText(current.largeText())
                .withReducedTransparency(current.reducedTransparency());
        return current.isHighContrast() ? theme.withHighContrast(true) : theme;
    }
}
