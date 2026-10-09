package dev.vanta.core.hud;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable state of one widget instance in a layout. Every mutation returns a copy, which keeps undo/redo trivial.
 *
 * @param id                unique instance id within the layout ({@code fps}, {@code fps-2}, …)
 * @param type              widget type
 * @param enabled           false hides the widget without removing it
 * @param anchor            screen anchor
 * @param offsetX           anchor-space horizontal offset
 * @param offsetY           anchor-space vertical offset
 * @param scale             0.5–2.0
 * @param opacity           0–1
 * @param width             unscaled width in GUI pixels
 * @param height            unscaled height in GUI pixels
 * @param backgroundEnabled draw the panel behind the content
 * @param backgroundColor   ARGB
 * @param textColor         ARGB
 * @param accentColor       ARGB (labels, bars, highlights)
 * @param props             widget-specific properties (validated against {@link HudWidgetType#settingsSchema()})
 */
public record HudWidgetState(String id, HudWidgetType type, boolean enabled, HudAnchor anchor, int offsetX, int offsetY,
                             double scale, double opacity, int width, int height, boolean backgroundEnabled,
                             int backgroundColor, int textColor, int accentColor, Map<String, String> props) {
    /** Smallest scale. */
    public static final double MIN_SCALE = 0.5;
    /** Largest scale. */
    public static final double MAX_SCALE = 2.0;
    /** Smallest width/height a widget may be resized to. */
    public static final int MIN_SIZE = 8;
    /**
     * Smallest width of the clock while it shows seconds: room for the widest AM/PM time, {@code 12:59:59 PM}, so a
     * layout sized for the former 24-hour clock ({@code 56}) never cuts the time off.
     */
    public static final int CLOCK_SECONDS_MIN_WIDTH = 68;
    /** Default panel colour: {@code bg.base} at 70 %. */
    public static final int DEFAULT_BACKGROUND = 0xB30B0B10;
    /** Default text colour: {@code text.primary}. */
    public static final int DEFAULT_TEXT = 0xFFF5F5F7;
    /** Default accent colour: {@code accent.violet}. */
    public static final int DEFAULT_ACCENT = 0xFF7C5CFF;

    public HudWidgetState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(anchor, "anchor");
        scale = clamp(scale, MIN_SCALE, MAX_SCALE);
        opacity = clamp(opacity, 0.0, 1.0);
        width = Math.max(MIN_SIZE, width);
        height = Math.max(MIN_SIZE, height);
        Map<String, String> validated = new LinkedHashMap<>();
        for (HudProp prop : type.settingsSchema()) {
            String raw = props == null ? null : props.get(prop.key());
            validated.put(prop.key(), prop.normalize(raw).orElse(prop.defaultValue()));
        }
        props = Map.copyOf(validated);
        if (type == HudWidgetType.CLOCK && "true".equals(props.get("showSeconds"))) {
            width = Math.max(width, CLOCK_SECONDS_MIN_WIDTH);
        }
        if (type.alwaysCentered()) {
            anchor = HudAnchor.CENTER;
            offsetX = 0;
            offsetY = 0;
        }
    }

    /** A widget with the type's defaults at the given id and offsets. */
    public static HudWidgetState defaults(String id, HudWidgetType type, HudAnchor anchor, int offsetX, int offsetY) {
        return new HudWidgetState(id, type, true, anchor, offsetX, offsetY, 1.0, 1.0, type.defaultWidth(),
                type.defaultHeight(), true, DEFAULT_BACKGROUND, DEFAULT_TEXT, DEFAULT_ACCENT, Map.of());
    }

    /** A widget with the type's defaults at its default anchor, 4px from the corner. */
    public static HudWidgetState defaults(String id, HudWidgetType type) {
        return defaults(id, type, type.defaultAnchor(), 4, 4);
    }

    /** Width after {@link #scale()}. */
    public int scaledWidth() {
        return Math.max(1, (int) Math.round(width * scale));
    }

    /** Height after {@link #scale()}. */
    public int scaledHeight() {
        return Math.max(1, (int) Math.round(height * scale));
    }

    /** Property value (validated default when unset). */
    public String prop(String key) {
        String value = props.get(key);
        if (value != null) {
            return value;
        }
        return type.prop(key).map(HudProp::defaultValue).orElse("");
    }

    /** Boolean property. */
    public boolean propBool(String key) {
        Optional<HudProp> schema = type.prop(key);
        return schema.isPresent() && schema.get().asBoolean(props.get(key));
    }

    /** Integer property. */
    public int propInt(String key) {
        Optional<HudProp> schema = type.prop(key);
        return schema.map(p -> p.asInt(props.get(key))).orElse(0);
    }

    public HudWidgetState withEnabled(boolean value) {
        return new HudWidgetState(id, type, value, anchor, offsetX, offsetY, scale, opacity, width, height,
                backgroundEnabled, backgroundColor, textColor, accentColor, props);
    }

    public HudWidgetState withPosition(HudAnchor newAnchor, int newOffsetX, int newOffsetY) {
        return new HudWidgetState(id, type, enabled, newAnchor, newOffsetX, newOffsetY, scale, opacity, width, height,
                backgroundEnabled, backgroundColor, textColor, accentColor, props);
    }

    public HudWidgetState withScale(double value) {
        return new HudWidgetState(id, type, enabled, anchor, offsetX, offsetY, value, opacity, width, height,
                backgroundEnabled, backgroundColor, textColor, accentColor, props);
    }

    public HudWidgetState withOpacity(double value) {
        return new HudWidgetState(id, type, enabled, anchor, offsetX, offsetY, scale, value, width, height,
                backgroundEnabled, backgroundColor, textColor, accentColor, props);
    }

    public HudWidgetState withSize(int newWidth, int newHeight) {
        return new HudWidgetState(id, type, enabled, anchor, offsetX, offsetY, scale, opacity, newWidth, newHeight,
                backgroundEnabled, backgroundColor, textColor, accentColor, props);
    }

    public HudWidgetState withBackground(boolean enabledBg, int color) {
        return new HudWidgetState(id, type, enabled, anchor, offsetX, offsetY, scale, opacity, width, height,
                enabledBg, color, textColor, accentColor, props);
    }

    public HudWidgetState withTextColor(int color) {
        return new HudWidgetState(id, type, enabled, anchor, offsetX, offsetY, scale, opacity, width, height,
                backgroundEnabled, backgroundColor, color, accentColor, props);
    }

    public HudWidgetState withAccentColor(int color) {
        return new HudWidgetState(id, type, enabled, anchor, offsetX, offsetY, scale, opacity, width, height,
                backgroundEnabled, backgroundColor, textColor, color, props);
    }

    public HudWidgetState withProp(String key, String value) {
        Map<String, String> next = new LinkedHashMap<>(props);
        next.put(key, value);
        return new HudWidgetState(id, type, enabled, anchor, offsetX, offsetY, scale, opacity, width, height,
                backgroundEnabled, backgroundColor, textColor, accentColor, next);
    }

    public HudWidgetState withId(String newId) {
        return new HudWidgetState(newId, type, enabled, anchor, offsetX, offsetY, scale, opacity, width, height,
                backgroundEnabled, backgroundColor, textColor, accentColor, props);
    }

    /** Copy with every visual attribute reset to the type default; id, enabled state and position are kept. */
    public HudWidgetState resetVisuals() {
        return new HudWidgetState(id, type, enabled, anchor, offsetX, offsetY, 1.0, 1.0, type.defaultWidth(),
                type.defaultHeight(), true, DEFAULT_BACKGROUND, DEFAULT_TEXT, DEFAULT_ACCENT, Map.of());
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }
}
