package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.log.Redactor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A running game. Standard output and error are pumped line by line to listeners and to
 * {@code logs/game-<timestamp>.log} with secrets redacted.
 */
public final class GameProcess {

    /**
     * One output line.
     *
     * @param stream {@code stdout} or {@code stderr}
     * @param text   line text (redacted)
     */
    public record Line(String stream, String text) {
    }

    private final Process process;
    private final Path logFile;
    private final List<Consumer<Line>> listeners = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Integer> exitCode = new CompletableFuture<>();
    private final Redactor redactor;

    GameProcess(final Process process, final Path logFile, final Redactor redactor) {
        this.process = Objects.requireNonNull(process, "process");
        this.logFile = Objects.requireNonNull(logFile, "logFile");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
    }

    /**
     * Starts a process and begins pumping its output.
     *
     * @param builder   configured builder
     * @param logFile   game log file
     * @param redactor  redactor for secrets
     * @param listeners initial listeners
     * @return process wrapper
     * @throws IOException when the process cannot be started
     */
    static GameProcess start(final ProcessBuilder builder, final Path logFile, final Redactor redactor,
                             final List<Consumer<Line>> listeners) throws IOException {
        Files.createDirectories(logFile.toAbsolutePath().getParent());
        final Process process = builder.start();
        final GameProcess gp = new GameProcess(process, logFile, redactor);
        gp.listeners.addAll(listeners);
        gp.pump();
        return gp;
    }

    private void pump() {
        final BufferedWriter writer;
        try {
            writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open game log " + logFile, e);
        }
        final Thread out = pumpThread("stdout", process.getInputStream(), writer);
        final Thread err = pumpThread("stderr", process.getErrorStream(), writer);
        final Thread waiter = new Thread(() -> {
            try {
                final int code = process.waitFor();
                out.join();
                err.join();
                synchronized (writer) {
                    writer.write("[launcher] process exited with code " + code);
                    writer.newLine();
                    writer.close();
                }
                exitCode.complete(code);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exitCode.completeExceptionally(e);
            } catch (IOException e) {
                exitCode.complete(process.exitValue());
            }
        }, "vanta-game-waiter");
        waiter.setDaemon(true);
        waiter.start();
    }

    private Thread pumpThread(final String stream, final InputStream in, final BufferedWriter writer) {
        final Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String safe = redactor.apply(line);
                    synchronized (writer) {
                        writer.write(safe);
                        writer.newLine();
                        writer.flush();
                    }
                    final Line l = new Line(stream, safe);
                    for (Consumer<Line> listener : listeners) {
                        try {
                            listener.accept(l);
                        } catch (RuntimeException ignored) {
                            // a misbehaving listener must not stop log streaming
                        }
                    }
                }
            } catch (IOException ignored) {
                // stream closed by process exit
            }
        }, "vanta-game-" + stream);
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Registers an output listener.
     *
     * @param listener listener
     */
    public void addListener(final Consumer<Line> listener) {
        listeners.add(listener);
    }

    /** @return completes with the exit code */
    public CompletableFuture<Integer> exitCode() {
        return exitCode;
    }

    /** @return game log file */
    public Path logFile() {
        return logFile;
    }

    /** @return whether the game is still running */
    public boolean isAlive() {
        return process.isAlive();
    }

    /** @return process id */
    public long pid() {
        return process.pid();
    }

    /** Asks the game to exit. */
    public void destroy() {
        process.destroy();
    }

    /** Kills the game. */
    public void destroyForcibly() {
        process.destroyForcibly();
    }
}
