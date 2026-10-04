package dev.vanta.core.bridge;

import java.util.List;
import java.util.Optional;

/**
 * Access to every registered key mapping, implemented by the client on top of {@code Options.keyMappings}.
 */
public interface KeybindBridge {

    /** All key mappings in vanilla order. */
    List<KeyBinding> all();

    /** Finds a mapping by id. */
    default Optional<KeyBinding> find(String id) {
        return all().stream().filter(b -> b.id().equals(id)).findFirst();
    }

    /**
     * Rebinds a mapping and saves options.
     *
     * @return true when the mapping exists and was changed
     */
    boolean setKey(String id, KeyRef key);

    /** Restores the default binding of one mapping. */
    boolean reset(String id);

    /** Restores the default binding of every mapping. */
    void resetAll();
}
