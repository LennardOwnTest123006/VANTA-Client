package dev.vanta.core.accessibility;

import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;

/**
 * Resolved accessibility preferences consumed by the UI layer (theme scale, animation and colour decisions).
 *
 * @param uiScale              multiplier for VANTA screens
 * @param reducedMotion        skip animations
 * @param highContrast         stronger borders and text
 * @param largeText            larger font size
 * @param reducedTransparency  opaque panels
 * @param palette              status colour palette
 */
public record AccessibilityState(double uiScale, boolean reducedMotion, boolean highContrast, boolean largeText,
                                 boolean reducedTransparency, ColorBlindPalette palette) {
    /** Factory defaults. */
    public static final AccessibilityState DEFAULT = new AccessibilityState(1.0, false, false, false, false,
            ColorBlindPalette.NONE);

    public AccessibilityState {
        uiScale = Math.max(0.5, Math.min(2.0, uiScale));
        if (palette == null) {
            palette = ColorBlindPalette.NONE;
        }
    }

    /** Reads the state from the settings store. */
    public static AccessibilityState fromSettings(SettingsStore settings) {
        return new AccessibilityState(
                settings.get(VantaSettings.GENERAL_UI_SCALE),
                settings.get(VantaSettings.ACCESSIBILITY_REDUCED_MOTION),
                settings.get(VantaSettings.ACCESSIBILITY_HIGH_CONTRAST),
                settings.get(VantaSettings.ACCESSIBILITY_LARGE_TEXT),
                settings.get(VantaSettings.ACCESSIBILITY_REDUCED_TRANSPARENCY),
                settings.get(VantaSettings.ACCESSIBILITY_COLOR_BLIND_PALETTE));
    }

    /** ARGB colour for "success" under the active palette. */
    public int successColor() {
        return palette.success();
    }

    /** ARGB colour for "warning" under the active palette. */
    public int warningColor() {
        return palette.warning();
    }

    /** ARGB colour for "danger" under the active palette. */
    public int dangerColor() {
        return palette.danger();
    }

    /** Effective text scale: large text adds 20 % on top of the UI scale. */
    public double textScale() {
        return uiScale * (largeText ? 1.2 : 1.0);
    }

    /** Panel alpha multiplier: 1 when transparency is reduced. */
    public double panelAlpha(double themeAlpha) {
        return reducedTransparency ? 1.0 : themeAlpha;
    }
}
