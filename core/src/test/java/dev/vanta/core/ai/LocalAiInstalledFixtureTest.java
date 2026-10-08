package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.core.config.MutableClock;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shared {@code installed.json} contract ({@code shared/local-ai/fixtures/}): the client, the VANTA Launcher and
 * the release tooling ({@code scripts/release/local-ai.mjs prepare}) write the same file for the same inputs, and
 * core reads a directory any of them produced. {@code installed.example.json} was generated with
 * {@link LocalAiInstalled#toJson()} from {@code manifest.example.json} and the fixed inputs documented in the fixtures
 * README; this test keeps all three in step.
 */
class LocalAiInstalledFixtureTest {
    static final Path FIXTURES = Path.of("..", "shared", "local-ai", "fixtures");
    static final Platform LINUX = Platform.parse("linux-x64").orElseThrow();
    static final String INSTALLED_AT = "2026-10-08T12:00:00Z";
    static final String VERIFIED_AT = "2026-10-08T12:05:00Z";
    static final long SERVER_MTIME = 1_791_460_800_000L;
    static final long MODEL_MTIME = 1_791_460_860_000L;
    static final byte[] SERVER_BYTES = "#!/bin/sh\necho fixture llama-server b11429\n".getBytes(StandardCharsets.US_ASCII);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TempDir
    Path dir;

    /** The 4096 fixture model bytes: byte i is (i * 7 + 3) & 0xff, the first four bytes spell GGUF. */
    static byte[] modelBytes() {
        byte[] bytes = new byte[4096];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((i * 7 + 3) & 0xff);
        }
        bytes[0] = 'G';
        bytes[1] = 'G';
        bytes[2] = 'U';
        bytes[3] = 'F';
        return bytes;
    }

    static JsonObject readJson(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static LocalAiManifest fixtureManifest() throws IOException {
        return LocalAiManifest.parse(Files.readString(FIXTURES.resolve("manifest.example.json"), StandardCharsets.UTF_8),
                false);
    }

    static LocalAiInstalled.Files fixtureFacts() {
        return new LocalAiInstalled.Files(SERVER_BYTES.length, SERVER_MTIME, modelBytes().length, MODEL_MTIME);
    }

    @Test
    void theFixtureManifestIsCompleteAndItsHashesAreTheDocumentedBytes() throws IOException {
        LocalAiManifest manifest = fixtureManifest();
        assertTrue(manifest.isComplete());
        assertEquals(6, manifest.runtime().platforms().size());
        assertEquals(dev.vanta.core.release.Sha256.hex(modelBytes()), manifest.model().sha256());
        assertEquals(modelBytes().length, manifest.model().size());
        for (LocalAiManifest.RuntimePlatform entry : manifest.runtime().platforms().values()) {
            byte[] archive = ("VANTA Local AI fixture archive " + entry.file() + "\n").getBytes(StandardCharsets.US_ASCII);
            assertEquals(archive.length, entry.size(), entry.key());
            assertEquals(dev.vanta.core.release.Sha256.hex(archive), entry.sha256(), entry.key());
        }
        assertEquals("llama-b11429/llama-server", manifest.platform("linux-x64").orElseThrow().serverPath());
        assertEquals("llama-server.exe", manifest.platform("windows-x64").orElseThrow().serverPath());
    }

    @Test
    void theFixtureParsesAndRoundTripsThroughToJson() throws IOException {
        JsonObject fixture = readJson(FIXTURES.resolve("installed.example.json"));
        LocalAiInstalled installed = LocalAiInstalled.fromJson(fixture).orElseThrow();
        assertEquals("linux-x64", installed.platform());
        assertEquals(INSTALLED_AT, installed.installedAt());
        assertEquals(VERIFIED_AT, installed.verifiedAt());
        assertEquals("b11429", installed.runtime().tag());
        assertEquals(List.of("linux-x64"), List.copyOf(installed.runtime().platforms().keySet()),
                "only the installed platform is recorded");
        assertEquals("llama-b11429/llama-server", installed.runtimePlatform().orElseThrow().serverPath());
        assertEquals("Qwen3-1.7B-Q8_0.gguf", installed.model().file());
        assertEquals(fixtureFacts(), installed.files());
        assertEquals(fixture, installed.toJson(), "fromJson(toJson) is the identity on the fixture");
        assertEquals(List.of("schemaVersion", "platform", "installedAt", "verifiedAt", "runtime", "model", "files"),
                List.copyOf(installed.toJson().keySet()));
        assertEquals(List.of("serverSize", "serverMtime", "modelSize", "modelMtime"),
                List.copyOf(installed.toJson().getAsJsonObject("files").keySet()));
        assertTrue(installed.matches(fixtureManifest()));
    }

    @Test
    void coreWritesExactlyTheFixtureFromTheFixedInputs() throws IOException {
        LocalAiManifest manifest = fixtureManifest();
        LocalAiInstalled built = new LocalAiInstalled(LINUX.key(), INSTALLED_AT, VERIFIED_AT,
                manifest.runtime().only(LINUX.key()), manifest.model(), fixtureFacts());
        assertEquals(readJson(FIXTURES.resolve("installed.example.json")), built.toJson());
    }

    /**
     * Lays the fixture out the way the launcher (and {@code prepare}) do: installed.json, the server executable at
     * runtime/<tag>/<platform>/<serverPath> and the model at models/<file>, with the recorded sizes and times.
     */
    static Path layOut(Path root, LocalAiManifest manifest) throws IOException {
        LocalAiPaths paths = LocalAiPaths.launcherManaged(root);
        Files.createDirectories(root);
        Files.copy(FIXTURES.resolve("installed.example.json"), paths.installedFile());
        LocalAiManifest.RuntimePlatform entry = manifest.forPlatform(LINUX).orElseThrow();
        Path server = paths.serverExecutable(manifest.runtime().tag(), LINUX, entry.serverPath());
        Files.createDirectories(server.getParent());
        Files.write(server, SERVER_BYTES);
        Files.setLastModifiedTime(server, FileTime.fromMillis(SERVER_MTIME));
        Path model = paths.modelFile(manifest.model().file());
        Files.createDirectories(model.getParent());
        Files.write(model, modelBytes());
        Files.setLastModifiedTime(model, FileTime.fromMillis(MODEL_MTIME));
        return root;
    }

    @Test
    void aLauncherWrittenDirectoryPassesTheQuickCheckAndTheFullVerification() throws Exception {
        LocalAiManifest manifest = fixtureManifest();
        Path root = layOut(dir.resolve("launcher-data").resolve("local-ai"), manifest);
        LocalAiPaths paths = LocalAiPaths.launcherManaged(root);
        LocalAiInstaller installer = new LocalAiInstaller(manifest, paths, LINUX, () -> HTTP,
                LocalAiInstaller.Config.defaults("1.4.0-test"), MutableClock.standard());

        assertEquals(LocalAiStatus.INSTALLED, installer.quickCheck());
        LocalAiInstalled read = installer.readInstalled().orElseThrow();
        assertEquals(readJson(FIXTURES.resolve("installed.example.json")), read.toJson());
        assertEquals(paths.root().resolve("runtime").resolve("b11429").resolve("linux-x64").resolve("llama-b11429")
                .resolve("llama-server"), installer.serverExecutable(read));
        assertEquals(LocalAiStatus.INSTALLED, installer.verify(LocalAiInstaller.Progress.none()),
                "the model re-hashes to the manifest's SHA-256");
        assertEquals(readJson(FIXTURES.resolve("installed.example.json")), readJson(paths.installedFile()),
                "a launcher-managed installed.json is never rewritten by the client");

        // The quick check still notices a changed model time; the full verification accepts the unchanged bytes.
        Files.setLastModifiedTime(paths.modelFile(manifest.model().file()), FileTime.fromMillis(MODEL_MTIME + 60_000));
        assertEquals(LocalAiStatus.PARTIAL, installer.quickCheck());
        assertEquals(LocalAiStatus.INSTALLED, installer.verify(LocalAiInstaller.Progress.none()));
    }

    @Test
    void aDirectoryWrittenForAnotherManifestIsPartial() throws Exception {
        LocalAiManifest manifest = fixtureManifest();
        Path root = layOut(dir.resolve("local-ai"), manifest);
        // The same layout checked against a manifest with another model hash: installed.json no longer matches.
        String json = Files.readString(FIXTURES.resolve("manifest.example.json"), StandardCharsets.UTF_8)
                .replace(manifest.model().sha256(), "0".repeat(64));
        LocalAiManifest other = LocalAiManifest.parse(json, false);
        LocalAiInstaller installer = new LocalAiInstaller(other, LocalAiPaths.launcherManaged(root), LINUX, () -> HTTP,
                LocalAiInstaller.Config.defaults("1.4.0-test"), MutableClock.standard());
        assertEquals(LocalAiStatus.PARTIAL, installer.quickCheck());
    }
}
