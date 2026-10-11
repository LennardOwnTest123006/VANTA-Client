package dev.vanta.client.gametest;

import static dev.vanta.client.gametest.VantaClientGameTest.check;
import static dev.vanta.client.gametest.VantaClientGameTest.pollFor;
import static dev.vanta.client.gametest.VantaClientGameTest.step;
import static dev.vanta.client.gametest.VantaClientGameTest.warn;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.vanta.client.VantaRuntime;
import dev.vanta.core.VantaVersion;
import dev.vanta.core.ai.ChatBackend;
import dev.vanta.core.ai.InstallStep;
import dev.vanta.core.ai.LocalAiClient;
import dev.vanta.core.ai.LocalAiException;
import dev.vanta.core.ai.LocalAiInstalled;
import dev.vanta.core.ai.LocalAiInstaller;
import dev.vanta.core.ai.LocalAiManifest;
import dev.vanta.core.ai.LocalAiPaths;
import dev.vanta.core.ai.LocalAiRuntime;
import dev.vanta.core.ai.LocalAiService;
import dev.vanta.core.ai.LocalAiStatus;
import dev.vanta.core.ai.NexusReply;
import dev.vanta.core.ai.NexusTranscript;
import dev.vanta.core.ai.Platform;
import dev.vanta.core.bridge.OptionsBridge;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.crosshair.CrosshairShape;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.release.Sha256;
import dev.vanta.core.screen.VantaServices;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * The Local AI end to end in the real game (runs in the VANTA main menu, without a world).
 * <p>
 * First, always (from client 1.6.0 on), the instant command {@value #INSTANT_COMMAND} goes through the assistant
 * ({@code NexusAssistant.ask}, the path the Nexus chat uses): it must be answered before the call returns, within
 * {@value #INSTANT_LIMIT_MS} ms, by the instant commands and not the model (a counting backend sees no request and the
 * Local AI status does not move); afterwards the vanilla music volume is 0 and the crosshair a circle, and Undo puts
 * both back.
 * <p>
 * The rest runs only when {@value VantaRuntime#LOCAL_AI_DIR_ENV} names a prepared install (CI prepares it from the
 * committed manifest and the client wrote the launcher note from it at start-up); without the variable the step logs
 * that it is skipped.
 * <ol>
 *   <li>the service reports {@link LocalAiStatus#INSTALLED} for that directory, launcher-managed (read-only);</li>
 *   <li>{@code llama-server} is started through {@code LocalAiService.start()}; every status change is logged; the
 *       step waits up to {@value #READY_TIMEOUT_TICKS} ticks (5 minutes) for {@link LocalAiStatus#READY};</li>
 *   <li>several HUD widgets are made visible, then {@value #QUESTION}, which the instant commands do not understand,
 *       is sent through the assistant; the reply (message, applied and rejected actions, the transcript entry) is
 *       logged; the reply must come from the model, carry at least one applied action, and afterwards exactly FPS and
 *       coordinates may be visible, whatever action sequence the model chose. The request keeps its own
 *       {@code LocalAiClient.DEFAULT_TIMEOUT} (90 s); the step waits at most {@value #ANSWER_WAIT_MS} ms of real time,
 *       so a model that does not answer in time still fails the step with the timeout error. While it waits, the
 *       CI runner's cores go to the model instead of the software renderer: the game's Max Framerate is lowered to
 *       {@value #WAIT_FRAMERATE} and put back afterwards, and the test thread sleeps {@value #QUIET_POLL_MS} ms
 *       between single game ticks (the client game test runs the game in lock-step with the test thread, so the game
 *       renders no frame while the test thread sleeps). The 1.5.0 Performance-pack job failed here because the menu
 *       was rendered back to back in software on 4 cores next to the model; there is no world to pause in the menu;</li>
 *   <li>the runtime is stopped; its port must no longer accept connections;</li>
 *   <li>the installer runs against a local {@code com.sun.net.httpserver.HttpServer} on 127.0.0.1 serving the
 *       prepared model file and a tiny archive (zip or tar.gz as the platform's manifest entry says) built here around
 *       the real server executable at the manifest's {@code serverPath}, with a mirror manifest whose URLs, sizes and
 *       hashes point at those files, into a temporary client-managed directory: download, verification, extraction,
 *       {@code installed.json} and the executable bit are asserted, the quick check reports INSTALLED, then the
 *       install is removed and the directory must be gone.</li>
 * </ol>
 * Nothing contacts the internet; the only network traffic is loopback.
 */
final class LocalAiStep {
    /** Ticks to wait for READY (5 minutes). */
    static final int READY_TIMEOUT_TICKS = 6_000;
    /**
     * Real time to wait for the assistant's answer: the request's own timeout ({@code LocalAiClient.DEFAULT_TIMEOUT},
     * 90 s) plus a margin, so the request's timeout, not this wait, ends a turn the model does not answer.
     */
    static final long ANSWER_WAIT_MS = 120_000;
    /** Max Framerate while the step waits for the model (the game's own value is put back afterwards). */
    static final int WAIT_FRAMERATE = 20;
    /** Real time the test thread sleeps between two game ticks while it waits for the model. */
    static final long QUIET_POLL_MS = 250;
    /** The instant command of the fast check (from client 1.6.0 on). */
    static final String INSTANT_COMMAND = "make music off and the crosshair a circle";
    /** Longest time the instant command may take, measured around the call on the render thread. */
    static final long INSTANT_LIMIT_MS = 1_000;
    /** Ticks to wait for the local install (5 minutes; the model is copied from loopback and hashed twice). */
    static final int INSTALL_TIMEOUT_TICKS = 6_000;
    /** Ticks to wait for the server to stop. */
    static final int STOP_TIMEOUT_TICKS = 600;
    /**
     * The question of the end-to-end check: the 1.5.0 question "Only show FPS and coordinates" (one of the prompt's
     * examples) with words the instant commands do not understand ("nothing else"), so it really goes to
     * llama-server.
     */
    static final String QUESTION = "Only show FPS and coordinates, nothing else";
    /** Visible widgets the question must leave. */
    static final Set<HudWidgetType> EXPECTED = EnumSet.of(HudWidgetType.FPS, HudWidgetType.COORDINATES);
    private static final int BUFFER = 64 * 1024;

    private LocalAiStep() {
    }

    static void run(ClientGameTestContext context, VantaServices services) {
        instantCommand(context, services);
        String value = System.getenv(VantaRuntime.LOCAL_AI_DIR_ENV);
        if (value == null || value.isBlank()) {
            step("local ai: " + VantaRuntime.LOCAL_AI_DIR_ENV + " is not set, the Local AI step is skipped");
            return;
        }
        Path prepared = Path.of(value.trim()).toAbsolutePath().normalize();
        LocalAiService ai = services.localAi();
        String state = context.computeOnClient(client -> ai.status() + " (" + ai.statusReason() + ") in "
                + ai.paths().root() + (ai.isLauncherManaged() ? ", launcher-managed" : ", client-managed")
                + "; platform " + ai.platform().map(Platform::key).orElse("unsupported"));
        step("local ai: " + VantaRuntime.LOCAL_AI_DIR_ENV + "=" + prepared + "; status " + state);
        check(context.computeOnClient(client -> ai.status() == LocalAiStatus.INSTALLED),
                "the prepared Local AI should report INSTALLED but the status is " + state);
        check(context.computeOnClient(client -> ai.isLauncherManaged()), "the prepared install should be launcher-managed "
                + "(read-only) through the note, but the service reports a client-managed folder");
        check(context.computeOnClient(client -> ai.paths().root().equals(prepared)), "the service resolved "
                + context.computeOnClient(client -> ai.paths().root()) + " instead of " + prepared);

        Runnable unsubscribe = context.computeOnClient(client -> ai.addListener(new LocalAiService.Listener() {
            @Override
            public void onStatusChanged(LocalAiStatus status, String reason) {
                step("local ai: status " + status + (reason.isEmpty() ? "" : " (" + reason + ")"));
            }

            @Override
            public void onProgress(InstallStep installStep, long bytesDone, long bytesTotal, double bytesPerSecond) {
                // The service's own progress is for Install/Verify; the installer run below logs its own.
            }
        }));
        try {
            startAndAsk(context, services, ai);
        } finally {
            context.runOnClient(client -> unsubscribe.run());
        }
        installerRoundTrip(context, services, prepared);
    }

    // ---- start, ask, stop --------------------------------------------------------------------------------------

    private static void startAndAsk(ClientGameTestContext context, VantaServices services, LocalAiService ai) {
        boolean started = context.computeOnClient(client -> ai.start());
        check(started, "LocalAiService.start() returned false: " + context.computeOnClient(client ->
                ai.status() + " (" + ai.statusReason() + ")"));
        long startedAt = System.currentTimeMillis();
        boolean ready = pollFor(context, client -> ai.status() == LocalAiStatus.READY, READY_TIMEOUT_TICKS);
        String status = context.computeOnClient(client -> ai.status() + " (" + ai.statusReason() + ")");
        check(ready, "llama-server did not become READY within " + READY_TIMEOUT_TICKS + " ticks; status " + status
                + "; command " + context.computeOnClient(client -> ai.runtime().map(r -> r.command(0)).orElse(null))
                + "; server log tail:\n" + logTail(ai.paths().serverLogFile(), 40));
        OptionalInt port = context.computeOnClient(client -> ai.runtime().map(LocalAiRuntime::port)
                .orElse(OptionalInt.empty()));
        step(String.format(Locale.ROOT, "local ai: READY after %.1f s on 127.0.0.1:%s (threads %d, context %d)",
                (System.currentTimeMillis() - startedAt) / 1000.0, port.isPresent() ? port.getAsInt() : "?",
                context.computeOnClient(client -> ai.runtime().map(r -> r.config().threads()).orElse(0)),
                context.computeOnClient(client -> ai.runtime().map(r -> r.config().contextSize()).orElse(0))));

        context.runOnClient(client -> VantaClientGameTest.enableWidgets(services,
                client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()));
        Set<HudWidgetType> before = context.computeOnClient(client -> NexusStep.visibleTypes(services.hud().layout()));
        check(before.size() > EXPECTED.size(), "the HUD should show more than " + EXPECTED + " before the question, "
                + "but shows " + before);

        boolean parsed = context.computeOnClient(client -> services.nexus().commands().parse(QUESTION).understood());
        check(!parsed, "'" + QUESTION + "' is understood by the instant commands, so it would not test the model");

        AtomicReference<NexusReply> reply = new AtomicReference<>();
        long askedAt = System.currentTimeMillis();
        OptionsBridge options = services.settings().optionsBridge().orElseThrow();
        Object framerate = context.computeOnClient(client -> options.get(VanillaOption.FRAMERATE_LIMIT).orElse(null));
        boolean answered;
        int[] ticks = {0};
        try {
            boolean lowered = context.computeOnClient(client -> options.set(VanillaOption.FRAMERATE_LIMIT,
                    WAIT_FRAMERATE));
            step("local ai: Max Framerate " + framerate + " -> " + (lowered ? WAIT_FRAMERATE : "unchanged (refused)")
                    + " while the model answers; the test thread sleeps " + QUIET_POLL_MS + " ms between ticks");
            context.runOnClient(client -> services.nexus().ask(QUESTION, reply::set));
            answered = waitQuietly(context, client -> reply.get() != null, ANSWER_WAIT_MS, ticks);
        } finally {
            if (framerate != null) {
                context.runOnClient(client -> options.set(VanillaOption.FRAMERATE_LIMIT, framerate));
            }
        }
        Object restored = context.computeOnClient(client -> options.get(VanillaOption.FRAMERATE_LIMIT).orElse(null));
        check(java.util.Objects.equals(restored, framerate), "Max Framerate is " + restored + " after the wait instead "
                + "of " + framerate);
        step(String.format(Locale.ROOT, "local ai: waited %.1f s over %d game ticks; Max Framerate back at %s",
                (System.currentTimeMillis() - askedAt) / 1000.0, ticks[0], restored));
        check(answered, "no answer to '" + QUESTION + "' within " + ANSWER_WAIT_MS + " ms; status "
                + context.computeOnClient(client -> ai.status() + " (" + ai.statusReason() + ")")
                + "; server log tail:\n" + logTail(ai.paths().serverLogFile(), 40));
        NexusReply answer = reply.get();
        List<String> applied = new ArrayList<>();
        answer.applied().forEach(a -> applied.add(a.type() + " " + a.target() + " " + a.detail()));
        List<String> rejected = new ArrayList<>();
        answer.rejected().forEach(r -> rejected.add(r.type() + " " + r.reason() + " " + r.detail()));
        List<NexusTranscript.Entry> transcript = context.computeOnClient(client ->
                services.nexusTranscript().lastEntries(2));
        step(String.format(Locale.ROOT, "local ai: reply after %.1f s: message=%s applied=%s rejected=%s error=%s "
                        + "transcript=%s", (System.currentTimeMillis() - askedAt) / 1000.0, quote(answer.message()),
                applied, rejected, answer.error().map(e -> e.kind() + ": " + e.getMessage()).orElse("none"),
                transcript));
        check(!answer.isError(), "the assistant failed: " + answer.error().map(e -> e.kind() + ": " + e.getMessage())
                .orElse("?") + "; raw message " + quote(answer.message()) + "; server log tail:\n"
                + logTail(ai.paths().serverLogFile(), 40));
        check(!answer.instant(), "the reply came from the instant commands, not from the model");
        check(!answer.applied().isEmpty(), "the reply applied no action; raw message " + quote(answer.message())
                + "; rejected " + rejected);
        Set<HudWidgetType> after = context.computeOnClient(client -> NexusStep.visibleTypes(services.hud().layout()));
        check(after.equals(EXPECTED), "after '" + QUESTION + "' the visible widgets are " + after + " instead of "
                + EXPECTED + "; applied " + applied + "; raw message " + quote(answer.message()));
        step("local ai: the HUD shows exactly " + after + " (before: " + before + ")");

        context.runOnClient(client -> ai.stop());
        boolean stopped = pollFor(context, client -> !ai.runtime().map(LocalAiRuntime::isRunning).orElse(false),
                STOP_TIMEOUT_TICKS);
        check(stopped, "llama-server is still running " + STOP_TIMEOUT_TICKS + " ticks after stop(): "
                + context.computeOnClient(client -> ai.status()));
        if (port.isPresent()) {
            check(!accepts(port.getAsInt()), "127.0.0.1:" + port.getAsInt() + " still accepts connections after the "
                    + "runtime stopped");
        }
        step("local ai: runtime stopped, status " + context.computeOnClient(client -> ai.status())
                + (port.isPresent() ? ", port " + port.getAsInt() + " closed" : ""));
    }

    /**
     * Polls with the game mostly paused: one game tick, then {@value #QUIET_POLL_MS} ms of sleep on the test thread,
     * until the predicate holds or {@code timeoutMillis} of real time passed. The client game test runs the game in
     * lock-step with the test thread, so no frame is rendered while the test thread sleeps; the Local AI's request
     * runs on its own worker thread and its reply is delivered on the next tick.
     */
    private static boolean waitQuietly(ClientGameTestContext context, Predicate<Minecraft> predicate,
                                       long timeoutMillis, int[] ticks) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (context.computeOnClient(predicate::test)) {
                return true;
            }
            try {
                Thread.sleep(QUIET_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            context.waitTick();
            ticks[0]++;
        }
        return context.computeOnClient(predicate::test);
    }

    // ---- the instant command (from client 1.6.0 on) -------------------------------------------------------------

    /** Counts the requests that reach the model and passes them on. */
    private static final class CountingBackend implements ChatBackend {
        private final ChatBackend delegate;
        private final AtomicInteger requests = new AtomicInteger();

        CountingBackend(ChatBackend delegate) {
            this.delegate = delegate;
        }

        @Override
        public void chat(LocalAiClient.ChatRequest request, Consumer<LocalAiClient.ChatResponse> onReply,
                         Consumer<LocalAiException> onError) {
            requests.incrementAndGet();
            delegate.chat(request, onReply, onError);
        }
    }

    /** Music volume and crosshair shape as the game and VANTA report them. */
    private record Sound(Object music, CrosshairShape shape) {
    }

    /**
     * {@value #INSTANT_COMMAND} through the assistant: answered at once without the model, music 0 and a circle
     * crosshair in the real game, then Undo restores both. The music is set to 0.6 and the crosshair to a cross first so
     * the command always changes something; the player's own values are put back at the end.
     */
    private static void instantCommand(ClientGameTestContext context, VantaServices services) {
        OptionsBridge options = services.settings().optionsBridge().orElseThrow();
        LocalAiService ai = services.localAi();
        Object originalMusic = context.computeOnClient(client -> options.get(VanillaOption.MUSIC_VOLUME).orElse(null));
        CrosshairStyle originalStyle = context.computeOnClient(client -> services.crosshair().style());
        check(originalMusic instanceof Number, "the options bridge does not provide the music volume: "
                + originalMusic);
        ChatBackend backend = context.computeOnClient(client -> services.nexusBackend());
        CountingBackend counting = new CountingBackend(backend);
        try {
            context.runOnClient(client -> {
                options.set(VanillaOption.MUSIC_VOLUME, 0.6);
                services.crosshair().setStyle(originalStyle.withShape(CrosshairShape.CROSS));
                services.setNexusBackend(counting);
            });
            Sound before = context.computeOnClient(client -> new Sound(options.get(VanillaOption.MUSIC_VOLUME)
                    .orElse(null), services.crosshair().style().shape()));
            LocalAiStatus statusBefore = context.computeOnClient(client -> ai.status());
            boolean parsed = context.computeOnClient(client -> services.nexus().commands().parse(INSTANT_COMMAND)
                    .understood());
            check(parsed, "'" + INSTANT_COMMAND + "' is not understood by the instant commands");

            long[] nanos = {0};
            NexusReply reply = context.computeOnClient(client -> {
                AtomicReference<NexusReply> answer = new AtomicReference<>();
                long started = System.nanoTime();
                services.nexus().ask(INSTANT_COMMAND, answer::set);
                nanos[0] = System.nanoTime() - started;
                return answer.get();
            });
            long millis = nanos[0] / 1_000_000;
            check(reply != null, "'" + INSTANT_COMMAND + "' was not answered before ask returned");
            List<String> applied = new ArrayList<>();
            reply.applied().forEach(a -> applied.add(a.summary()));
            step("local ai: instant command '" + INSTANT_COMMAND + "' answered in " + millis + " ms without the "
                    + "model: message=" + quote(reply.message()) + " applied=" + applied + " rejected="
                    + reply.rejected());
            check(reply.instant() && !reply.isError(), "'" + INSTANT_COMMAND + "' was not answered by the instant "
                    + "commands: " + reply);
            check(counting.requests.get() == 0, "the model was asked " + counting.requests.get() + " time(s)");
            check(millis < INSTANT_LIMIT_MS, "the instant command took " + millis + " ms");
            check(reply.rejected().isEmpty() && reply.applied().size() == 2, "the instant command applied "
                    + applied + " and refused " + reply.rejected());
            Sound after = context.computeOnClient(client -> new Sound(options.get(VanillaOption.MUSIC_VOLUME)
                    .orElse(null), services.crosshair().style().shape()));
            check(after.music() instanceof Number n && n.doubleValue() == 0.0, "the vanilla music volume after the "
                    + "instant command is " + after.music());
            check(after.shape() == CrosshairShape.CIRCLE, "the crosshair after the instant command is "
                    + after.shape());
            check(context.computeOnClient(client -> ai.status()) == statusBefore, "the Local AI status moved from "
                    + statusBefore + " to " + context.computeOnClient(client -> ai.status()));

            boolean undone = context.computeOnClient(client -> services.nexus().undoLast());
            check(undone, "Undo after the instant command returned false");
            Sound restored = context.computeOnClient(client -> new Sound(options.get(VanillaOption.MUSIC_VOLUME)
                    .orElse(null), services.crosshair().style().shape()));
            check(restored.equals(before), "Undo restored " + restored + " instead of " + before);
            step("local ai: Undo restored music " + restored.music() + " and the " + restored.shape() + " crosshair");
        } finally {
            context.runOnClient(client -> {
                services.setNexusBackend(backend);
                options.set(VanillaOption.MUSIC_VOLUME, originalMusic);
                services.crosshair().setStyle(originalStyle);
            });
        }
    }

    private static boolean accepts(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ---- installer against a loopback server -------------------------------------------------------------------

    private static void installerRoundTrip(ClientGameTestContext context, VantaServices services, Path prepared) {
        LocalAiService ai = services.localAi();
        LocalAiManifest manifest = ai.manifest().orElseThrow();
        Platform platform = ai.platform().orElseThrow();
        LocalAiManifest.RuntimePlatform archive = manifest.forPlatform(platform).orElseThrow();
        LocalAiInstalled installed = context.computeOnClient(client -> ai.installed()).orElseThrow();
        Path server = ai.paths().serverExecutable(installed.runtime().tag(), platform, archive.serverPath());
        Path model = ai.paths().modelFile(manifest.model().file());
        check(Files.isRegularFile(server), "prepared server executable missing: " + server);
        check(Files.isRegularFile(model), "prepared model file missing: " + model);
        long modelSize = size(model);
        check(modelSize == manifest.model().size(), "the prepared model has " + modelSize + " bytes, the manifest says "
                + manifest.model().size());

        Path work;
        try {
            work = Files.createTempDirectory(FabricLoader.getInstance().getGameDir(), "vanta-local-ai-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        HttpServer http = null;
        try {
            Path archiveFile = work.resolve(archive.file());
            if (platform.usesZip()) {
                writeZip(archiveFile, archive.serverPath(), server);
            } else {
                writeTarGz(archiveFile, archive.serverPath(), server);
            }
            long archiveSize = size(archiveFile);
            String archiveSha = sha256(archiveFile);
            http = serve(Map.of("/" + archive.file(), archiveFile, "/" + manifest.model().file(), model));
            String base = "http://127.0.0.1:" + http.getAddress().getPort() + "/";
            LocalAiManifest mirror = mirror(manifest, platform, base, archiveSize, archiveSha);
            step("local ai: mirror on " + base + " serving " + archive.file() + " (" + archiveSize + " bytes, "
                    + archiveSha + ") and " + manifest.model().file() + " (" + modelSize + " bytes)");

            LocalAiPaths target = LocalAiPaths.clientManaged(work.resolve("install"));
            LocalAiInstaller.Config config = LocalAiInstaller.Config.defaults(VantaVersion.CLIENT);
            LocalAiInstaller installer = new LocalAiInstaller(mirror, target, platform,
                    () -> LocalAiInstaller.defaultHttpClient(config), config, Clock.systemUTC());
            check(installer.quickCheck() == LocalAiStatus.NOT_INSTALLED, "a fresh directory should be NOT_INSTALLED "
                    + "but the quick check says " + installer.quickCheck());

            AtomicReference<Object> outcome = new AtomicReference<>();
            AtomicReference<String> lastStep = new AtomicReference<>("");
            Thread worker = new Thread(() -> {
                try {
                    outcome.set(installer.install((installStep, done, total, speed) -> {
                        String line = installStep.id() + (total > 0 ? String.format(Locale.ROOT, " %d/%d bytes",
                                done, total) : "");
                        if (!installStep.id().equals(lastStep.getAndSet(installStep.id()))) {
                            step("local ai: installer step " + line);
                        }
                    }));
                } catch (LocalAiException e) {
                    outcome.set(e);
                } catch (RuntimeException e) {
                    outcome.set(e);
                }
            }, "VANTA gametest Local AI installer");
            worker.setDaemon(true);
            long installStarted = System.currentTimeMillis();
            worker.start();
            boolean finished = pollFor(context, client -> outcome.get() != null, INSTALL_TIMEOUT_TICKS);
            check(finished, "the local install did not finish within " + INSTALL_TIMEOUT_TICKS + " ticks (last step "
                    + lastStep.get() + ")");
            Object result = outcome.get();
            check(result instanceof LocalAiInstalled, "the local install failed: " + result
                    + (result instanceof LocalAiException e ? " (" + e.kind() + ")" : ""));
            LocalAiInstalled written = (LocalAiInstalled) result;
            step(String.format(Locale.ROOT, "local ai: installed from the mirror in %.1f s into %s",
                    (System.currentTimeMillis() - installStarted) / 1000.0, target.root()));

            check(Files.isRegularFile(target.installedFile()), "installed.json missing in " + target.root());
            Optional<LocalAiInstalled> reread = installer.readInstalled();
            check(reread.isPresent() && reread.get().matches(mirror), "installed.json does not parse or does not "
                    + "match the mirror manifest: " + reread);
            Path installedServer = installer.serverExecutable(written);
            check(Files.isRegularFile(installedServer), "extracted server executable missing: " + installedServer);
            check(Files.isExecutable(installedServer), "the extracted server executable lacks the executable bit: "
                    + installedServer);
            check(size(installedServer) == size(server), "the extracted server executable has " + size(installedServer)
                    + " bytes, the prepared one " + size(server));
            check(Files.isRegularFile(installer.modelFile()) && size(installer.modelFile()) == modelSize,
                    "the downloaded model is missing or has the wrong size");
            check(!Files.exists(target.downloadFile(archive.file())), "the runtime archive was kept in downloads/");
            LocalAiStatus quick = installer.quickCheck();
            check(quick == LocalAiStatus.INSTALLED, "the quick check of the local install says " + quick);
            step("local ai: installed.json, executable bit, model and quick check INSTALLED verified");

            try {
                installer.remove();
            } catch (LocalAiException e) {
                throw new IllegalStateException("remove() of the local install failed (" + e.kind() + "): " + e.getMessage(), e);
            }
            check(!Files.exists(target.root()), "the install directory still exists after remove(): " + target.root());
            step("local ai: install removed, " + target.root() + " is gone");
        } finally {
            if (http != null) {
                http.stop(0);
            }
            deleteTree(work);
        }
    }

    /** The manifest with this platform's archive and the model pointed at the loopback server. */
    private static LocalAiManifest mirror(LocalAiManifest manifest, Platform platform, String base, long archiveSize,
                                          String archiveSha) {
        JsonObject json = manifest.toJson();
        JsonObject platforms = json.getAsJsonObject("runtime").getAsJsonObject("platforms");
        JsonObject entry = platforms.getAsJsonObject(platform.key());
        entry.addProperty("url", base + entry.get("file").getAsString());
        entry.addProperty("size", archiveSize);
        entry.addProperty("sha256", archiveSha);
        JsonObject model = json.getAsJsonObject("model");
        model.addProperty("url", base + model.get("file").getAsString());
        try {
            return LocalAiManifest.fromJson(json, false);
        } catch (LocalAiException e) {
            throw new AssertionError("the mirror manifest is invalid: " + e.getMessage(), e);
        }
    }

    /** A loopback HTTP server answering GET for the given paths with the file and its Content-Length. */
    private static HttpServer serve(Map<String, Path> files) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "VANTA gametest Local AI mirror");
                t.setDaemon(true);
                return t;
            }));
            for (Map.Entry<String, Path> file : files.entrySet()) {
                server.createContext(file.getKey(), exchange -> sendFile(exchange, file.getValue()));
            }
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sendFile(HttpExchange exchange, Path file) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            long size = Files.size(file);
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, size);
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(file, out);
            }
        }
    }

    // ---- archives ----------------------------------------------------------------------------------------------

    /** A zip with one entry: {@code entryName} holding the bytes of {@code source}. */
    private static void writeZip(Path out, String entryName, Path source) {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out))) {
            zip.putNextEntry(new ZipEntry(entryName.replace('\\', '/')));
            Files.copy(source, zip);
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A ustar tar.gz with one regular file entry (mode 0755) holding the bytes of {@code source}. */
    private static void writeTarGz(Path out, String entryName, Path source) {
        try (GZIPOutputStream gz = new GZIPOutputStream(Files.newOutputStream(out))) {
            long size = Files.size(source);
            gz.write(tarHeader(entryName.replace('\\', '/'), size, 0755));
            Files.copy(source, gz);
            int pad = (int) ((512 - size % 512) % 512);
            gz.write(new byte[pad]);
            gz.write(new byte[1024]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One 512-byte ustar header for a regular file (names above 100 chars use the prefix field). */
    static byte[] tarHeader(String path, long size, int mode) {
        String name = path;
        String prefix = "";
        if (name.length() > 100) {
            int cut = name.lastIndexOf('/', 155);
            if (cut <= 0 || name.length() - cut - 1 > 100) {
                throw new IllegalArgumentException("tar entry name too long: " + path);
            }
            prefix = name.substring(0, cut);
            name = name.substring(cut + 1);
        }
        byte[] header = new byte[512];
        putString(header, 0, 100, name);
        putOctal(header, 100, 8, mode & 07777);
        putOctal(header, 108, 8, 0);
        putOctal(header, 116, 8, 0);
        putOctal(header, 124, 12, size);
        putOctal(header, 136, 12, System.currentTimeMillis() / 1000L);
        header[156] = '0';
        putString(header, 257, 6, "ustar");
        header[263] = '0';
        header[264] = '0';
        putString(header, 265, 32, "vanta");
        putString(header, 297, 32, "vanta");
        putOctal(header, 329, 8, 0);
        putOctal(header, 337, 8, 0);
        putString(header, 345, 155, prefix);
        Arrays.fill(header, 148, 156, (byte) ' ');
        long sum = 0;
        for (byte b : header) {
            sum += b & 0xFF;
        }
        byte[] checksum = String.format(Locale.ROOT, "%06o", sum).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(checksum, 0, header, 148, 6);
        header[154] = 0;
        header[155] = ' ';
        return header;
    }

    private static void putString(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length));
    }

    private static void putOctal(byte[] header, int offset, int length, long value) {
        String text = String.format(Locale.ROOT, "%0" + (length - 1) + "o", value);
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length - 1));
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(Path file) {
        try {
            return Sha256.hex(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String logTail(Path file, int lines) {
        try {
            if (!Files.isRegularFile(file)) {
                return "(no server log at " + file + ")";
            }
            List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> tail = all.subList(Math.max(0, all.size() - lines), all.size());
            return String.join("\n", tail);
        } catch (IOException | RuntimeException e) {
            return "(could not read " + file + ": " + e + ")";
        }
    }

    private static String quote(String text) {
        return "\"" + (text == null ? "" : text.replace("\n", " ")) + "\"";
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            warn("local ai: could not delete the temporary directory " + root + ": " + e);
        }
    }
}
