package dev.vanta.core.crosshair;

import com.google.gson.JsonObject;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.Migrations;
import dev.vanta.core.config.VantaPaths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Persists the active crosshair style in {@code crosshair.json}.
 */
public final class CrosshairStore {
    public static final int SCHEMA_VERSION = 1;
    public static final Migrations MIGRATIONS = Migrations.none(SCHEMA_VERSION);

    private final JsonStore store;
    private final VantaPaths paths;
    private final List<Consumer<CrosshairStyle>> listeners = new CopyOnWriteArrayList<>();
    private CrosshairStyle style = CrosshairStyle.DEFAULT;
    private boolean dirty;

    public CrosshairStore(JsonStore store, VantaPaths paths) {
        this.store = Objects.requireNonNull(store, "store");
        this.paths = Objects.requireNonNull(paths, "paths");
    }

    /** Loads the style (default when missing). */
    public void load() {
        Optional<JsonObject> json = store.readMigrated(paths.crosshairFile(), MIGRATIONS);
        style = json.map(CrosshairStyle::fromJson).orElse(CrosshairStyle.DEFAULT);
        dirty = false;
        fire();
    }

    /** Writes {@code crosshair.json}. */
    public void save() {
        JsonObject root = style.toJson();
        root.addProperty(JsonStore.SCHEMA_VERSION, SCHEMA_VERSION);
        store.writeObject(paths.crosshairFile(), root);
        dirty = false;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Active style. */
    public CrosshairStyle style() {
        return style;
    }

    /** Replaces the style and notifies listeners. */
    public void setStyle(CrosshairStyle next) {
        Objects.requireNonNull(next, "next");
        if (next.equals(style)) {
            return;
        }
        style = next;
        dirty = true;
        fire();
    }

    /** Applies a built-in preset. */
    public void applyPreset(CrosshairPreset preset) {
        setStyle(preset.style());
    }

    /** The built-in preset equal to the active style, if any. */
    public Optional<CrosshairPreset> activePreset() {
        return CrosshairPresets.matching(style);
    }

    public Runnable onChange(Consumer<CrosshairStyle> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private void fire() {
        for (Consumer<CrosshairStyle> listener : listeners) {
            listener.accept(style);
        }
    }
}
