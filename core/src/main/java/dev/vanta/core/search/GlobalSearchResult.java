package dev.vanta.core.search;

import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.settings.Setting;
import java.util.List;

/**
 * A result of {@link GlobalSearch}: a setting, an action or a key binding.
 */
public sealed interface GlobalSearchResult {
    /** Relevance score. */
    double score();

    /** Display title (already translated). */
    String title();

    /** Secondary line (category, key, …), already translated. */
    String subtitle();

    /** Title ranges to highlight. */
    List<MatchRange> highlights();

    /** A setting hit. */
    record SettingResult(Setting<?> setting, String title, String subtitle, double score,
                         List<MatchRange> highlights) implements GlobalSearchResult {
    }

    /** An action hit. */
    record ActionResult(ActionEntry action, String title, String subtitle, double score,
                        List<MatchRange> highlights) implements GlobalSearchResult {
    }

    /** A key binding hit. */
    record KeybindResult(KeyBinding binding, String title, String subtitle, double score,
                         List<MatchRange> highlights) implements GlobalSearchResult {
    }
}
