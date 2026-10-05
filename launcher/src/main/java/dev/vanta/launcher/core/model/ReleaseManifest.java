package dev.vanta.launcher.core.model;

import dev.vanta.launcher.core.util.OsInfo;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    /** File name prefix of the VANTA client mod jar ({@code vanta-client-<version>.jar}). */
    public static final String CLIENT_JAR_PREFIX = "vanta-client-";
    /** File name prefix of the launcher installers and app images ({@code VANTA-Launcher-<version>...}). */
    public static final String LAUNCHER_INSTALLER_PREFIX = "VANTA-Launcher-";
    /** File name prefix of the launcher fat jars ({@code vanta-launcher-<version>-<platform>-all.jar}). */
    public static final String LAUNCHER_JAR_PREFIX = "vanta-launcher-";

    private static final Pattern GITHUB_RELEASE_ASSET =
        Pattern.compile("^(https://github\\.com/[^/]+/[^/]+)/releases/download/([^/]+)/[^/]+$");

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
     * Finds the first file whose name ends with the given extension (case-insensitive). Prefer the exact lookups
     * {@link #clientJar()} and {@link #launcherAssetFor(OsInfo)}: a manifest lists several jars.
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
     * @param name exact file name
     * @return the file with exactly this name
     */
    public Optional<ReleaseFile> fileNamed(final String name) {
        return files.stream().filter(f -> f.name() != null && f.name().equals(name)).findFirst();
    }

    /** @return {@code vanta-client-<version>.jar}, the name of the Fabric mod jar of this client release */
    public String clientJarName() {
        return CLIENT_JAR_PREFIX + version + ".jar";
    }

    /**
     * The VANTA client mod jar: exactly {@code vanta-client-<version>.jar}. The bundled {@code -mods.zip}, the
     * Fabric API jar and any {@code -sources.jar} listed in the same manifest are never selected.
     *
     * @return the client jar when the manifest lists it
     */
    public Optional<ReleaseFile> clientJar() {
        return fileNamed(clientJarName());
    }

    /**
     * Launcher self-update asset names for a platform, in order of preference.
     *
     * <ul>
     *   <li>Windows: {@code VANTA-Launcher-<v>.msi}, then {@code VANTA-Launcher-<v>.exe}</li>
     *   <li>Linux x64: {@code VANTA-Launcher-<v>-linux-x64.tar.gz}</li>
     *   <li>macOS on Apple Silicon: {@code vanta-launcher-<v>-macos-aarch64-all.jar}</li>
     *   <li>anything else (Intel macOS, Linux on ARM, ...): none; the user downloads from the release page</li>
     * </ul>
     *
     * @param launcherVersion launcher version
     * @param os              platform
     * @return candidate file names (empty when the release has nothing for the platform)
     */
    public static List<String> launcherAssetNames(final String launcherVersion, final OsInfo os) {
        if (os.isWindows()) {
            return List.of(LAUNCHER_INSTALLER_PREFIX + launcherVersion + ".msi", LAUNCHER_INSTALLER_PREFIX + launcherVersion + ".exe");
        }
        if (os.isLinux() && "x64".equals(os.arch())) {
            return List.of(LAUNCHER_INSTALLER_PREFIX + launcherVersion + "-linux-x64.tar.gz");
        }
        if (os.isMac() && "arm64".equals(os.arch())) {
            return List.of(LAUNCHER_JAR_PREFIX + launcherVersion + "-macos-aarch64-all.jar");
        }
        return List.of();
    }

    /**
     * The launcher self-update asset of this manifest for a platform (see {@link #launcherAssetNames}).
     *
     * @param os platform
     * @return the file, also when it is listed but not published yet; empty when the platform has no asset
     */
    public Optional<ReleaseFile> launcherAssetFor(final OsInfo os) {
        for (String name : launcherAssetNames(version, os)) {
            final Optional<ReleaseFile> file = fileNamed(name);
            if (file.isPresent()) {
                return file;
            }
        }
        return Optional.empty();
    }

    /**
     * The file a platform should install: for the client exactly {@link #clientJar()}, for the launcher
     * {@link #launcherAssetFor(OsInfo)}.
     *
     * @param os platform
     * @return the file when the manifest lists one for the platform
     */
    public Optional<ReleaseFile> preferredFile(final OsInfo os) {
        return PRODUCT_LAUNCHER.equals(product) ? launcherAssetFor(os) : clientJar();
    }

    /**
     * The GitHub release page, derived from the download URL of a published file
     * ({@code https://github.com/<owner>/<repo>/releases/download/<tag>/<file>} becomes
     * {@code https://github.com/<owner>/<repo>/releases/tag/<tag>}).
     *
     * @return release page URL when a published file is a GitHub release asset
     */
    public Optional<String> releasePageUrl() {
        for (ReleaseFile f : files) {
            final Matcher m = GITHUB_RELEASE_ASSET.matcher(f.downloadUrl());
            if (m.matches()) {
                return Optional.of(m.group(1) + "/releases/tag/" + m.group(2));
            }
        }
        return Optional.empty();
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
