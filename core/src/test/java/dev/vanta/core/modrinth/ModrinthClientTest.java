package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModrinthClientTest {
    @TempDir
    Path dir;

    private FakeModrinthServer server;
    private ModrinthFixtures fx;
    private final List<Duration> sleeps = new ArrayList<>();
    private ModrinthClient client;

    @BeforeEach
    void start() throws Exception {
        server = new FakeModrinthServer();
        fx = new ModrinthFixtures(server);
        client = server.client(sleeps);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void searchSendsFacetsForFabricModsOnTheGameVersionAndParsesHits() throws Exception {
        server.json("/v2/search", fx.load("search-sodium.json"));
        ModrinthSearchResult result = client.search(SearchRequest.of("sodium extra", ModrinthProjectType.MOD));
        assertEquals(3, result.hits().size());
        assertEquals(44, result.totalHits());
        assertTrue(result.hasMore());
        ModrinthSearchHit first = result.hits().get(0);
        assertEquals("AANobbMI", first.projectId());
        assertEquals("sodium", first.slug());
        assertEquals("Sodium", first.title());
        assertEquals("jellysquid3", first.author());
        assertEquals(236787190L, first.downloads());
        assertEquals(8703084, first.color());
        assertEquals(ModrinthProjectType.MOD, first.type().orElseThrow());

        FakeModrinthServer.Request request = server.requests("/v2/search").get(0);
        String query = URLDecoder.decode(request.query(), StandardCharsets.UTF_8);
        assertTrue(query.contains("query=sodium extra"), query);
        assertTrue(query.contains("facets=[[\"project_type:mod\"],[\"versions:1.21.11\"],[\"categories:fabric\"]]"), query);
        assertTrue(query.contains("index=relevance"), query);
        assertTrue(request.query().contains("query=sodium%20extra"), "spaces are sent as %20");
        assertEquals("LennardOwnTest123006/VANTA-Client/1.1.0 (https://github.com/LennardOwnTest123006/VANTA-Client)",
                request.userAgent());
    }

    @Test
    void shaderAndResourcePackFacets() {
        assertEquals("[[\"project_type:shader\"],[\"versions:1.21.11\"],[\"categories:iris\"]]",
                SearchRequest.of("", ModrinthProjectType.SHADER).facetsJson());
        assertEquals("[[\"project_type:resourcepack\"],[\"versions:1.21.11\"]]",
                SearchRequest.of("x", ModrinthProjectType.RESOURCE_PACK).facetsJson());
        SearchRequest browse = SearchRequest.of("  ", ModrinthProjectType.SHADER);
        assertEquals(SearchRequest.Sort.DOWNLOADS, browse.sort(), "an empty query lists the most downloaded");
        assertEquals(20, browse.nextPage().offset());
    }

    @Test
    void projectAndVersionsAreParsedFromRealShapes() throws Exception {
        fx.sodium();
        ModrinthProject project = client.project("sodium");
        assertEquals("AANobbMI", project.id());
        assertEquals(ModrinthProjectType.MOD, project.type().orElseThrow());
        List<ModrinthVersion> versions = client.versions("AANobbMI", List.of("fabric"), List.of("1.21.11"));
        assertEquals(3, versions.size());
        ModrinthVersion release = versions.get(1);
        assertEquals("rkdTcxoT", release.id());
        assertEquals("mc1.21.11-0.8.14-fabric", release.versionNumber());
        assertEquals(ModrinthVersion.Channel.RELEASE, release.channel());
        ModrinthFile file = release.primaryFile().orElseThrow();
        assertEquals("sodium-fabric-0.8.14+mc1.21.11.jar", file.filename());
        assertEquals(fx.sha512("sodium-0.8.14"), file.sha512());
        assertTrue(file.url().endsWith("/rkdTcxoT/sodium-fabric-0.8.14%2Bmc1.21.11.jar"));
        String query = URLDecoder.decode(server.requests("/v2/project/AANobbMI/version").get(0).query(),
                StandardCharsets.UTF_8);
        assertEquals("loaders=[\"fabric\"]&game_versions=[\"1.21.11\"]", query);
    }

    @Test
    void missingProjectIsNotFound() {
        ModrinthException e = assertThrows(ModrinthException.class, () -> client.project("does-not-exist"));
        assertEquals(ModrinthException.Kind.NOT_FOUND, e.kind());
        assertEquals(404, e.status());
    }

    @Test
    void rateLimitIsRetriedAfterTheRequestedDelay() throws Exception {
        server.json("/v2/search", fx.load("search-sodium.json"));
        server.rateLimit("/v2/search", 1, "2", null);
        ModrinthSearchResult result = client.search(SearchRequest.of("sodium", ModrinthProjectType.MOD));
        assertEquals(3, result.hits().size());
        assertEquals(List.of(Duration.ofSeconds(2)), sleeps, "waited exactly as long as Retry-After asked");
        assertEquals(2, server.requests("/v2/search").size());
    }

    @Test
    void rateLimitFallsBackToTheResetHeader() throws Exception {
        server.json("/v2/project/sodium", fx.load("project-sodium.json"));
        server.rateLimit("/v2/project/sodium", 1, null, "7");
        assertEquals("AANobbMI", client.project("sodium").id());
        assertEquals(List.of(Duration.ofSeconds(7)), sleeps);
    }

    @Test
    void persistentRateLimitFailsWithRetryAfter() {
        server.json("/v2/project/sodium", fx.load("project-sodium.json"));
        server.rateLimit("/v2/project/sodium", 10, "3", null);
        ModrinthException e = assertThrows(ModrinthException.class, () -> client.project("sodium"));
        assertEquals(ModrinthException.Kind.RATE_LIMITED, e.kind());
        assertEquals(Duration.ofSeconds(3), e.retryAfter().orElseThrow());
        assertEquals(3, server.requests("/v2/project/sodium").size(), "three attempts in total");
    }

    @Test
    void tooLongRateLimitIsNotWaitedFor() {
        server.json("/v2/project/sodium", fx.load("project-sodium.json"));
        server.rateLimit("/v2/project/sodium", 1, "3600", null);
        ModrinthException e = assertThrows(ModrinthException.class, () -> client.project("sodium"));
        assertEquals(ModrinthException.Kind.RATE_LIMITED, e.kind());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void downloadVerifiesSha512AndMovesIntoPlace() throws Exception {
        fx.sodium();
        ModrinthVersion release = client.version("rkdTcxoT");
        ModrinthFile file = release.primaryFile().orElseThrow();
        Path target = dir.resolve("mods").resolve(file.filename());
        long[] seen = {0};
        client.download(file, target, n -> seen[0] = n);
        assertArrayEquals(fx.content("sodium-0.8.14"), Files.readAllBytes(target));
        assertEquals(file.size(), seen[0]);
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertEquals(1, files.count(), "no temporary file left behind");
        }
        assertEquals(ModrinthConstants.userAgent("1.1.0"),
                server.requests().get(server.requests().size() - 1).userAgent());
    }

    @Test
    void hashMismatchIsRejectedAndNothingIsKept() throws Exception {
        fx.sodium();
        ModrinthFile real = client.version("rkdTcxoT").primaryFile().orElseThrow();
        ModrinthFile tampered = new ModrinthFile(real.url(), real.filename(), true, real.size(),
                fx.sha512("something else"), "");
        Path target = dir.resolve("mods").resolve(real.filename());
        ModrinthException e = assertThrows(ModrinthException.class, () -> client.download(tampered, target, null));
        assertEquals(ModrinthException.Kind.HASH_MISMATCH, e.kind());
        assertFalse(Files.exists(target));
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertEquals(0, files.count(), "the unverified temporary file was deleted");
        }
    }

    @Test
    void downloadsNeedHttpsAndAChecksum() throws Exception {
        ModrinthClient strict = new ModrinthClient(ModrinthClient.Config.defaults("1.1.0").withApiBase(server.apiBase()));
        ModrinthFile http = new ModrinthFile(server.url("cdn/x.jar"), "x.jar", true, 1, fx.sha512("x"), "");
        assertEquals(ModrinthException.Kind.UNSAFE_FILE,
                assertThrows(ModrinthException.class, () -> strict.download(http, dir.resolve("x.jar"), null)).kind());
        ModrinthFile noHash = new ModrinthFile("https://cdn.modrinth.com/x.jar", "x.jar", true, 1, "", "");
        assertEquals(ModrinthException.Kind.UNSAFE_FILE,
                assertThrows(ModrinthException.class, () -> strict.download(noHash, dir.resolve("x.jar"), null)).kind());
        assertTrue(server.requests().isEmpty(), "nothing was requested");
    }

    @Test
    void versionFilesLookupPostsSha512Hashes() throws Exception {
        String versions = fx.load("versions-sodium.json");
        String hash = fx.sha512("sodium-0.8.14");
        server.json("/v2/version_files", "{\"" + hash + "\":" + ModrinthFixtures.versionById(versions, "rkdTcxoT") + "}");
        Map<String, ModrinthVersion> found = client.versionsByHash(List.of(hash, "not-a-hash"));
        assertEquals("AANobbMI", found.get(hash).projectId());
        FakeModrinthServer.Request request = server.requests("/v2/version_files").get(0);
        assertEquals("POST", request.method());
        assertEquals("{\"hashes\":[\"" + hash + "\"],\"algorithm\":\"sha512\"}", request.body());
        assertTrue(client.versionsByHash(List.of()).isEmpty());
    }

    @Test
    void serverErrorsAreRetriedThenReported() {
        server.status("/v2/project/sodium", 503, "{}");
        ModrinthException e = assertThrows(ModrinthException.class, () -> client.project("sodium"));
        assertEquals(ModrinthException.Kind.HTTP, e.kind());
        assertEquals(503, e.status());
        assertEquals(3, server.requests("/v2/project/sodium").size());
    }

    @Test
    void invalidJsonIsReported() {
        server.json("/v2/project/sodium", "not json {");
        assertEquals(ModrinthException.Kind.INVALID_RESPONSE,
                assertThrows(ModrinthException.class, () -> client.project("sodium")).kind());
    }
}
