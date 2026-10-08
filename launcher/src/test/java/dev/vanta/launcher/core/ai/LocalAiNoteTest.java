package dev.vanta.launcher.core.ai;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAiNoteTest {

    @TempDir
    Path tmp;

    @Test
    void writesReadsAndLeavesAnIdenticalNoteAlone() throws IOException {
        final Path game = tmp.resolve("instances").resolve("vanta-1.21.11");
        final Path dir = tmp.resolve("local-ai");
        assertEquals(game.resolve("config").resolve("vanta").resolve("local-ai.json"), LocalAiNote.file(game));
        assertEquals(Optional.empty(), LocalAiNote.read(game));
        assertTrue(LocalAiNote.wouldChange(game, dir));

        assertTrue(LocalAiNote.write(game, dir));
        final String text = Files.readString(LocalAiNote.file(game), StandardCharsets.UTF_8);
        assertEquals(dir.toAbsolutePath().normalize().toString(),
            JsonParser.parseString(text).getAsJsonObject().get(LocalAiNote.KEY).getAsString(), "the note is {\"localAiDir\": \"<absolute path>\"}");
        assertEquals(Optional.of(dir.toAbsolutePath().normalize()), LocalAiNote.read(game));

        assertFalse(LocalAiNote.wouldChange(game, dir));
        assertFalse(LocalAiNote.write(game, dir), "same content: nothing is rewritten");
        assertTrue(LocalAiNote.write(game, tmp.resolve("elsewhere")), "a different folder replaces the note");
        assertEquals(Optional.of(tmp.resolve("elsewhere").toAbsolutePath().normalize()), LocalAiNote.read(game));
    }

    @Test
    void unreadableOrRelativeNotesAreIgnored() throws IOException {
        final Path game = tmp.resolve("game");
        Files.createDirectories(LocalAiNote.file(game).getParent());
        Files.writeString(LocalAiNote.file(game), "not json");
        assertEquals(Optional.empty(), LocalAiNote.read(game));
        Files.writeString(LocalAiNote.file(game), "{\"localAiDir\": \"relative/path\"}");
        assertEquals(Optional.empty(), LocalAiNote.read(game), "only absolute paths are trusted");
        Files.writeString(LocalAiNote.file(game), "{\"other\": 1}");
        assertEquals(Optional.empty(), LocalAiNote.read(game));
        assertTrue(LocalAiNote.wouldChange(game, tmp.resolve("local-ai")));
    }
}
