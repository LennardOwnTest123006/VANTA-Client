package dev.vanta.core.modrinth;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;

/**
 * The fields of {@code GET /project/{id|slug}} VANTA uses.
 *
 * @param id           Modrinth project id
 * @param slug         URL slug
 * @param title        display name
 * @param description  one-line summary
 * @param projectType  {@code mod}, {@code shader}, {@code resourcepack}, …
 * @param downloads    total downloads
 * @param loaders      loaders of any version
 * @param gameVersions game versions of any version
 * @param iconUrl      icon URL (may be empty)
 */
public record ModrinthProject(String id, String slug, String title, String description, String projectType,
                              long downloads, List<String> loaders, List<String> gameVersions, String iconUrl) {
    public ModrinthProject {
        loaders = List.copyOf(loaders);
        gameVersions = List.copyOf(gameVersions);
    }

    /** The project type, when VANTA supports it. */
    public Optional<ModrinthProjectType> type() {
        return ModrinthProjectType.fromApi(projectType);
    }

    /** Parses the project; fails when the id is missing. */
    static ModrinthProject fromJson(JsonObject json) throws ModrinthException {
        String id = Json.nullableString(json, "id");
        if (id == null) {
            throw new ModrinthException(ModrinthException.Kind.INVALID_RESPONSE, "project without id");
        }
        String slug = Json.string(json, "slug", id);
        return new ModrinthProject(id, slug, Json.string(json, "title", slug), Json.string(json, "description"),
                Json.string(json, "project_type"), Json.longValue(json, "downloads", 0), Json.strings(json, "loaders"),
                Json.strings(json, "game_versions"), Json.string(json, "icon_url"));
    }
}
