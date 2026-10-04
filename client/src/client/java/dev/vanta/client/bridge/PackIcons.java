package dev.vanta.client.bridge;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vanta.client.VantaClient;
import dev.vanta.core.ui.TextureRef;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
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
 * caches the result per pack id. Icons are registered as {@code vanta:packicon/<sanitised id>_<hash>} so the core can
 * draw them through a {@link TextureRef}.
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
            String path = "packicon/" + texturePath(pack.getId());
            Identifier id = Identifier.fromNamespaceAndPath(VantaClient.MOD_ID, path);
            minecraft.getTextureManager().register(id, new DynamicTexture(() -> "VANTA pack icon " + pack.getId(),
                    image));
            return Optional.of(new TextureRef(VantaClient.MOD_ID, path));
        } catch (IOException | RuntimeException e) {
            VantaClient.LOGGER.debug("No usable icon for resource pack {}", pack.getId(), e);
            return Optional.empty();
        }
    }

    /** Identifier-safe path segment for a pack id plus a hash so distinct ids never collide. */
    static String texturePath(String packId) {
        String lower = packId.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length() + 9);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_'
                    || c == '-';
            sb.append(ok ? c : '_');
        }
        return sb.append('_').append(Integer.toHexString(packId.hashCode())).toString();
    }
}
