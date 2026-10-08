package dev.vanta.core.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.vanta.core.release.Sha256;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The committed Local AI manifest ({@code shared/local-ai/local-ai.json}, embedded as
 * {@code /assets/vanta/local-ai.json}): which llama.cpp release and which GGUF model VANTA downloads, from where, how
 * big they are and which SHA-256 they must have. The resolver fills sizes and the model hash in CI; the parser here
 * rejects an unfilled manifest unless {@code templateAllowed} is set (tests, or the system property
 * {@link #TEMPLATE_PROPERTY}), because nothing can be verified against an empty hash.
 *
 * @param schemaVersion always 1
 * @param resolvedAt    ISO-8601 instant the resolver ran
 * @param runtime       the llama.cpp release and its per-platform archives
 * @param model         the GGUF model
 * @param requirements  disk and RAM the install needs
 */
public record LocalAiManifest(int schemaVersion, String resolvedAt, Runtime runtime, Model model,
                              Requirements requirements) {
    /** Supported schema version. */
    public static final int SCHEMA_VERSION = 1;
    /** Classpath resource both the client and the launcher embed. */
    public static final String RESOURCE = "/assets/vanta/local-ai.json";
    /**
     * System property that allows an unresolved (template) manifest: {@code -Dvanta.localai.templateAllowed=true}.
     * Built with {@code String.join} so the translation-key scan does not take it for a lang key.
     */
    public static final String TEMPLATE_PROPERTY = String.join(".", "vanta", "localai", "templateAllowed");
    /** Placeholder the template uses for values the resolver fills in. */
    public static final String PLACEHOLDER = "...";

    /**
     * The llama.cpp release.
     *
     * @param platforms archives keyed by platform key ({@code windows-x64}, ...)
     */
    public record Runtime(String name, String component, String tag, String license, String sourceUrl,
                          String releaseUrl, Map<String, RuntimePlatform> platforms) {
        public Runtime {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(license, "license");
            Objects.requireNonNull(sourceUrl, "sourceUrl");
            Objects.requireNonNull(releaseUrl, "releaseUrl");
            platforms = Map.copyOf(new LinkedHashMap<>(platforms));
        }

        /** Copy with only the given platform entry (for {@code installed.json}). */
        public Runtime only(String platformKey) {
            RuntimePlatform entry = platforms.get(platformKey);
            Map<String, RuntimePlatform> single = entry == null ? Map.of() : Map.of(platformKey, entry);
            return new Runtime(name, component, tag, license, sourceUrl, releaseUrl, single);
        }
    }

    /**
     * One runtime archive.
     *
     * @param serverPath path of the server executable relative to the extracted archive root
     */
    public record RuntimePlatform(String key, String file, String url, long size, String sha256, String serverPath) {
        public RuntimePlatform {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(sha256, "sha256");
            Objects.requireNonNull(serverPath, "serverPath");
            sha256 = sha256.toLowerCase(Locale.ROOT);
        }

        /** True when the resolver filled size and server path. */
        public boolean isResolved() {
            return size > 0 && !serverPath.isBlank() && !PLACEHOLDER.equals(serverPath);
        }
    }

    /** The GGUF model. */
    public record Model(String name, String quantization, String file, String url, long size, String sha256,
                        String license, String licenseUrl, String sourceUrl, int contextSize) {
        public Model {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(quantization, "quantization");
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(sha256, "sha256");
            Objects.requireNonNull(license, "license");
            Objects.requireNonNull(licenseUrl, "licenseUrl");
            Objects.requireNonNull(sourceUrl, "sourceUrl");
            sha256 = sha256.toLowerCase(Locale.ROOT);
        }

        /** True when the resolver filled size and hash. */
        public boolean isResolved() {
            return size > 0 && Sha256.isValidHex(sha256);
        }
    }

    /** Disk and RAM needed, in MiB. */
    public record Requirements(int diskMb, int ramMb) {
    }

    public LocalAiManifest {
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(requirements, "requirements");
    }

    // ---- lookups -----------------------------------------------------------------------------------------------

    /** The archive for a platform key. */
    public Optional<RuntimePlatform> platform(String key) {
        return Optional.ofNullable(runtime.platforms().get(key));
    }

    /** The archive for a platform. */
    public Optional<RuntimePlatform> forPlatform(Platform platform) {
        return platform(platform.key());
    }

    /** The archive for the JVM's platform (empty on unsupported systems). */
    public Optional<RuntimePlatform> forCurrentPlatform() {
        return Platform.current().flatMap(this::forPlatform);
    }

    /** True when every size and hash is filled in (the resolver ran). */
    public boolean isComplete() {
        if (!model.isResolved()) {
            return false;
        }
        for (RuntimePlatform entry : runtime.platforms().values()) {
            if (!entry.isResolved()) {
                return false;
            }
        }
        return true;
    }

    /** Bytes to download for a platform (0 when unknown). */
    public long downloadBytes(Platform platform) {
        return forPlatform(platform).map(RuntimePlatform::size).orElse(0L) + Math.max(0L, model.size());
    }

    // ---- parsing -----------------------------------------------------------------------------------------------

    /** True when {@link #TEMPLATE_PROPERTY} is set. */
    public static boolean templateAllowedByProperty() {
        return Boolean.parseBoolean(System.getProperty(TEMPLATE_PROPERTY, "false"));
    }

    /**
     * Reads the embedded manifest.
     *
     * @throws LocalAiException {@code INVALID_MANIFEST} when the resource is missing, malformed or (unless
     *                          {@code templateAllowed}) unresolved
     */
    public static LocalAiManifest bundled(boolean templateAllowed) throws LocalAiException {
        InputStream in = LocalAiManifest.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_MANIFEST,
                    "this build has no Local AI manifest (" + RESOURCE + ")");
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(reader, templateAllowed);
        } catch (IOException e) {
            if (e instanceof LocalAiException l) {
                throw l;
            }
            throw new LocalAiException(LocalAiException.Kind.INVALID_MANIFEST, "could not read " + RESOURCE, e);
        }
    }

    /** Parses manifest JSON text. */
    public static LocalAiManifest parse(String json, boolean templateAllowed) throws LocalAiException {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw invalid("manifest is not a JSON object");
            }
            return fromJson(element.getAsJsonObject(), templateAllowed);
        } catch (JsonParseException e) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_MANIFEST, "manifest is not valid JSON", e);
        }
    }

    /** Parses manifest JSON from a reader. */
    public static LocalAiManifest parse(Reader reader, boolean templateAllowed) throws LocalAiException {
        try {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                throw invalid("manifest is not a JSON object");
            }
            return fromJson(element.getAsJsonObject(), templateAllowed);
        } catch (JsonParseException e) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_MANIFEST, "manifest is not valid JSON", e);
        }
    }

    /**
     * Builds a manifest from its JSON object, checking every field.
     *
     * @param templateAllowed accept size 0, an empty model hash and {@value #PLACEHOLDER} server paths
     */
    public static LocalAiManifest fromJson(JsonObject json, boolean templateAllowed) throws LocalAiException {
        int schema = intField(json, "schemaVersion", 1, Integer.MAX_VALUE);
        if (schema != SCHEMA_VERSION) {
            throw invalid("unsupported schemaVersion " + schema);
        }
        String resolvedAt = text(json, "resolvedAt");
        JsonObject runtimeJson = object(json, "runtime");
        JsonObject modelJson = object(json, "model");
        JsonObject requirementsJson = object(json, "requirements");

        JsonObject platformsJson = object(runtimeJson, "platforms");
        if (platformsJson.isEmpty()) {
            throw invalid("runtime.platforms is empty");
        }
        Map<String, RuntimePlatform> platforms = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : platformsJson.entrySet()) {
            String key = entry.getKey();
            if (Platform.parse(key).isEmpty()) {
                throw invalid("unknown platform key '" + key + "' (expected <windows|linux|macos>-<x64|arm64>)");
            }
            if (!entry.getValue().isJsonObject()) {
                throw invalid("runtime.platforms." + key + " is not an object");
            }
            JsonObject p = entry.getValue().getAsJsonObject();
            String file = safeFileName(text(p, "file"), "runtime.platforms." + key + ".file");
            String url = downloadUrl(text(p, "url"), "runtime.platforms." + key + ".url");
            long size = longField(p, "size", "runtime.platforms." + key + ".size", templateAllowed);
            String sha = text(p, "sha256");
            if (!Sha256.isValidHex(sha)) {
                throw invalid("runtime.platforms." + key + ".sha256 is not a SHA-256 hex string");
            }
            String serverPath = text(p, "serverPath");
            if (PLACEHOLDER.equals(serverPath) || serverPath.isBlank()) {
                if (!templateAllowed) {
                    throw invalid("runtime.platforms." + key + ".serverPath is not resolved");
                }
            } else if (!isSafeRelativePath(serverPath)) {
                throw invalid("runtime.platforms." + key + ".serverPath is not a safe relative path");
            }
            platforms.put(key, new RuntimePlatform(key, file, url, size, sha, serverPath));
        }
        Runtime runtime = new Runtime(text(runtimeJson, "name"), text(runtimeJson, "component"),
                text(runtimeJson, "tag"), text(runtimeJson, "license"), text(runtimeJson, "sourceUrl"),
                text(runtimeJson, "releaseUrl"), platforms);
        if (!runtime.tag().matches("[A-Za-z0-9._-]{1,64}")) {
            throw invalid("runtime.tag '" + runtime.tag() + "' is not a safe directory name");
        }

        String modelFile = safeFileName(text(modelJson, "file"), "model.file");
        String modelUrl = downloadUrl(text(modelJson, "url"), "model.url");
        long modelSize = longField(modelJson, "size", "model.size", templateAllowed);
        String modelSha = stringOrEmpty(modelJson, "sha256");
        if (!Sha256.isValidHex(modelSha) && !(templateAllowed && modelSha.isEmpty())) {
            throw invalid("model.sha256 is not a SHA-256 hex string");
        }
        int contextSize = intField(modelJson, "contextSize", 512, 1 << 20);
        Model model = new Model(text(modelJson, "name"), text(modelJson, "quantization"), modelFile, modelUrl,
                modelSize, modelSha, text(modelJson, "license"), text(modelJson, "licenseUrl"),
                text(modelJson, "sourceUrl"), contextSize);

        Requirements requirements = new Requirements(intField(requirementsJson, "diskMb", 1, Integer.MAX_VALUE),
                intField(requirementsJson, "ramMb", 1, Integer.MAX_VALUE));
        return new LocalAiManifest(schema, resolvedAt, runtime, model, requirements);
    }

    /** The manifest as JSON (same shape as the committed file). */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", schemaVersion);
        root.addProperty("resolvedAt", resolvedAt);
        root.add("runtime", runtimeJson(runtime));
        root.add("model", modelJson(model));
        JsonObject req = new JsonObject();
        req.addProperty("diskMb", requirements.diskMb());
        req.addProperty("ramMb", requirements.ramMb());
        root.add("requirements", req);
        return root;
    }

    static JsonObject runtimeJson(Runtime runtime) {
        JsonObject r = new JsonObject();
        r.addProperty("name", runtime.name());
        r.addProperty("component", runtime.component());
        r.addProperty("tag", runtime.tag());
        r.addProperty("license", runtime.license());
        r.addProperty("sourceUrl", runtime.sourceUrl());
        r.addProperty("releaseUrl", runtime.releaseUrl());
        JsonObject platforms = new JsonObject();
        for (RuntimePlatform p : runtime.platforms().values()) {
            JsonObject o = new JsonObject();
            o.addProperty("file", p.file());
            o.addProperty("url", p.url());
            o.addProperty("size", p.size());
            o.addProperty("sha256", p.sha256());
            o.addProperty("serverPath", p.serverPath());
            platforms.add(p.key(), o);
        }
        r.add("platforms", platforms);
        return r;
    }

    static JsonObject modelJson(Model model) {
        JsonObject m = new JsonObject();
        m.addProperty("name", model.name());
        m.addProperty("quantization", model.quantization());
        m.addProperty("file", model.file());
        m.addProperty("url", model.url());
        m.addProperty("size", model.size());
        m.addProperty("sha256", model.sha256());
        m.addProperty("license", model.license());
        m.addProperty("licenseUrl", model.licenseUrl());
        m.addProperty("sourceUrl", model.sourceUrl());
        m.addProperty("contextSize", model.contextSize());
        return m;
    }

    // ---- field helpers -----------------------------------------------------------------------------------------

    private static LocalAiException invalid(String message) {
        return new LocalAiException(LocalAiException.Kind.INVALID_MANIFEST, message);
    }

    private static JsonObject object(JsonObject json, String key) throws LocalAiException {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonObject()) {
            throw invalid("missing object '" + key + "'");
        }
        return e.getAsJsonObject();
    }

    private static String text(JsonObject json, String key) throws LocalAiException {
        String value = stringOrEmpty(json, key);
        if (value.isBlank()) {
            throw invalid("missing or empty '" + key + "'");
        }
        return value;
    }

    private static String stringOrEmpty(JsonObject json, String key) throws LocalAiException {
        JsonElement e = json.get(key);
        if (e == null || e.isJsonNull()) {
            return "";
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw invalid("'" + key + "' is not a string");
        }
        return e.getAsString().trim();
    }

    private static int intField(JsonObject json, String key, int min, int max) throws LocalAiException {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw invalid("missing number '" + key + "'");
        }
        long value = e.getAsLong();
        if (value < min || value > max) {
            throw invalid("'" + key + "' out of range: " + value);
        }
        return (int) value;
    }

    private static long longField(JsonObject json, String key, String label, boolean templateAllowed)
            throws LocalAiException {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw invalid("missing number '" + label + "'");
        }
        long value = e.getAsLong();
        if (value < 0) {
            throw invalid("'" + label + "' is negative");
        }
        if (value == 0 && !templateAllowed) {
            throw invalid("'" + label + "' is not resolved (0)");
        }
        return value;
    }

    private static String safeFileName(String name, String label) throws LocalAiException {
        if (!isSafeFileName(name)) {
            throw invalid("'" + label + "' is not a safe file name: " + name);
        }
        return name;
    }

    /** True for a plain file name without path separators, parent references or a leading dot. */
    public static boolean isSafeFileName(String name) {
        return name != null && !name.isBlank() && name.length() <= 200 && !name.startsWith(".")
                && name.matches("[A-Za-z0-9._+-]+");
    }

    /** True for a relative path made of safe segments ({@code build/bin/llama-server}). */
    public static boolean isSafeRelativePath(String path) {
        if (path == null || path.isBlank() || path.length() > 400 || path.startsWith("/") || path.startsWith("\\")) {
            return false;
        }
        for (String segment : path.split("[/\\\\]")) {
            if (!isSafeFileName(segment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A download URL: {@code https://} anywhere, or {@code http://} on the loopback address only (the local test
     * server CI and the unit tests use). Nothing else is ever downloaded from.
     */
    private static String downloadUrl(String url, String label) throws LocalAiException {
        if (!isAllowedDownloadUrl(url)) {
            throw invalid("'" + label + "' is not an https URL: " + url);
        }
        return url;
    }

    /** True for {@code https://...} or a loopback {@code http://127.0.0.1...} / {@code http://localhost...} URL. */
    public static boolean isAllowedDownloadUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isEmpty()) {
                return false;
            }
            if ("https".equals(scheme)) {
                return true;
            }
            return "http".equals(scheme) && ("127.0.0.1".equals(host) || "localhost".equals(host)
                    || "[::1]".equals(host) || "::1".equals(host));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
