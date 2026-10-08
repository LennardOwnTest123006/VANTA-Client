package dev.vanta.core.hud;

import com.google.gson.JsonObject;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.FileNames;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.Migrations;
import dev.vanta.core.config.VantaPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Persists the live HUD layout ({@code hud/layout.json}) and user presets ({@code hud/presets/<id>.json}).
 */
public final class HudStore {
    /** Schema version of layout and preset files. */
    public static final int SCHEMA_VERSION = 1;
    /** Migration chain for HUD files. */
    public static final Migrations MIGRATIONS = Migrations.none(SCHEMA_VERSION);
    /** Longest user preset name. */
    public static final int MAX_PRESET_NAME = 48;

    private final JsonStore store;
    private final VantaPaths paths;
    private final List<Consumer<HudLayout>> listeners = new CopyOnWriteArrayList<>();
    private final List<HudPreset> userPresets = new ArrayList<>();
    private HudLayout layout = HudPresets.defaultPreset().layout();
    private boolean dirty;

    public HudStore(JsonStore store, VantaPaths paths) {
        this.store = Objects.requireNonNull(store, "store");
        this.paths = Objects.requireNonNull(paths, "paths");
    }

    /** Loads the layout (default preset when missing) and user presets. */
    public void load() {
        Optional<JsonObject> json = store.readMigrated(paths.hudLayoutFile(), MIGRATIONS);
        layout = json.map(HudLayoutCodec::read).filter(l -> !l.isEmpty()).orElse(HudPresets.defaultPreset().layout());
        userPresets.clear();
        Path dir = paths.hudPresetsDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(this::loadPreset);
            } catch (IOException e) {
                CoreLog.warn(e, "Could not list HUD presets in {}", dir);
            }
        }
        dirty = false;
        fire();
    }

    private void loadPreset(Path file) {
        String stem = file.getFileName().toString();
        stem = stem.substring(0, stem.length() - ".json".length());
        if (!FileNames.isSafe(stem)) {
            CoreLog.warn("Ignoring HUD preset with unsafe file name {}", file.getFileName());
            return;
        }
        Optional<JsonObject> json = store.readMigrated(file, MIGRATIONS);
        if (json.isEmpty()) {
            return;
        }
        HudLayout presetLayout = HudLayoutCodec.read(json.get());
        String name = HudLayoutCodec.string(json.get(), "name", stem);
        if (name.length() > MAX_PRESET_NAME) {
            name = name.substring(0, MAX_PRESET_NAME);
        }
        userPresets.add(new HudPreset(stem, name, presetLayout, false));
    }

    /** Writes the layout when it changed. */
    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty(JsonStore.SCHEMA_VERSION, SCHEMA_VERSION);
        HudLayoutCodec.write(layout, root);
        store.writeObject(paths.hudLayoutFile(), root);
        dirty = false;
    }

    /** Saves only when the layout changed since the last load/save. */
    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    /** The live layout. */
    public HudLayout layout() {
        return layout;
    }

    /** Replaces the live layout and notifies listeners. */
    public void setLayout(HudLayout next) {
        Objects.requireNonNull(next, "next");
        if (next.equals(layout)) {
            return;
        }
        layout = next;
        dirty = true;
        fire();
    }

    /** Applies a preset's layout. */
    public void applyPreset(HudPreset preset) {
        setLayout(preset.layout());
    }

    /** User presets in file order. */
    public List<HudPreset> userPresets() {
        return Collections.unmodifiableList(userPresets);
    }

    /** Built-in presets followed by user presets. */
    public List<HudPreset> allPresets() {
        List<HudPreset> all = new ArrayList<>(HudPresets.builtIns());
        all.addAll(userPresets);
        return Collections.unmodifiableList(all);
    }

    /** Finds a preset (built-in or user) by id. */
    public Optional<HudPreset> findPreset(String id) {
        return allPresets().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** The preset whose layout equals the live layout, if any. */
    public Optional<HudPreset> activePreset() {
        return allPresets().stream().filter(p -> p.layout().equals(layout)).findFirst();
    }

    /**
     * Saves the live layout as a new user preset. The id is derived from the name and made unique.
     */
    public HudPreset saveUserPreset(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Preset name must not be empty");
        }
        if (trimmed.length() > MAX_PRESET_NAME) {
            trimmed = trimmed.substring(0, MAX_PRESET_NAME);
        }
        String base = FileNames.sanitize(trimmed, "preset");
        String id = base;
        int n = 2;
        while (findPreset(id).isPresent()) {
            id = base + "-" + n++;
        }
        HudPreset preset = new HudPreset(id, trimmed, layout, false);
        writePreset(preset);
        userPresets.add(preset);
        return preset;
    }

    /**
     * Saves {@code layout} as a new user preset named {@code name} (the Nexus HUD Designer's "Duplicate" of a
     * named layout); the live layout is left alone.
     *
     * @throws IllegalArgumentException when the name is blank
     */
    public HudPreset saveUserPreset(String name, HudLayout layout) {
        Objects.requireNonNull(layout, "layout");
        HudLayout live = this.layout;
        this.layout = layout;
        try {
            return saveUserPreset(name);
        } finally {
            this.layout = live;
        }
    }

    /** Overwrites an existing user preset with the live layout. */
    public boolean updateUserPreset(String id) {
        for (int i = 0; i < userPresets.size(); i++) {
            HudPreset preset = userPresets.get(i);
            if (preset.id().equals(id)) {
                HudPreset updated = new HudPreset(preset.id(), preset.name(), layout, false);
                writePreset(updated);
                userPresets.set(i, updated);
                return true;
            }
        }
        return false;
    }

    /** Deletes a user preset (built-ins cannot be deleted). */
    public boolean deleteUserPreset(String id) {
        Optional<HudPreset> preset = userPresets.stream().filter(p -> p.id().equals(id)).findFirst();
        if (preset.isEmpty()) {
            return false;
        }
        try {
            Files.deleteIfExists(presetFile(id));
        } catch (IOException e) {
            CoreLog.warn(e, "Could not delete HUD preset {}", id);
            return false;
        }
        userPresets.remove(preset.get());
        return true;
    }

    private void writePreset(HudPreset preset) {
        JsonObject root = new JsonObject();
        root.addProperty(JsonStore.SCHEMA_VERSION, SCHEMA_VERSION);
        root.addProperty("name", preset.name());
        HudLayoutCodec.write(preset.layout(), root);
        store.writeObject(presetFile(preset.id()), root);
    }

    private Path presetFile(String id) {
        return paths.hudPresetsDir().resolve(id + ".json");
    }

    /** Listens for layout replacements. */
    public Runnable onLayoutChanged(Consumer<HudLayout> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private void fire() {
        for (Consumer<HudLayout> listener : listeners) {
            listener.accept(layout);
        }
    }
}
