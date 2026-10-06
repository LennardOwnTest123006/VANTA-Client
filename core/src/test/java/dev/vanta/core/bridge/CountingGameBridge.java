package dev.vanta.core.bridge;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * {@link GameBridge} that forwards to another bridge and counts every call per method, so tests can assert how
 * often the renderers touch the game (each call is a bridge round-trip into Minecraft state in the client).
 */
public final class CountingGameBridge implements GameBridge {
    private final GameBridge delegate;
    private final TreeMap<String, Integer> calls = new TreeMap<>();

    public CountingGameBridge(GameBridge delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** Calls recorded for one method since the last {@link #reset()}. */
    public int count(String method) {
        return calls.getOrDefault(method, 0);
    }

    /** Every call recorded since the last {@link #reset()}. */
    public int total() {
        int total = 0;
        for (int n : calls.values()) {
            total += n;
        }
        return total;
    }

    /** Snapshot of the counters, sorted by method name. */
    public TreeMap<String, Integer> calls() {
        return new TreeMap<>(calls);
    }

    /** Forgets every counter. */
    public void reset() {
        calls.clear();
    }

    private void hit(String method) {
        calls.merge(method, 1, Integer::sum);
    }

    @Override
    public int fps() {
        hit("fps");
        return delegate.fps();
    }

    @Override
    public double frameTimeMillis() {
        hit("frameTimeMillis");
        return delegate.frameTimeMillis();
    }

    @Override
    public long memoryUsedBytes() {
        hit("memoryUsedBytes");
        return delegate.memoryUsedBytes();
    }

    @Override
    public long memoryAllocatedBytes() {
        hit("memoryAllocatedBytes");
        return delegate.memoryAllocatedBytes();
    }

    @Override
    public long memoryMaxBytes() {
        hit("memoryMaxBytes");
        return delegate.memoryMaxBytes();
    }

    @Override
    public OptionalDouble cpuLoad() {
        hit("cpuLoad");
        return delegate.cpuLoad();
    }

    @Override
    public boolean isInWorld() {
        hit("isInWorld");
        return delegate.isInWorld();
    }

    @Override
    public Optional<Vec3d> playerPosition() {
        hit("playerPosition");
        return delegate.playerPosition();
    }

    @Override
    public float yaw() {
        hit("yaw");
        return delegate.yaw();
    }

    @Override
    public float pitch() {
        hit("pitch");
        return delegate.pitch();
    }

    @Override
    public Optional<String> biomeId() {
        hit("biomeId");
        return delegate.biomeId();
    }

    @Override
    public Optional<String> dimensionId() {
        hit("dimensionId");
        return delegate.dimensionId();
    }

    @Override
    public Optional<String> serverAddress() {
        hit("serverAddress");
        return delegate.serverAddress();
    }

    @Override
    public Optional<String> serverName() {
        hit("serverName");
        return delegate.serverName();
    }

    @Override
    public boolean isSingleplayer() {
        hit("isSingleplayer");
        return delegate.isSingleplayer();
    }

    @Override
    public OptionalInt pingMillis() {
        hit("pingMillis");
        return delegate.pingMillis();
    }

    @Override
    public int armorValue() {
        hit("armorValue");
        return delegate.armorValue();
    }

    @Override
    public List<ItemInfo> armorPieces() {
        hit("armorPieces");
        return delegate.armorPieces();
    }

    @Override
    public List<ItemInfo> heldItems() {
        hit("heldItems");
        return delegate.heldItems();
    }

    @Override
    public List<EffectInfo> activeEffects() {
        hit("activeEffects");
        return delegate.activeEffects();
    }

    @Override
    public KeyStates keyStates() {
        hit("keyStates");
        return delegate.keyStates();
    }

    @Override
    public int entityCount() {
        hit("entityCount");
        return delegate.entityCount();
    }

    @Override
    public int renderDistance() {
        hit("renderDistance");
        return delegate.renderDistance();
    }

    @Override
    public int simulationDistance() {
        hit("simulationDistance");
        return delegate.simulationDistance();
    }

    @Override
    public String minecraftVersion() {
        hit("minecraftVersion");
        return delegate.minecraftVersion();
    }

    @Override
    public String fabricLoaderVersion() {
        hit("fabricLoaderVersion");
        return delegate.fabricLoaderVersion();
    }

    @Override
    public String clientVersion() {
        hit("clientVersion");
        return delegate.clientVersion();
    }

    @Override
    public long currentTimeMillis() {
        hit("currentTimeMillis");
        return delegate.currentTimeMillis();
    }

    @Override
    public long gameTicks() {
        hit("gameTicks");
        return delegate.gameTicks();
    }

    @Override
    public void openVanillaScreen(VanillaScreen screen) {
        hit("openVanillaScreen");
        delegate.openVanillaScreen(screen);
    }

    @Override
    public boolean continueLastWorld() {
        hit("continueLastWorld");
        return delegate.continueLastWorld();
    }

    @Override
    public Optional<String> lastWorldName() {
        hit("lastWorldName");
        return delegate.lastWorldName();
    }

    @Override
    public void quitGame() {
        hit("quitGame");
        delegate.quitGame();
    }

    @Override
    public void openUrl(String url) {
        hit("openUrl");
        delegate.openUrl(url);
    }

    @Override
    public void playUiSound() {
        hit("playUiSound");
        delegate.playUiSound();
    }

    @Override
    public Optional<Path> screenshotsDir() {
        hit("screenshotsDir");
        return delegate.screenshotsDir();
    }

    @Override
    public boolean isDevelopment() {
        hit("isDevelopment");
        return delegate.isDevelopment();
    }
}
