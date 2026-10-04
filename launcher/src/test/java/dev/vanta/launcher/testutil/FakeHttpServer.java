package dev.vanta.launcher.testutil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local HTTP server for tests, backed by the JDK's {@link HttpServer}. Routes are exact paths; failure injection
 * allows the first N requests of a path to answer with an error status. Truncated or stalled bodies are simulated
 * with {@link RawHttpServer}, because {@link HttpServer} keeps connections alive.
 */
public final class FakeHttpServer implements AutoCloseable {

    private record Route(int status, byte[] body, String contentType) {
    }

    private final HttpServer server;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failuresLeft = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureStatus = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();

    /**
     * Starts a server on an ephemeral port.
     *
     * @throws IOException when binding fails
     */
    public FakeHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            final Thread t = new Thread(r, "fake-http");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", this::handle);
        server.start();
    }

    /** @return base URI ending with a slash */
    public URI base() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    /**
     * @param path path without leading slash
     * @return absolute URI
     */
    public URI url(final String path) {
        return base().resolve(path.startsWith("/") ? path.substring(1) : path);
    }

    /**
     * Registers binary content.
     *
     * @param path path (with or without leading slash)
     * @param body body
     */
    public void add(final String path, final byte[] body) {
        add(path, body, "application/octet-stream");
    }

    /**
     * Registers binary content with a content type.
     *
     * @param path        path
     * @param body        body
     * @param contentType content type
     */
    public void add(final String path, final byte[] body, final String contentType) {
        routes.put(normalise(path), new Route(200, body, contentType));
    }

    /**
     * Registers JSON content.
     *
     * @param path path
     * @param json JSON text
     */
    public void addJson(final String path, final String json) {
        add(path, json.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    /**
     * Registers plain text content.
     *
     * @param path path
     * @param text text
     */
    public void addText(final String path, final String text) {
        add(path, text.getBytes(StandardCharsets.UTF_8), "text/plain");
    }

    /**
     * Registers a fixed status response.
     *
     * @param path   path
     * @param status status code
     * @param body   body
     */
    public void addStatus(final String path, final int status, final String body) {
        routes.put(normalise(path), new Route(status, body.getBytes(StandardCharsets.UTF_8), "application/json"));
    }

    /**
     * Removes a route (subsequent requests get 404).
     *
     * @param path path
     */
    public void remove(final String path) {
        routes.remove(normalise(path));
    }

    /**
     * Makes the first N requests to a path fail with a status.
     *
     * @param path   path
     * @param times  number of failing requests
     * @param status status code
     */
    public void failFirst(final String path, final int times, final int status) {
        failuresLeft.put(normalise(path), new AtomicInteger(times));
        failureStatus.put(normalise(path), status);
    }

    /**
     * @param path path
     * @return number of requests seen for the path
     */
    public int hits(final String path) {
        final AtomicInteger n = hits.get(normalise(path));
        return n == null ? 0 : n.get();
    }

    /** @return total requests */
    public int totalHits() {
        return requests.size();
    }

    /** @return {@code METHOD /path} entries in order */
    public List<String> requests() {
        return List.copyOf(requests);
    }

    private void handle(final HttpExchange exchange) throws IOException {
        final String path = exchange.getRequestURI().getPath();
        requests.add(exchange.getRequestMethod() + " " + path);
        hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            final Route route = routes.get(path);
            if (route == null) {
                final byte[] body = ("not found: " + path).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, body.length);
                exchange.getResponseBody().write(body);
                return;
            }
            final AtomicInteger fail = failuresLeft.get(path);
            if (fail != null && fail.getAndDecrement() > 0) {
                final byte[] body = "injected failure".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(failureStatus.getOrDefault(path, 500), body.length);
                exchange.getResponseBody().write(body);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", route.contentType());
            exchange.sendResponseHeaders(route.status(), route.body().length == 0 ? -1 : route.body().length);
            if (route.body().length > 0) {
                exchange.getResponseBody().write(route.body());
            }
        }
    }

    private static String normalise(final String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
