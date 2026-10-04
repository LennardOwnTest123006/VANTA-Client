package dev.vanta.core.hud;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Objects;

/**
 * A named layout. Built-in presets have a translation key as name; user presets store a literal name.
 *
 * @param id      stable id (file name for user presets)
 * @param name    translation key for built-ins, literal name for user presets
 * @param layout  the layout
 * @param builtIn true for presets shipped with VANTA
 */
public record HudPreset(String id, String name, HudLayout layout, boolean builtIn) implements LangKeyed {
    public HudPreset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(layout, "layout");
    }

    /** Translation key (built-ins) or the literal name (user presets, which {@code Lang.tr} returns unchanged). */
    @Override
    public String langKey() {
        return name;
    }

    /** Translation key of the description (built-ins only). */
    public String descriptionKey() {
        return name + ".description";
    }
}
