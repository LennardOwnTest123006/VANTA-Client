package dev.vanta.launcher.core.modrinth;

import dev.vanta.launcher.core.net.HttpRequestSpec;
import dev.vanta.launcher.core.net.HttpResult;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.testutil.FakeTransport;
import dev.vanta.launcher.testutil.Fixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Modrinth API client: request shape (User-Agent, query encoding, facets), rate limits and failures.
 */
class ModrinthApiTest {

    private static final URI BASE = URI.create("https://api.modrinth.test/v2/");
    private static final String UA = ModrinthApi.userAgent("1.1.0");

    private static String query(final HttpRequestSpec r) {
        return URLDecoder.decode(r.uri().getRawQuery(), StandardCharsets.UTF_8);
    }

    @Test
    void userAgentNamesTheProjectAndHowToReachIt() {
        assertEquals("LennardOwnTest123006/VANTA-Client/1.1.0 (https://github.com/LennardOwnTest123006/VANTA-Client)", UA);
    }

    @Test
    void versionListAsksForTheLoaderAndGameVersionWithTheUserAgent() throws Exception {
        final FakeTransport transport = new FakeTransport().onJson("/v2/project/sodium/version", 200, Fixtures.read("modrinth/versions-sodium.json"));
        final ModrinthApi api = new ModrinthApi(transport, BASE, d -> { }, UA);
        final List<ModrinthModels.Version> versions = api.projectVersions("sodium", ContentType.MOD, "1.21.11");
        assertEquals(3, versions.size());
        assertEquals("RB7CDjTS", versions.get(0).id());
        assertEquals("beta", versions.get(0).versionType());
        assertEquals("sodium-fabric-0.8.14+mc1.21.11.jar", versions.get(1).primaryFile().orElseThrow().filename());
        assertEquals(128, versions.get(1).primaryFile().orElseThrow().sha512().length());
        final HttpRequestSpec request = transport.requests().get(0);
        assertEquals(UA, request.headers().get("User-Agent"));
        assertEquals("loaders=[\"fabric\"]&game_versions=[\"1.21.11\"]", query(request));

        transport.onJson("/v2/project/complementary-reimagined/version", 200, "[]");
        api.projectVersions("complementary-reimagined", ContentType.SHADER, "1.21.11");
        assertEquals("loaders=[\"iris\"]&game_versions=[\"1.21.11\"]", query(transport.requests().get(1)));
        transport.onJson("/v2/project/fresh-animations/version", 200, "[]");
        api.projectVersions("fresh-animations", ContentType.RESOURCE_PACK, "1.21.11");
        assertEquals("loaders=[\"minecraft\"]&game_versions=[\"1.21.11\"]", query(transport.requests().get(2)));
        assertThrows(IllegalArgumentException.class, () -> api.projectVersions("../etc", ContentType.MOD, "1.21.11"));
    }

    @Test
    void searchUsesTheFacetsOfTheTab() throws Exception {
        final FakeTransport transport = new FakeTransport().onJson("/v2/search", 200, Fixtures.read("modrinth/search-mods-sodium.json"));
        final ModrinthApi api = new ModrinthApi(transport, BASE, d -> { }, UA);
        final ModrinthModels.SearchPage page = api.search(ContentType.MOD, "sodium", "1.21.11", 0, 20);
        assertEquals(44, page.totalHits());
        assertEquals("sodium-extra", page.hits().get(1).slug());
        assertEquals("query=sodium&facets=[[\"project_type:mod\"],[\"versions:1.21.11\"],[\"categories:fabric\"]]&index=relevance&offset=0&limit=20",
            query(transport.requests().get(0)));
        api.search(ContentType.SHADER, " ", "1.21.11", 20, 20);
        assertEquals("facets=[[\"project_type:shader\"],[\"versions:1.21.11\"],[\"categories:iris\"]]&index=downloads&offset=20&limit=20",
            query(transport.requests().get(1)), "a blank search lists the most downloaded");
        api.search(ContentType.RESOURCE_PACK, "fresh animations", "1.21.11", 0, 20);
        assertEquals("query=fresh animations&facets=[[\"project_type:resourcepack\"],[\"versions:1.21.11\"]]&index=relevance&offset=0&limit=20",
            query(transport.requests().get(2)));
    }

    @Test
    void rateLimitWaitsAsToldAndRetries() throws Exception {
        final FakeTransport transport = new FakeTransport();
        final int[] calls = {0};
        transport.on("/v2/project/lithium", r -> {
            calls[0]++;
            if (calls[0] == 1) {
                return HttpResult.of(429, Map.of("X-Ratelimit-Reset", List.of("7")), "slow down".getBytes(StandardCharsets.UTF_8));
            }
            return HttpResult.json(200, "{\"id\":\"gvQqBUqZ\",\"slug\":\"lithium\",\"title\":\"Lithium\",\"project_type\":\"mod\"}");
        });
        final List<Duration> waits = new ArrayList<>();
        final ModrinthApi api = new ModrinthApi(transport, BASE, waits::add, UA);
        assertEquals("Lithium", api.project("lithium").title());
        assertEquals(List.of(Duration.ofSeconds(7)), waits);

        assertEquals(Duration.ofSeconds(60), ModrinthApi.rateLimitWait(HttpResult.of(429, Map.of("Retry-After", List.of("3600")), new byte[0])));
        assertEquals(Duration.ofSeconds(1), ModrinthApi.rateLimitWait(HttpResult.of(429, Map.of("X-Ratelimit-Reset", List.of("0")), new byte[0])));
        assertEquals(Duration.ofSeconds(5), ModrinthApi.rateLimitWait(HttpResult.of(429, Map.of(), new byte[0])));
    }

    @Test
    void notFoundAndPersistentFailuresAreReported() {
        final FakeTransport transport = new FakeTransport();
        transport.onJson("/v2/project/gone", 404, "{\"error\":\"not_found\"}");
        transport.onJson("/v2/project/busy", 503, "{}");
        final List<Duration> waits = new ArrayList<>();
        final ModrinthApi api = new ModrinthApi(transport, BASE, waits::add, UA);
        assertEquals(404, assertThrows(HttpStatusException.class, () -> api.project("gone")).status());
        assertEquals(1, transport.hits("/v2/project/gone"), "404 is not retried");
        assertThrows(HttpStatusException.class, () -> api.project("busy"));
        assertEquals(ModrinthApi.ATTEMPTS, transport.hits("/v2/project/busy"));
        assertEquals(ModrinthApi.ATTEMPTS - 1, waits.size());
        transport.failWith(new IOException("connection refused"));
        assertThrows(IOException.class, () -> api.version("abc"));
    }

    @Test
    void versionOfAnUnknownIdIsEmpty() throws Exception {
        final ModrinthApi api = new ModrinthApi(new FakeTransport(), BASE, d -> { }, UA);
        assertTrue(api.version("missing").isEmpty());
        assertTrue(api.projects(List.of()).isEmpty());
    }
}
