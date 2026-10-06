package dev.vanta.core.hud.render;

import dev.vanta.core.bridge.Cardinal;
import dev.vanta.core.bridge.EffectInfo;
import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.ItemInfo;
import dev.vanta.core.bridge.KeyStates;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.hud.FrameTimeTracker;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.i18n.Lang;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Immutable snapshot of everything the HUD widgets display, captured once per client tick by {@link HudRenderer}.
 * <p>
 * Widgets render from the snapshot instead of calling the {@link GameBridge} every frame, so the render pass never
 * touches game state and allocates at most the handful of small strings a frame needs. The formatted strings that
 * are expensive or shared between widgets (coordinates, clock, memory) are memoised inside the snapshot, so they
 * are built at most once per tick.
 */
public final class HudData {

    /** Snapshot used before the first tick: not in a world, every value empty. */
    public static final HudData EMPTY = new HudData(false, true, 0, 0.0, 0.0, OptionalInt.empty(), Optional.empty(),
            0f, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0, 0, 0L, 0L, ZoneOffset.UTC,
            0, List.of(), List.of(), List.of(), KeyStates.NONE, 0L, 0L, 0L, OptionalDouble.empty(), 0, "", "", "",
            false);

    private final boolean inWorld;
    private final boolean singleplayer;
    private final int fps;
    private final double frameTimeMs;
    private final double onePercentLowFps;
    private final OptionalInt pingMillis;
    private final Optional<Vec3d> position;
    private final float yaw;
    private final Optional<String> biomeId;
    private final Optional<String> dimensionId;
    private final Optional<String> serverName;
    private final Optional<String> serverAddress;
    private final int cpsLeft;
    private final int cpsRight;
    private final long epochMillis;
    private final long gameTicks;
    private final ZoneId zone;
    private final int armorValue;
    private final List<ItemInfo> armorPieces;
    private final List<ItemInfo> heldItems;
    private final List<EffectInfo> effects;
    private final KeyStates keys;
    private final long memoryUsed;
    private final long memoryAllocated;
    private final long memoryMax;
    private final OptionalDouble cpuLoad;
    private final int entityCount;
    private final String minecraftVersion;
    private final String fabricLoaderVersion;
    private final String clientVersion;
    private final boolean sample;

    // Memoised per-tick strings (lazily built, never observable from outside except through the accessors).
    private final String[] coordinateCache = new String[3 * 3];
    private String clock24;
    private String clock24Seconds;
    private String clock12;
    private String clock12Seconds;
    private String gameClock;
    private String biomeName;
    private java.util.HashMap<String, String> memo;

    HudData(boolean inWorld, boolean singleplayer, int fps, double frameTimeMs, double onePercentLowFps,
            OptionalInt pingMillis, Optional<Vec3d> position, float yaw, Optional<String> biomeId,
            Optional<String> dimensionId, Optional<String> serverName, Optional<String> serverAddress, int cpsLeft,
            int cpsRight, long epochMillis, long gameTicks, ZoneId zone, int armorValue, List<ItemInfo> armorPieces,
            List<ItemInfo> heldItems, List<EffectInfo> effects, KeyStates keys, long memoryUsed, long memoryAllocated,
            long memoryMax, OptionalDouble cpuLoad, int entityCount, String minecraftVersion,
            String fabricLoaderVersion, String clientVersion, boolean sample) {
        this.inWorld = inWorld;
        this.singleplayer = singleplayer;
        this.fps = Math.max(0, fps);
        this.frameTimeMs = Double.isFinite(frameTimeMs) ? Math.max(0.0, frameTimeMs) : 0.0;
        this.onePercentLowFps = Double.isFinite(onePercentLowFps) ? Math.max(0.0, onePercentLowFps) : 0.0;
        this.pingMillis = Objects.requireNonNull(pingMillis, "pingMillis");
        this.position = Objects.requireNonNull(position, "position");
        this.yaw = yaw;
        this.biomeId = Objects.requireNonNull(biomeId, "biomeId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.serverName = Objects.requireNonNull(serverName, "serverName");
        this.serverAddress = Objects.requireNonNull(serverAddress, "serverAddress");
        this.cpsLeft = Math.max(0, cpsLeft);
        this.cpsRight = Math.max(0, cpsRight);
        this.epochMillis = epochMillis;
        this.gameTicks = Math.max(0L, gameTicks);
        this.zone = Objects.requireNonNull(zone, "zone");
        this.armorValue = Math.max(0, armorValue);
        this.armorPieces = List.copyOf(armorPieces);
        this.heldItems = List.copyOf(heldItems);
        this.effects = List.copyOf(effects);
        this.keys = Objects.requireNonNull(keys, "keys");
        this.memoryUsed = Math.max(0L, memoryUsed);
        this.memoryAllocated = Math.max(0L, memoryAllocated);
        this.memoryMax = Math.max(0L, memoryMax);
        this.cpuLoad = Objects.requireNonNull(cpuLoad, "cpuLoad");
        this.entityCount = Math.max(0, entityCount);
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        this.fabricLoaderVersion = Objects.requireNonNull(fabricLoaderVersion, "fabricLoaderVersion");
        this.clientVersion = Objects.requireNonNull(clientVersion, "clientVersion");
        this.sample = sample;
    }

    /**
     * Captures a complete snapshot from the game (every bridge value, whatever the layout shows).
     *
     * @param game       live or sample game data
     * @param cpsLeft    left-button clicks in the last second
     * @param cpsRight   right-button clicks in the last second
     * @param frameTimes frame time history for the 1 % low figure (may be empty)
     * @param zone       time zone used by the clock widget
     */
    public static HudData capture(GameBridge game, int cpsLeft, int cpsRight, FrameTimeTracker frameTimes,
                                  ZoneId zone) {
        return capture(game, cpsLeft, cpsRight, frameTimes, zone, EnumSet.allOf(HudWidgetType.class));
    }

    /**
     * Captures a snapshot holding only what the enabled widgets display. Every other value stays at its empty
     * default and the matching bridge method is never called, so a layout with three widgets costs three or four
     * bridge round-trips per tick instead of twenty-five. {@code isInWorld} is always read.
     *
     * @param enabled the widget types enabled in the live layout
     */
    public static HudData capture(GameBridge game, int cpsLeft, int cpsRight, FrameTimeTracker frameTimes,
                                  ZoneId zone, Set<HudWidgetType> enabled) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(enabled, "enabled");
        boolean inWorld = game.isInWorld();
        boolean fps = enabled.contains(HudWidgetType.FPS);
        boolean ping = enabled.contains(HudWidgetType.PING);
        boolean coordinates = enabled.contains(HudWidgetType.COORDINATES);
        boolean direction = enabled.contains(HudWidgetType.DIRECTION);
        boolean biome = enabled.contains(HudWidgetType.BIOME);
        boolean server = enabled.contains(HudWidgetType.SERVER);
        boolean clock = enabled.contains(HudWidgetType.CLOCK);
        boolean armor = enabled.contains(HudWidgetType.ARMOR);
        boolean held = enabled.contains(HudWidgetType.ITEM_DURABILITY);
        boolean effects = enabled.contains(HudWidgetType.POTION_EFFECTS);
        boolean keys = enabled.contains(HudWidgetType.KEYSTROKES);
        boolean memory = enabled.contains(HudWidgetType.MEMORY);
        boolean cpu = enabled.contains(HudWidgetType.CPU);
        boolean entities = enabled.contains(HudWidgetType.ENTITY_COUNT);
        boolean versions = enabled.contains(HudWidgetType.MINECRAFT_VERSION);
        return new HudData(inWorld,
                ping || server ? game.isSingleplayer() : true,
                fps ? game.fps() : 0,
                fps ? game.frameTimeMillis() : 0.0,
                fps && frameTimes != null ? frameTimes.onePercentLowFps() : 0.0,
                ping ? game.pingMillis() : OptionalInt.empty(),
                coordinates && inWorld ? game.playerPosition() : Optional.empty(),
                direction ? game.yaw() : 0f,
                biome ? game.biomeId() : Optional.empty(),
                coordinates ? game.dimensionId() : Optional.empty(),
                server ? game.serverName() : Optional.empty(),
                server ? game.serverAddress() : Optional.empty(),
                cpsLeft, cpsRight,
                clock ? game.currentTimeMillis() : 0L,
                clock ? game.gameTicks() : 0L,
                zone == null ? ZoneOffset.UTC : zone,
                armor ? game.armorValue() : 0,
                armor ? game.armorPieces() : List.of(),
                held ? game.heldItems() : List.of(),
                effects ? game.activeEffects() : List.of(),
                keys ? game.keyStates() : KeyStates.NONE,
                memory ? game.memoryUsedBytes() : 0L,
                memory ? game.memoryAllocatedBytes() : 0L,
                memory ? game.memoryMaxBytes() : 0L,
                cpu ? game.cpuLoad() : OptionalDouble.empty(),
                entities ? game.entityCount() : 0,
                versions ? game.minecraftVersion() : "",
                versions ? game.fabricLoaderVersion() : "",
                versions ? game.clientVersion() : "",
                game instanceof SampleGameData);
    }

    /**
     * The believable sample snapshot shown by the HUD editor and the previews when no world is loaded. Uses
     * {@link SampleGameData} plus fixed click rates and a fixed 1 % low figure; the clock reads UTC so it is stable.
     */
    public static HudData sample() {
        SampleGameData game = new SampleGameData();
        return new HudData(true, game.isSingleplayer(), game.fps(), game.frameTimeMillis(),
                SampleGameData.ONE_PERCENT_LOW_FPS, game.pingMillis(), game.playerPosition(), game.yaw(),
                game.biomeId(), game.dimensionId(), game.serverName(), game.serverAddress(),
                SampleGameData.CPS_LEFT, SampleGameData.CPS_RIGHT, game.currentTimeMillis(), game.gameTicks(),
                ZoneOffset.UTC, game.armorValue(), game.armorPieces(), game.heldItems(), game.activeEffects(),
                game.keyStates(), game.memoryUsedBytes(), game.memoryAllocatedBytes(), game.memoryMaxBytes(),
                game.cpuLoad(), game.entityCount(), game.minecraftVersion(), game.fabricLoaderVersion(),
                game.clientVersion(), true);
    }

    // ---- raw values ------------------------------------------------------------------------------------------------

    /** True while the snapshot was taken inside a world. */
    public boolean inWorld() {
        return inWorld;
    }

    /** True in a local singleplayer world (no latency available). */
    public boolean singleplayer() {
        return singleplayer;
    }

    /** True when the snapshot holds sample data rather than live game values. */
    public boolean isSample() {
        return sample;
    }

    public int fps() {
        return fps;
    }

    /** Last frame duration in milliseconds. */
    public double frameTimeMs() {
        return frameTimeMs;
    }

    /** Frame rate of the slowest 1 % of frames, or 0 when no history exists. */
    public double onePercentLowFps() {
        return onePercentLowFps;
    }

    public OptionalInt pingMillis() {
        return pingMillis;
    }

    public Optional<Vec3d> position() {
        return position;
    }

    public float yaw() {
        return yaw;
    }

    /** Cardinal direction derived from the yaw, empty outside a world. */
    public Optional<Cardinal> facing() {
        return inWorld ? Optional.of(Cardinal.fromYaw(yaw)) : Optional.empty();
    }

    public Optional<String> biomeId() {
        return biomeId;
    }

    public Optional<String> dimensionId() {
        return dimensionId;
    }

    public Optional<String> serverName() {
        return serverName;
    }

    public Optional<String> serverAddress() {
        return serverAddress;
    }

    public int cpsLeft() {
        return cpsLeft;
    }

    public int cpsRight() {
        return cpsRight;
    }

    /** Wall clock at capture time. */
    public long epochMillis() {
        return epochMillis;
    }

    public long gameTicks() {
        return gameTicks;
    }

    public ZoneId zone() {
        return zone;
    }

    public int armorValue() {
        return armorValue;
    }

    /** Head to feet; empty slots are {@link ItemInfo#EMPTY}. */
    public List<ItemInfo> armorPieces() {
        return armorPieces;
    }

    /** Main hand then off hand. */
    public List<ItemInfo> heldItems() {
        return heldItems;
    }

    public List<EffectInfo> effects() {
        return effects;
    }

    public KeyStates keys() {
        return keys;
    }

    public long memoryUsed() {
        return memoryUsed;
    }

    public long memoryAllocated() {
        return memoryAllocated;
    }

    public long memoryMax() {
        return memoryMax;
    }

    public OptionalDouble cpuLoad() {
        return cpuLoad;
    }

    public int entityCount() {
        return entityCount;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public String fabricLoaderVersion() {
        return fabricLoaderVersion;
    }

    public String clientVersion() {
        return clientVersion;
    }

    // ---- memoised formatting --------------------------------------------------------------------------------------

    /**
     * A string a widget formatted earlier in this tick under {@code key}, or {@code null}. Widgets use their id as
     * the key so a frame formats each value at most once per tick.
     */
    public String cached(String key) {
        return memo == null ? null : memo.get(key);
    }

    /** Stores a formatted string for the rest of the tick and returns it. */
    public String cache(String key, String value) {
        if (memo == null) {
            memo = new java.util.HashMap<>();
        }
        memo.put(key, value);
        return value;
    }

    /**
     * One coordinate formatted with {@code decimals} (0–2) fractional digits. Zero decimals use the block coordinate
     * (floor) like the vanilla F3 screen.
     *
     * @param axis 0 = x, 1 = y, 2 = z
     */
    public String coordinate(int axis, int decimals) {
        int d = Math.max(0, Math.min(2, decimals));
        int index = axis * 3 + d;
        String cached = coordinateCache[index];
        if (cached != null) {
            return cached;
        }
        String formatted;
        if (position.isEmpty()) {
            formatted = Lang.tr("vanta.hud.label.unknown");
        } else {
            Vec3d p = position.get();
            double v = axis == 0 ? p.x() : axis == 1 ? p.y() : p.z();
            formatted = formatCoordinate(v, d);
        }
        coordinateCache[index] = formatted;
        return formatted;
    }

    /** Formats one coordinate value; exposed for tests and the sample preview. */
    public static String formatCoordinate(double value, int decimals) {
        if (decimals <= 0) {
            return Integer.toString((int) Math.floor(value));
        }
        return String.format(Locale.ROOT, decimals == 1 ? "%.1f" : "%.2f", value);
    }

    /** System time as {@code HH:mm} or {@code HH:mm:ss}. */
    public String clock24(boolean seconds) {
        if (seconds) {
            if (clock24Seconds == null) {
                clock24Seconds = formatTime(false, true);
            }
            return clock24Seconds;
        }
        if (clock24 == null) {
            clock24 = formatTime(false, false);
        }
        return clock24;
    }

    /** System time as {@code h:mm AM} or {@code h:mm:ss AM}. */
    public String clock12(boolean seconds) {
        if (seconds) {
            if (clock12Seconds == null) {
                clock12Seconds = formatTime(true, true);
            }
            return clock12Seconds;
        }
        if (clock12 == null) {
            clock12 = formatTime(true, false);
        }
        return clock12;
    }

    private String formatTime(boolean twelveHour, boolean seconds) {
        LocalTime time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime();
        int hour = time.getHour();
        StringBuilder sb = new StringBuilder(11);
        if (twelveHour) {
            int h = hour % 12;
            sb.append(h == 0 ? 12 : h);
        } else {
            pad2(sb, hour);
        }
        sb.append(':');
        pad2(sb, time.getMinute());
        if (seconds) {
            sb.append(':');
            pad2(sb, time.getSecond());
        }
        if (twelveHour) {
            sb.append(hour < 12 ? " AM" : " PM");
        }
        return sb.toString();
    }

    /**
     * In-game time of day as {@code HH:mm}. Minecraft days are 24 000 ticks long and tick 0 is 06:00.
     */
    public String gameClock() {
        if (gameClock == null) {
            gameClock = formatGameTime(gameTicks);
        }
        return gameClock;
    }

    /** Formats a tick count as the in-game time of day. */
    public static String formatGameTime(long ticks) {
        long dayTicks = Math.floorMod(ticks, 24_000L);
        int hour = (int) ((dayTicks / 1_000L + 6L) % 24L);
        int minute = (int) ((dayTicks % 1_000L) * 60L / 1_000L);
        StringBuilder sb = new StringBuilder(5);
        pad2(sb, hour);
        sb.append(':');
        pad2(sb, minute);
        return sb.toString();
    }

    /** Human-readable biome name ({@code minecraft:dark_forest} → {@code Dark Forest}), or "—" when unknown. */
    public String biomeName() {
        if (biomeName == null) {
            biomeName = biomeId.map(HudData::prettyName).orElseGet(() -> Lang.tr("vanta.hud.label.unknown"));
        }
        return biomeName;
    }

    /** Namespace of the biome id ({@code minecraft}), empty when unknown or without namespace. */
    public String biomeNamespace() {
        return biomeId.map(HudData::namespace).orElse("");
    }

    /**
     * Translated dimension name: the three vanilla dimensions use the bundled strings, anything else is prettified
     * from its id.
     */
    public String dimensionName() {
        if (dimensionId.isEmpty()) {
            return Lang.tr("vanta.hud.label.unknown");
        }
        String id = dimensionId.get();
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return switch (path) {
            case "overworld" -> Lang.tr("vanta.hud.label.dimension.overworld");
            case "the_nether" -> Lang.tr("vanta.hud.label.dimension.the_nether");
            case "the_end" -> Lang.tr("vanta.hud.label.dimension.the_end");
            default -> prettyName(id);
        };
    }

    /** Title-cases the path of an identifier: {@code minecraft:cherry_grove} → {@code Cherry Grove}. */
    public static String prettyName(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder sb = new StringBuilder(path.length());
        boolean upper = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_' || c == '/' || c == '.') {
                sb.append(' ');
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /** Namespace part of an identifier, empty when it has none. */
    public static String namespace(String id) {
        if (id == null) {
            return "";
        }
        int colon = id.indexOf(':');
        return colon < 0 ? "" : id.substring(0, colon);
    }

    /** Bytes as whole MiB. */
    public static long toMiB(long bytes) {
        return bytes / (1024L * 1024L);
    }

    /** Percentage of heap used relative to the allocated heap (0–100), 0 when nothing is allocated. */
    public int memoryPercent() {
        if (memoryAllocated <= 0L) {
            return 0;
        }
        return (int) Math.round(memoryUsed * 100.0 / memoryAllocated);
    }

    /** Fraction of the allocated heap in use (0–1). */
    public double memoryFraction() {
        return memoryAllocated <= 0L ? 0.0 : Math.max(0.0, Math.min(1.0, memoryUsed / (double) memoryAllocated));
    }

    private static void pad2(StringBuilder sb, int value) {
        if (value < 10) {
            sb.append('0');
        }
        sb.append(value);
    }
}
