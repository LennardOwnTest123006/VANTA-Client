package dev.vanta.core.modrinth;

import com.google.gson.JsonObject;
import java.util.Locale;

/**
 * A dependency declared by a version.
 *
 * @param versionId specific version required (may be {@code null})
 * @param projectId project required (may be {@code null} when only a version is given)
 * @param fileName  external file name (may be {@code null}; such dependencies cannot be installed automatically)
 * @param type      required / optional / incompatible / embedded
 */
public record ModrinthDependency(String versionId, String projectId, String fileName, Type type) {

    /** Modrinth's {@code dependency_type}. */
    public enum Type {
        REQUIRED, OPTIONAL, INCOMPATIBLE, EMBEDDED, UNKNOWN;

        static Type fromApi(String name) {
            if (name == null) {
                return UNKNOWN;
            }
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }

    static ModrinthDependency fromJson(JsonObject json) {
        return new ModrinthDependency(Json.nullableString(json, "version_id"), Json.nullableString(json, "project_id"),
                Json.nullableString(json, "file_name"), Type.fromApi(Json.string(json, "dependency_type", null)));
    }
}
