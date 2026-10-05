package dev.vanta.core.screen.mods;

import dev.vanta.core.i18n.LangKeyed;
import dev.vanta.core.modrinth.ModrinthProjectType;
import java.util.Locale;
import java.util.Optional;

/** Tabs of the Mods &amp; Shaders screen. */
public enum ModsTab implements LangKeyed {
    MODS(ModrinthProjectType.MOD),
    SHADERS(ModrinthProjectType.SHADER),
    RESOURCE_PACKS(ModrinthProjectType.RESOURCE_PACK),
    INSTALLED(null);

    private final ModrinthProjectType type;

    ModsTab(ModrinthProjectType type) {
        this.type = type;
    }

    /** The Modrinth project type searched on this tab (empty for Installed). */
    public Optional<ModrinthProjectType> type() {
        return Optional.ofNullable(type);
    }

    @Override
    public String langKey() {
        return "vanta.mods.tab." + name().toLowerCase(Locale.ROOT);
    }
}
