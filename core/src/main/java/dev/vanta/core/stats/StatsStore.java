package dev.vanta.core.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.Migrations;
import dev.vanta.core.config.VantaPaths;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persists lifetime aggregates and the last {@value #RECENT_SESSIONS} sessions in {@code stats.json}.
 * Nothing is ever sent anywhere; {@link #exportTo(Path)} writes a copy the user asked for.
 */
public final class StatsStore {
    public static final int SCHEMA_VERSION = 1;
    public static final Migrations MIGRATIONS = Migrations.none(SCHEMA_VERSION);
    /** Number of recent sessions kept. */
    public static final int RECENT_SESSIONS = 30;

    private final JsonStore store;
    private final VantaPaths paths;
    private LifetimeStats lifetime = LifetimeStats.EMPTY;
    private final Deque<SessionRecord> recent = new ArrayDeque<>();
    private boolean dirty;

    public StatsStore(JsonStore store, VantaPaths paths) {
        this.store = Objects.requireNonNull(store, "store");
        this.paths = Objects.requireNonNull(paths, "paths");
    }

    /** Loads {@code stats.json}. */
    public void load() {
        lifetime = LifetimeStats.EMPTY;
        recent.clear();
        Optional<JsonObject> json = store.readMigrated(paths.statsFile(), MIGRATIONS);
        if (json.isPresent()) {
            JsonElement lifetimeElement = json.get().get("lifetime");
            if (lifetimeElement != null && lifetimeElement.isJsonObject()) {
                lifetime = LifetimeStats.fromJson(lifetimeElement.getAsJsonObject());
            }
            JsonElement sessions = json.get().get("recentSessions");
            if (sessions != null && sessions.isJsonArray()) {
                for (JsonElement element : sessions.getAsJsonArray()) {
                    if (element.isJsonObject() && recent.size() < RECENT_SESSIONS) {
                        recent.addLast(SessionRecord.fromJson(element.getAsJsonObject()));
                    }
                }
            }
        }
        dirty = false;
    }

    /** Writes {@code stats.json}. */
    public void save() {
        store.writeObject(paths.statsFile(), toJson());
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

    /** Full JSON document (also used by {@link #exportTo}). */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty(JsonStore.SCHEMA_VERSION, SCHEMA_VERSION);
        root.add("lifetime", lifetime.toJson());
        JsonArray sessions = new JsonArray();
        for (SessionRecord record : recent) {
            sessions.add(record.toJson());
        }
        root.add("recentSessions", sessions);
        return root;
    }

    /** Lifetime aggregates. */
    public LifetimeStats lifetime() {
        return lifetime;
    }

    /** Recent sessions, newest first. */
    public List<SessionRecord> recentSessions() {
        return List.copyOf(new ArrayList<>(recent));
    }

    /** Adds a finished session. */
    public void record(SessionRecord session) {
        Objects.requireNonNull(session, "session");
        lifetime = lifetime.plus(session);
        recent.addFirst(session);
        while (recent.size() > RECENT_SESSIONS) {
            recent.removeLast();
        }
        dirty = true;
    }

    /** Deletes everything and saves. */
    public void clearAll() {
        lifetime = LifetimeStats.EMPTY;
        recent.clear();
        dirty = true;
        save();
    }

    /** Removes stored names when the matching privacy setting is turned off. */
    public void forgetNames(boolean worlds, boolean servers) {
        if (!worlds && !servers) {
            return;
        }
        lifetime = lifetime.withoutNames(worlds, servers);
        List<SessionRecord> copy = new ArrayList<>(recent);
        recent.clear();
        for (SessionRecord r : copy) {
            recent.addLast(new SessionRecord(r.startedAt(), r.endedAt(), r.playtimeMs(), r.averageFps(), r.maxFps(),
                    worlds ? List.of() : r.worlds(), servers ? List.of() : r.servers(), r.distanceBlocks(),
                    r.blocksBroken(), r.blocksPlaced(), r.screenshots()));
        }
        dirty = true;
    }

    /** Writes a copy of the statistics to {@code target}. */
    public void exportTo(Path target) {
        store.writeObject(target, toJson());
    }
}
