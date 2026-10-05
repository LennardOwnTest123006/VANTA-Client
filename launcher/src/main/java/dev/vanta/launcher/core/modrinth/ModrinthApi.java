package dev.vanta.launcher.core.modrinth;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.net.HttpRequestSpec;
import dev.vanta.launcher.core.net.HttpResult;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.core.net.HttpTransport;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.Sleeper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read-only client for the Modrinth API v2 ({@code https://api.modrinth.com/v2/}).
 *
 * <ul>
 *   <li>Every request carries the descriptive {@code User-Agent} Modrinth asks API clients for
 *       ({@link #userAgent(String)}: {@code LennardOwnTest123006/VANTA-Client/<launcher version>
 *       (https://github.com/LennardOwnTest123006/VANTA-Client)}).</li>
 *   <li>HTTP 429 (rate limited) waits for the number of seconds in {@code X-Ratelimit-Reset} or {@code Retry-After}
 *       (1 to {@value #MAX_RATE_LIMIT_WAIT_SECONDS} seconds) and tries again; 5xx answers and transport failures are
 *       retried with back-off; {@value #ATTEMPTS} attempts in total. Other statuses raise {@link HttpStatusException}
 *       (404: unknown project or version).</li>
 *   <li>No authentication: the launcher only reads public data.</li>
 * </ul>
 */
public final class ModrinthApi {

    /** Production API base. */
    public static final URI API_BASE = URI.create("https://api.modrinth.com/v2/");
    /** Project id of Fabric API on Modrinth; VANTA installs Fabric API itself from the Fabric Maven. */
    public static final String FABRIC_API_PROJECT_ID = "P7dR8mSH";
    /** Attempts per request. */
    public static final int ATTEMPTS = 4;
    /** Longest wait after HTTP 429. */
    public static final int MAX_RATE_LIMIT_WAIT_SECONDS = 60;
    /** Results per search page. */
    public static final int PAGE_SIZE = 20;

    private static final Logger LOG = LauncherLog.get("Modrinth");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpTransport transport;
    private final URI base;
    private final Sleeper sleeper;
    private final String userAgent;

    /**
     * @param transport HTTP transport
     * @param base      API base ending with a slash
     * @param sleeper   sleeper for rate limits and back-off
     * @param userAgent value of the {@code User-Agent} header
     */
    public ModrinthApi(final HttpTransport transport, final URI base, final Sleeper sleeper, final String userAgent) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.base = Objects.requireNonNull(base, "base");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
    }

    /**
     * The User-Agent Modrinth requires: it names the project and how to contact it.
     *
     * @param launcherVersion launcher version
     * @return e.g. {@code LennardOwnTest123006/VANTA-Client/1.1.0 (https://github.com/LennardOwnTest123006/VANTA-Client)}
     */
    public static String userAgent(final String launcherVersion) {
        return "LennardOwnTest123006/VANTA-Client/" + launcherVersion + " (https://github.com/LennardOwnTest123006/VANTA-Client)";
    }

    /** @return the API base */
    public URI base() {
        return base;
    }

    /** @return the User-Agent sent with every request */
    public String agent() {
        return userAgent;
    }

    /**
     * {@code GET /project/{idOrSlug}/version?loaders=["<loader>"]&game_versions=["<gameVersion>"]}.
     *
     * @param idOrSlug    project id or slug
     * @param type        content type (selects the loader)
     * @param gameVersion Minecraft version
     * @return the versions Modrinth lists, newest first as the API returns them
     * @throws HttpStatusException  404 when the project does not exist
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public List<ModrinthModels.Version> projectVersions(final String idOrSlug, final ContentType type, final String gameVersion)
        throws IOException, InterruptedException {
        final String query = "loaders=" + enc("[\"" + type.loader() + "\"]") + "&game_versions=" + enc("[\"" + gameVersion + "\"]");
        return get("project/" + path(idOrSlug) + "/version?" + query, new TypeToken<List<ModrinthModels.Version>>() { }.getType());
    }

    /**
     * {@code GET /version/{id}}.
     *
     * @param versionId version id
     * @return the version, empty when Modrinth does not know it (404)
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public Optional<ModrinthModels.Version> version(final String versionId) throws IOException, InterruptedException {
        try {
            return Optional.of(get("version/" + path(versionId), ModrinthModels.Version.class));
        } catch (HttpStatusException e) {
            if (e.status() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * {@code GET /project/{idOrSlug}}.
     *
     * @param idOrSlug project id or slug
     * @return project
     * @throws HttpStatusException  404 when the project does not exist
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public ModrinthModels.Project project(final String idOrSlug) throws IOException, InterruptedException {
        return get("project/" + path(idOrSlug), ModrinthModels.Project.class);
    }

    /**
     * {@code GET /projects?ids=[...]}.
     *
     * @param ids project ids
     * @return the projects Modrinth knows (unknown ids are left out)
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public List<ModrinthModels.Project> projects(final Collection<String> ids) throws IOException, InterruptedException {
        if (ids.isEmpty()) {
            return List.of();
        }
        final StringBuilder json = new StringBuilder("[");
        for (String id : ids) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(id.replace("\"", "")).append('"');
        }
        json.append(']');
        return get("projects?ids=" + enc(json.toString()), new TypeToken<List<ModrinthModels.Project>>() { }.getType());
    }

    /**
     * {@code GET /search} limited to content that loads in this instance: the project type, the Minecraft version and,
     * for mods and shaders, the loader ({@code categories:fabric}, {@code categories:iris}).
     *
     * @param type        content type
     * @param query       search text (blank: most downloaded first)
     * @param gameVersion Minecraft version
     * @param offset      offset of the first result
     * @param limit       page size
     * @return one page of results
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public ModrinthModels.SearchPage search(final ContentType type, final String query, final String gameVersion, final int offset,
                                            final int limit) throws IOException, InterruptedException {
        final StringBuilder facets = new StringBuilder("[[\"project_type:").append(type.id()).append("\"],[\"versions:")
            .append(gameVersion).append("\"]");
        type.loaderFacet().ifPresent(f -> facets.append(",[\"").append(f).append("\"]"));
        facets.append(']');
        final String q = query == null ? "" : query.trim();
        final StringBuilder url = new StringBuilder("search?");
        if (!q.isEmpty()) {
            url.append("query=").append(enc(q)).append('&');
        }
        url.append("facets=").append(enc(facets.toString()))
            .append("&index=").append(q.isEmpty() ? "downloads" : "relevance")
            .append("&offset=").append(Math.max(0, offset))
            .append("&limit=").append(Math.max(1, Math.min(100, limit)));
        return get(url.toString(), ModrinthModels.SearchPage.class);
    }

    // ---------------------------------------------------------------- transport

    private <T> T get(final String relative, final Type type) throws IOException, InterruptedException {
        final URI uri = base.resolve(relative);
        final String body = fetch(uri);
        try {
            final T value = Json.GSON.fromJson(body, type);
            if (value == null) {
                throw new IOException("Empty response from " + uri);
            }
            return value;
        } catch (JsonParseException e) {
            throw new IOException("Unexpected response from " + uri + ": " + e.getMessage(), e);
        }
    }

    private String fetch(final URI uri) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            final HttpRequestSpec spec = HttpRequestSpec.get(uri).withHeader("User-Agent", userAgent)
                .withHeader("Accept", "application/json").withTimeout(TIMEOUT);
            Duration wait;
            try (HttpResult result = transport.execute(spec)) {
                if (result.isSuccess()) {
                    return result.bodyAsString();
                }
                final HttpStatusException status = new HttpStatusException(result.status(), uri);
                if (result.status() == 429) {
                    wait = rateLimitWait(result);
                    LOG.log(Level.INFO, "Modrinth rate limit reached; waiting {0} s before retrying {1}", new Object[] {wait.toSeconds(), uri});
                } else if (status.isRetryable()) {
                    wait = backoff(attempt);
                } else {
                    throw status;
                }
                last = status;
            } catch (HttpStatusException e) {
                throw e;
            } catch (IOException e) {
                last = e;
                wait = backoff(attempt);
            }
            if (attempt < ATTEMPTS) {
                sleeper.sleep(wait);
            }
        }
        throw last;
    }

    /**
     * @param result a 429 response
     * @return how long Modrinth asks to wait: {@code X-Ratelimit-Reset} (seconds until the window resets), else
     *     {@code Retry-After} (seconds), clamped to 1..{@value #MAX_RATE_LIMIT_WAIT_SECONDS} seconds; 5 seconds when
     *     neither header is usable
     */
    static Duration rateLimitWait(final HttpResult result) {
        final Optional<String> header = result.header("X-Ratelimit-Reset").or(() -> result.header("Retry-After"));
        long seconds = 5;
        if (header.isPresent()) {
            try {
                seconds = Long.parseLong(header.get().trim());
            } catch (NumberFormatException ignored) {
                // an HTTP date in Retry-After: keep the default
            }
        }
        return Duration.ofSeconds(Math.max(1, Math.min(MAX_RATE_LIMIT_WAIT_SECONDS, seconds)));
    }

    private static Duration backoff(final int attempt) {
        return Duration.ofMillis(500L << Math.min(4, attempt - 1));
    }

    private static String enc(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * @param segment id or slug
     * @return the segment, URL-encoded; ids and slugs are plain ASCII, anything else is encoded
     */
    private static String path(final String segment) {
        final String s = Objects.requireNonNull(segment, "segment").trim();
        if (s.isEmpty() || s.contains("/") || s.contains("..")) {
            throw new IllegalArgumentException("Not a Modrinth id or slug: '" + segment + "'");
        }
        return enc(s);
    }
}
