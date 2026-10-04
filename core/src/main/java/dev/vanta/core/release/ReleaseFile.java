package dev.vanta.core.release;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Objects;

/**
 * One downloadable file of a release. An empty {@code downloadUrl} means "not published yet".
 *
 * @param name        file name
 * @param downloadUrl https URL or empty
 * @param size        bytes (0 when unpublished)
 * @param sha256      lower-case hex digest or empty
 */
public record ReleaseFile(String name, String downloadUrl, long size, String sha256) {
    public ReleaseFile {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException("Invalid release file name: " + name);
        }
        downloadUrl = downloadUrl == null ? "" : downloadUrl.trim();
        if (!downloadUrl.isEmpty() && !downloadUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Download URL must use https: " + downloadUrl);
        }
        if (size < 0) {
            throw new IllegalArgumentException("Size must be >= 0");
        }
        sha256 = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT);
        if (!sha256.isEmpty() && !Sha256.isValidHex(sha256)) {
            throw new IllegalArgumentException("sha256 must be 64 hex characters");
        }
    }

    /** True when URL, size and digest are all present. */
    public boolean isPublished() {
        return !downloadUrl.isEmpty() && size > 0 && !sha256.isEmpty();
    }

    /** JSON form. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("downloadUrl", downloadUrl);
        o.addProperty("size", size);
        o.addProperty("sha256", sha256);
        return o;
    }

    /**
     * Parses JSON.
     *
     * @throws IllegalArgumentException when fields are missing or invalid
     */
    public static ReleaseFile fromJson(JsonObject o) {
        if (!o.has("name")) {
            throw new IllegalArgumentException("Release file is missing 'name'");
        }
        return new ReleaseFile(o.get("name").getAsString(),
                o.has("downloadUrl") && !o.get("downloadUrl").isJsonNull() ? o.get("downloadUrl").getAsString() : "",
                o.has("size") && !o.get("size").isJsonNull() ? o.get("size").getAsLong() : 0,
                o.has("sha256") && !o.get("sha256").isJsonNull() ? o.get("sha256").getAsString() : "");
    }
}
