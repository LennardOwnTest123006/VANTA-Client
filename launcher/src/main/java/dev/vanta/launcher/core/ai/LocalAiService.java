package dev.vanta.launcher.core.ai;

import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstallPlan;
import dev.vanta.launcher.core.install.InstallProgress;
import dev.vanta.launcher.core.install.InstallStep;
import dev.vanta.launcher.core.java.ArchiveExtractor;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.net.DownloadResult;
import dev.vanta.launcher.core.net.Downloader;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.ByteSizes;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Installs, checks and removes the launcher-managed Local AI: the {@code llama-server} runtime from a llama.cpp GitHub
 * release and the GGUF model from Hugging Face, both named with size and SHA-256 by the {@link LocalAiManifest} of this
 * build. Everything lives below {@code <data>/local-ai/}:
 *
 * <pre>
 * local-ai/
 * ├── installed.json                 what is installed, when, and the verified sizes and digests
 * ├── runtime/&lt;tag&gt;/&lt;platform&gt;/      the extracted release archive (the server executable at its serverPath)
 * ├── models/&lt;file&gt;                  the GGUF model
 * ├── downloads/                     archives while they download (removed after extraction)
 * └── logs/                          llama-server.log, written by the client when it runs the server
 * </pre>
 *
 * <p>Downloads go through the launcher's {@link Downloader} (streamed SHA-256, atomic move, retries, stall watchdog)
 * with a per-request timeout long enough for a multi-gigabyte model; a size or digest mismatch deletes the file and
 * fails the install. Archives are extracted with {@link ArchiveExtractor} (path-traversal safe) into a staging folder
 * that is moved into place only when the server executable is really inside. Nothing downloaded is ever executed by the
 * launcher: the VANTA Client starts the server, from this folder, read-only. "Remove" deletes only this folder.</p>
 */
public final class LocalAiService {

    /** Exchange timeout for the runtime archive (tens of megabytes). */
    public static final Duration RUNTIME_TIMEOUT = Duration.ofHours(1);
    /** Exchange timeout for the model (gigabytes, slow connections); the stall watchdog still aborts a dead transfer. */
    public static final Duration MODEL_TIMEOUT = Duration.ofHours(6);
    /** Name of the record file in the Local AI folder. */
    public static final String INSTALLED_FILE = "installed.json";
    /** Folder of extracted runtimes. */
    public static final String RUNTIME_DIR = "runtime";
    /** Folder of model files. */
    public static final String MODELS_DIR = "models";
    /** Folder of archives while they download. */
    public static final String DOWNLOADS_DIR = "downloads";
    /** Folder of the server log the client writes. */
    public static final String LOGS_DIR = "logs";

    /** Step message: checking the manifest, the platform and what is already in place. */
    public static final String STEP_CHECKING = "Checking Local AI";
    /** Step message: downloading the runtime archive. */
    public static final String STEP_RUNTIME = "Downloading Local AI runtime";
    /** Step message: downloading the model. */
    public static final String STEP_MODEL = "Downloading Local AI model";
    /** Step message: verifying sizes and digests. */
    public static final String STEP_VERIFYING = "Verifying files";
    /** Step message: extracting the archive and writing the record. */
    public static final String STEP_INSTALLING = "Installing";
    /** Step message: everything is in place. */
    public static final String STEP_DONE = "Local AI ready";

    private static final Logger LOG = Logger.getLogger("VANTA.LocalAi");
    private static final long PROGRESS_STRIDE = 2_000_000L;
    private static final String EXTRACTING_SUFFIX = ".extracting";

    /** Where the manifest comes from (the embedded resource, a file named by {@value LocalAiManifest#MANIFEST_ENV}, a test). */
    @FunctionalInterface
    public interface ManifestSource {
        /**
         * @return the manifest
         * @throws IOException when there is none or it is invalid
         */
        LocalAiManifest load() throws IOException;
    }

    private final Downloader downloader;
    private final LauncherPaths paths;
    private final OsInfo os;
    private final ManifestSource source;
    private final Clock clock;
    private final String platformKey;
    private volatile LocalAiManifest cached;

    /**
     * @param downloader downloader (verified transfers)
     * @param paths      launcher paths ({@link LauncherPaths#localAiDir()} is the install folder)
     * @param os         host platform
     * @param source     manifest source
     * @param clock      clock for {@code installedAt} / {@code verifiedAt}
     */
    public LocalAiService(final Downloader downloader, final LauncherPaths paths, final OsInfo os, final ManifestSource source,
                          final Clock clock) {
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.os = Objects.requireNonNull(os, "os");
        this.source = Objects.requireNonNull(source, "source");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.platformKey = LocalAiManifest.platformKey(os);
    }

    // ---------------------------------------------------------------- paths

    /** @return the Local AI folder */
    public Path dir() {
        return paths.localAiDir();
    }

    /** @return {@code installed.json} */
    public Path installedFile() {
        return dir().resolve(INSTALLED_FILE);
    }

    /**
     * @param tag release tag
     * @return {@code runtime/<tag>/<platform>}
     */
    public Path runtimeDir(final String tag) {
        return dir().resolve(RUNTIME_DIR).resolve(tag).resolve(platformKey);
    }

    /** @return {@code models/} */
    public Path modelsDir() {
        return dir().resolve(MODELS_DIR);
    }

    /** @return {@code downloads/} */
    public Path downloadsDir() {
        return dir().resolve(DOWNLOADS_DIR);
    }

    /** @return {@code logs/} */
    public Path logsDir() {
        return dir().resolve(LOGS_DIR);
    }

    /** @return the host's platform key ({@code windows-x64}, ...) */
    public String platformKey() {
        return platformKey;
    }

    /** @return {@code <os>/<arch>} for messages, e.g. {@code linux/x86} */
    public String platformLabel() {
        return platformKey.replace('-', '/');
    }

    // ---------------------------------------------------------------- manifest

    /**
     * @return the manifest of this build (strictly validated, cached after the first read)
     * @throws IOException when it is missing or invalid
     */
    public LocalAiManifest manifest() throws IOException {
        LocalAiManifest m = cached;
        if (m == null) {
            m = source.load();
            cached = m;
        }
        return m;
    }

    /**
     * @return the platform archive for this host
     * @throws LocalAiUnavailableException when there is no manifest or it lists no runtime for this platform
     * @throws IOException                 when the manifest is unreadable
     */
    public LocalAiManifest.PlatformFile platformFile() throws IOException {
        final LocalAiManifest manifest;
        try {
            manifest = manifest();
        } catch (LocalAiUnavailableException e) {
            throw e;
        } catch (IOException e) {
            throw new LocalAiUnavailableException(LocalAiState.UNAVAILABLE, e.getMessage());
        }
        return manifest.platform(platformKey).orElseThrow(() -> new LocalAiUnavailableException(LocalAiState.UNSUPPORTED_PLATFORM,
            "Local AI is not available for " + platformLabel()));
    }

    // ---------------------------------------------------------------- status

    /**
     * Quick check: {@code installed.json} plus the size and modification time of every recorded file. Nothing is hashed.
     *
     * @return report
     * @throws IOException when the folder cannot be read
     */
    public LocalAiReport status() throws IOException {
        return inspect(false);
    }

    /**
     * Full check: every recorded file is hashed again. When everything matches, {@code verifiedAt} is updated.
     *
     * @return report
     * @throws IOException when the folder cannot be read
     */
    public LocalAiReport verify() throws IOException {
        return inspect(true);
    }

    private LocalAiReport inspect(final boolean fullHash) throws IOException {
        final List<String> problems = new ArrayList<>();
        Optional<LocalAiManifest> manifest = Optional.empty();
        try {
            manifest = Optional.of(manifest());
        } catch (IOException e) {
            problems.add(e.getMessage());
        }
        final Optional<LocalAiInstalled> installed = readInstalled(problems);
        final long bytes = sizeOnDisk();
        if (installed.isPresent()) {
            final LocalAiInstalled record = installed.get();
            final List<String> fileProblems = new ArrayList<>();
            final List<LocalAiInstalled.InstalledFile> refreshed = new ArrayList<>();
            if (record.file(LocalAiInstalled.ROLE_SERVER).isEmpty()) {
                fileProblems.add("installed.json records no server executable");
            }
            if (record.file(LocalAiInstalled.ROLE_MODEL).isEmpty()) {
                fileProblems.add("installed.json records no model file");
            }
            for (LocalAiInstalled.InstalledFile f : record.files()) {
                final Path file = dir().resolve(f.path());
                if (!Files.isRegularFile(file)) {
                    fileProblems.add("missing: " + f.path());
                    continue;
                }
                final long size = Files.size(file);
                if (size != f.size()) {
                    fileProblems.add(f.path() + " has " + size + " bytes, expected " + f.size());
                    continue;
                }
                final long modified = Files.getLastModifiedTime(file).toMillis();
                if (fullHash) {
                    final String actual = Checksums.sha256Hex(file);
                    if (!actual.equals(f.sha256())) {
                        fileProblems.add(f.path() + " does not match its SHA-256 (expected " + f.sha256() + ", computed " + actual + ")");
                        continue;
                    }
                    refreshed.add(new LocalAiInstalled.InstalledFile(f.role(), f.path(), size, f.sha256(), modified));
                } else if (modified != f.modifiedMillis()) {
                    fileProblems.add(f.path() + " changed since the install (modification time differs)");
                }
            }
            final boolean ok = fileProblems.isEmpty();
            problems.addAll(fileProblems);
            LocalAiInstalled current = record;
            if (ok && fullHash) {
                current = record.withVerified(Instant.now(clock).toString(), refreshed);
                Json.write(installedFile(), current);
            }
            final boolean outdated = manifest.isPresent() && isOutdated(manifest.get(), current);
            return new LocalAiReport(ok ? LocalAiState.INSTALLED : LocalAiState.PARTIAL, platformKey, dir(), manifest, Optional.of(current),
                bytes, problems, outdated);
        }
        if (manifest.isEmpty()) {
            return new LocalAiReport(LocalAiState.UNAVAILABLE, platformKey, dir(), manifest, Optional.empty(), bytes, problems, false);
        }
        if (manifest.get().platform(platformKey).isEmpty()) {
            problems.add("Local AI is not available for " + platformLabel());
            return new LocalAiReport(LocalAiState.UNSUPPORTED_PLATFORM, platformKey, dir(), manifest, Optional.empty(), bytes, problems, false);
        }
        if (bytes > 0) {
            problems.add("the install did not finish (" + INSTALLED_FILE + " is missing); Install completes it");
            return new LocalAiReport(LocalAiState.PARTIAL, platformKey, dir(), manifest, Optional.empty(), bytes, problems, false);
        }
        return new LocalAiReport(LocalAiState.NOT_INSTALLED, platformKey, dir(), manifest, Optional.empty(), bytes, problems, false);
    }

    private Optional<LocalAiInstalled> readInstalled(final List<String> problems) {
        if (!Files.isRegularFile(installedFile())) {
            return Optional.empty();
        }
        try {
            final LocalAiInstalled record = Json.read(installedFile(), LocalAiInstalled.class);
            if (record == null || record.runtime() == null || record.model() == null) {
                problems.add(INSTALLED_FILE + " is incomplete");
                return Optional.empty();
            }
            return Optional.of(record);
        } catch (IOException | RuntimeException e) {
            problems.add(INSTALLED_FILE + " is unreadable: " + e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isOutdated(final LocalAiManifest manifest, final LocalAiInstalled installed) {
        final Optional<LocalAiManifest.PlatformFile> platform = manifest.platform(installed.platform());
        final boolean runtimeDiffers = !manifest.runtime().tag().equals(installed.runtime().tag())
            || platform.isEmpty() || installed.runtime().file() == null || !platform.get().sha256().equals(installed.runtime().file().sha256());
        final boolean modelDiffers = !manifest.model().sha256().equals(installed.model().sha256())
            || !manifest.model().file().equals(installed.model().file());
        return runtimeDiffers || modelDiffers;
    }

    /**
     * @return bytes below the Local AI folder (0 when it does not exist)
     * @throws IOException when the folder cannot be read
     */
    public long sizeOnDisk() throws IOException {
        final Path dir = dir();
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            long sum = 0;
            for (Path p : walk.toList()) {
                if (Files.isRegularFile(p)) {
                    sum += Files.size(p);
                }
            }
            return sum;
        }
    }

    // ---------------------------------------------------------------- install

    /**
     * Downloads, verifies and extracts whatever is missing, writes {@code installed.json} and the note into the VANTA game
     * folder. A complete and current install is only checked, nothing is downloaded again.
     *
     * @param listener progress (one step {@link InstallStep#LOCAL_AI}; {@code done}/{@code total} are bytes)
     * @param token    cancellation
     * @return the report after the install
     * @throws LocalAiUnavailableException when there is no manifest or no runtime for this platform
     * @throws dev.vanta.launcher.core.install.InsufficientDiskSpaceException when the disk is too full
     * @throws dev.vanta.launcher.core.net.IntegrityException when a download does not match the manifest
     * @throws IOException          on any other failure
     * @throws InterruptedException when interrupted
     */
    public LocalAiReport install(final InstallListener listener, final CancellationToken token) throws IOException, InterruptedException {
        final InstallListener l = listener == null ? InstallListener.NONE : listener;
        final CancellationToken t = token == null ? CancellationToken.NONE : token;
        final Progress progress = new Progress(l);
        progress.phase(STEP_CHECKING, null);
        final LocalAiManifest.PlatformFile platform = platformFile();
        final LocalAiManifest manifest = manifest();
        // The quick check is not trusted here: an explicit install re-hashes what is in place (a repair after "Verify
        // files" found a mismatch must download the damaged file again) and downloads only what is missing or wrong.
        final LocalAiReport before = status();
        t.throwIfCancelled();
        l.onLog("Local AI: " + manifest.describe() + " for " + platformLabel());

        final Path archive = downloadsDir().resolve(platform.file());
        final Path runtimeDir = runtimeDir(manifest.runtime().tag());
        final Path server = runtimeDir.resolve(platform.serverPath()).normalize();
        final DownloadRequest runtimeRequest = new DownloadRequest(URI.create(platform.url()), archive, platform.size(),
            Checksum.sha256(platform.sha256()), "Local AI runtime " + platform.file(), RUNTIME_TIMEOUT);
        final DownloadRequest modelRequest = new DownloadRequest(URI.create(manifest.model().url()), modelsDir().resolve(manifest.model().file()),
            manifest.model().size(), Checksum.sha256(manifest.model().sha256()), "Local AI model " + manifest.model().file(), MODEL_TIMEOUT);
        final boolean runtimeNeeded = !runtimeInPlace(before, manifest, platform, server);
        final boolean modelNeeded = !downloader.isValid(modelRequest);
        t.throwIfCancelled();

        final long remaining = (runtimeNeeded ? platform.size() * 2 : 0L) + (modelNeeded ? manifest.model().size() : 0L);
        new InstallPlan(List.of(InstallStep.LOCAL_AI), remaining).checkDiskSpace(dir());
        Files.createDirectories(downloadsDir());
        Files.createDirectories(modelsDir());
        Files.createDirectories(logsDir());
        final long toDownload = (runtimeNeeded ? platform.size() : 0L) + (modelNeeded ? manifest.model().size() : 0L);
        progress.total(toDownload);
        l.onLog(toDownload == 0 ? "Nothing to download: runtime and model are in place and match their SHA-256"
            : "Downloading up to " + ByteSizes.format(toDownload) + " into " + dir() + " (" + manifest.runtime().name() + " from "
            + hostOf(platform.url()) + ", " + manifest.model().name() + " from " + hostOf(manifest.model().url()) + ")");

        // 1. Runtime archive -> runtime/<tag>/<platform>/
        if (runtimeNeeded) {
            progress.phase(STEP_RUNTIME, platform.file());
            downloader.download(runtimeRequest, progress.downloads(STEP_RUNTIME), t);
            t.throwIfCancelled();
            progress.phase(STEP_INSTALLING, "Extracting " + platform.file());
            extract(archive, runtimeDir, platform, server);
            l.onLog("Extracted " + platform.file() + " to " + runtimeDir);
        } else {
            l.onLog("Verified runtime " + manifest.runtime().tag() + " (" + platform.serverPath() + " is in place)");
        }

        // 2. Model -> models/<file> (skipped, after a full hash, when the file already matches)
        progress.phase(STEP_MODEL, manifest.model().file());
        final DownloadResult modelResult = downloader.download(modelRequest, progress.downloads(STEP_MODEL), t);
        t.throwIfCancelled();
        final boolean unchanged = !runtimeNeeded && modelResult.skipped() && before.installed().isPresent() && !before.outdated();

        // 3. Verify: the downloads were hashed while streaming (or by isValid); record sizes, digests and times.
        progress.phase(STEP_VERIFYING, null);
        if (!Files.isRegularFile(server)) {
            throw new IOException("The runtime at " + runtimeDir + " has no " + platform.serverPath());
        }
        setExecutable(server);
        final String serverSha = Checksums.sha256Hex(server);
        final List<LocalAiInstalled.InstalledFile> files = List.of(
            new LocalAiInstalled.InstalledFile(LocalAiInstalled.ROLE_SERVER, relative(server), Files.size(server), serverSha,
                Files.getLastModifiedTime(server).toMillis()),
            new LocalAiInstalled.InstalledFile(LocalAiInstalled.ROLE_MODEL, relative(modelRequest.target()), Files.size(modelRequest.target()),
                manifest.model().sha256(), Files.getLastModifiedTime(modelRequest.target()).toMillis()));

        // 4. Record (an unchanged install keeps its installedAt and only gets a new verifiedAt), prune older versions, note.
        progress.phase(STEP_INSTALLING, INSTALLED_FILE);
        final String now = Instant.now(clock).toString();
        final String installedAt = unchanged ? before.installed().orElseThrow().installedAt() : now;
        final LocalAiInstalled record = new LocalAiInstalled(LocalAiInstalled.SCHEMA_VERSION, platformKey, installedAt, now,
            LocalAiInstalled.InstalledRuntime.of(manifest.runtime(), platform), manifest.model(), files);
        Json.write(installedFile(), record);
        prune(manifest, l);
        writeNoteQuietly(l);
        l.onLog((unchanged ? "Local AI already installed: " : "Local AI installed: ") + record.describe() + " (" + ByteSizes.format(sizeOnDisk())
            + " in " + dir() + ")");
        progress.phase(STEP_DONE, null);
        return status();
    }

    private boolean runtimeInPlace(final LocalAiReport before, final LocalAiManifest manifest, final LocalAiManifest.PlatformFile platform,
                                   final Path server) {
        if (!Files.isRegularFile(server)) {
            return false;
        }
        // The archive itself is gone after extraction: trust the recorded install only when it names this exact archive and
        // the server executable still has the recorded digest (small file, cheap to hash).
        return before.installed()
            .filter(i -> i.runtime() != null && i.runtime().file() != null)
            .filter(i -> manifest.runtime().tag().equals(i.runtime().tag()) && platform.sha256().equals(i.runtime().file().sha256()))
            .flatMap(i -> i.file(LocalAiInstalled.ROLE_SERVER))
            .map(f -> dir().resolve(f.path()).normalize().equals(server) && hashMatches(server, f.sha256()))
            .orElse(false);
    }

    private static boolean hashMatches(final Path file, final String sha256) {
        try {
            return Checksums.sha256Hex(file).equals(sha256);
        } catch (IOException e) {
            return false;
        }
    }

    private void extract(final Path archive, final Path target, final LocalAiManifest.PlatformFile platform, final Path server) throws IOException {
        final Path staging = target.resolveSibling(target.getFileName() + EXTRACTING_SUFFIX);
        deleteRecursively(staging);
        try {
            ArchiveExtractor.extract(archive, staging);
            final Path extractedServer = staging.resolve(platform.serverPath()).normalize();
            if (!extractedServer.startsWith(staging) || !Files.isRegularFile(extractedServer)) {
                throw new IOException("The runtime archive " + platform.file() + " does not contain " + platform.serverPath());
            }
            setExecutable(extractedServer);
            deleteRecursively(target);
            Files.createDirectories(target.getParent());
            Files.move(staging, target);
        } finally {
            deleteRecursively(staging);
            Files.deleteIfExists(archive);
        }
        if (!Files.isRegularFile(server)) {
            throw new IOException("The runtime at " + target + " has no " + platform.serverPath());
        }
    }

    /** Removes runtimes and models the manifest no longer names (an update replaced them) and download leftovers. */
    private void prune(final LocalAiManifest manifest, final InstallListener l) throws IOException {
        final Path runtimes = dir().resolve(RUNTIME_DIR);
        if (Files.isDirectory(runtimes)) {
            try (Stream<Path> tags = Files.list(runtimes)) {
                for (Path tagDir : tags.toList()) {
                    if (!tagDir.getFileName().toString().equals(manifest.runtime().tag())) {
                        deleteRecursively(tagDir);
                        l.onLog("Removed old runtime " + tagDir.getFileName());
                    }
                }
            }
        }
        if (Files.isDirectory(modelsDir())) {
            try (Stream<Path> models = Files.list(modelsDir())) {
                for (Path model : models.toList()) {
                    if (Files.isRegularFile(model) && !model.getFileName().toString().equals(manifest.model().file())) {
                        Files.deleteIfExists(model);
                        l.onLog("Removed old model " + model.getFileName());
                    }
                }
            }
        }
        if (Files.isDirectory(downloadsDir())) {
            try (Stream<Path> leftovers = Files.list(downloadsDir())) {
                for (Path leftover : leftovers.toList()) {
                    Files.deleteIfExists(leftover);
                }
            }
        }
    }

    private void writeNoteQuietly(final InstallListener l) {
        try {
            if (writeNote(paths.instanceDir())) {
                l.onLog("Noted the Local AI folder for the VANTA Client in " + LocalAiNote.file(paths.instanceDir()));
            }
        } catch (IOException | RuntimeException e) {
            l.onLog("Could not write the Local AI note into the game folder: " + e.getMessage());
            LOG.log(Level.WARNING, "Could not write the Local AI note", e);
        }
    }

    // ---------------------------------------------------------------- remove, note

    /**
     * Deletes the Local AI folder and nothing else. The empty folder the launcher creates on start counts as nothing.
     *
     * @return whether any file existed
     * @throws IOException when a file cannot be deleted
     */
    public boolean remove() throws IOException {
        if (!Files.exists(dir())) {
            return false;
        }
        final boolean hadFiles;
        try (Stream<Path> walk = Files.walk(dir())) {
            hadFiles = walk.anyMatch(Files::isRegularFile);
        }
        if (!hadFiles) {
            return false;
        }
        deleteRecursively(dir());
        LOG.log(Level.INFO, "Removed the Local AI folder {0}", dir());
        return true;
    }

    /**
     * Writes {@code config/vanta/local-ai.json} into a VANTA game folder, naming this launcher's Local AI folder.
     *
     * @param gameDir the game directory
     * @return whether the file was written (false when it already had this content)
     * @throws IOException when it cannot be written
     */
    public boolean writeNote(final Path gameDir) throws IOException {
        return LocalAiNote.write(gameDir, dir());
    }

    // ---------------------------------------------------------------- helpers

    private String relative(final Path file) {
        return dir().toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String hostOf(final String url) {
        try {
            final String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private static void setExecutable(final Path file) throws IOException {
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            final Set<PosixFilePermission> perms = EnumSet.copyOf(Files.getPosixFilePermissions(file));
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        }
    }

    private static void deleteRecursively(final Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    /** Progress of one install: a single {@link InstallStep#LOCAL_AI} step whose units are bytes. */
    private static final class Progress {

        private final InstallListener listener;
        private long total;
        private long completed;
        private long bytes;
        private String phase = STEP_CHECKING;

        Progress(final InstallListener listener) {
            this.listener = listener;
        }

        void total(final long totalBytes) {
            this.total = Math.max(1L, totalBytes);
        }

        long totalBytes() {
            return total;
        }

        void phase(final String name, final String detail) {
            phase = name;
            listener.onLog(detail == null ? name : name + ": " + detail);
            publish(completed, detail == null ? name : name + " · " + detail);
        }

        DownloadProgressListener downloads(final String step) {
            return new DownloadProgressListener() {
                private long lastReported = -PROGRESS_STRIDE;

                @Override
                public void onProgress(final DownloadRequest request, final long bytesDone, final long bytesTotal) {
                    if (bytesDone - lastReported >= PROGRESS_STRIDE || (bytesTotal > 0 && bytesDone >= bytesTotal)) {
                        lastReported = bytesDone;
                        final String size = bytesTotal > 0 ? ByteSizes.format(bytesDone) + " / " + ByteSizes.format(bytesTotal) : ByteSizes.format(bytesDone);
                        publish(completed + bytesDone, step + " · " + size);
                    }
                }

                @Override
                public void onComplete(final DownloadRequest request, final boolean skipped, final long transferred) {
                    bytes += transferred;
                    completed += Math.max(0L, request.expectedSize());
                    final String text = (skipped ? "Verified " : "Downloaded ") + request.description();
                    listener.onLog(text);
                    publish(completed, text);
                }

                @Override
                public void onRetry(final DownloadRequest request, final int attempt, final Exception error) {
                    listener.onLog("Retrying " + request.description() + " (attempt " + (attempt + 1) + "): " + error.getMessage());
                }
            };
        }

        private void publish(final long done, final String message) {
            listener.onProgress(new InstallProgress(InstallStep.LOCAL_AI, 0, 1, Math.min(done, total), total, bytes, message));
        }
    }
}
