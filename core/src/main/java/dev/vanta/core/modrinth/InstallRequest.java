package dev.vanta.core.modrinth;

import java.util.Objects;

/**
 * Something the player asked to install: a project by id or slug.
 *
 * @param idOrSlug project id or slug
 * @param label    name shown in messages until the project is resolved (title or slug)
 */
public record InstallRequest(String idOrSlug, String label) {
    public InstallRequest {
        Objects.requireNonNull(idOrSlug, "idOrSlug");
        label = label == null || label.isBlank() ? idOrSlug : label;
    }

    /** Request by slug or id, labelled with itself. */
    public static InstallRequest of(String idOrSlug) {
        return new InstallRequest(idOrSlug, idOrSlug);
    }

    /** Request for a search hit. */
    public static InstallRequest of(ModrinthSearchHit hit) {
        return new InstallRequest(hit.projectId(), hit.title());
    }
}
