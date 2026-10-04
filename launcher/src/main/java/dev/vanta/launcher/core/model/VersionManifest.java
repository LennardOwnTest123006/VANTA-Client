package dev.vanta.launcher.core.model;

import java.util.List;
import java.util.Optional;

/**
 * Mojang {@code version_manifest_v2.json}.
 *
 * @param latest   latest release/snapshot ids
 * @param versions all versions
 */
public record VersionManifest(Latest latest, List<Entry> versions) {

    public VersionManifest {
        versions = versions == null ? List.of() : List.copyOf(versions);
    }

    /**
     * Finds a version by id.
     *
     * @param id e.g. {@code 1.21.11}
     * @return entry when present
     */
    public Optional<Entry> find(final String id) {
        return versions.stream().filter(v -> id.equals(v.id())).findFirst();
    }

    /**
     * Latest ids.
     *
     * @param release  latest release id
     * @param snapshot latest snapshot id
     */
    public record Latest(String release, String snapshot) {
    }

    /**
     * One version entry.
     *
     * @param id              version id
     * @param type            {@code release}, {@code snapshot}, ...
     * @param url             URL of the version JSON
     * @param time            last modification time
     * @param releaseTime     release time
     * @param sha1            SHA-1 of the version JSON
     * @param complianceLevel compliance level
     */
    public record Entry(String id, String type, String url, String time, String releaseTime, String sha1, int complianceLevel) {
    }
}
