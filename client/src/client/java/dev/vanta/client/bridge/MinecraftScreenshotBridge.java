package dev.vanta.client.bridge;

import dev.vanta.client.VantaClient;
import dev.vanta.core.bridge.ScreenshotBridge;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

/**
 * {@link ScreenshotBridge} using the vanilla {@link Screenshot} helper (same files, same folder as F2).
 * <p>
 * A HUD-free capture hides the GUI ({@code options.hideGui}) and the VANTA HUD, waits {@value #HIDDEN_TICKS} ticks so
 * at least one frame was rendered without them, grabs the main render target and restores the GUI immediately (the
 * read-back is queued before later draws). The saved file is identified as the newest PNG that appeared in the
 * screenshots folder; the vanilla helper only reports a chat message.
 */
public final class MinecraftScreenshotBridge implements ScreenshotBridge {
    /** Ticks to wait with the GUI hidden before grabbing (≥ 150 ms, so even 10 fps renders a frame). */
    static final int HIDDEN_TICKS = 3;

    private Consumer<Optional<Path>> pending;
    private int ticksRemaining;
    private boolean restoreHideGui;
    private boolean previousHideGui;
    private volatile boolean hudSuppressed;

    private static Path directory(Minecraft minecraft) {
        return new File(minecraft.gameDirectory, "screenshots").toPath();
    }

    @Override
    public Optional<Path> screenshotsDir() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return Optional.empty();
        }
        Path dir = directory(minecraft);
        return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
    }

    @Override
    public void capture(boolean hideHud, Consumer<Optional<Path>> onSaved) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || pending != null) {
            onSaved.accept(Optional.empty());
            return;
        }
        pending = onSaved;
        if (hideHud) {
            previousHideGui = minecraft.options.hideGui;
            restoreHideGui = true;
            minecraft.options.hideGui = true;
            hudSuppressed = true;
            ticksRemaining = HIDDEN_TICKS;
        } else {
            restoreHideGui = false;
            ticksRemaining = 1;
        }
    }

    /** True while the VANTA HUD must stay hidden for a pending capture. */
    public boolean isHudSuppressed() {
        return hudSuppressed;
    }

    /** Once per client tick: grabs the pending capture when its wait is over. */
    public void tick() {
        if (pending == null) {
            return;
        }
        ticksRemaining--;
        if (ticksRemaining > 0) {
            return;
        }
        Consumer<Optional<Path>> callback = pending;
        pending = null;
        Minecraft minecraft = Minecraft.getInstance();
        Path dir = directory(minecraft);
        Set<Path> before = listPngs(dir);
        try {
            Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(), message ->
                    minecraft.execute(() -> callback.accept(newestPng(dir, before))));
        } finally {
            restore(minecraft);
        }
    }

    private void restore(Minecraft minecraft) {
        if (restoreHideGui) {
            minecraft.options.hideGui = previousHideGui;
            restoreHideGui = false;
        }
        hudSuppressed = false;
    }

    @Override
    public void openScreenshotsFolder() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        Path dir = directory(minecraft);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            VantaClient.LOGGER.warn("Could not create {}", dir, e);
        }
        Util.getPlatform().openPath(dir);
    }

    private static Set<Path> listPngs(Path dir) {
        Set<Path> out = new HashSet<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".png")).forEach(out::add);
        } catch (IOException e) {
            VantaClient.LOGGER.debug("Could not list {}", dir, e);
        }
        return out;
    }

    /** The PNG that was not present before the capture; the most recently modified one when several appeared. */
    static Optional<Path> newestPng(Path dir, Set<Path> before) {
        Path best = null;
        long bestTime = Long.MIN_VALUE;
        for (Path candidate : listPngs(dir)) {
            if (before.contains(candidate)) {
                continue;
            }
            long modified;
            try {
                modified = Files.getLastModifiedTime(candidate).toMillis();
            } catch (IOException e) {
                continue;
            }
            if (modified > bestTime) {
                bestTime = modified;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }
}
