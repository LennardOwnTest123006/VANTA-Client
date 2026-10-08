package dev.vanta.launcher.core.ai;

import com.google.gson.JsonParseException;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The Local AI manifest ({@code shared/local-ai/local-ai.json}, embedded as the build resource {@value #RESOURCE}): which
 * llama.cpp release archive ({@code llama-server}) exists for each platform and which GGUF model is used, each with its
 * URL, size and SHA-256. The manifest is produced by the release tooling ({@code scripts/release/local-ai.mjs resolve}),
 * never typed by hand; the launcher only reads it.
 *
 * <p>The production parser is strict: every size must be known, every digest a full SHA-256 and every URL an http(s)
 * URL ({@link #parse(String)}). Unit tests may parse the template with placeholders ({@link #parse(String, boolean)}).
 * Nothing in the launcher ever downloads a file whose digest the manifest does not name.</p>
 *
 * @param schemaVersion manifest schema version (1)
 * @param resolvedAt    when the release tooling resolved the sizes and digests (ISO-8601)
 * @param runtime       the llama.cpp release with one archive per platform
 * @param model         the GGUF model
 * @param requirements  disk and memory the Local AI needs
 */
public record LocalAiManifest(int schemaVersion, String resolvedAt, Runtime runtime, Model model, Requirements requirements) {

    /** Classpath resource that holds the embedded copy of the manifest. */
    public static final String RESOURCE = "/local-ai.json";
    /** Environment variable naming a manifest file to use instead of the embedded resource (CI, tests). */
    public static final String MANIFEST_ENV = "VANTA_LOCAL_AI_MANIFEST";
    /** Supported manifest schema version. */
    public static final int SCHEMA_VERSION = 1;
    /** Platform keys the manifest may list ({@code <os>-<arch>}). */
    public static final Set<String> PLATFORMS = Set.of("windows-x64", "windows-arm64", "linux-x64", "linux-arm64", "macos-x64", "macos-arm64");

    private static final String PLACEHOLDER = "...";
    private static final int SHA256_HEX_LENGTH = 64;

    public LocalAiManifest {
        resolvedAt = resolvedAt == null ? "" : resolvedAt.trim();
        runtime = runtime == null ? new Runtime(null, null, null, null, null, null, null) : runtime;
        model = model == null ? new Model(null, null, null, null, 0, null, null, null, null, 0) : model;
        requirements = requirements == null ? new Requirements(0, 0) : requirements;
    }

    /**
     * The llama.cpp release.
     *
     * @param name       project name ({@code llama.cpp})
     * @param component  the binary used ({@code llama-server})
     * @param tag        release tag ({@code b11429})
     * @param license    licence id ({@code MIT})
     * @param sourceUrl  project page
     * @param releaseUrl release page
     * @param platforms  archive per platform key
     */
    public record Runtime(String name, String component, String tag, String license, String sourceUrl, String releaseUrl,
                          Map<String, PlatformFile> platforms) {

        public Runtime {
            name = blankToEmpty(name);
            component = blankToEmpty(component);
            tag = blankToEmpty(tag);
            license = blankToEmpty(license);
            sourceUrl = blankToEmpty(sourceUrl);
            releaseUrl = blankToEmpty(releaseUrl);
            platforms = platforms == null ? Map.of() : Map.copyOf(platforms);
        }

        /**
         * @param key platform key
         * @return copy with only that platform's archive (empty platforms when the key is unknown); what
         *         {@code installed.json} records
         */
        public Runtime only(final String key) {
            final PlatformFile entry = platforms.get(key);
            return new Runtime(name, component, tag, license, sourceUrl, releaseUrl, entry == null ? Map.of() : Map.of(key, entry));
        }
    }

    /**
     * One release archive.
     *
     * @param file       archive file name
     * @param url        download URL
     * @param size       size in bytes ({@code 0} only in an unresolved template)
     * @param sha256     lower-case hex SHA-256 of the archive
     * @param serverPath path of the server executable inside the extracted archive
     */
    public record PlatformFile(String file, String url, long size, String sha256, String serverPath) {

        public PlatformFile {
            file = blankToEmpty(file);
            url = blankToEmpty(url);
            sha256 = blankToEmpty(sha256).toLowerCase(Locale.ROOT);
            serverPath = blankToEmpty(serverPath).replace('\\', '/');
            size = Math.max(0L, size);
        }

        /** @return whether the archive is a {@code .tar.gz} / {@code .tgz} (else a {@code .zip}) */
        public boolean isTarGz() {
            final String lower = file.toLowerCase(Locale.ROOT);
            return lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
        }
    }

    /**
     * The GGUF model.
     *
     * @param name         model name
     * @param quantization quantisation ({@code Q8_0})
     * @param file         file name
     * @param url          download URL
     * @param size         size in bytes ({@code 0} only in an unresolved template)
     * @param sha256       lower-case hex SHA-256 ({@code ""} only in an unresolved template)
     * @param license      licence id
     * @param licenseUrl   licence text
     * @param sourceUrl    model page
     * @param contextSize  context size the client starts the server with
     */
    public record Model(String name, String quantization, String file, String url, long size, String sha256, String license,
                        String licenseUrl, String sourceUrl, int contextSize) {

        public Model {
            name = blankToEmpty(name);
            quantization = blankToEmpty(quantization);
            file = blankToEmpty(file);
            url = blankToEmpty(url);
            sha256 = blankToEmpty(sha256).toLowerCase(Locale.ROOT);
            license = blankToEmpty(license);
            licenseUrl = blankToEmpty(licenseUrl);
            sourceUrl = blankToEmpty(sourceUrl);
            size = Math.max(0L, size);
            contextSize = Math.max(0, contextSize);
        }
    }

    /**
     * What the Local AI needs.
     *
     * @param diskMb free disk space in MB
     * @param ramMb  free memory in MB
     */
    public record Requirements(int diskMb, int ramMb) {
    }

    // ---------------------------------------------------------------- loading

    /**
     * Parses a manifest strictly: every value must be resolved.
     *
     * @param json manifest text
     * @return manifest
     * @throws IOException when the JSON is malformed or any value is missing or unresolved
     */
    public static LocalAiManifest parse(final String json) throws IOException {
        return parse(json, false);
    }

    /**
     * Parses a manifest.
     *
     * @param json              manifest text
     * @param allowPlaceholders whether unresolved sizes ({@code 0}), digests ({@code ""}), URLs and server paths
     *                          ({@code "..."}) are accepted (unit tests of the template only; production never does)
     * @return manifest
     * @throws IOException when the JSON is malformed or invalid
     */
    public static LocalAiManifest parse(final String json, final boolean allowPlaceholders) throws IOException {
        final LocalAiManifest manifest;
        try {
            manifest = Json.parse(Objects.requireNonNull(json, "json"), LocalAiManifest.class);
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("The Local AI manifest is not valid JSON: " + e.getMessage(), e);
        }
        if (manifest == null) {
            throw new IOException("The Local AI manifest is empty");
        }
        final List<String> problems = manifest.problems(allowPlaceholders);
        if (!problems.isEmpty()) {
            throw new IOException("The Local AI manifest is incomplete: " + String.join("; ", problems));
        }
        return manifest;
    }

    /**
     * Reads a manifest file strictly.
     *
     * @param file manifest file
     * @return manifest
     * @throws IOException when unreadable or invalid
     */
    public static LocalAiManifest read(final Path file) throws IOException {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("Cannot read the Local AI manifest " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * The embedded manifest of this build, strictly parsed.
     *
     * @return manifest, empty when this build carries no {@value #RESOURCE}
     * @throws IOException when the embedded manifest is invalid
     */
    public static Optional<LocalAiManifest> resource() throws IOException {
        try (InputStream in = LocalAiManifest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    /**
     * The manifest to use: the file named by {@value #MANIFEST_ENV} when set, else the embedded resource.
     *
     * @param env environment variables
     * @return manifest
     * @throws IOException when the file or resource is missing or invalid
     */
    public static LocalAiManifest load(final Map<String, String> env) throws IOException {
        final String override = env.get(MANIFEST_ENV);
        if (override != null && !override.isBlank()) {
            return read(Path.of(override.trim()));
        }
        return resource().orElseThrow(() -> new IOException("This build of the launcher contains no Local AI manifest (" + RESOURCE
            + "); the Local AI cannot be installed with it"));
    }

    // ---------------------------------------------------------------- queries

    /**
     * @param os host platform
     * @return the manifest's platform key for the host, e.g. {@code windows-x64}, {@code macos-arm64}
     */
    public static String platformKey(final OsInfo os) {
        final String name = os.isWindows() ? "windows" : os.isMac() ? "macos" : os.isLinux() ? "linux" : os.name();
        return name + "-" + os.arch();
    }

    /**
     * @param key platform key
     * @return the archive for the platform when the manifest lists one
     */
    public Optional<PlatformFile> platform(final String key) {
        return Optional.ofNullable(runtime.platforms().get(key));
    }

    /**
     * @param key platform key
     * @return bytes the install downloads on that platform (archive plus model), {@code 0} when unsupported
     */
    public long downloadBytes(final String key) {
        return platform(key).map(p -> p.size() + model.size()).orElse(0L);
    }

    /** @return e.g. {@code llama.cpp b11429, Qwen3-1.7B Q8_0} */
    public String describe() {
        return runtime.name() + " " + runtime.tag() + ", " + model.name() + " " + model.quantization();
    }

    /**
     * Validation problems.
     *
     * @param allowPlaceholders whether unresolved template values are tolerated
     * @return problems, empty when the manifest is complete
     */
    public List<String> problems(final boolean allowPlaceholders) {
        final List<String> problems = new ArrayList<>();
        if (schemaVersion != SCHEMA_VERSION) {
            problems.add("schemaVersion must be " + SCHEMA_VERSION + " (is " + schemaVersion + ")");
        }
        if (resolvedAt.isEmpty() && !allowPlaceholders) {
            problems.add("resolvedAt is missing");
        }
        require(problems, "runtime.name", runtime.name());
        require(problems, "runtime.component", runtime.component());
        require(problems, "runtime.tag", runtime.tag());
        require(problems, "runtime.license", runtime.license());
        requireUrl(problems, "runtime.sourceUrl", runtime.sourceUrl(), false);
        requireUrl(problems, "runtime.releaseUrl", runtime.releaseUrl(), false);
        if (runtime.platforms().isEmpty()) {
            problems.add("runtime.platforms lists no platform");
        }
        for (Map.Entry<String, PlatformFile> e : runtime.platforms().entrySet()) {
            final String prefix = "runtime.platforms." + e.getKey();
            if (!PLATFORMS.contains(e.getKey())) {
                problems.add(prefix + " is not a known platform key");
            }
            final PlatformFile p = e.getValue();
            require(problems, prefix + ".file", p.file());
            if (!p.file().isEmpty() && !p.isTarGz() && !p.file().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                problems.add(prefix + ".file must be a .zip or .tar.gz archive");
            }
            requireUrl(problems, prefix + ".url", p.url(), allowPlaceholders);
            requireSize(problems, prefix + ".size", p.size(), allowPlaceholders);
            requireSha256(problems, prefix + ".sha256", p.sha256(), false);
            if (p.serverPath().isEmpty() || PLACEHOLDER.equals(p.serverPath())) {
                if (!allowPlaceholders || p.serverPath().isEmpty()) {
                    problems.add(prefix + ".serverPath is unresolved");
                }
            } else if (p.serverPath().startsWith("/") || p.serverPath().matches("^[A-Za-z]:.*")
                || List.of(p.serverPath().split("/")).contains("..")) {
                problems.add(prefix + ".serverPath must be a relative path inside the archive");
            }
        }
        require(problems, "model.name", model.name());
        require(problems, "model.quantization", model.quantization());
        require(problems, "model.file", model.file());
        requireUrl(problems, "model.url", model.url(), allowPlaceholders);
        requireSize(problems, "model.size", model.size(), allowPlaceholders);
        requireSha256(problems, "model.sha256", model.sha256(), allowPlaceholders);
        require(problems, "model.license", model.license());
        requireUrl(problems, "model.licenseUrl", model.licenseUrl(), false);
        requireUrl(problems, "model.sourceUrl", model.sourceUrl(), false);
        if (model.contextSize() <= 0) {
            problems.add("model.contextSize must be positive");
        }
        if (requirements.diskMb() <= 0) {
            problems.add("requirements.diskMb must be positive");
        }
        if (requirements.ramMb() <= 0) {
            problems.add("requirements.ramMb must be positive");
        }
        return problems;
    }

    private static void require(final List<String> problems, final String name, final String value) {
        if (value == null || value.isEmpty() || PLACEHOLDER.equals(value)) {
            problems.add(name + " is missing");
        }
    }

    private static void requireUrl(final List<String> problems, final String name, final String value, final boolean allowPlaceholder) {
        if (PLACEHOLDER.equals(value)) {
            if (!allowPlaceholder) {
                problems.add(name + " is unresolved");
            }
            return;
        }
        final String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("https://") || lower.startsWith("http://")) || lower.length() < 11) {
            problems.add(name + " is not an http(s) URL");
        }
    }

    private static void requireSize(final List<String> problems, final String name, final long size, final boolean allowPlaceholder) {
        if (size <= 0 && !allowPlaceholder) {
            problems.add(name + " is unresolved");
        }
    }

    private static void requireSha256(final List<String> problems, final String name, final String hex, final boolean allowPlaceholder) {
        if (hex == null || hex.isEmpty()) {
            if (!allowPlaceholder) {
                problems.add(name + " is unresolved");
            }
            return;
        }
        if (hex.length() != SHA256_HEX_LENGTH || !hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            problems.add(name + " is not a SHA-256 hex digest");
        }
    }

    private static String blankToEmpty(final String value) {
        return value == null ? "" : value.trim();
    }
}
