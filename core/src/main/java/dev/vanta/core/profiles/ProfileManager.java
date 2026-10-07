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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private final JsonStore store;
    private final VantaPaths paths;
    private final Clock clock;
    private final SettingsStore settings;
    private final HudStore hud;
    private final CrosshairStore crosshair;
    private final KeybindBridge keybinds;
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final List<Consumer<Profile>> listeners = new CopyOnWriteArrayList<>();
    private String activeId;

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
        if (profiles.isEmpty()) {
            for (Profile profile : BuiltInProfiles.create(settings.registry(), clock.millis())) {
                profiles.put(profile.id(), profile);
                write(profile);
            }
        } else {
            upgradeSchema1();
        }
        Optional<JsonObject> state = store.readObject(paths.profilesStateFile());
        String stored = state.map(s -> s.has(STATE_ACTIVE) && s.get(STATE_ACTIVE).isJsonPrimitive()
                ? s.get(STATE_ACTIVE).getAsString() : null).orElse(null);
        activeId = stored != null && profiles.containsKey(stored) ? stored : profiles.keySet().iterator().next();
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

    /** Writes every profile and the state file. */
    public void saveAll() {
        for (Profile profile : profiles.values()) {
            write(profile);
        }
        saveState();
    }

    private void write(Profile profile) {
        store.writeObject(fileOf(profile.id()), ProfileCodec.toJson(profile));
    }

    private void saveState() {
        JsonObject state = new JsonObject();
        state.addProperty(JsonStore.SCHEMA_VERSION, 1);
        state.addProperty(STATE_ACTIVE, activeId);
        store.writeObject(paths.profilesStateFile(), state);
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
        Profile profile = new Profile(Profile.SCHEMA_VERSION, id, cleanName, icon, now, now, settings.snapshot(true),
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
        Profile updated = active.withContent(settings.snapshot(true), hud.layout(), currentVantaKeys(),
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
