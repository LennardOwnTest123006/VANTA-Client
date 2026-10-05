package dev.vanta.core.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One project VANTA (or the VANTA launcher) installed from Modrinth, as stored in {@code config/vanta/modrinth.json}.
 * <p>
 * {@code file} is the path relative to the game directory with forward slashes and always names the <em>enabled</em>
 * file ({@code mods/sodium-fabric-0.8.14+mc1.21.11.jar}). A disabled mod lives at the same path with
 * {@value #DISABLED_SUFFIX} appended and has {@code enabled: false}. Unknown JSON fields of the entry are kept in
 * {@link #extra()} and written back unchanged.
 *
 * @param projectId     Modrinth project id
 * @param slug          project slug
 * @param title         display name
 * @param versionId     installed version id
 * @param versionNumber installed version number
 * @param type          {@code mod}, {@code shader} or {@code resourcepack} (kept verbatim)
 * @param file          path of the enabled file relative to the game directory
 * @param sha512        SHA-512 of the file
 * @param enabled       whether the file is active
 * @param requiredBy    project ids of installed projects that need this one
 * @param installedAt   ISO-8601 time of the install
 * @param extra         unknown fields to preserve
 */
public record InstalledEntry(String projectId, String slug, String title, String versionId, String versionNumber,
                             String type, String file, String sha512, boolean enabled, List<String> requiredBy,
                             String installedAt, JsonObject extra) {
    /** Suffix Fabric ignores; a disabled mod is renamed to {@code <file>.disabled}. */
    public static final String DISABLED_SUFFIX = ".disabled";

    public InstalledEntry {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(file, "file");
        slug = slug == null ? "" : slug;
        title = title == null || title.isBlank() ? (slug.isEmpty() ? projectId : slug) : title;
        versionId = versionId == null ? "" : versionId;
        versionNumber = versionNumber == null ? "" : versionNumber;
        type = type == null ? "" : type;
        file = canonicalFile(file);
        sha512 = sha512 == null ? "" : sha512;
        requiredBy = List.copyOf(new LinkedHashSet<>(requiredBy == null ? List.of() : requiredBy));
        installedAt = installedAt == null ? "" : installedAt;
        extra = extra == null ? new JsonObject() : extra.deepCopy();
    }

    /** The project type when known. */
    public Optional<ModrinthProjectType> projectType() {
        return ModrinthProjectType.fromApi(type);
    }

    /** File name without the folder. */
    public String fileName() {
        int slash = file.lastIndexOf('/');
        return slash < 0 ? file : file.substring(slash + 1);
    }

    /** Path of the enabled file. */
    public Path enabledPath(Path gameDir) {
        return gameDir.resolve(file).normalize();
    }

    /** Path of the disabled file. */
    public Path disabledPath(Path gameDir) {
        return gameDir.resolve(file + DISABLED_SUFFIX).normalize();
    }

    /** Where the file should be according to {@link #enabled()}. */
    public Path expectedPath(Path gameDir) {
        return enabled ? enabledPath(gameDir) : disabledPath(gameDir);
    }

    public InstalledEntry withEnabled(boolean value) {
        return new InstalledEntry(projectId, slug, title, versionId, versionNumber, type, file, sha512, value, requiredBy,
                installedAt, extra);
    }

    public InstalledEntry withRequiredBy(List<String> parents) {
        return new InstalledEntry(projectId, slug, title, versionId, versionNumber, type, file, sha512, enabled, parents,
                installedAt, extra);
    }

    /** Adds parents (keeps order, no duplicates). */
    public InstalledEntry withRequiredByAdded(java.util.Collection<String> parents) {
        List<String> all = new ArrayList<>(requiredBy);
        for (String parent : parents) {
            if (!all.contains(parent) && !parent.equals(projectId)) {
                all.add(parent);
            }
        }
        return withRequiredBy(all);
    }

    /** Removes a parent. */
    public InstalledEntry withoutRequiredBy(String parent) {
        List<String> all = new ArrayList<>(requiredBy);
        all.remove(parent);
        return withRequiredBy(all);
    }

    /** Strips a trailing {@value #DISABLED_SUFFIX} and normalises separators. */
    static String canonicalFile(String file) {
        String f = file.replace('\\', '/');
        while (f.startsWith("./")) {
            f = f.substring(2);
        }
        return f.endsWith(DISABLED_SUFFIX) ? f.substring(0, f.length() - DISABLED_SUFFIX.length()) : f;
    }

    /** JSON in the shared format, unknown fields first so known fields always win. */
    JsonObject toJson() {
        JsonObject json = extra.deepCopy();
        json.addProperty("projectId", projectId);
        json.addProperty("slug", slug);
        json.addProperty("title", title);
        json.addProperty("versionId", versionId);
        json.addProperty("versionNumber", versionNumber);
        json.addProperty("type", type);
        json.addProperty("file", file);
        json.addProperty("sha512", sha512);
        json.addProperty("enabled", enabled);
        JsonArray parents = new JsonArray();
        requiredBy.forEach(parents::add);
        json.add("requiredBy", parents);
        json.addProperty("installedAt", installedAt);
        return json;
    }

    private static final List<String> KNOWN = List.of("projectId", "slug", "title", "versionId", "versionNumber",
            "type", "file", "sha512", "enabled", "requiredBy", "installedAt");

    /** Parses an entry; empty when {@code projectId} or {@code file} is missing. */
    static Optional<InstalledEntry> fromJson(JsonObject json) {
        String projectId = Json.nullableString(json, "projectId");
        String file = Json.nullableString(json, "file");
        if (projectId == null || file == null) {
            return Optional.empty();
        }
        JsonObject extra = new JsonObject();
        for (var e : json.entrySet()) {
            if (!KNOWN.contains(e.getKey())) {
                JsonElement value = e.getValue();
                extra.add(e.getKey(), value == null ? null : value.deepCopy());
            }
        }
        boolean enabled = Json.bool(json, "enabled", !file.endsWith(DISABLED_SUFFIX));
        return Optional.of(new InstalledEntry(projectId, Json.string(json, "slug"), Json.string(json, "title"),
                Json.string(json, "versionId"), Json.string(json, "versionNumber"), Json.string(json, "type"), file,
                Json.string(json, "sha512"), enabled, Json.strings(json, "requiredBy"), Json.string(json, "installedAt"),
                extra));
    }
}
