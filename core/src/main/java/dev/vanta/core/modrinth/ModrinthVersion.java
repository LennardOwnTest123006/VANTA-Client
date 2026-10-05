package dev.vanta.core.modrinth;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The fields of a Modrinth version ({@code GET /project/{id}/version}, {@code GET /version/{id}}) VANTA uses.
 *
 * @param id            version id
 * @param projectId     project id
 * @param name          display name
 * @param versionNumber version number, e.g. {@code mc1.21.11-0.8.14-fabric}
 * @param versionType   {@code release}, {@code beta} or {@code alpha}
 * @param published     publication time
 * @param downloads     downloads of this version
 * @param gameVersions  supported Minecraft versions
 * @param loaders       supported loaders
 * @param files         files (the primary one is installed)
 * @param dependencies  declared dependencies
 */
public record ModrinthVersion(String id, String projectId, String name, String versionNumber, String versionType,
                              Instant published, long downloads, List<String> gameVersions, List<String> loaders,
                              List<ModrinthFile> files, List<ModrinthDependency> dependencies) {
    public ModrinthVersion {
        gameVersions = List.copyOf(gameVersions);
        loaders = List.copyOf(loaders);
        files = List.copyOf(files);
        dependencies = List.copyOf(dependencies);
        published = published == null ? Instant.EPOCH : published;
    }

    /** Release channel, in order of preference. */
    public enum Channel {
        RELEASE, BETA, ALPHA, UNKNOWN;

        static Channel of(String versionType) {
            if (versionType == null) {
                return UNKNOWN;
            }
            return switch (versionType.trim().toLowerCase(Locale.ROOT)) {
                case "release" -> RELEASE;
                case "beta" -> BETA;
                case "alpha" -> ALPHA;
                default -> UNKNOWN;
            };
        }
    }

    /** The release channel. */
    public Channel channel() {
        return Channel.of(versionType);
    }

    /** The primary file, or the first file when none is marked primary. */
    public Optional<ModrinthFile> primaryFile() {
        for (ModrinthFile file : files) {
            if (file.primary()) {
                return Optional.of(file);
            }
        }
        return files.isEmpty() ? Optional.empty() : Optional.of(files.get(0));
    }

    /**
     * True when this version lists {@code gameVersion} and at least one of {@code acceptedLoaders} (an empty
     * collection accepts any loader).
     */
    public boolean supports(String gameVersion, Collection<String> acceptedLoaders) {
        if (!gameVersions.contains(gameVersion)) {
            return false;
        }
        if (acceptedLoaders == null || acceptedLoaders.isEmpty()) {
            return true;
        }
        for (String loader : loaders) {
            if (acceptedLoaders.contains(loader.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Dependencies of one type. */
    public List<ModrinthDependency> dependencies(ModrinthDependency.Type type) {
        List<ModrinthDependency> out = new ArrayList<>();
        for (ModrinthDependency d : dependencies) {
            if (d.type() == type) {
                out.add(d);
            }
        }
        return out;
    }

    /** Parses a version; fails when the id or project id is missing. */
    static ModrinthVersion fromJson(JsonObject json) throws ModrinthException {
        String id = Json.nullableString(json, "id");
        String projectId = Json.nullableString(json, "project_id");
        if (id == null || projectId == null) {
            throw new ModrinthException(ModrinthException.Kind.INVALID_RESPONSE, "version without id or project_id");
        }
        List<ModrinthFile> files = new ArrayList<>();
        for (JsonObject file : Json.objects(json, "files")) {
            ModrinthFile.fromJson(file).ifPresent(files::add);
        }
        List<ModrinthDependency> deps = new ArrayList<>();
        for (JsonObject dep : Json.objects(json, "dependencies")) {
            deps.add(ModrinthDependency.fromJson(dep));
        }
        String number = Json.string(json, "version_number", id);
        return new ModrinthVersion(id, projectId, Json.string(json, "name", number), number,
                Json.string(json, "version_type", "release"), Json.instant(Json.string(json, "date_published")),
                Json.longValue(json, "downloads", 0), Json.strings(json, "game_versions"), Json.strings(json, "loaders"),
                files, deps);
    }
}
