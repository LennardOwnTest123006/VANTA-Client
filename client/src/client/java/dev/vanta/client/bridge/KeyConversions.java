package dev.vanta.client.bridge;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.bridge.KeyType;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Conversions between the core's Minecraft-free {@link KeyRef} and {@link InputConstants.Key}, plus the mapping of
 * key-mapping categories to the {@code key.categories.*} ids the core groups by.
 */
public final class KeyConversions {
    private KeyConversions() {
    }

    /** The vanilla namespace. */
    private static final String MINECRAFT = "minecraft";

    /** Converts a core key reference to the game's key; unbound references become {@link InputConstants#UNKNOWN}. */
    public static InputConstants.Key toKey(KeyRef ref) {
        if (ref == null || ref.isUnbound()) {
            return InputConstants.UNKNOWN;
        }
        return toType(ref.type()).getOrCreate(ref.code());
    }

    /** Converts a game key to a core key reference ({@code UNKNOWN} maps to an unbound reference). */
    public static KeyRef toRef(InputConstants.Key key) {
        if (key == null) {
            return KeyRef.UNBOUND;
        }
        return new KeyRef(toType(key.getType()), key.getValue(), key.getName());
    }

    /** {@link KeyType} to {@link InputConstants.Type}; the two enums share constant names. */
    public static InputConstants.Type toType(KeyType type) {
        return switch (type) {
            case KEYSYM -> InputConstants.Type.KEYSYM;
            case SCANCODE -> InputConstants.Type.SCANCODE;
            case MOUSE -> InputConstants.Type.MOUSE;
        };
    }

    /** {@link InputConstants.Type} to {@link KeyType}. */
    public static KeyType toType(InputConstants.Type type) {
        if (type == InputConstants.Type.MOUSE) {
            return KeyType.MOUSE;
        }
        if (type == InputConstants.Type.SCANCODE) {
            return KeyType.SCANCODE;
        }
        return KeyType.KEYSYM;
    }

    /**
     * Parses a vanilla key name such as {@code key.keyboard.c} or {@code key.mouse.left}; unknown names yield empty.
     */
    public static Optional<InputConstants.Key> fromVanillaName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(InputConstants.getKey(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Category id in the core's convention: vanilla {@code minecraft:movement} becomes
     * {@code key.categories.movement}, VANTA's {@code vanta:vanta} becomes {@code key.categories.vanta} and another
     * mod's {@code modid:thing} becomes {@code key.categories.modid.thing}.
     */
    public static String categoryId(Identifier id) {
        String namespace = id.getNamespace();
        String path = id.getPath();
        if (MINECRAFT.equals(namespace) || namespace.equals(path)) {
            return "key.categories." + path;
        }
        return "key.categories." + namespace + "." + path;
    }

    /** True for categories registered by the game itself. */
    public static boolean isVanillaCategory(Identifier id) {
        return MINECRAFT.equals(id.getNamespace());
    }
}
