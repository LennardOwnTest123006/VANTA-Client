package dev.vanta.client.render;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Client resource reloader that clears the memoised text widths ({@link MinecraftTextMetrics}) and styled components
 * ({@link VantaFonts}) whenever the game's resources reload: a resource pack can replace the font providers, which
 * changes the glyph advances the cached widths were measured with. Registered by {@code VantaClient}.
 */
public final class TextCacheReloader implements ResourceManagerReloadListener {
    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        MinecraftTextMetrics.clearCache();
        VantaFonts.clearCache();
    }
}
