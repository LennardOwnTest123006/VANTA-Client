package dev.vanta.core.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Built-in HUD presets loaded from {@code /vanta/presets/hud/<id>.json} on the classpath.
 */
public final class HudPresets {
    /** Id of the default preset. */
    public static final String DEFAULT = "default";
    public static final String MINIMAL = "minimal";
    public static final String PVP = "pvp";
    public static final String STREAMER = "streamer";
    public static final String PERFORMANCE = "performance";

    /** Built-in ids in display order. */
    public static final List<String> IDS = List.of(DEFAULT, MINIMAL, PVP, STREAMER, PERFORMANCE);

    private static final String RESOURCE_DIR = "/vanta/presets/hud/";
    private static volatile List<HudPreset> cache;

    private HudPresets() {
    }

    /** Every built-in preset. */
    public static List<HudPreset> builtIns() {
        List<HudPreset> presets = cache;
        if (presets == null) {
            List<HudPreset> loaded = new ArrayList<>();
            for (String id : IDS) {
                loaded.add(load(id));
            }
            presets = Collections.unmodifiableList(loaded);
            cache = presets;
        }
        return presets;
    }

    /** The default preset. */
    public static HudPreset defaultPreset() {
        return find(DEFAULT).orElseThrow();
    }

    /** Finds a built-in preset by id. */
    public static Optional<HudPreset> find(String id) {
        for (HudPreset preset : builtIns()) {
            if (preset.id().equalsIgnoreCase(id)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    /** Loads one preset resource. */
    static HudPreset load(String id) {
        String resource = RESOURCE_DIR + id + ".json";
        InputStream in = HudPresets.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing HUD preset resource " + resource);
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            HudLayout layout = HudLayoutCodec.read(json);
            String name = json.has("name") ? json.get("name").getAsString() : "vanta.hud.preset." + id;
            return new HudPreset(id, name, layout, true);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read HUD preset " + resource, e);
        }
    }
}
