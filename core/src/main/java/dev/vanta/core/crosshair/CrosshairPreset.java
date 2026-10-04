package dev.vanta.core.crosshair;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Objects;

/**
 * A named crosshair style shipped with VANTA.
 *
 * @param id    stable id ({@code default}, {@code dot}, …)
 * @param style the style
 */
public record CrosshairPreset(String id, CrosshairStyle style) implements LangKeyed {
    public CrosshairPreset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(style, "style");
    }

    @Override
    public String langKey() {
        return "vanta.crosshair.preset." + id;
    }

    /** Translation key of the description. */
    public String descriptionKey() {
        return "vanta.crosshair.preset." + id + ".description";
    }
}
