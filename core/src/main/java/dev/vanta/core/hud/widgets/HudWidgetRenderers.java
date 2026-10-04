package dev.vanta.core.hud.widgets;

import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.hud.render.HudWidgetRenderer;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry mapping every {@link HudWidgetType} to its {@link HudWidgetRenderer}. {@link #standard()} contains the
 * built-in renderer for each type; a unit test guarantees the mapping is complete.
 */
public final class HudWidgetRenderers {

    private final Map<HudWidgetType, HudWidgetRenderer> byType = new EnumMap<>(HudWidgetType.class);

    private HudWidgetRenderers(List<HudWidgetRenderer> renderers) {
        for (HudWidgetRenderer renderer : renderers) {
            Objects.requireNonNull(renderer, "renderer");
            if (byType.putIfAbsent(renderer.type(), renderer) != null) {
                throw new IllegalArgumentException("Duplicate renderer for " + renderer.type());
            }
        }
    }

    /** The built-in renderers, one per widget type. */
    public static HudWidgetRenderers standard() {
        return new HudWidgetRenderers(List.of(
                new FpsWidget(), new PingWidget(), new CoordinatesWidget(), new DirectionWidget(), new BiomeWidget(),
                new ServerWidget(), new CpsWidget(), new ClockWidget(), new ArmorWidget(),
                new ItemDurabilityWidget(), new PotionEffectsWidget(), new KeystrokesWidget(), new MemoryWidget(),
                new CpuWidget(), new EntityCountWidget(), new MinecraftVersionWidget(), new CrosshairWidget()));
    }

    /**
     * The renderer for a type.
     *
     * @throws IllegalArgumentException when no renderer is registered for the type
     */
    public HudWidgetRenderer forType(HudWidgetType type) {
        HudWidgetRenderer renderer = byType.get(Objects.requireNonNull(type, "type"));
        if (renderer == null) {
            throw new IllegalArgumentException("No HUD widget renderer for " + type);
        }
        return renderer;
    }

    /** True when the type has a renderer. */
    public boolean supports(HudWidgetType type) {
        return byType.containsKey(type);
    }

    /** All renderers in type order. */
    public Collection<HudWidgetRenderer> all() {
        return Collections.unmodifiableCollection(byType.values());
    }
}
