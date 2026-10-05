package dev.vanta.core.modrinth;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;

/**
 * One result of {@code GET /search}.
 *
 * @param projectId   Modrinth project id
 * @param slug        URL slug
 * @param title       display name
 * @param author      author or team name (may be empty)
 * @param description one-line summary
 * @param projectType {@code mod}, {@code shader}, {@code resourcepack}, …
 * @param downloads   total downloads
 * @param follows     followers
 * @param categories  categories (loaders appear here too, e.g. {@code fabric}, {@code iris})
 * @param iconUrl     icon URL (may be empty; VANTA draws a letter avatar instead)
 * @param color       accent colour Modrinth derived from the icon (RGB), or -1
 */
public record ModrinthSearchHit(String projectId, String slug, String title, String author, String description,
                                String projectType, long downloads, long follows, List<String> categories,
                                String iconUrl, int color) {
    public ModrinthSearchHit {
        categories = List.copyOf(categories);
    }

    /** The project type, when VANTA supports it. */
    public Optional<ModrinthProjectType> type() {
        return ModrinthProjectType.fromApi(projectType);
    }

    /** Parses one hit; returns empty when the project id is missing. */
    static Optional<ModrinthSearchHit> fromJson(JsonObject json) {
        String id = Json.nullableString(json, "project_id");
        if (id == null) {
            return Optional.empty();
        }
        String slug = Json.string(json, "slug", id);
        String title = Json.string(json, "title", slug);
        return Optional.of(new ModrinthSearchHit(id, slug, title, Json.string(json, "author"),
                Json.string(json, "description"), Json.string(json, "project_type"), Json.longValue(json, "downloads", 0),
                Json.longValue(json, "follows", 0), Json.strings(json, "categories"), Json.string(json, "icon_url"),
                Json.intValue(json, "color", -1)));
    }
}
