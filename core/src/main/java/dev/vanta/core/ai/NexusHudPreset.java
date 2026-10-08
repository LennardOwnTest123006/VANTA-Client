package dev.vanta.core.ai;

import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.i18n.LangKeyed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The six HUD presets of Vanta Nexus ({@code hud.preset} action and the HUD Designer buttons), each a set of
 * {@link HudWidgetType}s plus a scale. {@link #apply(HudLayout)} keeps the player's existing widgets and positions:
 * widgets of the set are enabled (added at their default anchor when missing), every other widget is hidden, the
 * crosshair always stays.
 */
public enum NexusHudPreset implements LangKeyed {
    /** FPS and coordinates only. */
    MINIMAL(1.0, HudWidgetType.FPS, HudWidgetType.COORDINATES),
    /** Fight information: FPS, ping, CPS, keystrokes, armor, durability, effects. */
    PVP(1.0, HudWidgetType.FPS, HudWidgetType.PING, HudWidgetType.CPS, HudWidgetType.KEYSTROKES,
            HudWidgetType.ARMOR, HudWidgetType.ITEM_DURABILITY, HudWidgetType.POTION_EFFECTS),
    /** Viewer-friendly: FPS, clock, coordinates, direction, keystrokes, version; no server address. */
    RECORDING(0.9, HudWidgetType.FPS, HudWidgetType.CLOCK, HudWidgetType.COORDINATES, HudWidgetType.DIRECTION,
            HudWidgetType.KEYSTROKES, HudWidgetType.MINECRAFT_VERSION),
    /** Exploring and surviving: coordinates, direction, biome, clock, armor, durability, effects, FPS. */
    SURVIVAL(1.0, HudWidgetType.FPS, HudWidgetType.COORDINATES, HudWidgetType.DIRECTION, HudWidgetType.BIOME,
            HudWidgetType.CLOCK, HudWidgetType.ARMOR, HudWidgetType.ITEM_DURABILITY, HudWidgetType.POTION_EFFECTS),
    /** Building: coordinates, direction, biome and clock, slightly smaller. */
    BUILDING(0.9, HudWidgetType.COORDINATES, HudWidgetType.DIRECTION, HudWidgetType.BIOME, HudWidgetType.CLOCK),
    /** Every widget. */
    FULL(1.0, HudWidgetType.values());

    private final double scale;
    private final Set<HudWidgetType> widgets;

    NexusHudPreset(double scale, HudWidgetType... types) {
        this.scale = scale;
        EnumSet<HudWidgetType> set = EnumSet.noneOf(HudWidgetType.class);
        set.addAll(List.of(types));
        set.remove(HudWidgetType.CROSSHAIR);
        // Vanta Lab widgets are opt-in: the frame time graph is added by the player (or lab.set), never by a preset.
        set.remove(HudWidgetType.FRAMETIME_GRAPH);
        this.widgets = Collections.unmodifiableSet(set);
    }

    /** Lower-case id used by the {@code hud.preset} action and in lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.nexus.hud_preset." + id();
    }

    /** Translation key of the one-line description. */
    public String descriptionKey() {
        return langKey() + ".description";
    }

    /** Widget types shown by this preset (the crosshair is implied). */
    public Set<HudWidgetType> widgets() {
        return widgets;
    }

    /** Scale applied to the preset's widgets. */
    public double scale() {
        return scale;
    }

    /**
     * The layout after applying this preset: widgets of the set enabled with the preset scale (added when missing),
     * other widgets disabled, the crosshair kept.
     */
    public HudLayout apply(HudLayout current) {
        HudLayout result = current;
        for (HudWidgetType type : widgets) {
            List<HudWidgetState> existing = result.byType(type);
            if (existing.isEmpty()) {
                HudWidgetState added = HudWidgetState.defaults(result.nextId(type), type)
                        .withScale(type.supportsScale() ? scale : 1.0);
                result = result.with(result.placedClear(added, NexusActions.REFERENCE_WIDTH,
                        NexusActions.REFERENCE_HEIGHT));
            } else {
                for (HudWidgetState widget : existing) {
                    result = result.with(widget.withEnabled(true)
                            .withScale(type.supportsScale() ? scale : widget.scale()));
                }
            }
        }
        for (HudWidgetState widget : result.widgets()) {
            if (widget.type() == HudWidgetType.CROSSHAIR) {
                if (!widget.enabled()) {
                    result = result.with(widget.withEnabled(true));
                }
            } else if (!widgets.contains(widget.type()) && widget.enabled()) {
                result = result.with(widget.withEnabled(false));
            }
        }
        if (result.byType(HudWidgetType.CROSSHAIR).isEmpty()) {
            result = result.with(HudWidgetState.defaults(result.nextId(HudWidgetType.CROSSHAIR),
                    HudWidgetType.CROSSHAIR));
        }
        return result;
    }

    /** Finds a preset by id (case-insensitive). */
    public static Optional<NexusHudPreset> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (NexusHudPreset preset : values()) {
            if (preset.id().equalsIgnoreCase(id.trim())) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    /** Ids in declaration order. */
    public static List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (NexusHudPreset preset : values()) {
            ids.add(preset.id());
        }
        return Collections.unmodifiableList(ids);
    }
}
