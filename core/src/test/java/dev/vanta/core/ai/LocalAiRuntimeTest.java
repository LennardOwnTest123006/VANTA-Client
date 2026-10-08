package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.vanta.core.config.MutableClock;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The runtime with a fake process (the "server" is a loopback HTTP server answering /health and chat completions)
 * and direct executors, so every step runs synchronously in the test.
 */
class LocalAiRuntimeTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Executor DIRECT = Runnable::run;
    private static final String CANNED = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
            + "\"content\":\"{\\\"message\\\":\\\"done\\\",\\\"actions\\\":[]}\"}}],\"usage\":{\"prompt_tokens\":3,"
            + "\"completion_tokens\":2}}";

    @TempDir
    Path dir;
    LocalFileServer server;
    final MutableClock clock = MutableClock.standard();
    final FakeProcess.Factory processes = new FakeProcess.Factory();
    final List<String> statuses = new ArrayList<>();
    Path exe;
    Path model;

    @BeforeEach
    void start() throws IOException {
        server = new LocalFileServer();
        server.json(LocalAiClient.CHAT_PATH, body -> CANNED);
        exe = dir.resolve("runtime").resolve("b11429").resolve("linux-x64").resolve("build").resolve("bin")
                .resolve("llama-server");
        model = dir.resolve("models").resolve("model.gguf");
        Files.createDirectories(exe.getParent());
        Files.createDirectories(model.getParent());
        Files.writeString(exe, "fake");
        Files.writeString(model, "fake");
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private LocalAiRuntime runtime(LocalAiRuntime.Config config) {
        LocalAiRuntime runtime = new LocalAiRuntime(exe, model, dir.resolve("logs").resolve("llama-server.log"),
                config, processes, server::port, HTTP, duration -> clock.advance(duration.toMillis()), DIRECT,
                DIRECT, clock);
        runtime.addListener((status, reason) -> statuses.add(status.id() + (reason.isEmpty() ? "" : ":" + reason)));
        return runtime;
    }

    private static LocalAiRuntime.Config config() {
        return LocalAiRuntime.Config.defaults(4, 4096).withShutdownHook(false);
    }

    private static LocalAiClient.ChatRequest request() {
        return LocalAiClient.ChatRequest.of(List.of(LocalAiClient.Message.system("s"),
                LocalAiClient.Message.user("u")), new JsonObject());
    }

    @Test
    void startsTheServerWithTheDocumentedFlagsAndWaitsForHealth() {
        server.healthFailuresLeft = 2;
        LocalAiRuntime runtime = runtime(config());
        assertEquals(LocalAiStatus.INSTALLED, runtime.status());
        runtime.start();
        assertEquals(LocalAiStatus.READY, runtime.status());
        assertEquals(server.port(), runtime.port().orElseThrow());
        assertEquals(List.of("starting", "ready"), statuses);
        assertEquals(3, server.requestCount(LocalAiClient.HEALTH_PATH), "two 503s, then 200");

        List<String> command = processes.commands.get(0);
        assertEquals(exe.toString(), command.get(0));
        assertEquals(List.of(exe.toString(), "-m", model.toString(), "--host", "127.0.0.1", "--port",
                Integer.toString(server.port()), "-c", "4096", "-t", "4", "-ngl", "0", "--jinja",
                "--reasoning-budget", "0", "--no-webui"), command);
        assertEquals(dir.resolve("logs").resolve("llama-server.log"), processes.logFiles.get(0));
        runtime.close();
        assertEquals(1, processes.last().destroyCalls);
        assertFalse(runtime.isRunning());
    }

    @Test
    void chatStartsOnDemandAndDeliversTheReply() throws LocalAiException {
        LocalAiRuntime runtime = runtime(config());
        List<LocalAiClient.ChatResponse> replies = new ArrayList<>();
        List<LocalAiException> errors = new ArrayList<>();
        runtime.chat(request(), replies::add, errors::add);
        assertEquals(1, replies.size(), errors.toString());
        assertTrue(errors.isEmpty());
        assertEquals("done", replies.get(0).contentAsJson().get("message").getAsString());
        assertEquals(LocalAiStatus.READY, runtime.status());
        assertEquals(List.of("starting", "ready", "busy", "ready"), statuses);
        assertEquals(1, processes.processes.size());
        runtime.chat(request(), replies::add, errors::add);
        assertEquals(2, replies.size());
        assertEquals(1, processes.processes.size(), "the running server is reused");
        runtime.close();
    }

    @Test
    void stopsAfterTheIdleTimeoutAndRestartsOnTheNextQuestion() {
        LocalAiRuntime runtime = runtime(config().withIdle(Duration.ofMinutes(10), false));
        runtime.start();
        FakeProcess first = processes.last();
        clock.advance(Duration.ofMinutes(9).toMillis());
        runtime.tick(clock.millis());
        assertTrue(first.alive, "not idle long enough");
        clock.advance(Duration.ofMinutes(1).toMillis() + 1);
        runtime.tick(clock.millis());
        assertFalse(first.alive, "destroyed after 10 idle minutes");
        assertEquals(LocalAiStatus.INSTALLED, runtime.status());
        assertTrue(runtime.port().isEmpty());

        List<LocalAiClient.ChatResponse> replies = new ArrayList<>();
        runtime.chat(request(), replies::add, e -> {
            throw new AssertionError(e);
        });
        assertEquals(1, replies.size());
        assertEquals(2, processes.processes.size(), "a new process after the idle stop");
        runtime.close();
    }

    @Test
    void keepRunningDisablesTheIdleStop() {
        LocalAiRuntime runtime = runtime(config().withIdle(Duration.ofMinutes(1), true));
        runtime.start();
        clock.advance(Duration.ofHours(2).toMillis());
        runtime.tick(clock.millis());
        assertTrue(processes.last().alive);
        assertEquals(LocalAiStatus.READY, runtime.status());
        runtime.close();
    }

    @Test
    void aProcessThatExitsEarlyFails() {
        processes.exitImmediatelyWith = 3;
        LocalAiRuntime runtime = runtime(config());
        List<LocalAiException> errors = new ArrayList<>();
        runtime.chat(request(), r -> {
            throw new AssertionError("no reply expected");
        }, errors::add);
        assertEquals(1, errors.size());
        assertEquals(LocalAiException.Kind.PROCESS, errors.get(0).kind());
        assertEquals(LocalAiStatus.FAILED, runtime.status());
        assertTrue(runtime.statusReason().contains("exited with code 3"), runtime.statusReason());
        assertTrue(runtime.statusReason().contains("llama-server.log"));
        runtime.close();
    }

    @Test
    void aServerThatNeverAnswersHealthTimesOut() {
        server.healthFailuresLeft = Integer.MAX_VALUE;
        LocalAiRuntime runtime = runtime(new LocalAiRuntime.Config(2, 4096, Duration.ofSeconds(30),
                Duration.ofSeconds(1), Duration.ofMinutes(10), false, false));
        runtime.start();
        assertEquals(LocalAiStatus.FAILED, runtime.status());
        assertTrue(runtime.statusReason().contains("did not become ready within 30 s"), runtime.statusReason());
        assertFalse(processes.last().alive, "the stuck process is killed");
        runtime.close();
    }

    @Test
    void aFailingStartIsReported() {
        processes.failWith = new IOException("permission denied");
        LocalAiRuntime runtime = runtime(config());
        runtime.start();
        assertEquals(LocalAiStatus.FAILED, runtime.status());
        assertTrue(runtime.statusReason().contains("permission denied"));
        runtime.close();
    }

    @Test
    void threadDefaultsFollowTheSpec() {
        assertEquals(2, LocalAiRuntime.defaultThreads(1));
        assertEquals(2, LocalAiRuntime.defaultThreads(4));
        assertEquals(4, LocalAiRuntime.defaultThreads(6));
        assertEquals(8, LocalAiRuntime.defaultThreads(10));
        assertEquals(8, LocalAiRuntime.defaultThreads(64));
        int port = LocalAiRuntime.freePort();
        assertTrue(port > 0 && port < 65536);
    }
}
