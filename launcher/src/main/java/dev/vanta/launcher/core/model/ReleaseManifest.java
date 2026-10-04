package dev.vanta.launcher.core.model;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A VANTA release manifest ({@code shared/releases/*.json}, {@code <releasesBaseUrl>/client-latest.json}, ...).
 * Mirrors {@code shared/schemas/release-manifest.schema.json}.
 *
 * @param schemaVersion    schema version (1)
 * @param product          {@code client} or {@code launcher}
 * @param version          SemVer of the product
 * @param minecraftVersion Minecraft version the release targets
 * @param fabricVersion    Fabric Loader version
 * @param fabricApiVersion Fabric API version
 * @param javaVersion      required Java major
 * @param releaseDate      ISO date
 * @param channel          {@code stable}, {@code beta}, ...
 * @param files            downloadable files
 * @param changelog        repository-relative path of the release notes
 * @param notes            optional free-text note (known issues); empty when absent
 */
public record ReleaseManifest(int schemaVersion, String product, String version, String minecraftVersion,
                              String fabricVersion, String fabricApiVersion, int javaVersion, String releaseDate,
                              String channel, List<ReleaseFile> files, String changelog, String notes) {

    /** Product name of the Fabric mod. */
    public static final String PRODUCT_CLIENT = "client";
    /** Product name of the launcher. */
    public static final String PRODUCT_LAUNCHER = "launcher";

    public ReleaseManifest {
        files = files == null ? List.of() : List.copyOf(files);
        channel = channel == null ? "stable" : channel;
        changelog = changelog == null ? "" : changelog;
        notes = notes == null ? "" : notes;
    }

    /**
     * Manifest without a note.
     *
     * @param schemaVersion    schema version
     * @param product          product
     * @param version          version
     * @param minecraftVersion Minecraft version
     * @param fabricVersion    Fabric Loader version
     * @param fabricApiVersion Fabric API version
     * @param javaVersion      Java major
     * @param releaseDate      release date
     * @param channel          channel
     * @param files            files
     * @param changelog        changelog path
     */
    public ReleaseManifest(final int schemaVersion, final String product, final String version, final String minecraftVersion,
                           final String fabricVersion, final String fabricApiVersion, final int javaVersion, final String releaseDate,
                           final String channel, final List<ReleaseFile> files, final String changelog) {
        this(schemaVersion, product, version, minecraftVersion, fabricVersion, fabricApiVersion, javaVersion, releaseDate, channel,
            files, changelog, "");
    }

    /** @return whether a free-text note is present */
    public boolean hasNotes() {
        return !notes.isEmpty();
    }

    /** @return whether at least one file has a download URL */
    public boolean isPublished() {
        return files.stream().anyMatch(ReleaseFile::isPublished);
    }

    /**
     * Finds the first published file whose name ends with the given extension (case-insensitive).
     *
     * @param extension e.g. {@code .jar}, {@code .msi}
     * @return file when present
     */
    public Optional<ReleaseFile> fileWithExtension(final String extension) {
        final String ext = extension.toLowerCase(Locale.ROOT);
        return files.stream()
            .filter(f -> f.name() != null && f.name().toLowerCase(Locale.ROOT).endsWith(ext))
            .findFirst();
    }

    /**
     * The file a given platform should install: for the client the jar; for the launcher the Windows installer
     * ({@code .msi} preferred, then {@code .exe}) on Windows, else the portable jar.
     *
     * @param windows whether the running platform is Windows
     * @return the preferred file when the manifest lists one
     */
    public Optional<ReleaseFile> preferredFile(final boolean windows) {
        if (PRODUCT_LAUNCHER.equals(product) && windows) {
            final Optional<ReleaseFile> msi = fileWithExtension(".msi");
            if (msi.isPresent()) {
                return msi;
            }
            final Optional<ReleaseFile> exe = fileWithExtension(".exe");
            if (exe.isPresent()) {
                return exe;
            }
        }
        final Optional<ReleaseFile> jar = fileWithExtension(".jar");
        if (jar.isPresent()) {
            return jar;
        }
        return files.stream().findFirst();
    }

    /**
     * A file of a release.
     *
     * @param name        file name
     * @param downloadUrl absolute download URL; empty means not published yet
     * @param size        size in bytes (0 when unknown)
     * @param sha256      hex SHA-256; empty means unknown
     */
    public record ReleaseFile(String name, String downloadUrl, long size, String sha256) {

        public ReleaseFile {
            downloadUrl = downloadUrl == null ? "" : downloadUrl.trim();
            sha256 = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT);
        }

        /** @return whether the file has a download URL */
        public boolean isPublished() {
            return !downloadUrl.isEmpty();
        }

        /** @return whether a SHA-256 is present */
        public boolean hasSha256() {
            return !sha256.isEmpty();
        }
    }
}
