package dev.vanta.core.modrinth;

import dev.vanta.core.config.CoreLog;
import dev.vanta.core.config.JsonStore;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The local side of the Modrinth integration: the {@link ModrinthIndex} file and the content folders of one game
 * directory. It lists what is installed (including files VANTA did not install, by file name only), removes projects
 * VANTA installed and switches mods on and off by renaming {@code .jar} ↔ {@code .jar.disabled}.
 * <p>
 * Index updates re-read the file first ({@link #update(Function)}) so changes the launcher made meanwhile are kept.
 * Only the two paths an index entry names ({@code file} and {@code file.disabled}) are ever deleted, and only inside
 * {@code mods/}, {@code shaderpacks/} or {@code resourcepacks/}.
 */
public final class ModrinthLibrary {
    /** Suffix of a download that is verified but not yet committed (Fabric ignores it). */
    public static final String STAGING_SUFFIX = ".vanta-download";

    private final Path gameDir;
    private final Path indexFile;
    private final JsonStore store;

    /**
     * @param gameDir the Minecraft game directory ({@code .minecraft} or the instance folder)
     * @param store   JSON persistence (atomic writes, corrupt files moved aside)
     */
    public ModrinthLibrary(Path gameDir, JsonStore store) {
        this.gameDir = Objects.requireNonNull(gameDir, "gameDir").toAbsolutePath().normalize();
        this.store = Objects.requireNonNull(store, "store");
        this.indexFile = indexFile(this.gameDir);
    }

    /** {@code <gameDir>/config/vanta/modrinth.json}. */
    public static Path indexFile(Path gameDir) {
        return gameDir.resolve("config").resolve("vanta").resolve("modrinth.json");
    }

    public Path gameDir() {
        return gameDir;
    }

    public Path indexFile() {
        return indexFile;
    }

    /** Folder of a project type. */
    public Path directory(ModrinthProjectType type) {
        return gameDir.resolve(type.directory());
    }

    /** Reads the index from disk. */
    public synchronized ModrinthIndex index() {
        return ModrinthIndex.read(store, indexFile);
    }

    /** Re-reads the index, applies {@code mutation} and writes the result. */
    public synchronized <T> T update(Function<ModrinthIndex, T> mutation) {
        ModrinthIndex index = index();
        T result = mutation.apply(index);
        index.write(store, indexFile);
        return result;
    }

    // ---- listing -------------------------------------------------------------------------------------------------

    /** Everything installed: index entries first (by type, then name), then files found on disk (by type, then name). */
    public List<LocalItem> items() {
        ModrinthIndex index = index();
        List<LocalItem> managed = new ArrayList<>();
        for (InstalledEntry entry : index.entries()) {
            ModrinthProjectType type = entry.projectType().orElseGet(() -> typeOfFile(entry.file()));
            boolean enabledExists = Files.exists(entry.enabledPath(gameDir));
            boolean disabledExists = Files.exists(entry.disabledPath(gameDir));
            LocalItem.State state;
            boolean enabled;
            if (entry.enabled() ? enabledExists : disabledExists) {
                state = LocalItem.State.INSTALLED;
                enabled = entry.enabled();
            } else if (enabledExists || disabledExists) {
                state = LocalItem.State.INSTALLED;
                enabled = enabledExists;
            } else {
                state = LocalItem.State.MISSING;
                enabled = entry.enabled();
            }
            String relative = enabled ? entry.file() : entry.file() + InstalledEntry.DISABLED_SUFFIX;
            managed.add(new LocalItem(Optional.of(entry), type, entry.title(), relative, enabled, state));
        }
        List<LocalItem> manual = new ArrayList<>();
        for (ModrinthProjectType type : ModrinthProjectType.values()) {
            for (Path path : list(directory(type))) {
                String name = path.getFileName().toString();
                if (!isContent(type, path, name)) {
                    continue;
                }
                boolean enabled = !name.endsWith(InstalledEntry.DISABLED_SUFFIX);
                String relative = type.directory() + "/" + name;
                if (index.findByFile(relative).isPresent()) {
                    continue;
                }
                manual.add(new LocalItem(Optional.empty(), type, name, relative, enabled, LocalItem.State.MANUAL));
            }
        }
        Comparator<LocalItem> order = Comparator.comparing((LocalItem i) -> i.type().ordinal())
                .thenComparing(i -> i.name().toLowerCase(Locale.ROOT));
        managed.sort(order);
        manual.sort(order);
        List<LocalItem> out = new ArrayList<>(managed);
        out.addAll(manual);
        return out;
    }

    /** Mod jars in {@code mods/} that are not in the index (candidates for a hash lookup). */
    public List<Path> unknownModJars() {
        List<Path> out = new ArrayList<>();
        for (LocalItem item : items()) {
            if (item.state() == LocalItem.State.MANUAL && item.type() == ModrinthProjectType.MOD) {
                out.add(gameDir.resolve(item.relativeFile()));
            }
        }
        return out;
    }

    /** True when an enabled {@code fabric-api*.jar} is in {@code mods/}. */
    public boolean hasFabricApiJar() {
        for (Path path : list(directory(ModrinthProjectType.MOD))) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.startsWith("fabric-api") && name.endsWith(".jar")) {
                return true;
            }
        }
        return false;
    }

    /** True when the project is in the index. */
    public boolean isInstalled(String projectId) {
        return index().contains(projectId);
    }

    private static boolean isContent(ModrinthProjectType type, Path path, String name) {
        if (name.startsWith(".") || name.endsWith(STAGING_SUFFIX) || name.endsWith(".part")) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (type == ModrinthProjectType.MOD) {
            return Files.isRegularFile(path) && (lower.endsWith(".jar") || lower.endsWith(".jar.disabled"));
        }
        if (Files.isDirectory(path)) {
            return true;
        }
        return Files.isRegularFile(path) && (lower.endsWith(".zip") || lower.endsWith(".zip.disabled"));
    }

    private static List<Path> list(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                out.add(path);
            }
        } catch (IOException e) {
            CoreLog.warn(e, "Could not list {}", dir);
        }
        return out;
    }

    /** Type of a relative path by its folder (mods when unknown). */
    static ModrinthProjectType typeOfFile(String relative) {
        for (ModrinthProjectType type : ModrinthProjectType.values()) {
            if (relative.startsWith(type.directory() + "/")) {
                return type;
            }
        }
        return ModrinthProjectType.MOD;
    }

    // ---- changes -------------------------------------------------------------------------------------------------

    /**
     * Deletes the files of a project VANTA installed and removes it from the index (and from every
     * {@code requiredBy} list).
     *
     * @return the removed entry
     */
    public synchronized InstalledEntry remove(String projectId) throws ModrinthException {
        InstalledEntry entry = index().find(projectId).orElseThrow(() ->
                new ModrinthException(ModrinthException.Kind.NOT_FOUND, projectId + " is not installed by VANTA"));
        Path enabled = checkedPath(entry.enabledPath(gameDir));
        Path disabled = checkedPath(entry.disabledPath(gameDir));
        try {
            Files.deleteIfExists(enabled);
            Files.deleteIfExists(disabled);
        } catch (IOException e) {
            throw new ModrinthException(ModrinthException.Kind.LOCAL_IO, "could not delete " + entry.file(), e);
        }
        update(index -> index.remove(projectId));
        CoreLog.info("Removed {} ({})", entry.title(), entry.file());
        return entry;
    }

    /** Removes a project from the index without touching files (entries whose file is already gone). */
    public synchronized void forget(String projectId) {
        update(index -> index.remove(projectId));
    }

    /**
     * Enables or disables a mod by renaming {@code <file>} ↔ {@code <file>.disabled}.
     *
     * @return the updated entry
     */
    public synchronized InstalledEntry setEnabled(String projectId, boolean enabled) throws ModrinthException {
        InstalledEntry entry = index().find(projectId).orElseThrow(() ->
                new ModrinthException(ModrinthException.Kind.NOT_FOUND, projectId + " is not installed by VANTA"));
        if (!entry.projectType().map(ModrinthProjectType::canDisable).orElse(false)) {
            throw new ModrinthException(ModrinthException.Kind.UNSAFE_FILE,
                    entry.title() + " is not a mod and cannot be disabled by renaming");
        }
        Path on = checkedPath(entry.enabledPath(gameDir));
        Path off = checkedPath(entry.disabledPath(gameDir));
        Path from = enabled ? off : on;
        Path to = enabled ? on : off;
        try {
            if (Files.exists(from)) {
                if (Files.exists(to)) {
                    throw new ModrinthException(ModrinthException.Kind.FILE_EXISTS, to.getFileName() + " already exists");
                }
                move(from, to);
            } else if (!Files.exists(to)) {
                throw new ModrinthException(ModrinthException.Kind.NOT_FOUND, entry.file() + " is missing");
            }
        } catch (ModrinthException e) {
            throw e;
        } catch (IOException e) {
            throw new ModrinthException(ModrinthException.Kind.LOCAL_IO, "could not rename " + entry.file(), e);
        }
        InstalledEntry updated = entry.withEnabled(enabled);
        update(index -> {
            index.put(updated);
            return null;
        });
        return updated;
    }

    /** Ensures a path lies inside one of the content folders of the game directory. */
    Path checkedPath(Path path) throws ModrinthException {
        Path normal = path.toAbsolutePath().normalize();
        for (ModrinthProjectType type : ModrinthProjectType.values()) {
            Path dir = directory(type);
            if (normal.startsWith(dir) && !normal.equals(dir)) {
                return normal;
            }
        }
        throw new ModrinthException(ModrinthException.Kind.UNSAFE_FILE, path + " is outside the content folders");
    }

    static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to);
        }
    }
}
