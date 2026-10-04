package dev.vanta.core.keybinds;

import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.bridge.KeyRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Finds key mappings that share the same physical input. Unbound mappings never conflict.
 * <p>
 * Severity: {@link Severity#HIGH} when two conflicting mappings belong to the same category or are both vanilla
 * (the game will trigger both at once); {@link Severity#MEDIUM} when a mod key overlaps a key from another category,
 * which is sometimes intentional (e.g. a key that only works inside a screen).
 */
public final class ConflictDetector {

    /** How serious a conflict is. */
    public enum Severity {
        HIGH, MEDIUM
    }

    /**
     * Mappings sharing one input.
     *
     * @param key      the shared input
     * @param bindings at least two mappings, in input order
     * @param severity severity
     */
    public record Conflict(KeyRef key, List<KeyBinding> bindings, Severity severity) {
        public Conflict {
            Objects.requireNonNull(key, "key");
            bindings = List.copyOf(bindings);
            if (bindings.size() < 2) {
                throw new IllegalArgumentException("A conflict needs at least two bindings");
            }
        }

        /** True when the mapping is part of this conflict. */
        public boolean involves(String id) {
            return bindings.stream().anyMatch(b -> b.id().equals(id));
        }
    }

    private ConflictDetector() {
    }

    /** Groups mappings by input and returns every group with two or more members. */
    public static List<Conflict> detect(List<KeyBinding> bindings) {
        Map<String, List<KeyBinding>> groups = new LinkedHashMap<>();
        for (KeyBinding binding : bindings) {
            if (binding.isUnbound()) {
                continue;
            }
            String key = binding.boundKeyType() + ":" + binding.boundKeyCode();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(binding);
        }
        List<Conflict> out = new ArrayList<>();
        for (List<KeyBinding> group : groups.values()) {
            if (group.size() >= 2) {
                out.add(new Conflict(group.get(0).boundKey(), group, severityOf(group)));
            }
        }
        return out;
    }

    /** Severity rule, see class documentation. */
    public static Severity severityOf(List<KeyBinding> group) {
        boolean allVanilla = group.stream().allMatch(KeyBinding::isVanilla);
        if (allVanilla) {
            return Severity.HIGH;
        }
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                if (group.get(i).categoryId().equals(group.get(j).categoryId())) {
                    return Severity.HIGH;
                }
            }
        }
        return Severity.MEDIUM;
    }

    /** The conflict involving the mapping, if any. */
    public static Optional<Conflict> conflictFor(List<Conflict> conflicts, String id) {
        return conflicts.stream().filter(c -> c.involves(id)).findFirst();
    }

    /** Number of mappings involved in any conflict. */
    public static int involvedCount(List<Conflict> conflicts) {
        int count = 0;
        for (Conflict conflict : conflicts) {
            count += conflict.bindings().size();
        }
        return count;
    }
}
