package dev.vanta.core.keybinds;

import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.bridge.KeybindBridge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * View model of the keybind manager: all mappings grouped by category (VANTA first), conflicts, search and the
 * rebinding operations. Every mutation goes through the {@link KeybindBridge} and refreshes the snapshot.
 */
public final class KeybindModel {

    /** Mappings of one category. */
    public record Category(String id, String name, List<KeyBinding> bindings) {
        public Category {
            bindings = List.copyOf(bindings);
        }

        /** True for VANTA's own category. */
        public boolean isVanta() {
            return VantaKeys.CATEGORY.equals(id);
        }
    }

    private final KeybindBridge bridge;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private List<KeyBinding> bindings = List.of();
    private List<Category> categories = List.of();
    private List<ConflictDetector.Conflict> conflicts = List.of();
    private KeybindSearch search = new KeybindSearch(List.of());

    public KeybindModel(KeybindBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        refresh();
    }

    /** Re-reads every mapping from the bridge. */
    public void refresh() {
        bindings = List.copyOf(bridge.all());
        Map<String, List<KeyBinding>> grouped = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (KeyBinding binding : bindings) {
            grouped.computeIfAbsent(binding.categoryId(), k -> new ArrayList<>()).add(binding);
            names.putIfAbsent(binding.categoryId(), binding.categoryName());
        }
        List<Category> result = new ArrayList<>();
        if (grouped.containsKey(VantaKeys.CATEGORY)) {
            result.add(new Category(VantaKeys.CATEGORY, names.get(VantaKeys.CATEGORY), grouped.get(VantaKeys.CATEGORY)));
        }
        for (Map.Entry<String, List<KeyBinding>> entry : grouped.entrySet()) {
            if (!entry.getKey().equals(VantaKeys.CATEGORY)) {
                result.add(new Category(entry.getKey(), names.get(entry.getKey()), entry.getValue()));
            }
        }
        categories = Collections.unmodifiableList(result);
        conflicts = ConflictDetector.detect(bindings);
        search = new KeybindSearch(bindings);
        fire();
    }

    /** Every mapping. */
    public List<KeyBinding> all() {
        return bindings;
    }

    /** Categories, VANTA first then vanilla order. */
    public List<Category> categories() {
        return categories;
    }

    /** Finds a mapping by id. */
    public Optional<KeyBinding> find(String id) {
        return bindings.stream().filter(b -> b.id().equals(id)).findFirst();
    }

    /** Current conflicts. */
    public List<ConflictDetector.Conflict> conflicts() {
        return conflicts;
    }

    /** The conflict involving a mapping, if any. */
    public Optional<ConflictDetector.Conflict> conflictFor(String id) {
        return ConflictDetector.conflictFor(conflicts, id);
    }

    /** Number of mappings whose binding differs from the default. */
    public int modifiedCount() {
        int count = 0;
        for (KeyBinding binding : bindings) {
            if (!binding.isDefault()) {
                count++;
            }
        }
        return count;
    }

    /** Mappings matching the query, ranked. */
    public List<KeyBinding> search(String query) {
        if (query == null || query.isBlank()) {
            return bindings;
        }
        return search.find(query);
    }

    /** Rebinds a mapping; {@link KeyRef#UNBOUND} clears it. */
    public boolean setKey(String id, KeyRef key) {
        boolean changed = bridge.setKey(id, Objects.requireNonNull(key, "key"));
        if (changed) {
            refresh();
        }
        return changed;
    }

    /** Restores one default. */
    public boolean reset(String id) {
        boolean changed = bridge.reset(id);
        if (changed) {
            refresh();
        }
        return changed;
    }

    /** Restores every default. */
    public void resetAll() {
        bridge.resetAll();
        refresh();
    }

    /** Current bindings of VANTA's own keys as id → key (used for profile keybind overrides). */
    public Map<String, KeyRef> vantaBindings() {
        Map<String, KeyRef> out = new LinkedHashMap<>();
        for (KeyBinding binding : bindings) {
            if (VantaKeys.isVantaKey(binding.id())) {
                out.put(binding.id(), binding.boundKey());
            }
        }
        return out;
    }

    /** Listens for refreshes. */
    public Runnable onChange(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private void fire() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
