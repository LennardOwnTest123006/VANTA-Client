package dev.vanta.core.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A loopback HTTP server for the installer, client and runtime tests: serves byte arrays by path, answers JSON for
 * POSTs, records every request and can be told to fail a path with a status code or to answer /health with 503 a
 * number of times first.
 */
final class LocalFileServer implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final Map<String, Function<String, String>> jsonHandlers = new ConcurrentHashMap<>();
    final List<String> requests = new CopyOnWriteArrayList<>();
    final List<String> bodies = new CopyOnWriteArrayList<>();
    volatile int healthFailuresLeft;
    volatile boolean omitContentLength;

    LocalFileServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    String url(String path) {
        return baseUrl() + path;
    }

    void file(String path, byte[] content) {
        files.put(path, content);
    }

    void fail(String path, int status) {
        failures.put(path, status);
    }

    /** Answers POSTs to {@code path} with the JSON the handler returns for the request body. */
    void json(String path, Function<String, String> handler) {
        jsonHandlers.put(path, handler);
    }

    long requestCount(String path) {
        return requests.stream().filter(path::equals).count();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(path);
        try (exchange) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!body.isEmpty()) {
                bodies.add(body);
            }
            Integer failure = failures.get(path);
            if (failure != null) {
                exchange.sendResponseHeaders(failure, -1);
                return;
            }
            if (path.equals(LocalAiClient.HEALTH_PATH)) {
                if (healthFailuresLeft > 0) {
                    healthFailuresLeft--;
                    send(exchange, 503, "{\"error\":{\"code\":503,\"message\":\"Loading model\"}}");
                } else {
                    send(exchange, 200, "{\"status\":\"ok\"}");
                }
                return;
            }
            Function<String, String> handler = jsonHandlers.get(path);
            if (handler != null) {
                send(exchange, 200, handler.apply(body));
                return;
            }
            byte[] content = files.get(path);
            if (content == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, omitContentLength ? 0 : content.length);
            try (OutputStream out = exchange.getResponseBody()) {
                int offset = 0;
                while (offset < content.length) {
                    int chunk = Math.min(16 * 1024, content.length - offset);
                    out.write(content, offset, chunk);
                    offset += chunk;
                }
            }
        }
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
