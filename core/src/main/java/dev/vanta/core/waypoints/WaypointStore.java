package dev.vanta.core.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.Migrations;
import dev.vanta.core.config.VantaPaths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every waypoint of every world, persisted in {@code config/vanta/waypoints.json}
 * ({@code {"schemaVersion":1,"waypoints":[...]}}). Render thread only, like every other store.
 * <p>
 * Names are unique per world (case-insensitive) because the assistant and the player refer to waypoints by name.
 * {@link #add} and {@link #update} reject a duplicate; a hand-edited file with duplicates is still read in full and
 * name lookups return the first match.
 */
public final class WaypointStore {
    public static final int SCHEMA_VERSION = 1;
    public static final Migrations MIGRATIONS = Migrations.none(SCHEMA_VERSION);
    /** Most waypoints kept, over all worlds. */
    public static final int MAX_WAYPOINTS = 500;

    private final JsonStore store;
    private final VantaPaths paths;
    private final Clock clock;
    private final List<Waypoint> waypoints = new ArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean dirty;
    private int sequence;

    public WaypointStore(JsonStore store, VantaPaths paths, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ---- persistence -------------------------------------------------------------------------------------------

    /** Loads {@code waypoints.json}; a missing or corrupt file yields an empty store. */
    public void load() {
        waypoints.clear();
        Optional<JsonObject> json = store.readMigrated(paths.waypointsFile(), MIGRATIONS);
        if (json.isPresent()) {
            JsonElement array = json.get().get("waypoints");
            if (array != null && array.isJsonArray()) {
                readAll(array.getAsJsonArray());
            }
        }
        dirty = false;
        fire();
    }

    private void readAll(JsonArray array) {
        Set<String> ids = new HashSet<>();
        int skipped = 0;
        for (JsonElement element : array) {
            if (waypoints.size() >= MAX_WAYPOINTS) {
                CoreLog.warn("waypoints.json holds more than {} waypoints; the rest is ignored", MAX_WAYPOINTS);
                break;
            }
            Optional<Waypoint> parsed = element.isJsonObject() ? Waypoint.fromJson(element.getAsJsonObject())
                    : Optional.empty();
            if (parsed.isEmpty() || !ids.add(parsed.get().id())) {
                skipped++;
                continue;
            }
            waypoints.add(parsed.get());
        }
        if (skipped > 0) {
            CoreLog.warn("Ignored {} invalid or duplicate entries in waypoints.json", skipped);
        }
    }

    /** Writes {@code waypoints.json}. */
    public void save() {
        store.writeObject(paths.waypointsFile(), toJson());
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

    /** The full document as written. */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty(JsonStore.SCHEMA_VERSION, SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        for (Waypoint waypoint : waypoints) {
            array.add(waypoint.toJson());
        }
        root.add("waypoints", array);
        return root;
    }

    // ---- queries -------------------------------------------------------------------------------------------------

    /** Every waypoint of every world, oldest first. */
    public List<Waypoint> all() {
        return List.copyOf(waypoints);
    }

    public int size() {
        return waypoints.size();
    }

    /** True when {@link #MAX_WAYPOINTS} is reached and {@link #add} would refuse. */
    public boolean isFull() {
        return waypoints.size() >= MAX_WAYPOINTS;
    }

    /** Waypoint by id. */
    public Optional<Waypoint> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (Waypoint waypoint : waypoints) {
            if (waypoint.id().equals(id)) {
                return Optional.of(waypoint);
            }
        }
        return Optional.empty();
    }

    /** Waypoint of {@code worldKey} by name (case-insensitive, whitespace trimmed). */
    public Optional<Waypoint> find(String worldKey, String name) {
        if (worldKey == null || name == null) {
            return Optional.empty();
        }
        for (Waypoint waypoint : waypoints) {
            if (waypoint.isIn(worldKey) && waypoint.hasName(name)) {
                return Optional.of(waypoint);
            }
        }
        return Optional.empty();
    }

    /** True when {@code worldKey} has a waypoint called {@code name}. */
    public boolean has(String worldKey, String name) {
        return find(worldKey, name).isPresent();
    }

    /** Names of the waypoints of {@code worldKey}, oldest first. */
    public List<String> names(String worldKey) {
        return forWorld(worldKey).stream().map(Waypoint::name).toList();
    }

    /** Waypoints of {@code worldKey} (all dimensions), oldest first. */
    public List<Waypoint> forWorld(String worldKey) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (waypoint.isIn(worldKey)) {
                out.add(waypoint);
            }
        }
        return List.copyOf(out);
    }

    /** Waypoints of {@code worldKey} in {@code dimension}, oldest first. */
    public List<Waypoint> forWorld(String worldKey, String dimension) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (waypoint.isIn(worldKey, dimension)) {
                out.add(waypoint);
            }
        }
        return List.copyOf(out);
    }

    /** Enabled waypoints of {@code worldKey} in {@code dimension}: the ones that get markers. */
    public List<Waypoint> enabledIn(String worldKey, String dimension) {
        return forWorld(worldKey, dimension).stream().filter(Waypoint::enabled).toList();
    }

    /** Every world key that has waypoints, in first-seen order. */
    public List<String> worldKeys() {
        List<String> out = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (!out.contains(waypoint.worldKey())) {
                out.add(waypoint.worldKey());
            }
        }
        return List.copyOf(out);
    }

    /**
     * Waypoints of {@code worldKey} whose name or category contains {@code query} (case-insensitive). A blank query
     * matches everything.
     */
    public List<Waypoint> search(String worldKey, String query) {
        return filter(forWorld(worldKey), query);
    }

    /** {@link #search(String, String)} over every world. */
    public List<Waypoint> searchAll(String query) {
        return filter(waypoints, query);
    }

    private static List<Waypoint> filter(List<Waypoint> scope, String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.copyOf(scope);
        }
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint waypoint : scope) {
            if (waypoint.name().toLowerCase(Locale.ROOT).contains(needle)
                    || waypoint.category().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(waypoint);
            }
        }
        return List.copyOf(out);
    }

    /** A sorted copy; {@code origin} is the player's position for {@link WaypointSort#DISTANCE}. */
    public static List<Waypoint> sort(Collection<Waypoint> waypoints, WaypointSort sort, Optional<Vec3d> origin) {
        return sort.sort(waypoints, origin);
    }

    /** Category suggestions: the built-in ones plus every category in use. */
    public List<String> categories() {
        return WaypointCategories.suggestions(waypoints);
    }

    // ---- mutations -----------------------------------------------------------------------------------------------

    /**
     * Adds a waypoint with the default category and the next palette colour.
     *
     * @see #add(String, String, String, Vec3d, String, int)
     */
    public Optional<Waypoint> add(String worldKey, String dimension, String name, Vec3d position) {
        return add(worldKey, dimension, name, position, WaypointCategories.DEFAULT,
                WaypointColors.forIndex(waypoints.size()));
    }

    /**
     * Adds a waypoint. The name and category are sanitised first ({@link Waypoint#sanitizeName},
     * {@link WaypointCategories#sanitize}).
     *
     * @return the stored waypoint, or empty when the world key is {@link WorldKeys#NONE}, the store is full
     *         ({@link #isFull()}) or the world already has a waypoint with that name ({@link #has})
     */
    public Optional<Waypoint> add(String worldKey, String dimension, String name, Vec3d position, String category,
                                  int color) {
        Objects.requireNonNull(position, "position");
        if (WorldKeys.isNone(worldKey) || isFull()) {
            return Optional.empty();
        }
        String key = worldKey.strip();
        String cleanName = Waypoint.sanitizeName(name);
        if (has(key, cleanName)) {
            return Optional.empty();
        }
        String cleanDimension = dimension == null || dimension.isBlank() ? Waypoint.UNKNOWN_DIMENSION
                : dimension.strip();
        Waypoint waypoint = new Waypoint(newId(), cleanName, key, cleanDimension, position.x(), position.y(),
                position.z(), color, WaypointCategories.sanitize(category), true, clock.millis());
        waypoints.add(waypoint);
        changed();
        return Optional.of(waypoint);
    }

    /**
     * Replaces the waypoint with {@code updated.id()}. The name and category are sanitised; id, world key and
     * creation time cannot change.
     *
     * @return the stored waypoint, or empty when the id is unknown or another waypoint of the world has the new name
     */
    public Optional<Waypoint> update(Waypoint updated) {
        Objects.requireNonNull(updated, "updated");
        int index = indexOf(updated.id());
        if (index < 0) {
            return Optional.empty();
        }
        Waypoint current = waypoints.get(index);
        String cleanName = Waypoint.sanitizeName(updated.name());
        Optional<Waypoint> clash = find(current.worldKey(), cleanName);
        if (clash.isPresent() && !clash.get().id().equals(current.id())) {
            return Optional.empty();
        }
        Waypoint next = new Waypoint(current.id(), cleanName, current.worldKey(),
                updated.dimension().isBlank() ? current.dimension() : updated.dimension().strip(), updated.x(),
                updated.y(), updated.z(), updated.color(), WaypointCategories.sanitize(updated.category()),
                updated.enabled(), current.createdAt());
        if (next.equals(current)) {
            return Optional.of(current);
        }
        waypoints.set(index, next);
        changed();
        return Optional.of(next);
    }

    /** Removes a waypoint by id. */
    public boolean remove(String id) {
        int index = indexOf(id);
        if (index < 0) {
            return false;
        }
        waypoints.remove(index);
        changed();
        return true;
    }

    /** Removes the waypoint of {@code worldKey} called {@code name}. */
    public boolean remove(String worldKey, String name) {
        Optional<Waypoint> found = find(worldKey, name);
        return found.isPresent() && remove(found.get().id());
    }

    /**
     * Enables or disables a waypoint by id.
     *
     * @return true when the waypoint exists (whether or not the state changed)
     */
    public boolean setEnabled(String id, boolean enabled) {
        int index = indexOf(id);
        if (index < 0) {
            return false;
        }
        Waypoint current = waypoints.get(index);
        if (current.enabled() != enabled) {
            waypoints.set(index, current.withEnabled(enabled));
            changed();
        }
        return true;
    }

    /**
     * Enables or disables the waypoint of {@code worldKey} called {@code name}.
     *
     * @return true when the waypoint exists
     */
    public boolean toggle(String worldKey, String name, boolean enabled) {
        Optional<Waypoint> found = find(worldKey, name);
        return found.isPresent() && setEnabled(found.get().id(), enabled);
    }

    /** Flips a waypoint's enabled state; the new state, or empty for an unknown id. */
    public Optional<Boolean> toggle(String id) {
        Optional<Waypoint> found = find(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        boolean next = !found.get().enabled();
        setEnabled(id, next);
        return Optional.of(next);
    }

    /** Fires after every change and after {@link #load()}. */
    public Runnable onChange(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    // ---- internals -----------------------------------------------------------------------------------------------

    private int indexOf(String id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * {@code wp<millis base36>-<sequence base36>}; the sequence skips ids a hand-edited file already uses, so an id is
     * unique even when many waypoints are created in the same millisecond.
     */
    private String newId() {
        String base = "wp" + Long.toString(clock.millis(), 36) + "-";
        String candidate = base + Integer.toString(sequence, 36);
        while (find(candidate).isPresent()) {
            sequence++;
            candidate = base + Integer.toString(sequence, 36);
        }
        sequence++;
        return candidate;
    }

    private void changed() {
        dirty = true;
        fire();
    }

    private void fire() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                CoreLog.warn(e, "Waypoint listener failed");
            }
        }
    }
}
