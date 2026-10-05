package dev.vanta.core.modrinth;

import com.google.gson.JsonArray;
import java.util.Locale;
import java.util.Objects;

/**
 * Parameters of {@code GET /search}. The facets always restrict results to the project type and to Minecraft
 * {@value ModrinthConstants#GAME_VERSION}; mods are further restricted to the {@code fabric} category and shaders to
 * the {@code iris} category.
 *
 * @param query       free text; empty lists the most downloaded projects
 * @param type        project type
 * @param offset      index of the first result
 * @param limit       page size (1–100)
 * @param sort        sort index
 * @param gameVersion Minecraft version facet
 */
public record SearchRequest(String query, ModrinthProjectType type, int offset, int limit, Sort sort,
                            String gameVersion) {
    /** Default page size. */
    public static final int PAGE_SIZE = 20;

    /** Modrinth's {@code index} parameter. */
    public enum Sort {
        RELEVANCE, DOWNLOADS, FOLLOWS, NEWEST, UPDATED;

        /** API value. */
        public String apiName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public SearchRequest {
        query = query == null ? "" : query.trim();
        Objects.requireNonNull(type, "type");
        offset = Math.max(0, offset);
        limit = Math.max(1, Math.min(100, limit));
        Objects.requireNonNull(sort, "sort");
        gameVersion = gameVersion == null || gameVersion.isBlank() ? ModrinthConstants.GAME_VERSION : gameVersion;
    }

    /**
     * First page for {@code query}: by relevance when there is text, by downloads when it is empty (a browse list of
     * the most popular projects).
     */
    public static SearchRequest of(String query, ModrinthProjectType type) {
        String q = query == null ? "" : query.trim();
        return new SearchRequest(q, type, 0, PAGE_SIZE, q.isEmpty() ? Sort.DOWNLOADS : Sort.RELEVANCE,
                ModrinthConstants.GAME_VERSION);
    }

    /** The next page. */
    public SearchRequest nextPage() {
        return new SearchRequest(query, type, offset + limit, limit, sort, gameVersion);
    }

    /** Facets as the JSON array of OR-groups the API expects, e.g. {@code [["project_type:mod"],["versions:1.21.11"]]}. */
    public String facetsJson() {
        JsonArray facets = new JsonArray();
        facets.add(group("project_type:" + type.apiName()));
        facets.add(group("versions:" + gameVersion));
        type.categoryFacet().ifPresent(category -> facets.add(group("categories:" + category)));
        return facets.toString();
    }

    private static JsonArray group(String facet) {
        JsonArray group = new JsonArray();
        group.add(facet);
        return group;
    }

    /** Cache key of this request (query, type, sort and page). */
    public String cacheKey() {
        return type.apiName() + '|' + sort.apiName() + '|' + offset + '|' + limit + '|' + query.toLowerCase(Locale.ROOT);
    }
}
