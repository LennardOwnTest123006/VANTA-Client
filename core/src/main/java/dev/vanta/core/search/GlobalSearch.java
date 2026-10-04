package dev.vanta.core.search;

import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.keybinds.KeybindFormatter;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingsRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Searches settings, actions and key bindings at once (the Ctrl+K style search screen).
 * <p>
 * Settings and actions are indexed once per {@link #rebuild()}; key bindings are re-indexed on every rebuild from the
 * supplier so rebinding is reflected. Call {@link #rebuild()} after a language change.
 */
public final class GlobalSearch {
    /** Score boost for settings so they win ties against generic actions. */
    private static final double SETTING_BIAS = 0.5;

    private final SettingsRegistry registry;
    private final List<ActionEntry> actions;
    private final Supplier<List<KeyBinding>> keybinds;
    private final SearchIndex<Setting<?>> settingIndex = new SearchIndex<>();
    private final SearchIndex<ActionEntry> actionIndex = new SearchIndex<>();
    private final SearchIndex<KeyBinding> keybindIndex = new SearchIndex<>();

    public GlobalSearch(SettingsRegistry registry, List<ActionEntry> actions, Supplier<List<KeyBinding>> keybinds) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.actions = List.copyOf(actions);
        this.keybinds = Objects.requireNonNull(keybinds, "keybinds");
        rebuild();
    }

    /** Re-indexes everything with the current translations and key bindings. */
    public void rebuild() {
        settingIndex.clear();
        for (Setting<?> setting : registry.all()) {
            List<String> keywords = new ArrayList<>(setting.keywords());
            keywords.add(Lang.tr(setting.category().langKey()));
            keywords.addAll(Fuzzy.words(setting.id()));
            settingIndex.add(setting, Lang.tr(setting.titleKey()), keywords, Lang.tr(setting.descriptionKey()));
        }
        actionIndex.clear();
        for (ActionEntry action : actions) {
            List<String> keywords = new ArrayList<>(action.keywords());
            action.screen().ifPresent(s -> keywords.add(Lang.tr(s.langKey())));
            actionIndex.add(action, Lang.tr(action.langKey()), keywords, Lang.tr(action.descriptionKey()));
        }
        keybindIndex.clear();
        for (KeyBinding binding : keybinds.get()) {
            List<String> keywords = new ArrayList<>();
            keywords.add(binding.categoryName());
            keywords.add(KeybindFormatter.format(binding.boundKey()));
            keywords.addAll(Fuzzy.words(binding.id()));
            keybindIndex.add(binding, binding.displayName(), keywords, Lang.tr("vanta.search.kind.keybind"));
        }
    }

    /** Number of indexed entries across all kinds. */
    public int size() {
        return settingIndex.size() + actionIndex.size() + keybindIndex.size();
    }

    /** Searches all kinds and returns the best {@code limit} results. */
    public List<GlobalSearchResult> search(String query, int limit) {
        List<GlobalSearchResult> results = new ArrayList<>();
        for (SearchHit<Setting<?>> hit : settingIndex.search(query, limit)) {
            Setting<?> setting = hit.item();
            results.add(new GlobalSearchResult.SettingResult(setting, Lang.tr(setting.titleKey()),
                    Lang.tr(setting.category().langKey()), hit.score() + SETTING_BIAS, hit.highlights()));
        }
        for (SearchHit<ActionEntry> hit : actionIndex.search(query, limit)) {
            ActionEntry action = hit.item();
            results.add(new GlobalSearchResult.ActionResult(action, Lang.tr(action.langKey()),
                    Lang.tr("vanta.search.kind.action"), hit.score(), hit.highlights()));
        }
        for (SearchHit<KeyBinding> hit : keybindIndex.search(query, limit)) {
            KeyBinding binding = hit.item();
            results.add(new GlobalSearchResult.KeybindResult(binding, binding.displayName(),
                    binding.categoryName() + " · " + KeybindFormatter.format(binding.boundKey()), hit.score(),
                    hit.highlights()));
        }
        results.sort(Comparator.comparingDouble(GlobalSearchResult::score).reversed()
                .thenComparing(GlobalSearchResult::title));
        return results.size() > limit ? List.copyOf(results.subList(0, limit)) : List.copyOf(results);
    }
}
