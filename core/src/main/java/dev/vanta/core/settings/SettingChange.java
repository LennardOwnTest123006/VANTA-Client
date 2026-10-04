package dev.vanta.core.settings;

/**
 * Describes one value change delivered to {@link SettingsListener}s.
 *
 * @param setting  the setting
 * @param oldValue previous value
 * @param newValue new value
 */
public record SettingChange(Setting<?> setting, Object oldValue, Object newValue) {
    /** True when the setting is the given one. */
    public boolean is(Setting<?> candidate) {
        return setting.equals(candidate);
    }

    /** New value cast to the setting's type. */
    @SuppressWarnings("unchecked")
    public <T> T newValueAs(Setting<T> candidate) {
        if (!is(candidate)) {
            throw new IllegalArgumentException("Change is for " + setting.id() + ", not " + candidate.id());
        }
        return (T) newValue;
    }
}
