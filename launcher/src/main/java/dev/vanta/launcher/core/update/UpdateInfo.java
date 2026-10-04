package dev.vanta.launcher.core.update;

import dev.vanta.launcher.core.model.ReleaseManifest;

import java.util.Objects;

/**
 * An available update.
 *
 * @param product        {@code launcher} or {@code client}
 * @param currentVersion installed version
 * @param latestVersion  version in the manifest
 * @param changelog      changelog text or path from the manifest
 * @param file           file to download
 * @param sha256         expected SHA-256 of the file
 * @param manifest       the full manifest
 */
public record UpdateInfo(String product, SemVer currentVersion, SemVer latestVersion, String changelog,
                         ReleaseManifest.ReleaseFile file, String sha256, ReleaseManifest manifest) {

    public UpdateInfo {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(currentVersion, "currentVersion");
        Objects.requireNonNull(latestVersion, "latestVersion");
        Objects.requireNonNull(file, "file");
        changelog = changelog == null ? "" : changelog;
        sha256 = sha256 == null ? "" : sha256;
    }

    /** @return whether the manifest version is newer than the installed one */
    public boolean isNewer() {
        return latestVersion.isNewerThan(currentVersion);
    }

    /** @return whether the update can be downloaded (URL present) */
    public boolean isDownloadable() {
        return file.isPublished();
    }
}
