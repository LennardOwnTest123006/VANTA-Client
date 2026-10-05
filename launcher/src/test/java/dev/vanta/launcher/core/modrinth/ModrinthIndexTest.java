package dev.vanta.launcher.core.modrinth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code config/vanta/modrinth.json}, the file shared with the VANTA Client's in-game browser.
 */
class ModrinthIndexTest {

    @TempDir
    Path tmp;

    @Test
    void aMissingFileIsAnEmptyIndexInTheSharedFormat() throws Exception {
        final Path file = tmp.resolve("config/vanta/modrinth.json");
        final ModrinthIndex index = ModrinthIndex.load(file);
        assertTrue(index.entries().isEmpty());
        index.upsert("AANobbMI").slug("sodium").title("Sodium").versionId("rkdTcxoT").versionNumber("mc1.21.11-0.8.14-fabric")
            .type(ContentType.MOD).file("mods/sodium-fabric-0.8.14+mc1.21.11.jar").sha512("ab".repeat(64)).enabled(true)
            .requiredBy(List.of("YL57xq9U", "YL57xq9U")).installedAt("2026-10-05T10:00:00Z");
        index.save();
        final JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(1, root.get("schemaVersion").getAsInt());
        final JsonObject e = root.getAsJsonArray("installed").get(0).getAsJsonObject();
        assertEquals(List.of("projectId", "slug", "title", "versionId", "versionNumber", "type", "file", "sha512", "enabled", "requiredBy",
            "installedAt"), List.copyOf(e.keySet()));
        assertEquals(1, e.getAsJsonArray("requiredBy").size(), "no duplicates");
    }

    @Test
    void unknownKeysAndANewerSchemaSurviveARewrite() throws Exception {
        final Path file = tmp.resolve("modrinth.json");
        Files.writeString(file, "{\"schemaVersion\":2,\"browser\":{\"lastTab\":\"shader\"},\"installed\":[{\"projectId\":\"HVnmMxH1\","
            + "\"type\":\"shader\",\"file\":\"shaderpacks/ComplementaryReimagined_r5.9.3.zip\",\"enabled\":false,\"favourite\":true},"
            + "\"not an object\",{\"slug\":\"no-id\"}]}");
        final ModrinthIndex index = ModrinthIndex.load(file);
        assertEquals(2, index.schemaVersion());
        assertEquals(1, index.entries().size(), "entries without a project id are ignored, not deleted");
        final ModrinthIndex.Entry shader = index.entries().get(0);
        assertEquals(ContentType.SHADER, shader.type().orElseThrow());
        assertFalse(shader.enabled());
        assertEquals("ComplementaryReimagined_r5.9.3.zip", shader.fileName());
        assertEquals(tmp.resolve("inst/shaderpacks/ComplementaryReimagined_r5.9.3.zip.disabled"), shader.path(tmp.resolve("inst")));
        shader.title("Complementary Shaders - Reimagined");
        index.save();
        final JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(2, root.get("schemaVersion").getAsInt(), "a newer schema is never lowered");
        assertEquals("shader", root.getAsJsonObject("browser").get("lastTab").getAsString());
        assertEquals(3, root.getAsJsonArray("installed").size());
        final JsonObject e = root.getAsJsonArray("installed").get(0).getAsJsonObject();
        assertTrue(e.get("favourite").getAsBoolean());
        assertEquals("Complementary Shaders - Reimagined", e.get("title").getAsString());
    }

    @Test
    void typeAndEnabledAreDerivedWhenMissingAndRemoveCleansRequiredBy() throws Exception {
        final Path file = tmp.resolve("modrinth.json");
        Files.writeString(file, "{\"installed\":[{\"projectId\":\"a\",\"file\":\"mods/a.jar.disabled\",\"requiredBy\":[\"b\"]},"
            + "{\"projectId\":\"b\",\"file\":\"resourcepacks/b.zip\"}]}");
        final ModrinthIndex index = ModrinthIndex.load(file);
        final ModrinthIndex.Entry a = index.byProjectId("a").orElseThrow();
        assertEquals(ContentType.MOD, a.type().orElseThrow());
        assertFalse(a.enabled(), "a .disabled file name means disabled");
        assertEquals("mods/a.jar", a.file());
        assertEquals(ContentType.RESOURCE_PACK, index.byProjectId("b").orElseThrow().type().orElseThrow());
        assertTrue(index.remove("b"));
        assertTrue(index.byProjectId("a").orElseThrow().requiredBy().isEmpty());
        assertFalse(index.remove("b"));
    }

    @Test
    void anUnreadableFileIsKeptAsideAndReplaced() throws Exception {
        final Path file = tmp.resolve("modrinth.json");
        Files.writeString(file, "{ not json");
        final ModrinthIndex index = ModrinthIndex.load(file);
        assertTrue(index.entries().isEmpty());
        assertTrue(Files.isRegularFile(tmp.resolve("modrinth.json.corrupt")));
        index.save();
        assertEquals(1, JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("schemaVersion").getAsInt());
    }
}
