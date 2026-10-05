package dev.vanta.launcher.core.install;

import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.core.util.Sleeper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The official Minecraft Launcher as a running program: is it open (it reads {@code launcher_profiles.json} only when it
 * starts, and often stays in the system tray after its window was closed), and how to start it. The launcher never
 * stops or kills it; the player closes it.
 *
 * <p>Process names and install locations, with sources:</p>
 * <ul>
 *   <li>Windows, launcher from minecraft.net: {@code MinecraftLauncher.exe}, installed to
 *       {@code %ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe} (Minecraft Wiki, "Minecraft Launcher":
 *       "Minecraft Launcher\MinecraftLauncher.exe on Windows"; Microsoft Q&amp;A 3870057: "cd "C:\Program Files
 *       (x86)\Minecraft Launcher"" then "MinecraftLauncher.exe"; echotrail.io/insights/minecraftlauncher.exe: "typically
 *       runs from C:\Program Files (x86)\Minecraft Launcher").</li>
 *   <li>Windows, launcher from the Microsoft Store / Xbox app: package {@code Microsoft.4297127D64EC6} whose program is
 *       {@code Minecraft.exe} (Microsoft Q&amp;A 3831475 and 4024949:
 *       "windowsapps/microsoft.4297127D64EC6-..._x64_8wekyb3d8bbwe/Minecraft.exe"; Bedrock Edition itself is
 *       {@code Minecraft.Windows.exe}, a different name). It is started through its application user model id
 *       {@value #STORE_APP_ID} with {@code explorer.exe shell:AppsFolder\...}.</li>
 *   <li>macOS: {@code /Applications/Minecraft.app/Contents/MacOS/launcher} (Minecraft Wiki; Mojang bug reports MCL-21264
 *       and macOS crash reports "Path: /Applications/Minecraft.app/Contents/MacOS/launcher, Identifier:
 *       com.mojang.minecraftlauncher"), opened with {@code open -a Minecraft}; a process named
 *       {@code Minecraft Launcher} counts as well.</li>
 *   <li>Linux: {@code minecraft-launcher} (Minecraft Wiki), started from the {@code PATH}.</li>
 * </ul>
 */
public final class OfficialLauncher {

    /** Application user model id of the Minecraft Launcher from the Microsoft Store / Xbox app. */
    public static final String STORE_APP_ID = "Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft";
    /** Package family name of the Minecraft Launcher from the Microsoft Store / Xbox app. */
    public static final String STORE_PACKAGE_FAMILY = "Microsoft.4297127D64EC6_8wekyb3d8bbwe";
    /** How long {@link #open()} waits for the launcher process to appear. */
    public static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(15);

    private static final Logger LOG = LauncherLog.get("OfficialLauncher");
    private static final Duration POLL = Duration.ofMillis(500);

    /** A process as seen by the detection: its id and executable path (or image name). */
    public record ProcessEntry(long pid, String command) {
    }

    /**
     * A running Minecraft Launcher process.
     *
     * @param pid  process id
     * @param name executable name, e.g. {@code MinecraftLauncher.exe}
     */
    public record Running(long pid, String name) {

        /** @return e.g. {@code MinecraftLauncher.exe (process 1234)} */
        public String describe() {
            return name + " (process " + pid + ")";
        }
    }

    /** How {@link #open()} ended. */
    public enum Outcome {
        /** Started, and a launcher process is running now. */
        OPENED,
        /** Started, but no launcher process appeared within {@link #CONFIRM_TIMEOUT}. */
        STARTED_UNCONFIRMED,
        /** No Minecraft Launcher was found on this computer. */
        NOT_FOUND,
        /** Starting it failed. */
        FAILED
    }

    /**
     * Result of {@link #open()}.
     *
     * @param outcome outcome
     * @param method  how it was started (command line), empty when nothing was started
     * @param detail  English explanation for logs and the command line
     */
    public record OpenResult(Outcome outcome, String method, String detail) {

        /** @return whether the launcher was started */
        public boolean started() {
            return outcome == Outcome.OPENED || outcome == Outcome.STARTED_UNCONFIRMED;
        }
    }

    /** The operating system as far as this class needs it; tests replace it. */
    public interface Host {
        /** @return running processes */
        List<ProcessEntry> processes();

        /**
         * @param file file
         * @return whether it is a regular file
         */
        boolean isFile(Path file);

        /**
         * @param dir directory
         * @return whether it is a directory
         */
        boolean isDirectory(Path dir);

        /**
         * Starts a program without waiting for it.
         *
         * @param command command line
         * @throws IOException when it cannot be started
         */
        void start(List<String> command) throws IOException;

        /**
         * Runs a program to its end.
         *
         * @param command command line
         * @param timeout how long to wait
         * @return exit code, or empty when it did not finish in time
         * @throws IOException when it cannot be started
         */
        Optional<Integer> run(List<String> command, Duration timeout) throws IOException, InterruptedException;
    }

    private final OsInfo os;
    private final Map<String, String> env;
    private final Path userHome;
    private final Host host;
    private final Sleeper sleeper;

    /**
     * @param os       host platform
     * @param env      environment variables
     * @param userHome user home
     * @param host     processes and files
     * @param sleeper  sleeper for the start confirmation
     */
    public OfficialLauncher(final OsInfo os, final Map<String, String> env, final Path userHome, final Host host, final Sleeper sleeper) {
        this.os = Objects.requireNonNull(os, "os");
        this.env = Map.copyOf(Objects.requireNonNull(env, "env"));
        this.userHome = Objects.requireNonNull(userHome, "userHome");
        this.host = Objects.requireNonNull(host, "host");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /**
     * @param os       host platform
     * @param env      environment variables
     * @param userHome user home
     * @return the launcher detection for this computer
     */
    public static OfficialLauncher system(final OsInfo os, final Map<String, String> env, final Path userHome) {
        return new OfficialLauncher(os, env, userHome, new SystemHost(os), Sleeper.REAL);
    }

    // ---------------------------------------------------------------- detection

    /**
     * @return the Minecraft Launcher processes that are running now (empty when none)
     */
    public List<Running> running() {
        final Map<Long, Running> found = new LinkedHashMap<>();
        for (ProcessEntry p : host.processes()) {
            if (p.command() == null || p.command().isBlank()) {
                continue;
            }
            if (matches(os, p.command())) {
                found.putIfAbsent(p.pid(), new Running(p.pid(), baseName(p.command())));
            }
        }
        return List.copyOf(found.values());
    }

    /**
     * @param os      platform
     * @param command executable path or image name of a process
     * @return whether it is the official Minecraft Launcher (see the class description for the names)
     */
    public static boolean matches(final OsInfo os, final String command) {
        final String normalized = command.replace('\\', '/');
        final String base = baseName(normalized).toLowerCase(Locale.ROOT);
        if (os.isWindows()) {
            return base.equals("minecraftlauncher.exe") || base.equals("minecraft.exe");
        }
        if (os.isMac()) {
            return normalized.toLowerCase(Locale.ROOT).endsWith("/minecraft.app/contents/macos/launcher") || base.equals("minecraft launcher");
        }
        return base.equals("minecraft-launcher");
    }

    private static String baseName(final String command) {
        final String normalized = command.replace('\\', '/');
        final int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    // ---------------------------------------------------------------- start

    /**
     * @return where the Minecraft Launcher from minecraft.net is installed on Windows (candidates, existing or not)
     */
    public List<Path> classicWindowsExecutables() {
        final List<Path> out = new ArrayList<>();
        final String x86 = env.get("ProgramFiles(x86)");
        if (x86 != null && !x86.isBlank()) {
            out.add(Path.of(x86, "Minecraft Launcher", "MinecraftLauncher.exe"));
        }
        return out;
    }

    /**
     * @param minecraftDir the official Minecraft directory
     * @return whether the Minecraft Launcher from the Microsoft Store / Xbox app is (or was) installed for this user:
     *     its package folder exists, or it wrote {@code launcher_profiles_microsoft_store.json}
     */
    public boolean storeLauncherInstalled(final Path minecraftDir) {
        final String local = env.get("LOCALAPPDATA");
        final Path localAppData = local != null && !local.isBlank() ? Path.of(local) : userHome.resolve("AppData").resolve("Local");
        return host.isDirectory(localAppData.resolve("Packages").resolve(STORE_PACKAGE_FAMILY))
            || host.isFile(minecraftDir.resolve(OfficialProfileService.STORE_PROFILES_FILE));
    }

    /**
     * Starts the Minecraft Launcher: on Windows {@code MinecraftLauncher.exe} from its install folder when present,
     * else the Microsoft Store / Xbox app launcher through {@code explorer.exe shell:AppsFolder\}{@value #STORE_APP_ID};
     * on macOS {@code open -a Minecraft}; on Linux {@code minecraft-launcher} from the {@code PATH}. Afterwards it waits up
     * to {@link #CONFIRM_TIMEOUT} for a launcher process. Never stops a running launcher.
     *
     * @param minecraftDir the official Minecraft directory (tells whether the Store launcher was used)
     * @return result
     * @throws InterruptedException when interrupted
     */
    public OpenResult open(final Path minecraftDir) throws InterruptedException {
        final List<String> command;
        if (os.isWindows()) {
            final Optional<Path> classic = classicWindowsExecutables().stream().filter(host::isFile).findFirst();
            if (classic.isPresent()) {
                command = List.of(classic.get().toString());
            } else if (storeLauncherInstalled(minecraftDir)) {
                command = List.of("explorer.exe", "shell:AppsFolder\\" + STORE_APP_ID);
            } else {
                return new OpenResult(Outcome.NOT_FOUND, "", "No Minecraft Launcher was found: neither "
                    + classicWindowsExecutables().stream().map(Path::toString).findFirst().orElse("MinecraftLauncher.exe")
                    + " nor the Minecraft Launcher app from the Microsoft Store or Xbox app. Install it from minecraft.net.");
            }
        } else if (os.isMac()) {
            command = List.of("open", "-a", "Minecraft");
            try {
                final Optional<Integer> code = host.run(command, Duration.ofSeconds(20));
                if (code.isPresent() && code.get() != 0) {
                    return new OpenResult(Outcome.NOT_FOUND, String.join(" ", command), "macOS could not open the application 'Minecraft'"
                        + " (open exited with " + code.get() + "). Install the Minecraft Launcher from minecraft.net.");
                }
            } catch (IOException e) {
                return new OpenResult(Outcome.FAILED, String.join(" ", command), "Could not run open: " + e.getMessage());
            }
            return confirm(command);
        } else {
            final Optional<Path> onPath = findOnPath("minecraft-launcher");
            if (onPath.isEmpty()) {
                return new OpenResult(Outcome.NOT_FOUND, "", "minecraft-launcher was not found on the PATH. Install the Minecraft Launcher"
                    + " from minecraft.net (Minecraft.deb or Minecraft.tar.gz) and start it once.");
            }
            command = List.of(onPath.get().toString());
        }
        try {
            host.start(command);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not start the Minecraft Launcher", e);
            return new OpenResult(Outcome.FAILED, String.join(" ", command), "Could not start " + String.join(" ", command) + ": " + e.getMessage());
        }
        return confirm(command);
    }

    private OpenResult confirm(final List<String> command) throws InterruptedException {
        final String method = String.join(" ", command);
        final long polls = Math.max(1, CONFIRM_TIMEOUT.toMillis() / POLL.toMillis());
        for (long i = 0; i < polls; i++) {
            final List<Running> now = running();
            if (!now.isEmpty()) {
                LOG.log(Level.INFO, "Minecraft Launcher started: {0}", now.get(0).describe());
                return new OpenResult(Outcome.OPENED, method, "The Minecraft Launcher is running: " + now.get(0).describe());
            }
            sleeper.sleep(POLL);
        }
        return new OpenResult(Outcome.STARTED_UNCONFIRMED, method, "Started " + method + ", but no Minecraft Launcher window was detected within "
            + CONFIRM_TIMEOUT.toSeconds() + " seconds. If nothing opens, start the Minecraft Launcher yourself.");
    }

    private Optional<Path> findOnPath(final String program) {
        final String path = env.getOrDefault("PATH", "");
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            final Path candidate = Path.of(dir, program);
            if (host.isFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- real host

    /** Processes from {@link ProcessHandle} (plus {@code tasklist} on Windows), files from {@link Files}. */
    static final class SystemHost implements Host {

        private final OsInfo os;

        SystemHost(final OsInfo os) {
            this.os = os;
        }

        @Override
        public List<ProcessEntry> processes() {
            final List<ProcessEntry> out = new ArrayList<>();
            ProcessHandle.allProcesses().forEach(ph -> ph.info().command().ifPresent(c -> out.add(new ProcessEntry(ph.pid(), c))));
            if (os.isWindows()) {
                // ProcessHandle cannot read the executable path of some processes (packaged Store apps, other
                // integrity levels); tasklist lists the image name of every process of every user.
                out.addAll(tasklist());
            }
            return out;
        }

        private static List<ProcessEntry> tasklist() {
            final List<ProcessEntry> out = new ArrayList<>();
            try {
                final Process p = new ProcessBuilder("tasklist", "/FO", "CSV", "/NH").redirectErrorStream(true).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        parseTasklistLine(line).ifPresent(out::add);
                    }
                }
                if (!p.waitFor(10, TimeUnit.SECONDS)) {
                    p.destroy();
                }
            } catch (IOException e) {
                LOG.log(Level.FINE, "tasklist failed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return out;
        }

        @Override
        public boolean isFile(final Path file) {
            return Files.isRegularFile(file);
        }

        @Override
        public boolean isDirectory(final Path dir) {
            return Files.isDirectory(dir);
        }

        @Override
        public void start(final List<String> command) throws IOException {
            new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        }

        @Override
        public Optional<Integer> run(final List<String> command, final Duration timeout) throws IOException, InterruptedException {
            final Process p = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS) ? Optional.of(p.exitValue()) : Optional.empty();
        }
    }

    /**
     * @param line one line of {@code tasklist /FO CSV /NH}: {@code "MinecraftLauncher.exe","1234","Console","1","123,456 K"}
     * @return image name and process id
     */
    static Optional<ProcessEntry> parseTasklistLine(final String line) {
        final String trimmed = line.trim();
        if (!trimmed.startsWith("\"")) {
            return Optional.empty();
        }
        final String[] cells = trimmed.substring(1).split("\",\"", -1);
        if (cells.length < 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ProcessEntry(Long.parseLong(cells[1].replace("\"", "").trim()), cells[0]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
