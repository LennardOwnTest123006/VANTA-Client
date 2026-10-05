package dev.vanta.core.modrinth;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * A page of search results.
 *
 * @param hits      the projects on this page
 * @param offset    offset of the first hit
 * @param limit     page size requested
 * @param totalHits number of matching projects
 */
public record ModrinthSearchResult(List<ModrinthSearchHit> hits, int offset, int limit, int totalHits) {
    public ModrinthSearchResult {
        hits = List.copyOf(hits);
    }

    /** An empty page. */
    public static ModrinthSearchResult empty() {
        return new ModrinthSearchResult(List.of(), 0, 0, 0);
    }

    /** True when more results exist after this page. */
    public boolean hasMore() {
        return offset + hits.size() < totalHits;
    }

    /** This page followed by {@code next} (for "Load more"). */
    public ModrinthSearchResult append(ModrinthSearchResult next) {
        List<ModrinthSearchHit> all = new ArrayList<>(hits);
        for (ModrinthSearchHit hit : next.hits()) {
            if (all.stream().noneMatch(h -> h.projectId().equals(hit.projectId()))) {
                all.add(hit);
            }
        }
        return new ModrinthSearchResult(all, offset, limit, Math.max(totalHits, next.totalHits()));
    }

    static ModrinthSearchResult fromJson(JsonObject json) {
        List<ModrinthSearchHit> hits = new ArrayList<>();
        for (JsonObject hit : Json.objects(json, "hits")) {
            ModrinthSearchHit.fromJson(hit).ifPresent(hits::add);
        }
        return new ModrinthSearchResult(hits, Json.intValue(json, "offset", 0), Json.intValue(json, "limit", hits.size()),
                Json.intValue(json, "total_hits", hits.size()));
    }
}
