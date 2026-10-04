package dev.vanta.core.keybinds;

import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.search.Fuzzy;
import dev.vanta.core.search.SearchHit;
import dev.vanta.core.search.SearchIndex;
import java.util.ArrayList;
import java.util.List;

/**
 * Search over key mappings by name, category, mapping id and bound key label.
 */
public final class KeybindSearch {
    private final SearchIndex<KeyBinding> index = new SearchIndex<>();

    public KeybindSearch(List<KeyBinding> bindings) {
        rebuild(bindings);
    }

    /** Re-indexes the given mappings. */
    public void rebuild(List<KeyBinding> bindings) {
        index.clear();
        for (KeyBinding binding : bindings) {
            List<String> keywords = new ArrayList<>();
            keywords.add(binding.categoryName());
            keywords.addAll(Fuzzy.words(binding.id()));
            keywords.add(KeybindFormatter.format(binding.boundKey()));
            index.add(binding, binding.displayName(), keywords, binding.categoryName());
        }
    }

    /** Ranked hits. */
    public List<SearchHit<KeyBinding>> search(String query, int limit) {
        return index.search(query, limit);
    }

    /** Matching mappings in rank order. */
    public List<KeyBinding> find(String query) {
        List<KeyBinding> out = new ArrayList<>();
        for (SearchHit<KeyBinding> hit : index.search(query)) {
            out.add(hit.item());
        }
        return out;
    }

    public int size() {
        return index.size();
    }
}
