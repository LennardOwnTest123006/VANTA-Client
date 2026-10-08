package dev.vanta.launcher.core.ai;

import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.testutil.FakeLocalAi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAiManifestTest {

    @TempDir
    Path tmp;

    private static String template() throws IOException {
        try (InputStream in = LocalAiManifestTest.class.getResourceAsStream(LocalAiManifest.RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theSpecTemplateParsesOnlyWithPlaceholdersAllowed() throws IOException {
        final LocalAiManifest template = LocalAiManifest.parse(template(), true);
        assertEquals(1, template.schemaVersion());
        assertEquals("llama.cpp", template.runtime().name());
        assertEquals("b11429", template.runtime().tag());
        assertEquals(6, template.runtime().platforms().size());
        assertEquals("1283323272b04cd07905816a597a0da810918102de958f4ff6f7bbaa70ed2efe", template.platform("windows-x64").orElseThrow().sha256());
        assertEquals("llama-server.exe", template.platform("windows-x64").orElseThrow().serverPath());
        assertTrue(template.platform("linux-x64").orElseThrow().isTarGz());
        assertEquals("Qwen3-1.7B", template.model().name());
        assertEquals(4096, template.model().contextSize());
        assertEquals(2200, template.requirements().diskMb());
        assertEquals("llama.cpp b11429, Qwen3-1.7B Q8_0", template.describe());

        // Production is strict: sizes of 0, an empty model digest and "..." URLs are unresolved values.
        final IOException strict = assertThrows(IOException.class, () -> LocalAiManifest.parse(template()));
        assertTrue(strict.getMessage().contains("incomplete"), strict.getMessage());
        assertTrue(strict.getMessage().contains("model.sha256 is unresolved"), strict.getMessage());
        assertTrue(strict.getMessage().contains("runtime.platforms.windows-x64.size is unresolved"), strict.getMessage());
        assertTrue(strict.getMessage().contains("runtime.platforms.linux-x64.url is unresolved"), strict.getMessage());
        assertTrue(strict.getMessage().contains("runtime.platforms.linux-x64.serverPath is unresolved"), strict.getMessage());
        final List<String> problems = template.problems(false);
        assertTrue(problems.contains("resolvedAt is missing"), problems.toString());
        assertTrue(template.problems(true).isEmpty(), template.problems(true).toString());
    }

    @Test
    void aResolvedManifestParsesStrictlyAndRoundTripsThroughAFile() throws IOException {
        try (FakeLocalAi ai = new FakeLocalAi()) {
            final LocalAiManifest parsed = LocalAiManifest.parse(ai.manifestJson());
            assertEquals(ai.manifest(), parsed);
            assertTrue(parsed.problems(false).isEmpty());
            final Path file = ai.writeManifest(tmp.resolve("manifests").resolve("local-ai.json"));
            assertEquals(parsed, LocalAiManifest.read(file));
            assertEquals(parsed, LocalAiManifest.load(Map.of(LocalAiManifest.MANIFEST_ENV, file.toString())));
            final long linux = parsed.platform("linux-x64").orElseThrow().size() + parsed.model().size();
            assertEquals(linux, parsed.downloadBytes("linux-x64"));
            assertEquals(0L, parsed.downloadBytes("freebsd-x64"));
            assertTrue(parsed.platform("freebsd-x64").isEmpty());
        }
    }

    @Test
    void loadFallsBackToTheEmbeddedResourceWhichIsTheUnresolvedTemplateInTests() {
        // The test class path carries the SPEC template, which production parsing refuses: an honest "unavailable" state.
        final IOException e = assertThrows(IOException.class, () -> LocalAiManifest.load(Map.of()));
        assertTrue(e.getMessage().contains("incomplete"), e.getMessage());
        final IOException missing = assertThrows(IOException.class, () -> LocalAiManifest.load(Map.of(LocalAiManifest.MANIFEST_ENV,
            tmp.resolve("nope.json").toString())));
        assertTrue(missing.getMessage().contains("Cannot read the Local AI manifest"), missing.getMessage());
    }

    @Test
    void validationNamesEveryProblem() throws IOException {
        try (FakeLocalAi ai = new FakeLocalAi()) {
            final String good = ai.manifestJson();
            assertThrows(IOException.class, () -> LocalAiManifest.parse(good.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")));
            final IOException badSha = assertThrows(IOException.class, () -> LocalAiManifest.parse(good.replace(ai.manifest().model().sha256(), "abc")));
            assertTrue(badSha.getMessage().contains("model.sha256 is not a SHA-256 hex digest"), badSha.getMessage());
            final IOException badKey = assertThrows(IOException.class, () -> LocalAiManifest.parse(good.replace("\"linux-x64\"", "\"amiga-m68k\"")));
            assertTrue(badKey.getMessage().contains("runtime.platforms.amiga-m68k is not a known platform key"), badKey.getMessage());
            final IOException escape = assertThrows(IOException.class, () -> LocalAiManifest.parse(good.replace("build/bin/llama-server", "../../bin/sh")));
            assertTrue(escape.getMessage().contains("serverPath must be a relative path inside the archive"), escape.getMessage());
            final IOException ftp = assertThrows(IOException.class, () -> LocalAiManifest.parse(good.replace("https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/blob/main/LICENSE", "ftp://x")));
            assertTrue(ftp.getMessage().contains("model.licenseUrl is not an http(s) URL"), ftp.getMessage());
            assertThrows(IOException.class, () -> LocalAiManifest.parse("{not json"));
            assertThrows(IOException.class, () -> LocalAiManifest.parse("{}"));
        }
    }

    @Test
    void platformKeysFollowTheManifestVocabulary() {
        assertEquals("windows-x64", LocalAiManifest.platformKey(OsInfo.fromProperties("Windows 11", "amd64", "10.0")));
        assertEquals("windows-arm64", LocalAiManifest.platformKey(OsInfo.fromProperties("Windows 11", "aarch64", "10.0")));
        assertEquals("linux-x64", LocalAiManifest.platformKey(OsInfo.fromProperties("Linux", "x86_64", "6.8")));
        assertEquals("linux-arm64", LocalAiManifest.platformKey(OsInfo.fromProperties("Linux", "aarch64", "6.8")));
        assertEquals("macos-arm64", LocalAiManifest.platformKey(OsInfo.fromProperties("Mac OS X", "aarch64", "15.0")));
        assertEquals("macos-x64", LocalAiManifest.platformKey(OsInfo.fromProperties("Mac OS X", "x86_64", "15.0")));
        assertEquals("linux-x86", LocalAiManifest.platformKey(OsInfo.fromProperties("Linux", "i386", "6.8")));
        assertFalse(LocalAiManifest.PLATFORMS.contains("linux-x86"));
    }

    @Test
    void theSharedManifestWhenPresentIsAtLeastAValidTemplate() throws IOException {
        // shared/local-ai/local-ai.json is produced by the release tooling and embedded by Gradle; when the checkout has
        // it, it must parse (resolved values are checked by the schema in CI, placeholders are tolerated here).
        final Path shared = Path.of("..", "shared", "local-ai", "local-ai.json");
        if (Files.isRegularFile(shared)) {
            final LocalAiManifest manifest = LocalAiManifest.parse(Files.readString(shared, StandardCharsets.UTF_8), true);
            assertEquals("llama-server", manifest.runtime().component());
            assertTrue(manifest.platform("windows-x64").isPresent());
        }
    }
}
