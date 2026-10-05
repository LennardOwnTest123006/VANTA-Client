package dev.vanta.launcher.core.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.util.AtomicFiles;
import dev.vanta.launcher.core.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code <instance>/config/vanta/modrinth.json}: the Modrinth content installed into the VANTA instance. The launcher
 * (performance pack, Mods page) and the VANTA Client's in-game browser share this file:
 *
 * <pre>
 * {"schemaVersion":1,"installed":[{"projectId":"...","slug":"...","title":"...","versionId":"...","versionNumber":"...",
 *   "type":"mod|shader|resourcepack","file":"mods/&lt;filename&gt;","sha512":"...","enabled":true,
 *   "requiredBy":["projectId",...],"installedAt":"ISO-8601"}]}
 * </pre>
 *
 * <p>The document is kept as a JSON tree: keys this launcher does not know (in the root and in every entry) survive a
 * rewrite unchanged, and a newer {@code schemaVersion} is never lowered. {@code file} is relative to the instance
 * directory with {@code /} separators and names the file as Modrinth published it; a disabled entry
 * ({@code "enabled": false}) is on disk as {@code <file>.disabled}. Writes are atomic.</p>
 */
public final class ModrinthIndex {

    /** Schema version this launcher writes. */
    public static final int SCHEMA_VERSION = 1;
    /** Suffix of a disabled file. */
    public static final String DISABLED_SUFFIX = ".disabled";

    private static final Logger LOG = LauncherLog.get("Modrinth");

    private final Path file;
    private final JsonObject root;
    private final JsonArray installed;

    private ModrinthIndex(final Path file, final JsonObject root) {
        this.file = file;
        this.root = root;
        if (!root.has("schemaVersion") || !root.get("schemaVersion").isJsonPrimitive()) {
            root.addProperty("schemaVersion", SCHEMA_VERSION);
        }
        final JsonElement list = root.get("installed");
        if (list == null || !list.isJsonArray()) {
            root.add("installed", new JsonArray());
        }
        this.installed = root.getAsJsonArray("installed");
    }

    /**
     * Loads the index. A missing file is an empty index; an unreadable one is kept as {@code modrinth.json.corrupt}
     * and an empty index is returned (the files on disk are still listed as untracked).
     *
     * @param file {@code modrinth.json}
     * @return index
     */
    public static ModrinthIndex load(final Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            return new ModrinthIndex(file, new JsonObject());
        }
        try {
            final JsonElement tree = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (tree.isJsonObject()) {
                return new ModrinthIndex(file, tree.getAsJsonObject());
            }
            throw new JsonParseException("not a JSON object");
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOG.log(Level.WARNING, "{0} is unreadable ({1}); it is kept as {0}.corrupt", new Object[] {file, e.getMessage()});
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailure) {
                LOG.log(Level.FINE, "Could not keep the unreadable index", moveFailure);
            }
            return new ModrinthIndex(file, new JsonObject());
        }
    }

    /** @return the file */
    public Path file() {
        return file;
    }

    /** @return the schema version in the file (at least {@value #SCHEMA_VERSION}) */
    public int schemaVersion() {
        try {
            return root.get("schemaVersion").getAsInt();
        } catch (RuntimeException e) {
            return SCHEMA_VERSION;
        }
    }

    /** @return entries in file order (entries without a project id are skipped) */
    public List<Entry> entries() {
        final List<Entry> out = new ArrayList<>();
        for (JsonElement e : installed) {
            if (e.isJsonObject()) {
                final Entry entry = new Entry(e.getAsJsonObject());
                if (!entry.projectId().isEmpty()) {
                    out.add(entry);
                }
            }
        }
        return out;
    }

    /**
     * @param projectId Modrinth project id
     * @return the entry of that project
     */
    public Optional<Entry> byProjectId(final String projectId) {
        return entries().stream().filter(e -> e.projectId().equals(projectId)).findFirst();
    }

    /**
     * Adds an entry, or updates the entry of the same project in place (its unknown keys are kept).
     *
     * @param projectId project id
     * @return the entry to fill in
     */
    public Entry upsert(final String projectId) {
        final Optional<Entry> existing = byProjectId(projectId);
        if (existing.isPresent()) {
            return existing.get();
        }
        final JsonObject obj = new JsonObject();
        obj.addProperty("projectId", projectId);
        installed.add(obj);
        return new Entry(obj);
    }

    /**
     * Removes the entry of a project and the project from every {@code requiredBy}.
     *
     * @param projectId project id
     * @return whether an entry was removed
     */
    public boolean remove(final String projectId) {
        boolean removed = false;
        for (int i = installed.size() - 1; i >= 0; i--) {
            final JsonElement e = installed.get(i);
            if (e.isJsonObject() && projectId.equals(new Entry(e.getAsJsonObject()).projectId())) {
                installed.remove(i);
                removed = true;
            }
        }
        for (Entry e : entries()) {
            final Set<String> required = new LinkedHashSet<>(e.requiredBy());
            if (required.remove(projectId)) {
                e.requiredBy(required);
            }
        }
        return removed;
    }

    /**
     * Writes the index atomically.
     *
     * @throws IOException on failure
     */
    public void save() throws IOException {
        AtomicFiles.writeString(file, Json.treeToJson(root) + System.lineSeparator());
    }

    /**
     * One installed project. Getters tolerate missing or mistyped values; setters write the known keys only.
     */
    public static final class Entry {

        private final JsonObject obj;

        Entry(final JsonObject obj) {
            this.obj = obj;
        }

        /** @return project id */
        public String projectId() {
            return string("projectId");
        }

        /** @return slug */
        public String slug() {
            return string("slug");
        }

        /** @return title (the slug when missing) */
        public String title() {
            final String t = string("title");
            return t.isEmpty() ? slug() : t;
        }

        /** @return version id */
        public String versionId() {
            return string("versionId");
        }

        /** @return version number */
        public String versionNumber() {
            return string("versionNumber");
        }

        /** @return content type (from {@code type}, else from the folder in {@code file}) */
        public Optional<ContentType> type() {
            final Optional<ContentType> t = ContentType.fromId(string("type"));
            if (t.isPresent()) {
                return t;
            }
            final String f = file();
            final int slash = f.indexOf('/');
            return slash > 0 ? ContentType.fromFolder(f.substring(0, slash)) : Optional.empty();
        }

        /** @return {@code <folder>/<file name>} relative to the instance, without a {@code .disabled} suffix */
        public String file() {
            final String f = string("file").replace('\\', '/');
            return f.endsWith(DISABLED_SUFFIX) ? f.substring(0, f.length() - DISABLED_SUFFIX.length()) : f;
        }

        /** @return the file name part of {@link #file()} */
        public String fileName() {
            final String f = file();
            return f.substring(f.lastIndexOf('/') + 1);
        }

        /** @return lower-case hex SHA-512 */
        public String sha512() {
            return string("sha512").toLowerCase(java.util.Locale.ROOT);
        }

        /** @return whether the content is enabled (missing: enabled unless {@code file} ends with {@code .disabled}) */
        public boolean enabled() {
            final JsonElement e = obj.get("enabled");
            if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
                return e.getAsBoolean();
            }
            return !string("file").endsWith(DISABLED_SUFFIX);
        }

        /** @return project ids that required this project as a dependency */
        public List<String> requiredBy() {
            final JsonElement e = obj.get("requiredBy");
            final List<String> out = new ArrayList<>();
            if (e != null && e.isJsonArray()) {
                for (JsonElement id : e.getAsJsonArray()) {
                    if (id.isJsonPrimitive()) {
                        out.add(id.getAsString());
                    }
                }
            }
            return out;
        }

        /** @return install time (ISO-8601) */
        public String installedAt() {
            return string("installedAt");
        }

        /**
         * @param instanceDir instance directory
         * @return where the file is on disk now: {@code <file>} when enabled, {@code <file>.disabled} when disabled, or the
         *     other variant when only that one exists
         */
        public Path path(final Path instanceDir) {
            final Path plain = instanceDir.resolve(file());
            final Path disabled = plain.resolveSibling(plain.getFileName() + DISABLED_SUFFIX);
            if (enabled()) {
                return Files.exists(plain) || !Files.exists(disabled) ? plain : disabled;
            }
            return Files.exists(disabled) || !Files.exists(plain) ? disabled : plain;
        }

        /** @param v slug @return this */
        public Entry slug(final String v) {
            obj.addProperty("slug", nz(v));
            return this;
        }

        /** @param v title @return this */
        public Entry title(final String v) {
            obj.addProperty("title", nz(v));
            return this;
        }

        /** @param v version id @return this */
        public Entry versionId(final String v) {
            obj.addProperty("versionId", nz(v));
            return this;
        }

        /** @param v version number @return this */
        public Entry versionNumber(final String v) {
            obj.addProperty("versionNumber", nz(v));
            return this;
        }

        /** @param v content type @return this */
        public Entry type(final ContentType v) {
            obj.addProperty("type", v.id());
            return this;
        }

        /** @param v {@code <folder>/<file name>} @return this */
        public Entry file(final String v) {
            obj.addProperty("file", nz(v));
            return this;
        }

        /** @param v hex SHA-512 @return this */
        public Entry sha512(final String v) {
            obj.addProperty("sha512", nz(v));
            return this;
        }

        /** @param v enabled @return this */
        public Entry enabled(final boolean v) {
            obj.addProperty("enabled", v);
            return this;
        }

        /** @param ids project ids @return this */
        public Entry requiredBy(final Iterable<String> ids) {
            final JsonArray a = new JsonArray();
            for (String id : new LinkedHashSet<>(toList(ids))) {
                a.add(new JsonPrimitive(id));
            }
            obj.add("requiredBy", a);
            return this;
        }

        /** @param v ISO-8601 time @return this */
        public Entry installedAt(final String v) {
            obj.addProperty("installedAt", nz(v));
            return this;
        }

        /** @return the raw JSON object (tests) */
        JsonObject raw() {
            return obj;
        }

        private String string(final String key) {
            final JsonElement e = obj.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsString().trim() : "";
        }

        private static String nz(final String v) {
            return v == null ? "" : v;
        }

        private static List<String> toList(final Iterable<String> ids) {
            final List<String> out = new ArrayList<>();
            ids.forEach(out::add);
            return out;
        }
    }
}
