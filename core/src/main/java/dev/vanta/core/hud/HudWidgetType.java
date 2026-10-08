package dev.vanta.core.hud;

import dev.vanta.core.i18n.LangKeyed;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Every HUD widget VANTA can draw, with its defaults and the schema of its widget-specific properties.
 * <p>
 * Sizes are in GUI pixels for the vanilla font (line height 9). All widgets only display information the game already
 * shows or exposes (F3 screen, inventory, effects); none gives information about other players.
 */
public enum HudWidgetType implements LangKeyed {
    FPS(44, 14, HudAnchor.TOP_LEFT, true, true, true, false,
            List.of(HudProp.bool("showLabel", true), HudProp.bool("showFrameTime", false))),
    PING(48, 14, HudAnchor.TOP_LEFT, true, true, true, false,
            List.of(HudProp.bool("showLabel", true))),
    COORDINATES(112, 14, HudAnchor.TOP_LEFT, true, true, true, false,
            List.of(HudProp.integer("decimals", 0, 0, 2), HudProp.bool("showDimension", false),
                    HudProp.bool("singleLine", true))),
    DIRECTION(64, 14, HudAnchor.TOP_LEFT, true, true, true, false,
            List.of(HudProp.bool("showYaw", true), HudProp.bool("showAxis", false))),
    BIOME(96, 14, HudAnchor.TOP_LEFT, true, true, true, false,
            List.of(HudProp.bool("showNamespace", false))),
    SERVER(104, 14, HudAnchor.TOP_RIGHT, true, true, true, false,
            List.of(HudProp.bool("showAddress", true))),
    CPS(60, 14, HudAnchor.BOTTOM_RIGHT, true, true, true, false,
            List.of(HudProp.bool("showRight", true), HudProp.bool("showLabel", true))),
    CLOCK(56, 14, HudAnchor.TOP_RIGHT, true, true, true, false,
            List.of(HudProp.choice("format", "system_24h", "system_24h", "system_12h", "game_time"),
                    HudProp.bool("showSeconds", false))),
    ARMOR(64, 56, HudAnchor.BOTTOM_LEFT, true, true, true, false,
            List.of(HudProp.bool("showDurability", true), HudProp.choice("layout", "vertical", "vertical", "horizontal"))),
    ITEM_DURABILITY(72, 26, HudAnchor.BOTTOM_RIGHT, true, true, true, false,
            List.of(HudProp.bool("showOffhand", true), HudProp.bool("showPercent", false))),
    POTION_EFFECTS(96, 40, HudAnchor.TOP_RIGHT, true, true, true, false,
            List.of(HudProp.bool("showDuration", true), HudProp.bool("compact", false))),
    KEYSTROKES(62, 84, HudAnchor.BOTTOM_RIGHT, false, true, true, false,
            List.of(HudProp.bool("showMouse", true), HudProp.bool("showSpace", true), HudProp.bool("showCps", false))),
    MEMORY(96, 14, HudAnchor.TOP_LEFT, true, true, true, false,
            List.of(HudProp.bool("showPercent", true), HudProp.bool("showMax", false))),
    CPU(60, 14, HudAnchor.TOP_LEFT, true, true, true, false,
            List.of(HudProp.bool("showLabel", true))),
    ENTITY_COUNT(72, 14, HudAnchor.TOP_LEFT, true, true, true, false,
            List.of(HudProp.bool("showLabel", true))),
    MINECRAFT_VERSION(96, 14, HudAnchor.BOTTOM_LEFT, true, true, true, false,
            List.of(HudProp.bool("showFabric", false))),
    /**
     * Vanta Lab: the last {@value #FRAMETIME_GRAPH_SAMPLES} frame times as a sparkline with p50 and p99 labels.
     * Drawn, listed in the editor and offered to the assistant only while {@code LabFeature.FRAMETIME_GRAPH} is on;
     * a layout keeps the widget while the feature is off, so switching it back on restores the player's placement.
     */
    FRAMETIME_GRAPH(120, 40, HudAnchor.TOP_RIGHT, true, true, true, false,
            List.of(HudProp.bool("showLabels", true))),
    /** The crosshair is always centred; its look comes from {@code CrosshairStyle}. */
    CROSSHAIR(16, 16, HudAnchor.CENTER, false, false, false, true, List.of());

    /** Frame times the graph widget shows. */
    public static final int FRAMETIME_GRAPH_SAMPLES = 120;

    private final int defaultWidth;
    private final int defaultHeight;
    private final HudAnchor defaultAnchor;
    private final boolean supportsResize;
    private final boolean supportsScale;
    private final boolean supportsColor;
    private final boolean alwaysCentered;
    private final List<HudProp> props;

    HudWidgetType(int defaultWidth, int defaultHeight, HudAnchor defaultAnchor, boolean supportsResize,
                  boolean supportsScale, boolean supportsColor, boolean alwaysCentered, List<HudProp> props) {
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.defaultAnchor = defaultAnchor;
        this.supportsResize = supportsResize;
        this.supportsScale = supportsScale;
        this.supportsColor = supportsColor;
        this.alwaysCentered = alwaysCentered;
        this.props = props;
    }

    /** Lower-case id used in JSON ({@code item_durability}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String langKey() {
        return "vanta.hud.widget." + id();
    }

    /** Translation key of the one-line description. */
    public String descriptionKey() {
        return "vanta.hud.widget." + id() + ".description";
    }

    /** Default width in GUI pixels. */
    public int defaultWidth() {
        return defaultWidth;
    }

    /** Default height in GUI pixels. */
    public int defaultHeight() {
        return defaultHeight;
    }

    /** Anchor used when the widget is added to a layout. */
    public HudAnchor defaultAnchor() {
        return defaultAnchor;
    }

    /** True when the user may drag the resize handles. */
    public boolean supportsResize() {
        return supportsResize;
    }

    /** True when the scale slider applies. */
    public boolean supportsScale() {
        return supportsScale;
    }

    /** True when text/background/accent colours apply. */
    public boolean supportsColor() {
        return supportsColor;
    }

    /** True for widgets pinned to the screen centre (crosshair). */
    public boolean alwaysCentered() {
        return alwaysCentered;
    }

    /** True when the widget can be moved by the editor. */
    public boolean movable() {
        return !alwaysCentered;
    }

    /** Widget-specific property schema. */
    public List<HudProp> settingsSchema() {
        return props;
    }

    /** Finds a property by key. */
    public Optional<HudProp> prop(String key) {
        for (HudProp prop : props) {
            if (prop.key().equals(key)) {
                return Optional.of(prop);
            }
        }
        return Optional.empty();
    }

    /** Finds a type by id (case-insensitive). */
    public static Optional<HudWidgetType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (HudWidgetType type : values()) {
            if (type.id().equalsIgnoreCase(id) || type.name().equalsIgnoreCase(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
