package dev.vanta.launcher.testutil;

import dev.vanta.launcher.core.ai.LocalAiManifest;
import dev.vanta.launcher.core.net.Checksums;
import dev.vanta.launcher.core.net.HashAlgorithm;
import dev.vanta.launcher.core.util.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A fake Local AI world: small runtime archives for every platform the manifest knows (zip for Windows, tar.gz for
 * Linux and macOS, each with a fake {@code llama-server} inside) and a small fake GGUF model, served by a
 * {@link FakeHttpServer} with a manifest whose sizes and SHA-256 digests match the served bytes. Tests may corrupt a
 * served file afterwards to exercise the integrity checks.
 */
public final class FakeLocalAi implements AutoCloseable {

    /** Release tag of the fake runtime. */
    public static final String TAG = "b11429";
    /** File name of the fake model. */
    public static final String MODEL_FILE = "Qwen3-1.7B-Q8_0.gguf";
    /** Server path inside the Windows archives. */
    public static final String WINDOWS_SERVER = "llama-server.exe";
    /** Server path inside the Linux and macOS archives. */
    public static final String UNIX_SERVER = "build/bin/llama-server";
    /** Bytes of the fake server executable (a tiny shell script, so the file is a real executable on POSIX). */
    public static final byte[] SERVER_BYTES = "#!/bin/sh\necho fake llama-server b11429\n".getBytes(StandardCharsets.UTF_8);

    private final FakeHttpServer server;
    private final boolean ownsServer;
    private final String prefix;
    private final byte[] model;
    private final Map<String, byte[]> archives = new LinkedHashMap<>();
    private final LocalAiManifest manifest;

    /**
     * Own server, default tag.
     *
     * @throws IOException on failure
     */
    public FakeLocalAi() throws IOException {
        this(new FakeHttpServer(), true, TAG, 300_000);
    }

    /**
     * On a shared server.
     *
     * @param server shared fake server
     * @throws IOException on failure
     */
    public FakeLocalAi(final FakeHttpServer server) throws IOException {
        this(server, false, TAG, 300_000);
    }

    /**
     * @param server     server
     * @param ownsServer whether {@link #close()} stops it
     * @param tag        release tag (changes the archive contents, so a second world looks like a newer release)
     * @param modelSize  fake model size in bytes
     * @throws IOException on failure
     */
    public FakeLocalAi(final FakeHttpServer server, final boolean ownsServer, final String tag, final int modelSize) throws IOException {
        this.server = server;
        this.ownsServer = ownsServer;
        this.prefix = "local-ai/" + tag + "/";
        this.model = syntheticModel(tag, modelSize);
        server.add(prefix + MODEL_FILE, model);
        final Map<String, LocalAiManifest.PlatformFile> platforms = new LinkedHashMap<>();
        for (String key : new String[] {"windows-x64", "windows-arm64", "linux-x64", "linux-arm64", "macos-x64", "macos-arm64"}) {
            final boolean windows = key.startsWith("windows");
            final String file = "llama-" + tag + "-bin-" + (windows ? "win-cpu-" + key.substring(8) + ".zip"
                : key.startsWith("linux") ? "ubuntu-" + key.substring(6) + ".tar.gz" : "macos-" + key.substring(6) + ".tar.gz");
            final byte[] archive = windows ? zip(tag) : tarGz(tag);
            archives.put(key, archive);
            server.add(prefix + file, archive);
            platforms.put(key, new LocalAiManifest.PlatformFile(file, server.url(prefix + file).toString(), archive.length,
                Checksums.hex(archive, HashAlgorithm.SHA256), windows ? WINDOWS_SERVER : UNIX_SERVER));
        }
        final LocalAiManifest.Runtime runtime = new LocalAiManifest.Runtime("llama.cpp", "llama-server", tag, "MIT",
            "https://github.com/ggml-org/llama.cpp", "https://github.com/ggml-org/llama.cpp/releases/tag/" + tag, platforms);
        final LocalAiManifest.Model modelSection = new LocalAiManifest.Model("Qwen3-1.7B", "Q8_0", MODEL_FILE,
            server.url(prefix + MODEL_FILE).toString(), model.length, Checksums.hex(model, HashAlgorithm.SHA256), "Apache-2.0",
            "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/blob/main/LICENSE", "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF", 4096);
        this.manifest = new LocalAiManifest(1, "2026-10-08T12:00:00Z", runtime, modelSection, new LocalAiManifest.Requirements(1, 1));
    }

    /** @return the server */
    public FakeHttpServer server() {
        return server;
    }

    /** @return the manifest (complete: strict parsing accepts it) */
    public LocalAiManifest manifest() {
        return manifest;
    }

    /** @return the manifest as JSON text */
    public String manifestJson() {
        return Json.toJson(manifest);
    }

    /**
     * Writes the manifest to a file (for {@code VANTA_LOCAL_AI_MANIFEST}).
     *
     * @param file target
     * @return the file
     * @throws IOException on failure
     */
    public Path writeManifest(final Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, manifestJson(), StandardCharsets.UTF_8);
        return file;
    }

    /** @return the fake model bytes */
    public byte[] model() {
        return model.clone();
    }

    /**
     * @param platform platform key
     * @return the served archive bytes
     */
    public byte[] archive(final String platform) {
        return archives.get(platform).clone();
    }

    /** Serves different bytes for the model from now on (same length), so its SHA-256 no longer matches. */
    public void corruptModel() {
        final byte[] bad = model.clone();
        bad[bad.length / 2] ^= 0x55;
        server.add(prefix + MODEL_FILE, bad);
    }

    /**
     * Serves different bytes for a platform's archive (same length), so its SHA-256 no longer matches.
     *
     * @param platform platform key
     */
    public void corruptArchive(final String platform) {
        final byte[] bad = archives.get(platform).clone();
        bad[bad.length - 1] ^= 0x55;
        server.add(prefix + manifest.platform(platform).orElseThrow().file(), bad);
    }

    /**
     * @param platform platform key
     * @return request path of the archive on the fake server (for hit counting)
     */
    public String archivePath(final String platform) {
        return prefix + manifest.platform(platform).orElseThrow().file();
    }

    /** @return request path of the model on the fake server */
    public String modelPath() {
        return prefix + MODEL_FILE;
    }

    @Override
    public void close() {
        if (ownsServer) {
            server.close();
        }
    }

    private static byte[] syntheticModel(final String seed, final int size) {
        final byte[] out = new byte[size];
        long x = seed.hashCode() * 2654435761L + 12345;
        for (int i = 0; i < size; i++) {
            x = x * 6364136223846793005L + 1442695040888963407L;
            out[i] = (byte) (x >>> 56);
        }
        // GGUF files start with the magic "GGUF".
        out[0] = 'G';
        out[1] = 'G';
        out[2] = 'U';
        out[3] = 'F';
        return out;
    }

    private static byte[] zip(final String tag) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("LICENSE"));
            zip.write(("MIT License (fake, " + tag + ")\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("ggml.dll"));
            zip.write(("fake ggml " + tag).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(WINDOWS_SERVER));
            zip.write(serverBytes(tag));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static byte[] tarGz(final String tag) throws IOException {
        try (TarGzWriter tar = new TarGzWriter()) {
            tar.file("LICENSE", ("MIT License (fake, " + tag + ")\n").getBytes(StandardCharsets.UTF_8), 0644);
            tar.file("build/bin/libggml.so", ("fake ggml " + tag).getBytes(StandardCharsets.UTF_8), 0644);
            tar.file(UNIX_SERVER, serverBytes(tag), 0755);
            return tar.finish();
        }
    }

    private static byte[] serverBytes(final String tag) {
        return TAG.equals(tag) ? SERVER_BYTES : ("#!/bin/sh\necho fake llama-server " + tag + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
