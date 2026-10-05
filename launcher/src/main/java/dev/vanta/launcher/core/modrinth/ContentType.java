package dev.vanta.launcher.core.modrinth;

import java.util.Locale;
import java.util.Optional;

/**
 * Kinds of Modrinth content the launcher installs into the VANTA instance.
 *
 * <p>{@link #id()} is both Modrinth's {@code project_type} and the {@code "type"} value in
 * {@code config/vanta/modrinth.json}. {@link #loader()} is the Modrinth loader a version must list for Minecraft to
 * load it here: Fabric mods, Iris shader packs, and resource packs, which Modrinth lists under the loader
 * {@code minecraft} (search results for resource packs carry the category {@code minecraft}).</p>
 */
public enum ContentType {
    /** Fabric mod, installed into {@code mods/}. */
    MOD("mod", "mods", "fabric", "categories:fabric"),
    /** Iris shader pack, installed into {@code shaderpacks/}. */
    SHADER("shader", "shaderpacks", "iris", "categories:iris"),
    /** Resource pack, installed into {@code resourcepacks/}. */
    RESOURCE_PACK("resourcepack", "resourcepacks", "minecraft", null);

    private final String id;
    private final String folder;
    private final String loader;
    private final String loaderFacet;

    ContentType(final String id, final String folder, final String loader, final String loaderFacet) {
        this.id = id;
        this.folder = folder;
        this.loader = loader;
        this.loaderFacet = loaderFacet;
    }

    /** @return Modrinth project type and the {@code type} value in {@code modrinth.json} */
    public String id() {
        return id;
    }

    /** @return the folder inside the instance */
    public String folder() {
        return folder;
    }

    /** @return the Modrinth loader a compatible version lists */
    public String loader() {
        return loader;
    }

    /** @return the search facet that limits results to content this instance can load, when one is needed */
    public Optional<String> loaderFacet() {
        return Optional.ofNullable(loaderFacet);
    }

    /**
     * @param projectType Modrinth {@code project_type} or {@code modrinth.json} {@code type}
     * @return the content type, empty for types the launcher does not install (modpacks, plugins, data packs)
     */
    public static Optional<ContentType> fromId(final String projectType) {
        if (projectType == null) {
            return Optional.empty();
        }
        final String p = projectType.trim().toLowerCase(Locale.ROOT);
        for (ContentType t : values()) {
            if (t.id.equals(p)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    /**
     * @param folderName first path segment of a {@code modrinth.json} {@code file} value
     * @return the content type stored in that folder
     */
    public static Optional<ContentType> fromFolder(final String folderName) {
        for (ContentType t : values()) {
            if (t.folder.equals(folderName)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
