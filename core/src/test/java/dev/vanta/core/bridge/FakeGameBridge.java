package dev.vanta.core.bridge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Scriptable {@link GameBridge} for unit tests and screen previews. Every value has a public setter; actions are
 * recorded in {@link #actions}.
 */
public final class FakeGameBridge implements GameBridge {
    public int fps = 120;
    public double frameTimeMillis = 8.3;
    public long memoryUsed = 1_500L << 20;
    public long memoryAllocated = 2_048L << 20;
    public long memoryMax = 4_096L << 20;
    public OptionalDouble cpuLoad = OptionalDouble.of(0.23);
    public boolean inWorld = true;
    public Vec3d position = new Vec3d(128.5, 64.0, -220.25);
    public float yaw = 180f;
    public float pitch = 12f;
    public Optional<String> biomeId = Optional.of("minecraft:plains");
    public Optional<String> dimensionId = Optional.of("minecraft:overworld");
    public Optional<String> serverAddress = Optional.empty();
    public Optional<String> serverName = Optional.empty();
    public boolean singleplayer = true;
    public OptionalInt ping = OptionalInt.empty();
    public int armorValue = 15;
    public List<ItemInfo> armorPieces = new ArrayList<>(List.of(
            new ItemInfo("Diamond Helmet", 300, 363),
            new ItemInfo("Diamond Chestplate", 480, 528),
            new ItemInfo("Diamond Leggings", 400, 495),
            new ItemInfo("Diamond Boots", 200, 429)));
    public List<ItemInfo> heldItems = new ArrayList<>(List.of(new ItemInfo("Diamond Sword", 1200, 1561), ItemInfo.EMPTY));
    public List<EffectInfo> effects = new ArrayList<>(List.of(new EffectInfo("Speed", 0x33EBFF, 1800, 1, false)));
    public KeyStates keyStates = KeyStates.NONE;
    public int entityCount = 87;
    public int renderDistance = 12;
    public int simulationDistance = 10;
    public String minecraftVersion = "1.21.11";
    public String fabricLoaderVersion = "0.19.5";
    public String clientVersion = "1.0.0";
    public long currentTimeMillis = 1_791_115_200_000L;
    public long gameTicks = 24_000L;
    public Optional<String> lastWorldName = Optional.of("Survival World");
    public boolean continueLastWorldResult = true;
    public Optional<Path> screenshotsDir = Optional.empty();
    public boolean development = true;
    /** False emulates a screen open, the game paused or the window unfocused (only matters while in a world). */
    public boolean gameplayActive = true;
    /** {@code GL_RENDERER} string the client would report. */
    public Optional<String> gpuRenderer = Optional.empty();
    /** Recorded actions in call order, e.g. {@code openVanillaScreen:OPTIONS}, {@code quit}, {@code openUrl:https://…}. */
    public final List<String> actions = new ArrayList<>();

    @Override
    public int fps() {
        return fps;
    }

    @Override
    public double frameTimeMillis() {
        return frameTimeMillis;
    }

    @Override
    public long memoryUsedBytes() {
        return memoryUsed;
    }

    @Override
    public long memoryAllocatedBytes() {
        return memoryAllocated;
    }

    @Override
    public long memoryMaxBytes() {
        return memoryMax;
    }

    @Override
    public OptionalDouble cpuLoad() {
        return cpuLoad;
    }

    @Override
    public boolean isInWorld() {
        return inWorld;
    }

    @Override
    public boolean isGameplayActive() {
        return inWorld && gameplayActive;
    }

    @Override
    public Optional<String> gpuRenderer() {
        return gpuRenderer;
    }

    /** Simulates the player looking around (activity for the gameplay gate). */
    public FakeGameBridge look() {
        yaw = (yaw + 1f) % 360f;
        return this;
    }

    @Override
    public Optional<Vec3d> playerPosition() {
        return inWorld ? Optional.ofNullable(position) : Optional.empty();
    }

    @Override
    public float yaw() {
        return yaw;
    }

    @Override
    public float pitch() {
        return pitch;
    }

    @Override
    public Optional<String> biomeId() {
        return inWorld ? biomeId : Optional.empty();
    }

    @Override
    public Optional<String> dimensionId() {
        return inWorld ? dimensionId : Optional.empty();
    }

    @Override
    public Optional<String> serverAddress() {
        return serverAddress;
    }

    @Override
    public Optional<String> serverName() {
        return serverName;
    }

    @Override
    public boolean isSingleplayer() {
        return singleplayer;
    }

    @Override
    public OptionalInt pingMillis() {
        return ping;
    }

    @Override
    public int armorValue() {
        return armorValue;
    }

    @Override
    public List<ItemInfo> armorPieces() {
        return List.copyOf(armorPieces);
    }

    @Override
    public List<ItemInfo> heldItems() {
        return List.copyOf(heldItems);
    }

    @Override
    public List<EffectInfo> activeEffects() {
        return List.copyOf(effects);
    }

    @Override
    public KeyStates keyStates() {
        return keyStates;
    }

    @Override
    public int entityCount() {
        return entityCount;
    }

    @Override
    public int renderDistance() {
        return renderDistance;
    }

    @Override
    public int simulationDistance() {
        return simulationDistance;
    }

    @Override
    public String minecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public String fabricLoaderVersion() {
        return fabricLoaderVersion;
    }

    @Override
    public String clientVersion() {
        return clientVersion;
    }

    @Override
    public long currentTimeMillis() {
        return currentTimeMillis;
    }

    @Override
    public long gameTicks() {
        return gameTicks;
    }

    @Override
    public void openVanillaScreen(VanillaScreen screen) {
        actions.add("openVanillaScreen:" + screen);
    }

    @Override
    public boolean continueLastWorld() {
        actions.add("continueLastWorld");
        return continueLastWorldResult;
    }

    @Override
    public Optional<String> lastWorldName() {
        return lastWorldName;
    }

    @Override
    public void quitGame() {
        actions.add("quit");
    }

    @Override
    public void openUrl(String url) {
        actions.add("openUrl:" + url);
    }

    @Override
    public void playUiSound() {
        actions.add("sound");
    }

    @Override
    public Optional<Path> screenshotsDir() {
        return screenshotsDir;
    }

    @Override
    public boolean isDevelopment() {
        return development;
    }

    /** Puts the fake on a multiplayer server. */
    public FakeGameBridge onServer(String address, String name, int pingMs) {
        this.inWorld = true;
        this.singleplayer = false;
        this.serverAddress = Optional.of(address);
        this.serverName = Optional.of(name);
        this.ping = OptionalInt.of(pingMs);
        return this;
    }

    /** Puts the fake on the title screen. */
    public FakeGameBridge onTitleScreen() {
        this.inWorld = false;
        this.singleplayer = false;
        this.serverAddress = Optional.empty();
        this.serverName = Optional.empty();
        this.ping = OptionalInt.empty();
        this.gameTicks = 0;
        return this;
    }
}
