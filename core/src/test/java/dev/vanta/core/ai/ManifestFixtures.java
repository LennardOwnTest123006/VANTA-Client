package dev.vanta.core.ai;

import dev.vanta.core.release.Sha256;
import java.nio.charset.StandardCharsets;

/**
 * Complete manifests whose URLs point at a {@link LocalFileServer} and whose sizes and hashes match the fake
 * archives it serves.
 */
final class ManifestFixtures {
    static final String TAG = "b11429";
    static final String ZIP_PATH = "/releases/llama-" + TAG + "-bin-win-cpu-x64.zip";
    static final String TGZ_PATH = "/releases/llama-" + TAG + "-bin-ubuntu-x64.tar.gz";
    static final String MODEL_PATH = "/models/Qwen3-1.7B-Q8_0.gguf";
    static final String MODEL_FILE = "Qwen3-1.7B-Q8_0.gguf";

    private ManifestFixtures() {
    }

    /** Serves the fake archives and model and returns a complete manifest naming them. */
    static LocalAiManifest serve(LocalFileServer server, byte[] zip, byte[] tgz, byte[] model) throws LocalAiException {
        server.file(ZIP_PATH, zip);
        server.file(TGZ_PATH, tgz);
        server.file(MODEL_PATH, model);
        return LocalAiManifest.parse(json(server.baseUrl(), zip, tgz, model, Sha256.hex(model)), false);
    }

    /** Same, but with a wrong model hash. */
    static LocalAiManifest serveWithBadModelHash(LocalFileServer server, byte[] zip, byte[] tgz, byte[] model)
            throws LocalAiException {
        server.file(ZIP_PATH, zip);
        server.file(TGZ_PATH, tgz);
        server.file(MODEL_PATH, model);
        return LocalAiManifest.parse(json(server.baseUrl(), zip, tgz, model, "0".repeat(64)), false);
    }

    static String json(String baseUrl, byte[] zip, byte[] tgz, byte[] model, String modelSha) {
        return """
                {
                  "schemaVersion": 1,
                  "resolvedAt": "2026-10-08T12:00:00Z",
                  "runtime": {
                    "name": "llama.cpp", "component": "llama-server", "tag": "%s", "license": "MIT",
                    "sourceUrl": "https://github.com/ggml-org/llama.cpp",
                    "releaseUrl": "https://github.com/ggml-org/llama.cpp/releases/tag/%s",
                    "platforms": {
                      "windows-x64": { "file": "llama-%s-bin-win-cpu-x64.zip", "url": "%s%s", "size": %d, "sha256": "%s", "serverPath": "llama-server.exe" },
                      "linux-x64": { "file": "llama-%s-bin-ubuntu-x64.tar.gz", "url": "%s%s", "size": %d, "sha256": "%s", "serverPath": "build/bin/llama-server" },
                      "macos-arm64": { "file": "llama-%s-bin-macos-arm64.tar.gz", "url": "%s%s", "size": %d, "sha256": "%s", "serverPath": "build/bin/llama-server" }
                    }
                  },
                  "model": {
                    "name": "Qwen3-1.7B", "quantization": "Q8_0", "file": "%s",
                    "url": "%s%s", "size": %d, "sha256": "%s", "license": "Apache-2.0",
                    "licenseUrl": "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/blob/main/LICENSE",
                    "sourceUrl": "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF",
                    "contextSize": 4096
                  },
                  "requirements": { "diskMb": 2200, "ramMb": 3072 }
                }
                """.formatted(TAG, TAG,
                TAG, baseUrl, ZIP_PATH, zip.length, Sha256.hex(zip),
                TAG, baseUrl, TGZ_PATH, tgz.length, Sha256.hex(tgz),
                TAG, baseUrl, TGZ_PATH, tgz.length, Sha256.hex(tgz),
                MODEL_FILE, baseUrl, MODEL_PATH, model.length, modelSha);
    }

    /** The bundled template from the test classpath as text. */
    static String templateText() {
        try (var in = ManifestFixtures.class.getResourceAsStream(LocalAiManifest.RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource " + LocalAiManifest.RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
