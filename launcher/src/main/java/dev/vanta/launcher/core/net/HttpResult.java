package dev.vanta.launcher.core.net;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A streaming HTTP response. The body is consumed once; {@link #close()} releases the connection.
 */
public final class HttpResult implements Closeable {

    private final int status;
    private final Map<String, List<String>> headers;
    private final InputStream body;
    private final long contentLength;

    /**
     * @param status        status code
     * @param headers       response headers (case-insensitive lookup)
     * @param body          body stream (never {@code null})
     * @param contentLength content length or {@code -1}
     */
    public HttpResult(final int status, final Map<String, List<String>> headers, final InputStream body, final long contentLength) {
        this.status = status;
        final Map<String, List<String>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k != null) {
                    map.put(k, List.copyOf(v));
                }
            });
        }
        this.headers = map;
        this.body = Objects.requireNonNullElseGet(body, () -> new ByteArrayInputStream(new byte[0]));
        this.contentLength = contentLength;
    }

    /**
     * Convenience constructor for in-memory responses (tests, fakes).
     *
     * @param status  status code
     * @param headers headers
     * @param body    body bytes
     * @return result
     */
    public static HttpResult of(final int status, final Map<String, List<String>> headers, final byte[] body) {
        return new HttpResult(status, headers, new ByteArrayInputStream(body), body.length);
    }

    /**
     * In-memory JSON response.
     *
     * @param status status
     * @param json   body
     * @return result
     */
    public static HttpResult json(final int status, final String json) {
        return of(status, Map.of("Content-Type", List.of("application/json")), json.getBytes(StandardCharsets.UTF_8));
    }

    /** @return status code */
    public int status() {
        return status;
    }

    /** @return {@code true} for 2xx */
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    /** @return headers */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * @param name header name (case-insensitive)
     * @return first value
     */
    public Optional<String> header(final String name) {
        final List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    /** @return body stream */
    public InputStream body() {
        return body;
    }

    /** @return content length or {@code -1} when unknown */
    public long contentLength() {
        return contentLength;
    }

    /**
     * Reads the whole body as UTF-8 text and closes the stream.
     *
     * @return text
     * @throws IOException on failure
     */
    public String bodyAsString() throws IOException {
        try (InputStream in = body) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Reads the whole body and closes the stream.
     *
     * @return bytes
     * @throws IOException on failure
     */
    public byte[] bodyAsBytes() throws IOException {
        try (InputStream in = body) {
            return in.readAllBytes();
        }
    }

    /** @return content type lower-cased without parameters */
    public Optional<String> contentType() {
        return header("Content-Type").map(v -> v.split(";")[0].trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}
