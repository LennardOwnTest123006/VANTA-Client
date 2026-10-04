package dev.vanta.client.bridge;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vanta.client.VantaClient;
import dev.vanta.core.ui.TextureRef;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Loads resource pack icons ({@code pack.png}) into dynamic textures, exactly like the vanilla pack screen does, and
 * caches the result per pack id. Icons are registered under exactly the identifier the core's
 * {@link dev.vanta.core.screen.packs.PackIcons#texture} expects ({@code vanta:pack_icons/<sanitised id>}) so the
 * resource pack screen can draw them through a {@link TextureRef}.
 */
final class PackIcons {
    private final Map<String, Optional<TextureRef>> cache = new HashMap<>();

    /** True when the pack ships a readable {@code pack.png}. */
    boolean hasIcon(Pack pack) {
        return texture(pack).isPresent();
    }

    /** The registered icon texture, loading it on first use. */
    Optional<TextureRef> texture(Pack pack) {
        return cache.computeIfAbsent(pack.getId(), id -> load(pack));
    }

    private static Optional<TextureRef> load(Pack pack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return Optional.empty();
        }
        try (PackResources resources = pack.open()) {
            IoSupplier<InputStream> supplier = resources.getRootResource("pack.png");
            if (supplier == null) {
                return Optional.empty();
            }
            NativeImage image;
            try (InputStream in = supplier.get()) {
                image = NativeImage.read(in);
            }
            TextureRef ref = dev.vanta.core.screen.packs.PackIcons.texture(pack.getId());
            Identifier id = Identifier.fromNamespaceAndPath(ref.namespace(), ref.path());
            minecraft.getTextureManager().register(id, new DynamicTexture(() -> "VANTA pack icon " + pack.getId(),
                    image));
            return Optional.of(ref);
        } catch (IOException | RuntimeException e) {
            VantaClient.LOGGER.debug("No usable icon for resource pack {}", pack.getId(), e);
            return Optional.empty();
        }
    }
}
