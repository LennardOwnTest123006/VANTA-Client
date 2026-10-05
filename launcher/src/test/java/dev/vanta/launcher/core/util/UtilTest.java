package dev.vanta.launcher.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilTest {

    @TempDir
    Path tmp;

    @Test
    void atomicWriteLeavesNoTempFilesAndReplaces() throws IOException {
        final Path target = tmp.resolve("sub/settings.json");
        AtomicFiles.writeString(target, "one");
        AtomicFiles.writeString(target, "two");
        assertEquals("two", Files.readString(target));
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertEquals(1, files.count(), "no temp files left behind");
        }
    }

    @Test
    void restrictToOwnerOnPosix() throws IOException {
        final Path f = tmp.resolve("key.bin");
        Files.write(f, new byte[] {1, 2, 3});
        AtomicFiles.restrictToOwner(f);
        if (Files.getFileStore(f).supportsFileAttributeView("posix")) {
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(f)));
        }
    }

    @Test
    void jsonWriteReadRoundTrip() throws IOException {
        record Sample(String name, int count) {
        }
        final Path f = tmp.resolve("sample.json");
        Json.write(f, new Sample("x", 3));
        assertEquals(new Sample("x", 3), Json.read(f, Sample.class));
        assertTrue(Files.readString(f, StandardCharsets.UTF_8).contains("\n"), "pretty printed");
    }

    @Test
    void treeSerialisationKeepsNullMembersAndNumberText() {
        final String original = "{\"a\":null,\"nested\":{\"b\":null,\"c\":[null,1e3,2.50]},\"html\":\"<&>\"}";
        final com.google.gson.JsonElement tree = com.google.gson.JsonParser.parseString(original);
        final String written = Json.treeToJson(tree);
        assertEquals(tree, com.google.gson.JsonParser.parseString(written), "nothing is lost: " + written);
        assertTrue(written.contains("\"a\": null"), written);
        assertTrue(written.contains("\"b\": null"), written);
        assertTrue(written.contains("1e3") && written.contains("2.50"), "numbers keep their text: " + written);
        assertTrue(written.contains("<&>"), "no HTML escaping: " + written);
        assertFalse(Json.GSON.toJson(tree).contains("\"a\""), "the regular Gson drops null members, which is why the tree variant exists");
    }

    @Test
    void jsonRejectsNullDocument() {
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(com.google.gson.JsonParseException.class,
            () -> Json.parse("null", Map.class)).getMessage().contains("Empty"));
        org.junit.jupiter.api.Assertions.assertThrows(com.google.gson.JsonParseException.class, () -> Json.parse("{not json", Map.class));
    }

    @Test
    void systemMemoryIsPositiveWhenAvailable() {
        SystemMemory.totalMemoryMb().ifPresent(mb -> assertTrue(mb > 0));
    }

    @Test
    void osInfoDetectReturnsNormalisedValues() {
        final OsInfo os = OsInfo.detect();
        assertTrue(os.name().equals("windows") || os.name().equals("osx") || os.name().equals("linux"));
        assertFalse(os.arch().isEmpty());
    }
}
