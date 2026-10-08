package dev.vanta.core.profiles;

import com.google.gson.JsonObject;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.bridge.KeybindBridge;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.FileNames;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.cosmetics.CosmeticsSelection;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.crosshair.CrosshairStore;
import dev.vanta.core.hud.HudStore;
import dev.vanta.core.keybinds.VantaKeys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

import dev.vanta.core.settings.SettingsStore;

/**
 * Lists, creates, switches, exports and imports profiles.
 * <p>
 * Files: {@code profiles/<id>.json} per profile and {@code profiles/state.json} holding the active id. Activating a
 * profile applies its content to the settings store, HUD store, crosshair store and VANTA key mappings. Imports are
 * validated: ≤ 1 MiB, supported schema, sanitised file name, unknown keys ignored, string lengths bounded.
 */
public final class ProfileManager {
    /** Largest importable file. */
    public static final long MAX_IMPORT_BYTES = 1024L * 1024;
    private static final String STATE_ACTIVE = "activeId";
    /** Built-in content version the files were last re-seeded to ({@link BuiltInProfiles#CONTENT_VERSION}). */
    private static final String STATE_BUILT_INS = "builtInContent";

    private final JsonStore store;
    private final VantaPaths paths;
    private final Clock clock;
    private final SettingsStore settings;
    private final HudStore hud;
    private final CrosshairStore crosshair;
    private final KeybindBridge keybinds;
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final List<Consumer<Profile>> listeners = new CopyOnWriteArrayList<>();
    /**
     * Profiles whose last write did not complete. Every command writes the profile it changed right away, so after a
     * successful write nothing is pending and {@link #saveAll()} (which runs whenever a VANTA screen closes) writes
     * nothing; only a write that failed is retried there.
     */
    private final Set<String> unsaved = new LinkedHashSet<>();
    private boolean stateUnsaved;
    private String activeId;
    private int builtInContent;

    public ProfileManager(JsonStore store, VantaPaths paths, Clock clock, SettingsStore settings, HudStore hud,
                          CrosshairStore crosshair, KeybindBridge keybinds) {
        this.store = Objects.requireNonNull(store, "store");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.hud = Objects.requireNonNull(hud, "hud");
        this.crosshair = Objects.requireNonNull(crosshair, "crosshair");
        this.keybinds = Objects.requireNonNull(keybinds, "keybinds");
    }

    // ---- persistence -------------------------------------------------------------------------------------------

    /**
     * Loads profiles; creates the built-ins when the directory is empty and upgrades files of earlier releases
     * ({@link #upgradeSchema1()}). Does not activate anything.
     */
    public void load() {
        profiles.clear();
        unsaved.clear();
        stateUnsaved = false;
        Path dir = paths.profilesDir();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .filter(p -> !p.equals(paths.profilesStateFile()))
                        .sorted()
                        .forEach(this::loadFile);
            } catch (IOException e) {
                CoreLog.warn(e, "Could not list profiles in {}", dir);
            }
        }
        Optional<JsonObject> state = store.readObject(paths.profilesStateFile());
        builtInContent = state.map(s -> s.has(STATE_BUILT_INS) && s.get(STATE_BUILT_INS).isJsonPrimitive()
                && s.get(STATE_BUILT_INS).getAsJsonPrimitive().isNumber() ? s.get(STATE_BUILT_INS).getAsInt() : 0)
                .orElse(0);
        boolean seeded = false;
        if (profiles.isEmpty()) {
            for (Profile profile : BuiltInProfiles.create(settings.registry(), clock.millis())) {
                profiles.put(profile.id(), profile);
                write(profile);
            }
            seeded = true;
        } else {
            upgradeSchema1();
            seeded = reseedUntouchedBuiltIns();
        }
        String stored = state.map(s -> s.has(STATE_ACTIVE) && s.get(STATE_ACTIVE).isJsonPrimitive()
                ? s.get(STATE_ACTIVE).getAsString() : null).orElse(null);
        activeId = stored != null && profiles.containsKey(stored) ? stored : profiles.keySet().iterator().next();
        // A missing or stale state file is written once by the next saveAll(), not on every screen close.
        stateUnsaved = !activeId.equals(stored);
        if (seeded) {
            builtInContent = BuiltInProfiles.CONTENT_VERSION;
            saveState();
        }
    }

    /**
     * One-time refresh of built-in profiles the player never touched when their content changed in a release
     * ({@link BuiltInProfiles#CONTENT_VERSION} above the version recorded in {@code state.json}). Client 1.3.0 stopped
     * putting every vanilla default (volumes, sensitivity, FOV, GUI scale, …) into the built-ins, so activating
     * "Default" no longer resets options the profile is not about. Untouched means {@code updatedAt == createdAt}
     * (rename, icon and "Update from current" move it); edited built-ins and the player's own profiles are never
     * changed, nothing is deleted, and the re-seeded profile keeps its timestamps. The marker lives in
     * {@code state.json} rather than in a profile schema bump, so an older release can still read every profile.
     *
     * @return true when the marker has to be written (the check ran)
     */
    private boolean reseedUntouchedBuiltIns() {
        if (builtInContent >= BuiltInProfiles.CONTENT_VERSION) {
            return false;
        }
        for (Profile profile : new ArrayList<>(profiles.values())) {
            if (!BuiltInProfiles.IDS.contains(profile.id()) || profile.updatedAt() != profile.createdAt()) {
                continue;
            }
            Optional<Profile> shipped = BuiltInProfiles.shipped(settings.registry(), clock.millis(), profile.id());
            if (shipped.isEmpty() || shipped.get().settings().equals(profile.settings())) {
                continue;
            }
            Profile fresh = shipped.get();
            Profile reseeded = profile.withContent(fresh.settings(), fresh.hud(), fresh.keybinds(), fresh.crosshair(),
                    fresh.cosmetics(), profile.updatedAt());
            profiles.put(reseeded.id(), reseeded);
            write(reseeded);
            CoreLog.info("Re-seeded the untouched built-in profile {} (content {})", profile.id(),
                    BuiltInProfiles.CONTENT_VERSION);
        }
        // Built-ins that are new since the recorded content version are added once; a built-in the player deleted
        // in an earlier release (content version at or above the one it shipped with) is never brought back.
        for (String id : BuiltInProfiles.IDS) {
            if (profiles.containsKey(id) || BuiltInProfiles.addedInVersion(id) <= builtInContent) {
                continue;
            }
            Optional<Profile> shipped = BuiltInProfiles.shipped(settings.registry(), clock.millis(), id);
            if (shipped.isPresent()) {
                profiles.put(id, shipped.get());
                write(shipped.get());
                CoreLog.info("Added the new built-in profile {} (content {})", id, BuiltInProfiles.CONTENT_VERSION);
            }
        }
        return true;
    }

    /**
     * One-time upgrade of profile files written by releases before 1.2.0 (schema 1). Their built-in profiles carried
     * the 60 / 120 FPS cap of the old preset table, which activation replayed over the player's "Unlimited" choice.
     * Built-ins the player never touched (rename, icon and "Update from current" all move {@code updatedAt} past
     * {@code createdAt}) are re-seeded from the current {@link BuiltInProfiles}; edited built-ins and the player's
     * own profiles keep their content (activation derives the frame rate from their preset anyway). Every schema-1
     * file is re-written as schema {@value Profile#SCHEMA_VERSION}, so this runs once per upgrade. A re-seeded
     * profile keeps its timestamps, so it still counts as untouched for a later upgrade.
     */
    private void upgradeSchema1() {
        for (Profile profile : new ArrayList<>(profiles.values())) {
            if (profile.schemaVersion() >= Profile.SCHEMA_VERSION) {
                continue;
            }
            Profile upgraded = profile.withSchemaVersion(Profile.SCHEMA_VERSION);
            boolean untouchedBuiltIn = BuiltInProfiles.IDS.contains(profile.id())
                    && profile.updatedAt() == profile.createdAt();
            if (untouchedBuiltIn) {
                Optional<Profile> shipped = BuiltInProfiles.shipped(settings.registry(), clock.millis(), profile.id());
                if (shipped.isPresent()) {
                    Profile fresh = shipped.get();
                    upgraded = upgraded.withContent(fresh.settings(), fresh.hud(), fresh.keybinds(), fresh.crosshair(),
                            fresh.cosmetics(), profile.updatedAt());
                    CoreLog.info("Re-seeded the untouched built-in profile {} from the current defaults", profile.id());
                }
            }
            profiles.put(upgraded.id(), upgraded);
            write(upgraded);
        }
    }

    private void loadFile(Path file) {
        String stem = file.getFileName().toString();
        stem = stem.substring(0, stem.length() - ".json".length());
        if (!FileNames.isSafe(stem)) {
            CoreLog.warn("Ignoring profile with unsafe file name {}", file.getFileName());
            return;
        }
        Optional<JsonObject> json = store.readObject(file);
        if (json.isEmpty()) {
            return;
        }
        try {
            profiles.put(stem, ProfileCodec.fromJson(json.get(), stem));
        } catch (ProfileImportException e) {
            CoreLog.warn(e, "Ignoring invalid profile {}", file.getFileName());
        }
    }

    /**
     * Writes what is not on disk yet: profiles and the state file whose last write failed, and the state file once
     * after a load that found none. Runs whenever a VANTA screen closes (on the render thread), so it never rewrites
     * files that are already up to date; every command below writes its change immediately. A write that fails again
     * is logged and kept pending instead of breaking the screen close.
     */
    public void saveAll() {
        for (String id : new ArrayList<>(unsaved)) {
            Profile profile = profiles.get(id);
            if (profile == null) {
                unsaved.remove(id);
                continue;
            }
            try {
                write(profile);
            } catch (UncheckedIOException e) {
                CoreLog.warn(e, "Could not save profile {}; trying again later", id);
            }
        }
        if (stateUnsaved) {
            try {
                saveState();
            } catch (UncheckedIOException e) {
                CoreLog.warn(e, "Could not save the active profile; trying again later");
            }
        }
    }

    /** Whether a profile or the state file still has to be written ({@link #saveAll()} writes it). */
    public boolean hasUnsavedChanges() {
        return !unsaved.isEmpty() || stateUnsaved;
    }

    private void write(Profile profile) {
        unsaved.add(profile.id());
        store.writeObject(fileOf(profile.id()), ProfileCodec.toJson(profile));
        unsaved.remove(profile.id());
    }

    private void saveState() {
        stateUnsaved = true;
        JsonObject state = new JsonObject();
        state.addProperty(JsonStore.SCHEMA_VERSION, 1);
        state.addProperty(STATE_ACTIVE, activeId);
        state.addProperty(STATE_BUILT_INS, builtInContent);
        store.writeObject(paths.profilesStateFile(), state);
        stateUnsaved = false;
    }

    private Path fileOf(String id) {
        return paths.profilesDir().resolve(id + ".json");
    }

    // ---- queries -------------------------------------------------------------------------------------------------

    /** Profiles in load order. */
    public List<Profile> list() {
        return Collections.unmodifiableList(new ArrayList<>(profiles.values()));
    }

    /** Finds a profile by id. */
    public Optional<Profile> find(String id) {
        return Optional.ofNullable(profiles.get(id));
    }

    /** The active profile. */
    public Optional<Profile> active() {
        return Optional.ofNullable(profiles.get(activeId));
    }

    /** Id of the active profile. */
    public Optional<String> activeId() {
        return Optional.ofNullable(activeId);
    }

    /** Number of profiles. */
    public int size() {
        return profiles.size();
    }

    // ---- commands ------------------------------------------------------------------------------------------------

    /** Creates a profile from the current settings, HUD, keys, crosshair and cosmetics and saves it. */
    public Profile createFromCurrent(String name, String icon) {
        String cleanName = cleanName(name);
        String id = uniqueId(cleanName);
        long now = clock.millis();
        Profile profile = new Profile(Profile.SCHEMA_VERSION, id, cleanName, icon, now, now, liveSettings(),
                hud.layout(), currentVantaKeys(), crosshair.style(), CosmeticsSelection.fromSettings(settings));
        profiles.put(id, profile);
        write(profile);
        return profile;
    }

    /** Copies a profile under a new name. */
    public Optional<Profile> duplicate(String id, String newName) {
        Profile source = profiles.get(id);
        if (source == null) {
            return Optional.empty();
        }
        String cleanName = cleanName(newName);
        long now = clock.millis();
        Profile copy = new Profile(Profile.SCHEMA_VERSION, uniqueId(cleanName), cleanName, source.icon(), now, now,
                source.settings(), source.hud(), source.keybinds(), source.crosshair(), source.cosmetics());
        profiles.put(copy.id(), copy);
        write(copy);
        return Optional.of(copy);
    }

    /** Renames a profile (the id / file name stays). */
    public boolean rename(String id, String newName) {
        Profile profile = profiles.get(id);
        if (profile == null) {
            return false;
        }
        Profile renamed = profile.withName(cleanName(newName), clock.millis());
        profiles.put(id, renamed);
        write(renamed);
        return true;
    }

    /** Changes a profile's icon id. */
    public boolean setIcon(String id, String icon) {
        Profile profile = profiles.get(id);
        if (profile == null) {
            return false;
        }
        Profile updated = profile.withIcon(icon, clock.millis());
        profiles.put(id, updated);
        write(updated);
        return true;
    }

    /**
     * Deletes a profile. The last remaining profile cannot be deleted; deleting the active profile activates the
     * first remaining one.
     */
    public boolean delete(String id) {
        if (!profiles.containsKey(id) || profiles.size() <= 1) {
            return false;
        }
        profiles.remove(id);
        unsaved.remove(id);
        try {
            Files.deleteIfExists(fileOf(id));
        } catch (IOException e) {
            CoreLog.warn(e, "Could not delete profile file {}", id);
        }
        if (id.equals(activeId)) {
            activate(profiles.keySet().iterator().next());
        }
        return true;
    }

    /**
     * Applies a profile to the live stores and marks it active. The vanilla frame-rate limit and VSync are taken from
     * the profile's frame-rate choice ({@link BuiltInProfiles#withFrameRateFromPreset}), never from stale values in
     * the file.
     */
    public boolean activate(String id) {
        Profile profile = profiles.get(id);
        if (profile == null) {
            return false;
        }
        settings.applySnapshot(BuiltInProfiles.withFrameRateFromPreset(profile.settings()));
        if (!profile.hud().isEmpty()) {
            hud.setLayout(profile.hud());
        }
        crosshair.setStyle(profile.crosshair());
        profile.cosmetics().applyTo(settings);
        for (Map.Entry<String, KeyRef> entry : profile.keybinds().entrySet()) {
            keybinds.setKey(entry.getKey(), entry.getValue());
        }
        activeId = id;
        saveState();
        for (Consumer<Profile> listener : listeners) {
            listener.accept(profile);
        }
        return true;
    }

    /** Stores the live configuration into the active profile. */
    public Optional<Profile> updateActiveFromCurrent() {
        Profile active = profiles.get(activeId);
        if (active == null) {
            return Optional.empty();
        }
        Profile updated = active.withContent(liveSettings(), hud.layout(), currentVantaKeys(),
                crosshair.style(), CosmeticsSelection.fromSettings(settings), clock.millis());
        profiles.put(updated.id(), updated);
        write(updated);
        return Optional.of(updated);
    }

    /** Writes a profile to {@code target} (an export the user chose). */
    public boolean exportTo(String id, Path target) {
        Profile profile = profiles.get(id);
        if (profile == null) {
            return false;
        }
        store.writeObject(target, ProfileCodec.toJson(profile));
        return true;
    }

    /** Suggested export file name for a profile. */
    public static String exportFileName(Profile profile) {
        return "vanta-profile-" + FileNames.sanitize(profile.name(), profile.id()) + ".json";
    }

    /**
     * Imports a profile file. The new profile gets an id derived from its name and is saved; it is not activated.
     *
     * @throws ProfileImportException when the file is too large, not JSON, from a newer schema or invalid
     */
    public Profile importFrom(Path file) throws ProfileImportException {
        String text;
        try {
            if (!Files.isRegularFile(file)) {
                throw new ProfileImportException(ProfileImportException.Reason.IO_ERROR, "File not found");
            }
            if (Files.size(file) > MAX_IMPORT_BYTES) {
                throw new ProfileImportException(ProfileImportException.Reason.TOO_LARGE,
                        "Profile files must be at most 1 MiB");
            }
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ProfileImportException(ProfileImportException.Reason.IO_ERROR, "Could not read file", e);
        }
        Profile parsed = ProfileCodec.parse(text, "import");
        String id = uniqueId(parsed.name());
        long now = clock.millis();
        Profile imported = new Profile(Profile.SCHEMA_VERSION, id, uniqueName(parsed.name()), parsed.icon(),
                parsed.createdAt() == 0 ? now : parsed.createdAt(), now, parsed.settings(), parsed.hud(),
                parsed.keybinds(), parsed.crosshair(), parsed.cosmetics());
        profiles.put(id, imported);
        write(imported);
        return imported;
    }

    /** Listens for activations. */
    public Runnable onActivated(Consumer<Profile> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /**
     * The live settings for a profile: the game's real vanilla values (a "Custom" graphics preset stays "Custom" and
     * is skipped on activation) with the frame-rate choice taken from the game's real limit and VSync.
     */
    private Map<String, com.google.gson.JsonElement> liveSettings() {
        return BuiltInProfiles.withPresetFromFrameRate(settings.snapshot(true));
    }

    private Map<String, KeyRef> currentVantaKeys() {
        Map<String, KeyRef> out = new LinkedHashMap<>();
        for (var binding : keybinds.all()) {
            if (VantaKeys.isVantaKey(binding.id())) {
                out.put(binding.id(), binding.boundKey());
            }
        }
        return out;
    }

    private String uniqueId(String name) {
        String base = FileNames.sanitize(name, "profile");
        String id = base;
        int n = 2;
        while (profiles.containsKey(id)) {
            id = base + "-" + n++;
        }
        return id;
    }

    private String uniqueName(String name) {
        boolean taken = profiles.values().stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
        if (!taken) {
            return name;
        }
        int n = 2;
        while (true) {
            String candidate = name + " (" + n + ")";
            if (candidate.length() > Profile.MAX_NAME) {
                candidate = name.substring(0, Math.max(1, Profile.MAX_NAME - 5)) + " (" + n + ")";
            }
            String finalCandidate = candidate;
            if (profiles.values().stream().noneMatch(p -> p.name().equalsIgnoreCase(finalCandidate))) {
                return candidate;
            }
            n++;
        }
    }

    private static String cleanName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Profile name must not be empty");
        }
        return trimmed.length() > Profile.MAX_NAME ? trimmed.substring(0, Profile.MAX_NAME) : trimmed;
    }
}
