package dev.vanta.core.screen.keybinds;

import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.keybinds.KeybindModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The view filter of the keybinds screen: a search query (matched through {@link KeybindModel#search(String)}) and
 * the "show only conflicts" switch. {@link #sections} applies it to the model's categories and drops empty ones,
 * keeping the category order (VANTA first, then vanilla).
 *
 * @param query         search text ({@code ""} for none)
 * @param conflictsOnly show only mappings involved in a conflict
 */
public record KeybindFilter(String query, boolean conflictsOnly) {
    /** No filtering. */
    public static final KeybindFilter NONE = new KeybindFilter("", false);

    public KeybindFilter {
        query = query == null ? "" : query.trim();
    }

    public KeybindFilter withQuery(String q) {
        return new KeybindFilter(q, conflictsOnly);
    }

    public KeybindFilter withConflictsOnly(boolean on) {
        return new KeybindFilter(query, on);
    }

    /** True when neither the query nor the conflicts switch is active. */
    public boolean isEmpty() {
        return query.isEmpty() && !conflictsOnly;
    }

    /** Categories with the mappings that pass the filter, empty categories removed. */
    public List<KeybindModel.Category> sections(KeybindModel model) {
        Objects.requireNonNull(model, "model");
        Set<String> matching = null;
        if (!query.isEmpty()) {
            matching = new HashSet<>();
            for (KeyBinding binding : model.search(query)) {
                matching.add(binding.id());
            }
        }
        List<KeybindModel.Category> out = new ArrayList<>();
        for (KeybindModel.Category category : model.categories()) {
            List<KeyBinding> kept = new ArrayList<>();
            for (KeyBinding binding : category.bindings()) {
                if (matching != null && !matching.contains(binding.id())) {
                    continue;
                }
                if (conflictsOnly && model.conflictFor(binding.id()).isEmpty()) {
                    continue;
                }
                kept.add(binding);
            }
            if (!kept.isEmpty()) {
                out.add(new KeybindModel.Category(category.id(), category.name(), kept));
            }
        }
        return out;
    }

    /** Total number of mappings across {@link #sections}. */
    public int count(KeybindModel model) {
        int n = 0;
        for (KeybindModel.Category category : sections(model)) {
            n += category.bindings().size();
        }
        return n;
    }
}
