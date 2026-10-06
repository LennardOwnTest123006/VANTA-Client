package dev.vanta.client.render;

import dev.vanta.client.perf.FrameProbe;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.TextMetrics;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * {@link TextMetrics} backed by the game's {@link Font}. Used during layout, before a {@code GuiGraphics} exists;
 * {@link GuiGraphicsCanvas} measures the same way so layout and drawing agree.
 * <p>
 * Widths are memoised in a bounded LRU keyed by font kind and string: a frame measures the same labels over and
 * over, and every uncached measurement of a VANTA font allocates a styled {@code Component} and walks its glyphs.
 * The cache is used from the render thread only. It is cleared by {@link #clearCache()} when the game's resources
 * reload (a resource pack may change the glyph advances) and whenever another {@link Font} instance is measured.
 */
public final class MinecraftTextMetrics implements TextMetrics {
    /** Shared instance (the game font is a singleton). */
    public static final MinecraftTextMetrics INSTANCE = new MinecraftTextMetrics();

    /** Upper bound of memoised widths; the least recently measured string is evicted first. */
    public static final int CACHE_SIZE = 4096;

    private record Key(FontKind kind, String text) {
    }

    private static final Map<Key, Integer> WIDTHS = new LinkedHashMap<>(CACHE_SIZE * 4 / 3, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Integer> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private static Font cachedFont;

    private MinecraftTextMetrics() {
    }

    /** Width of {@code text} drawn with {@code kind}, in GUI pixels. */
    public static int measure(Font font, String text, FontKind kind) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        FrameProbe.countTextWidth();
        if (font != cachedFont) {
            WIDTHS.clear();
            cachedFont = font;
        }
        Key key = new Key(kind, text);
        Integer cached = WIDTHS.get(key);
        if (cached != null) {
            return cached;
        }
        int width = VantaFonts.isCustom(kind) ? font.width(VantaFonts.styled(text, kind)) : font.width(text);
        WIDTHS.put(key, width);
        return width;
    }

    /** Forgets every memoised width (resource reload). */
    public static void clearCache() {
        WIDTHS.clear();
    }

    /** Number of memoised widths (diagnostics). */
    public static int cacheSize() {
        return WIDTHS.size();
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
