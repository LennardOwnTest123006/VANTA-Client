package dev.vanta.launcher.testutil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.ServiceEndpoints;
import dev.vanta.launcher.core.auth.MicrosoftAuthService;
import dev.vanta.launcher.core.install.FabricApiService;
import dev.vanta.launcher.core.install.FabricService;
import dev.vanta.launcher.core.install.Installer;
import dev.vanta.launcher.core.install.MojangService;
import dev.vanta.launcher.core.install.SharedFileSource;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.modrinth.ModrinthApi;
import dev.vanta.launcher.core.modrinth.ModrinthService;
import dev.vanta.launcher.core.modrinth.PerformancePack;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.HashAlgorithm;
import dev.vanta.launcher.core.net.JdkHttpTransport;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.core.util.Sleeper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A complete fake download world built from the fixtures and served by a {@link FakeHttpServer}: Mojang manifest,
 * version JSON, client jar, libraries, asset index and objects, Fabric meta and Maven (with checksum sidecars),
 * Fabric API, the VANTA release manifest + client jar and the Modrinth API for the performance pack (recorded version
 * lists under {@code fixtures/modrinth/}, each file replaced by a small jar with a {@code fabric.mod.json}). All URLs
 * and digests in the served JSON are rewritten to point at the fake server and to match the served bytes, so every
 * download verifies.
 */
public final class FakeWorld implements AutoCloseable {

    /** Fabric loader jar coordinate path. */
    public static final String FABRIC_LOADER_PATH = "net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar";

    private final FakeHttpServer server;
    private final Map<String, byte[]> blobs = new LinkedHashMap<>();
    private final JsonObject versionJson;
    private final JsonObject assetIndex;
    private final JsonObject fabricProfile;
    private final JsonObject clientManifest;
    private final Map<String, JsonArray> modrinthVersions = new LinkedHashMap<>();
    private long totalBytes;

    /**
     * Builds the world with synthetic library/asset bytes and a real runnable fake game as the Fabric loader jar.
     *
     * @throws IOException on failure
     */
    public FakeWorld() throws IOException {
        this(true);
    }

    /**
     * @param runnableGame when true the Fabric loader jar is a compiled {@link FakeGameJar}
     * @throws IOException on failure
     */
    public FakeWorld(final boolean runnableGame) throws IOException {
        server = new FakeHttpServer();
        final URI base = server.base();

        // ---- Mojang version JSON: rewrite every download to the fake server ----
        versionJson = Fixtures.json("1.21.11.json").getAsJsonObject();
        rewriteDownload(versionJson.getAsJsonObject("downloads").getAsJsonObject("client"), "mojang/objects/client.jar", "minecraft client jar", 60_000);
        versionJson.getAsJsonObject("downloads").remove("server");
        versionJson.getAsJsonObject("downloads").remove("server_mappings");
        versionJson.getAsJsonObject("downloads").remove("client_mappings");
        for (JsonElement libEl : versionJson.getAsJsonArray("libraries")) {
            final JsonObject lib = libEl.getAsJsonObject();
            final JsonObject artifact = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
            final String path = artifact.get("path").getAsString();
            rewriteDownload(artifact, "mojang/libraries/" + path, "library " + lib.get("name").getAsString(), 2_000);
        }
        final JsonObject logFile = versionJson.getAsJsonObject("logging").getAsJsonObject("client").getAsJsonObject("file");
        rewriteDownload(logFile, "mojang/objects/client-1.12.xml", "<Configuration status=\"WARN\"/>", 0);

        // ---- Asset index + objects ----
        assetIndex = Fixtures.json("asset-index-26.json").getAsJsonObject();
        final JsonObject objects = assetIndex.getAsJsonObject("objects");
        long assetTotal = 0;
        for (Map.Entry<String, JsonElement> e : objects.entrySet()) {
            final byte[] content = ("asset " + e.getValue().getAsJsonObject().get("hash").getAsString()).getBytes(StandardCharsets.UTF_8);
            final String hash = Checksums.hex(content, HashAlgorithm.SHA1);
            e.getValue().getAsJsonObject().addProperty("hash", hash);
            e.getValue().getAsJsonObject().addProperty("size", content.length);
            serve("assets/" + hash.substring(0, 2) + "/" + hash, content);
            assetTotal += content.length;
        }
        final byte[] indexBytes = Json.GSON.toJson(assetIndex).getBytes(StandardCharsets.UTF_8);
        final JsonObject assetIndexRef = versionJson.getAsJsonObject("assetIndex");
        assetIndexRef.addProperty("url", base.resolve("mojang/indexes/26.json").toString());
        assetIndexRef.addProperty("sha1", Checksums.hex(indexBytes, HashAlgorithm.SHA1));
        assetIndexRef.addProperty("size", indexBytes.length);
        assetIndexRef.addProperty("totalSize", assetTotal);
        serve("mojang/indexes/26.json", indexBytes);

        // ---- Manifest ----
        final byte[] versionBytes = Json.GSON.toJson(versionJson).getBytes(StandardCharsets.UTF_8);
        serve("mojang/1.21.11.json", versionBytes);
        final JsonObject manifest = Fixtures.json("version_manifest_v2.json").getAsJsonObject();
        for (JsonElement v : manifest.getAsJsonArray("versions")) {
            final JsonObject entry = v.getAsJsonObject();
            if ("1.21.11".equals(entry.get("id").getAsString())) {
                entry.addProperty("url", base.resolve("mojang/1.21.11.json").toString());
                entry.addProperty("sha1", Checksums.hex(versionBytes, HashAlgorithm.SHA1));
            }
        }
        server.addJson("mojang/version_manifest_v2.json", Json.GSON.toJson(manifest));

        // ---- Fabric meta + maven ----
        fabricProfile = Fixtures.json("fabric-profile-0.19.5.json").getAsJsonObject();
        final String mavenBase = base.resolve("fabric/maven/").toString();
        for (JsonElement libEl : fabricProfile.getAsJsonArray("libraries")) {
            final JsonObject lib = libEl.getAsJsonObject();
            final String name = lib.get("name").getAsString();
            final String[] parts = name.split(":");
            final String path = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
            lib.addProperty("url", mavenBase);
            final byte[] content;
            if (runnableGame && path.equals(FABRIC_LOADER_PATH)) {
                final Path tmp = Files.createTempFile("fake-knot", ".jar");
                FakeGameJar.build(tmp);
                content = Files.readAllBytes(tmp);
                Files.deleteIfExists(tmp);
            } else {
                content = synthetic("fabric library " + name, 1_500);
            }
            serve("fabric/maven/" + path, content);
            if (lib.has("sha1")) {
                lib.addProperty("sha1", Checksums.hex(content, HashAlgorithm.SHA1));
                lib.addProperty("sha256", Checksums.hex(content, HashAlgorithm.SHA256));
                lib.addProperty("size", content.length);
            } else if (name.startsWith("net.fabricmc:intermediary")) {
                // only a .sha1 sidecar exists for this one (exercises the fallback)
                server.addText("fabric/maven/" + path + ".sha1", Checksums.hex(content, HashAlgorithm.SHA1));
            } else {
                server.addText("fabric/maven/" + path + ".sha256", Checksums.hex(content, HashAlgorithm.SHA256) + "  " + parts[1] + ".jar");
            }
        }
        server.addJson("fabric/meta/v2/versions/loader/1.21.11", Fixtures.read("fabric-loaders-1.21.11.json"));
        server.addJson("fabric/meta/v2/versions/loader/1.21.11/0.19.5/profile/json", Json.GSON.toJson(fabricProfile));

        // ---- Fabric API ----
        final String apiPath = FabricApiService.coordinate(LauncherVersion.FABRIC_API).path();
        final byte[] apiBytes = synthetic("fabric api", 3_000);
        serve("fabric/maven/" + apiPath, apiBytes);
        server.addText("fabric/maven/" + apiPath + ".sha256", Checksums.hex(apiBytes, HashAlgorithm.SHA256));

        // ---- VANTA release ----
        clientManifest = Fixtures.json("release/client-latest.json").getAsJsonObject();
        final byte[] vantaJar = synthetic("vanta client jar", 4_000);
        serve("releases/vanta-client-1.0.0.jar", vantaJar);
        final JsonObject file = clientManifest.getAsJsonArray("files").get(0).getAsJsonObject();
        file.addProperty("downloadUrl", base.resolve("releases/vanta-client-1.0.0.jar").toString());
        file.addProperty("size", vantaJar.length);
        file.addProperty("sha256", Checksums.hex(vantaJar, HashAlgorithm.SHA256));
        server.addJson("releases/client-latest.json", Json.GSON.toJson(clientManifest));
        server.addJson("releases/client-unpublished.json", Fixtures.read("release/client-unpublished.json"));

        // ---- Modrinth: the performance pack ----
        final JsonArray projects = Fixtures.json("modrinth/projects.json").getAsJsonArray();
        for (JsonElement p : projects) {
            final JsonObject project = p.getAsJsonObject();
            final String slug = project.get("slug").getAsString();
            final String id = project.get("id").getAsString();
            final JsonArray versions = Fixtures.json("modrinth/versions-" + slug + ".json").getAsJsonArray();
            for (JsonElement v : versions) {
                final JsonObject version = v.getAsJsonObject();
                final JsonObject modFile = version.getAsJsonArray("files").get(0).getAsJsonObject();
                final String fileName = modFile.get("filename").getAsString();
                final byte[] jar = modJar(fabricModId(slug), version.get("version_number").getAsString());
                serve("modrinth/cdn/" + fileName, jar);
                modFile.addProperty("url", server.url("modrinth/cdn/" + fileName).toString());
                modFile.addProperty("size", jar.length);
                modFile.getAsJsonObject("hashes").addProperty("sha1", Checksums.hex(jar, HashAlgorithm.SHA1));
                modFile.getAsJsonObject("hashes").addProperty("sha512", Checksums.hex(jar, HashAlgorithm.SHA512));
                server.addJson("modrinth/v2/version/" + version.get("id").getAsString(), Json.GSON.toJson(version));
            }
            modrinthVersions.put(slug, versions);
            server.addJson("modrinth/v2/project/" + slug + "/version", Json.GSON.toJson(versions));
            server.addJson("modrinth/v2/project/" + id + "/version", Json.GSON.toJson(versions));
            server.addJson("modrinth/v2/project/" + slug, Json.GSON.toJson(project));
            server.addJson("modrinth/v2/project/" + id, Json.GSON.toJson(project));
        }
        server.addJson("modrinth/v2/projects", Json.GSON.toJson(projects));
        server.addJson("modrinth/v2/search", Fixtures.read("modrinth/search-mods-sodium.json"));
    }

    /**
     * @param slug Modrinth slug of a pack project
     * @return the Fabric mod id its jar declares
     */
    public static String fabricModId(final String slug) {
        return "ferrite-core".equals(slug) ? "ferritecore" : slug;
    }

    /**
     * A small mod jar: {@code fabric.mod.json} with id and version (fixed timestamps, so the bytes are stable).
     *
     * @param modId   Fabric mod id
     * @param version version
     * @return jar bytes
     * @throws IOException on failure
     */
    public static byte[] modJar(final String modId, final String version) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            final ZipEntry entry = new ZipEntry("fabric.mod.json");
            entry.setTime(0L);
            zip.putNextEntry(entry);
            zip.write(("{\"schemaVersion\":1,\"id\":\"" + modId + "\",\"version\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    /**
     * Serves other bytes for a Modrinth file of a pack project, with the SHA-512, SHA-1 and size Modrinth then reports
     * (the download verifies, so what is checked is the content).
     *
     * @param slug     pack project slug
     * @param fileName file name of one of its versions
     * @param jar      new content
     */
    public void replaceModrinthFile(final String slug, final String fileName, final byte[] jar) {
        final JsonArray versions = modrinthVersions.get(slug);
        String projectId = slug;
        for (JsonElement v : versions) {
            final JsonObject version = v.getAsJsonObject();
            projectId = version.get("project_id").getAsString();
            final JsonObject file = version.getAsJsonArray("files").get(0).getAsJsonObject();
            if (file.get("filename").getAsString().equals(fileName)) {
                serve("modrinth/cdn/" + fileName, jar);
                file.addProperty("size", jar.length);
                file.getAsJsonObject("hashes").addProperty("sha1", Checksums.hex(jar, HashAlgorithm.SHA1));
                file.getAsJsonObject("hashes").addProperty("sha512", Checksums.hex(jar, HashAlgorithm.SHA512));
                server.addJson("modrinth/v2/version/" + version.get("id").getAsString(), Json.GSON.toJson(version));
            }
        }
        server.addJson("modrinth/v2/project/" + slug + "/version", Json.GSON.toJson(versions));
        server.addJson("modrinth/v2/project/" + projectId + "/version", Json.GSON.toJson(versions));
    }

    /** @return the underlying server */
    public FakeHttpServer server() {
        return server;
    }

    /** @return the rewritten version JSON */
    public JsonObject versionJson() {
        return versionJson;
    }

    /** @return the rewritten asset index */
    public JsonObject assetIndex() {
        return assetIndex;
    }

    /** @return the rewritten Fabric profile */
    public JsonObject fabricProfile() {
        return fabricProfile;
    }

    /** @return the rewritten client manifest */
    public JsonObject clientManifest() {
        return clientManifest;
    }

    /** @return bytes served for a path */
    public byte[] blob(final String path) {
        return blobs.get(path);
    }

    /** @return total bytes of all served download blobs */
    public long totalBytes() {
        return totalBytes;
    }

    /**
     * @param slug pack project slug
     * @return its rewritten version list (newest first, as Modrinth returns it)
     */
    public JsonArray modrinthVersions(final String slug) {
        return modrinthVersions.get(slug);
    }

    /** @return the fake Modrinth API base */
    public URI modrinthBase() {
        return server.url("modrinth/v2/");
    }

    /** @return Mojang manifest URL */
    public URI manifestUrl() {
        return server.url("mojang/version_manifest_v2.json");
    }

    /** @return asset objects base */
    public URI resourcesBase() {
        return server.url("assets/");
    }

    /** @return Fabric meta base */
    public URI fabricMetaBase() {
        return server.url("fabric/meta/v2/");
    }

    /** @return Fabric Maven base */
    public URI fabricMavenBase() {
        return server.url("fabric/maven/");
    }

    /** @return releases base URL (string, as stored in settings) */
    public String releasesBase() {
        return server.url("releases/").toString();
    }

    /**
     * @return endpoints pointing at this world (auth endpoints relative to the server; the built-in releases base URL
     *     is this world's {@code releases/})
     */
    public ServiceEndpoints endpoints() {
        return new ServiceEndpoints(manifestUrl(), resourcesBase(), fabricMetaBase(), fabricMavenBase(), server.url("adoptium/v3/"),
            MicrosoftAuthService.Endpoints.relativeTo(server.url("auth/")), releasesBase(), modrinthBase());
    }

    /**
     * @param sleeper sleeper for retries
     * @return a downloader using the real JDK transport against this world
     */
    public Downloader downloader(final Sleeper sleeper) {
        return new Downloader(new JdkHttpTransport("VANTA-Launcher/test"), sleeper, 3, 4);
    }

    /**
     * Builds a fully wired installer for a data directory.
     *
     * @param paths   paths
     * @param os      platform
     * @param shared  optional shared source
     * @param clock   clock
     * @return installer and its services
     */
    public Wired wire(final LauncherPaths paths, final OsInfo os, final Optional<SharedFileSource> shared, final Clock clock) {
        final Downloader downloader = downloader(Sleeper.NONE);
        final MojangService mojang = new MojangService(downloader, paths, manifestUrl(), resourcesBase());
        final FabricService fabric = new FabricService(downloader, paths, fabricMetaBase());
        final FabricApiService fabricApi = new FabricApiService(fabric, paths, fabricMavenBase());
        final VantaClientService vanta = new VantaClientService(downloader, paths, this::releasesBase);
        final ModrinthService modrinth = modrinth(paths, clock);
        final PerformancePack pack = new PerformancePack(modrinth, clock);
        final Installer installer = new Installer(paths, os, downloader, mojang, fabric, fabricApi, vanta, shared, clock, Optional.of(pack));
        return new Wired(downloader, mojang, fabric, fabricApi, vanta, installer, modrinth, pack);
    }

    /**
     * @param paths launcher paths
     * @param clock clock
     * @return a Modrinth service against this world
     */
    public ModrinthService modrinth(final LauncherPaths paths, final Clock clock) {
        final JdkHttpTransport transport = new JdkHttpTransport("VANTA-Launcher/test");
        return new ModrinthService(new ModrinthApi(transport, modrinthBase(), Sleeper.NONE, ModrinthApi.userAgent(LauncherVersion.VERSION)),
            new Downloader(transport, Sleeper.NONE, 3, 4), paths, clock, LauncherVersion.MINECRAFT);
    }

    /**
     * Wired services.
     *
     * @param downloader downloader
     * @param mojang     Mojang service
     * @param fabric     Fabric service
     * @param fabricApi  Fabric API service
     * @param vanta      VANTA client service
     * @param installer  installer (with the performance pack)
     * @param modrinth   Modrinth service
     * @param pack       performance pack
     */
    public record Wired(Downloader downloader, MojangService mojang, FabricService fabric, FabricApiService fabricApi,
                        VantaClientService vanta, Installer installer, ModrinthService modrinth, PerformancePack pack) {
    }

    private void rewriteDownload(final JsonObject download, final String path, final String seed, final int size) {
        final byte[] content = size == 0 ? seed.getBytes(StandardCharsets.UTF_8) : synthetic(seed, size);
        serve(path, content);
        download.addProperty("url", server.url(path).toString());
        download.addProperty("sha1", Checksums.hex(content, HashAlgorithm.SHA1));
        download.addProperty("size", content.length);
    }

    private void serve(final String path, final byte[] content) {
        blobs.put(path, content);
        totalBytes += content.length;
        server.add(path, content);
    }

    /**
     * Deterministic pseudo-content.
     *
     * @param seed seed text
     * @param size approximate size
     * @return bytes
     */
    public static byte[] synthetic(final String seed, final int size) {
        final StringBuilder sb = new StringBuilder(size + seed.length());
        int i = 0;
        while (sb.length() < size) {
            sb.append(seed).append('#').append(i++).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Convenience to build a JSON array of strings.
     *
     * @param values values
     * @return array
     */
    public static JsonArray array(final String... values) {
        final JsonArray a = new JsonArray();
        for (String v : values) {
            a.add(v);
        }
        return a;
    }

    @Override
    public void close() {
        server.close();
    }
}
