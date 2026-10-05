package dev.vanta.core.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.config.JsonStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code <gameDir>/config/vanta/modrinth.json}: every project installed from Modrinth. The VANTA launcher reads and
 * writes the same file, so the format is fixed and reading is tolerant:
 * <pre>
 * {"schemaVersion":1,"installed":[{"projectId":"…","slug":"…","title":"…","versionId":"…","versionNumber":"…",
 *   "type":"mod|shader|resourcepack","file":"mods/&lt;filename&gt;","sha512":"…","enabled":true,
 *   "requiredBy":["projectId",…],"installedAt":"ISO-8601"}]}
 * </pre>
 * Unknown top-level fields, unknown entry fields and entries VANTA cannot interpret are written back unchanged. A
 * missing file is an empty index; a corrupt file is moved aside by {@link JsonStore} and read as empty.
 */
public final class ModrinthIndex {
    /** The schema version written by this code. */
    public static final int SCHEMA_VERSION = 1;

    private final JsonObject extra;
    private final List<InstalledEntry> entries;
    private final List<JsonElement> unreadable;
    private final int schemaVersion;

    private ModrinthIndex(JsonObject extra, List<InstalledEntry> entries, List<JsonElement> unreadable,
                          int schemaVersion) {
        this.extra = extra;
        this.entries = entries;
        this.unreadable = unreadable;
        this.schemaVersion = schemaVersion;
    }

    /** An empty index. */
    public static ModrinthIndex empty() {
        return new ModrinthIndex(new JsonObject(), new ArrayList<>(), new ArrayList<>(), SCHEMA_VERSION);
    }

    /** Reads the index (empty when missing or corrupt). */
    public static ModrinthIndex read(JsonStore store, Path file) {
        return store.readObject(file).map(ModrinthIndex::fromJson).orElseGet(ModrinthIndex::empty);
    }

    /** Parses the JSON root object. */
    public static ModrinthIndex fromJson(JsonObject root) {
        JsonObject extra = new JsonObject();
        for (var e : root.entrySet()) {
            if (!e.getKey().equals("installed") && !e.getKey().equals(JsonStore.SCHEMA_VERSION)) {
                extra.add(e.getKey(), e.getValue() == null ? null : e.getValue().deepCopy());
            }
        }
        List<InstalledEntry> entries = new ArrayList<>();
        List<JsonElement> unreadable = new ArrayList<>();
        JsonElement installed = root.get("installed");
        if (installed != null && installed.isJsonArray()) {
            for (JsonElement element : installed.getAsJsonArray()) {
                Optional<InstalledEntry> entry = element != null && element.isJsonObject()
                        ? InstalledEntry.fromJson(element.getAsJsonObject()) : Optional.empty();
                if (entry.isPresent() && entries.stream().noneMatch(x -> x.projectId().equals(entry.get().projectId()))) {
                    entries.add(entry.get());
                } else if (element != null) {
                    unreadable.add(element.deepCopy());
                }
            }
        }
        int schema = Math.max(SCHEMA_VERSION, JsonStore.schemaVersion(root, SCHEMA_VERSION));
        return new ModrinthIndex(extra, entries, unreadable, schema);
    }

    /** Serialises the index (known fields win over unknown ones of the same name). */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty(JsonStore.SCHEMA_VERSION, schemaVersion);
        for (var e : extra.entrySet()) {
            root.add(e.getKey(), e.getValue() == null ? null : e.getValue().deepCopy());
        }
        JsonArray installed = new JsonArray();
        for (InstalledEntry entry : entries) {
            installed.add(entry.toJson());
        }
        for (JsonElement element : unreadable) {
            installed.add(element.deepCopy());
        }
        root.add("installed", installed);
        return root;
    }

    /** Writes the index atomically. */
    public void write(JsonStore store, Path file) {
        store.writeObject(file, toJson());
    }

    /** Readable entries in file order. */
    public List<InstalledEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** The entry of a project. */
    public Optional<InstalledEntry> find(String projectId) {
        for (InstalledEntry entry : entries) {
            if (entry.projectId().equals(projectId)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** The entry whose (enabled) file is {@code relativeFile}. */
    public Optional<InstalledEntry> findByFile(String relativeFile) {
        String wanted = InstalledEntry.canonicalFile(relativeFile);
        for (InstalledEntry entry : entries) {
            if (entry.file().equals(wanted)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** True when the project is listed. */
    public boolean contains(String projectId) {
        return find(projectId).isPresent();
    }

    /** Adds the entry or replaces the entry of the same project (keeping its position). */
    public void put(InstalledEntry entry) {
        Objects.requireNonNull(entry, "entry");
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).projectId().equals(entry.projectId())) {
                entries.set(i, entry);
                return;
            }
        }
        entries.add(entry);
    }

    /** Removes a project and drops it from every {@code requiredBy} list; returns the removed entry. */
    public Optional<InstalledEntry> remove(String projectId) {
        Optional<InstalledEntry> removed = find(projectId);
        entries.removeIf(e -> e.projectId().equals(projectId));
        for (int i = 0; i < entries.size(); i++) {
            InstalledEntry entry = entries.get(i);
            if (entry.requiredBy().contains(projectId)) {
                entries.set(i, entry.withoutRequiredBy(projectId));
            }
        }
        return removed;
    }

    /** Entries that could not be interpreted (kept verbatim). */
    public int unreadableCount() {
        return unreadable.size();
    }

    /** The schema version that will be written. */
    public int schemaVersion() {
        return schemaVersion;
    }
}
