package dev.vanta.launcher.core.install;

import dev.vanta.launcher.core.util.OsInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Detecting and starting the official Minecraft Launcher with a scripted operating system. It is never stopped.
 */
class OfficialLauncherTest {

    private static final OsInfo WINDOWS = new OsInfo("windows", "x64", "10.0");
    private static final OsInfo MAC = new OsInfo("osx", "arm64", "15.0");
    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8");
    private static final Path HOME = Path.of("/home/player");

    /** Scripted host: process list, files, started commands. */
    private static final class FakeHost implements OfficialLauncher.Host {
        final List<OfficialLauncher.ProcessEntry> processes = new ArrayList<>();
        final Set<Path> files = new HashSet<>();
        final Set<Path> dirs = new HashSet<>();
        final List<List<String>> started = new ArrayList<>();
        final List<List<String>> ran = new ArrayList<>();
        int exitCode;
        boolean startShowsLauncher = true;
        IOException startFailure;

        @Override
        public List<OfficialLauncher.ProcessEntry> processes() {
            return List.copyOf(processes);
        }

        @Override
        public boolean isFile(final Path file) {
            return files.contains(file);
        }

        @Override
        public boolean isDirectory(final Path dir) {
            return dirs.contains(dir);
        }

        @Override
        public void start(final List<String> command) throws IOException {
            if (startFailure != null) {
                throw startFailure;
            }
            started.add(command);
            if (startShowsLauncher) {
                processes.add(new OfficialLauncher.ProcessEntry(4242, command.get(0).endsWith("explorer.exe") ? "Minecraft.exe" : command.get(0)));
            }
        }

        @Override
        public Optional<Integer> run(final List<String> command, final Duration timeout) {
            ran.add(command);
            if (exitCode == 0) {
                processes.add(new OfficialLauncher.ProcessEntry(77, "/Applications/Minecraft.app/Contents/MacOS/launcher"));
            }
            return Optional.of(exitCode);
        }
    }

    private static OfficialLauncher launcher(final OsInfo os, final Map<String, String> env, final FakeHost host) {
        return new OfficialLauncher(os, env, HOME, host, d -> { });
    }

    @Test
    void recognisesTheLauncherProcessesOfEachSystem() {
        assertTrue(OfficialLauncher.matches(WINDOWS, "C:\\Program Files (x86)\\Minecraft Launcher\\MinecraftLauncher.exe"));
        assertTrue(OfficialLauncher.matches(WINDOWS, "C:\\Program Files\\WindowsApps\\Microsoft.4297127D64EC6_1.7.2.0_x64__8wekyb3d8bbwe\\Minecraft.exe"));
        assertTrue(OfficialLauncher.matches(WINDOWS, "MINECRAFTLAUNCHER.EXE"), "tasklist image names, any case");
        assertFalse(OfficialLauncher.matches(WINDOWS, "C:\\XboxGames\\Minecraft for Windows\\Content\\Minecraft.Windows.exe"), "Bedrock Edition");
        assertFalse(OfficialLauncher.matches(WINDOWS, "C:\\Program Files\\Java\\bin\\javaw.exe"), "the game itself");
        assertFalse(OfficialLauncher.matches(WINDOWS, "C:\\Users\\p\\AppData\\Local\\VANTA Launcher\\VANTA Launcher.exe"));
        assertTrue(OfficialLauncher.matches(MAC, "/Applications/Minecraft.app/Contents/MacOS/launcher"));
        assertTrue(OfficialLauncher.matches(MAC, "/Users/p/Applications/Minecraft.app/Contents/MacOS/launcher"));
        assertTrue(OfficialLauncher.matches(MAC, "/Applications/Minecraft Launcher.app/Contents/MacOS/Minecraft Launcher"));
        assertFalse(OfficialLauncher.matches(MAC, "/usr/libexec/launcher"));
        assertTrue(OfficialLauncher.matches(LINUX, "/opt/minecraft-launcher/minecraft-launcher"));
        assertFalse(OfficialLauncher.matches(LINUX, "/usr/bin/minecraft"));
    }

    @Test
    void runningListsEachLauncherProcessOnce() {
        final FakeHost host = new FakeHost();
        host.processes.add(new OfficialLauncher.ProcessEntry(10, "C:\\Windows\\explorer.exe"));
        host.processes.add(new OfficialLauncher.ProcessEntry(1234, "C:\\Program Files (x86)\\Minecraft Launcher\\MinecraftLauncher.exe"));
        host.processes.add(new OfficialLauncher.ProcessEntry(1234, "MinecraftLauncher.exe"));
        host.processes.add(new OfficialLauncher.ProcessEntry(99, ""));
        final List<OfficialLauncher.Running> running = launcher(WINDOWS, Map.of(), host).running();
        assertEquals(1, running.size());
        assertEquals("MinecraftLauncher.exe (process 1234)", running.get(0).describe());
        assertTrue(launcher(WINDOWS, Map.of(), new FakeHost()).running().isEmpty());
    }

    @Test
    void windowsPrefersTheClassicLauncherThenTheStoreApp() throws Exception {
        final Map<String, String> env = Map.of("ProgramFiles(x86)", "C:\\Program Files (x86)", "LOCALAPPDATA", "C:\\Users\\p\\AppData\\Local");
        final Path classic = Path.of("C:\\Program Files (x86)", "Minecraft Launcher", "MinecraftLauncher.exe");
        final Path mc = Path.of("C:\\Users\\p\\AppData\\Roaming\\.minecraft");

        final FakeHost both = new FakeHost();
        both.files.add(classic);
        both.dirs.add(Path.of("C:\\Users\\p\\AppData\\Local", "Packages", OfficialLauncher.STORE_PACKAGE_FAMILY));
        final OfficialLauncher.OpenResult opened = launcher(WINDOWS, env, both).open(mc);
        assertEquals(OfficialLauncher.Outcome.OPENED, opened.outcome());
        assertEquals(List.of(List.of(classic.toString())), both.started);

        final FakeHost store = new FakeHost();
        store.dirs.add(Path.of("C:\\Users\\p\\AppData\\Local", "Packages", OfficialLauncher.STORE_PACKAGE_FAMILY));
        assertEquals(OfficialLauncher.Outcome.OPENED, launcher(WINDOWS, env, store).open(mc).outcome());
        assertEquals(List.of(List.of("explorer.exe", "shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft")), store.started);

        final FakeHost storeProfilesOnly = new FakeHost();
        storeProfilesOnly.files.add(mc.resolve(OfficialProfileService.STORE_PROFILES_FILE));
        storeProfilesOnly.startShowsLauncher = false;
        final OfficialLauncher.OpenResult unconfirmed = launcher(WINDOWS, env, storeProfilesOnly).open(mc);
        assertEquals(OfficialLauncher.Outcome.STARTED_UNCONFIRMED, unconfirmed.outcome());
        assertTrue(unconfirmed.started());

        final FakeHost none = new FakeHost();
        final OfficialLauncher.OpenResult missing = launcher(WINDOWS, env, none).open(mc);
        assertEquals(OfficialLauncher.Outcome.NOT_FOUND, missing.outcome());
        assertTrue(missing.detail().contains("minecraft.net"), missing.detail());
        assertTrue(none.started.isEmpty(), "nothing is started blindly");

        final FakeHost broken = new FakeHost();
        broken.files.add(classic);
        broken.startFailure = new IOException("Access is denied");
        final OfficialLauncher.OpenResult failed = launcher(WINDOWS, env, broken).open(mc);
        assertEquals(OfficialLauncher.Outcome.FAILED, failed.outcome());
        assertTrue(failed.detail().contains("Access is denied"));
    }

    @Test
    void macOsOpensTheAppAndReportsAMissingOne() throws Exception {
        final FakeHost host = new FakeHost();
        assertEquals(OfficialLauncher.Outcome.OPENED, launcher(MAC, Map.of(), host).open(HOME).outcome());
        assertEquals(List.of(List.of("open", "-a", "Minecraft")), host.ran);
        final FakeHost missing = new FakeHost();
        missing.exitCode = 1;
        assertEquals(OfficialLauncher.Outcome.NOT_FOUND, launcher(MAC, Map.of(), missing).open(HOME).outcome());
    }

    @Test
    void linuxStartsMinecraftLauncherFromThePath() throws Exception {
        final FakeHost host = new FakeHost();
        host.files.add(Path.of("/usr/bin/minecraft-launcher"));
        final Map<String, String> env = Map.of("PATH", "/usr/local/bin" + java.io.File.pathSeparator + "/usr/bin");
        assertEquals(OfficialLauncher.Outcome.OPENED, launcher(LINUX, env, host).open(HOME).outcome());
        assertEquals(List.of(List.of("/usr/bin/minecraft-launcher")), host.started);
        assertEquals(OfficialLauncher.Outcome.NOT_FOUND, launcher(LINUX, Map.of("PATH", "/usr/local/bin"), new FakeHost()).open(HOME).outcome());
    }

    @Test
    void tasklistLinesAreParsed() {
        assertEquals(Optional.of(new OfficialLauncher.ProcessEntry(1234, "MinecraftLauncher.exe")),
            OfficialLauncher.parseTasklistLine("\"MinecraftLauncher.exe\",\"1234\",\"Console\",\"1\",\"123,456 K\""));
        assertTrue(OfficialLauncher.parseTasklistLine("INFO: No tasks are running which match the specified criteria.").isEmpty());
        assertTrue(OfficialLauncher.parseTasklistLine("\"x.exe\",\"not-a-pid\"").isEmpty());
    }
}
