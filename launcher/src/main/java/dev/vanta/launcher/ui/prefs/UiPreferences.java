package dev.vanta.launcher.ui.prefs;

/**
 * Preferences that only concern the JavaFX window ({@code ui-preferences.json} in the data directory). Launcher
 * behaviour lives in {@link dev.vanta.launcher.core.settings.LauncherSettings}; this file is purely presentational.
 *
 * @param schemaVersion schema version (1)
 * @param reducedMotion disable transitions and pulsing indicators
 * @param windowWidth   last window width (0 = default)
 * @param windowHeight  last window height (0 = default)
 * @param maximized     whether the window was maximised
 * @param lastPage      last shown page id (empty = Home)
 */
public record UiPreferences(int schemaVersion, boolean reducedMotion, double windowWidth, double windowHeight, boolean maximized,
                            String lastPage) {

    /** Current schema version. */
    public static final int SCHEMA_VERSION = 1;

    public UiPreferences {
        schemaVersion = schemaVersion <= 0 ? SCHEMA_VERSION : schemaVersion;
        windowWidth = windowWidth < 0 || Double.isNaN(windowWidth) ? 0 : windowWidth;
        windowHeight = windowHeight < 0 || Double.isNaN(windowHeight) ? 0 : windowHeight;
        lastPage = lastPage == null ? "" : lastPage;
    }

    /** @return defaults */
    public static UiPreferences defaults() {
        return new UiPreferences(SCHEMA_VERSION, false, 0, 0, false, "");
    }

    /**
     * @param value reduced motion
     * @return copy
     */
    public UiPreferences withReducedMotion(final boolean value) {
        return new UiPreferences(schemaVersion, value, windowWidth, windowHeight, maximized, lastPage);
    }

    /**
     * @param width     width
     * @param height    height
     * @param isMaximized maximised
     * @return copy
     */
    public UiPreferences withWindow(final double width, final double height, final boolean isMaximized) {
        return new UiPreferences(schemaVersion, reducedMotion, width, height, isMaximized, lastPage);
    }

    /**
     * @param page page id
     * @return copy
     */
    public UiPreferences withLastPage(final String page) {
        return new UiPreferences(schemaVersion, reducedMotion, windowWidth, windowHeight, maximized, page);
    }

    /** @return whether a stored window size exists */
    public boolean hasWindowSize() {
        return windowWidth > 0 && windowHeight > 0;
    }
}
