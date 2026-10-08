package dev.vanta.core.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Optional;

/**
 * Content of {@code installed.json}: the manifest sections that were installed (runtime with only this platform's
 * archive, model) plus when and for which platform, the verified hashes and the sizes and modification times the
 * quick check compares against.
 *
 * @param platform    platform key the runtime was installed for
 * @param installedAt ISO-8601 instant of the install
 * @param verifiedAt  ISO-8601 instant of the last full verification
 * @param runtime     installed runtime section (one platform)
 * @param model       installed model section (hash as verified)
 * @param files       sizes and mtimes of the server executable and the model file at verification time
 */
public record LocalAiInstalled(String platform, String installedAt, String verifiedAt, LocalAiManifest.Runtime runtime,
                               LocalAiManifest.Model model, Files files) {
    /** Schema version of {@code installed.json}. */
    public static final int SCHEMA_VERSION = 1;

    /** Sizes and modification times recorded at verification. */
    public record Files(long serverSize, long serverMtime, long modelSize, long modelMtime) {
    }

    public LocalAiInstalled {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(installedAt, "installedAt");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(files, "files");
    }

    /** The installed archive entry. */
    public Optional<LocalAiManifest.RuntimePlatform> runtimePlatform() {
        return Optional.ofNullable(runtime.platforms().get(platform));
    }

    /** Copy with a new verification time and file facts. */
    public LocalAiInstalled verified(String at, Files facts) {
        return new LocalAiInstalled(platform, installedAt, at, runtime, model, facts);
    }

    /** True when this install matches the manifest's runtime tag, archive hash and model hash. */
    public boolean matches(LocalAiManifest manifest) {
        Optional<LocalAiManifest.RuntimePlatform> mine = runtimePlatform();
        Optional<LocalAiManifest.RuntimePlatform> theirs = manifest.platform(platform);
        if (mine.isEmpty() || theirs.isEmpty()) {
            return false;
        }
        return runtime.tag().equals(manifest.runtime().tag())
                && mine.get().sha256().equals(theirs.get().sha256())
                && model.file().equals(manifest.model().file())
                && model.sha256().equals(manifest.model().sha256());
    }

    /** JSON form. */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("platform", platform);
        root.addProperty("installedAt", installedAt);
        root.addProperty("verifiedAt", verifiedAt);
        root.add("runtime", LocalAiManifest.runtimeJson(runtime));
        root.add("model", LocalAiManifest.modelJson(model));
        JsonObject f = new JsonObject();
        f.addProperty("serverSize", files.serverSize());
        f.addProperty("serverMtime", files.serverMtime());
        f.addProperty("modelSize", files.modelSize());
        f.addProperty("modelMtime", files.modelMtime());
        root.add("files", f);
        return root;
    }

    /**
     * Parses {@code installed.json}; empty for a malformed or incomplete file (the quick check then reports
     * {@link LocalAiStatus#PARTIAL}).
     */
    public static Optional<LocalAiInstalled> fromJson(JsonObject json) {
        try {
            String platform = str(json, "platform");
            String installedAt = str(json, "installedAt");
            String verifiedAt = str(json, "verifiedAt");
            if (platform == null || installedAt == null || verifiedAt == null) {
                return Optional.empty();
            }
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("schemaVersion", LocalAiManifest.SCHEMA_VERSION);
            wrapper.addProperty("resolvedAt", installedAt);
            wrapper.add("runtime", json.get("runtime"));
            wrapper.add("model", json.get("model"));
            JsonObject requirements = new JsonObject();
            requirements.addProperty("diskMb", 1);
            requirements.addProperty("ramMb", 1);
            wrapper.add("requirements", requirements);
            LocalAiManifest parsed = LocalAiManifest.fromJson(wrapper, false);
            if (parsed.platform(platform).isEmpty()) {
                return Optional.empty();
            }
            JsonElement f = json.get("files");
            Files files = new Files(0, 0, 0, 0);
            if (f != null && f.isJsonObject()) {
                JsonObject o = f.getAsJsonObject();
                files = new Files(num(o, "serverSize"), num(o, "serverMtime"), num(o, "modelSize"),
                        num(o, "modelMtime"));
            }
            return Optional.of(new LocalAiInstalled(platform, installedAt, verifiedAt, parsed.runtime(),
                    parsed.model(), files));
        } catch (LocalAiException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String str(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    private static long num(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0L;
    }
}
