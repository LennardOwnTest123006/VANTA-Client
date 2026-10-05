package dev.vanta.core.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modrinth API fixtures for {@link FakeModrinthServer}. The Sodium and Iris responses in
 * {@code src/test/resources/modrinth/} are trimmed copies of real API v2 answers (2026-10-05); the other projects are
 * built in the same shape with fixture ids. Download URLs point at the fake server's {@code /cdn/} and every SHA-512
 * is computed from the fake file content served there.
 */
public final class ModrinthFixtures {
    private static final Pattern VAR = Pattern.compile("\\$\\{(SHA512|SIZE):([^}]+)}");

    private final FakeModrinthServer server;
    private final Map<String, byte[]> contents = new LinkedHashMap<>();

    public ModrinthFixtures(FakeModrinthServer server) {
        this.server = server;
    }

    /** Fake content of a file key (created on first use). */
    public byte[] content(String key) {
        return contents.computeIfAbsent(key, k -> ("fake jar content of " + k + " for the VANTA tests\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-512 of a file key's content. */
    public String sha512(String key) {
        return Sha512.hex(content(key));
    }

    /** Base of the fake CDN. */
    public String cdn() {
        return server.url("cdn/");
    }

    /** Loads a resource fixture and substitutes {@code ${CDN}}, {@code ${SHA512:key}} and {@code ${SIZE:key}}. */
    public String load(String name) {
        String text;
        try (InputStream in = ModrinthFixtures.class.getResourceAsStream("/modrinth/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("missing fixture " + name);
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        text = text.replace("${CDN}", cdn());
        Matcher m = VAR.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = m.group(1).equals("SHA512") ? sha512(m.group(2)) : Integer.toString(content(m.group(2)).length);
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Serves a fixture's download files: every {@code files[].url} of the given versions JSON. */
    public void serveFiles(String versionsJson, Map<String, String> filenameToKey) {
        JsonElement root = JsonParser.parseString(versionsJson);
        JsonArray versions = root.isJsonArray() ? root.getAsJsonArray() : wrap(root);
        for (JsonElement v : versions) {
            for (JsonElement f : v.getAsJsonObject().getAsJsonArray("files")) {
                JsonObject file = f.getAsJsonObject();
                String filename = file.get("filename").getAsString();
                String key = filenameToKey.get(filename);
                if (key != null) {
                    String path = java.net.URI.create(file.get("url").getAsString()).getPath();
                    server.file(path, content(key));
                }
            }
        }
    }

    private static JsonArray wrap(JsonElement e) {
        JsonArray a = new JsonArray();
        a.add(e);
        return a;
    }

    /** One version object of a versions array by id. */
    public static String versionById(String versionsJson, String id) {
        for (JsonElement v : JsonParser.parseString(versionsJson).getAsJsonArray()) {
            if (v.getAsJsonObject().get("id").getAsString().equals(id)) {
                return v.toString();
            }
        }
        throw new IllegalArgumentException("no version " + id);
    }

    // ---- builders in the API's shape ------------------------------------------------------------------------------

    /** A project object. */
    public static String project(String id, String slug, String title, String type) {
        JsonObject p = new JsonObject();
        p.addProperty("id", id);
        p.addProperty("slug", slug);
        p.addProperty("project_type", type);
        p.addProperty("title", title);
        p.addProperty("description", title + " fixture project");
        p.addProperty("downloads", 1000);
        p.add("loaders", Json.array(List.of(type.equals("shader") ? "iris" : "fabric")));
        p.add("game_versions", Json.array(List.of("1.21.11")));
        p.addProperty("icon_url", "");
        return p.toString();
    }

    /** A dependency object ({@code versionId}/{@code projectId} may be null). */
    public static JsonObject dependency(String versionId, String projectId, String type) {
        JsonObject d = new JsonObject();
        d.addProperty("version_id", versionId);
        d.addProperty("project_id", projectId);
        d.add("file_name", null);
        d.addProperty("dependency_type", type);
        return d;
    }

    /** A version object whose primary file is served by the fake CDN with the content of {@code key}. */
    public JsonObject version(String id, String projectId, String number, String versionType, String date,
                              List<String> loaders, String filename, String key, JsonObject... dependencies) {
        JsonObject v = new JsonObject();
        v.add("game_versions", Json.array(List.of("1.21.11")));
        v.add("loaders", Json.array(loaders));
        v.addProperty("id", id);
        v.addProperty("project_id", projectId);
        v.addProperty("name", number);
        v.addProperty("version_number", number);
        v.addProperty("date_published", date);
        v.addProperty("downloads", 10);
        v.addProperty("version_type", versionType);
        v.addProperty("status", "listed");
        JsonObject file = new JsonObject();
        JsonObject hashes = new JsonObject();
        hashes.addProperty("sha1", "0000000000000000000000000000000000000000");
        hashes.addProperty("sha512", sha512(key));
        file.add("hashes", hashes);
        String path = "cdn/data/" + projectId + "/versions/" + id + "/" + filename;
        file.addProperty("url", server.url(path));
        file.addProperty("filename", filename);
        file.addProperty("primary", true);
        file.addProperty("size", content(key).length);
        JsonArray files = new JsonArray();
        files.add(file);
        v.add("files", files);
        JsonArray deps = new JsonArray();
        for (JsonObject d : dependencies) {
            deps.add(d);
        }
        v.add("dependencies", deps);
        server.file("/" + path, content(key));
        return v;
    }

    /** Wraps version objects into an array string. */
    public static String array(JsonObject... versions) {
        JsonArray a = new JsonArray();
        for (JsonObject v : versions) {
            a.add(v);
        }
        return a.toString();
    }

    // ---- scenarios ------------------------------------------------------------------------------------------------

    /** Registers Sodium (real shape) under its slug and id, its versions and downloads. */
    public void sodium() {
        String project = load("project-sodium.json");
        server.json("/v2/project/sodium", project).json("/v2/project/AANobbMI", project);
        String versions = load("versions-sodium.json");
        server.json("/v2/project/AANobbMI/version", versions).json("/v2/project/sodium/version", versions);
        server.json("/v2/version/rkdTcxoT", versionById(versions, "rkdTcxoT"));
        server.json("/v2/version/RB7CDjTS", versionById(versions, "RB7CDjTS"));
        serveFiles(versions, Map.of(
                "sodium-fabric-0.8.15-beta.1+mc1.21.11.jar", "sodium-0.8.15-beta.1",
                "sodium-fabric-0.8.14+mc1.21.11.jar", "sodium-0.8.14",
                "sodium-fabric-0.8.12+mc1.21.11.jar", "sodium-0.8.12"));
    }

    /** Registers Iris (real shape: 1.10.8 requires Sodium version rkdTcxoT). */
    public void iris() {
        String project = project("YL57xq9U", "iris", "Iris Shaders", "mod");
        server.json("/v2/project/iris", project).json("/v2/project/YL57xq9U", project);
        String versions = load("versions-iris.json");
        server.json("/v2/project/YL57xq9U/version", versions);
        serveFiles(versions, Map.of(
                "iris-fabric-1.10.8+mc1.21.11.jar", "iris-1.10.8",
                "iris-fabric-1.10.7+mc1.21.11.jar", "iris-1.10.7"));
    }

    /** Registers a simple Fabric mod with one release (fixture ids). */
    public void simpleMod(String id, String slug, String title, String versionId, String number, String filename,
                          JsonObject... dependencies) {
        String project = project(id, slug, title, "mod");
        server.json("/v2/project/" + slug, project).json("/v2/project/" + id, project);
        JsonObject version = version(versionId, id, number, "release", "2026-09-01T10:00:00Z", List.of("fabric"),
                filename, slug + "-" + number, dependencies);
        server.json("/v2/project/" + id + "/version", array(version));
        server.json("/v2/version/" + versionId, version.toString());
    }

    /** Registers a project that has no version for 1.21.11 + Fabric (empty filtered list). */
    public void noCompatibleVersion(String id, String slug, String title) {
        String project = project(id, slug, title, "mod");
        server.json("/v2/project/" + slug, project).json("/v2/project/" + id, project);
        server.json("/v2/project/" + id + "/version", "[]");
    }

    /**
     * The whole Performance pack: Sodium and Iris in their real shape, Lithium / ImmediatelyFast plain, EntityCulling
     * requiring Fabric API (P7dR8mSH) and FerriteCore without a 1.21.11 Fabric version.
     */
    public void performancePack() {
        sodium();
        iris();
        simpleMod("LITHIUM1", "lithium", "Lithium", "LITHV001", "mc1.21.11-0.21.4-fabric",
                "lithium-fabric-0.21.4+mc1.21.11.jar");
        simpleMod("IMMFAST1", "immediatelyfast", "ImmediatelyFast", "IMMFV001", "1.14.3+1.21.11-fabric",
                "ImmediatelyFast-Fabric-1.14.3+1.21.11.jar");
        simpleMod("ENTCULL1", "entityculling", "EntityCulling", "ENTCV001", "1.11.2",
                "entityculling-fabric-1.11.2-mc1.21.11.jar",
                dependency(null, ModrinthConstants.FABRIC_API_PROJECT_ID, "required"));
        noCompatibleVersion("FERRITE1", "ferrite-core", "FerriteCore");
    }
}
