package dev.vanta.core.worlds;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads the note the VANTA launcher leaves in the game folder about the official Minecraft folder it set the game up
 * for: {@code <gameDir>/config/vanta/minecraft-folder.json}, {@code {"minecraftDir": "<absolute path>"}}. The
 * launcher writes it for both launch paths (the "VANTA" profile in the Minecraft Launcher and the launcher's own
 * PLAY), so a custom {@code --minecraft-dir} is honoured. The launcher's {@code MinecraftFolderHint} writes the same
 * format; keep the two in sync.
 */
public final class MinecraftFolderHint {
    /** File name below {@code config/vanta}. */
    public static final String FILE_NAME = "minecraft-folder.json";
    /** JSON key holding the absolute path of the official Minecraft folder. */
    public static final String KEY = "minecraftDir";

    private MinecraftFolderHint() {
    }

    /** {@code <gameDir>/config/vanta/minecraft-folder.json}. */
    public static Path file(Path gameDir) {
        return gameDir.resolve("config").resolve("vanta").resolve(FILE_NAME);
    }

    /**
     * The folder recorded in the game folder's note, or empty when there is no note or it cannot be read. Tolerant:
     * a missing, malformed or relative entry counts as no note.
     */
    public static Optional<Path> read(Path gameDir) {
        Path file = file(gameDir);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Parses the note's JSON text; empty for anything but an object with an absolute {@value #KEY} string. */
    public static Optional<Path> parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = root.getAsJsonObject();
            JsonElement value = object.get(KEY);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }
            String text = value.getAsString().trim();
            if (text.isEmpty()) {
                return Optional.empty();
            }
            Path path = Path.of(text);
            return path.isAbsolute() ? Optional.of(path.normalize()) : Optional.empty();
        } catch (InvalidPathException | IllegalStateException | com.google.gson.JsonParseException e) {
            return Optional.empty();
        }
    }
}
