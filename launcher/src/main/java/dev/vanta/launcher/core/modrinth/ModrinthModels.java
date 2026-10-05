package dev.vanta.launcher.core.modrinth;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Gson models of the Modrinth API v2 responses the launcher reads. Only the fields the launcher uses are declared;
 * everything else in a response is ignored. Field names follow the API ({@code snake_case} through
 * {@link SerializedName}); shapes were taken from real responses of {@code api.modrinth.com/v2}, recorded as test
 * fixtures under {@code src/test/resources/fixtures/modrinth/}.
 */
public final class ModrinthModels {

    private ModrinthModels() {
    }

    /**
     * One file of a version ({@code GET /v2/project/{id}/version}, {@code GET /v2/version/{id}}).
     *
     * @param url      download URL on the Modrinth CDN
     * @param filename file name
     * @param primary  whether this is the version's primary file
     * @param size     size in bytes
     * @param hashes   {@code sha1} and {@code sha512} in hex
     */
    public record VersionFile(String url, String filename, boolean primary, long size, Map<String, String> hashes) {

        public VersionFile {
            hashes = hashes == null ? Map.of() : Map.copyOf(hashes);
        }

        /** @return the SHA-512 in lower-case hex, empty when the API did not list one */
        public String sha512() {
            final String h = hashes.get("sha512");
            return h == null ? "" : h.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A dependency of a version.
     *
     * @param versionId      the exact version required, when the author pinned one
     * @param projectId      the project, when known
     * @param fileName       file name of an external dependency (not resolvable through Modrinth)
     * @param dependencyType {@code required}, {@code optional}, {@code incompatible} or {@code embedded}
     */
    public record Dependency(@SerializedName("version_id") String versionId, @SerializedName("project_id") String projectId,
                             @SerializedName("file_name") String fileName, @SerializedName("dependency_type") String dependencyType) {

        /** @return whether the dependency must be installed */
        public boolean required() {
            return "required".equals(dependencyType);
        }
    }

    /**
     * A version of a project.
     *
     * @param id            version id
     * @param projectId     project id
     * @param name          display name
     * @param versionNumber version number
     * @param versionType   {@code release}, {@code beta} or {@code alpha}
     * @param datePublished ISO-8601 publication time
     * @param loaders       loaders the version supports
     * @param gameVersions  Minecraft versions the version supports
     * @param files         files
     * @param dependencies  dependencies
     * @param status        {@code listed}, {@code archived}, ... (may be absent)
     */
    public record Version(String id, @SerializedName("project_id") String projectId, String name,
                          @SerializedName("version_number") String versionNumber, @SerializedName("version_type") String versionType,
                          @SerializedName("date_published") String datePublished, List<String> loaders,
                          @SerializedName("game_versions") List<String> gameVersions, List<VersionFile> files,
                          List<Dependency> dependencies, String status) {

        /** Stable releases first, then betas, then alphas; within a type the newest first. */
        public static final Comparator<Version> PREFERRED_FIRST = Comparator
            .comparingInt((Version v) -> v.typeRank())
            .thenComparing(Version::published, Comparator.reverseOrder());

        public Version {
            name = name == null ? "" : name;
            versionNumber = versionNumber == null ? "" : versionNumber;
            versionType = versionType == null ? "release" : versionType;
            datePublished = datePublished == null ? "" : datePublished;
            loaders = loaders == null ? List.of() : List.copyOf(loaders);
            gameVersions = gameVersions == null ? List.of() : List.copyOf(gameVersions);
            files = files == null ? List.of() : List.copyOf(files);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        }

        /** @return the primary file, else the first file */
        public Optional<VersionFile> primaryFile() {
            return files.stream().filter(VersionFile::primary).findFirst().or(() -> files.stream().findFirst());
        }

        /**
         * @param type        content type (its loader)
         * @param gameVersion Minecraft version
         * @return whether this version installs into an instance of that Minecraft version for that loader, with a
         *     downloadable primary file that has a SHA-512
         */
        public boolean compatible(final ContentType type, final String gameVersion) {
            final boolean listed = status == null || status.isBlank() || "listed".equals(status);
            return listed && loaders.contains(type.loader()) && gameVersions.contains(gameVersion)
                && primaryFile().filter(f -> f.url() != null && !f.url().isBlank() && f.sha512().length() == 128).isPresent();
        }

        private int typeRank() {
            return switch (versionType) {
                case "release" -> 0;
                case "beta" -> 1;
                default -> 2;
            };
        }

        private Instant published() {
            try {
                return Instant.parse(datePublished);
            } catch (DateTimeParseException e) {
                return Instant.EPOCH;
            }
        }
    }

    /**
     * A project ({@code GET /v2/project/{id}}, {@code GET /v2/projects?ids=[...]}).
     *
     * @param id          project id
     * @param slug        slug
     * @param title       title
     * @param description short description
     * @param projectType {@code mod}, {@code shader}, {@code resourcepack}, ...
     */
    public record Project(String id, String slug, String title, String description, @SerializedName("project_type") String projectType) {
    }

    /**
     * A search result ({@code GET /v2/search}).
     *
     * @param projectId     project id
     * @param slug          slug
     * @param title         title
     * @param description   short description
     * @param author        author name
     * @param projectType   project type
     * @param downloads     total downloads
     * @param categories    categories (loaders appear here too)
     * @param latestVersion id of the latest version (any game version)
     */
    public record SearchHit(@SerializedName("project_id") String projectId, String slug, String title, String description, String author,
                            @SerializedName("project_type") String projectType, long downloads, List<String> categories,
                            @SerializedName("latest_version") String latestVersion) {

        public SearchHit {
            title = title == null ? slug : title;
            description = description == null ? "" : description;
            author = author == null ? "" : author;
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }

    /**
     * One page of search results.
     *
     * @param hits      results
     * @param offset    offset of the first result
     * @param limit     page size
     * @param totalHits number of matching projects
     */
    public record SearchPage(List<SearchHit> hits, int offset, int limit, @SerializedName("total_hits") int totalHits) {

        public SearchPage {
            hits = hits == null ? List.of() : List.copyOf(hits);
        }

        /** @return whether more results exist after this page */
        public boolean hasMore() {
            return offset + hits.size() < totalHits;
        }
    }
}
