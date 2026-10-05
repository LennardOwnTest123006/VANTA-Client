package dev.vanta.launcher.core.update;

import dev.vanta.launcher.core.model.ReleaseManifest;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * An available update.
 *
 * @param product        {@code launcher} or {@code client}
 * @param currentVersion installed version
 * @param latestVersion  version in the manifest
 * @param changelog      changelog text or path from the manifest
 * @param file           file to download; a file with an empty name means the release has no asset for this
 *                       platform (see {@link #hasPlatformAsset()})
 * @param sha256         expected SHA-256 of the file
 * @param manifest       the full manifest
 * @param releasePage    release page in a browser (empty when unknown)
 */
public record UpdateInfo(String product, SemVer currentVersion, SemVer latestVersion, String changelog,
                         ReleaseManifest.ReleaseFile file, String sha256, ReleaseManifest manifest, String releasePage) {

    /** How a downloaded file is used. */
    public enum AssetKind {
        /** Windows installer ({@code .msi}, {@code .exe}): handed to the operating system after confirmation. */
        INSTALLER,
        /** Archive with an app image ({@code .tar.gz}, {@code .zip}): the user extracts it. */
        ARCHIVE,
        /** Runnable jar: started with an installed Java 21. */
        JAR,
        /** Nothing to download for this platform (or unknown type). */
        NONE
    }

    public UpdateInfo {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(currentVersion, "currentVersion");
        Objects.requireNonNull(latestVersion, "latestVersion");
        Objects.requireNonNull(file, "file");
        changelog = changelog == null ? "" : changelog;
        sha256 = sha256 == null ? "" : sha256;
        releasePage = releasePage == null ? "" : releasePage.trim();
    }

    /**
     * Update without a known release page.
     *
     * @param product        product
     * @param currentVersion installed version
     * @param latestVersion  version in the manifest
     * @param changelog      changelog reference
     * @param file           file to download
     * @param sha256         expected SHA-256
     * @param manifest       manifest
     */
    public UpdateInfo(final String product, final SemVer currentVersion, final SemVer latestVersion, final String changelog,
                      final ReleaseManifest.ReleaseFile file, final String sha256, final ReleaseManifest manifest) {
        this(product, currentVersion, latestVersion, changelog, file, sha256, manifest, "");
    }

    /** @return whether the manifest version is newer than the installed one */
    public boolean isNewer() {
        return latestVersion.isNewerThan(currentVersion);
    }

    /** @return whether the update can be downloaded (URL present) */
    public boolean isDownloadable() {
        return file.isPublished();
    }

    /** @return whether the release lists a file for this platform (published or not) */
    public boolean hasPlatformAsset() {
        return file.name() != null && !file.name().isEmpty();
    }

    /** @return the release page when known */
    public Optional<URI> releasePageUri() {
        if (releasePage.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(URI.create(releasePage));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** @return how the downloaded file is used */
    public AssetKind assetKind() {
        if (!hasPlatformAsset()) {
            return AssetKind.NONE;
        }
        final String name = file.name().toLowerCase(Locale.ROOT);
        if (name.endsWith(".msi") || name.endsWith(".exe")) {
            return AssetKind.INSTALLER;
        }
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz") || name.endsWith(".zip")) {
            return AssetKind.ARCHIVE;
        }
        if (name.endsWith(".jar")) {
            return AssetKind.JAR;
        }
        return AssetKind.NONE;
    }
}
