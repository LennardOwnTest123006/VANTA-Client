package dev.vanta.core.hud.render;

import dev.vanta.core.VantaVersion;
import dev.vanta.core.bridge.EffectInfo;
import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.ItemInfo;
import dev.vanta.core.bridge.KeyStates;
import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.bridge.Vec3d;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Believable, clearly fictional game data used by the HUD editor and the previews whenever no world is loaded.
 * Every value is fixed so previews are reproducible; the server is named "Sample Server" on the reserved
 * {@code .invalid} domain so it can never be mistaken for a real one. Actions are no-ops.
 */
public final class SampleGameData implements GameBridge {
    /**
     * A believable frame time history for the frame time graph widget (about 144 fps with a few hitches), the same
     * values every time.
     */
    public static double[] frameTimeSamples() {
        double[] out = new double[dev.vanta.core.hud.HudWidgetType.FRAMETIME_GRAPH_SAMPLES];
        for (int i = 0; i < out.length; i++) {
            double base = 6.9 + Math.sin(i / 5.0) * 1.1;
            double hitch = i % 41 == 7 ? 24.0 : 0.0;
            out[i] = base + hitch;
        }
        return out;
    }


    /** Left-button clicks per second shown by the sample CPS widget. */
    public static final int CPS_LEFT = 6;
    /** Right-button clicks per second shown by the sample CPS widget. */
    public static final int CPS_RIGHT = 3;
    /** 1 % low frame rate shown by the sample FPS widget. */
    public static final double ONE_PERCENT_LOW_FPS = 97.0;
    /** Sample wall clock: 2026-10-04T14:32:07Z. */
    public static final long EPOCH_MILLIS = 1_791_124_327_000L;
    /** Sample in-game time: 6 000 ticks after dawn, i.e. noon. */
    public static final long GAME_TICKS = 6_000L;
    /** Sample server name. */
    public static final String SERVER_NAME = "Sample Server";
    /** Sample server address on the reserved {@code .invalid} top-level domain. */
    public static final String SERVER_ADDRESS = "play.sample.invalid";

    private static final Vec3d POSITION = new Vec3d(128.5, 64.0, -220.3);
    private static final List<ItemInfo> ARMOR = List.of(
            new ItemInfo("Netherite Helmet", 380, 407),
            new ItemInfo("Netherite Chestplate", 512, 592),
            new ItemInfo("Netherite Leggings", 190, 555),
            new ItemInfo("Netherite Boots", 300, 481));
    private static final List<ItemInfo> HELD = List.of(
            new ItemInfo("Diamond Pickaxe", 1203, 1561),
            new ItemInfo("Shield", 290, 336));
    private static final List<EffectInfo> EFFECTS = List.of(
            new EffectInfo("Speed", 0x33EBFF, 1800, 1, false),
            new EffectInfo("Night Vision", 0x1F1FA1, 0, 0, true),
            new EffectInfo("Regeneration", 0xCD5CAB, 900, 0, false));
    private static final KeyStates KEYS = new KeyStates(true, false, false, false, false, false, true, false);

    @Override
    public int fps() {
        return 144;
    }

    @Override
    public double frameTimeMillis() {
        return 6.9;
    }

    @Override
    public long memoryUsedBytes() {
        return 1_536L << 20;
    }

    @Override
    public long memoryAllocatedBytes() {
        return 2_048L << 20;
    }

    @Override
    public long memoryMaxBytes() {
        return 4_096L << 20;
    }

    @Override
    public OptionalDouble cpuLoad() {
        return OptionalDouble.of(0.18);
    }

    @Override
    public boolean isInWorld() {
        return true;
    }

    @Override
    public Optional<Vec3d> playerPosition() {
        return Optional.of(POSITION);
    }

    @Override
    public float yaw() {
        return 180f;
    }

    @Override
    public float pitch() {
        return 12f;
    }

    @Override
    public Optional<String> biomeId() {
        return Optional.of("minecraft:cherry_grove");
    }

    @Override
    public Optional<String> dimensionId() {
        return Optional.of("minecraft:overworld");
    }

    @Override
    public Optional<String> serverAddress() {
        return Optional.of(SERVER_ADDRESS);
    }

    @Override
    public Optional<String> serverName() {
        return Optional.of(SERVER_NAME);
    }

    @Override
    public boolean isSingleplayer() {
        return false;
    }

    @Override
    public OptionalInt pingMillis() {
        return OptionalInt.of(32);
    }

    @Override
    public int armorValue() {
        return 20;
    }

    @Override
    public List<ItemInfo> armorPieces() {
        return ARMOR;
    }

    @Override
    public List<ItemInfo> heldItems() {
        return HELD;
    }

    @Override
    public List<EffectInfo> activeEffects() {
        return EFFECTS;
    }

    @Override
    public KeyStates keyStates() {
        return KEYS;
    }

    @Override
    public int entityCount() {
        return 87;
    }

    @Override
    public int renderDistance() {
        return 12;
    }

    @Override
    public int simulationDistance() {
        return 10;
    }

    @Override
    public String minecraftVersion() {
        return VantaVersion.MINECRAFT;
    }

    @Override
    public String fabricLoaderVersion() {
        return VantaVersion.FABRIC_LOADER;
    }

    @Override
    public String clientVersion() {
        return VantaVersion.CLIENT;
    }

    @Override
    public long currentTimeMillis() {
        return EPOCH_MILLIS;
    }

    @Override
    public long gameTicks() {
        return GAME_TICKS;
    }

    @Override
    public void openVanillaScreen(VanillaScreen screen) {
        // Sample data never drives the game.
    }

    @Override
    public boolean continueLastWorld() {
        return false;
    }

    @Override
    public Optional<String> lastWorldName() {
        return Optional.empty();
    }

    @Override
    public void quitGame() {
        // Sample data never drives the game.
    }

    @Override
    public void openUrl(String url) {
        // Sample data never drives the game.
    }

    @Override
    public void playUiSound() {
        // Sample data never drives the game.
    }

    @Override
    public Optional<Path> screenshotsDir() {
        return Optional.empty();
    }

    @Override
    public boolean isDevelopment() {
        return false;
    }
}
