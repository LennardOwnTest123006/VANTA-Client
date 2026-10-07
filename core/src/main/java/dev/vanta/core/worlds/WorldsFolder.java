package dev.vanta.core.worlds;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Which {@code saves/} (and {@code backups/}) folder Singleplayer uses.
 * <p>
 * VANTA runs in its own game folder, so vanilla Minecraft would list only {@code <VANTA game folder>/saves}, which
 * is empty for a new VANTA player while their worlds are in the official Minecraft folder. With the setting on
 * {@link WorldsFolderMode#MINECRAFT_FOLDER} (the default) the game uses the official folder's {@code saves/} and
 * {@code backups/} instead, exactly like a normal Minecraft installation: the world list, Create New World, loading,
 * backups. Nothing is moved, copied or deleted, and no folder is created in the official Minecraft folder.
 * <p>
 * The VANTA folder is kept whenever one of these holds (the {@link #reason()} says which):
 * <ul>
 *   <li>the system property {@code vanta.worlds.folder} is {@code vanta} (escape hatch for tests and support);</li>
 *   <li>the setting is {@link WorldsFolderMode#VANTA_FOLDER};</li>
 *   <li>the official folder (from the launcher's {@link MinecraftFolderHint}, else the platform default) or its
 *       {@code saves/} does not exist or is not a directory;</li>
 *   <li>the game already runs in the official folder (manual install), so there is nothing to redirect;</li>
 *   <li>there is no launcher note and the game folder is not a VANTA Launcher instance (another launcher's instance
 *       keeps its own worlds);</li>
 *   <li>the official {@code saves/} has no world yet while the VANTA folder's has (vanilla creates an empty
 *       {@code saves/} on its first start): redirecting would hide the player's VANTA worlds and show nothing.</li>
 * </ul>
 * Minecraft creates its level storage once while starting, so a change of the setting takes effect at the next start.
 *
 * @param savesDir     the folder Minecraft's level storage should use for worlds
 * @param backupsDir   the folder for world backups
 * @param minecraftDir the official Minecraft folder when the worlds are redirected there, empty otherwise
 * @param reason       English one-line explanation for the log
 */
public record WorldsFolder(Path savesDir, Path backupsDir, Optional<Path> minecraftDir, String reason) {
    /**
     * System property {@code vanta.worlds.folder}: {@code vanta} keeps the VANTA game folder's saves whatever the
     * setting says. Joined from parts so the translation-key scan of the domain sources does not take it for a key.
     */
    public static final String PROPERTY = String.join(".", "vanta", "worlds", "folder");
    /** Value of {@link #PROPERTY} that keeps the VANTA folder. */
    public static final String PROPERTY_VANTA = "vanta";

    public WorldsFolder {
        Objects.requireNonNull(savesDir, "savesDir");
        Objects.requireNonNull(backupsDir, "backupsDir");
        Objects.requireNonNull(minecraftDir, "minecraftDir");
        Objects.requireNonNull(reason, "reason");
    }

    /** True when the worlds come from the official Minecraft folder. */
    public boolean redirected() {
        return minecraftDir.isPresent();
    }

    /** The vanilla folders of the game directory, with a reason for the log. */
    public static WorldsFolder vanta(Path gameDir, String reason) {
        return new WorldsFolder(gameDir.resolve("saves"), gameDir.resolve("backups"), Optional.empty(), reason);
    }

    /**
     * Resolves the folders for this game from the real environment: the launcher's note in {@code gameDir}, the
     * environment variables, {@code os.name}, {@code user.home} and {@code vanta.worlds.folder}. Never throws; any
     * problem keeps the VANTA folder.
     */
    public static WorldsFolder resolveForThisGame(Path gameDir, WorldsFolderMode mode) {
        try {
            return resolve(gameDir, mode, MinecraftFolderHint.read(gameDir), System.getenv(),
                    System.getProperty("os.name", ""), Path.of(System.getProperty("user.home", ".")),
                    System.getProperty(PROPERTY));
        } catch (RuntimeException e) {
            return vanta(gameDir, "the worlds folder could not be resolved (" + e + ")");
        }
    }

    /**
     * Resolves the folders.
     *
     * @param gameDir  the game directory Minecraft runs in (the VANTA game folder)
     * @param mode     the "Singleplayer worlds" setting
     * @param hinted   the official folder recorded by the launcher, if any; it wins over the platform default
     * @param env      environment variables ({@code APPDATA} on Windows)
     * @param osName   {@code os.name}
     * @param userHome {@code user.home}
     * @param override value of {@code vanta.worlds.folder}, or {@code null}
     * @return the folders to use; never {@code null}
     */
    public static WorldsFolder resolve(Path gameDir, WorldsFolderMode mode, Optional<Path> hinted,
                                       Map<String, String> env, String osName, Path userHome, String override) {
        Objects.requireNonNull(gameDir, "gameDir");
        if (override != null && PROPERTY_VANTA.equalsIgnoreCase(override.trim())) {
            return vanta(gameDir, "the system property " + PROPERTY + "=" + PROPERTY_VANTA + " keeps the VANTA folder");
        }
        if (mode != WorldsFolderMode.MINECRAFT_FOLDER) {
            return vanta(gameDir, "the setting \"Singleplayer worlds\" is set to the VANTA folder");
        }
        Path official;
        String source;
        boolean platformDefault;
        try {
            platformDefault = hinted == null || hinted.isEmpty();
            if (!platformDefault) {
                official = hinted.get();
                source = "recorded by the VANTA launcher";
            } else {
                official = platformDefault(osName, env, userHome);
                source = "platform default";
            }
        } catch (InvalidPathException | NullPointerException e) {
            return vanta(gameDir, "the Minecraft folder could not be determined (" + e.getMessage() + ")");
        }
        if (!Files.exists(official)) {
            return vanta(gameDir, "the Minecraft folder " + official + " (" + source + ") does not exist");
        }
        if (!Files.isDirectory(official)) {
            return vanta(gameDir, "the Minecraft folder " + official + " (" + source + ") is not a directory");
        }
        Path saves = official.resolve("saves");
        if (!Files.exists(saves)) {
            return vanta(gameDir, "the Minecraft folder " + official + " (" + source + ") has no saves folder yet");
        }
        if (!Files.isDirectory(saves)) {
            return vanta(gameDir, saves + " is not a directory");
        }
        if (sameFile(official, gameDir) || sameFile(saves, gameDir.resolve("saves"))) {
            return vanta(gameDir, "the game already runs in the Minecraft folder " + official);
        }
        // Without the launcher's note the platform default is only a guess, so it is used for a VANTA Launcher
        // instance alone. Another launcher's instance (Prism, MultiMC, Modrinth App, CurseForge...) keeps its own
        // worlds, which its players expect to see.
        if (platformDefault && !isVantaLauncherInstance(gameDir)) {
            return vanta(gameDir, "the game folder " + gameDir + " is not a VANTA Launcher instance and no launcher "
                    + "note names a Minecraft folder, so this launcher's own worlds stay listed");
        }
        // Vanilla creates saves/ the first time it starts, so an empty one is common. Switching to it would hide the
        // worlds a player already made in the VANTA folder and show nothing in return (Singleplayer would open Create
        // New World again), so the VANTA folder stays while it is the only one with worlds.
        WorldCount officialWorlds = countWorlds(saves);
        if (officialWorlds == WorldCount.UNREADABLE) {
            return vanta(gameDir, "the saves folder " + saves + " (" + source + ") cannot be read");
        }
        if (officialWorlds == WorldCount.NONE && countWorlds(gameDir.resolve("saves")) == WorldCount.SOME) {
            return vanta(gameDir, "the Minecraft folder " + official + " (" + source + ") has no worlds yet and the "
                    + "VANTA folder has, so the VANTA folder's worlds stay listed");
        }
        Path absolute = official.toAbsolutePath().normalize();
        return new WorldsFolder(absolute.resolve("saves"), absolute.resolve("backups"), Optional.of(absolute),
                "using the worlds of the Minecraft folder " + absolute + " (" + source + ")");
    }

    /**
     * Where the official Minecraft Launcher keeps its data, the same rule as the launcher's
     * {@code LauncherPaths.officialMinecraftDirCandidate}: Windows {@code %APPDATA%\.minecraft} (or
     * {@code <home>\AppData\Roaming\.minecraft} when {@code APPDATA} is blank), macOS
     * {@code ~/Library/Application Support/minecraft}, Linux and others {@code ~/.minecraft}. Not checked for
     * existence.
     */
    public static Path platformDefault(String osName, Map<String, String> env, Path userHome) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = env == null ? null : env.get("APPDATA");
            Path base = appData != null && !appData.isBlank() ? Path.of(appData)
                    : userHome.resolve("AppData").resolve("Roaming");
            return base.resolve(".minecraft");
        }
        if (os.contains("mac") || os.contains("darwin") || os.contains("os x")) {
            return userHome.resolve("Library").resolve("Application Support").resolve("minecraft");
        }
        return userHome.resolve(".minecraft");
    }

    /** Folder names of the VANTA Launcher's default data directory ({@code LauncherPaths.defaultDataDir}). */
    private static final Set<String> LAUNCHER_DATA_DIR_NAMES = Set.of("VANTA Launcher", "vanta-launcher");

    /**
     * Whether {@code gameDir} is an instance of the VANTA Launcher: {@code <data dir>/instances/<id>}, where the data
     * directory has the launcher's default name or holds its {@code versions/vanta-client} folder (a data directory
     * moved with the launcher's home override).
     */
    static boolean isVantaLauncherInstance(Path gameDir) {
        Path abs = gameDir.toAbsolutePath().normalize();
        Path instances = abs.getParent();
        if (instances == null || instances.getFileName() == null
                || !"instances".equals(instances.getFileName().toString())) {
            return false;
        }
        Path data = instances.getParent();
        if (data == null || data.getFileName() == null) {
            return false;
        }
        return LAUNCHER_DATA_DIR_NAMES.contains(data.getFileName().toString())
                || Files.isDirectory(data.resolve("versions").resolve("vanta-client"));
    }

    private enum WorldCount { NONE, SOME, UNREADABLE }

    /**
     * Whether {@code saves} holds a world by vanilla's rule ({@code LevelStorageSource.findLevelCandidates}: a folder
     * with a {@code level.dat} or {@code level.dat_old}). A missing folder has none.
     */
    private static WorldCount countWorlds(Path saves) {
        if (!Files.isDirectory(saves)) {
            return WorldCount.NONE;
        }
        try (Stream<Path> dirs = Files.list(saves)) {
            return dirs.anyMatch(dir -> Files.isDirectory(dir) && (Files.isRegularFile(dir.resolve("level.dat"))
                    || Files.isRegularFile(dir.resolve("level.dat_old")))) ? WorldCount.SOME : WorldCount.NONE;
        } catch (IOException | UncheckedIOException | SecurityException e) {
            return WorldCount.UNREADABLE;
        }
    }

    /** Same file after resolving links; falls back to comparing normalised absolute paths. */
    private static boolean sameFile(Path a, Path b) {
        if (!Files.exists(b)) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
        try {
            return a.toRealPath().equals(b.toRealPath());
        } catch (IOException | SecurityException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }
}
