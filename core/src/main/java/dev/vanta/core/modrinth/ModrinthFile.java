package dev.vanta.core.modrinth;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Optional;

/**
 * One file of a version.
 *
 * @param url      download URL exactly as Modrinth returned it
 * @param filename file name
 * @param primary  whether Modrinth marks it as the primary file
 * @param size     size in bytes (0 when unknown)
 * @param sha512   lower-case hex SHA-512 Modrinth published (empty when missing)
 * @param sha1     lower-case hex SHA-1 (informational)
 */
public record ModrinthFile(String url, String filename, boolean primary, long size, String sha512, String sha1) {
    public ModrinthFile {
        url = url == null ? "" : url;
        filename = filename == null ? "" : filename;
        sha512 = sha512 == null ? "" : sha512.toLowerCase(Locale.ROOT);
        sha1 = sha1 == null ? "" : sha1.toLowerCase(Locale.ROOT);
    }

    /** True when a well-formed SHA-512 is present; files without one are never installed. */
    public boolean hasSha512() {
        return isSha512(sha512);
    }

    /** True for a 128-character hex string. */
    public static boolean isSha512(String hex) {
        return hex != null && hex.matches("[0-9a-fA-F]{128}");
    }

    /**
     * True when the file name can be written as-is: no path separators or parent references, no leading dot, no control
     * characters, at most 200 characters.
     */
    public boolean hasSafeFilename() {
        return isSafeFilename(filename);
    }

    /** See {@link #hasSafeFilename()}. */
    public static boolean isSafeFilename(String name) {
        if (name == null || name.isBlank() || name.length() > 200 || name.startsWith(".")) {
            return false;
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..") || name.contains(":")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7f || "<>\"|?*".indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    static Optional<ModrinthFile> fromJson(JsonObject json) {
        String url = Json.nullableString(json, "url");
        String filename = Json.nullableString(json, "filename");
        if (url == null || filename == null) {
            return Optional.empty();
        }
        JsonObject hashes = Json.object(json, "hashes");
        return Optional.of(new ModrinthFile(url, filename, Json.bool(json, "primary", false),
                Json.longValue(json, "size", 0), Json.string(hashes, "sha512"), Json.string(hashes, "sha1")));
    }
}
