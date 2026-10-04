package dev.vanta.core.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.settings.Setting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JSON (de)serialisation of {@link HudLayout}. Reading is tolerant: unknown widget types, duplicate ids and invalid
 * values are skipped or defaulted, never fatal.
 * <pre>
 * {"widgets": [{"id": "fps", "type": "fps", "enabled": true, "anchor": "top_left", "offsetX": 4, "offsetY": 4,
 *   "scale": 1.0, "opacity": 1.0, "width": 44, "height": 14, "background": true,
 *   "backgroundColor": "#B30B0B10", "textColor": "#FFF5F5F7", "accentColor": "#FF7C5CFF", "props": {}}]}
 * </pre>
 */
public final class HudLayoutCodec {
    /** Maximum widgets accepted from a file. */
    public static final int MAX_WIDGETS = 64;

    private HudLayoutCodec() {
    }

    /** Serialises the widget list into {@code target} under {@code "widgets"}. */
    public static JsonObject write(HudLayout layout, JsonObject target) {
        JsonArray array = new JsonArray();
        for (HudWidgetState widget : layout.widgets()) {
            array.add(writeWidget(widget));
        }
        target.add("widgets", array);
        return target;
    }

    /** Serialises one widget. */
    public static JsonObject writeWidget(HudWidgetState w) {
        JsonObject o = new JsonObject();
        o.addProperty("id", w.id());
        o.addProperty("type", w.type().id());
        o.addProperty("enabled", w.enabled());
        o.addProperty("anchor", w.anchor().id());
        o.addProperty("offsetX", w.offsetX());
        o.addProperty("offsetY", w.offsetY());
        o.addProperty("scale", w.scale());
        o.addProperty("opacity", w.opacity());
        o.addProperty("width", w.width());
        o.addProperty("height", w.height());
        o.addProperty("background", w.backgroundEnabled());
        o.addProperty("backgroundColor", Setting.formatColor(w.backgroundColor()));
        o.addProperty("textColor", Setting.formatColor(w.textColor()));
        o.addProperty("accentColor", Setting.formatColor(w.accentColor()));
        JsonObject props = new JsonObject();
        for (Map.Entry<String, String> entry : w.props().entrySet()) {
            props.addProperty(entry.getKey(), entry.getValue());
        }
        o.add("props", props);
        return o;
    }

    /** Reads the {@code "widgets"} array of {@code json}; missing or malformed → empty layout. */
    public static HudLayout read(JsonObject json) {
        JsonElement array = json.get("widgets");
        if (array == null || !array.isJsonArray()) {
            return HudLayout.EMPTY;
        }
        List<HudWidgetState> widgets = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (JsonElement element : array.getAsJsonArray()) {
            if (widgets.size() >= MAX_WIDGETS) {
                CoreLog.warn("HUD layout has more than {} widgets; ignoring the rest", MAX_WIDGETS);
                break;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            Optional<HudWidgetState> widget = readWidget(element.getAsJsonObject());
            if (widget.isEmpty()) {
                continue;
            }
            String id = widget.get().id();
            if (ids.contains(id)) {
                CoreLog.warn("Duplicate HUD widget id {} skipped", id);
                continue;
            }
            ids.add(id);
            widgets.add(widget.get());
        }
        return new HudLayout(widgets);
    }

    /** Reads one widget; empty when the type is unknown. */
    public static Optional<HudWidgetState> readWidget(JsonObject o) {
        Optional<HudWidgetType> type = HudWidgetType.fromId(string(o, "type", ""));
        if (type.isEmpty()) {
            CoreLog.warn("Unknown HUD widget type {} skipped", string(o, "type", "?"));
            return Optional.empty();
        }
        String id = string(o, "id", type.get().id());
        if (id.isBlank() || id.length() > 64) {
            id = type.get().id();
        }
        HudAnchor anchor = HudAnchor.fromId(string(o, "anchor", "")).orElse(type.get().defaultAnchor());
        Map<String, String> props = new LinkedHashMap<>();
        JsonElement propsElement = o.get("props");
        if (propsElement != null && propsElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : propsElement.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    props.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
        HudWidgetState state = new HudWidgetState(id, type.get(), bool(o, "enabled", true), anchor,
                integer(o, "offsetX", 4), integer(o, "offsetY", 4), number(o, "scale", 1.0),
                number(o, "opacity", 1.0), integer(o, "width", type.get().defaultWidth()),
                integer(o, "height", type.get().defaultHeight()), bool(o, "background", true),
                color(o, "backgroundColor", HudWidgetState.DEFAULT_BACKGROUND),
                color(o, "textColor", HudWidgetState.DEFAULT_TEXT),
                color(o, "accentColor", HudWidgetState.DEFAULT_ACCENT), props);
        return Optional.of(state);
    }

    static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }

    static int integer(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return e.getAsInt();
        }
        return fallback;
    }

    static double number(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            double v = e.getAsDouble();
            return Double.isFinite(v) ? v : fallback;
        }
        return fallback;
    }

    static int color(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        if (e.getAsJsonPrimitive().isNumber()) {
            return e.getAsInt();
        }
        return Setting.parseColor(e.getAsString()).orElse(fallback);
    }
}
