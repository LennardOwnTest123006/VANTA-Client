package dev.vanta.core.ai;

import dev.vanta.core.config.CoreLog;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * The {@code llama-server} child process: started on demand on {@code 127.0.0.1} and a free port with
 * <pre>
 * llama-server -m &lt;model&gt; --host 127.0.0.1 --port &lt;p&gt; -c &lt;ctx&gt; -t &lt;threads&gt; -ngl 0 --jinja --reasoning-budget 0 --no-webui
 * </pre>
 * (every flag checked against the llama-server README of the pinned release), waited for until {@code GET /health}
 * answers 200, stopped after an idle timeout, on {@link #close()} and by a JVM shutdown hook. Its output goes to
 * {@code logs/llama-server.log}. All work runs on the injected worker executor; status changes and chat results are
 * delivered through the main-thread executor, so nothing here ever blocks the render thread.
 */
public final class LocalAiRuntime implements ChatBackend, AutoCloseable {
    /** Loopback host the server binds to. The AI never listens anywhere else. */
    public static final String HOST = "127.0.0.1";
    /** Largest server log kept between starts; a bigger one is truncated before the next start. */
    public static final long MAX_LOG_BYTES = 5L << 20;

    /** Creates the child process (injectable for tests). */
    public interface ProcessFactory {
        /**
         * Starts the server.
         *
         * @param command    the full command line
         * @param workingDir the runtime directory
         * @param logFile    where stdout and stderr go
         */
        Process start(List<String> command, Path workingDir, Path logFile) throws IOException;

        /** The real thing: {@link ProcessBuilder} with output appended to the log file. */
        static ProcessFactory system() {
            return (command, workingDir, logFile) -> {
                Path parent = logFile.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (Files.isRegularFile(logFile) && Files.size(logFile) > MAX_LOG_BYTES) {
                    Files.delete(logFile);
                }
                ProcessBuilder builder = new ProcessBuilder(command).directory(workingDir.toFile())
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
                return builder.start();
            };
        }
    }

    /** Sleeps between health polls (injectable for tests). */
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;

        static Sleeper system() {
            return duration -> Thread.sleep(Math.max(0L, duration.toMillis()));
        }
    }

    /** Observer of status changes (called on the main-thread executor). */
    public interface Listener {
        void onStatusChanged(LocalAiStatus status, String reason);
    }

    /**
     * Start and idle settings.
     *
     * @param threads        {@code -t}
     * @param contextSize    {@code -c}
     * @param startTimeout   how long the server may take to answer {@code /health}
     * @param healthInterval pause between health polls
     * @param idleTimeout    stop the server after this much time without a question
     * @param keepRunning    never stop for idleness
     * @param shutdownHook   register a JVM shutdown hook that kills the process
     */
    public record Config(int threads, int contextSize, Duration startTimeout, Duration healthInterval,
                         Duration idleTimeout, boolean keepRunning, boolean shutdownHook) {
        public Config {
            Objects.requireNonNull(startTimeout, "startTimeout");
            Objects.requireNonNull(healthInterval, "healthInterval");
            Objects.requireNonNull(idleTimeout, "idleTimeout");
            if (threads < 1) {
                throw new IllegalArgumentException("threads must be at least 1");
            }
            if (contextSize < 512) {
                throw new IllegalArgumentException("contextSize must be at least 512");
            }
        }

        /** Defaults: 3 minutes to start, health poll every 250 ms, 10 minutes idle, shutdown hook on. */
        public static Config defaults(int threads, int contextSize) {
            return new Config(threads, contextSize, Duration.ofMinutes(3), Duration.ofMillis(250),
                    Duration.ofMinutes(10), false, true);
        }

        public Config withIdle(Duration timeout, boolean keep) {
            return new Config(threads, contextSize, startTimeout, healthInterval, timeout, keep, shutdownHook);
        }

        public Config withThreads(int count) {
            return new Config(count, contextSize, startTimeout, healthInterval, idleTimeout, keepRunning, shutdownHook);
        }

        public Config withShutdownHook(boolean enabled) {
            return new Config(threads, contextSize, startTimeout, healthInterval, idleTimeout, keepRunning, enabled);
        }
    }

    private final Path serverExecutable;
    private final Path modelFile;
    private final Path logFile;
    private final Path workingDir;
    private final ProcessFactory processFactory;
    private final IntSupplier ports;
    private final HttpClient http;
    private final Sleeper sleeper;
    private final Executor worker;
    private final Executor mainThread;
    private final Clock clock;
    private final Duration chatTimeout;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Config config;
    private volatile LocalAiStatus status = LocalAiStatus.INSTALLED;
    private volatile String statusReason = "";
    private volatile Process process;
    private volatile int port = -1;
    private volatile long lastUsedAt;
    private volatile boolean closed;
    private Thread shutdownHook;

    public LocalAiRuntime(Path serverExecutable, Path modelFile, Path logFile, Config config,
                          ProcessFactory processFactory, IntSupplier ports, HttpClient http, Sleeper sleeper,
                          Executor worker, Executor mainThread, Clock clock) {
        this(serverExecutable, modelFile, logFile, config, processFactory, ports, http, sleeper, worker, mainThread,
                clock, LocalAiClient.DEFAULT_TIMEOUT);
    }

    public LocalAiRuntime(Path serverExecutable, Path modelFile, Path logFile, Config config,
                          ProcessFactory processFactory, IntSupplier ports, HttpClient http, Sleeper sleeper,
                          Executor worker, Executor mainThread, Clock clock, Duration chatTimeout) {
        this.serverExecutable = Objects.requireNonNull(serverExecutable, "serverExecutable").toAbsolutePath();
        this.modelFile = Objects.requireNonNull(modelFile, "modelFile").toAbsolutePath();
        this.logFile = Objects.requireNonNull(logFile, "logFile").toAbsolutePath();
        Path dir = this.serverExecutable.getParent();
        this.workingDir = dir == null ? this.serverExecutable : dir;
        this.config = Objects.requireNonNull(config, "config");
        this.processFactory = Objects.requireNonNull(processFactory, "processFactory");
        this.ports = Objects.requireNonNull(ports, "ports");
        this.http = Objects.requireNonNull(http, "http");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.chatTimeout = Objects.requireNonNull(chatTimeout, "chatTimeout");
        this.lastUsedAt = clock.millis();
    }

    /** {@code max(2, min(8, availableProcessors - 2))}. */
    public static int defaultThreads(int availableProcessors) {
        return Math.max(2, Math.min(8, availableProcessors - 2));
    }

    /** A free loopback TCP port chosen by the OS. */
    public static int freePort() {
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("no free loopback port", e);
        }
    }

    /** The command line for a port. */
    public List<String> command(int serverPort) {
        Config c = config;
        List<String> command = new ArrayList<>();
        command.add(serverExecutable.toString());
        command.add("-m");
        command.add(modelFile.toString());
        command.add("--host");
        command.add(HOST);
        command.add("--port");
        command.add(Integer.toString(serverPort));
        command.add("-c");
        command.add(Integer.toString(c.contextSize()));
        command.add("-t");
        command.add(Integer.toString(c.threads()));
        command.add("-ngl");
        command.add("0");
        command.add("--jinja");
        command.add("--reasoning-budget");
        command.add("0");
        command.add("--no-webui");
        return command;
    }

    // ---- state ----------------------------------------------------------------------------------------------------

    public LocalAiStatus status() {
        return status;
    }

    /** Why the status is {@code FAILED} (empty otherwise). */
    public String statusReason() {
        return statusReason;
    }

    /** The port the running server listens on. */
    public OptionalInt port() {
        return port > 0 && status.isRunning() ? OptionalInt.of(port) : OptionalInt.empty();
    }

    /** True while the process is alive (starting, ready or busy). */
    public boolean isRunning() {
        return status.isRunning();
    }

    /** Last time the server reached READY or answered a question. */
    public long lastUsedAt() {
        return lastUsedAt;
    }

    public Config config() {
        return config;
    }

    /** Applies new settings; threads and context size take effect at the next start. */
    public void setConfig(Config next) {
        this.config = Objects.requireNonNull(next, "next");
    }

    public Path logFile() {
        return logFile;
    }

    /** Registers a listener; the returned runnable removes it. */
    public Runnable addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------------

    /** Starts the server in the background (no-op while it runs). */
    public void start() {
        if (closed) {
            return;
        }
        worker.execute(this::ensureStarted);
    }

    /** Stops the server in the background. */
    public void stop() {
        worker.execute(() -> {
            destroyProcess();
            if (status != LocalAiStatus.FAILED) {
                setStatus(LocalAiStatus.INSTALLED, "");
            }
        });
    }

    /** Called once per client tick on the render thread: stops an idle server. */
    public void tick(long nowMillis) {
        Config c = config;
        if (status == LocalAiStatus.READY && !c.keepRunning() && nowMillis - lastUsedAt >= c.idleTimeout().toMillis()) {
            CoreLog.info("Local AI idle for {} min; stopping llama-server", c.idleTimeout().toMinutes());
            stop();
        }
    }

    @Override
    public void chat(LocalAiClient.ChatRequest request, Consumer<LocalAiClient.ChatResponse> onReply,
                     Consumer<LocalAiException> onError) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onReply, "onReply");
        Objects.requireNonNull(onError, "onError");
        if (closed) {
            mainThread.execute(() -> onError.accept(new LocalAiException(LocalAiException.Kind.PROCESS,
                    "the Local AI was shut down")));
            return;
        }
        worker.execute(() -> {
            if (!ensureStarted()) {
                LocalAiException failure = new LocalAiException(LocalAiException.Kind.PROCESS, statusReason);
                mainThread.execute(() -> onError.accept(failure));
                return;
            }
            setStatus(LocalAiStatus.BUSY, "");
            LocalAiClient client = new LocalAiClient(http, LocalAiClient.loopback(port), chatTimeout);
            try {
                LocalAiClient.ChatResponse response = client.chat(request);
                lastUsedAt = clock.millis();
                setStatus(LocalAiStatus.READY, "");
                mainThread.execute(() -> onReply.accept(response));
            } catch (LocalAiException e) {
                lastUsedAt = clock.millis();
                Process p = process;
                if (p != null && !p.isAlive()) {
                    destroyProcess();
                    setStatus(LocalAiStatus.FAILED, "llama-server exited while answering (see " + logFile.getFileName()
                            + ")");
                } else {
                    setStatus(LocalAiStatus.READY, "");
                }
                mainThread.execute(() -> onError.accept(e));
            }
        });
    }

    /** Stops the process synchronously and removes the shutdown hook. */
    @Override
    public void close() {
        closed = true;
        destroyProcess();
        status = LocalAiStatus.INSTALLED;
        synchronized (this) {
            if (shutdownHook != null) {
                try {
                    java.lang.Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException | SecurityException ignored) {
                    // the JVM is already shutting down; the hook runs on its own
                }
                shutdownHook = null;
            }
        }
    }

    // ---- worker side ---------------------------------------------------------------------------------------------

    /** Worker thread: starts the server when it is not running; true when READY. */
    boolean ensureStarted() {
        Process p = process;
        if (p != null && p.isAlive() && (status == LocalAiStatus.READY || status == LocalAiStatus.BUSY)) {
            return true;
        }
        if (p != null) {
            destroyProcess();
        }
        if (closed) {
            setStatus(LocalAiStatus.INSTALLED, "");
            return false;
        }
        setStatus(LocalAiStatus.STARTING, "");
        int chosenPort;
        Process started;
        try {
            chosenPort = ports.getAsInt();
            installShutdownHook();
            started = processFactory.start(command(chosenPort), workingDir, logFile);
        } catch (IOException | RuntimeException e) {
            setStatus(LocalAiStatus.FAILED, "could not start llama-server: " + e.getMessage());
            CoreLog.warn(e, "Could not start llama-server from {}", serverExecutable);
            return false;
        }
        process = started;
        port = chosenPort;
        LocalAiClient client = new LocalAiClient(http, LocalAiClient.loopback(chosenPort), Duration.ofSeconds(5));
        long startedAt = clock.millis();
        Config c = config;
        while (true) {
            if (!started.isAlive()) {
                int code = exitCode(started);
                process = null;
                setStatus(LocalAiStatus.FAILED, "llama-server exited with code " + code + " before it was ready (see "
                        + logFile.getFileName() + ")");
                return false;
            }
            if (client.healthStatus() == 200) {
                lastUsedAt = clock.millis();
                setStatus(LocalAiStatus.READY, "");
                CoreLog.info("llama-server ready on {}:{} after {} ms", HOST, chosenPort, clock.millis() - startedAt);
                return true;
            }
            if (clock.millis() - startedAt > c.startTimeout().toMillis()) {
                destroyProcess();
                setStatus(LocalAiStatus.FAILED, "llama-server did not become ready within "
                        + c.startTimeout().toSeconds() + " s (see " + logFile.getFileName() + ")");
                return false;
            }
            try {
                sleeper.sleep(c.healthInterval());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                destroyProcess();
                setStatus(LocalAiStatus.FAILED, "start interrupted");
                return false;
            }
        }
    }

    private static int exitCode(Process p) {
        try {
            return p.exitValue();
        } catch (IllegalThreadStateException e) {
            return -1;
        }
    }

    private void installShutdownHook() {
        if (!config.shutdownHook()) {
            return;
        }
        synchronized (this) {
            if (shutdownHook != null) {
                return;
            }
            Thread hook = new Thread(this::destroyProcess, "VANTA Local AI shutdown");
            try {
                java.lang.Runtime.getRuntime().addShutdownHook(hook);
                shutdownHook = hook;
            } catch (IllegalStateException | SecurityException e) {
                CoreLog.debug("Could not register the Local AI shutdown hook: {}", e.toString());
            }
        }
    }

    /** Kills the process if any (any thread). */
    void destroyProcess() {
        Process p = process;
        process = null;
        port = -1;
        if (p == null) {
            return;
        }
        if (p.isAlive()) {
            p.destroy();
            try {
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
        }
    }

    private void setStatus(LocalAiStatus next, String reason) {
        status = next;
        statusReason = reason == null ? "" : reason;
        String r = statusReason;
        mainThread.execute(() -> {
            for (Listener listener : listeners) {
                try {
                    listener.onStatusChanged(next, r);
                } catch (RuntimeException e) {
                    CoreLog.warn(e, "Local AI status listener failed");
                }
            }
        });
    }
}
