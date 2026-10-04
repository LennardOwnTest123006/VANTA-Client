package dev.vanta.core.bridge;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Read-mostly view of the running game, implemented by the Fabric client on top of {@code Minecraft}.
 * <p>
 * Everything {@code core} knows about the game passes through this interface, which keeps {@code core} free of
 * Minecraft classes and unit-testable with {@code FakeGameBridge}. All methods are called on the render thread.
 * Methods that only make sense inside a world return {@link Optional} / empty values on the title screen.
 */
public interface GameBridge {

    // ---- performance -------------------------------------------------------------------------------------------

    /** Frames per second as reported by the game. */
    int fps();

    /** Duration of the last frame in milliseconds. */
    double frameTimeMillis();

    /** Heap bytes currently used. */
    long memoryUsedBytes();

    /** Heap bytes currently allocated by the JVM. */
    long memoryAllocatedBytes();

    /** Maximum heap bytes the JVM may allocate. */
    long memoryMaxBytes();

    /** Process CPU load in [0, 1] when the platform exposes it. */
    OptionalDouble cpuLoad();

    // ---- world -------------------------------------------------------------------------------------------------

    /** True while a world or server is loaded and the local player exists. */
    boolean isInWorld();

    /** Player position (feet), empty outside a world. */
    Optional<Vec3d> playerPosition();

    /** Player yaw in degrees (Minecraft convention). 0 outside a world. */
    float yaw();

    /** Player pitch in degrees. 0 outside a world. */
    float pitch();

    /** Cardinal direction the player faces, empty outside a world. */
    default Optional<Cardinal> facing() {
        return isInWorld() ? Optional.of(Cardinal.fromYaw(yaw())) : Optional.empty();
    }

    /** Biome identifier, e.g. {@code minecraft:plains}. */
    Optional<String> biomeId();

    /** Dimension identifier, e.g. {@code minecraft:overworld}. */
    Optional<String> dimensionId();

    /** Address of the connected server (host[:port]) when on multiplayer. */
    Optional<String> serverAddress();

    /** Saved name of the connected server when on multiplayer. */
    Optional<String> serverName();

    /** True when the player is in a singleplayer (local) world. */
    boolean isSingleplayer();

    /** Latency to the server in milliseconds when known. */
    OptionalInt pingMillis();

    /** Armor points (0–20). */
    int armorValue();

    /** Armor pieces head → feet; empty slots are {@link ItemInfo#EMPTY}. */
    List<ItemInfo> armorPieces();

    /** Main hand followed by off hand; empty slots are {@link ItemInfo#EMPTY}. */
    List<ItemInfo> heldItems();

    /** Active status effects. */
    List<EffectInfo> activeEffects();

    /** Pressed state of movement/mouse inputs. */
    KeyStates keyStates();

    /** Number of entities loaded in the client world. */
    int entityCount();

    /** Current render distance in chunks. */
    int renderDistance();

    /** Current simulation distance in chunks. */
    int simulationDistance();

    // ---- versions & time ---------------------------------------------------------------------------------------

    /** Minecraft version string ({@code 1.21.11}). */
    String minecraftVersion();

    /** Fabric Loader version. */
    String fabricLoaderVersion();

    /** VANTA Client version. */
    String clientVersion();

    /** Wall clock in epoch milliseconds. */
    long currentTimeMillis();

    /** Game ticks elapsed in the current world (0 outside a world). */
    long gameTicks();

    // ---- actions -----------------------------------------------------------------------------------------------

    /** Opens a vanilla screen on top of the current one. */
    void openVanillaScreen(VanillaScreen screen);

    /**
     * Opens the most recently played singleplayer world.
     *
     * @return false when there is no world to continue
     */
    boolean continueLastWorld();

    /** Name of the most recently played singleplayer world, if any. */
    Optional<String> lastWorldName();

    /** Quits the game gracefully (same as the vanilla Quit button). */
    void quitGame();

    /** Opens a URL in the system browser (the game asks for confirmation). */
    void openUrl(String url);

    /** Plays the vanilla UI click sound. */
    void playUiSound();

    /** Screenshot directory when it exists. */
    Optional<Path> screenshotsDir();

    /** True in a development environment (Loom run). */
    boolean isDevelopment();
}
