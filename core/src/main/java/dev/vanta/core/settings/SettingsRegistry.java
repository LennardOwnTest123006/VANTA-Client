package dev.vanta.core.settings;

import dev.vanta.core.bridge.VanillaOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ordered collection of {@link Setting}s keyed by id. {@link VantaSettings#registry()} builds the standard one.
 */
public final class SettingsRegistry {
    private final Map<String, Setting<?>> byId = new LinkedHashMap<>();

    /**
     * Adds a setting.
     *
     * @throws IllegalArgumentException on duplicate ids
     */
    public <T> Setting<T> register(Setting<T> setting) {
        Objects.requireNonNull(setting, "setting");
        if (byId.putIfAbsent(setting.id(), setting) != null) {
            throw new IllegalArgumentException("Duplicate setting id: " + setting.id());
        }
        return setting;
    }

    /** Adds several settings. */
    public void registerAll(Iterable<Setting<?>> settings) {
        for (Setting<?> setting : settings) {
            register(setting);
        }
    }

    /** Looks a setting up by id. */
    public Optional<Setting<?>> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Looks a setting up by id.
     *
     * @throws IllegalArgumentException when unknown
     */
    public Setting<?> require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + id));
    }

    /** True when the id is registered. */
    public boolean contains(String id) {
        return byId.containsKey(id);
    }

    /** All settings in registration order. */
    public List<Setting<?>> all() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    /** Settings of one category in registration order. */
    public List<Setting<?>> byCategory(SettingCategory category) {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> setting : byId.values()) {
            if (setting.category() == category) {
                out.add(setting);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** The setting bound to a vanilla option, if registered. */
    public Optional<Setting<?>> forVanillaOption(VanillaOption option) {
        for (Setting<?> setting : byId.values()) {
            if (setting.vanillaBinding().isPresent() && setting.vanillaBinding().get() == option) {
                return Optional.of(setting);
            }
        }
        return Optional.empty();
    }

    /** Number of settings. */
    public int size() {
        return byId.size();
    }
}
