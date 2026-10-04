package dev.vanta.launcher.core.net;

import java.io.IOException;

/**
 * Minimal HTTP transport. Production uses {@link JdkHttpTransport}; tests inject fakes that answer from recorded
 * JSON so no test ever touches the network.
 */
public interface HttpTransport extends AutoCloseable {

    /**
     * Executes a request. Non-2xx responses are returned, not thrown.
     *
     * @param request request
     * @return streaming result; the caller closes it
     * @throws IOException          on connection failure
     * @throws InterruptedException when interrupted
     */
    HttpResult execute(HttpRequestSpec request) throws IOException, InterruptedException;

    @Override
    default void close() {
    }
}
