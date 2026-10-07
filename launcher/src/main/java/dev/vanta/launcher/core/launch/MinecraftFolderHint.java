package dev.vanta.launcher.core.launch;

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
 * Tells the VANTA Client which official Minecraft folder the launcher set the game up for, so Singleplayer can list
 * the player's existing worlds from that folder's {@code saves/} (the client's "Singleplayer worlds" setting, core
 * {@code dev.vanta.core.worlds.WorldsFolder}). The note is {@code <gameDir>/config/vanta/minecraft-folder.json}:
 * {@code {"minecraftDir": "<absolute path>"}}; the client's {@code MinecraftFolderHint} reads the same format.
 * <p>
 * Written by "Use with the Minecraft Launcher" with the folder the profile was written to (a custom
 * {@code --minecraft-dir} included) and, before the launcher's own PLAY, with the platform default when no usable
 * note exists yet. The note is local data only: nothing is read from or written to the Minecraft folder itself, and
 * a file with the same content is left untouched.
 */
public final class MinecraftFolderHint {

    /** File name below {@code config/vanta/}. */
    public static final String FILE_NAME = "minecraft-folder.json";
    /** JSON key holding the absolute path of the official Minecraft folder. */
    public static final String KEY = "minecraftDir";

    private MinecraftFolderHint() {
    }

    /**
     * @param gameDir the game directory (the VANTA instance)
     * @return {@code <gameDir>/config/vanta/minecraft-folder.json}
     */
    public static Path file(final Path gameDir) {
        return gameDir.resolve("config").resolve("vanta").resolve(FILE_NAME);
    }

    /**
     * @param minecraftDir the official Minecraft folder
     * @return the note's exact file content
     */
    public static String content(final Path minecraftDir) {
        final JsonObject root = new JsonObject();
        root.addProperty(KEY, normalise(minecraftDir).toString());
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
     * @param gameDir      the game directory
     * @param minecraftDir the official Minecraft folder
     * @return whether {@link #write} would change the file (missing or different content)
     */
    public static boolean wouldChange(final Path gameDir, final Path minecraftDir) {
        final Path file = file(gameDir);
        try {
            return !Files.isRegularFile(file) || !content(minecraftDir).equals(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Records {@code minecraftDir}, atomically and only when the content changes.
     *
     * @param gameDir      the game directory
     * @param minecraftDir the official Minecraft folder
     * @return whether the file was written
     * @throws IOException when it cannot be written
     */
    public static boolean write(final Path gameDir, final Path minecraftDir) throws IOException {
        if (!wouldChange(gameDir, minecraftDir)) {
            return false;
        }
        AtomicFiles.writeString(file(gameDir), content(minecraftDir));
        return true;
    }

    /**
     * Records {@code minecraftDir} unless the note already names an existing folder (for example a custom
     * {@code --minecraft-dir} the Minecraft Launcher profile was set up for, which must win over the platform default).
     *
     * @param gameDir      the game directory
     * @param minecraftDir the platform default Minecraft folder
     * @return whether the file was written
     * @throws IOException when it cannot be written
     */
    public static boolean writeUnlessRecorded(final Path gameDir, final Path minecraftDir) throws IOException {
        final Optional<Path> recorded = read(gameDir);
        if (recorded.isPresent() && Files.isDirectory(recorded.get())) {
            return false;
        }
        return write(gameDir, minecraftDir);
    }

    private static Path normalise(final Path minecraftDir) {
        return Objects.requireNonNull(minecraftDir, "minecraftDir").toAbsolutePath().normalize();
    }
}
