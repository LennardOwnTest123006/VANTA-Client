package dev.vanta.core.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.vanta.core.config.CoreLog;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongConsumer;

/**
 * {@link ModrinthApi} over HTTP with {@link java.net.http.HttpClient}.
 * <ul>
 *   <li>Every request carries the descriptive User-Agent Modrinth requires
 *       ({@link ModrinthConstants#userAgent(String)}).</li>
 *   <li>HTTP 429 is retried after the delay the server asks for ({@code Retry-After}, else
 *       {@code X-Ratelimit-Reset}), at most {@link Config#maxAttempts()} times and never waiting longer than
 *       {@link Config#maxRetryWait()}; 502/503/504 and connection failures are retried with a short back-off.</li>
 *   <li>Downloads use the file URL Modrinth returned, must be HTTPS, are hashed while streaming into a temporary file
 *       next to the target and are only moved into place when the SHA-512 matches; otherwise the temporary file is
 *       deleted and {@link ModrinthException.Kind#HASH_MISMATCH} is thrown.</li>
 * </ul>
 */
public final class ModrinthClient implements ModrinthApi {

    /**
     * Client configuration.
     *
     * @param apiBase                base URI of API v2 (ends with a slash)
     * @param userAgent              User-Agent header value
     * @param connectTimeout         TCP/TLS connect timeout
     * @param requestTimeout         time until the response headers of an API request must arrive
     * @param downloadTimeout        time until the response headers of a download must arrive
     * @param maxAttempts            attempts per request (1 = no retries)
     * @param maxRetryWait           longest wait honoured for a rate limit
     * @param allowInsecureDownloads accept {@code http://} download URLs (tests against a local server only)
     */
    public record Config(URI apiBase, String userAgent, Duration connectTimeout, Duration requestTimeout,
                         Duration downloadTimeout, int maxAttempts, Duration maxRetryWait,
                         boolean allowInsecureDownloads) {
        public Config {
            Objects.requireNonNull(apiBase, "apiBase");
            String base = apiBase.toString();
            apiBase = base.endsWith("/") ? apiBase : URI.create(base + "/");
            Objects.requireNonNull(userAgent, "userAgent");
            maxAttempts = Math.max(1, maxAttempts);
        }

        /** Production settings for the public API. */
        public static Config defaults(String clientVersion) {
            return new Config(ModrinthConstants.API_BASE, ModrinthConstants.userAgent(clientVersion),
                    Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(30), 3, Duration.ofSeconds(60),
                    false);
        }

        /** Same settings against another base URI. */
        public Config withApiBase(URI base) {
            return new Config(base, userAgent, connectTimeout, requestTimeout, downloadTimeout, maxAttempts,
                    maxRetryWait, allowInsecureDownloads);
        }

        /** Same settings, accepting plain HTTP downloads (local test servers). */
        public Config withInsecureDownloads(boolean allow) {
            return new Config(apiBase, userAgent, connectTimeout, requestTimeout, downloadTimeout, maxAttempts,
                    maxRetryWait, allow);
        }
    }

    /** Waits between attempts; replaced in tests so rate-limit handling runs instantly. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final Config config;
    private final HttpClient http;
    private final Sleeper sleeper;
    private final Clock clock;

    /** Client with its own {@link HttpClient} that sleeps for real. */
    public ModrinthClient(Config config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), d -> Thread.sleep(Math.max(0L, d.toMillis())), Clock.systemUTC());
    }

    /** Fully injected client (tests). */
    public ModrinthClient(Config config, HttpClient http, Sleeper sleeper, Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.http = Objects.requireNonNull(http, "http");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The configuration. */
    public Config config() {
        return config;
    }

    // ---- API ----------------------------------------------------------------------------------------------------

    @Override
    public ModrinthSearchResult search(SearchRequest request) throws ModrinthException {
        StringBuilder query = new StringBuilder("search?");
        if (!request.query().isEmpty()) {
            query.append("query=").append(encode(request.query())).append('&');
        }
        query.append("facets=").append(encode(request.facetsJson()))
                .append("&index=").append(request.sort().apiName())
                .append("&offset=").append(request.offset())
                .append("&limit=").append(request.limit());
        return ModrinthSearchResult.fromJson(object(getJson(query.toString()), "search"));
    }

    @Override
    public ModrinthProject project(String idOrSlug) throws ModrinthException {
        return ModrinthProject.fromJson(object(getJson("project/" + segment(idOrSlug)), "project"));
    }

    @Override
    public List<ModrinthVersion> versions(String idOrSlug, List<String> loaders, List<String> gameVersions)
            throws ModrinthException {
        StringBuilder path = new StringBuilder("project/").append(segment(idOrSlug)).append("/version");
        char sep = '?';
        if (loaders != null && !loaders.isEmpty()) {
            path.append(sep).append("loaders=").append(encode(Json.array(loaders).toString()));
            sep = '&';
        }
        if (gameVersions != null && !gameVersions.isEmpty()) {
            path.append(sep).append("game_versions=").append(encode(Json.array(gameVersions).toString()));
        }
        JsonElement json = getJson(path.toString());
        if (!json.isJsonArray()) {
            throw new ModrinthException(ModrinthException.Kind.INVALID_RESPONSE, "version list is not an array");
        }
        List<ModrinthVersion> out = new ArrayList<>();
        for (JsonObject version : Json.objects(json.getAsJsonArray())) {
            out.add(ModrinthVersion.fromJson(version));
        }
        return out;
    }

    @Override
    public ModrinthVersion version(String versionId) throws ModrinthException {
        return ModrinthVersion.fromJson(object(getJson("version/" + segment(versionId)), "version"));
    }

    @Override
    public Map<String, ModrinthVersion> versionsByHash(Collection<String> sha512Hashes) throws ModrinthException {
        Map<String, ModrinthVersion> out = new LinkedHashMap<>();
        List<String> hashes = sha512Hashes.stream().filter(ModrinthFile::isSha512)
                .map(h -> h.toLowerCase(Locale.ROOT)).distinct().toList();
        if (hashes.isEmpty()) {
            return out;
        }
        JsonObject body = new JsonObject();
        JsonArray array = new JsonArray();
        hashes.forEach(array::add);
        body.add("hashes", array);
        body.addProperty("algorithm", "sha512");
        HttpRequest.Builder request = apiRequest("version_files")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        JsonObject json = object(readJson(send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)),
                "version_files"), "version_files");
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonObject()) {
                out.put(entry.getKey().toLowerCase(Locale.ROOT), ModrinthVersion.fromJson(entry.getValue().getAsJsonObject()));
            }
        }
        return out;
    }

    @Override
    public void download(ModrinthFile file, Path target, LongConsumer bytesRead) throws ModrinthException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(target, "target");
        if (!file.hasSha512()) {
            throw new ModrinthException(ModrinthException.Kind.UNSAFE_FILE,
                    file.filename() + " has no SHA-512; refusing to download it");
        }
        URI uri;
        try {
            uri = URI.create(file.url());
        } catch (IllegalArgumentException e) {
            throw new ModrinthException(ModrinthException.Kind.UNSAFE_FILE, "invalid download URL " + file.url(), e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(config.allowInsecureDownloads() && scheme.equals("http"))) {
            throw new ModrinthException(ModrinthException.Kind.UNSAFE_FILE, "refusing non-HTTPS download " + file.url());
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(config.downloadTimeout())
                .header("User-Agent", config.userAgent())
                .GET();
        HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status != 200) {
            discard(response);
            throw statusError(status, "download of " + file.filename());
        }
        Path absolute = target.toAbsolutePath();
        Path dir = absolute.getParent();
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, ".vanta-", ".part");
            MessageDigest digest = Sha512.digest();
            long total = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new ModrinthException(ModrinthException.Kind.CANCELLED, "download cancelled");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    total += read;
                    if (bytesRead != null) {
                        bytesRead.accept(total);
                    }
                }
            }
            String actual = Sha512.hex(digest);
            if (!actual.equals(file.sha512())) {
                throw new ModrinthException(ModrinthException.Kind.HASH_MISMATCH, file.filename()
                        + ": SHA-512 " + actual + " does not match the published " + file.sha512());
            }
            try {
                Files.move(tmp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (ModrinthException e) {
            throw e;
        } catch (IOException e) {
            throw new ModrinthException(ModrinthException.Kind.NETWORK, "download of " + file.filename() + " failed: "
                    + e.getMessage(), e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    CoreLog.warn(e, "Could not delete the partial download {}", tmp);
                }
            }
        }
    }

    // ---- HTTP plumbing -------------------------------------------------------------------------------------------

    private HttpRequest.Builder apiRequest(String relative) {
        return HttpRequest.newBuilder(config.apiBase().resolve(relative))
                .timeout(config.requestTimeout())
                .header("User-Agent", config.userAgent())
                .header("Accept", "application/json");
    }

    private JsonElement getJson(String relative) throws ModrinthException {
        HttpRequest.Builder request = apiRequest(relative).GET();
        return readJson(send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)), relative);
    }

    private static JsonElement readJson(HttpResponse<String> response, String what) throws ModrinthException {
        int status = response.statusCode();
        if (status != 200) {
            throw statusError(status, what);
        }
        try {
            JsonElement json = JsonParser.parseString(response.body());
            if (json == null || json.isJsonNull()) {
                throw new ModrinthException(ModrinthException.Kind.INVALID_RESPONSE, what + ": empty response");
            }
            return json;
        } catch (JsonParseException e) {
            throw new ModrinthException(ModrinthException.Kind.INVALID_RESPONSE, what + ": " + e.getMessage(), e);
        }
    }

    private static JsonObject object(JsonElement json, String what) throws ModrinthException {
        if (!json.isJsonObject()) {
            throw new ModrinthException(ModrinthException.Kind.INVALID_RESPONSE, what + " is not a JSON object");
        }
        return json.getAsJsonObject();
    }

    private static ModrinthException statusError(int status, String what) {
        if (status == 404 || status == 410) {
            return new ModrinthException(ModrinthException.Kind.NOT_FOUND, what + ": not found", status, null, null);
        }
        if (status == 429) {
            return new ModrinthException(ModrinthException.Kind.RATE_LIMITED, what + ": rate limited", status, null,
                    null);
        }
        return new ModrinthException(ModrinthException.Kind.HTTP, what + ": HTTP " + status, status, null, null);
    }

    /** Sends with the retry policy described in the class comment. */
    private <T> HttpResponse<T> send(HttpRequest.Builder builder, HttpResponse.BodyHandler<T> handler)
            throws ModrinthException {
        int attempt = 0;
        while (true) {
            attempt++;
            HttpRequest request = builder.build();
            HttpResponse<T> response;
            try {
                response = http.send(request, handler);
            } catch (IOException e) {
                if (attempt >= config.maxAttempts()) {
                    throw new ModrinthException(ModrinthException.Kind.NETWORK,
                            "Modrinth could not be reached (" + request.uri().getHost() + "): " + e.getMessage(), e);
                }
                pause(backoff(attempt));
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModrinthException(ModrinthException.Kind.CANCELLED, "interrupted", e);
            }
            int status = response.statusCode();
            if (status == 429) {
                Duration wait = retryAfter(response.headers(), attempt);
                discard(response);
                if (attempt >= config.maxAttempts() || wait.compareTo(config.maxRetryWait()) > 0) {
                    throw new ModrinthException(ModrinthException.Kind.RATE_LIMITED,
                            "Modrinth rate limit reached; retry after " + wait.toSeconds() + " s", 429, wait, null);
                }
                CoreLog.info("Modrinth rate limit reached; waiting {} ms before retrying {}", wait.toMillis(),
                        request.uri().getPath());
                pause(wait);
                continue;
            }
            if ((status == 502 || status == 503 || status == 504) && attempt < config.maxAttempts()) {
                discard(response);
                pause(backoff(attempt));
                continue;
            }
            return response;
        }
    }

    private void pause(Duration duration) throws ModrinthException {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModrinthException(ModrinthException.Kind.CANCELLED, "interrupted", e);
        }
    }

    private static Duration backoff(int attempt) {
        return Duration.ofMillis(500L * attempt);
    }

    /**
     * Delay requested by a 429: {@code Retry-After} in seconds or as an HTTP date, else Modrinth's
     * {@code X-Ratelimit-Reset} (seconds until the window resets), else one second per attempt.
     */
    Duration retryAfter(HttpHeaders headers, int attempt) {
        OptionalLong seconds = parseSeconds(headers.firstValue("Retry-After").orElse(null));
        if (seconds.isEmpty()) {
            String date = headers.firstValue("Retry-After").orElse(null);
            if (date != null) {
                try {
                    ZonedDateTime when = ZonedDateTime.parse(date.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                    long millis = Math.max(0L, when.toInstant().toEpochMilli() - clock.millis());
                    return Duration.ofMillis(millis);
                } catch (DateTimeParseException e) {
                    // fall through to the rate-limit headers
                }
            }
            seconds = parseSeconds(headers.firstValue("X-Ratelimit-Reset").orElse(null));
        }
        if (seconds.isPresent()) {
            return Duration.ofSeconds(Math.max(0L, seconds.getAsLong()));
        }
        return Duration.ofSeconds(attempt);
    }

    private static OptionalLong parseSeconds(String value) {
        if (value == null || value.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static void discard(HttpResponse<?> response) {
        if (response.body() instanceof InputStream in) {
            try {
                in.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }

    /** Query parameter encoding with {@code %20} for spaces. */
    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Encodes one path segment (ids and slugs); rejects empty values. */
    static String segment(String idOrSlug) throws ModrinthException {
        if (idOrSlug == null || idOrSlug.isBlank()) {
            throw new ModrinthException(ModrinthException.Kind.NOT_FOUND, "empty project or version id");
        }
        return encode(idOrSlug.trim());
    }
}
