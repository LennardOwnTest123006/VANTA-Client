package dev.vanta.launcher.core.net;

import dev.vanta.launcher.LauncherVersion;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;

/**
 * {@link HttpTransport} backed by {@link java.net.http.HttpClient}: HTTP/2 where available, 30 second connect
 * timeout, redirects followed, {@code User-Agent: VANTA-Launcher/<version>} unless the request sets its own
 * {@code User-Agent}.
 */
public final class JdkHttpTransport implements HttpTransport {

    /** Connect timeout. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;
    private final String userAgent;

    /**
     * Creates a transport with the default user agent.
     */
    public JdkHttpTransport() {
        this(LauncherVersion.USER_AGENT);
    }

    /**
     * @param userAgent user agent header value
     */
    public JdkHttpTransport(final String userAgent) {
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public HttpResult execute(final HttpRequestSpec spec) throws IOException, InterruptedException {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(spec.uri())
            .timeout(spec.readTimeout())
            .header("User-Agent", userAgent);
        for (Map.Entry<String, String> h : spec.headers().entrySet()) {
            // setHeader: a request header (for example the User-Agent Modrinth asks for) replaces the default one.
            builder.setHeader(h.getKey(), h.getValue());
        }
        final byte[] body = spec.body();
        final HttpRequest.BodyPublisher publisher = body.length == 0
            ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body);
        builder.method(spec.method(), publisher);
        final HttpResponse<java.io.InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        final OptionalLong length = response.headers().firstValueAsLong("Content-Length");
        return new HttpResult(response.statusCode(), response.headers().map(), response.body(), length.orElse(-1L));
    }

    @Override
    public void close() {
        client.close();
    }
}
