package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.notifications.Notification;
import dev.vanta.core.notifications.NotificationCenter;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The facade: install through the local server with toasts and listeners, chat through a fake process, verify,
 * remove, and the honest states for missing manifests, unsupported systems and launcher-managed installs.
 */
class LocalAiServiceTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Executor DIRECT = Runnable::run;
    private static final Platform LINUX = Platform.parse("linux-x64").orElseThrow();
    private static final String CANNED = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
            + "\"content\":\"{\\\"message\\\":\\\"done\\\",\\\"actions\\\":[]}\"}}]}";

    @TempDir
    Path dir;
    LocalFileServer server;
    VantaPaths paths;
    SettingsStore settings;
    NotificationCenter notifications;
    final MutableClock clock = MutableClock.standard();
    final FakeProcess.Factory processes = new FakeProcess.Factory();
    final List<String> events = new ArrayList<>();
    final byte[] model = FakeArchives.randomBytes(200_000, 8);

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalFileServer();
        server.json(LocalAiClient.CHAT_PATH, body -> CANNED);
        paths = VantaPaths.inGameDirectory(dir);
        paths.createDirectories();
        settings = new SettingsStore(VantaSettings.registry(), new JsonStore(clock), paths.settingsFile(),
                Optional.empty());
        settings.load();
        notifications = new NotificationCenter(clock);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private LocalAiService.Dependencies deps(Optional<Platform> platform, Optional<LocalAiManifest> manifest,
                                             String problem) {
        return new LocalAiService.Dependencies(platform, manifest, problem, () -> HTTP, processes, server::port,
                duration -> clock.advance(duration.toMillis()), DIRECT, 8, false,
                LocalAiInstaller.Config.defaults("test"));
    }

    private LocalAiService service(LocalAiService.Dependencies deps) {
        LocalAiService service = new LocalAiService(paths, settings, notifications, DIRECT, clock, deps);
        service.addListener(new LocalAiService.Listener() {
            @Override
            public void onStatusChanged(LocalAiStatus status, String reason) {
                events.add("status:" + status.id() + (reason.isEmpty() ? "" : ":" + reason));
            }

            @Override
            public void onProgress(InstallStep step, long bytesDone, long bytesTotal, double bytesPerSecond) {
                if (events.isEmpty() || !events.get(events.size() - 1).equals("progress:" + step.id())) {
                    events.add("progress:" + step.id());
                }
            }

            @Override
            public void onFinished(Optional<LocalAiException> error) {
                events.add("finished:" + error.map(e -> e.kind().id()).orElse("ok"));
            }
        });
        service.load();
        return service;
    }

    private LocalAiManifest manifest() throws LocalAiException {
        return ManifestFixtures.serve(server, FakeArchives.windowsRuntimeZip(), FakeArchives.linuxRuntimeTarGz(),
                model);
    }

    private static LocalAiClient.ChatRequest request() {
        return LocalAiClient.ChatRequest.of(List.of(LocalAiClient.Message.user("hi")), new JsonObject());
    }

    @Test
    void installsWithToastsAndListenersThenAnswersThroughTheRuntime() throws Exception {
        LocalAiService service = service(deps(Optional.of(LINUX), Optional.of(manifest()), ""));
        assertEquals(LocalAiStatus.NOT_INSTALLED, service.status());
        assertTrue(service.isSupported());
        assertFalse(service.isInstalled());
        assertFalse(service.isLauncherManaged());
        assertEquals(paths.localAiDir(), service.paths().root());
        assertTrue(service.downloadBytes() > model.length);

        assertTrue(service.install());
        assertEquals(LocalAiStatus.INSTALLED, service.status());
        assertTrue(service.isInstalled());
        assertTrue(service.installed().isPresent());
        assertFalse(service.isBusy());
        assertTrue(events.contains("progress:downloading_runtime"), events.toString());
        assertTrue(events.contains("progress:downloading_model"), events.toString());
        assertTrue(events.contains("progress:installing"), events.toString());
        assertEquals("finished:ok", events.get(events.size() - 1));
        assertTrue(events.contains("status:installed"), events.toString());
        List<String> titles = notifications.history().stream().map(Notification::title).toList();
        assertTrue(titles.contains("Local AI installed"), titles.toString());
        assertTrue(titles.contains("Local AI"), "the progress toast was shown: " + titles);
        assertTrue(notifications.visible().stream().noneMatch(n -> n.title().equals("Local AI")),
                "the progress toast is dismissed at the end");
        assertTrue(Files.isRegularFile(service.paths().installedFile()));

        List<LocalAiClient.ChatResponse> replies = new ArrayList<>();
        List<LocalAiException> errors = new ArrayList<>();
        service.chat(request(), replies::add, errors::add);
        assertEquals(1, replies.size(), errors.toString());
        assertEquals(LocalAiStatus.READY, service.status());
        assertTrue(service.runtime().isPresent());
        List<String> command = processes.commands.get(0);
        assertTrue(command.get(0).endsWith("llama-server"), command.toString());
        assertEquals("4096", command.get(command.indexOf("-c") + 1));
        assertEquals("6", command.get(command.indexOf("-t") + 1), "8 processors: max(2, min(8, 8 - 2))");
        assertEquals(paths.localAiDir().resolve("logs").resolve("llama-server.log"), processes.logFiles.get(0));

        settings.set(VantaSettings.NEXUS_THREADS, 3);
        settings.set(VantaSettings.NEXUS_IDLE_TIMEOUT_MINUTES, 1);
        service.tick();
        assertEquals(3, service.runtime().orElseThrow().config().threads());
        clock.advance(Duration.ofMinutes(1).toMillis() + 1);
        service.tick();
        assertFalse(processes.last().alive, "idle stop after the configured minute");
        assertEquals(LocalAiStatus.INSTALLED, service.status());
        service.chat(request(), replies::add, errors::add);
        assertEquals(2, replies.size());
        assertEquals("3", processes.commands.get(1).get(processes.commands.get(1).indexOf("-t") + 1));

        assertTrue(service.verify());
        assertEquals(LocalAiStatus.READY, service.status(), "the running server keeps its status");
        assertTrue(notifications.history().stream().anyMatch(n -> n.title().equals("Local AI files checked")));

        assertTrue(service.remove());
        assertFalse(Files.exists(service.paths().root()));
        assertEquals(LocalAiStatus.NOT_INSTALLED, service.status());
        assertTrue(service.runtime().isEmpty());
        assertTrue(notifications.history().stream().anyMatch(n -> n.title().equals("Local AI removed")));
        service.close();
    }

    @Test
    void failuresAreReportedHonestly() throws Exception {
        LocalAiManifest bad = ManifestFixtures.serveWithBadModelHash(server, FakeArchives.windowsRuntimeZip(),
                FakeArchives.linuxRuntimeTarGz(), model);
        LocalAiService service = service(deps(Optional.of(LINUX), Optional.of(bad), ""));
        assertTrue(service.install());
        assertEquals("finished:hash_mismatch", events.get(events.size() - 1));
        assertEquals(LocalAiStatus.PARTIAL, service.status(), "the verified runtime archive is kept");
        assertTrue(notifications.history().stream().anyMatch(n -> n.title().equals("Local AI problem")
                && n.body().startsWith("A downloaded file did not match its checksum")));

        List<LocalAiException> errors = new ArrayList<>();
        service.chat(request(), r -> {
            throw new AssertionError("no reply expected");
        }, errors::add);
        assertEquals(LocalAiException.Kind.NOT_INSTALLED, errors.get(0).kind());

        settings.set(VantaSettings.NEXUS_ENABLED, false);
        service.chat(request(), r -> {
            throw new AssertionError("no reply expected");
        }, errors::add);
        assertEquals(LocalAiException.Kind.DISABLED, errors.get(1).kind());
        service.close();
    }

    @Test
    void missingManifestAndUnsupportedPlatformAreStates() {
        LocalAiService noManifest = service(deps(Optional.of(LINUX), Optional.empty(), "manifest incomplete"));
        assertEquals(LocalAiStatus.FAILED, noManifest.status());
        assertEquals("manifest incomplete", noManifest.statusReason());
        assertFalse(noManifest.isSupported());
        assertFalse(noManifest.install());
        assertFalse(noManifest.verify());
        List<LocalAiException> errors = new ArrayList<>();
        noManifest.chat(request(), r -> {
            throw new AssertionError();
        }, errors::add);
        assertEquals(LocalAiException.Kind.INVALID_MANIFEST, errors.get(0).kind());
        noManifest.close();

        events.clear();
        LocalAiService unsupported = service(deps(Optional.empty(), Optional.empty(), ""));
        assertEquals(LocalAiStatus.UNSUPPORTED_PLATFORM, unsupported.status());
        assertTrue(unsupported.statusReason().startsWith("Local AI is not available for"), unsupported.statusReason());
        assertFalse(unsupported.install());
        unsupported.chat(request(), r -> {
            throw new AssertionError();
        }, errors::add);
        assertEquals(LocalAiException.Kind.UNSUPPORTED_PLATFORM, errors.get(1).kind());
        unsupported.close();
    }

    @Test
    void aLauncherNoteMakesTheInstallReadOnly() throws Exception {
        LocalAiManifest manifest = manifest();
        Path launcherDir = dir.resolve("launcher").resolve("local-ai");
        new LocalAiInstaller(manifest, LocalAiPaths.clientManaged(launcherDir), LINUX, () -> HTTP,
                LocalAiInstaller.Config.defaults("test"), clock).install(LocalAiInstaller.Progress.none());
        LocalAiPaths.writeNote(paths.localAiNoteFile(), launcherDir);

        LocalAiService service = service(deps(Optional.of(LINUX), Optional.of(manifest), ""));
        assertTrue(service.isLauncherManaged());
        assertEquals(LocalAiStatus.INSTALLED, service.status());
        assertEquals("Installed by the VANTA Launcher", service.statusReason());
        assertFalse(service.install(), "the client never writes into the launcher's folder");
        assertFalse(service.remove());
        List<LocalAiClient.ChatResponse> replies = new ArrayList<>();
        service.chat(request(), replies::add, e -> {
            throw new AssertionError(e);
        });
        assertEquals(1, replies.size(), "but it runs the server from there");
        assertTrue(processes.commands.get(0).get(0).startsWith(launcherDir.toAbsolutePath().toString()));
        assertTrue(Files.isRegularFile(launcherDir.resolve("installed.json")));
        service.close();
        assertFalse(processes.last().alive, "close stops the server");
    }

    @Test
    void cancelStopsARunningInstall() throws Exception {
        LocalAiManifest manifest = manifest();
        // A worker that cancels before running the task: the installer sees isCancelled() at its first check.
        Executor cancelling = task -> task.run();
        LocalAiService.Dependencies deps = new LocalAiService.Dependencies(Optional.of(LINUX), Optional.of(manifest),
                "", () -> HTTP, processes, server::port, d -> clock.advance(d.toMillis()), cancelling, 4, false,
                LocalAiInstaller.Config.defaults("test"));
        LocalAiService service = new LocalAiService(paths, settings, notifications, DIRECT, clock, deps);
        service.load();
        service.addListener(new LocalAiService.Listener() {
            @Override
            public void onProgress(InstallStep step, long bytesDone, long bytesTotal, double bytesPerSecond) {
                if (step == InstallStep.DOWNLOADING_MODEL) {
                    service.cancelInstall();
                }
            }

            @Override
            public void onFinished(Optional<LocalAiException> error) {
                events.add("finished:" + error.map(e -> e.kind().id()).orElse("ok"));
            }
        });
        assertTrue(service.install());
        assertEquals(List.of("finished:cancelled"), events);
        assertFalse(Files.exists(service.paths().partFile(ManifestFixtures.MODEL_FILE)));
        assertFalse(service.isInstalled());
        assertFalse(notifications.history().stream().anyMatch(n -> n.title().equals("Local AI problem")),
                "a cancel is not an error toast");
        service.close();
    }

    @Test
    void formatsBytes() {
        assertEquals("0 B", LocalAiService.formatBytes(0));
        assertEquals("512 B", LocalAiService.formatBytes(512));
        assertEquals("12 KB", LocalAiService.formatBytes(12_000));
        assertEquals("1.5 MB", LocalAiService.formatBytes(1_500_000));
        assertEquals("345 MB", LocalAiService.formatBytes(345_000_000L));
        assertEquals("1.8 GB", LocalAiService.formatBytes(1_834_426_016L));
        assertEquals("1.9 GB", LocalAiService.formatBytes(1_852_119_478L));
    }
}
