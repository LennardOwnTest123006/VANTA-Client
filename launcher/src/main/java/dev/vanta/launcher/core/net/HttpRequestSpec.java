package dev.vanta.launcher.core.net;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A transport-independent HTTP request.
 *
 * @param method      HTTP method
 * @param uri         absolute URI
 * @param headers     request headers (insertion ordered)
 * @param body        request body or empty array
 * @param readTimeout timeout for the whole exchange
 */
public record HttpRequestSpec(String method, URI uri, Map<String, String> headers, byte[] body, Duration readTimeout) {

    /** Default exchange timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    public HttpRequestSpec {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(uri, "uri");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? new byte[0] : body.clone();
        readTimeout = readTimeout == null ? DEFAULT_TIMEOUT : readTimeout;
    }

    /**
     * GET request.
     *
     * @param uri URI
     * @return request
     */
    public static HttpRequestSpec get(final URI uri) {
        return new HttpRequestSpec("GET", uri, Map.of(), null, DEFAULT_TIMEOUT);
    }

    /**
     * POST with an {@code application/x-www-form-urlencoded} body.
     *
     * @param uri  URI
     * @param form form fields
     * @return request
     */
    public static HttpRequestSpec postForm(final URI uri, final Map<String, String> form) {
        final StringJoiner joiner = new StringJoiner("&");
        form.forEach((k, v) -> joiner.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
            + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Accept", "application/json");
        return new HttpRequestSpec("POST", uri, headers, joiner.toString().getBytes(StandardCharsets.UTF_8),
            Duration.ofSeconds(60));
    }

    /**
     * POST with a JSON body.
     *
     * @param uri  URI
     * @param json JSON text
     * @return request
     */
    public static HttpRequestSpec postJson(final URI uri, final String json) {
        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        return new HttpRequestSpec("POST", uri, headers, json.getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(60));
    }

    /**
     * Returns a copy with an additional header.
     *
     * @param name  header name
     * @param value header value
     * @return copy
     */
    public HttpRequestSpec withHeader(final String name, final String value) {
        final Map<String, String> copy = new LinkedHashMap<>(headers);
        copy.put(name, value);
        return new HttpRequestSpec(method, uri, copy, body, readTimeout);
    }

    /**
     * Returns a copy with a different timeout.
     *
     * @param timeout timeout
     * @return copy
     */
    public HttpRequestSpec withTimeout(final Duration timeout) {
        return new HttpRequestSpec(method, uri, headers, body, timeout);
    }

    /** @return a copy of the body */
    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public String toString() {
        return method + " " + uri;
    }
}
