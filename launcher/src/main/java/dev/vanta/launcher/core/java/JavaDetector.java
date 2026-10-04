package dev.vanta.launcher.core.java;

import dev.vanta.launcher.core.model.MavenCoordinate;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Finds Java runtimes on the machine.
 *
 * <p>Candidates: an explicit override, {@code JAVA_HOME}, every {@code PATH} entry, our own {@code runtimes/},
 * and platform directories (Windows: Program Files vendors and the official launcher runtimes; macOS:
 * {@code /Library/Java/JavaVirtualMachines}; Linux: {@code /usr/lib/jvm}). Each candidate is probed with a
 * {@link JavaProbe}; {@link #pick(int)} prefers an exact major match, then the newest higher version.</p>
 */
public final class JavaDetector {

    /** Semantic ordering of version strings ({@code 21.0.4} &gt; {@code 21.0.4-beta} &gt; {@code 21.0.3}). */
    static final Comparator<String> VERSION_ORDER = MavenCoordinate::compareVersions;

    private final OsInfo os;
    private final Map<String, String> env;
    private final LauncherPaths paths;
    private final JavaProbe probe;
    private final List<Path> extraRoots;

    /**
     * @param os         host platform
     * @param env        environment variables
     * @param paths      launcher paths (for {@code runtimes/})
     * @param probe      probe
     * @param extraRoots additional directories whose children are treated as Java homes (tests, custom installs)
     */
    public JavaDetector(final OsInfo os, final Map<String, String> env, final LauncherPaths paths, final JavaProbe probe,
                        final List<Path> extraRoots) {
        this.os = Objects.requireNonNull(os, "os");
        this.env = Map.copyOf(Objects.requireNonNull(env, "env"));
        this.paths = Objects.requireNonNull(paths, "paths");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.extraRoots = List.copyOf(extraRoots);
    }

    /**
     * Production detector for the host.
     *
     * @param paths launcher paths
     * @return detector
     */
    public static JavaDetector forHost(final LauncherPaths paths) {
        return new JavaDetector(OsInfo.detect(), System.getenv(), paths, new ProcessJavaProbe(), List.of());
    }

    /**
     * Resolves the Java executable inside a home directory (handles macOS {@code Contents/Home}).
     *
     * @param home installation directory
     * @param os   platform
     * @return executable path (may not exist)
     */
    public static Path executableIn(final Path home, final OsInfo os) {
        final String exe = os.javaExecutableName();
        final Path direct = home.resolve("bin").resolve(exe);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        final Path mac = home.resolve("Contents").resolve("Home").resolve("bin").resolve(exe);
        if (Files.isRegularFile(mac)) {
            return mac;
        }
        final Path jre = home.resolve("jre").resolve("bin").resolve(exe);
        if (Files.isRegularFile(jre)) {
            return jre;
        }
        return direct;
    }

    /**
     * Normalises a user supplied path (home directory, {@code bin} directory or executable) to an executable.
     *
     * @param userPath path from settings or CLI
     * @return executable when the path points at something that looks like Java
     */
    public Optional<Path> executableFromUserPath(final Path userPath) {
        if (Files.isRegularFile(userPath)) {
            return Optional.of(userPath);
        }
        if (Files.isDirectory(userPath)) {
            final Path inBin = userPath.resolve(os.javaExecutableName());
            if (Files.isRegularFile(inBin)) {
                return Optional.of(inBin);
            }
            final Path exe = executableIn(userPath, os);
            if (Files.isRegularFile(exe)) {
                return Optional.of(exe);
            }
        }
        return Optional.empty();
    }

    /**
     * Collects candidate executables without probing them.
     *
     * @return distinct candidate executables that exist on disk
     */
    public List<Path> candidateExecutables() {
        final Set<Path> out = new LinkedHashSet<>();
        final String javaHome = env.get("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            addIfExists(out, executableIn(Path.of(javaHome), os));
        }
        final String pathVar = env.get("PATH");
        if (pathVar != null) {
            for (String dir : pathVar.split(java.util.regex.Pattern.quote(os.isWindows() ? ";" : ":"))) {
                if (!dir.isBlank()) {
                    addIfExists(out, Path.of(dir).resolve(os.javaExecutableName()));
                }
            }
        }
        for (Path root : roots()) {
            addChildren(out, root);
        }
        return new ArrayList<>(out);
    }

    /**
     * Probes all candidates.
     *
     * @return detected runtimes (one per distinct home), newest first
     */
    public List<JavaInstall> detect() {
        final Map<Path, JavaInstall> byHome = new java.util.LinkedHashMap<>();
        for (Path exe : candidateExecutables()) {
            probe.probe(exe).ifPresent(install -> byHome.putIfAbsent(install.home().toAbsolutePath().normalize(), install));
        }
        final List<JavaInstall> list = new ArrayList<>(byHome.values());
        list.sort(Comparator.comparingInt(JavaInstall::major).reversed()
            .thenComparing(JavaInstall::version, VERSION_ORDER.reversed()));
        return list;
    }

    /**
     * Picks the best runtime for a required major version: exact major first (newest build), then the lowest
     * higher major (a newer runtime is accepted but not preferred), 64-bit preferred.
     *
     * @param requiredMajor required major (21)
     * @return runtime when one satisfies the requirement
     */
    public Optional<JavaInstall> pick(final int requiredMajor) {
        return pick(detect(), requiredMajor);
    }

    /**
     * Picks from a given list with the same preference rules as {@link #pick(int)}.
     *
     * @param installs      runtimes
     * @param requiredMajor required major
     * @return best match
     */
    public static Optional<JavaInstall> pick(final List<JavaInstall> installs, final int requiredMajor) {
        return installs.stream()
            .filter(j -> j.satisfies(requiredMajor))
            .min(Comparator
                .comparingInt((JavaInstall j) -> j.major() == requiredMajor ? 0 : 1)
                .thenComparingInt(j -> j.is64Bit() ? 0 : 1)
                .thenComparingInt(JavaInstall::major)
                .thenComparing(JavaInstall::version, VERSION_ORDER.reversed()));
    }

    /**
     * Probes one explicit path (settings override or {@code --java}).
     *
     * @param userPath path to a Java home, bin directory or executable
     * @return runtime when valid
     */
    public Optional<JavaInstall> probeUserPath(final Path userPath) {
        return executableFromUserPath(userPath).flatMap(probe::probe);
    }

    private List<Path> roots() {
        final List<Path> roots = new ArrayList<>(extraRoots);
        roots.add(paths.runtimesDir());
        if (os.isWindows()) {
            final String programFiles = env.getOrDefault("ProgramFiles", "C:\\Program Files");
            final String programFilesX86 = env.getOrDefault("ProgramFiles(x86)", "C:\\Program Files (x86)");
            for (String vendor : List.of("Java", "Eclipse Adoptium", "Eclipse Foundation", "Microsoft", "Zulu", "BellSoft",
                "Amazon Corretto", "AdoptOpenJDK", "Azul", "Semeru", "SapMachine", "Temurin")) {
                roots.add(Path.of(programFiles, vendor));
                roots.add(Path.of(programFilesX86, vendor));
            }
            final String localAppData = env.get("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                roots.add(Path.of(localAppData, "Packages", "Microsoft.4297127D64EC6_8wekyb3d8bbwe", "LocalCache", "Local", "runtime"));
            }
            roots.add(Path.of(programFilesX86, "Minecraft Launcher", "runtime"));
            roots.add(Path.of(programFiles, "Minecraft Launcher", "runtime"));
        } else if (os.isMac()) {
            roots.add(Path.of("/Library/Java/JavaVirtualMachines"));
            roots.add(Path.of(System.getProperty("user.home", "~"), "Library", "Java", "JavaVirtualMachines"));
            roots.add(Path.of("/opt/homebrew/opt"));
            roots.add(Path.of("/usr/local/opt"));
        } else {
            roots.add(Path.of("/usr/lib/jvm"));
            roots.add(Path.of("/usr/lib64/jvm"));
            roots.add(Path.of("/usr/java"));
            roots.add(Path.of("/opt/java"));
            roots.add(Path.of("/opt/jdk"));
            roots.add(Path.of(System.getProperty("user.home", "~"), ".sdkman", "candidates", "java"));
            roots.add(Path.of(System.getProperty("user.home", "~"), ".jdks"));
        }
        return roots;
    }

    private void addChildren(final Set<Path> out, final Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        // The root itself may be a Java home (e.g. a user pointed a custom root at one installation).
        addIfExists(out, executableIn(root, os));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                addIfExists(out, executableIn(child, os));
                // Official launcher layout: runtime/<component>/<platform>/<component>/bin/java
                try (DirectoryStream<Path> grandChildren = Files.newDirectoryStream(child)) {
                    for (Path gc : grandChildren) {
                        if (Files.isDirectory(gc)) {
                            addIfExists(out, executableIn(gc, os));
                            try (DirectoryStream<Path> ggc = Files.newDirectoryStream(gc)) {
                                for (Path g : ggc) {
                                    if (Files.isDirectory(g)) {
                                        addIfExists(out, executableIn(g, os));
                                    }
                                }
                            } catch (IOException ignored) {
                                // unreadable; skip
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // unreadable; skip
                }
            }
        } catch (IOException ignored) {
            // unreadable root; skip
        }
    }

    private static void addIfExists(final Set<Path> out, final Path exe) {
        if (Files.isRegularFile(exe)) {
            try {
                out.add(exe.toRealPath());
            } catch (IOException e) {
                out.add(exe.toAbsolutePath().normalize());
            }
        }
    }
}
