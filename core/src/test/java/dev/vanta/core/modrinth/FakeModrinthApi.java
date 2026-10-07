package dev.vanta.core.modrinth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * In-memory {@link ModrinthApi} for screen tests and previews: a handful of projects with one version each, file
 * contents generated from the project slug and real SHA-512 values for them. Searches filter by type and title.
 * Nothing touches the network.
 */
public final class FakeModrinthApi implements ModrinthApi {
    private final Map<String, ModrinthProject> projects = new LinkedHashMap<>();
    private final Map<String, ModrinthSearchHit> hits = new LinkedHashMap<>();
    private final Map<String, List<ModrinthVersion>> versions = new LinkedHashMap<>();
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private ModrinthException failSearch;
    private ModrinthException failProjects;

    /** API with the six Performance pack members, two other mods, two shader packs and a resource pack. */
    public static FakeModrinthApi standard() {
        FakeModrinthApi api = new FakeModrinthApi();
        api.add("AANobbMI", "sodium", "Sodium", "mod", "jellysquid3",
                "A high-performance rendering engine replacement for Minecraft.", 236_787_190L, 8703084);
        api.add("LITHIUM1", "lithium", "Lithium", "mod", "jellysquid3",
                "No-compromises game logic optimization mod.", 81_000_000L, 4247520);
        api.add("FERRITE1", "ferrite-core", "FerriteCore", "mod", "malte0811",
                "Memory usage optimizations.", 74_000_000L, -1);
        api.add("IMMFAST1", "immediatelyfast", "ImmediatelyFast", "mod", "RaphiMC",
                "Speed up immediate mode rendering in Minecraft.", 52_000_000L, -1);
        api.add("ENTCULL1", "entityculling", "EntityCulling", "mod", "tr7zw",
                "Using async path-tracing to hide Block-/Entities that are not visible.", 61_000_000L, -1);
        api.add("YL57xq9U", "iris", "Iris Shaders", "mod", "coderbot",
                "A modern shader pack loader for Minecraft intended to be compatible with existing OptiFine shader packs.",
                90_000_000L, 5592405, new ModrinthDependency(null, "AANobbMI", null, ModrinthDependency.Type.REQUIRED));
        api.add("MODMENU1", "modmenu", "Mod Menu", "mod", "Prospector",
                "Adds a mod menu to view the list of mods you have installed.", 98_000_000L, -1);
        api.add("HVnmMxH1", "complementary-reimagined", "Complementary Shaders - Reimagined", "shader", "EminGT",
                "Preserving the elements of Minecraft with exceptional quality, detail, and performance.",
                68_409_474L, 14197949);
        api.add("BSLSHAD1", "bsl-shaders", "BSL Shaders", "shader", "capt-tatsu",
                "A bright, colorful and soft shader pack.", 30_000_000L, -1);
        api.add("FRESHAN1", "fresh-animations", "Fresh Animations", "resourcepack", "FreshLX",
                "A resource pack that adds new animations to mobs.", 25_000_000L, -1);
        return api;
    }

    /** Adds a project with one release for 1.21.11. */
    public FakeModrinthApi add(String id, String slug, String title, String type, String author, String description,
                               long downloads, int color, ModrinthDependency... dependencies) {
        ModrinthProjectType projectType = ModrinthProjectType.fromApi(type).orElse(ModrinthProjectType.MOD);
        String ext = projectType == ModrinthProjectType.MOD ? ".jar" : ".zip";
        String filename = slug + "-1.0.0" + ext;
        byte[] content = ("fake " + slug + " content").getBytes(StandardCharsets.UTF_8);
        String url = "https://cdn.modrinth.com/data/" + id + "/versions/V" + id + "/" + filename;
        files.put(url, content);
        ModrinthFile file = new ModrinthFile(url, filename, true, content.length, Sha512.hex(content), "");
        ModrinthVersion version = new ModrinthVersion("V" + id, id, title + " 1.0.0", "1.0.0", "release",
                Instant.parse("2026-09-01T00:00:00Z"), downloads / 10, List.of(ModrinthConstants.GAME_VERSION),
                projectType.loaders(), List.of(file), List.of(dependencies));
        projects.put(id, new ModrinthProject(id, slug, title, description, type, downloads, projectType.loaders(),
                List.of(ModrinthConstants.GAME_VERSION), ""));
        hits.put(id, new ModrinthSearchHit(id, slug, title, author, description, type, downloads, downloads / 5000,
                List.of(), "", color));
        versions.put(id, List.of(version));
        return this;
    }

    /**
     * Adds an older release of an existing mod for another Minecraft version, with its own file name and bytes
     * (see {@link #fileContent}), so hash lookups can identify a leftover jar that does not fit this game.
     */
    public FakeModrinthApi addRelease(String projectId, String versionId, String versionNumber, String gameVersion,
                                      String filename) {
        ModrinthProject project = projects.get(projectId);
        if (project == null) {
            throw new IllegalArgumentException("unknown project " + projectId);
        }
        byte[] content = ("fake " + project.slug() + " " + versionNumber + " content").getBytes(StandardCharsets.UTF_8);
        String url = "https://cdn.modrinth.com/data/" + projectId + "/versions/" + versionId + "/" + filename;
        files.put(url, content);
        ModrinthFile file = new ModrinthFile(url, filename, true, content.length, Sha512.hex(content), "");
        ModrinthVersion version = new ModrinthVersion(versionId, projectId, project.title() + " " + versionNumber,
                versionNumber, "release", Instant.parse("2025-01-01T00:00:00Z"), 1, List.of(gameVersion),
                ModrinthProjectType.MOD.loaders(), List.of(file), List.of());
        List<ModrinthVersion> all = new ArrayList<>(versions.getOrDefault(projectId, List.of()));
        all.add(version);
        versions.put(projectId, List.copyOf(all));
        return this;
    }

    /** The bytes of a version's primary file. */
    public byte[] fileContent(String versionId) {
        try {
            return files.get(version(versionId).primaryFile().orElseThrow().url());
        } catch (ModrinthException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Makes every project lookup fail with the given error ({@code null} to stop failing), like an offline game. */
    public FakeModrinthApi failProjects(ModrinthException error) {
        this.failProjects = error;
        return this;
    }

    /** Forgets a project page but keeps its versions and files, like a project that was removed from Modrinth. */
    public FakeModrinthApi removeProject(String id) {
        projects.remove(id);
        hits.remove(id);
        return this;
    }

    /** Makes every search fail with the given error ({@code null} to stop failing). */
    public FakeModrinthApi failSearch(ModrinthException error) {
        this.failSearch = error;
        return this;
    }

    /** Calls made, e.g. {@code search:mod:sodium}, {@code download:sodium-1.0.0.jar}. */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public ModrinthSearchResult search(SearchRequest request) throws ModrinthException {
        calls.add("search:" + request.type().apiName() + ":" + request.query());
        if (failSearch != null) {
            throw failSearch;
        }
        String q = request.query().toLowerCase(Locale.ROOT);
        List<ModrinthSearchHit> out = new ArrayList<>();
        for (ModrinthSearchHit hit : hits.values()) {
            if (hit.projectType().equals(request.type().apiName())
                    && (q.isEmpty() || hit.title().toLowerCase(Locale.ROOT).contains(q))) {
                out.add(hit);
            }
        }
        out.sort((a, b) -> Long.compare(b.downloads(), a.downloads()));
        int from = Math.min(out.size(), request.offset());
        int to = Math.min(out.size(), request.offset() + request.limit());
        return new ModrinthSearchResult(out.subList(from, to), request.offset(), request.limit(), out.size());
    }

    private ModrinthProject find(String idOrSlug) throws ModrinthException {
        for (ModrinthProject p : projects.values()) {
            if (p.id().equals(idOrSlug) || p.slug().equals(idOrSlug)) {
                return p;
            }
        }
        throw new ModrinthException(ModrinthException.Kind.NOT_FOUND, idOrSlug + ": not found");
    }

    @Override
    public ModrinthProject project(String idOrSlug) throws ModrinthException {
        calls.add("project:" + idOrSlug);
        if (failProjects != null) {
            throw failProjects;
        }
        return find(idOrSlug);
    }

    @Override
    public List<ModrinthVersion> versions(String idOrSlug, List<String> loaders, List<String> gameVersions)
            throws ModrinthException {
        calls.add("versions:" + idOrSlug);
        return versions.getOrDefault(find(idOrSlug).id(), List.of());
    }

    @Override
    public ModrinthVersion version(String versionId) throws ModrinthException {
        for (List<ModrinthVersion> list : versions.values()) {
            for (ModrinthVersion v : list) {
                if (v.id().equals(versionId)) {
                    return v;
                }
            }
        }
        throw new ModrinthException(ModrinthException.Kind.NOT_FOUND, versionId);
    }

    /** Like Modrinth's {@code POST /version_files}: the version whose primary file has one of the hashes, by hash. */
    @Override
    public Map<String, ModrinthVersion> versionsByHash(Collection<String> sha512Hashes) {
        calls.add("version_files:" + sha512Hashes.size());
        Map<String, ModrinthVersion> out = new LinkedHashMap<>();
        for (List<ModrinthVersion> list : versions.values()) {
            for (ModrinthVersion v : list) {
                v.primaryFile().filter(f -> sha512Hashes.contains(f.sha512())).ifPresent(f -> out.put(f.sha512(), v));
            }
        }
        return out;
    }

    @Override
    public void download(ModrinthFile file, Path target, LongConsumer bytesRead) throws ModrinthException {
        calls.add("download:" + file.filename());
        byte[] content = files.get(file.url());
        if (content == null) {
            throw new ModrinthException(ModrinthException.Kind.NOT_FOUND, file.url());
        }
        if (!Sha512.hex(content).equals(file.sha512())) {
            throw new ModrinthException(ModrinthException.Kind.HASH_MISMATCH, file.filename());
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new ModrinthException(ModrinthException.Kind.LOCAL_IO, e.getMessage(), e);
        }
        if (bytesRead != null) {
            bytesRead.accept(content.length);
        }
    }
}
