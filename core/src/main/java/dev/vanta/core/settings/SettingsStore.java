package dev.vanta.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.Migrations;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Holds the current value of every setting, persists them to {@code settings.json} and notifies listeners.
 * <p>
 * Vanilla-bound settings are read from and written to the {@link OptionsBridge} (when present and supported) and never
 * stored in {@code settings.json}. Unknown ids found in the file are preserved so a downgrade does not lose data.
 * <p>
 * File shape: {@code {"schemaVersion": 1, "values": {"hud.enabled": true, …}}}.
 */
public final class SettingsStore {
    /** Current schema version of {@code settings.json}. */
    public static final int SCHEMA_VERSION = 1;
    /** Migration chain for {@code settings.json}. */
    public static final Migrations MIGRATIONS = Migrations.none(SCHEMA_VERSION);

    private static final String VALUES = "values";

    private final SettingsRegistry registry;
    private final JsonStore store;
    private final Path file;
    private final Optional<OptionsBridge> options;
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, JsonElement> unknown = new LinkedHashMap<>();
    private final List<SettingsListener> listeners = new CopyOnWriteArrayList<>();
    private boolean dirty;
    private boolean optionsDirty;

    /**
     * @param registry settings schema
     * @param store    persistence
     * @param file     {@code settings.json}
     * @param options  vanilla options access (empty in tests and previews)
     */
    public SettingsStore(SettingsRegistry registry, JsonStore store, Path file, Optional<OptionsBridge> options) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.store = Objects.requireNonNull(store, "store");
        this.file = Objects.requireNonNull(file, "file");
        this.options = Objects.requireNonNull(options, "options");
    }

    /** The schema. */
    public SettingsRegistry registry() {
        return registry;
    }

    /** The file this store reads and writes. */
    public Path file() {
        return file;
    }

    // ---- persistence -------------------------------------------------------------------------------------------

    /** Loads values from disk; missing or corrupt files leave every setting at its default. */
    public void load() {
        values.clear();
        unknown.clear();
        Optional<JsonObject> json = store.readMigrated(file, MIGRATIONS);
        if (json.isEmpty()) {
            dirty = false;
            return;
        }
        JsonElement valuesElement = json.get().get(VALUES);
        if (valuesElement != null && valuesElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : valuesElement.getAsJsonObject().entrySet()) {
                Optional<Setting<?>> setting = registry.find(entry.getKey());
                if (setting.isEmpty()) {
                    unknown.put(entry.getKey(), entry.getValue());
                    continue;
                }
                if (!setting.get().isPersisted()) {
                    continue;
                }
                Optional<?> decoded = setting.get().decode(entry.getValue());
                if (decoded.isPresent()) {
                    values.put(entry.getKey(), decoded.get());
                } else {
                    CoreLog.warn("Ignoring invalid value for setting {}: {}", entry.getKey(), entry.getValue());
                }
            }
        }
        dirty = false;
    }

    /** Writes {@code settings.json} (and vanilla options when they changed). */
    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty(JsonStore.SCHEMA_VERSION, SCHEMA_VERSION);
        JsonObject valuesJson = new JsonObject();
        for (Setting<?> setting : registry.all()) {
            if (!setting.isPersisted()) {
                continue;
            }
            valuesJson.add(setting.id(), encode(setting));
        }
        for (Map.Entry<String, JsonElement> entry : unknown.entrySet()) {
            valuesJson.add(entry.getKey(), entry.getValue());
        }
        root.add(VALUES, valuesJson);
        store.writeObject(file, root);
        dirty = false;
        if (optionsDirty) {
            options.ifPresent(OptionsBridge::save);
            optionsDirty = false;
        }
    }

    /** Saves only when something changed since the last load/save. */
    public void saveIfDirty() {
        if (dirty || optionsDirty) {
            save();
        }
    }

    /** True when unsaved changes exist. */
    public boolean isDirty() {
        return dirty || optionsDirty;
    }

    // ---- values ------------------------------------------------------------------------------------------------

    /** Current value (never null). */
    @SuppressWarnings("unchecked")
    public <T> T get(Setting<T> setting) {
        Objects.requireNonNull(setting, "setting");
        if (setting.vanillaBinding().isPresent()) {
            Optional<T> fromGame = readVanilla(setting);
            if (fromGame.isPresent()) {
                return fromGame.get();
            }
        }
        Object value = values.get(setting.id());
        if (value == null) {
            return setting.defaultValue();
        }
        return (T) value;
    }

    /** Current value by id (for the UI's generic widgets), empty when unknown. */
    public Optional<Object> getRaw(String id) {
        return registry.find(id).map(this::get);
    }

    /**
     * Sets a value (normalised through the setting), notifying listeners when it changed.
     *
     * @return true when the stored value changed
     */
    public <T> boolean set(Setting<T> setting, T value) {
        Objects.requireNonNull(setting, "setting");
        T normalized = setting.normalize(value);
        T old = get(setting);
        if (Objects.equals(old, normalized)) {
            return false;
        }
        if (setting.vanillaBinding().isPresent()) {
            if (!writeVanilla(setting, normalized)) {
                values.put(setting.id(), normalized);
            }
        } else if (setting.kind() == SettingKind.ACTION) {
            return false;
        } else {
            values.put(setting.id(), normalized);
            dirty = true;
        }
        fire(new SettingChange(setting, old, normalized));
        return true;
    }

    /** Sets from a raw object (JSON value, bridge value, UI value); ignores incompatible input. */
    @SuppressWarnings("unchecked")
    public boolean setRaw(Setting<?> setting, Object raw) {
        Optional<?> coerced = setting.coerce(raw);
        if (coerced.isEmpty()) {
            return false;
        }
        return set((Setting<Object>) setting, coerced.get());
    }

    /** Sets from a JSON element; ignores incompatible input. */
    @SuppressWarnings("unchecked")
    public boolean setJson(Setting<?> setting, JsonElement json) {
        Optional<?> decoded = setting.decode(json);
        if (decoded.isEmpty()) {
            return false;
        }
        return set((Setting<Object>) setting, decoded.get());
    }

    /** True when the current value equals the default. */
    public boolean isDefault(Setting<?> setting) {
        return Objects.equals(get(setting), setting.defaultValue());
    }

    /** Restores the default of one setting. */
    public <T> boolean reset(Setting<T> setting) {
        return set(setting, setting.defaultValue());
    }

    /** Restores the default of one setting by id. */
    public boolean reset(String id) {
        Optional<Setting<?>> setting = registry.find(id);
        return setting.isPresent() && resetErased(setting.get());
    }

    /** Restores the defaults of a whole category. */
    public int resetCategory(SettingCategory category) {
        int changed = 0;
        for (Setting<?> setting : registry.byCategory(category)) {
            if (resetErased(setting)) {
                changed++;
            }
        }
        return changed;
    }

    /** Restores the defaults of every setting (vanilla-bound ones included). */
    public int resetAll() {
        int changed = 0;
        for (Setting<?> setting : registry.all()) {
            if (resetErased(setting)) {
                changed++;
            }
        }
        unknown.clear();
        dirty = true;
        return changed;
    }

    @SuppressWarnings("unchecked")
    private boolean resetErased(Setting<?> setting) {
        return reset((Setting<Object>) setting);
    }

    // ---- snapshots (profiles) ------------------------------------------------------------------------------------

    /**
     * Current values encoded as JSON, keyed by id.
     *
     * @param includeVanilla true to include vanilla-bound settings (profiles do, settings.json does not)
     */
    public Map<String, JsonElement> snapshot(boolean includeVanilla) {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        for (Setting<?> setting : registry.all()) {
            if (setting.kind() == SettingKind.ACTION) {
                continue;
            }
            if (setting.isVanilla() && !includeVanilla) {
                continue;
            }
            out.put(setting.id(), encode(setting));
        }
        return out;
    }

    /**
     * Applies a snapshot; unknown ids and invalid values are ignored.
     *
     * @return number of settings whose value changed
     */
    public int applySnapshot(Map<String, JsonElement> snapshot) {
        int changed = 0;
        for (Map.Entry<String, JsonElement> entry : snapshot.entrySet()) {
            Optional<Setting<?>> setting = registry.find(entry.getKey());
            if (setting.isPresent() && setting.get().kind() != SettingKind.ACTION
                    && setJson(setting.get(), entry.getValue())) {
                changed++;
            }
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private <T> JsonElement encode(Setting<T> setting) {
        return setting.encode(get(setting));
    }

    // ---- listeners -----------------------------------------------------------------------------------------------

    /** Adds a listener for every change. */
    public void addListener(SettingsListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Removes a listener. */
    public void removeListener(SettingsListener listener) {
        listeners.remove(listener);
    }

    /**
     * Listens to one setting.
     *
     * @return a handle that unsubscribes when run
     */
    public <T> Runnable onChange(Setting<T> setting, BiConsumer<T, T> handler) {
        SettingsListener listener = change -> {
            if (change.is(setting)) {
                @SuppressWarnings("unchecked")
                T oldValue = (T) change.oldValue();
                handler.accept(oldValue, change.newValueAs(setting));
            }
        };
        addListener(listener);
        return () -> removeListener(listener);
    }

    private void fire(SettingChange change) {
        for (SettingsListener listener : listeners) {
            try {
                listener.onSettingChanged(change);
            } catch (RuntimeException e) {
                CoreLog.warn(e, "Settings listener failed for {}", change.setting().id());
            }
        }
    }

    // ---- vanilla ---------------------------------------------------------------------------------------------------

    private <T> Optional<T> readVanilla(Setting<T> setting) {
        VanillaOption option = setting.vanillaBinding().orElseThrow();
        if (options.isEmpty() || !options.get().supports(option)) {
            return Optional.empty();
        }
        return options.get().get(option).flatMap(setting::coerce);
    }

    private <T> boolean writeVanilla(Setting<T> setting, T value) {
        VanillaOption option = setting.vanillaBinding().orElseThrow();
        if (options.isEmpty() || !options.get().supports(option)) {
            return false;
        }
        boolean applied = options.get().set(option, setting.toBridgeValue(value));
        if (applied) {
            optionsDirty = true;
        }
        return applied;
    }

    /** Ids present in the file that no registered setting claims (kept for forward compatibility). */
    public List<String> unknownIds() {
        return Collections.unmodifiableList(new ArrayList<>(unknown.keySet()));
    }

    /** The options bridge, when present. */
    public Optional<OptionsBridge> optionsBridge() {
        return options;
    }
}
