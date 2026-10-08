package dev.vanta.core.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.VantaPaths;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the Local AI lives and what is inside:
 * <pre>
 * &lt;root&gt;/installed.json
 * &lt;root&gt;/runtime/&lt;tag&gt;/&lt;platform&gt;/        the extracted archive; serverPath relative to it
 * &lt;root&gt;/models/&lt;file&gt;
 * &lt;root&gt;/downloads/&lt;name&gt;.part          partial files, removed on success
 * &lt;root&gt;/logs/llama-server.log
 * </pre>
 * The root is the launcher's folder when the note {@code config/vanta/local-ai.json} ({@code {"localAiDir": ...}})
 * points at one (the client then uses it read-only), otherwise {@code config/vanta/local-ai/} inside the game folder,
 * the only place the client itself writes to.
 */
public final class LocalAiPaths {
    /** Key of the note file. */
    public static final String NOTE_KEY = "localAiDir";
    /** Name of the server log inside {@code logs/}. */
    public static final String SERVER_LOG = "llama-server.log";
    /** Suffix of partial downloads. */
    public static final String PART_SUFFIX = ".part";

    private final Path root;
    private final boolean launcherManaged;

    private LocalAiPaths(Path root, boolean launcherManaged) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.launcherManaged = launcherManaged;
    }

    /** The client's own install directory ({@code config/vanta/local-ai}). */
    public static LocalAiPaths clientManaged(Path dir) {
        return new LocalAiPaths(dir, false);
    }

    /** A directory the launcher installed into; the client never writes there. */
    public static LocalAiPaths launcherManaged(Path dir) {
        return new LocalAiPaths(dir, true);
    }

    /**
     * Resolves the install directory for a game folder: the note's directory when the note exists and names an
     * absolute path, otherwise {@link VantaPaths#localAiDir()}.
     */
    public static LocalAiPaths resolve(VantaPaths paths) {
        Optional<Path> noted = readNote(paths.localAiNoteFile());
        return noted.map(LocalAiPaths::launcherManaged).orElseGet(() -> clientManaged(paths.localAiDir()));
    }

    /** Reads the note file; empty when missing, malformed or not an absolute path. */
    public static Optional<Path> readNote(Path noteFile) {
        if (!Files.isRegularFile(noteFile)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(noteFile, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                CoreLog.warn("{} does not contain a JSON object; using the client's Local AI folder", noteFile);
                return Optional.empty();
            }
            JsonElement dir = element.getAsJsonObject().get(NOTE_KEY);
            if (dir == null || !dir.isJsonPrimitive() || !dir.getAsJsonPrimitive().isString()
                    || dir.getAsString().isBlank()) {
                return Optional.empty();
            }
            Path path = Path.of(dir.getAsString().trim());
            if (!path.isAbsolute()) {
                CoreLog.warn("{} names a relative Local AI folder ({}); ignoring it", noteFile, path);
                return Optional.empty();
            }
            return Optional.of(path.normalize());
        } catch (IOException | RuntimeException e) {
            CoreLog.warn(e, "Could not read the Local AI note {}", noteFile);
            return Optional.empty();
        }
    }

    /** Writes the note ({@code {"localAiDir": "<absolute path>"}}); used by the launcher-side tooling and tests. */
    public static void writeNote(Path noteFile, Path localAiDir) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty(NOTE_KEY, localAiDir.toAbsolutePath().normalize().toString());
        Path parent = noteFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(noteFile, json + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    /** The install root. */
    public Path root() {
        return root;
    }

    /** True when the launcher owns the directory (the client only reads it). */
    public boolean isLauncherManaged() {
        return launcherManaged;
    }

    /** {@code installed.json}. */
    public Path installedFile() {
        return root.resolve("installed.json");
    }

    /** {@code runtime/}. */
    public Path runtimeRoot() {
        return root.resolve("runtime");
    }

    /** {@code runtime/<tag>/<platform>/}. */
    public Path runtimeDir(String tag, Platform platform) {
        return runtimeRoot().resolve(tag).resolve(platform.key());
    }

    /** The server executable inside a runtime directory. */
    public Path serverExecutable(String tag, Platform platform, String serverPath) {
        Path dir = runtimeDir(tag, platform);
        Path exe = dir;
        for (String segment : serverPath.split("[/\\\\]")) {
            if (!segment.isEmpty()) {
                exe = exe.resolve(segment);
            }
        }
        return exe.normalize();
    }

    /** {@code models/}. */
    public Path modelsDir() {
        return root.resolve("models");
    }

    /** {@code models/<file>}. */
    public Path modelFile(String file) {
        return modelsDir().resolve(file);
    }

    /** {@code downloads/}. */
    public Path downloadsDir() {
        return root.resolve("downloads");
    }

    /** {@code downloads/<name>.part}. */
    public Path partFile(String name) {
        return downloadsDir().resolve(name + PART_SUFFIX);
    }

    /** {@code downloads/<name>}: a verified download waiting to be installed. */
    public Path downloadFile(String name) {
        return downloadsDir().resolve(name);
    }

    /** {@code logs/}. */
    public Path logsDir() {
        return root.resolve("logs");
    }

    /** {@code logs/llama-server.log}. */
    public Path serverLogFile() {
        return logsDir().resolve(SERVER_LOG);
    }

    /** Directories an install creates. */
    public List<Path> directories() {
        return List.of(root, runtimeRoot(), modelsDir(), downloadsDir(), logsDir());
    }

    /** True when {@code path} lies inside the root. */
    public boolean contains(Path path) {
        return path.toAbsolutePath().normalize().startsWith(root);
    }

    @Override
    public String toString() {
        return "LocalAiPaths[" + root + (launcherManaged ? ", launcher]" : "]");
    }
}
