package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.core.config.JsonStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModrinthIndexTest {
    @TempDir
    Path dir;

    private final JsonStore store = new JsonStore(Clock.systemUTC());

    private static final String LAUNCHER_FILE = """
            {
              "schemaVersion": 1,
              "writtenBy": "VANTA Launcher 1.1.0",
              "installed": [
                {
                  "projectId": "AANobbMI",
                  "slug": "sodium",
                  "title": "Sodium",
                  "versionId": "rkdTcxoT",
                  "versionNumber": "mc1.21.11-0.8.14-fabric",
                  "type": "mod",
                  "file": "mods/sodium-fabric-0.8.14+mc1.21.11.jar",
                  "sha512": "abc",
                  "enabled": true,
                  "requiredBy": ["YL57xq9U"],
                  "installedAt": "2026-10-05T12:00:00Z",
                  "launcherNote": {"pinned": true}
                },
                {"slug": "no-project-id"},
                "garbage",
                {
                  "projectId": "YL57xq9U",
                  "file": "mods/iris-fabric-1.10.8+mc1.21.11.jar.disabled",
                  "type": "mod"
                }
              ]
            }
            """;

    @Test
    void readsTheSharedFormatAndKeepsWhatItDoesNotKnow() throws Exception {
        Path file = dir.resolve("modrinth.json");
        Files.writeString(file, LAUNCHER_FILE);
        ModrinthIndex index = ModrinthIndex.read(store, file);
        assertEquals(2, index.entries().size());
        assertEquals(2, index.unreadableCount());
        InstalledEntry sodium = index.find("AANobbMI").orElseThrow();
        assertEquals("rkdTcxoT", sodium.versionId());
        assertEquals(List.of("YL57xq9U"), sodium.requiredBy());
        assertEquals(ModrinthProjectType.MOD, sodium.projectType().orElseThrow());
        InstalledEntry iris = index.find("YL57xq9U").orElseThrow();
        assertEquals("mods/iris-fabric-1.10.8+mc1.21.11.jar", iris.file(), "a .disabled suffix is normalised");
        assertFalse(iris.enabled(), "and means disabled when the flag is missing");

        index.write(store, file);
        JsonObject written = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(1, written.get("schemaVersion").getAsInt());
        assertEquals("VANTA Launcher 1.1.0", written.get("writtenBy").getAsString());
        assertEquals(4, written.getAsJsonArray("installed").size(), "unreadable entries are written back unchanged");
        JsonObject first = written.getAsJsonArray("installed").get(0).getAsJsonObject();
        assertTrue(first.getAsJsonObject("launcherNote").get("pinned").getAsBoolean());
        assertEquals("garbage", written.getAsJsonArray("installed").get(3).getAsString());
    }

    @Test
    void roundTripWritesExactlyTheDocumentedFields() {
        ModrinthIndex index = ModrinthIndex.empty();
        index.put(new InstalledEntry("AANobbMI", "sodium", "Sodium", "rkdTcxoT", "mc1.21.11-0.8.14-fabric", "mod",
                "mods/sodium-fabric-0.8.14+mc1.21.11.jar", "f".repeat(128), true, List.of("YL57xq9U"),
                "2026-10-05T12:00:00Z", null));
        JsonObject json = index.toJson();
        assertEquals("{\"schemaVersion\":1,\"installed\":[{\"projectId\":\"AANobbMI\",\"slug\":\"sodium\","
                + "\"title\":\"Sodium\",\"versionId\":\"rkdTcxoT\",\"versionNumber\":\"mc1.21.11-0.8.14-fabric\","
                + "\"type\":\"mod\",\"file\":\"mods/sodium-fabric-0.8.14+mc1.21.11.jar\",\"sha512\":\"" + "f".repeat(128)
                + "\",\"enabled\":true,\"requiredBy\":[\"YL57xq9U\"],\"installedAt\":\"2026-10-05T12:00:00Z\"}]}",
                json.toString());
        ModrinthIndex back = ModrinthIndex.fromJson(json);
        assertEquals(index.entries(), back.entries());
    }

    @Test
    void newerSchemaIsNotDowngraded() {
        JsonObject root = JsonParser.parseString("{\"schemaVersion\":3,\"installed\":[]}").getAsJsonObject();
        assertEquals(3, ModrinthIndex.fromJson(root).toJson().get("schemaVersion").getAsInt());
    }

    @Test
    void missingOrCorruptFilesAreEmpty() throws Exception {
        Path file = dir.resolve("modrinth.json");
        assertTrue(ModrinthIndex.read(store, file).entries().isEmpty());
        Files.writeString(file, "{ not json");
        assertTrue(ModrinthIndex.read(store, file).entries().isEmpty());
        assertEquals(1, store.backups(file).size(), "the corrupt file is kept as a backup");
    }

    @Test
    void removeDropsTheProjectFromEveryRequiredByList() {
        ModrinthIndex index = ModrinthIndex.empty();
        index.put(new InstalledEntry("A", "a", "A", "", "", "mod", "mods/a.jar", "", true, List.of("B", "C"), "", null));
        index.put(new InstalledEntry("B", "b", "B", "", "", "mod", "mods/b.jar", "", true, List.of(), "", null));
        index.remove("B");
        assertEquals(List.of("C"), index.find("A").orElseThrow().requiredBy());
        assertTrue(index.findByFile("mods/a.jar.disabled").isPresent());
    }
}
