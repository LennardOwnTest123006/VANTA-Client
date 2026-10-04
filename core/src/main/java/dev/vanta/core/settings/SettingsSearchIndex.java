package dev.vanta.core.settings;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.search.Fuzzy;
import dev.vanta.core.search.SearchHit;
import dev.vanta.core.search.SearchIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Search over the settings of a registry: translated title, keywords (+ category name + id words) and description.
 * Rebuild after a language change.
 */
public final class SettingsSearchIndex {
    private final SettingsRegistry registry;
    private final SearchIndex<Setting<?>> index = new SearchIndex<>();

    public SettingsSearchIndex(SettingsRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        rebuild();
    }

    /** Re-indexes with the current translations. */
    public void rebuild() {
        index.clear();
        for (Setting<?> setting : registry.all()) {
            List<String> keywords = new ArrayList<>(setting.keywords());
            keywords.add(Lang.tr(setting.category().langKey()));
            keywords.addAll(Fuzzy.words(setting.id()));
            setting.vanillaBinding().ifPresent(o -> keywords.add("vanilla"));
            index.add(setting, Lang.tr(setting.titleKey()), keywords, Lang.tr(setting.descriptionKey()));
        }
    }

    /** Ranked hits for the query. */
    public List<SearchHit<Setting<?>>> search(String query, int limit) {
        return index.search(query, limit);
    }

    /** Ranked hits without a limit. */
    public List<SearchHit<Setting<?>>> search(String query) {
        return index.search(query);
    }

    /** Number of indexed settings. */
    public int size() {
        return index.size();
    }
}
