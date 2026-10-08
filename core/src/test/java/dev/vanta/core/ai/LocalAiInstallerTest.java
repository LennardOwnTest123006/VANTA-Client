package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.MutableClock;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The installer against a loopback HTTP server serving a fake zip, a fake tar.gz and a fake model.
 */
class LocalAiInstallerTest {
    private static final Platform WINDOWS = Platform.parse("windows-x64").orElseThrow();
    private static final Platform LINUX = Platform.parse("linux-x64").orElseThrow();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TempDir
    Path dir;
    LocalFileServer server;
    final byte[] zip = FakeArchives.windowsRuntimeZip();
    final byte[] tgz = FakeArchives.linuxRuntimeTarGz();
    final byte[] model = FakeArchives.randomBytes(300_000, 42);
    final MutableClock clock = MutableClock.standard();

    /** Records progress calls; can cancel after a step. */
    static final class RecordingProgress implements LocalAiInstaller.Progress {
        final List<InstallStep> steps = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        InstallStep cancelAt;
        double maxSpeed;

        @Override
        public void onProgress(InstallStep step, long bytesDone, long bytesTotal, double bytesPerSecond) {
            if (steps.isEmpty() || steps.get(steps.size() - 1) != step) {
                steps.add(step);
            }
            events.add(step.id() + ":" + bytesDone + "/" + bytesTotal);
            maxSpeed = Math.max(maxSpeed, bytesPerSecond);
        }

        @Override
        public boolean isCancelled() {
            return cancelAt != null && !steps.isEmpty() && steps.get(steps.size() - 1) == cancelAt;
        }
    }

    @BeforeEach
    void start() throws IOException {
        server = new LocalFileServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private LocalAiInstaller installer(LocalAiManifest manifest, Platform platform) {
        return installer(manifest, platform, LocalAiPaths.clientManaged(dir.resolve("local-ai")), clock);
    }

    private LocalAiInstaller installer(LocalAiManifest manifest, Platform platform, LocalAiPaths paths, Clock c) {
        return new LocalAiInstaller(manifest, paths, platform, () -> HTTP,
                LocalAiInstaller.Config.defaults("1.4.0-test"), c);
    }

    @Test
    void installsTheZipRuntimeAndTheModelEndToEnd() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serve(server, zip, tgz, model);
        LocalAiInstaller installer = installer(manifest, WINDOWS);
        assertEquals(LocalAiStatus.NOT_INSTALLED, installer.quickCheck());

        RecordingProgress progress = new RecordingProgress();
        LocalAiInstalled installed = installer.install(progress);

        assertEquals(List.of(InstallStep.CHECKING, InstallStep.DOWNLOADING_RUNTIME, InstallStep.DOWNLOADING_MODEL,
                InstallStep.VERIFYING, InstallStep.INSTALLING), progress.steps);
        LocalAiPaths paths = installer.paths();
        Path exe = paths.serverExecutable(ManifestFixtures.TAG, WINDOWS, "llama-server.exe");
        assertTrue(Files.isRegularFile(exe), exe.toString());
        assertEquals("MZ fake llama-server", Files.readString(exe));
        assertTrue(Files.isRegularFile(paths.runtimeDir(ManifestFixtures.TAG, WINDOWS).resolve("ggml.dll")));
        assertArrayEquals(model, Files.readAllBytes(paths.modelFile(ManifestFixtures.MODEL_FILE)));
        assertTrue(Files.isRegularFile(paths.installedFile()));
        assertEquals("windows-x64", installed.platform());
        assertEquals(Instant.ofEpochMilli(clock.millis()).toString(), installed.installedAt());
        assertTrue(installed.matches(manifest));
        assertEquals(installed, installer.readInstalled().orElseThrow());
        assertEquals(1, installed.runtime().platforms().size(), "only the installed platform is recorded");
        assertEquals(model.length, installed.files().modelSize());
        try (Stream<Path> downloads = Files.list(paths.downloadsDir())) {
            assertTrue(downloads.toList().isEmpty(), "no .part and no archive left in downloads/");
        }
        assertFalse(Files.exists(paths.runtimeDir(ManifestFixtures.TAG, WINDOWS).resolveSibling(
                "windows-x64.extracting")));
        assertEquals(LocalAiStatus.INSTALLED, installer.quickCheck());
        assertEquals(1, server.requestCount(ManifestFixtures.ZIP_PATH));
        assertEquals(1, server.requestCount(ManifestFixtures.MODEL_PATH));
        assertEquals(0, server.requestCount(ManifestFixtures.TGZ_PATH));

        // A second install downloads nothing: everything already matches.
        RecordingProgress again = new RecordingProgress();
        installer.install(again);
        assertEquals(1, server.requestCount(ManifestFixtures.ZIP_PATH));
        assertEquals(1, server.requestCount(ManifestFixtures.MODEL_PATH));
        assertTrue(again.steps.contains(InstallStep.INSTALLING));
        assertEquals(installed.installedAt(), installer.readInstalled().orElseThrow().installedAt());
    }

    @Test
    void installsTheTarGzRuntimeWithTheExecutableBit() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serve(server, zip, tgz, model);
        LocalAiInstaller installer = installer(manifest, LINUX);
        LocalAiInstalled installed = installer.install(LocalAiInstaller.Progress.none());
        Path exe = installer.serverExecutable(installed);
        assertEquals(installer.paths().runtimeDir(ManifestFixtures.TAG, LINUX).resolve("build").resolve("bin")
                .resolve("llama-server"), exe);
        assertTrue(Files.isRegularFile(exe));
        assertTrue(Files.isRegularFile(exe.resolveSibling("libggml.so")));
        if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
            assertTrue(Files.isExecutable(exe), "the server gets the executable bit");
            assertFalse(Files.isExecutable(exe.resolveSibling("libggml.so")), "mode 0644 in the archive stays");
            assertFalse(Files.isExecutable(exe.resolveSibling("LICENSE")));
        }
        assertEquals(LocalAiStatus.INSTALLED, installer.quickCheck());
        assertEquals(LocalAiStatus.INSTALLED, installer.verify(LocalAiInstaller.Progress.none()));
        assertEquals(0, server.requestCount(ManifestFixtures.ZIP_PATH));
    }

    @Test
    void aHashMismatchIsRejectedAndLeavesNoPartialFile() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serveWithBadModelHash(server, zip, tgz, model);
        LocalAiInstaller installer = installer(manifest, WINDOWS);
        LocalAiException e = assertThrows(LocalAiException.class,
                () -> installer.install(LocalAiInstaller.Progress.none()));
        assertEquals(LocalAiException.Kind.HASH_MISMATCH, e.kind());
        LocalAiPaths paths = installer.paths();
        assertFalse(Files.exists(paths.partFile(ManifestFixtures.MODEL_FILE)));
        assertFalse(Files.exists(paths.modelFile(ManifestFixtures.MODEL_FILE)));
        assertFalse(Files.exists(paths.installedFile()));
        assertTrue(Files.isRegularFile(paths.downloadFile("llama-" + ManifestFixtures.TAG + "-bin-win-cpu-x64.zip")),
                "the verified runtime archive is kept for the next attempt");
        assertEquals(LocalAiStatus.PARTIAL, installer.quickCheck());
    }

    @Test
    void aSizeMismatchIsRejected() throws Exception {
        byte[] shortModel = FakeArchives.randomBytes(1000, 3);
        String json = ManifestFixtures.json(server.baseUrl(), zip, tgz, model, dev.vanta.core.release.Sha256.hex(model));
        server.file(ManifestFixtures.ZIP_PATH, zip);
        server.file(ManifestFixtures.TGZ_PATH, tgz);
        server.file(ManifestFixtures.MODEL_PATH, shortModel);
        LocalAiInstaller installer = installer(LocalAiManifest.parse(json, false), WINDOWS);
        LocalAiException e = assertThrows(LocalAiException.class,
                () -> installer.install(LocalAiInstaller.Progress.none()));
        assertEquals(LocalAiException.Kind.SIZE_MISMATCH, e.kind());
        assertFalse(Files.exists(installer.paths().partFile(ManifestFixtures.MODEL_FILE)));
    }

    @Test
    void cancellingStopsTheDownloadAndRemovesThePartFile() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serve(server, zip, tgz, model);
        LocalAiInstaller installer = installer(manifest, WINDOWS);
        RecordingProgress progress = new RecordingProgress();
        progress.cancelAt = InstallStep.DOWNLOADING_MODEL;
        LocalAiException e = assertThrows(LocalAiException.class, () -> installer.install(progress));
        assertEquals(LocalAiException.Kind.CANCELLED, e.kind());
        LocalAiPaths paths = installer.paths();
        assertFalse(Files.exists(paths.partFile(ManifestFixtures.MODEL_FILE)));
        assertFalse(Files.exists(paths.modelFile(ManifestFixtures.MODEL_FILE)));
        assertFalse(Files.exists(paths.installedFile()));
    }

    @Test
    void httpErrorsAreReportedAsSuch() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serve(server, zip, tgz, model);
        server.fail(ManifestFixtures.ZIP_PATH, 404);
        LocalAiInstaller installer = installer(manifest, WINDOWS);
        LocalAiException e = assertThrows(LocalAiException.class,
                () -> installer.install(LocalAiInstaller.Progress.none()));
        assertEquals(LocalAiException.Kind.HTTP, e.kind());
        assertTrue(e.getMessage().contains("404"));
    }

    @Test
    void anArchiveWithoutTheServerIsInvalid() throws Exception {
        byte[] emptyZip = FakeArchives.zip(List.of("README.md"), List.of(new byte[] {1}));
        LocalAiManifest manifest = ManifestFixtures.serve(server, emptyZip, tgz, model);
        LocalAiInstaller installer = installer(manifest, WINDOWS);
        LocalAiException e = assertThrows(LocalAiException.class,
                () -> installer.install(LocalAiInstaller.Progress.none()));
        assertEquals(LocalAiException.Kind.INVALID_ARCHIVE, e.kind());
        assertFalse(Files.exists(installer.paths().runtimeDir(ManifestFixtures.TAG, WINDOWS)));
    }

    @Test
    void launcherManagedInstallsAreReadOnly() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serve(server, zip, tgz, model);
        LocalAiPaths launcher = LocalAiPaths.launcherManaged(dir.resolve("launcher").resolve("local-ai"));
        LocalAiInstaller installer = installer(manifest, WINDOWS, launcher, clock);
        assertEquals(LocalAiException.Kind.READ_ONLY,
                assertThrows(LocalAiException.class, () -> installer.install(LocalAiInstaller.Progress.none())).kind());
        assertEquals(LocalAiException.Kind.READ_ONLY, assertThrows(LocalAiException.class, installer::remove).kind());
        assertEquals(LocalAiStatus.NOT_INSTALLED, installer.quickCheck());
        assertTrue(server.requests.isEmpty(), "nothing was downloaded");

        // A complete launcher install is used read-only: quick check and verify work, installed.json is not rewritten.
        LocalAiInstaller launcherSide = installer(manifest, WINDOWS, LocalAiPaths.clientManaged(launcher.root()), clock);
        launcherSide.install(LocalAiInstaller.Progress.none());
        String before = Files.readString(launcher.installedFile());
        clock.advance(60_000);
        assertEquals(LocalAiStatus.INSTALLED, installer.quickCheck());
        assertEquals(LocalAiStatus.INSTALLED, installer.verify(LocalAiInstaller.Progress.none()));
        assertEquals(before, Files.readString(launcher.installedFile()));
    }

    @Test
    void unsupportedPlatformIsHonest() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serve(server, zip, tgz, model);
        LocalAiInstaller installer = installer(manifest, Platform.parse("windows-arm64").orElseThrow());
        assertEquals(LocalAiStatus.UNSUPPORTED_PLATFORM, installer.quickCheck());
        assertEquals(LocalAiException.Kind.UNSUPPORTED_PLATFORM,
                assertThrows(LocalAiException.class, () -> installer.install(LocalAiInstaller.Progress.none())).kind());
    }

    @Test
    void anUnresolvedManifestCannotInstall() throws Exception {
        LocalAiManifest template = LocalAiManifest.parse(ManifestFixtures.templateText(), true);
        LocalAiInstaller installer = installer(template, LINUX);
        assertEquals(LocalAiException.Kind.INVALID_MANIFEST,
                assertThrows(LocalAiException.class, () -> installer.install(LocalAiInstaller.Progress.none())).kind());
    }

    @Test
    void verifyFindsACorruptedModelAndQuickCheckSeesChangedFiles() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serve(server, zip, tgz, model);
        LocalAiInstaller installer = installer(manifest, LINUX);
        installer.install(LocalAiInstaller.Progress.none());
        Path modelFile = installer.modelFile();
        byte[] corrupt = model.clone();
        corrupt[1000] ^= 0x55;
        Files.write(modelFile, corrupt);
        Files.setLastModifiedTime(modelFile, java.nio.file.attribute.FileTime.fromMillis(clock.millis() + 5_000_000));
        assertEquals(LocalAiStatus.PARTIAL, installer.quickCheck(), "mtime changed");
        RecordingProgress progress = new RecordingProgress();
        assertEquals(LocalAiStatus.PARTIAL, installer.verify(progress));
        assertEquals(List.of(InstallStep.VERIFYING), progress.steps);

        // A re-install repairs it: the model is downloaded again, the runtime is kept.
        installer.install(LocalAiInstaller.Progress.none());
        assertArrayEquals(model, Files.readAllBytes(modelFile));
        assertEquals(2, server.requestCount(ManifestFixtures.MODEL_PATH));
        assertEquals(1, server.requestCount(ManifestFixtures.TGZ_PATH));
        assertEquals(LocalAiStatus.INSTALLED, installer.quickCheck());

        // Deleting installed.json alone leaves a PARTIAL state that the next install completes without downloads.
        Files.delete(installer.paths().installedFile());
        assertEquals(LocalAiStatus.PARTIAL, installer.quickCheck());
        installer.install(LocalAiInstaller.Progress.none());
        assertEquals(2, server.requestCount(ManifestFixtures.MODEL_PATH), "a matching model is re-hashed, not fetched");
        assertEquals(LocalAiStatus.INSTALLED, installer.quickCheck());
    }

    @Test
    void removeDeletesOnlyTheLocalAiDirectory() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serve(server, zip, tgz, model);
        LocalAiInstaller installer = installer(manifest, WINDOWS);
        installer.install(LocalAiInstaller.Progress.none());
        Path sibling = dir.resolve("settings.json");
        Files.writeString(sibling, "{}");
        installer.remove();
        assertFalse(Files.exists(installer.paths().root()));
        assertTrue(Files.exists(sibling));
        assertEquals(LocalAiStatus.NOT_INSTALLED, installer.quickCheck());
    }

    @Test
    void progressReportsBytesAndSpeedWhileTheClockMoves() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serve(server, zip, tgz, model);
        Clock ticking = new Clock() {
            private long now = 1_791_115_200_000L;

            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(millis());
            }

            @Override
            public long millis() {
                now += 150;
                return now;
            }
        };
        LocalAiInstaller installer = installer(manifest, WINDOWS, LocalAiPaths.clientManaged(dir.resolve("ai")), ticking);
        RecordingProgress progress = new RecordingProgress();
        installer.install(progress);
        assertTrue(progress.maxSpeed > 0, "speed is derived from bytes per elapsed clock time");
        assertTrue(progress.events.contains("downloading_model:" + model.length + "/" + model.length),
                progress.events.toString());
        assertTrue(progress.events.stream().anyMatch(e -> e.startsWith("downloading_model:") && !e.startsWith(
                "downloading_model:0/") && !e.startsWith("downloading_model:" + model.length)),
                "intermediate progress: " + progress.events);
    }

    @Test
    void refusesDownloadUrlsOutsideTheManifestRule() throws Exception {
        LocalAiManifest manifest = ManifestFixtures.serve(server, zip, tgz, model);
        LocalAiInstaller installer = installer(manifest, WINDOWS);
        LocalAiException e = assertThrows(LocalAiException.class, () -> installer.download(
                "http://example.com/evil.bin", "evil.bin", 1, "0".repeat(64), InstallStep.DOWNLOADING_MODEL,
                LocalAiInstaller.Progress.none()));
        assertEquals(LocalAiException.Kind.INVALID_MANIFEST, e.kind());
    }
}
