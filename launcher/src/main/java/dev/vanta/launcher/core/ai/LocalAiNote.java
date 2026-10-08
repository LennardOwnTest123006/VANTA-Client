package dev.vanta.launcher.core.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.vanta.launcher.core.util.AtomicFiles;
import dev.vanta.launcher.core.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Tells the VANTA Client where the launcher keeps the Local AI (llama-server runtime and model), so the client starts
 * the server from there and never downloads a second copy. The note is {@code <gameDir>/config/vanta/local-ai.json}:
 * {@code {"localAiDir": "<absolute path>"}}, next to the {@code minecraft-folder.json} note; the core module's
 * {@code LocalAiPaths} reads the same format and uses the folder read-only.
 *
 * <p>Written into every VANTA game folder the launcher sets up (PLAY, "Use with the Minecraft Launcher",
 * {@code --install}, {@code --install-official-profile}, {@code --install-local-ai}). A file with the same content is
 * left untouched.</p>
 */
public final class LocalAiNote {

    /** File name below {@code config/vanta/}. */
    public static final String FILE_NAME = "local-ai.json";
    /** JSON key holding the absolute path of the launcher's Local AI folder. */
    public static final String KEY = "localAiDir";

    private LocalAiNote() {
    }

    /**
     * @param gameDir the game directory (the VANTA instance)
     * @return {@code <gameDir>/config/vanta/local-ai.json}
     */
    public static Path file(final Path gameDir) {
        return gameDir.resolve("config").resolve("vanta").resolve(FILE_NAME);
    }

    /**
     * @param localAiDir the launcher's Local AI folder
     * @return the note's exact file content
     */
    public static String content(final Path localAiDir) {
        final JsonObject root = new JsonObject();
        root.addProperty(KEY, normalise(localAiDir).toString());
        return Json.treeToJson(root) + System.lineSeparator();
    }

    /**
     * @param gameDir the game directory
     * @return the folder recorded in the note; empty when there is none or it cannot be read
     */
    public static Optional<Path> read(final Path gameDir) {
        final Path file = file(gameDir);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            final JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            final JsonElement value = root.getAsJsonObject().get(KEY);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }
            final Path path = Path.of(value.getAsString().trim());
            return path.isAbsolute() ? Optional.of(path.normalize()) : Optional.empty();
        } catch (IOException | JsonParseException | IllegalStateException | InvalidPathException e) {
            return Optional.empty();
        }
    }

    /**
     * @param gameDir    the game directory
     * @param localAiDir the Local AI folder
     * @return whether {@link #write} would change the file (missing or different content)
     */
    public static boolean wouldChange(final Path gameDir, final Path localAiDir) {
        final Path file = file(gameDir);
        try {
            return !Files.isRegularFile(file) || !content(localAiDir).equals(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Records {@code localAiDir}, atomically and only when the content changes.
     *
     * @param gameDir    the game directory
     * @param localAiDir the Local AI folder
     * @return whether the file was written
     * @throws IOException when it cannot be written
     */
    public static boolean write(final Path gameDir, final Path localAiDir) throws IOException {
        if (!wouldChange(gameDir, localAiDir)) {
            return false;
        }
        AtomicFiles.writeString(file(gameDir), content(localAiDir));
        return true;
    }

    private static Path normalise(final Path dir) {
        return Objects.requireNonNull(dir, "localAiDir").toAbsolutePath().normalize();
    }
}
