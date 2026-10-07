package dev.vanta.core.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.settings.SettingChange;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Cosmetics service: loads packs from {@code config/vanta/cosmetics/*.json}, exposes the registry and the current
 * {@link CosmeticsSelection}, and notifies the UI when the selection changes. Selection values live in the settings
 * store; {@code cosmetics.json} only records which packs were loaded (for the About screen).
 */
public final class CosmeticsStore {
    public static final int SCHEMA_VERSION = 1;
    /** Largest pack file accepted (256 KiB). */
    public static final long MAX_PACK_BYTES = 256L * 1024;

    private static final Set<String> SELECTION_SETTINGS = Set.of(
            VantaSettings.GENERAL_THEME_ID.id(), VantaSettings.MENU_BACKGROUND.id(),
            VantaSettings.MENU_PARTICLES.id(), VantaSettings.COSMETICS_HUD_THEME.id(),
            VantaSettings.COSMETICS_BADGE.id(), VantaSettings.COSMETICS_CROSSHAIR_PRESET.id());

    private final SettingsStore settings;
    private final CosmeticsRegistry registry;
    private final JsonStore store;
    private final VantaPaths paths;
    private final List<Consumer<CosmeticsSelection>> listeners = new CopyOnWriteArrayList<>();
    private final List<String> loadErrors = new ArrayList<>();
    /** Pack ids of the last successful {@link #save()}; null until the first one. */
    private List<String> savedPackIds;

    public CosmeticsStore(SettingsStore settings, CosmeticsRegistry registry, JsonStore store, VantaPaths paths) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.store = Objects.requireNonNull(store, "store");
        this.paths = Objects.requireNonNull(paths, "paths");
        settings.addListener(this::onSettingChanged);
    }

    /** Loads packs from the cosmetics directory. */
    public void load() {
        registry.clearPacks();
        loadErrors.clear();
        Path dir = paths.cosmeticPacksDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(this::loadPack);
            } catch (IOException e) {
                CoreLog.warn(e, "Could not list cosmetic packs in {}", dir);
            }
        }
        fire();
    }

    private void loadPack(Path file) {
        try {
            if (Files.size(file) > MAX_PACK_BYTES) {
                loadErrors.add(file.getFileName() + ": larger than 256 KiB");
                return;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                CosmeticPack pack = CosmeticsRegistry.parsePack(reader);
                registry.addPack(pack);
            }
        } catch (IOException | JsonParseException | IllegalArgumentException | IllegalStateException e) {
            CoreLog.warn(e, "Ignoring invalid cosmetic pack {}", file.getFileName());
            loadErrors.add(file.getFileName() + ": " + e.getMessage());
        }
    }

    /** Writes {@code cosmetics.json} (installed pack ids). */
    public void save() {
        List<String> ids = installedPackIds();
        JsonObject root = new JsonObject();
        root.addProperty(JsonStore.SCHEMA_VERSION, SCHEMA_VERSION);
        JsonArray packs = new JsonArray();
        for (String id : ids) {
            packs.add(id);
        }
        root.add("installedPacks", packs);
        store.writeObject(paths.cosmeticsFile(), root);
        savedPackIds = ids;
    }

    /**
     * Writes {@code cosmetics.json} only when the installed packs differ from what this store last wrote (the first
     * call after start-up writes once). Runs whenever a VANTA screen closes, on the render thread, so an unchanged
     * file is not rewritten every time.
     */
    public void saveIfDirty() {
        if (isDirty()) {
            save();
        }
    }

    /** Whether the installed packs differ from what was last written (true before the first write). */
    public boolean isDirty() {
        return !installedPackIds().equals(savedPackIds);
    }

    private List<String> installedPackIds() {
        List<String> ids = new ArrayList<>();
        for (CosmeticPack pack : registry.packs()) {
            ids.add(pack.id());
        }
        return ids;
    }

    /** The registry. */
    public CosmeticsRegistry registry() {
        return registry;
    }

    /** Human-readable problems from the last {@link #load()}. */
    public List<String> loadErrors() {
        return List.copyOf(loadErrors);
    }

    /** Current selection. */
    public CosmeticsSelection selection() {
        return CosmeticsSelection.fromSettings(settings);
    }

    /** Active UI theme (default when the selected id is unknown). */
    public UiThemeDefinition activeTheme() {
        return registry.themeOrDefault(settings.get(VantaSettings.GENERAL_THEME_ID));
    }

    /** Selects a theme by id (unknown ids are ignored). */
    public boolean selectTheme(String id) {
        if (registry.findTheme(id).isEmpty()) {
            return false;
        }
        return settings.set(VantaSettings.GENERAL_THEME_ID, id);
    }

    public boolean selectBackground(MenuBackground background) {
        return settings.set(VantaSettings.MENU_BACKGROUND, background);
    }

    public boolean selectParticles(MenuParticles particles) {
        return settings.set(VantaSettings.MENU_PARTICLES, particles);
    }

    public boolean selectHudTheme(HudTheme theme) {
        return settings.set(VantaSettings.COSMETICS_HUD_THEME, theme);
    }

    public boolean selectBadge(Badge badge) {
        return settings.set(VantaSettings.COSMETICS_BADGE, badge);
    }

    /** Applies a whole selection (profiles). */
    public void apply(CosmeticsSelection selection) {
        selection.applyTo(settings);
    }

    /** Every built-in cosmetic is owned; pack themes are owned once installed. */
    public boolean isOwned(UiThemeDefinition theme) {
        return registry.findTheme(theme.id()).isPresent();
    }

    /** Listens for selection changes. */
    public Runnable onSelectionChanged(Consumer<CosmeticsSelection> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private void onSettingChanged(SettingChange change) {
        if (SELECTION_SETTINGS.contains(change.setting().id())) {
            fire();
        }
    }

    private void fire() {
        CosmeticsSelection current = selection();
        for (Consumer<CosmeticsSelection> listener : listeners) {
            listener.accept(current);
        }
    }
}
