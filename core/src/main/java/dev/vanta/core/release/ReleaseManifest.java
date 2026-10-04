package dev.vanta.core.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A release manifest as stored in {@code shared/releases/*.json} and validated by
 * {@code shared/schemas/release-manifest.schema.json}. The launcher's update check and the website read this shape;
 * {@code downloadUrl}, {@code size} and {@code sha256} are filled in by the release workflow only.
 */
public record ReleaseManifest(int schemaVersion, Product product, SemVer version, String minecraftVersion,
                              String fabricVersion, String fabricApiVersion, int javaVersion, LocalDate releaseDate,
                              Channel channel, List<ReleaseFile> files, String changelog, String notes) {
    /** Current manifest schema version. */
    public static final int SCHEMA_VERSION = 1;

    /** Which product the manifest describes. */
    public enum Product {
        CLIENT, LAUNCHER;

        /** Lower-case id used in JSON. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<Product> fromId(String id) {
            for (Product p : values()) {
                if (p.id().equalsIgnoreCase(id)) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        }
    }

    /** Release channel. */
    public enum Channel {
        STABLE, BETA;

        /** Lower-case id used in JSON. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<Channel> fromId(String id) {
            for (Channel c : values()) {
                if (c.id().equalsIgnoreCase(id)) {
                    return Optional.of(c);
                }
            }
            return Optional.empty();
        }
    }

    public ReleaseManifest {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(fabricVersion, "fabricVersion");
        Objects.requireNonNull(fabricApiVersion, "fabricApiVersion");
        Objects.requireNonNull(releaseDate, "releaseDate");
        Objects.requireNonNull(channel, "channel");
        files = List.copyOf(files);
        changelog = changelog == null ? "" : changelog;
        notes = notes == null ? "" : notes;
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
        if (javaVersion < 17) {
            throw new IllegalArgumentException("javaVersion must be >= 17");
        }
    }

    /** True when every file is published (non-empty URL, size and digest). */
    public boolean isPublished() {
        return !files.isEmpty() && files.stream().allMatch(ReleaseFile::isPublished);
    }

    /** The first file (the main artifact), if any. */
    public Optional<ReleaseFile> primaryFile() {
        return files.isEmpty() ? Optional.empty() : Optional.of(files.get(0));
    }

    /** Finds a file by name. */
    public Optional<ReleaseFile> file(String name) {
        return files.stream().filter(f -> f.name().equals(name)).findFirst();
    }

    /** JSON form matching the schema. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("schemaVersion", schemaVersion);
        o.addProperty("product", product.id());
        o.addProperty("version", version.toString());
        o.addProperty("minecraftVersion", minecraftVersion);
        o.addProperty("fabricVersion", fabricVersion);
        o.addProperty("fabricApiVersion", fabricApiVersion);
        o.addProperty("javaVersion", javaVersion);
        o.addProperty("releaseDate", releaseDate.toString());
        o.addProperty("channel", channel.id());
        JsonArray array = new JsonArray();
        for (ReleaseFile file : files) {
            array.add(file.toJson());
        }
        o.add("files", array);
        o.addProperty("changelog", changelog);
        o.addProperty("notes", notes);
        return o;
    }

    /**
     * Parses a manifest.
     *
     * @throws IllegalArgumentException when the JSON does not match the schema
     */
    public static ReleaseManifest fromJson(JsonObject o) {
        int schema = requireInt(o, "schemaVersion");
        if (schema > SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported manifest schemaVersion " + schema);
        }
        Product product = Product.fromId(requireString(o, "product"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown product"));
        SemVer version = SemVer.parseStrict(requireString(o, "version"));
        String minecraft = requireString(o, "minecraftVersion");
        if (product == Product.CLIENT && !"1.21.11".equals(minecraft)) {
            throw new IllegalArgumentException("Client manifests must target Minecraft 1.21.11, found " + minecraft);
        }
        LocalDate date;
        try {
            date = LocalDate.parse(requireString(o, "releaseDate"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("releaseDate must be YYYY-MM-DD", e);
        }
        Channel channel = Channel.fromId(requireString(o, "channel"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown channel"));
        List<ReleaseFile> files = new ArrayList<>();
        JsonElement filesElement = o.get("files");
        if (filesElement == null || !filesElement.isJsonArray()) {
            throw new IllegalArgumentException("'files' must be an array");
        }
        for (JsonElement element : filesElement.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Every entry of 'files' must be an object");
            }
            files.add(ReleaseFile.fromJson(element.getAsJsonObject()));
        }
        return new ReleaseManifest(schema, product, version, minecraft, requireString(o, "fabricVersion"),
                requireString(o, "fabricApiVersion"), requireInt(o, "javaVersion"), date, channel, files,
                optionalString(o, "changelog"), optionalString(o, "notes"));
    }

    /** Parses from a reader. */
    public static ReleaseManifest parse(Reader reader) {
        try {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                throw new IllegalArgumentException("Manifest must be a JSON object");
            }
            return fromJson(element.getAsJsonObject());
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException("Manifest is not valid JSON", e);
        }
    }

    private static String requireString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank()) {
            throw new IllegalArgumentException("Manifest is missing '" + key + "'");
        }
        return e.getAsString();
    }

    private static String optionalString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static int requireInt(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Manifest is missing numeric '" + key + "'");
        }
        return e.getAsInt();
    }
}
