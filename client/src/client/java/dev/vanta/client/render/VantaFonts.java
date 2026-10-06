package dev.vanta.client.render;

import dev.vanta.core.ui.FontKind;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Maps the core's {@link FontKind}s to Minecraft font providers.
 * <p>
 * The VANTA fonts are TTF providers shipped in {@code assets/vanta/font/*.json} ({@code vanta:ui} Inter Medium 10px,
 * {@code vanta:ui_bold} Inter Bold 10px, {@code vanta:display} Space Grotesk Bold 14px). A string is drawn with one of
 * them by wrapping it in a {@link Component} whose {@link Style} carries the matching {@link FontDescription}; the
 * vanilla font ({@link FontKind#MINECRAFT}) is drawn as a plain string.
 * <p>
 * Line heights: Minecraft's {@code Font.lineHeight} is always 9 regardless of the provider, so the layout line advance
 * comes from {@link FontKind#lineHeight()} (9 for the Inter fonts and the vanilla font, 14 for the display font) and is
 * shared with the Java2D preview canvas, which keeps in-game and preview layouts identical.
 * <p>
 * The styled components are memoised in a bounded LRU keyed by font kind and string, because every text draw and
 * width measurement would otherwise allocate one. A component is never mutated after creation, so callers share the
 * cached instance; the cache is used from the render thread only and cleared by {@link #clearCache()} on resource
 * reloads.
 */
public final class VantaFonts {
    /** Upper bound of memoised components; the least recently used string is evicted first. */
    public static final int CACHE_SIZE = 4096;

    private record Key(FontKind kind, String text) {
    }

    private static final Map<FontKind, Style> STYLES = new EnumMap<>(FontKind.class);
    private static final Map<Key, MutableComponent> COMPONENTS =
            new LinkedHashMap<>(CACHE_SIZE * 4 / 3, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, MutableComponent> eldest) {
                    return size() > CACHE_SIZE;
                }
            };

    static {
        for (FontKind kind : FontKind.values()) {
            if (kind != FontKind.MINECRAFT) {
                Identifier id = Identifier.parse(kind.fontId());
                STYLES.put(kind, Style.EMPTY.withFont(new FontDescription.Resource(id)));
            }
        }
    }

    private VantaFonts() {
    }

    /** True when the kind is rendered with a VANTA TTF provider rather than the vanilla font. */
    public static boolean isCustom(FontKind kind) {
        return kind != FontKind.MINECRAFT;
    }

    /**
     * Text wrapped in the style that selects the provider of {@code kind}. The returned component is shared through
     * the cache and must not be mutated.
     */
    public static MutableComponent styled(String text, FontKind kind) {
        Key key = new Key(kind, text);
        MutableComponent cached = COMPONENTS.get(key);
        if (cached != null) {
            return cached;
        }
        Style style = STYLES.get(kind);
        MutableComponent component = Component.literal(text);
        if (style != null) {
            component = component.withStyle(style);
        }
        COMPONENTS.put(key, component);
        return component;
    }

    /** Forgets every memoised component (resource reload). */
    public static void clearCache() {
        COMPONENTS.clear();
    }

    /** Number of memoised components (diagnostics). */
    public static int cacheSize() {
        return COMPONENTS.size();
    }

    /** Line advance used by both canvases. */
    public static int lineHeight(FontKind kind) {
        return kind.lineHeight();
    }
}
