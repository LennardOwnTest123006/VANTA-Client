package dev.vanta.client.render;

import dev.vanta.core.ui.FontKind;
import java.util.EnumMap;
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
 */
public final class VantaFonts {
    private static final Map<FontKind, Style> STYLES = new EnumMap<>(FontKind.class);

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

    /** Text wrapped in the style that selects the provider of {@code kind}. */
    public static MutableComponent styled(String text, FontKind kind) {
        Style style = STYLES.get(kind);
        MutableComponent component = Component.literal(text);
        return style == null ? component : component.withStyle(style);
    }

    /** Line advance used by both canvases. */
    public static int lineHeight(FontKind kind) {
        return kind.lineHeight();
    }
}
