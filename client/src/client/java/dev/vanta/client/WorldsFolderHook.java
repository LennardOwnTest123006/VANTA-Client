package dev.vanta.client;

import dev.vanta.core.worlds.MinecraftFolderHint;
import dev.vanta.core.worlds.WorldsFolder;
import dev.vanta.core.worlds.WorldsFolderMode;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Hands the resolved Singleplayer worlds folder ({@link WorldsFolder}) to {@code mixin.MinecraftLevelSourceMixin}.
 * <p>
 * Timing: Fabric runs the client entrypoint inside the {@code Minecraft} constructor, right after the game directory
 * is stored and before the constructor creates its {@code LevelStorageSource}, so {@link VantaClient} can
 * {@link #install} the folder first. Until then, or when anything failed, the mixin gets the vanilla paths back
 * unchanged.
 */
public final class WorldsFolderHook {
    /** JVM property the game tests run with ({@code -Dvanta.gametest=true}). */
    private static final String GAMETEST_PROPERTY = "vanta.gametest";

    private static volatile WorldsFolder active;

    private WorldsFolderHook() {
    }

    /**
     * Resolves and remembers the folder for this game start and logs which one is used and why. Never throws.
     *
     * @param gameDir the game directory Minecraft runs in
     * @param mode    the "Singleplayer worlds" setting
     * @return the folder in use
     */
    public static WorldsFolder install(Path gameDir, WorldsFolderMode mode) {
        WorldsFolder folder;
        try {
            folder = resolve(gameDir, mode);
        } catch (RuntimeException e) {
            folder = WorldsFolder.vanta(gameDir, "the worlds folder could not be resolved (" + e + ")");
        }
        active = folder;
        if (folder.redirected()) {
            VantaClient.LOGGER.info("Singleplayer worlds: {} → saves {}", folder.reason(), folder.savesDir());
        } else {
            VantaClient.LOGGER.info("Singleplayer worlds stay in the VANTA folder {}: {}", folder.savesDir(),
                    folder.reason());
        }
        return folder;
    }

    private static WorldsFolder resolve(Path gameDir, WorldsFolderMode mode) {
        // A game test never falls back to the platform default: that would be the developer's real .minecraft. CI
        // points it at a stand-in folder through the launcher's note instead.
        if (Boolean.getBoolean(GAMETEST_PROPERTY) && MinecraftFolderHint.read(gameDir).isEmpty()) {
            return WorldsFolder.vanta(gameDir, "game test without a launcher note (the real Minecraft folder is "
                    + "never used by a game test)");
        }
        return WorldsFolder.resolveForThisGame(gameDir, mode);
    }

    /** The folder resolved at this start, empty before {@link #install}. */
    public static Optional<WorldsFolder> active() {
        return Optional.ofNullable(active);
    }

    /** The saves folder Minecraft's level storage should use instead of {@code vanillaSaves}. */
    public static Path savesDir(Path vanillaSaves) {
        WorldsFolder folder = active;
        return folder != null && folder.redirected() ? folder.savesDir() : vanillaSaves;
    }

    /** The backups folder Minecraft's level storage should use instead of {@code vanillaBackups}. */
    public static Path backupsDir(Path vanillaBackups) {
        WorldsFolder folder = active;
        return folder != null && folder.redirected() ? folder.backupsDir() : vanillaBackups;
    }
}
