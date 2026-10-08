package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class LocalAiManifestTest {
    @Test
    void parsesTheBundledTemplateWhenTemplatesAreAllowed() throws LocalAiException {
        LocalAiManifest manifest = LocalAiManifest.parse(ManifestFixtures.templateText(), true);
        assertEquals(1, manifest.schemaVersion());
        assertEquals("b11429", manifest.runtime().tag());
        assertEquals("llama-server", manifest.runtime().component());
        assertEquals(6, manifest.runtime().platforms().size());
        LocalAiManifest.RuntimePlatform windows = manifest.platform("windows-x64").orElseThrow();
        assertEquals("1283323272b04cd07905816a597a0da810918102de958f4ff6f7bbaa70ed2efe", windows.sha256());
        assertEquals("llama-server.exe", windows.serverPath());
        assertEquals("https://github.com/ggml-org/llama.cpp/releases/download/b11429/llama-b11429-bin-win-cpu-x64.zip",
                windows.url());
        assertEquals("f6d25dde8f51133143d1453da4fd5f73b145127177612a283bf7995957af3392",
                manifest.platform("linux-x64").orElseThrow().sha256());
        assertEquals("Qwen3-1.7B-Q8_0.gguf", manifest.model().file());
        assertEquals(4096, manifest.model().contextSize());
        assertEquals(2200, manifest.requirements().diskMb());
        assertFalse(manifest.isComplete(), "sizes 0 and an empty model hash mark an unresolved template");
        assertFalse(windows.isResolved());
        assertTrue(manifest.platform("freebsd-x64").isEmpty());
        assertTrue(manifest.forPlatform(Platform.parse("macos-arm64").orElseThrow()).isPresent());
        assertEquals(0L, manifest.downloadBytes(Platform.parse("linux-x64").orElseThrow()));
    }

    @Test
    void theRealParserRejectsTheTemplate() {
        LocalAiException e = assertThrows(LocalAiException.class,
                () -> LocalAiManifest.parse(ManifestFixtures.templateText(), false));
        assertEquals(LocalAiException.Kind.INVALID_MANIFEST, e.kind());
        assertTrue(e.getMessage().contains("not resolved"), e.getMessage());
        assertFalse(LocalAiManifest.templateAllowedByProperty(), "the property is not set in the test JVM");
    }

    @Test
    void bundledResourceFollowsTheSameRule() throws LocalAiException {
        assertEquals("b11429", LocalAiManifest.bundled(true).runtime().tag());
        assertThrows(LocalAiException.class, () -> LocalAiManifest.bundled(false));
    }

    @Test
    void acceptsACompleteManifestAndRoundTripsIt() throws LocalAiException {
        byte[] zip = FakeArchives.windowsRuntimeZip();
        byte[] tgz = FakeArchives.linuxRuntimeTarGz();
        byte[] model = FakeArchives.randomBytes(5000, 1);
        String json = ManifestFixtures.json("http://127.0.0.1:1", zip, tgz, model, "ab".repeat(32));
        LocalAiManifest manifest = LocalAiManifest.parse(json, false);
        assertTrue(manifest.isComplete());
        assertEquals(zip.length, manifest.platform("windows-x64").orElseThrow().size());
        assertEquals(tgz.length + model.length, manifest.downloadBytes(Platform.parse("linux-x64").orElseThrow()));
        LocalAiManifest again = LocalAiManifest.fromJson(manifest.toJson(), false);
        assertEquals(manifest, again);
        assertEquals(1, manifest.runtime().only("linux-x64").platforms().size());
        assertTrue(manifest.runtime().only("nope").platforms().isEmpty());
    }

    @Test
    void rejectsUnknownPlatformKeys() {
        JsonObject json = template();
        JsonObject platforms = json.getAsJsonObject("runtime").getAsJsonObject("platforms");
        platforms.add("freebsd-x64", platforms.getAsJsonObject("linux-x64").deepCopy());
        LocalAiException e = assertThrows(LocalAiException.class, () -> LocalAiManifest.fromJson(json, true));
        assertTrue(e.getMessage().contains("freebsd-x64"), e.getMessage());

        JsonObject badArch = template();
        JsonObject p2 = badArch.getAsJsonObject("runtime").getAsJsonObject("platforms");
        p2.add("linux-x86", p2.getAsJsonObject("linux-x64").deepCopy());
        assertThrows(LocalAiException.class, () -> LocalAiManifest.fromJson(badArch, true));
    }

    @Test
    void rejectsMalformedValues() {
        assertKind("not json at all", LocalAiException.Kind.INVALID_MANIFEST);
        assertKind("[]", LocalAiException.Kind.INVALID_MANIFEST);
        JsonObject badSha = template();
        badSha.getAsJsonObject("runtime").getAsJsonObject("platforms").getAsJsonObject("windows-x64")
                .addProperty("sha256", "nothex");
        assertThrows(LocalAiException.class, () -> LocalAiManifest.fromJson(badSha, true));
        JsonObject badUrl = template();
        badUrl.getAsJsonObject("model").addProperty("url", "http://example.com/model.gguf");
        assertThrows(LocalAiException.class, () -> LocalAiManifest.fromJson(badUrl, true),
                "plain http is only allowed on the loopback address");
        JsonObject badFile = template();
        badFile.getAsJsonObject("model").addProperty("file", "../evil.gguf");
        assertThrows(LocalAiException.class, () -> LocalAiManifest.fromJson(badFile, true));
        JsonObject badServerPath = template();
        badServerPath.getAsJsonObject("runtime").getAsJsonObject("platforms").getAsJsonObject("windows-x64")
                .addProperty("serverPath", "../../bin/sh");
        assertThrows(LocalAiException.class, () -> LocalAiManifest.fromJson(badServerPath, true));
        JsonObject badSchema = template();
        badSchema.addProperty("schemaVersion", 2);
        assertThrows(LocalAiException.class, () -> LocalAiManifest.fromJson(badSchema, true));
        JsonObject noPlatforms = template();
        noPlatforms.getAsJsonObject("runtime").add("platforms", new JsonObject());
        assertThrows(LocalAiException.class, () -> LocalAiManifest.fromJson(noPlatforms, true));
        JsonObject badContext = template();
        badContext.getAsJsonObject("model").addProperty("contextSize", 0);
        assertThrows(LocalAiException.class, () -> LocalAiManifest.fromJson(badContext, true));
    }

    @Test
    void downloadUrlRule() {
        assertTrue(LocalAiManifest.isAllowedDownloadUrl("https://github.com/x/y.zip"));
        assertTrue(LocalAiManifest.isAllowedDownloadUrl("http://127.0.0.1:8080/x.zip"));
        assertTrue(LocalAiManifest.isAllowedDownloadUrl("http://localhost:8080/x.zip"));
        assertFalse(LocalAiManifest.isAllowedDownloadUrl("http://github.com/x.zip"));
        assertFalse(LocalAiManifest.isAllowedDownloadUrl("ftp://github.com/x.zip"));
        assertFalse(LocalAiManifest.isAllowedDownloadUrl("file:///etc/passwd"));
        assertFalse(LocalAiManifest.isAllowedDownloadUrl("not a url"));
        assertFalse(LocalAiManifest.isAllowedDownloadUrl(null));
        assertTrue(LocalAiManifest.isSafeRelativePath("build/bin/llama-server"));
        assertFalse(LocalAiManifest.isSafeRelativePath("/usr/bin/llama-server"));
        assertFalse(LocalAiManifest.isSafeRelativePath("build/../llama-server"));
        assertFalse(LocalAiManifest.isSafeFileName(".hidden"));
    }

    private static void assertKind(String json, LocalAiException.Kind kind) {
        LocalAiException e = assertThrows(LocalAiException.class, () -> LocalAiManifest.parse(json, true));
        assertEquals(kind, e.kind());
    }

    private static JsonObject template() {
        return JsonParser.parseString(ManifestFixtures.templateText()).getAsJsonObject();
    }
}
