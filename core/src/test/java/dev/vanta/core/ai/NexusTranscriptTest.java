package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NexusTranscriptTest {
    @TempDir
    Path dir;
    final MutableClock clock = MutableClock.standard();

    @Test
    void appendsPersistsAndReloads() {
        JsonStore store = new JsonStore(clock);
        Path file = dir.resolve("nexus-chat.json");
        NexusTranscript transcript = new NexusTranscript(store, file);
        transcript.load();
        assertTrue(transcript.isEmpty());
        assertFalse(transcript.isDirty());
        List<Integer> sizes = new ArrayList<>();
        transcript.onChanged(entries -> sizes.add(entries.size()));

        transcript.append(NexusTranscript.Entry.user("Make my HUD minimal", 1000));
        transcript.append(NexusTranscript.Entry.assistant("Done.", 2000, List.of("HUD preset minimal (Minimal)"),
                List.of("No HUD widget named “radar”")));
        transcript.append(NexusTranscript.Entry.failure("Connection problem", 3000));
        assertTrue(transcript.isDirty());
        assertEquals(List.of(1, 2, 3), sizes);
        transcript.saveIfDirty();
        assertFalse(transcript.isDirty());
        assertTrue(Files.isRegularFile(file));

        NexusTranscript again = new NexusTranscript(store, file);
        again.load();
        assertEquals(3, again.size());
        assertEquals(transcript.entries(), again.entries());
        NexusTranscript.Entry assistant = again.entries().get(1);
        assertEquals(NexusTranscript.Role.ASSISTANT, assistant.role());
        assertEquals(List.of("HUD preset minimal (Minimal)"), assistant.applied());
        assertEquals(1, assistant.rejected().size());
        assertTrue(again.entries().get(2).error());
        assertEquals(2, again.lastEntries(2).size());
        assertEquals("Done.", again.lastEntries(2).get(0).text());
        again.saveIfDirty();
        assertEquals(1, Files.exists(file) ? 1 : 0, "unchanged store touches no file");
    }

    @Test
    void keepsOnlyTheLastHundredEntries() {
        NexusTranscript transcript = new NexusTranscript(new JsonStore(clock), dir.resolve("chat.json"));
        for (int i = 0; i < 130; i++) {
            transcript.append(NexusTranscript.Entry.user("q" + i, i));
        }
        assertEquals(NexusTranscript.MAX_ENTRIES, transcript.size());
        assertEquals("q30", transcript.entries().get(0).text());
        transcript.clear();
        assertTrue(transcript.isEmpty());
        assertTrue(transcript.isDirty());
        String longText = "x".repeat(NexusTranscript.MAX_TEXT + 50);
        assertEquals(NexusTranscript.MAX_TEXT, NexusTranscript.Entry.user(longText, 0).text().length());
    }

    @Test
    void aCorruptFileIsMovedAsideNotFatal() throws IOException {
        Path file = dir.resolve("chat.json");
        Files.writeString(file, "{ broken", StandardCharsets.UTF_8);
        NexusTranscript transcript = new NexusTranscript(new JsonStore(clock), file);
        transcript.load();
        assertTrue(transcript.isEmpty());
        assertFalse(Files.exists(file));
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("chat.broken-")));
        }
        Files.writeString(file, "{\"schemaVersion\":1,\"entries\":[{\"role\":\"user\",\"text\":\"ok\",\"at\":5},"
                + "{\"role\":\"ghost\",\"text\":\"skip\"},{\"text\":\"no role\"},7]}", StandardCharsets.UTF_8);
        transcript.load();
        assertEquals(1, transcript.size());
        assertEquals("ok", transcript.entries().get(0).text());
    }
}
