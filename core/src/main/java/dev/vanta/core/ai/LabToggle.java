package dev.vanta.core.ai;

import java.util.List;

/**
 * The Vanta Lab features the assistant may switch. {@code dev.vanta.core.lab} implements it over {@code LabFeature}
 * and {@code LabSettings}; {@link #none()} serves until it is wired.
 */
public interface LabToggle {
    /** Feature ids in display order (lower-case, e.g. {@code dynamic_hud}). */
    List<String> features();

    /** Current state of a feature; false for unknown ids. */
    boolean isEnabled(String feature);

    /**
     * Switches a feature.
     *
     * @return true when the feature exists and now has the requested state
     */
    boolean set(String feature, boolean enabled);

    /** A toggle without features. */
    static LabToggle none() {
        return new LabToggle() {
            @Override
            public List<String> features() {
                return List.of();
            }

            @Override
            public boolean isEnabled(String feature) {
                return false;
            }

            @Override
            public boolean set(String feature, boolean enabled) {
                return false;
            }
        };
    }
}
