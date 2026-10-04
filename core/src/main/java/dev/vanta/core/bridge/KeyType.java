package dev.vanta.core.bridge;

/**
 * Input source of a key reference, mirroring {@code InputConstants.Type}.
 */
public enum KeyType {
    /** Keyboard key identified by its GLFW key code. */
    KEYSYM,
    /** Keyboard key identified by its platform scancode. */
    SCANCODE,
    /** Mouse button (0 = left, 1 = right, 2 = middle, …). */
    MOUSE
}
