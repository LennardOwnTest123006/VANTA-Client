package dev.vanta.launcher.core.ai;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code installed.json} in the Local AI folder: the manifest section that was installed plus when, for which platform
 * and the verified sizes and digests of the files the install produced. The quick check at start compares sizes and
 * modification times with this record; "Verify files" re-hashes everything.
 *
 * @param schemaVersion record schema version (1)
 * @param platform      platform key the runtime archive was chosen for
 * @param installedAt   when the install finished (ISO-8601)
 * @param verifiedAt    when the files were last fully verified (ISO-8601)
 * @param runtime       the installed llama.cpp release and its archive
 * @param model         the installed model (manifest section)
 * @param files         the verified files
 */
public record LocalAiInstalled(int schemaVersion, String platform, String installedAt, String verifiedAt, InstalledRuntime runtime,
                               LocalAiManifest.Model model, List<InstalledFile> files) {

    /** Current schema version. */
    public static final int SCHEMA_VERSION = 1;
    /** Role of the server executable in {@link #files()}. */
    public static final String ROLE_SERVER = "server";
    /** Role of the model file in {@link #files()}. */
    public static final String ROLE_MODEL = "model";

    public LocalAiInstalled {
        platform = platform == null ? "" : platform;
        installedAt = installedAt == null ? "" : installedAt;
        verifiedAt = verifiedAt == null ? "" : verifiedAt;
        files = files == null ? List.of() : List.copyOf(files);
    }

    /**
     * The installed release.
     *
     * @param name       project name
     * @param component  binary name
     * @param tag        release tag
     * @param license    licence id
     * @param sourceUrl  project page
     * @param releaseUrl release page
     * @param file       the archive that was installed (with its digest)
     */
    public record InstalledRuntime(String name, String component, String tag, String license, String sourceUrl, String releaseUrl,
                                   LocalAiManifest.PlatformFile file) {

        /**
         * @param runtime manifest runtime
         * @param file    the platform archive
         * @return record of what was installed
         */
        public static InstalledRuntime of(final LocalAiManifest.Runtime runtime, final LocalAiManifest.PlatformFile file) {
            return new InstalledRuntime(runtime.name(), runtime.component(), runtime.tag(), runtime.license(), runtime.sourceUrl(),
                runtime.releaseUrl(), file);
        }
    }

    /**
     * A verified file inside the Local AI folder.
     *
     * @param role           {@link #ROLE_SERVER} or {@link #ROLE_MODEL}
     * @param path           path relative to the Local AI folder (forward slashes)
     * @param size           size in bytes
     * @param sha256         lower-case hex SHA-256
     * @param modifiedMillis last modification time when verified (epoch millis)
     */
    public record InstalledFile(String role, String path, long size, String sha256, long modifiedMillis) {

        public InstalledFile {
            role = role == null ? "" : role;
            path = path == null ? "" : path.replace('\\', '/');
            sha256 = sha256 == null ? "" : sha256.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param role file role
     * @return the file with that role
     */
    public Optional<InstalledFile> file(final String role) {
        return files.stream().filter(f -> f.role().equals(role)).findFirst();
    }

    /**
     * @param at verification time (ISO-8601)
     * @param verified files with refreshed modification times
     * @return copy
     */
    public LocalAiInstalled withVerified(final String at, final List<InstalledFile> verified) {
        return new LocalAiInstalled(schemaVersion, platform, installedAt, at, runtime, model, verified);
    }

    /** @return e.g. {@code llama.cpp b11429, Qwen3-1.7B Q8_0} */
    public String describe() {
        final String runtimeText = runtime == null ? "runtime" : runtime.name() + " " + runtime.tag();
        final String modelText = model == null ? "model" : model.name() + " " + model.quantization();
        return runtimeText + ", " + modelText;
    }
}
