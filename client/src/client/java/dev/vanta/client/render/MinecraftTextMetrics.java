package dev.vanta.client.render;

import dev.vanta.client.perf.FrameProbe;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.TextMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * {@link TextMetrics} backed by the game's {@link Font}. Used during layout, before a {@code GuiGraphics} exists;
 * {@link GuiGraphicsCanvas} measures the same way so layout and drawing agree.
 */
public final class MinecraftTextMetrics implements TextMetrics {
    /** Shared instance (the game font is a singleton). */
    public static final MinecraftTextMetrics INSTANCE = new MinecraftTextMetrics();

    private MinecraftTextMetrics() {
    }

    /** Width of {@code text} drawn with {@code kind}, in GUI pixels. */
    public static int measure(Font font, String text, FontKind kind) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        FrameProbe.countTextWidth();
        if (VantaFonts.isCustom(kind)) {
            return font.width(VantaFonts.styled(text, kind));
        }
        return font.width(text);
    }

    @Override
    public int textWidth(String text, FontKind font) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            return text == null ? 0 : text.length() * 6;
        }
        return measure(minecraft.font, text, font == null ? FontKind.UI : font);
    }

    @Override
    public int lineHeight(FontKind font) {
        return VantaFonts.lineHeight(font == null ? FontKind.UI : font);
    }
}
