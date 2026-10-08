package dev.vanta.launcher.core.ai;

import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstallProgress;
import dev.vanta.launcher.core.install.InstallStep;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.net.HashAlgorithm;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.net.JdkHttpTransport;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.core.util.Sleeper;
import dev.vanta.launcher.testutil.FakeLocalAi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end against a {@link FakeLocalAi}: download, verify, extract (zip and tar.gz), executable bit, installed.json,
 * the note, quick and full checks, refused mismatches, updates and removal.
 */
class LocalAiServiceTest {

    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8");
    private static final OsInfo WINDOWS = new OsInfo("windows", "x64", "10.0");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;
    private FakeLocalAi ai;
    private Downloader downloader;
    private LauncherPaths paths;

    @BeforeEach
    void start() throws IOException {
        ai = new FakeLocalAi();
        downloader = new Downloader(new JdkHttpTransport("VANTA-Launcher/test"), Sleeper.NONE, 3, 2);
        paths = new LauncherPaths(tmp.resolve("data"));
    }

    @AfterEach
    void stop() {
        ai.close();
        downloader.close();
    }

    private LocalAiService service(final OsInfo os) {
        return service(os, ai.manifest());
    }

    private LocalAiService service(final OsInfo os, final LocalAiManifest manifest) {
        return new LocalAiService(downloader, paths, os, () -> manifest, CLOCK);
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

    @Test
    void installsATarGzRuntimeAndTheModelAndRecordsEverything() throws Exception {
        final LocalAiService service = service(LINUX);
        assertEquals("linux-x64", service.platformKey());
        assertEquals(paths.dataDir().resolve("local-ai"), service.dir());
        final LocalAiReport before = service.status();
        assertEquals(LocalAiState.NOT_INSTALLED, before.state());
        assertTrue(before.installable());
        assertEquals(ai.manifest().downloadBytes("linux-x64"), before.downloadBytes());
        assertEquals("llama.cpp b11429, Qwen3-1.7B Q8_0", before.describeVersions());

        final Recorder recorder = new Recorder();
        final LocalAiReport after = service.install(recorder, new CancellationToken());

        assertEquals(LocalAiState.INSTALLED, after.state(), after.problems().toString());
        assertFalse(after.outdated());
        assertTrue(after.problems().isEmpty());
        // runtime/<tag>/<platform>/<serverPath>, executable
        final Path server = service.runtimeDir("b11429").resolve(FakeLocalAi.UNIX_SERVER);
        assertTrue(Files.isRegularFile(server), "server extracted at its serverPath");
        assertArrayEquals(FakeLocalAi.SERVER_BYTES, Files.readAllBytes(server));
        if (Files.getFileStore(server).supportsFileAttributeView("posix")) {
            final Set<PosixFilePermission> perms = Files.getPosixFilePermissions(server);
            assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE), perms.toString());
            assertTrue(Files.isExecutable(server));
        }
        assertTrue(Files.isRegularFile(service.runtimeDir("b11429").resolve("build/bin/libggml.so")));
        assertEquals(Optional.of(server), after.serverExecutable());
        // models/<file> with the exact bytes
        final Path model = service.modelsDir().resolve(FakeLocalAi.MODEL_FILE);
        assertArrayEquals(ai.model(), Files.readAllBytes(model));
        assertEquals(Optional.of(model), after.modelFile());
        // the archive is gone, downloads/ is empty, logs/ exists for the client
        assertFalse(Files.exists(service.downloadsDir().resolve(ai.manifest().platform("linux-x64").orElseThrow().file())));
        try (var leftovers = Files.list(service.downloadsDir())) {
            assertEquals(0, leftovers.count());
        }
        assertTrue(Files.isDirectory(service.logsDir()));
        // installed.json: the installed manifest section, platform, times and the verified files
        final LocalAiInstalled installed = Json.read(service.installedFile(), LocalAiInstalled.class);
        assertEquals(LocalAiInstalled.SCHEMA_VERSION, installed.schemaVersion());
        assertEquals("linux-x64", installed.platform());
        assertEquals("2026-10-08T12:00:00Z", installed.installedAt());
        assertEquals("2026-10-08T12:00:00Z", installed.verifiedAt());
        assertEquals("b11429", installed.runtime().tag());
        assertEquals(ai.manifest().platform("linux-x64").orElseThrow(), installed.runtime().file());
        assertEquals(ai.manifest().model(), installed.model());
        assertEquals("runtime/b11429/linux-x64/build/bin/llama-server", installed.file(LocalAiInstalled.ROLE_SERVER).orElseThrow().path());
        assertEquals(Checksums.hex(FakeLocalAi.SERVER_BYTES, HashAlgorithm.SHA256), installed.file(LocalAiInstalled.ROLE_SERVER).orElseThrow().sha256());
        assertEquals("models/" + FakeLocalAi.MODEL_FILE, installed.file(LocalAiInstalled.ROLE_MODEL).orElseThrow().path());
        assertEquals(ai.manifest().model().sha256(), installed.file(LocalAiInstalled.ROLE_MODEL).orElseThrow().sha256());
        assertEquals(ai.model().length, installed.file(LocalAiInstalled.ROLE_MODEL).orElseThrow().size());
        assertEquals("llama.cpp b11429, Qwen3-1.7B Q8_0", installed.describe());
        // the note in the game folder
        assertEquals(Optional.of(service.dir().toAbsolutePath().normalize()), LocalAiNote.read(paths.instanceDir()));
        assertTrue(after.bytesOnDisk() >= ai.model().length + FakeLocalAi.SERVER_BYTES.length);
        // progress: one step, bytes as units, the step names in order
        assertFalse(recorder.progress().isEmpty());
        assertTrue(recorder.progress().stream().allMatch(p -> p.step() == InstallStep.LOCAL_AI && p.stepCount() == 1));
        final InstallProgress last = recorder.progress().get(recorder.progress().size() - 1);
        assertEquals(last.total(), last.done(), "the bar ends full");
        assertEquals(1d, last.fraction(), 1e-9);
        final String joined = String.join("\n", recorder.logs());
        for (String step : List.of(LocalAiService.STEP_CHECKING, LocalAiService.STEP_RUNTIME, LocalAiService.STEP_MODEL, LocalAiService.STEP_VERIFYING,
            LocalAiService.STEP_INSTALLING, LocalAiService.STEP_DONE)) {
            assertTrue(joined.contains(step), "log names step '" + step + "':\n" + joined);
        }
        assertTrue(joined.contains("Downloaded Local AI runtime"), joined);
        assertTrue(joined.contains("Downloaded Local AI model"), joined);
        assertTrue(joined.contains("Noted the Local AI folder"), joined);
        assertEquals(1, ai.server().hits(ai.archivePath("linux-x64")));
        assertEquals(1, ai.server().hits(ai.modelPath()));

        // Installing again downloads nothing and only confirms.
        final Recorder again = new Recorder();
        final LocalAiReport second = service.install(again, new CancellationToken());
        assertEquals(LocalAiState.INSTALLED, second.state());
        assertEquals(1, ai.server().hits(ai.archivePath("linux-x64")), "the runtime is not downloaded again");
        assertEquals(1, ai.server().hits(ai.modelPath()), "the model is not downloaded again");
        assertTrue(String.join("\n", again.logs()).contains("Local AI already installed"), again.logs().toString());
    }

    @Test
    void installsAZipRuntimeForWindows() throws Exception {
        final LocalAiService service = service(WINDOWS);
        assertEquals("windows-x64", service.platformKey());
        final LocalAiReport after = service.install(new Recorder(), new CancellationToken());
        assertEquals(LocalAiState.INSTALLED, after.state(), after.problems().toString());
        final Path server = service.runtimeDir("b11429").resolve(FakeLocalAi.WINDOWS_SERVER);
        assertTrue(Files.isRegularFile(server));
        assertTrue(Files.isRegularFile(service.runtimeDir("b11429").resolve("ggml.dll")));
        assertEquals("runtime/b11429/windows-x64/llama-server.exe",
            after.installed().orElseThrow().file(LocalAiInstalled.ROLE_SERVER).orElseThrow().path());
    }

    @Test
    void aModelWithTheWrongDigestIsRefusedAndNothingIsRecorded() throws Exception {
        ai.corruptModel();
        final LocalAiService service = service(LINUX);
        final Recorder recorder = new Recorder();
        final IntegrityException e = assertThrows(IntegrityException.class, () -> service.install(recorder, new CancellationToken()));
        assertTrue(e.getMessage().contains("SHA256 mismatch"), e.getMessage());
        assertFalse(Files.exists(service.modelsDir().resolve(FakeLocalAi.MODEL_FILE)), "the corrupt model is deleted");
        assertFalse(Files.exists(service.installedFile()), "no installed.json for a failed install");
        assertEquals(3, ai.server().hits(ai.modelPath()), "the downloader retried the transfer");
        final LocalAiReport report = service.status();
        assertEquals(LocalAiState.PARTIAL, report.state(), "the runtime is in place, the model is not");
        assertTrue(report.problems().get(0).contains("installed.json is missing"), report.problems().toString());
    }

    @Test
    void anArchiveWithTheWrongDigestIsRefusedBeforeExtraction() throws Exception {
        ai.corruptArchive("linux-x64");
        final LocalAiService service = service(LINUX);
        assertThrows(IntegrityException.class, () -> service.install(new Recorder(), new CancellationToken()));
        assertFalse(Files.exists(service.runtimeDir("b11429")), "nothing was extracted");
        assertFalse(Files.exists(service.dir().resolve("runtime").resolve("b11429").resolve("linux-x64.extracting")));
        assertEquals(0, ai.server().hits(ai.modelPath()), "the model is not fetched after the runtime failed");
    }

    @Test
    void anArchiveWithoutTheServerPathFails() throws Exception {
        final LocalAiManifest.PlatformFile linux = ai.manifest().platform("linux-x64").orElseThrow();
        final LocalAiManifest.PlatformFile wrongPath = new LocalAiManifest.PlatformFile(linux.file(), linux.url(), linux.size(), linux.sha256(),
            "bin/llama-server");
        final LocalAiManifest manifest = new LocalAiManifest(1, ai.manifest().resolvedAt(),
            new LocalAiManifest.Runtime("llama.cpp", "llama-server", "b11429", "MIT", ai.manifest().runtime().sourceUrl(),
                ai.manifest().runtime().releaseUrl(), java.util.Map.of("linux-x64", wrongPath)), ai.manifest().model(), ai.manifest().requirements());
        final LocalAiService service = service(LINUX, manifest);
        final IOException e = assertThrows(IOException.class, () -> service.install(new Recorder(), new CancellationToken()));
        assertTrue(e.getMessage().contains("does not contain bin/llama-server"), e.getMessage());
        assertFalse(Files.exists(service.runtimeDir("b11429")), "the staging folder never became the runtime");
    }

    @Test
    void unsupportedPlatformAndMissingManifestAreHonestStates() throws Exception {
        final LocalAiService x86 = service(new OsInfo("linux", "x86", "6.8"));
        final LocalAiReport unsupported = x86.status();
        assertEquals(LocalAiState.UNSUPPORTED_PLATFORM, unsupported.state());
        assertFalse(unsupported.installable());
        assertEquals(List.of("Local AI is not available for linux/x86"), unsupported.problems());
        final LocalAiUnavailableException e1 = assertThrows(LocalAiUnavailableException.class, () -> x86.install(new Recorder(), new CancellationToken()));
        assertEquals(LocalAiState.UNSUPPORTED_PLATFORM, e1.state());

        final LocalAiService none = new LocalAiService(downloader, paths, LINUX, () -> {
            throw new IOException("This build of the launcher contains no Local AI manifest");
        }, CLOCK);
        final LocalAiReport unavailable = none.status();
        assertEquals(LocalAiState.UNAVAILABLE, unavailable.state());
        assertTrue(unavailable.manifest().isEmpty());
        assertEquals(List.of("This build of the launcher contains no Local AI manifest"), unavailable.problems());
        final LocalAiUnavailableException e2 = assertThrows(LocalAiUnavailableException.class, () -> none.install(new Recorder(), new CancellationToken()));
        assertEquals(LocalAiState.UNAVAILABLE, e2.state());
        assertFalse(Files.exists(none.dir().resolve(LocalAiService.INSTALLED_FILE)));
    }

    @Test
    void quickAndFullChecksFindMissingChangedAndTamperedFiles() throws Exception {
        final LocalAiService service = service(LINUX);
        service.install(new Recorder(), new CancellationToken());
        final Path model = service.modelsDir().resolve(FakeLocalAi.MODEL_FILE);
        final Path server = service.runtimeDir("b11429").resolve(FakeLocalAi.UNIX_SERVER);

        assertEquals(LocalAiState.INSTALLED, service.status().state());
        assertEquals(LocalAiState.INSTALLED, service.verify().state());

        // Same bytes, newer time stamp: the quick check reports it, the full check hashes and accepts it, then records the new time.
        final FileTime later = FileTime.fromMillis(Files.getLastModifiedTime(model).toMillis() + 60_000);
        Files.setLastModifiedTime(model, later);
        final LocalAiReport touched = service.status();
        assertEquals(LocalAiState.PARTIAL, touched.state());
        assertTrue(touched.problems().get(0).contains("changed since the install"), touched.problems().toString());
        final LocalAiReport rehashed = service.verify();
        assertEquals(LocalAiState.INSTALLED, rehashed.state(), rehashed.problems().toString());
        assertEquals(LocalAiState.INSTALLED, service.status().state(), "the refreshed modification time is recorded");

        // Tampered content with the same size: only the full check sees it.
        final byte[] bytes = Files.readAllBytes(model);
        bytes[10] ^= 0x7F;
        Files.write(model, bytes);
        Files.setLastModifiedTime(model, later);
        final LocalAiReport tampered = service.verify();
        assertEquals(LocalAiState.PARTIAL, tampered.state());
        assertTrue(tampered.problems().get(0).contains("does not match its SHA-256"), tampered.problems().toString());
        // Install repairs it: the model is downloaded again, the runtime is kept.
        final int archiveHits = ai.server().hits(ai.archivePath("linux-x64"));
        final LocalAiReport repaired = service.install(new Recorder(), new CancellationToken());
        assertEquals(LocalAiState.INSTALLED, repaired.state(), repaired.problems().toString());
        assertArrayEquals(ai.model(), Files.readAllBytes(model));
        assertEquals(archiveHits, ai.server().hits(ai.archivePath("linux-x64")), "the intact runtime is not downloaded again");

        // A missing file: both checks see it.
        Files.delete(server);
        final LocalAiReport missing = service.status();
        assertEquals(LocalAiState.PARTIAL, missing.state());
        assertTrue(missing.problems().get(0).startsWith("missing: runtime/b11429/linux-x64/build/bin/llama-server"), missing.problems().toString());
        assertEquals(LocalAiState.PARTIAL, service.verify().state());
        // A short file: size mismatch.
        Files.writeString(server, "x", StandardCharsets.UTF_8);
        final LocalAiReport shortFile = service.status();
        assertTrue(shortFile.problems().get(0).contains("bytes, expected"), shortFile.problems().toString());
    }

    @Test
    void aNewerManifestMarksTheInstallOutdatedAndTheUpdateReplacesOldFiles() throws Exception {
        final LocalAiService service = service(LINUX);
        service.install(new Recorder(), new CancellationToken());
        try (FakeLocalAi newer = new FakeLocalAi(ai.server(), false, "b11500", 320_000)) {
            final LocalAiService updated = service(LINUX, newer.manifest());
            final LocalAiReport check = updated.status();
            assertEquals(LocalAiState.INSTALLED, check.state());
            assertTrue(check.outdated(), "the manifest names a newer release");
            assertEquals("llama.cpp b11429, Qwen3-1.7B Q8_0", check.describeVersions(), "what is installed is described, not what is available");

            final Recorder recorder = new Recorder();
            final LocalAiReport after = updated.install(recorder, new CancellationToken());
            assertEquals(LocalAiState.INSTALLED, after.state(), after.problems().toString());
            assertFalse(after.outdated());
            assertEquals("b11500", after.installed().orElseThrow().runtime().tag());
            assertTrue(Files.isRegularFile(updated.runtimeDir("b11500").resolve(FakeLocalAi.UNIX_SERVER)));
            assertFalse(Files.exists(service.runtimeDir("b11429")), "the old runtime is removed after a successful update");
            assertArrayEquals(newer.model(), Files.readAllBytes(updated.modelsDir().resolve(FakeLocalAi.MODEL_FILE)));
            assertTrue(String.join("\n", recorder.logs()).contains("Removed old runtime b11429"), recorder.logs().toString());
        }
    }

    @Test
    void removeDeletesOnlyTheLocalAiFolder() throws Exception {
        final LocalAiService service = service(LINUX);
        assertFalse(service.remove(), "nothing to remove yet");
        service.install(new Recorder(), new CancellationToken());
        final Path neighbour = paths.dataDir().resolve("settings.json");
        Files.writeString(neighbour, "{}");
        assertTrue(service.sizeOnDisk() > 0);

        assertTrue(service.remove());
        assertFalse(Files.exists(service.dir()));
        assertTrue(Files.exists(neighbour), "nothing outside local-ai/ is touched");
        assertTrue(Files.exists(paths.instanceDir()), "the game folder stays");
        assertEquals(LocalAiState.NOT_INSTALLED, service.status().state());
        assertEquals(0L, service.sizeOnDisk());
        assertFalse(service.remove());
    }

    @Test
    void cancellationStopsTheInstallAndLeavesNoRecord() throws Exception {
        final LocalAiService service = service(LINUX);
        final CancellationToken token = new CancellationToken();
        final InstallListener cancelAfterRuntime = new InstallListener() {
            @Override
            public void onLog(final String message) {
                if (message.startsWith("Downloaded Local AI runtime")) {
                    token.cancel();
                }
            }
        };
        assertThrows(java.util.concurrent.CancellationException.class, () -> service.install(cancelAfterRuntime, token));
        assertFalse(Files.exists(service.installedFile()));
        assertEquals(0, ai.server().hits(ai.modelPath()), "the model download never started");
    }

    @Test
    void theNoteNamesTheLauncherFolderAndIsWrittenOnce() throws Exception {
        final LocalAiService service = service(LINUX);
        final Path game = tmp.resolve("another-game");
        assertTrue(service.writeNote(game));
        assertFalse(service.writeNote(game));
        assertEquals(Optional.of(service.dir().toAbsolutePath().normalize()), LocalAiNote.read(game));
        assertEquals(LocalAiNote.file(game), game.resolve("config/vanta/local-ai.json"));
    }
}
