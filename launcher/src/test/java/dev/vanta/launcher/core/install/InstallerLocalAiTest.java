package dev.vanta.launcher.core.install;

import dev.vanta.launcher.core.ai.LocalAiNote;
import dev.vanta.launcher.core.ai.LocalAiReport;
import dev.vanta.launcher.core.ai.LocalAiService;
import dev.vanta.launcher.core.ai.LocalAiState;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.testutil.FakeLocalAi;
import dev.vanta.launcher.testutil.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@link InstallStep#LOCAL_AI} step inside the regular install: optional, never fatal, with the note in every case. */
class InstallerLocalAiTest {

    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;
    private FakeWorld world;
    private FakeLocalAi ai;

    @BeforeEach
    void start() throws IOException {
        world = new FakeWorld(false);
        ai = new FakeLocalAi(world.server());
    }

    @AfterEach
    void stop() {
        world.close();
    }

    private record Recorder(List<InstallProgress> progress, List<String> logs) implements InstallListener {
        Recorder() {
            this(new ArrayList<>(), new ArrayList<>());
        }

        @Override
        public void onProgress(final InstallProgress p) {
            progress.add(p);
        }

        @Override
        public void onLog(final String message) {
            logs.add(message);
        }
    }

    private Installer installer(final LauncherPaths paths, final LocalAiService.ManifestSource source) {
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        final LocalAiService localAi = new LocalAiService(wired.downloader(), paths, LINUX, source, CLOCK);
        return new Installer(paths, LINUX, wired.downloader(), wired.mojang(), wired.fabric(), wired.fabricApi(), wired.vanta(), Optional.empty(),
            CLOCK, Optional.of(wired.pack()), Optional.of(localAi));
    }

    @Test
    void thePlanAddsTheStepOnlyWhenAskedAndTheRequestKeepsOldConstructors() {
        final InstallRequest standard = InstallRequest.standard();
        assertFalse(standard.includeLocalAi(), "the standard install never downloads the Local AI without consent");
        assertFalse(InstallPlan.stepsFor(standard).contains(InstallStep.LOCAL_AI));
        final List<InstallStep> with = InstallPlan.stepsFor(standard.withLocalAi(true));
        assertTrue(with.contains(InstallStep.LOCAL_AI));
        assertEquals(InstallStep.FINALIZE, with.get(with.size() - 1));
        assertEquals(InstallStep.PERFORMANCE_PACK, with.get(with.indexOf(InstallStep.LOCAL_AI) - 1), "after the performance pack, before finalize");
        assertEquals(12, with.size());
        assertFalse(new InstallRequest("1.21.11", "0.19.5", "0.141.6+1.21.11", null, true, false, true).includeLocalAi());
        assertFalse(new InstallRequest("1.21.11", "0.19.5", "0.141.6+1.21.11", null, true, false).includeLocalAi());
        assertEquals("Installing the Local AI", InstallStep.LOCAL_AI.label());
    }

    @Test
    void theStepInstallsTheLocalAiAndWritesTheNote() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final Installer installer = installer(paths, ai::manifest);
        final Recorder recorder = new Recorder();
        final InstallRequest request = new InstallRequest("1.21.11", "0.19.5", dev.vanta.launcher.LauncherVersion.FABRIC_API, null, true, false, false)
            .withLocalAi(true);

        final InstanceInfo info = installer.install(request, recorder, new CancellationToken());

        assertTrue(info.hasVantaClient());
        final LocalAiService service = new LocalAiService(world.downloader(dev.vanta.launcher.core.util.Sleeper.NONE), paths, LINUX, ai::manifest, CLOCK);
        assertEquals(LocalAiState.INSTALLED, service.status().state(), service.status().problems().toString());
        assertTrue(Files.isRegularFile(paths.localAiDir().resolve(LocalAiService.INSTALLED_FILE)));
        assertEquals(Optional.of(paths.localAiDir()), LocalAiNote.read(paths.instanceDir()));
        assertTrue(recorder.progress().stream().anyMatch(p -> p.step() == InstallStep.LOCAL_AI && p.done() == p.total() && p.total() > 1),
            "the Local AI step reports its bytes under the plan's step");
        final int stepCount = recorder.progress().get(0).stepCount();
        assertTrue(recorder.progress().stream().filter(p -> p.step() == InstallStep.LOCAL_AI).allMatch(p -> p.stepCount() == stepCount
            && p.stepIndex() == stepCount - 2), "the nested progress is re-indexed into this plan");
        final String logs = String.join("\n", recorder.logs());
        assertTrue(logs.contains("Downloading Local AI model"), logs);
        assertTrue(logs.contains("Local AI: llama.cpp b11429, Qwen3-1.7B Q8_0 in place"), logs);

        // A second install with the step only confirms; without the step the note is still (re)written.
        final Recorder again = new Recorder();
        installer.install(request, again, new CancellationToken());
        assertTrue(String.join("\n", again.logs()).contains("Local AI already installed"), again.logs().toString());
        Files.delete(LocalAiNote.file(paths.instanceDir()));
        installer.install(request.withLocalAi(false), new Recorder(), new CancellationToken());
        assertEquals(Optional.of(paths.localAiDir()), LocalAiNote.read(paths.instanceDir()), "every install writes the note");
    }

    /**
     * PLAY pressed while the first-start offer's install still downloads: the pipeline shares the service with it, finds
     * it busy and skips its Local AI step with a log line instead of a second install into the same folder (or a wait
     * behind the download); the offer's install finishes on its own.
     */
    @Test
    void theStepIsSkippedWhileAnotherLocalAiTaskRuns() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final FakeWorld.Wired wired = world.wire(paths, LINUX, Optional.empty(), CLOCK);
        final LocalAiService localAi = new LocalAiService(wired.downloader(), paths, LINUX, ai::manifest, CLOCK);
        final Installer installer = new Installer(paths, LINUX, wired.downloader(), wired.mojang(), wired.fabric(), wired.fabricApi(), wired.vanta(),
            Optional.empty(), CLOCK, Optional.of(wired.pack()), Optional.of(localAi));
        // The offer's install: its listener holds the first step until the test lets it go, so the service stays busy.
        final CountDownLatch offerStarted = new CountDownLatch(1);
        final CountDownLatch letTheOfferFinish = new CountDownLatch(1);
        final InstallListener holding = new InstallListener() {
            @Override
            public void onLog(final String message) {
                offerStarted.countDown();
                try {
                    letTheOfferFinish.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            final Future<LocalAiReport> offer = pool.submit(() -> localAi.install(holding, new CancellationToken()));
            assertTrue(offerStarted.await(30, TimeUnit.SECONDS), "the offer's install started");
            assertTrue(localAi.isBusy());

            final Recorder recorder = new Recorder();
            final InstallRequest request = new InstallRequest("1.21.11", "0.19.5", dev.vanta.launcher.LauncherVersion.FABRIC_API, null, true, false, false)
                .withLocalAi(true);
            final InstanceInfo info = installer.install(request, recorder, new CancellationToken());

            assertTrue(info.hasVantaClient(), "Minecraft, Fabric and the client are installed");
            assertTrue(localAi.isBusy(), "the offer's install is still running; PLAY did not wait for it");
            final String logs = String.join("\n", recorder.logs());
            assertTrue(logs.contains("Local AI skipped: a Local AI install, verify or remove is already running"), logs);
            assertFalse(logs.contains("Downloading Local AI model"), "the pipeline started no install of its own:\n" + logs);
            assertFalse(Files.exists(paths.localAiDir().resolve(LocalAiService.INSTALLED_FILE)), "nothing was installed behind the running one");
            assertEquals(Optional.of(paths.localAiDir()), LocalAiNote.read(paths.instanceDir()), "the note is written anyway");

            letTheOfferFinish.countDown();
            final LocalAiReport finished = offer.get(60, TimeUnit.SECONDS);
            assertEquals(LocalAiState.INSTALLED, finished.state(), finished.problems().toString());
            assertFalse(localAi.isBusy());
            assertEquals(1, world.server().hits(ai.modelPath()), "the model was downloaded by the offer's install only");

            // With the service free again, the step confirms the install the offer made.
            final Recorder again = new Recorder();
            installer.install(request, again, new CancellationToken());
            assertTrue(String.join("\n", again.logs()).contains("Local AI already installed"), again.logs().toString());
        } finally {
            letTheOfferFinish.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void aFailingLocalAiStepNeverFailsTheInstall() throws Exception {
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        ai.corruptModel();
        final Installer installer = installer(paths, ai::manifest);
        final Recorder recorder = new Recorder();
        final InstallRequest request = new InstallRequest("1.21.11", "0.19.5", dev.vanta.launcher.LauncherVersion.FABRIC_API, null, true, false, false)
            .withLocalAi(true);

        final InstanceInfo info = installer.install(request, recorder, new CancellationToken());

        assertTrue(info.hasVantaClient(), "Minecraft, Fabric and the client are installed");
        assertTrue(Files.isRegularFile(paths.instanceFile()));
        final String logs = String.join("\n", recorder.logs());
        assertTrue(logs.contains("Local AI skipped: "), logs);
        assertTrue(logs.contains("SHA256 mismatch"), logs);
        assertFalse(Files.exists(paths.localAiDir().resolve(LocalAiService.INSTALLED_FILE)));
        assertEquals(Optional.of(paths.localAiDir()), LocalAiNote.read(paths.instanceDir()), "the note is written anyway");

        // No manifest in this build: skipped with the reason, install still complete.
        final Installer none = installer(new LauncherPaths(tmp.resolve("data2")), () -> {
            throw new IOException("This build of the launcher contains no Local AI manifest");
        });
        final Recorder noneRecorder = new Recorder();
        none.install(request, noneRecorder, new CancellationToken());
        assertTrue(String.join("\n", noneRecorder.logs()).contains("Local AI skipped: This build of the launcher contains no Local AI manifest"),
            noneRecorder.logs().toString());
    }
}
