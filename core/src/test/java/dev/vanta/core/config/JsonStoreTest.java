package dev.vanta.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonStoreTest {
    @TempDir
    Path dir;

    private final MutableClock clock = MutableClock.standard();
    private final JsonStore store = new JsonStore(clock);

    @Test
    void writeThenReadRoundTripsAndLeavesNoTempFile() throws IOException {
        Path file = dir.resolve("nested").resolve("settings.json");
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("name", "VANTA");
        store.writeObject(file, json);

        assertTrue(Files.exists(file));
        assertFalse(Files.exists(dir.resolve("nested").resolve("settings.json.tmp")));
        Optional<JsonObject> read = store.readObject(file);
        assertTrue(read.isPresent());
        assertEquals("VANTA", read.get().get("name").getAsString());
        assertTrue(Files.readString(file).contains("\n  \"name\""), "pretty printed");
    }

    @Test
    void missingFileIsEmpty() {
        assertTrue(store.readObject(dir.resolve("missing.json")).isEmpty());
    }

    @Test
    void corruptFileIsBackedUpAndEmpty() throws IOException {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        assertTrue(store.readObject(file).isEmpty());
        assertFalse(Files.exists(file), "corrupt file moved aside");
        List<Path> backups = store.backups(file);
        assertEquals(1, backups.size());
        assertEquals("settings.broken-" + clock.millis() + ".json", backups.get(0).getFileName().toString());
        assertEquals("{ not json", Files.readString(backups.get(0)));
    }

    @Test
    void nonObjectJsonIsBackedUp() throws IOException {
        Path file = dir.resolve("list.json");
        Files.writeString(file, "[1,2,3]", StandardCharsets.UTF_8);
        assertTrue(store.readObject(file).isEmpty());
        assertEquals(1, store.backups(file).size());
    }

    @Test
    void overwriteReplacesContentAtomically() throws IOException {
        Path file = dir.resolve("a.json");
        JsonObject first = new JsonObject();
        first.addProperty("v", 1);
        store.writeObject(file, first);
        JsonObject second = new JsonObject();
        second.addProperty("v", 2);
        store.writeObject(file, second);
        assertEquals(2, store.readObject(file).orElseThrow().get("v").getAsInt());
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(1, files.count(), "no stray temp files");
        }
    }

    @Test
    void migratedFileIsUpgradedAndWrittenBack() throws IOException {
        Path file = dir.resolve("hud.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"old\":true}", StandardCharsets.UTF_8);
        Migrations migrations = new Migrations(2, List.of(Migrations.step(1, 2, json -> {
            json.addProperty("renamed", json.get("old").getAsBoolean());
            json.remove("old");
            return json;
        })));
        JsonObject result = store.readMigrated(file, migrations).orElseThrow();
        assertEquals(2, result.get("schemaVersion").getAsInt());
        assertTrue(result.get("renamed").getAsBoolean());
        assertFalse(result.has("old"));
        assertEquals(2, store.readObject(file).orElseThrow().get("schemaVersion").getAsInt(), "written back");
    }

    @Test
    void newerSchemaIsReadTolerantly() throws IOException {
        Path file = dir.resolve("future.json");
        Files.writeString(file, "{\"schemaVersion\":9,\"x\":1}", StandardCharsets.UTF_8);
        JsonObject result = store.readMigrated(file, Migrations.none(1)).orElseThrow();
        assertEquals(9, result.get("schemaVersion").getAsInt());
        assertTrue(Files.exists(file));
    }

    @Test
    void schemaVersionFallsBackWhenMissing() {
        assertEquals(7, JsonStore.schemaVersion(new JsonObject(), 7));
        JsonObject bad = new JsonObject();
        bad.addProperty("schemaVersion", "one");
        assertEquals(1, JsonStore.schemaVersion(bad, 1));
    }
}
