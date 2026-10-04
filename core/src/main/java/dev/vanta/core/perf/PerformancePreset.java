package dev.vanta.core.perf;

import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.i18n.LangKeyed;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bundles of vanilla video options tuned for different hardware. A preset only writes options that exist in vanilla
 * Minecraft; it never touches rendering internals, so applying one is identical to moving the vanilla sliders.
 * <p>
 * Rationale per option (LOW / BALANCED / HIGH / ULTRA):
 * <ul>
 *   <li>Render distance 6/10/16/24 and simulation distance 6/8/12/16: chunk count grows quadratically, so these are
 *       the biggest levers. Simulation stays at or below render distance to avoid simulating invisible chunks.</li>
 *   <li>FPS limit 60/120/unlimited/unlimited: a cap keeps weak GPUs cool; strong machines may run free.</li>
 *   <li>Particles MINIMAL/DECREASED/ALL/ALL and clouds OFF/FAST/FANCY/FANCY: cheap wins on low-end hardware.</li>
 *   <li>Smooth lighting off only on LOW (it costs mesh-building time), entity shadows off on LOW.</li>
 *   <li>Entity distance 50 %/100 %/100 %/125 %: controls how far entities are rendered.</li>
 *   <li>VSync off in every preset so the FPS limit is the only cap; users who prefer vsync use the FPS-limit menu.</li>
 *   <li>Biome blend 0/2/4/6 and mipmaps 0/2/4/4: colour blending costs CPU at chunk build; mipmaps cost VRAM.</li>
 *   <li>Graphics FAST on LOW, FANCY otherwise. ULTRA deliberately stays FANCY instead of FABULOUS: fabulous adds
 *       extra translucency passes that cost a lot of GPU time for a small visual change, and it conflicts with some
 *       shader/OptiFine-style mods. Users can still select Fabulous manually.</li>
 * </ul>
 */
public enum PerformancePreset implements LangKeyed {
    LOW(6, 6, 60, "MINIMAL", "OFF", false, false, 0.5, 0, 0, "FAST"),
    BALANCED(10, 8, 120, "DECREASED", "FAST", true, true, 1.0, 2, 2, "FANCY"),
    HIGH(16, 12, FpsLimitPreset.VANILLA_UNLIMITED, "ALL", "FANCY", true, true, 1.0, 4, 4, "FANCY"),
    ULTRA(24, 16, FpsLimitPreset.VANILLA_UNLIMITED, "ALL", "FANCY", true, true, 1.25, 6, 4, "FANCY");

    private final Map<VanillaOption, Object> values;

    PerformancePreset(int renderDistance, int simulationDistance, int fpsLimit, String particles, String clouds,
                      boolean smoothLighting, boolean entityShadows, double entityDistance, int biomeBlend,
                      int mipmaps, String graphics) {
        Map<VanillaOption, Object> map = new EnumMap<>(VanillaOption.class);
        map.put(VanillaOption.RENDER_DISTANCE, renderDistance);
        map.put(VanillaOption.SIMULATION_DISTANCE, simulationDistance);
        map.put(VanillaOption.FRAMERATE_LIMIT, fpsLimit);
        map.put(VanillaOption.PARTICLES, particles);
        map.put(VanillaOption.CLOUDS, clouds);
        map.put(VanillaOption.SMOOTH_LIGHTING, smoothLighting);
        map.put(VanillaOption.ENTITY_SHADOWS, entityShadows);
        map.put(VanillaOption.ENTITY_DISTANCE_SCALING, entityDistance);
        map.put(VanillaOption.VSYNC, false);
        map.put(VanillaOption.BIOME_BLEND, biomeBlend);
        map.put(VanillaOption.MIPMAP_LEVELS, mipmaps);
        map.put(VanillaOption.GRAPHICS_MODE, graphics);
        this.values = Collections.unmodifiableMap(map);
    }

    /** Vanilla option values the preset applies. */
    public Map<VanillaOption, Object> optionValues() {
        return values;
    }

    /** Render distance in chunks. */
    public int renderDistance() {
        return (Integer) values.get(VanillaOption.RENDER_DISTANCE);
    }

    /** Simulation distance in chunks. */
    public int simulationDistance() {
        return (Integer) values.get(VanillaOption.SIMULATION_DISTANCE);
    }

    /** Lower-case id used in JSON. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.perf.preset." + id();
    }

    /** Translation key of the one-line description. */
    public String descriptionKey() {
        return "vanta.perf.preset." + id() + ".description";
    }

    /** Finds a preset by id (case-insensitive). */
    public static Optional<PerformancePreset> fromId(String id) {
        for (PerformancePreset preset : values()) {
            if (preset.id().equalsIgnoreCase(id)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }
}
