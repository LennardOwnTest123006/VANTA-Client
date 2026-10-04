package dev.vanta.launcher.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Mojang version JSON (e.g. {@code 1.21.11.json}) or a Fabric profile converted to the same shape.
 *
 * @param id                     version id
 * @param type                   release type
 * @param mainClass              main class
 * @param inheritsFrom           parent version id (Fabric profiles)
 * @param assets                 asset index id (legacy field, mirrors {@code assetIndex.id})
 * @param assetIndex             asset index reference
 * @param downloads              {@code client}, {@code server}, ... downloads
 * @param libraries              libraries
 * @param arguments              modern arguments block
 * @param minecraftArguments     legacy game argument string (pre-1.13)
 * @param javaVersion            required Java runtime
 * @param logging                log4j configuration
 * @param releaseTime            release time
 * @param time                   modification time
 * @param minimumLauncherVersion minimum launcher version
 * @param complianceLevel        compliance level
 */
public record VersionJson(String id, String type, String mainClass, String inheritsFrom, String assets,
                          AssetIndexRef assetIndex, Map<String, Download> downloads, List<Library> libraries,
                          Arguments arguments, String minecraftArguments, JavaVersionRef javaVersion, Logging logging,
                          String releaseTime, String time, int minimumLauncherVersion, int complianceLevel) {

    public VersionJson {
        downloads = downloads == null ? Map.of() : Map.copyOf(downloads);
        libraries = libraries == null ? List.of() : List.copyOf(libraries);
        arguments = arguments == null ? Arguments.EMPTY : arguments;
    }

    /** @return the client jar download when present */
    public Optional<Download> clientDownload() {
        return Optional.ofNullable(downloads.get("client"));
    }

    /** @return the asset index id ({@code assetIndex.id}, falling back to {@code assets}) */
    public String assetIndexId() {
        if (assetIndex != null && assetIndex.id() != null) {
            return assetIndex.id();
        }
        return assets == null ? "legacy" : assets;
    }

    /** @return required Java major version, defaulting to 21 for modern versions */
    public int javaMajor() {
        return javaVersion == null || javaVersion.majorVersion() <= 0 ? 21 : javaVersion.majorVersion();
    }

    /**
     * Merges a child (Fabric profile) onto its parent (vanilla): the child's main class and id win, libraries are
     * child first then parent, and arguments are parent first then child.
     *
     * @param parent the version referenced by {@link #inheritsFrom()}
     * @return merged version with {@code inheritsFrom} cleared
     */
    public VersionJson mergeOnto(final VersionJson parent) {
        final List<Library> libs = new ArrayList<>(libraries);
        libs.addAll(parent.libraries());
        return new VersionJson(
            id != null ? id : parent.id(),
            type != null ? type : parent.type(),
            mainClass != null ? mainClass : parent.mainClass(),
            null,
            assets != null ? assets : parent.assets(),
            assetIndex != null ? assetIndex : parent.assetIndex(),
            downloads.isEmpty() ? parent.downloads() : downloads,
            libs,
            parent.arguments().concat(arguments),
            minecraftArguments != null ? minecraftArguments : parent.minecraftArguments(),
            javaVersion != null ? javaVersion : parent.javaVersion(),
            logging != null ? logging : parent.logging(),
            releaseTime != null ? releaseTime : parent.releaseTime(),
            time != null ? time : parent.time(),
            Math.max(minimumLauncherVersion, parent.minimumLauncherVersion()),
            Math.max(complianceLevel, parent.complianceLevel()));
    }

    /**
     * Asset index reference.
     *
     * @param id        index id (e.g. {@code 26})
     * @param url       URL of the index JSON
     * @param sha1      SHA-1 of the index JSON
     * @param size      size of the index JSON
     * @param totalSize total size of all objects
     */
    public record AssetIndexRef(String id, String url, String sha1, long size, long totalSize) {
    }

    /**
     * Required Java runtime.
     *
     * @param component    Mojang runtime component name (e.g. {@code java-runtime-delta})
     * @param majorVersion major version (e.g. 21)
     */
    public record JavaVersionRef(String component, int majorVersion) {
    }

    /**
     * Logging block.
     *
     * @param client client logging configuration
     */
    public record Logging(LoggingClient client) {
    }

    /**
     * Client logging configuration.
     *
     * @param argument JVM argument with {@code ${path}} placeholder
     * @param file     log4j configuration file
     * @param type     configuration type ({@code log4j2-xml})
     */
    public record LoggingClient(String argument, LoggingFile file, String type) {
    }

    /**
     * Log configuration file reference.
     *
     * @param id   file name
     * @param url  URL
     * @param sha1 SHA-1
     * @param size size
     */
    public record LoggingFile(String id, String url, String sha1, long size) {
    }
}
