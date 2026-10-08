package dev.vanta.client.bridge;

import dev.vanta.client.screen.VanillaScreenOpener;
import dev.vanta.core.VantaVersion;
import dev.vanta.core.bridge.EffectInfo;
import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.ItemInfo;
import dev.vanta.core.bridge.KeyStates;
import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.perf.CpuSampler;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

/**
 * {@link GameBridge} on top of {@link Minecraft}. Every method reads the live game state through the public
 * Minecraft API; nothing here writes to the world or sends packets. All methods are safe to call on the title
 * screen (they return empty values when no world is loaded).
 */
public final class MinecraftGameBridge implements GameBridge {
    private final CpuSampler cpu = new CpuSampler();
    private final LastWorldLocator lastWorld = new LastWorldLocator();
    private final String fabricLoaderVersion;
    /** {@code GL_RENDERER}, read once at CLIENT_STARTED by {@link #captureGpuRenderer()}. */
    private volatile String gpuRenderer;

    public MinecraftGameBridge() {
        this.fabricLoaderVersion = FabricLoader.getInstance().getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(VantaVersion.FABRIC_LOADER);
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static LocalPlayer player() {
        Minecraft minecraft = minecraft();
        return minecraft == null ? null : minecraft.player;
    }

    private static ClientLevel level() {
        Minecraft minecraft = minecraft();
        return minecraft == null ? null : minecraft.level;
    }

    // ---- performance -------------------------------------------------------------------------------------------

    @Override
    public int fps() {
        Minecraft minecraft = minecraft();
        return minecraft == null ? 0 : minecraft.getFps();
    }

    /** Duration of the last frame as measured by the game loop ({@code Minecraft.getFrameTimeNs}). */
    @Override
    public double frameTimeMillis() {
        Minecraft minecraft = minecraft();
        return minecraft == null ? 0.0 : minecraft.getFrameTimeNs() / 1_000_000.0;
    }

    @Override
    public long memoryUsedBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    @Override
    public long memoryAllocatedBytes() {
        return Runtime.getRuntime().totalMemory();
    }

    @Override
    public long memoryMaxBytes() {
        return Runtime.getRuntime().maxMemory();
    }

    @Override
    public OptionalDouble cpuLoad() {
        return cpu.sample();
    }

    // ---- world -------------------------------------------------------------------------------------------------

    @Override
    public boolean isInWorld() {
        return level() != null && player() != null;
    }

    /**
     * Smart Boost's gameplay gate: a world is loaded, no screen is open (pause menu, inventory, chat, VANTA screens),
     * the game is not paused and the window has the input focus.
     */
    @Override
    public boolean isGameplayActive() {
        Minecraft minecraft = minecraft();
        return minecraft != null && minecraft.level != null && minecraft.player != null && minecraft.screen == null
                && !minecraft.isPaused() && minecraft.isWindowActive();
    }

    @Override
    public Optional<String> gpuRenderer() {
        return Optional.ofNullable(gpuRenderer);
    }

    /**
     * Reads the OpenGL renderer string once (call on the render thread with the GL context current, i.e. at
     * CLIENT_STARTED). Any failure leaves it unknown; Smart Boost then starts from its unknown-GPU guess.
     */
    public void captureGpuRenderer() {
        if (gpuRenderer != null) {
            return;
        }
        try {
            String renderer = GL11.glGetString(GL11.GL_RENDERER);
            if (renderer != null && !renderer.isBlank()) {
                gpuRenderer = renderer.trim();
            }
        } catch (RuntimeException | LinkageError e) {
            // No current context or no OpenGL binding: the renderer string is optional.
        }
    }

    @Override
    public Optional<Vec3d> playerPosition() {
        LocalPlayer player = player();
        if (player == null) {
            return Optional.empty();
        }
        Vec3 position = player.position();
        return Optional.of(new Vec3d(position.x, position.y, position.z));
    }

    @Override
    public float yaw() {
        LocalPlayer player = player();
        return player == null ? 0f : player.getYRot();
    }

    @Override
    public float pitch() {
        LocalPlayer player = player();
        return player == null ? 0f : player.getXRot();
    }

    @Override
    public Optional<String> biomeId() {
        LocalPlayer player = player();
        ClientLevel level = level();
        if (player == null || level == null) {
            return Optional.empty();
        }
        return level.getBiome(player.blockPosition()).unwrapKey().map(key -> key.identifier().toString());
    }

    @Override
    public Optional<String> dimensionId() {
        ClientLevel level = level();
        if (level == null) {
            return Optional.empty();
        }
        return Optional.of(level.dimension().identifier().toString());
    }

    @Override
    public Optional<String> serverAddress() {
        Minecraft minecraft = minecraft();
        if (minecraft == null || minecraft.isLocalServer() || level() == null) {
            return Optional.empty();
        }
        ServerData server = minecraft.getCurrentServer();
        if (server == null || server.ip == null || server.ip.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(server.ip);
    }

    @Override
    public Optional<String> serverName() {
        Minecraft minecraft = minecraft();
        if (minecraft == null || minecraft.isLocalServer() || level() == null) {
            return Optional.empty();
        }
        ServerData server = minecraft.getCurrentServer();
        if (server == null || server.name == null || server.name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(server.name);
    }

    @Override
    public boolean isSingleplayer() {
        Minecraft minecraft = minecraft();
        return minecraft != null && level() != null && minecraft.isLocalServer();
    }

    /**
     * Folder name of the open singleplayer level: the integrated server's world directory
     * ({@code MinecraftServer.getWorldPath(LevelResource.ROOT)}, the folder under {@code saves/}). This is the name
     * {@code WorldKeys.singleplayer} keys waypoints by, not the display name from {@code level.dat}.
     */
    @Override
    public Optional<String> singleplayerLevelName() {
        Minecraft minecraft = minecraft();
        if (minecraft == null || level() == null || !minecraft.isLocalServer()) {
            return Optional.empty();
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return Optional.empty();
        }
        try {
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path name = root.getFileName();
            if (name == null || name.toString().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(name.toString());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public OptionalInt pingMillis() {
        Minecraft minecraft = minecraft();
        LocalPlayer player = player();
        if (minecraft == null || player == null) {
            return OptionalInt.empty();
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return OptionalInt.empty();
        }
        PlayerInfo info = connection.getPlayerInfo(player.getUUID());
        return info == null ? OptionalInt.empty() : OptionalInt.of(Math.max(0, info.getLatency()));
    }

    @Override
    public int armorValue() {
        LocalPlayer player = player();
        return player == null ? 0 : player.getArmorValue();
    }

    @Override
    public List<ItemInfo> armorPieces() {
        LocalPlayer player = player();
        if (player == null) {
            return List.of(ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY);
        }
        List<ItemInfo> out = new ArrayList<>(4);
        out.add(itemInfo(player.getItemBySlot(EquipmentSlot.HEAD)));
        out.add(itemInfo(player.getItemBySlot(EquipmentSlot.CHEST)));
        out.add(itemInfo(player.getItemBySlot(EquipmentSlot.LEGS)));
        out.add(itemInfo(player.getItemBySlot(EquipmentSlot.FEET)));
        return out;
    }

    @Override
    public List<ItemInfo> heldItems() {
        LocalPlayer player = player();
        if (player == null) {
            return List.of(ItemInfo.EMPTY, ItemInfo.EMPTY);
        }
        return List.of(itemInfo(player.getMainHandItem()), itemInfo(player.getOffhandItem()));
    }

    /** Converts a stack to the core's snapshot; empty stacks map to {@link ItemInfo#EMPTY}. */
    static ItemInfo itemInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemInfo.EMPTY;
        }
        String name = stack.getHoverName().getString();
        if (!stack.isDamageableItem()) {
            return new ItemInfo(name, 0, 0);
        }
        int max = stack.getMaxDamage();
        return new ItemInfo(name, Math.max(0, max - stack.getDamageValue()), max);
    }

    @Override
    public List<EffectInfo> activeEffects() {
        LocalPlayer player = player();
        if (player == null) {
            return List.of();
        }
        List<EffectInfo> out = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (!instance.isVisible()) {
                continue;
            }
            MobEffect effect = instance.getEffect().value();
            out.add(new EffectInfo(effect.getDisplayName().getString(), effect.getColor(), instance.getDuration(),
                    instance.getAmplifier(), instance.isInfiniteDuration()));
        }
        return out;
    }

    @Override
    public KeyStates keyStates() {
        Minecraft minecraft = minecraft();
        if (minecraft == null || minecraft.options == null || player() == null) {
            return KeyStates.NONE;
        }
        Options options = minecraft.options;
        return new KeyStates(options.keyUp.isDown(), options.keyLeft.isDown(), options.keyDown.isDown(),
                options.keyRight.isDown(), options.keyJump.isDown(), options.keyShift.isDown(),
                options.keyAttack.isDown(), options.keyUse.isDown());
    }

    @Override
    public int entityCount() {
        ClientLevel level = level();
        return level == null ? 0 : level.getEntityCount();
    }

    @Override
    public int renderDistance() {
        Minecraft minecraft = minecraft();
        return minecraft == null || minecraft.options == null ? 0 : minecraft.options.renderDistance().get();
    }

    @Override
    public int simulationDistance() {
        Minecraft minecraft = minecraft();
        return minecraft == null || minecraft.options == null ? 0 : minecraft.options.simulationDistance().get();
    }

    // ---- versions & time ---------------------------------------------------------------------------------------

    @Override
    public String minecraftVersion() {
        return VantaVersion.MINECRAFT;
    }

    @Override
    public String fabricLoaderVersion() {
        return fabricLoaderVersion;
    }

    @Override
    public String clientVersion() {
        return FabricLoader.getInstance().getModContainer("vanta")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(VantaVersion.CLIENT);
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long gameTicks() {
        ClientLevel level = level();
        return level == null ? 0L : level.getGameTime();
    }

    // ---- actions -----------------------------------------------------------------------------------------------

    @Override
    public void openVanillaScreen(VanillaScreen screen) {
        VanillaScreenOpener.open(screen);
    }

    @Override
    public boolean continueLastWorld() {
        return lastWorld.continueLastWorld();
    }

    @Override
    public Optional<String> lastWorldName() {
        return lastWorld.lastWorldName();
    }

    @Override
    public void quitGame() {
        minecraft().stop();
    }

    @Override
    public void openUrl(String url) {
        VanillaScreenOpener.openUrl(url);
    }

    @Override
    public void playUiSound() {
        Minecraft minecraft = minecraft();
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public Optional<Path> screenshotsDir() {
        Minecraft minecraft = minecraft();
        if (minecraft == null) {
            return Optional.empty();
        }
        Path dir = new File(minecraft.gameDirectory, "screenshots").toPath();
        return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
    }

    @Override
    public boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
