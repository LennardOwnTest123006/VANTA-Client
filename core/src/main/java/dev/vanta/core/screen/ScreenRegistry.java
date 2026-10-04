package dev.vanta.core.screen;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Maps {@link ScreenId}s to screen factories.
 * <p>
 * The factories are registered by the UI layer (which owns the screen classes); this registry is typed as
 * {@link Object} so the domain layer does not depend on {@code dev.vanta.core.ui}. The host casts the created object
 * to its screen type.
 */
public final class ScreenRegistry {
    private final Map<ScreenId, Supplier<Object>> factories = new EnumMap<>(ScreenId.class);

    /** Registers (or replaces) the factory of a screen. */
    public void register(ScreenId id, Supplier<Object> factory) {
        factories.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(factory, "factory"));
    }

    /** True when a factory exists for the screen. */
    public boolean isRegistered(ScreenId id) {
        return factories.containsKey(id);
    }

    /** Creates a new screen instance, or empty when no factory is registered. */
    public Optional<Object> create(ScreenId id) {
        Supplier<Object> factory = factories.get(id);
        return factory == null ? Optional.empty() : Optional.ofNullable(factory.get());
    }

    /** Registered screens. */
    public Set<ScreenId> registered() {
        return factories.keySet();
    }

    /** Removes every factory (tests). */
    public void clear() {
        factories.clear();
    }
}
