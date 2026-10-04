package dev.vanta.launcher.testutil;

import dev.vanta.launcher.core.net.HttpRequestSpec;
import dev.vanta.launcher.core.net.HttpResult;
import dev.vanta.launcher.core.net.HttpTransport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * In-memory {@link HttpTransport} routing by request path. Supports per-path response sequences and records every
 * request (including bodies) for assertions.
 */
public final class FakeTransport implements HttpTransport {

    private final Map<String, Deque<Function<HttpRequestSpec, HttpResult>>> sequences = new ConcurrentHashMap<>();
    private final Map<String, Function<HttpRequestSpec, HttpResult>> routes = new ConcurrentHashMap<>();
    private final List<HttpRequestSpec> requests = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private IOException failure;

    /**
     * Registers a handler for a path.
     *
     * @param path    request path
     * @param handler handler
     * @return this
     */
    public FakeTransport on(final String path, final Function<HttpRequestSpec, HttpResult> handler) {
        routes.put(path, handler);
        return this;
    }

    /**
     * Registers a JSON response.
     *
     * @param path   request path
     * @param status status
     * @param json   JSON body
     * @return this
     */
    public FakeTransport onJson(final String path, final int status, final String json) {
        return on(path, req -> HttpResult.json(status, json));
    }

    /**
     * Registers a sequence of JSON responses consumed one per request; the last one repeats.
     *
     * @param path      request path
     * @param responses status/body pairs
     * @return this
     */
    public FakeTransport onJsonSequence(final String path, final List<Map.Entry<Integer, String>> responses) {
        final Deque<Function<HttpRequestSpec, HttpResult>> queue = new ArrayDeque<>();
        for (Map.Entry<Integer, String> r : responses) {
            queue.add(req -> HttpResult.json(r.getKey(), r.getValue()));
        }
        sequences.put(path, queue);
        return this;
    }

    /**
     * Makes every request fail with an {@link IOException}.
     *
     * @param e exception
     */
    public void failWith(final IOException e) {
        this.failure = e;
    }

    /** @return recorded requests */
    public List<HttpRequestSpec> requests() {
        return new ArrayList<>(requests);
    }

    /** @return recorded request bodies as UTF-8 strings */
    public List<String> bodies() {
        return new ArrayList<>(bodies);
    }

    /**
     * @param path request path
     * @return how many requests hit the path
     */
    public long hits(final String path) {
        return requests.stream().filter(r -> r.uri().getPath().equals(path)).count();
    }

    @Override
    public HttpResult execute(final HttpRequestSpec request) throws IOException {
        requests.add(request);
        bodies.add(new String(request.body(), StandardCharsets.UTF_8));
        if (failure != null) {
            throw failure;
        }
        final String path = request.uri().getPath();
        final Deque<Function<HttpRequestSpec, HttpResult>> seq = sequences.get(path);
        if (seq != null && !seq.isEmpty()) {
            final Function<HttpRequestSpec, HttpResult> next = seq.size() == 1 ? seq.peek() : seq.poll();
            return next.apply(request);
        }
        final Function<HttpRequestSpec, HttpResult> handler = routes.get(path);
        if (handler == null) {
            return HttpResult.json(404, "{\"error\":\"not found\",\"path\":\"" + path + "\"}");
        }
        return handler.apply(request);
    }
}
