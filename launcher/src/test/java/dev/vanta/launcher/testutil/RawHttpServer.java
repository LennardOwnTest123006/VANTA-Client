package dev.vanta.launcher.testutil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal raw-socket HTTP/1.1 server used to simulate misbehaving servers that {@code com.sun.net.httpserver}
 * cannot express: a response whose connection is cut after half the body, or a body that stalls forever.
 * Each accepted connection consumes the next {@link Behaviour}; the last one repeats.
 */
public final class RawHttpServer implements AutoCloseable {

    /** What to do with a connection. */
    public enum Behaviour {
        /** Send headers with the full Content-Length, half the body, then close the socket. */
        TRUNCATE,
        /** Send headers with the full Content-Length, half the body, then stop sending until closed. */
        STALL,
        /** Serve the whole body correctly. */
        FULL
    }

    private final ServerSocket socket;
    private final byte[] body;
    private final Deque<Behaviour> behaviours = new ConcurrentLinkedDeque<>();
    private final AtomicInteger connections = new AtomicInteger();
    private final CountDownLatch stop = new CountDownLatch(1);
    private final Thread acceptor;

    /**
     * @param body       body to serve
     * @param behaviours per-connection behaviours (last one repeats)
     * @throws IOException when binding fails
     */
    public RawHttpServer(final byte[] body, final List<Behaviour> behaviours) throws IOException {
        this.body = body.clone();
        this.behaviours.addAll(behaviours);
        this.socket = new ServerSocket();
        this.socket.bind(new InetSocketAddress("127.0.0.1", 0));
        this.acceptor = new Thread(this::acceptLoop, "raw-http-acceptor");
        this.acceptor.setDaemon(true);
        this.acceptor.start();
    }

    /** @return URL of the single resource this server serves */
    public URI url() {
        return URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/file.bin");
    }

    /** @return number of connections accepted so far */
    public int connections() {
        return connections.get();
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try {
                final Socket client = socket.accept();
                connections.incrementAndGet();
                final Behaviour behaviour = behaviours.size() > 1 ? behaviours.poll() : behaviours.peek();
                final Thread t = new Thread(() -> serve(client, behaviour == null ? Behaviour.FULL : behaviour), "raw-http-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return; // closed
            }
        }
    }

    private void serve(final Socket client, final Behaviour behaviour) {
        try (client) {
            client.setSoTimeout(10_000);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // consume request line and headers
            }
            final OutputStream out = client.getOutputStream();
            final String headers = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
            switch (behaviour) {
                case FULL -> out.write(body);
                case TRUNCATE -> out.write(body, 0, body.length / 2);
                case STALL -> {
                    out.write(body, 0, body.length / 2);
                    out.flush();
                    stop.await();
                }
            }
            out.flush();
        } catch (IOException | InterruptedException ignored) {
            // connection torn down
        }
    }

    @Override
    public void close() {
        stop.countDown();
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
