package dev.vanta.core.settings;

/**
 * Observes value changes in a {@link SettingsStore}.
 */
@FunctionalInterface
public interface SettingsListener {
    /** Called after the value has been stored. */
    void onSettingChanged(SettingChange change);
}
