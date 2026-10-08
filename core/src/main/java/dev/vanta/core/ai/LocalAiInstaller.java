package dev.vanta.core.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.release.Sha256;
import dev.vanta.core.screen.VantaLinks;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Downloads, verifies and extracts the Local AI runtime and model into a {@link LocalAiPaths} directory. Every byte
 * is hashed while it streams into a {@code .part} file; a file counts as installed only when size and SHA-256 match
 * the manifest. Steps in order: Checking, Downloading runtime, Downloading model, Verifying, Installing. Runs on a
 * background thread; the {@link Progress} callbacks arrive on that thread and the caller marshals them.
 */
public final class LocalAiInstaller {
    /** Receives step and byte progress; may cancel. Called on the installer's thread. */
    public interface Progress {
        /**
         * @param step           the running step
         * @param bytesDone      bytes transferred or hashed in this step so far
         * @param bytesTotal     total bytes of the step (0 when unknown or not a byte step)
         * @param bytesPerSecond recent transfer speed, 0 when not yet known
         */
        void onProgress(InstallStep step, long bytesDone, long bytesTotal, double bytesPerSecond);

        /** Polled between buffer reads; true aborts the install with {@code CANCELLED}. */
        default boolean isCancelled() {
            return false;
        }

        /** A listener that ignores everything. */
        static Progress none() {
            return (step, done, total, speed) -> {
            };
        }
    }

    /** HTTP settings. */
    public record Config(String userAgent, Duration connectTimeout, Duration headerTimeout) {
        public Config {
            Objects.requireNonNull(userAgent, "userAgent");
            Objects.requireNonNull(connectTimeout, "connectTimeout");
            Objects.requireNonNull(headerTimeout, "headerTimeout");
        }

        /** Defaults: VANTA user agent, 15 s connect, 60 s until the response headers arrive. */
        public static Config defaults(String clientVersion) {
            return new Config("VANTA-Client/" + clientVersion + " (" + VantaLinks.REPOSITORY + ")",
                    Duration.ofSeconds(15), Duration.ofSeconds(60));
        }
    }

    /** Minimum time between two progress reports of a download. */
    public static final long REPORT_INTERVAL_MS = 100;
    private static final int BUFFER = 64 * 1024;
    private static final HexFormat HEX = HexFormat.of();

    private final LocalAiManifest manifest;
    private final LocalAiPaths paths;
    private final Platform platform;
    private final Supplier<HttpClient> http;
    private final Config config;
    private final Clock clock;

    /**
     * @param http supplies the HTTP client on first use (a JDK client owns a selector thread, so it is created lazily)
     */
    public LocalAiInstaller(LocalAiManifest manifest, LocalAiPaths paths, Platform platform, Supplier<HttpClient> http,
                            Config config, Clock clock) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.http = Objects.requireNonNull(http, "http");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** An HTTP client following redirects (GitHub release downloads redirect to a CDN). */
    public static HttpClient defaultHttpClient(Config config) {
        return HttpClient.newBuilder().connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public LocalAiManifest manifest() {
        return manifest;
    }

    public LocalAiPaths paths() {
        return paths;
    }

    public Platform platform() {
        return platform;
    }

    // ---- state ----------------------------------------------------------------------------------------------------

    /** Reads {@code installed.json}; empty when missing or malformed. */
    public Optional<LocalAiInstalled> readInstalled() {
        Path file = paths.installedFile();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                return Optional.empty();
            }
            return LocalAiInstalled.fromJson(element.getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            CoreLog.warn(e, "Could not read {}", file);
            return Optional.empty();
        }
    }

    /** The server executable of an install. */
    public Path serverExecutable(LocalAiInstalled installed) {
        String serverPath = installed.runtimePlatform().map(LocalAiManifest.RuntimePlatform::serverPath)
                .orElseGet(() -> manifest.forPlatform(platform).map(LocalAiManifest.RuntimePlatform::serverPath)
                        .orElse("llama-server"));
        return paths.serverExecutable(installed.runtime().tag(), platform, serverPath);
    }

    /** The model file of the manifest. */
    public Path modelFile() {
        return paths.modelFile(manifest.model().file());
    }

    /**
     * Cheap check for start-up: {@code installed.json} present and matching the manifest, server executable and model
     * file present with the recorded sizes and modification times.
     */
    public LocalAiStatus quickCheck() {
        Optional<LocalAiManifest.RuntimePlatform> archive = manifest.forPlatform(platform);
        if (archive.isEmpty()) {
            return LocalAiStatus.UNSUPPORTED_PLATFORM;
        }
        Optional<LocalAiInstalled> installed = readInstalled();
        Path model = modelFile();
        if (installed.isEmpty()) {
            boolean traces = Files.exists(model) || hasChildren(paths.runtimeRoot()) || hasChildren(paths.downloadsDir());
            return traces ? LocalAiStatus.PARTIAL : LocalAiStatus.NOT_INSTALLED;
        }
        LocalAiInstalled state = installed.get();
        if (!state.platform().equals(platform.key()) || !state.matches(manifest)) {
            return LocalAiStatus.PARTIAL;
        }
        Path server = serverExecutable(state);
        if (!Files.isRegularFile(server) || !Files.isRegularFile(model)) {
            return LocalAiStatus.PARTIAL;
        }
        try {
            LocalAiInstalled.Files facts = facts(server, model);
            LocalAiInstalled.Files recorded = state.files();
            if (facts.modelSize() != recorded.modelSize() || facts.serverSize() != recorded.serverSize()
                    || recorded.modelMtime() > 0 && facts.modelMtime() != recorded.modelMtime()) {
                return LocalAiStatus.PARTIAL;
            }
            if (manifest.model().size() > 0 && facts.modelSize() != manifest.model().size()) {
                return LocalAiStatus.PARTIAL;
            }
        } catch (IOException e) {
            CoreLog.warn(e, "Could not inspect the Local AI files in {}", paths.root());
            return LocalAiStatus.PARTIAL;
        }
        return LocalAiStatus.INSTALLED;
    }

    private static boolean hasChildren(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    // ---- install --------------------------------------------------------------------------------------------------

    /**
     * Runs the install (or completes a partial one). Files that already match the manifest are not downloaded again.
     *
     * @return the written {@code installed.json} content
     * @throws LocalAiException with the kind that stopped the install
     */
    public LocalAiInstalled install(Progress progress) throws LocalAiException {
        Objects.requireNonNull(progress, "progress");
        LocalAiManifest.RuntimePlatform archive = manifest.forPlatform(platform).orElseThrow(() ->
                new LocalAiException(LocalAiException.Kind.UNSUPPORTED_PLATFORM,
                        "no llama.cpp build for " + platform.key()));
        if (paths.isLauncherManaged()) {
            throw new LocalAiException(LocalAiException.Kind.READ_ONLY,
                    "the Local AI in " + paths.root() + " is managed by the VANTA Launcher");
        }
        if (!archive.isResolved() || !manifest.model().isResolved()) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_MANIFEST,
                    "the Local AI manifest of this build is not resolved (sizes or hashes missing)");
        }
        progress.onProgress(InstallStep.CHECKING, 0, 0, 0);
        checkCancelled(progress);
        try {
            for (Path dir : paths.directories()) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.LOCAL_IO, "could not create " + paths.root(), e);
        }
        Optional<LocalAiInstalled> existing = readInstalled();
        String tag = manifest.runtime().tag();
        Path server = paths.serverExecutable(tag, platform, archive.serverPath());
        boolean runtimeOk = existing.isPresent() && existing.get().platform().equals(platform.key())
                && existing.get().runtime().tag().equals(tag)
                && existing.get().runtimePlatform().map(p -> p.sha256().equals(archive.sha256())).orElse(false)
                && Files.isRegularFile(server);

        // Step 2: runtime archive
        Path archiveFile = paths.downloadFile(archive.file());
        if (runtimeOk) {
            progress.onProgress(InstallStep.DOWNLOADING_RUNTIME, archive.size(), archive.size(), 0);
        } else if (isVerifiedFile(archiveFile, archive.size(), archive.sha256())) {
            progress.onProgress(InstallStep.DOWNLOADING_RUNTIME, archive.size(), archive.size(), 0);
        } else {
            download(archive.url(), archive.file(), archive.size(), archive.sha256(), InstallStep.DOWNLOADING_RUNTIME,
                    progress);
        }

        // Step 3: model
        Path model = modelFile();
        boolean modelOk = existingModelOk(existing, model);
        if (modelOk) {
            progress.onProgress(InstallStep.DOWNLOADING_MODEL, manifest.model().size(), manifest.model().size(), 0);
        } else {
            Path downloaded = download(manifest.model().url(), manifest.model().file(), manifest.model().size(),
                    manifest.model().sha256(), InstallStep.DOWNLOADING_MODEL, progress);
            move(downloaded, model);
        }

        // Step 4: full verification of everything that will be used
        long verifyTotal = manifest.model().size() + (runtimeOk ? 0 : archive.size());
        progress.onProgress(InstallStep.VERIFYING, 0, verifyTotal, 0);
        long verified = hashAndCompare(model, manifest.model().size(), manifest.model().sha256(), progress,
                InstallStep.VERIFYING, 0, verifyTotal);
        if (!runtimeOk) {
            hashAndCompare(archiveFile, archive.size(), archive.sha256(), progress, InstallStep.VERIFYING, verified,
                    verifyTotal);
        }

        // Step 5: extract and record
        progress.onProgress(InstallStep.INSTALLING, 0, 0, 0);
        checkCancelled(progress);
        if (!runtimeOk) {
            extractRuntime(archiveFile, archive, tag, server);
        }
        deleteQuietly(archiveFile);
        cleanDownloads();
        String now = Instant.ofEpochMilli(clock.millis()).toString();
        LocalAiInstalled installed;
        try {
            installed = new LocalAiInstalled(platform.key(),
                    runtimeOk && existing.isPresent() ? existing.get().installedAt() : now, now,
                    manifest.runtime().only(platform.key()), manifest.model(), facts(server, model));
            writeInstalled(installed);
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.LOCAL_IO, "could not write installed.json", e);
        }
        progress.onProgress(InstallStep.INSTALLING, 1, 1, 0);
        CoreLog.info("Local AI installed in {} ({} {}, {} {})", paths.root(), manifest.runtime().name(), tag,
                manifest.model().name(), manifest.model().quantization());
        return installed;
    }

    private boolean existingModelOk(Optional<LocalAiInstalled> existing, Path model) throws LocalAiException {
        if (!Files.isRegularFile(model)) {
            return false;
        }
        try {
            if (Files.size(model) != manifest.model().size()) {
                return false;
            }
            if (existing.isPresent() && existing.get().model().sha256().equals(manifest.model().sha256())) {
                LocalAiInstalled.Files recorded = existing.get().files();
                BasicFileAttributes attrs = Files.readAttributes(model, BasicFileAttributes.class);
                if (recorded.modelSize() == attrs.size() && recorded.modelMtime() == attrs.lastModifiedTime().toMillis()) {
                    return true;
                }
            }
            return Sha256.matches(model, manifest.model().sha256());
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.LOCAL_IO, "could not inspect " + model, e);
        }
    }

    private static boolean isVerifiedFile(Path file, long size, String sha256) throws LocalAiException {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            return (size <= 0 || Files.size(file) == size) && Sha256.matches(file, sha256);
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.LOCAL_IO, "could not inspect " + file, e);
        }
    }

    /**
     * Downloads {@code url} into {@code downloads/<name>.part}, hashing while streaming, then renames it to
     * {@code downloads/<name>} once size and hash match. Any failure removes the partial file.
     */
    Path download(String url, String name, long expectedSize, String sha256, InstallStep step, Progress progress)
            throws LocalAiException {
        if (!LocalAiManifest.isAllowedDownloadUrl(url)) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_MANIFEST, "refusing download URL " + url);
        }
        Path part = paths.partFile(name);
        progress.onProgress(step, 0, expectedSize, 0);
        checkCancelled(progress);
        HttpResponse<InputStream> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET()
                    .header("User-Agent", config.userAgent())
                    .header("Accept", "application/octet-stream, */*")
                    .timeout(config.headerTimeout()).build();
            response = http.get().send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.NETWORK, "download of " + name + " failed: "
                    + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocalAiException(LocalAiException.Kind.CANCELLED, "download of " + name + " interrupted", e);
        } catch (IllegalArgumentException e) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_MANIFEST, "invalid download URL " + url, e);
        }
        int status = response.statusCode();
        if (status != 200) {
            closeQuietly(response.body());
            throw new LocalAiException(LocalAiException.Kind.HTTP, "download of " + name + " failed with HTTP "
                    + status);
        }
        long total = response.headers().firstValueAsLong("content-length").orElse(expectedSize);
        if (total <= 0) {
            total = expectedSize;
        }
        MessageDigest digest = sha256Digest();
        long done = 0;
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[BUFFER];
            long lastReportAt = clock.millis();
            long lastReportBytes = 0;
            double speed = 0;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (progress.isCancelled() || Thread.currentThread().isInterrupted()) {
                    throw new LocalAiException(LocalAiException.Kind.CANCELLED, "download of " + name + " cancelled");
                }
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                done += read;
                long now = clock.millis();
                long elapsed = now - lastReportAt;
                if (elapsed >= REPORT_INTERVAL_MS) {
                    speed = (done - lastReportBytes) * 1000.0 / elapsed;
                    lastReportAt = now;
                    lastReportBytes = done;
                    progress.onProgress(step, done, Math.max(total, done), speed);
                }
            }
            progress.onProgress(step, done, Math.max(total, done), speed);
        } catch (LocalAiException e) {
            deleteQuietly(part);
            throw e;
        } catch (IOException e) {
            deleteQuietly(part);
            throw new LocalAiException(LocalAiException.Kind.NETWORK, "download of " + name + " failed: "
                    + e.getMessage(), e);
        }
        if (expectedSize > 0 && done != expectedSize) {
            deleteQuietly(part);
            throw new LocalAiException(LocalAiException.Kind.SIZE_MISMATCH, name + " has " + done
                    + " bytes, expected " + expectedSize);
        }
        String actual = HEX.formatHex(digest.digest());
        if (!actual.equals(sha256)) {
            deleteQuietly(part);
            throw new LocalAiException(LocalAiException.Kind.HASH_MISMATCH, name + " has SHA-256 " + actual
                    + ", expected " + sha256);
        }
        Path target = paths.downloadFile(name);
        move(part, target);
        return target;
    }

    private long hashAndCompare(Path file, long expectedSize, String sha256, Progress progress, InstallStep step,
                                long offset, long total) throws LocalAiException {
        MessageDigest digest = sha256Digest();
        long done = 0;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER];
            long lastReportAt = clock.millis();
            int read;
            while ((read = in.read(buffer)) >= 0) {
                checkCancelled(progress);
                digest.update(buffer, 0, read);
                done += read;
                long now = clock.millis();
                if (now - lastReportAt >= REPORT_INTERVAL_MS) {
                    lastReportAt = now;
                    progress.onProgress(step, offset + done, total, 0);
                }
            }
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.LOCAL_IO, "could not read " + file, e);
        }
        if (expectedSize > 0 && done != expectedSize) {
            deleteQuietly(file);
            throw new LocalAiException(LocalAiException.Kind.SIZE_MISMATCH, file.getFileName() + " has " + done
                    + " bytes, expected " + expectedSize);
        }
        String actual = HEX.formatHex(digest.digest());
        if (!actual.equals(sha256)) {
            deleteQuietly(file);
            throw new LocalAiException(LocalAiException.Kind.HASH_MISMATCH, file.getFileName() + " has SHA-256 "
                    + actual + ", expected " + sha256);
        }
        progress.onProgress(step, offset + done, total, 0);
        return offset + done;
    }

    private void extractRuntime(Path archiveFile, LocalAiManifest.RuntimePlatform archive, String tag, Path server)
            throws LocalAiException {
        Path runtimeDir = paths.runtimeDir(tag, platform);
        Path staging = runtimeDir.resolveSibling(runtimeDir.getFileName() + ".extracting");
        try {
            deleteTree(staging);
            Files.createDirectories(staging);
            List<Path> extracted = platform.usesZip() ? ZipExtractor.extract(archiveFile, staging)
                    : TarGzExtractor.extract(archiveFile, staging);
            Path stagedServer = staging;
            for (String segment : archive.serverPath().split("[/\\\\]")) {
                if (!segment.isEmpty()) {
                    stagedServer = stagedServer.resolve(segment);
                }
            }
            if (!Files.isRegularFile(stagedServer)) {
                deleteTree(staging);
                throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, archive.file()
                        + " does not contain " + archive.serverPath() + " (" + extracted.size() + " files)");
            }
            markExecutable(stagedServer);
            deleteTree(runtimeDir);
            Files.createDirectories(runtimeDir.getParent());
            try {
                Files.move(staging, runtimeDir, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, runtimeDir);
            }
            if (!Files.isRegularFile(server)) {
                throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "server executable missing after "
                        + "extraction: " + server);
            }
        } catch (LocalAiException e) {
            throw e;
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.LOCAL_IO, "could not extract " + archiveFile.getFileName()
                    + ": " + e.getMessage(), e);
        }
    }

    /**
     * Makes sure the server executable carries the executable bit on macOS and Linux (the tar extractor already
     * applies the archive's mode bits to every file; this covers an archive that lost them). Nothing to do on Windows.
     */
    void markExecutable(Path server) {
        if (platform.isWindows()) {
            return;
        }
        TarGzExtractor.setExecutable(server);
    }

    // ---- verify / remove --------------------------------------------------------------------------------------------

    /**
     * Full re-hash of the model file and presence check of the server executable. Updates {@code installed.json}
     * (client-managed installs only) and returns {@code INSTALLED} or {@code PARTIAL}.
     */
    public LocalAiStatus verify(Progress progress) throws LocalAiException {
        Objects.requireNonNull(progress, "progress");
        if (manifest.forPlatform(platform).isEmpty()) {
            return LocalAiStatus.UNSUPPORTED_PLATFORM;
        }
        Optional<LocalAiInstalled> installed = readInstalled();
        if (installed.isEmpty()) {
            return quickCheck();
        }
        LocalAiInstalled state = installed.get();
        if (!state.platform().equals(platform.key()) || !state.matches(manifest)) {
            return LocalAiStatus.PARTIAL;
        }
        Path server = serverExecutable(state);
        Path model = modelFile();
        if (!Files.isRegularFile(server) || !Files.isRegularFile(model)) {
            return LocalAiStatus.PARTIAL;
        }
        long total = manifest.model().size();
        progress.onProgress(InstallStep.VERIFYING, 0, total, 0);
        try {
            if (manifest.model().size() > 0 && Files.size(model) != manifest.model().size()) {
                return LocalAiStatus.PARTIAL;
            }
            MessageDigest digest = sha256Digest();
            long done = 0;
            try (InputStream in = Files.newInputStream(model)) {
                byte[] buffer = new byte[BUFFER];
                long lastReportAt = clock.millis();
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    checkCancelled(progress);
                    digest.update(buffer, 0, read);
                    done += read;
                    long now = clock.millis();
                    if (now - lastReportAt >= REPORT_INTERVAL_MS) {
                        lastReportAt = now;
                        progress.onProgress(InstallStep.VERIFYING, done, total, 0);
                    }
                }
            }
            progress.onProgress(InstallStep.VERIFYING, done, total, 0);
            if (!HEX.formatHex(digest.digest()).equals(manifest.model().sha256())) {
                return LocalAiStatus.PARTIAL;
            }
            if (!paths.isLauncherManaged()) {
                writeInstalled(state.verified(Instant.ofEpochMilli(clock.millis()).toString(), facts(server, model)));
            }
            return LocalAiStatus.INSTALLED;
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.LOCAL_IO, "could not verify " + model, e);
        }
    }

    /** Deletes the whole client-managed install directory. Nothing else is ever touched. */
    public void remove() throws LocalAiException {
        if (paths.isLauncherManaged()) {
            throw new LocalAiException(LocalAiException.Kind.READ_ONLY,
                    "the Local AI in " + paths.root() + " is managed by the VANTA Launcher");
        }
        try {
            deleteTree(paths.root());
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.LOCAL_IO, "could not remove " + paths.root(), e);
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private void writeInstalled(LocalAiInstalled installed) throws IOException {
        Path file = paths.installedFile();
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.createDirectories(file.getParent());
        Files.writeString(tmp, installed.toJson().toString() + System.lineSeparator(), StandardCharsets.UTF_8);
        move(tmp, file);
    }

    private static LocalAiInstalled.Files facts(Path server, Path model) throws IOException {
        BasicFileAttributes s = Files.readAttributes(server, BasicFileAttributes.class);
        BasicFileAttributes m = Files.readAttributes(model, BasicFileAttributes.class);
        return new LocalAiInstalled.Files(s.size(), s.lastModifiedTime().toMillis(), m.size(),
                m.lastModifiedTime().toMillis());
    }

    private void cleanDownloads() {
        Path dir = paths.downloadsDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                if (file.getFileName().toString().endsWith(LocalAiPaths.PART_SUFFIX)) {
                    deleteQuietly(file);
                }
            }
        } catch (IOException e) {
            CoreLog.debug("Could not list {}: {}", dir, e.toString());
        }
    }

    private static void move(Path from, Path to) throws LocalAiException {
        try {
            Path parent = to.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new LocalAiException(LocalAiException.Kind.LOCAL_IO, "could not move " + from.getFileName()
                    + " to " + to, e);
        }
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(root);
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            CoreLog.debug("Could not delete {}: {}", file, e.toString());
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }

    private static void checkCancelled(Progress progress) throws LocalAiException {
        if (progress.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new LocalAiException(LocalAiException.Kind.CANCELLED, "Local AI install cancelled");
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
        }
    }
}
