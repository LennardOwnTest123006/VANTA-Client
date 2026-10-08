package dev.vanta.launcher.core.ai;

import java.util.Objects;
import java.util.Optional;

/**
 * {@code installed.json} in the Local AI folder, in exactly the shape the VANTA Client reads
 * ({@code dev.vanta.core.ai.LocalAiInstalled}; the committed example is {@code shared/local-ai/fixtures/installed.example.json}):
 * the manifest sections that were installed (the runtime with only this platform's archive, the model) plus when, for
 * which platform, and the sizes and modification times of the two files the install produced. The quick check at start
 * compares sizes and modification times with this record; "Verify files" hashes the model again. The recorded digests
 * are the manifest's (the archive's and the model's); the extracted server executable has no digest of its own.
 *
 * <pre>
 * { schemaVersion, platform, installedAt, verifiedAt,
 *   runtime: { name, component, tag, license, sourceUrl, releaseUrl, platforms: { &lt;platform&gt;: { file, url, size, sha256, serverPath } } },
 *   model: { name, quantization, file, url, size, sha256, license, licenseUrl, sourceUrl, contextSize },
 *   files: { serverSize, serverMtime, modelSize, modelMtime } }
 * </pre>
 *
 * @param schemaVersion record schema version (1)
 * @param platform      platform key the runtime archive was chosen for
 * @param installedAt   when the install finished (ISO-8601 UTC instant)
 * @param verifiedAt    when the files were last fully verified (ISO-8601 UTC instant)
 * @param runtime       the installed llama.cpp release with only this platform's archive
 * @param model         the installed model (manifest section)
 * @param files         sizes and modification times (epoch milliseconds) of the server executable and the model file
 */
public record LocalAiInstalled(int schemaVersion, String platform, String installedAt, String verifiedAt, LocalAiManifest.Runtime runtime,
                               LocalAiManifest.Model model, Files files) {

    /** Current schema version. */
    public static final int SCHEMA_VERSION = 1;
    /** Folder of extracted runtimes below the Local AI folder. */
    public static final String RUNTIME_DIR = "runtime";
    /** Folder of model files below the Local AI folder. */
    public static final String MODELS_DIR = "models";

    /**
     * Sizes and modification times recorded at verification.
     *
     * @param serverSize  bytes of the server executable
     * @param serverMtime its modification time in epoch milliseconds
     * @param modelSize   bytes of the model file
     * @param modelMtime  its modification time in epoch milliseconds
     */
    public record Files(long serverSize, long serverMtime, long modelSize, long modelMtime) {
    }

    public LocalAiInstalled {
        platform = platform == null ? "" : platform;
        installedAt = installedAt == null ? "" : installedAt;
        verifiedAt = verifiedAt == null ? "" : verifiedAt;
        files = files == null ? new Files(0, 0, 0, 0) : files;
    }

    /**
     * The record of an install of {@code manifest} on {@code platform}.
     *
     * @param manifest    the manifest that was installed
     * @param platform    platform key (must be listed by the manifest)
     * @param installedAt install instant (ISO-8601)
     * @param verifiedAt  verification instant (ISO-8601)
     * @param files       sizes and times of the server executable and the model file
     * @return record with only that platform's archive in its runtime section
     */
    public static LocalAiInstalled of(final LocalAiManifest manifest, final String platform, final String installedAt, final String verifiedAt,
                                      final Files files) {
        Objects.requireNonNull(manifest, "manifest");
        return new LocalAiInstalled(SCHEMA_VERSION, platform, installedAt, verifiedAt, manifest.runtime().only(platform), manifest.model(), files);
    }

    /** @return whether the record names a runtime, its archive for {@link #platform()} and a model */
    public boolean isComplete() {
        return runtime != null && model != null && !platform.isEmpty() && runtimePlatform().isPresent() && !runtime.tag().isEmpty()
            && !model.file().isEmpty();
    }

    /** @return the installed archive entry (the one for {@link #platform()}) */
    public Optional<LocalAiManifest.PlatformFile> runtimePlatform() {
        return runtime == null ? Optional.empty() : Optional.ofNullable(runtime.platforms().get(platform));
    }

    /** @return {@code runtime/<tag>/<platform>/<serverPath>} with forward slashes, relative to the Local AI folder */
    public String serverRelativePath() {
        final String serverPath = runtimePlatform().map(LocalAiManifest.PlatformFile::serverPath).orElse("");
        return RUNTIME_DIR + "/" + (runtime == null ? "" : runtime.tag()) + "/" + platform + "/" + serverPath;
    }

    /** @return {@code models/<file>}, relative to the Local AI folder */
    public String modelRelativePath() {
        return MODELS_DIR + "/" + (model == null ? "" : model.file());
    }

    /**
     * @param at    verification time (ISO-8601)
     * @param facts refreshed sizes and modification times
     * @return copy
     */
    public LocalAiInstalled withVerified(final String at, final Files facts) {
        return new LocalAiInstalled(schemaVersion, platform, installedAt, at, runtime, model, facts);
    }

    /** @return e.g. {@code llama.cpp b11429, Qwen3-1.7B Q8_0} */
    public String describe() {
        final String runtimeText = runtime == null ? "runtime" : runtime.name() + " " + runtime.tag();
        final String modelText = model == null ? "model" : model.name() + " " + model.quantization();
        return runtimeText + ", " + modelText;
    }
}
