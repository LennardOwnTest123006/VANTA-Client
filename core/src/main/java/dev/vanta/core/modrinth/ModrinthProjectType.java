package dev.vanta.core.modrinth;

import dev.vanta.core.i18n.LangKeyed;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The Modrinth project types VANTA installs, with the game folder each one goes to and the loader / category filters
 * used for Minecraft 1.21.11 with Fabric.
 */
public enum ModrinthProjectType implements LangKeyed {
    /** Fabric mods: {@code mods/*.jar}; versions must list the {@code fabric} loader. */
    MOD("mod", "mods", List.of("fabric"), "fabric"),
    /** Shader packs for Iris: {@code shaderpacks/*.zip}; versions must list the {@code iris} loader. */
    SHADER("shader", "shaderpacks", List.of("iris"), "iris"),
    /** Resource packs: {@code resourcepacks/*.zip}; versions list the {@code minecraft} loader. */
    RESOURCE_PACK("resourcepack", "resourcepacks", List.of("minecraft"), null);

    private final String apiName;
    private final String directory;
    private final List<String> loaders;
    private final String category;

    ModrinthProjectType(String apiName, String directory, List<String> loaders, String category) {
        this.apiName = apiName;
        this.directory = directory;
        this.loaders = loaders;
        this.category = category;
    }

    /** Name used by the Modrinth API ({@code project_type}) and in {@code modrinth.json} ({@code type}). */
    public String apiName() {
        return apiName;
    }

    /** Folder below the game directory the files of this type are written to. */
    public String directory() {
        return directory;
    }

    /** Loaders a version must list to be installable. */
    public List<String> loaders() {
        return loaders;
    }

    /** Search category facet ({@code fabric} for mods, {@code iris} for shaders), empty for resource packs. */
    public Optional<String> categoryFacet() {
        return Optional.ofNullable(category);
    }

    /** True when files of this type need a game restart to take effect (Fabric only loads mods at start-up). */
    public boolean needsRestart() {
        return this == MOD;
    }

    /** True when this type can be switched off by renaming the file ({@code .jar} → {@code .jar.disabled}). */
    public boolean canDisable() {
        return this == MOD;
    }

    @Override
    public String langKey() {
        return "vanta.mods.type." + apiName;
    }

    /** Maps the API name ({@code mod}, {@code shader}, {@code resourcepack}) to a type. */
    public static Optional<ModrinthProjectType> fromApi(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (ModrinthProjectType type : values()) {
            if (type.apiName.equals(wanted)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
