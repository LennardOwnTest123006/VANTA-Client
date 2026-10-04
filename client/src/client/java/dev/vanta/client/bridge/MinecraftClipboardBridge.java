package dev.vanta.client.bridge;

import dev.vanta.core.bridge.ClipboardBridge;
import net.minecraft.client.Minecraft;

/**
 * {@link ClipboardBridge} over the game's GLFW clipboard access ({@code KeyboardHandler}).
 */
public final class MinecraftClipboardBridge implements ClipboardBridge {
    @Override
    public String get() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.keyboardHandler == null) {
            return "";
        }
        String text = minecraft.keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }

    @Override
    public void set(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.keyboardHandler != null) {
            minecraft.keyboardHandler.setClipboard(text == null ? "" : text);
        }
    }
}
