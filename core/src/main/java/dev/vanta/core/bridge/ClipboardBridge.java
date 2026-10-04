package dev.vanta.core.bridge;

/**
 * System clipboard access (implemented by the client with GLFW through {@code KeyboardHandler}).
 */
public interface ClipboardBridge {
    /** Current clipboard text, empty string when unavailable. */
    String get();

    /** Replaces the clipboard text. */
    void set(String text);
}
