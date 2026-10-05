package dev.vanta.core.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseTest {
    @Test
    void sha256KnownVectors(@TempDir Path dir) throws IOException {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(new byte[0]));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256.hex("abc".getBytes(StandardCharsets.UTF_8)));
        Path file = dir.resolve("abc.txt");
        Files.writeString(file, "abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex(file));
        assertTrue(Sha256.matches(file, "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"));
        assertFalse(Sha256.matches(file, "nope"));
        assertTrue(Sha256.isValidHex("0".repeat(64)));
        assertFalse(Sha256.isValidHex("0".repeat(63)));
    }

    @Test
    void semVerParsingAndOrdering() {
        SemVer v = SemVer.parseStrict("v1.2.3-beta.2+build.7");
        assertEquals(1, v.major());
        assertEquals(List.of("beta", "2"), v.preRelease());
        assertEquals("build.7", v.build());
        assertEquals("1.2.3-beta.2+build.7", v.toString());
        assertTrue(v.isPreRelease());
        assertTrue(SemVer.parse("1.2").isEmpty());
        assertTrue(SemVer.parse("01.2.3").isEmpty());
        assertTrue(SemVer.parse("1.2.3-").isEmpty());
        List<String> ordered = List.of("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta",
                "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0");
        for (int i = 1; i < ordered.size(); i++) {
            SemVer a = SemVer.parseStrict(ordered.get(i - 1));
            SemVer b = SemVer.parseStrict(ordered.get(i));
            assertTrue(a.compareTo(b) < 0, a + " < " + b);
            assertTrue(b.isNewerThan(a));
        }
        assertEquals(SemVer.parseStrict("1.0.0+a"), SemVer.parseStrict("1.0.0+b"), "build ignored");
        assertEquals(SemVer.of(1, 0, 1), SemVer.of(1, 0, 0).nextPatch());
        assertThrows(IllegalArgumentException.class, () -> SemVer.parseStrict("abc"));
    }

    @Test
    void realManifestsInSharedReleasesParse() throws IOException {
        Path dir = Path.of("..", "shared", "releases");
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                ReleaseManifest manifest = ReleaseManifest.parse(Files.newBufferedReader(file));
                assertEquals(1, manifest.schemaVersion(), file.toString());
                assertEquals("1.21.11", manifest.minecraftVersion(), file.toString());
                assertEquals(21, manifest.javaVersion(), file.toString());
                assertFalse(manifest.files().isEmpty(), file.toString());
                assertEquals(manifest, ReleaseManifest.fromJson(manifest.toJson()), file.toString());
            }
        }
    }

    @Test
    void manifestRoundTripAndValidation() {
        String json = """
                {"schemaVersion": 1, "product": "client", "version": "1.0.0", "minecraftVersion": "1.21.11",
                 "fabricVersion": "0.19.5", "fabricApiVersion": "0.141.6+1.21.11", "javaVersion": 21,
                 "releaseDate": "2026-10-04", "channel": "stable",
                 "files": [{"name": "vanta-client-1.0.0.jar", "downloadUrl": "", "size": 0, "sha256": ""}],
                 "changelog": "website/content/changelog/client-1.0.0.md", "notes": ""}
                """;
        ReleaseManifest manifest = ReleaseManifest.parse(new StringReader(json));
        assertEquals(ReleaseManifest.Product.CLIENT, manifest.product());
        assertEquals(SemVer.of(1, 0, 0), manifest.version());
        assertEquals(LocalDate.of(2026, 10, 4), manifest.releaseDate());
        assertFalse(manifest.isPublished(), "empty URL means not published yet");
        assertEquals(Optional.of("vanta-client-1.0.0.jar"), manifest.primaryFile().map(ReleaseFile::name));
        assertEquals(manifest, ReleaseManifest.fromJson(manifest.toJson()));

        ReleaseFile published = new ReleaseFile("x.jar", "https://example.invalid/x.jar", 10,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertTrue(published.isPublished());
        assertThrows(IllegalArgumentException.class, () -> new ReleaseFile("../x.jar", "", 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new ReleaseFile("x.jar", "http://insecure", 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new ReleaseFile("x.jar", "", 0, "zz"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseManifest.parse(new StringReader(
                json.replace("1.21.11", "1.21.10"))), "client manifests must target 1.21.11");
        assertThrows(IllegalArgumentException.class, () -> ReleaseManifest.parse(new StringReader(
                json.replace("\"channel\": \"stable\"", "\"channel\": \"nightly\""))));
        assertThrows(IllegalArgumentException.class, () -> ReleaseManifest.parse(new StringReader("[]")));
        assertThrows(IllegalArgumentException.class, () -> ReleaseManifest.parse(new StringReader("{nope")));
        ReleaseManifest launcher = ReleaseManifest.parse(new StringReader(json.replace("\"client\"", "\"launcher\"")
                .replace("1.21.11", "any")));
        assertEquals(ReleaseManifest.Product.LAUNCHER, launcher.product());
    }
}
