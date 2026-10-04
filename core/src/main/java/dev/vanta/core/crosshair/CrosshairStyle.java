package dev.vanta.core.crosshair;

import com.google.gson.JsonObject;
import dev.vanta.core.settings.Setting;
import java.util.Objects;

/**
 * Complete description of a crosshair. Values are clamped on construction so any JSON produces a drawable style.
 *
 * @param shape             base shape
 * @param size              arm length / radius / dot size in GUI pixels (1–32)
 * @param thickness         line thickness (1–8)
 * @param gap               distance from the centre to the arms (0–16)
 * @param outline           draw a contrasting outline
 * @param outlineThickness  outline width (1–3)
 * @param opacity           0–1 multiplier applied to both colours
 * @param color             ARGB fill colour
 * @param outlineColor      ARGB outline colour
 * @param dynamic           expand the gap while moving or attacking (visual only, like vanilla's attack indicator)
 * @param hideOnThirdPerson hide the crosshair in third-person view
 */
public record CrosshairStyle(CrosshairShape shape, int size, int thickness, int gap, boolean outline,
                             int outlineThickness, double opacity, int color, int outlineColor, boolean dynamic,
                             boolean hideOnThirdPerson) {
    public static final int MAX_SIZE = 32;
    public static final int MAX_THICKNESS = 8;
    public static final int MAX_GAP = 16;
    public static final int MAX_OUTLINE = 3;

    /** The vanilla-like default: white 5 px cross, 1 px thick, 1 px gap, black outline. */
    public static final CrosshairStyle DEFAULT = new CrosshairStyle(CrosshairShape.CROSS, 5, 1, 1, true, 1, 1.0,
            0xFFFFFFFF, 0xFF000000, false, false);

    public CrosshairStyle {
        Objects.requireNonNull(shape, "shape");
        size = clamp(size, 1, MAX_SIZE);
        thickness = clamp(thickness, 1, MAX_THICKNESS);
        gap = clamp(gap, 0, MAX_GAP);
        outlineThickness = clamp(outlineThickness, 1, MAX_OUTLINE);
        opacity = Double.isNaN(opacity) ? 1.0 : Math.max(0.0, Math.min(1.0, opacity));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public CrosshairStyle withShape(CrosshairShape v) {
        return new CrosshairStyle(v, size, thickness, gap, outline, outlineThickness, opacity, color, outlineColor,
                dynamic, hideOnThirdPerson);
    }

    public CrosshairStyle withSize(int v) {
        return new CrosshairStyle(shape, v, thickness, gap, outline, outlineThickness, opacity, color, outlineColor,
                dynamic, hideOnThirdPerson);
    }

    public CrosshairStyle withThickness(int v) {
        return new CrosshairStyle(shape, size, v, gap, outline, outlineThickness, opacity, color, outlineColor,
                dynamic, hideOnThirdPerson);
    }

    public CrosshairStyle withGap(int v) {
        return new CrosshairStyle(shape, size, thickness, v, outline, outlineThickness, opacity, color, outlineColor,
                dynamic, hideOnThirdPerson);
    }

    public CrosshairStyle withOutline(boolean enabled, int thicknessPx) {
        return new CrosshairStyle(shape, size, thickness, gap, enabled, thicknessPx, opacity, color, outlineColor,
                dynamic, hideOnThirdPerson);
    }

    public CrosshairStyle withOpacity(double v) {
        return new CrosshairStyle(shape, size, thickness, gap, outline, outlineThickness, v, color, outlineColor,
                dynamic, hideOnThirdPerson);
    }

    public CrosshairStyle withColor(int argb) {
        return new CrosshairStyle(shape, size, thickness, gap, outline, outlineThickness, opacity, argb, outlineColor,
                dynamic, hideOnThirdPerson);
    }

    public CrosshairStyle withOutlineColor(int argb) {
        return new CrosshairStyle(shape, size, thickness, gap, outline, outlineThickness, opacity, color, argb,
                dynamic, hideOnThirdPerson);
    }

    public CrosshairStyle withDynamic(boolean v) {
        return new CrosshairStyle(shape, size, thickness, gap, outline, outlineThickness, opacity, color, outlineColor,
                v, hideOnThirdPerson);
    }

    public CrosshairStyle withHideOnThirdPerson(boolean v) {
        return new CrosshairStyle(shape, size, thickness, gap, outline, outlineThickness, opacity, color, outlineColor,
                dynamic, v);
    }

    /** Serialises to JSON. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("shape", shape.id());
        o.addProperty("size", size);
        o.addProperty("thickness", thickness);
        o.addProperty("gap", gap);
        o.addProperty("outline", outline);
        o.addProperty("outlineThickness", outlineThickness);
        o.addProperty("opacity", opacity);
        o.addProperty("color", Setting.formatColor(color));
        o.addProperty("outlineColor", Setting.formatColor(outlineColor));
        o.addProperty("dynamic", dynamic);
        o.addProperty("hideOnThirdPerson", hideOnThirdPerson);
        return o;
    }

    /** Tolerant JSON read; missing or invalid fields fall back to {@link #DEFAULT}. */
    public static CrosshairStyle fromJson(JsonObject o) {
        CrosshairStyle d = DEFAULT;
        if (o == null) {
            return d;
        }
        return new CrosshairStyle(
                CrosshairShape.fromId(str(o, "shape")).orElse(d.shape),
                num(o, "size", d.size),
                num(o, "thickness", d.thickness),
                num(o, "gap", d.gap),
                bool(o, "outline", d.outline),
                num(o, "outlineThickness", d.outlineThickness),
                dbl(o, "opacity", d.opacity),
                color(o, "color", d.color),
                color(o, "outlineColor", d.outlineColor),
                bool(o, "dynamic", d.dynamic),
                bool(o, "hideOnThirdPerson", d.hideOnThirdPerson));
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static int num(JsonObject o, String key, int fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber()
                ? o.get(key).getAsInt() : fallback;
    }

    private static double dbl(JsonObject o, String key, double fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber()
                ? o.get(key).getAsDouble() : fallback;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean()
                ? o.get(key).getAsBoolean() : fallback;
    }

    private static int color(JsonObject o, String key, int fallback) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            return fallback;
        }
        if (o.get(key).getAsJsonPrimitive().isNumber()) {
            return o.get(key).getAsInt();
        }
        return Setting.parseColor(o.get(key).getAsString()).orElse(fallback);
    }
}
