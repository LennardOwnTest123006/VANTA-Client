package dev.vanta.core.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.Migrations;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The Nexus conversation: player and assistant entries with the applied and rejected receipts, kept in memory and
 * persisted to {@code config/vanta/nexus-chat.json} (last {@value #MAX_ENTRIES} entries, i.e. 50 turns). Follows the
 * store pattern: {@link #load()}, {@link #saveIfDirty()}.
 */
public final class NexusTranscript {
    /** Schema version of the chat file. */
    public static final int SCHEMA_VERSION = 1;
    /** Migration chain. */
    public static final Migrations MIGRATIONS = Migrations.none(SCHEMA_VERSION);
    /** Entries kept (a turn is a player entry plus an assistant entry). */
    public static final int MAX_ENTRIES = 100;
    /** Longest text kept per entry. */
    public static final int MAX_TEXT = 4000;

    /** Who wrote an entry. */
    public enum Role {
        USER, ASSISTANT;

        /** Lower-case id used in JSON. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<Role> fromId(String id) {
            for (Role role : values()) {
                if (role.id().equalsIgnoreCase(id)) {
                    return Optional.of(role);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * One chat bubble.
     *
     * @param applied  receipt lines of applied actions (assistant entries)
     * @param rejected explanation lines of rejected actions (assistant entries)
     * @param error    true when the entry reports a failed turn
     */
    public record Entry(Role role, String text, long at, List<String> applied, List<String> rejected, boolean error) {
        public Entry {
            Objects.requireNonNull(role, "role");
            text = Objects.requireNonNull(text, "text");
            if (text.length() > MAX_TEXT) {
                text = text.substring(0, MAX_TEXT);
            }
            applied = List.copyOf(applied);
            rejected = List.copyOf(rejected);
        }

        /** A player message. */
        public static Entry user(String text, long at) {
            return new Entry(Role.USER, text, at, List.of(), List.of(), false);
        }

        /** An assistant reply with its receipts. */
        public static Entry assistant(String text, long at, List<String> applied, List<String> rejected) {
            return new Entry(Role.ASSISTANT, text, at, applied, rejected, false);
        }

        /** An assistant entry that reports a failure. */
        public static Entry failure(String text, long at) {
            return new Entry(Role.ASSISTANT, text, at, List.of(), List.of(), true);
        }
    }

    private final JsonStore store;
    private final Path file;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Consumer<List<Entry>>> listeners = new CopyOnWriteArrayList<>();
    private boolean dirty;

    public NexusTranscript(JsonStore store, Path file) {
        this.store = Objects.requireNonNull(store, "store");
        this.file = Objects.requireNonNull(file, "file");
    }

    /** Reads the chat file (missing or corrupt: empty chat). */
    public void load() {
        entries.clear();
        Optional<JsonObject> json = store.readMigrated(file, MIGRATIONS);
        if (json.isPresent()) {
            JsonElement array = json.get().get("entries");
            if (array != null && array.isJsonArray()) {
                for (JsonElement element : array.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        read(element.getAsJsonObject()).ifPresent(entries::add);
                    }
                }
            }
        }
        trim();
        dirty = false;
        fire();
    }

    /** Writes the chat file. */
    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty(JsonStore.SCHEMA_VERSION, SCHEMA_VERSION);
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            array.add(write(entry));
        }
        root.add("entries", array);
        store.writeObject(file, root);
        dirty = false;
    }

    /** Writes only when something changed since load/save. */
    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    /** All entries, oldest first. */
    public List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** The last {@code count} entries, oldest first. */
    public List<Entry> lastEntries(int count) {
        int from = Math.max(0, entries.size() - Math.max(0, count));
        return Collections.unmodifiableList(new ArrayList<>(entries.subList(from, entries.size())));
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Appends an entry (oldest entries drop out beyond {@value #MAX_ENTRIES}). */
    public void append(Entry entry) {
        entries.add(Objects.requireNonNull(entry, "entry"));
        trim();
        dirty = true;
        fire();
    }

    /** Removes every entry. */
    public void clear() {
        if (entries.isEmpty()) {
            return;
        }
        entries.clear();
        dirty = true;
        fire();
    }

    /** Listens for changes; the returned runnable unsubscribes. */
    public Runnable onChanged(Consumer<List<Entry>> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private void trim() {
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    private void fire() {
        List<Entry> snapshot = entries();
        for (Consumer<List<Entry>> listener : listeners) {
            listener.accept(snapshot);
        }
    }

    private static JsonObject write(Entry entry) {
        JsonObject o = new JsonObject();
        o.addProperty("role", entry.role().id());
        o.addProperty("text", entry.text());
        o.addProperty("at", entry.at());
        if (!entry.applied().isEmpty()) {
            o.add("applied", strings(entry.applied()));
        }
        if (!entry.rejected().isEmpty()) {
            o.add("rejected", strings(entry.rejected()));
        }
        if (entry.error()) {
            o.addProperty("error", true);
        }
        return o;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static Optional<Entry> read(JsonObject o) {
        JsonElement roleElement = o.get("role");
        JsonElement textElement = o.get("text");
        if (roleElement == null || !roleElement.isJsonPrimitive() || textElement == null
                || !textElement.isJsonPrimitive()) {
            return Optional.empty();
        }
        Optional<Role> role = Role.fromId(roleElement.getAsString());
        if (role.isEmpty()) {
            return Optional.empty();
        }
        long at = 0;
        JsonElement atElement = o.get("at");
        if (atElement != null && atElement.isJsonPrimitive() && atElement.getAsJsonPrimitive().isNumber()) {
            at = atElement.getAsLong();
        }
        boolean error = o.has("error") && o.get("error").isJsonPrimitive() && o.get("error").getAsJsonPrimitive()
                .isBoolean() && o.get("error").getAsBoolean();
        return Optional.of(new Entry(role.get(), textElement.getAsString(), at, stringList(o.get("applied")),
                stringList(o.get("rejected")), error));
    }

    private static List<String> stringList(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement e : element.getAsJsonArray()) {
            if (e.isJsonPrimitive()) {
                out.add(e.getAsString());
            }
        }
        return out;
    }
}
