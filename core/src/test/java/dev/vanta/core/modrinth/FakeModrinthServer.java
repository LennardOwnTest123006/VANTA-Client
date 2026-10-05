package dev.vanta.core.modrinth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local stand-in for {@code api.modrinth.com} and {@code cdn.modrinth.com} on the JDK's {@link HttpServer}: routes by
 * path, records every request (method, path, query, User-Agent, body) and can answer the first N requests of a path
 * with HTTP 429 and a {@code Retry-After} header.
 */
public final class FakeModrinthServer implements AutoCloseable {

    /** One recorded request. */
    public record Request(String method, String path, String query, String userAgent, String body) {
    }

    private record Route(int status, byte[] body, String contentType) {
    }

    private record Limit(AtomicInteger left, String retryAfter, String reset) {
    }

    private final HttpServer server;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private final Map<String, Limit> limits = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public FakeModrinthServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-modrinth");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", this::handle);
        server.start();
    }

    /** Base URI ending with a slash. */
    public URI base() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    /** API base ({@code <base>v2/}). */
    public URI apiBase() {
        return base().resolve("v2/");
    }

    /** Absolute URL of a path on this server. */
    public String url(String path) {
        return base().resolve(path.startsWith("/") ? path.substring(1) : path).toString();
    }

    /** A client against this server: HTTP downloads allowed, no real sleeping (waits are recorded). */
    public ModrinthClient client(List<Duration> sleeps) {
        ModrinthClient.Config config = ModrinthClient.Config.defaults("1.1.0").withApiBase(apiBase())
                .withInsecureDownloads(true);
        return new ModrinthClient(config, java.net.http.HttpClient.newHttpClient(), sleeps::add, Clock.systemUTC());
    }

    public FakeModrinthServer json(String path, String json) {
        routes.put(normal(path), new Route(200, json.getBytes(StandardCharsets.UTF_8), "application/json"));
        return this;
    }

    public FakeModrinthServer file(String path, byte[] content) {
        routes.put(normal(path), new Route(200, content, "application/java-archive"));
        return this;
    }

    public FakeModrinthServer status(String path, int status, String body) {
        routes.put(normal(path), new Route(status, body.getBytes(StandardCharsets.UTF_8), "application/json"));
        return this;
    }

    /** The first {@code times} requests of {@code path} answer 429 with the given headers (null = header absent). */
    public FakeModrinthServer rateLimit(String path, int times, String retryAfter, String reset) {
        limits.put(normal(path), new Limit(new AtomicInteger(times), retryAfter, reset));
        return this;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    /** Requests of one path. */
    public List<Request> requests(String path) {
        List<Request> out = new ArrayList<>();
        for (Request r : requests) {
            if (r.path().equals(normal(path))) {
                out.add(r);
            }
        }
        return out;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("User-Agent"), body));
        try (exchange) {
            Limit limit = limits.get(path);
            if (limit != null && limit.left().getAndDecrement() > 0) {
                if (limit.retryAfter() != null) {
                    exchange.getResponseHeaders().add("Retry-After", limit.retryAfter());
                }
                if (limit.reset() != null) {
                    exchange.getResponseHeaders().add("X-Ratelimit-Reset", limit.reset());
                }
                byte[] msg = "{\"error\":\"ratelimited\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(429, msg.length);
                exchange.getResponseBody().write(msg);
                return;
            }
            Route route = routes.get(path);
            if (route == null) {
                byte[] msg = "{\"error\":\"not_found\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, msg.length);
                exchange.getResponseBody().write(msg);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", route.contentType());
            exchange.sendResponseHeaders(route.status(), route.body().length == 0 ? -1 : route.body().length);
            if (route.body().length > 0) {
                exchange.getResponseBody().write(route.body());
            }
        }
    }

    private static String normal(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
