package dev.vanta.launcher.core.launch;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftFolderHintTest {

    @TempDir
    Path tmp;

    @Test
    void writesTheAbsoluteFolderOnlyWhenItChanges() throws IOException {
        final Path game = tmp.resolve("instance");
        final Path mc = tmp.resolve("x").resolve("..").resolve(".minecraft");
        final Path file = MinecraftFolderHint.file(game);
        assertEquals(game.resolve("config").resolve("vanta").resolve("minecraft-folder.json"), file);
        assertTrue(MinecraftFolderHint.wouldChange(game, mc));

        assertTrue(MinecraftFolderHint.write(game, mc));
        final String json = Files.readString(file);
        assertEquals(mc.toAbsolutePath().normalize().toString(),
            JsonParser.parseString(json).getAsJsonObject().get("minecraftDir").getAsString(), json);
        assertEquals(Optional.of(mc.toAbsolutePath().normalize()), MinecraftFolderHint.read(game));
        assertFalse(MinecraftFolderHint.wouldChange(game, mc));
        assertFalse(MinecraftFolderHint.write(game, mc), "identical content is left as it is");
        assertFalse(Files.exists(mc), "the Minecraft folder itself is never created");

        final Path other = tmp.resolve("other");
        assertTrue(MinecraftFolderHint.write(game, other));
        assertEquals(Optional.of(other.toAbsolutePath().normalize()), MinecraftFolderHint.read(game));
        try (var files = Files.list(file.getParent())) {
            assertEquals(List.of(file), files.toList(), "no temporary files are left behind");
        }
    }

    @Test
    void writeUnlessRecordedKeepsANoteForAnExistingFolder() throws IOException {
        final Path game = tmp.resolve("instance");
        final Path custom = tmp.resolve("custom");
        final Path platform = tmp.resolve("platform");
        Files.createDirectories(custom);
        Files.createDirectories(platform);

        assertTrue(MinecraftFolderHint.writeUnlessRecorded(game, platform), "no note yet");
        assertTrue(MinecraftFolderHint.write(game, custom));
        assertFalse(MinecraftFolderHint.writeUnlessRecorded(game, platform), "the existing folder's note wins");
        assertEquals(Optional.of(custom.toAbsolutePath().normalize()), MinecraftFolderHint.read(game));

        Files.delete(custom);
        assertTrue(MinecraftFolderHint.writeUnlessRecorded(game, platform), "a note for a folder that is gone is replaced");
        assertEquals(Optional.of(platform.toAbsolutePath().normalize()), MinecraftFolderHint.read(game));
    }

    @Test
    void unreadableNotesCountAsNone() throws IOException {
        final Path game = tmp.resolve("instance");
        final Path file = MinecraftFolderHint.file(game);
        Files.createDirectories(file.getParent());
        for (String bad : List.of("", "nope", "[1]", "{}", "{\"minecraftDir\":1}", "{\"minecraftDir\":\"rel/dir\"}")) {
            Files.writeString(file, bad);
            assertEquals(Optional.empty(), MinecraftFolderHint.read(game), bad);
            assertTrue(MinecraftFolderHint.wouldChange(game, tmp));
        }
    }
}
