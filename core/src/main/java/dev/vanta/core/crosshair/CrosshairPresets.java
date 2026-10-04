package dev.vanta.core.crosshair;

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
 * Built-in crosshair presets loaded from {@code /vanta/presets/crosshair/<id>.json}.
 */
public final class CrosshairPresets {
    public static final String DEFAULT = "default";
    public static final String DOT = "dot";
    public static final String THIN = "thin";
    public static final String BOLD = "bold";
    public static final String CIRCLE = "circle";
    public static final String PRECISION = "precision";

    /** Built-in ids in display order. */
    public static final List<String> IDS = List.of(DEFAULT, DOT, THIN, BOLD, CIRCLE, PRECISION);

    private static final String RESOURCE_DIR = "/vanta/presets/crosshair/";
    private static volatile List<CrosshairPreset> cache;

    private CrosshairPresets() {
    }

    /** Every built-in preset. */
    public static List<CrosshairPreset> builtIns() {
        List<CrosshairPreset> presets = cache;
        if (presets == null) {
            List<CrosshairPreset> loaded = new ArrayList<>();
            for (String id : IDS) {
                loaded.add(load(id));
            }
            presets = Collections.unmodifiableList(loaded);
            cache = presets;
        }
        return presets;
    }

    /** Finds a preset by id. */
    public static Optional<CrosshairPreset> find(String id) {
        for (CrosshairPreset preset : builtIns()) {
            if (preset.id().equalsIgnoreCase(id)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    /** The preset whose style equals {@code style}, if any. */
    public static Optional<CrosshairPreset> matching(CrosshairStyle style) {
        for (CrosshairPreset preset : builtIns()) {
            if (preset.style().equals(style)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    static CrosshairPreset load(String id) {
        String resource = RESOURCE_DIR + id + ".json";
        InputStream in = CrosshairPresets.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing crosshair preset resource " + resource);
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return new CrosshairPreset(id, CrosshairStyle.fromJson(json));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read crosshair preset " + resource, e);
        }
    }
}
