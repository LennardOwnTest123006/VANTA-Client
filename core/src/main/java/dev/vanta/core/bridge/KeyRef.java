package dev.vanta.core.bridge;

import java.util.Objects;

/**
 * A concrete key or mouse button, independent of Minecraft classes.
 *
 * @param type input source
 * @param code GLFW key code, scancode or mouse button; {@code -1} when unbound
 * @param name vanilla key name such as {@code key.keyboard.right.shift} or {@code key.mouse.left}
 */
public record KeyRef(KeyType type, int code, String name) {
    /** Vanilla name of the unbound key. */
    public static final String UNKNOWN_NAME = "key.keyboard.unknown";
    /** The "not bound" reference. */
    public static final KeyRef UNBOUND = new KeyRef(KeyType.KEYSYM, -1, UNKNOWN_NAME);

    public KeyRef {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
    }

    /** Keyboard key by GLFW code with the vanilla naming convention. */
    public static KeyRef keyboard(int glfwCode, String name) {
        return new KeyRef(KeyType.KEYSYM, glfwCode, name);
    }

    /** Mouse button by index with the vanilla naming convention. */
    public static KeyRef mouse(int button) {
        String name = switch (button) {
            case 0 -> "key.mouse.left";
            case 1 -> "key.mouse.right";
            case 2 -> "key.mouse.middle";
            default -> "key.mouse." + (button + 1);
        };
        return new KeyRef(KeyType.MOUSE, button, name);
    }

    /** True when nothing is bound. */
    public boolean isUnbound() {
        return code < 0 || UNKNOWN_NAME.equals(name);
    }

    /** Two references collide when they are the same physical input (type + code). */
    public boolean sameInput(KeyRef other) {
        return other != null && !isUnbound() && !other.isUnbound() && type == other.type && code == other.code;
    }

    /** Stable string form used in JSON: {@code keysym:344}, {@code mouse:0}. */
    public String serialize() {
        return type.name().toLowerCase(java.util.Locale.ROOT) + ":" + code + ":" + name;
    }

    /** Parses {@link #serialize()} output; malformed input yields {@link #UNBOUND}. */
    public static KeyRef parse(String text) {
        if (text == null) {
            return UNBOUND;
        }
        String[] parts = text.split(":", 3);
        if (parts.length < 2) {
            return UNBOUND;
        }
        try {
            KeyType type = KeyType.valueOf(parts[0].toUpperCase(java.util.Locale.ROOT));
            int code = Integer.parseInt(parts[1]);
            String name = parts.length == 3 && !parts[2].isBlank() ? parts[2] : UNKNOWN_NAME;
            return new KeyRef(type, code, name);
        } catch (IllegalArgumentException e) {
            return UNBOUND;
        }
    }
}
