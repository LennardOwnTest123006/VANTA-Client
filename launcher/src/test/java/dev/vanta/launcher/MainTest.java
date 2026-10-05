package dev.vanta.launcher;

import dev.vanta.launcher.core.util.OsInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bootstrap decisions of {@link Main} with an injected environment: the wrong-platform check runs before JavaFX,
 * the CLI works with every jar, and every failed UI start exits with 1.
 */
class MainTest {

    private static final OsInfo WINDOWS = new OsInfo("windows", "x64", "10.0");
    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8");
    private static final OsInfo LINUX_ARM = new OsInfo("linux", "arm64", "6.8");
    private static final OsInfo MAC_ARM = new OsInfo("osx", "arm64", "15.0");
    private static final OsInfo MAC_INTEL = new OsInfo("osx", "x64", "13.6");
    private static final OsInfo WINDOWS_ARM = new OsInfo("windows", "arm64", "10.0");
    /** A 32-bit Java on (usually 64-bit) Windows: os.arch is the Java runtime's, x86. */
    private static final OsInfo WINDOWS_32_BIT_JAVA = OsInfo.fromProperties("Windows 11", "x86", "10.0");
    private static final OsInfo LINUX_32_BIT_JAVA = OsInfo.fromProperties("Linux", "i386", "6.8");
    private static final String V = LauncherVersion.VERSION;

    /** Records what Main asked the environment to do. */
    private static final class FakeHost implements Main.Host {
        final OsInfo os;
        boolean headless;
        boolean uiAvailable = true;
        Throwable uiFailure;
        int uiLaunches;
        final List<String> dialogs = new ArrayList<>();
        final List<String[]> cliRuns = new ArrayList<>();

        FakeHost(final OsInfo os) {
            this.os = os;
        }

        @Override
        public OsInfo os() {
            return os;
        }

        @Override
        public boolean headless() {
            return headless;
        }

        @Override
        public boolean uiAvailable() {
            return uiAvailable;
        }

        @Override
        public void launchUi(final String[] args) throws Throwable {
            uiLaunches++;
            if (uiFailure != null) {
                throw uiFailure;
            }
        }

        @Override
        public void showError(final String title, final String message) {
            dialogs.add(title + ": " + message);
        }

        @Override
        public int runCli(final String[] args, final PrintStream out, final PrintStream err) {
            cliRuns.add(args);
            out.println("cli ran");
            return 0;
        }
    }

    private record Outcome(OptionalInt code, String out, String err) {
    }

    private static Outcome start(final String builtFor, final FakeHost host, final String... args) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final OptionalInt code;
        try (PrintStream o = new PrintStream(out, true, StandardCharsets.UTF_8); PrintStream e = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            code = Main.start(args, o, e, builtFor, host);
        }
        return new Outcome(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void windowsJarOnLinuxNamesTheLinuxDownloadsAndExitsWithOne() {
        final FakeHost host = new FakeHost(LINUX);
        final Outcome r = start("win", host);
        assertEquals(OptionalInt.of(1), r.code());
        assertEquals(0, host.uiLaunches, "JavaFX is never initialised for a wrong-platform jar");
        assertTrue(r.err().contains("This jar is for Windows x64, but it was started by a Java runtime for Linux x64."), r.err());
        assertTrue(r.err().contains("Download vanta-launcher-" + V + "-linux-all.jar for Linux x64, or the app image VANTA-Launcher-" + V
            + "-linux-x64.tar.gz"), r.err());
        assertTrue(r.err().contains("https://github.com/LennardOwnTest123006/VANTA-Client/releases"), r.err());
        assertEquals(1, host.dialogs.size(), "a display is available: the message is also shown in a window (double-click)");
        assertTrue(host.dialogs.get(0).contains("vanta-launcher-" + V + "-linux-all.jar"), host.dialogs.get(0));
    }

    @Test
    void linuxJarOnWindowsNamesTheWindowsDownloads() {
        final FakeHost host = new FakeHost(WINDOWS);
        final Outcome r = start("linux", host);
        assertEquals(OptionalInt.of(1), r.code());
        assertTrue(r.err().contains("This jar is for Linux x64, but it was started by a Java runtime for Windows x64."), r.err());
        assertTrue(r.err().contains("vanta-launcher-" + V + "-windows-all.jar"), r.err());
        assertTrue(r.err().contains("VANTA-Launcher-" + V + ".msi"), r.err());
        assertTrue(r.err().contains("VANTA-Launcher-" + V + "-windows-portable.zip"), r.err());
    }

    @Test
    void headlessMismatchPrintsWithoutDialog() {
        final FakeHost host = new FakeHost(MAC_ARM);
        host.headless = true;
        final Outcome r = start("win", host);
        assertEquals(OptionalInt.of(1), r.code());
        assertTrue(r.err().contains("vanta-launcher-" + V + "-macos-aarch64-all.jar"), r.err());
        assertTrue(host.dialogs.isEmpty());
    }

    @Test
    void platformsWithoutADownloadSaySo() {
        final FakeHost host = new FakeHost(LINUX_ARM);
        host.headless = true;
        final Outcome r = start("linux", host);
        assertEquals(OptionalInt.of(1), r.code());
        assertTrue(r.err().contains("There is no ready-made VANTA Launcher download for " + PlatformCheck.describeRunning(LINUX_ARM) + " yet."), r.err());
        assertTrue(r.err().contains("--install-official-profile"), r.err());
    }

    @Test
    void windowsOnArmIsPointedToTheX64DownloadsBeforeAnythingElse() {
        // os.aarch64 / arm64 reported by an arm64 Java on Windows 11 on ARM, which runs x64 programs under emulation.
        for (String raw : List.of("aarch64", "arm64")) {
            final OsInfo os = OsInfo.fromProperties("Windows 11", raw, "10.0");
            assertEquals(WINDOWS_ARM, os);
            for (String jar : List.of("win", "linux")) {
                final FakeHost host = new FakeHost(os);
                host.headless = true;
                final Outcome r = start(jar, host);
                assertEquals(OptionalInt.of(1), r.code());
                assertEquals(0, host.uiLaunches);
                final String err = r.err();
                assertTrue(err.contains("but it was started by a Java runtime for Windows arm64."), err);
                assertTrue(err.contains("Windows 11 on ARM runs x64 programs under emulation"), err);
                final int msi = err.indexOf("VANTA-Launcher-" + V + ".msi");
                final int portable = err.indexOf("VANTA-Launcher-" + V + "-windows-portable.zip");
                final int x64Java = err.indexOf("install an x64 Java 21 and start vanta-launcher-" + V + "-windows-all.jar");
                assertTrue(msi > 0 && portable > 0 && x64Java > 0, err);
                assertFalse(err.contains("There is no ready-made VANTA Launcher download"), "there is one: the x64 builds. " + err);
                final int official = err.indexOf("--install-official-profile");
                assertTrue(official > x64Java, "fallbacks only after the x64 recommendations: " + err);
                assertFalse(err.contains("Build the launcher from source"), "JavaFX has no Windows arm64 build to compile against: " + err);
            }
        }
        // An x64 Java on the same computer (emulated) reports x64 and simply runs the Windows jar.
        assertTrue(PlatformCheck.mismatch("win", OsInfo.fromProperties("Windows 11", "amd64", "10.0"), V, "https://x").isEmpty());
    }

    @Test
    void x64JavaOnAMacPointsToArm64JavaForAppleSiliconAndSaysIntelHasNoDownload() {
        // An x64 Java under Rosetta 2 on an Apple Silicon Mac reports os.arch x86_64, exactly like an Intel Mac.
        final OsInfo rosetta = OsInfo.fromProperties("Mac OS X", "x86_64", "15.0");
        assertEquals(MAC_INTEL.name(), rosetta.name());
        assertEquals(MAC_INTEL.arch(), rosetta.arch());
        final FakeHost host = new FakeHost(rosetta);
        host.headless = true;
        final Outcome r = start("mac-aarch64", host);
        assertEquals(OptionalInt.of(1), r.code());
        assertEquals(0, host.uiLaunches);
        assertTrue(r.err().contains("This jar is for macOS on Apple Silicon (aarch64), but it was started by a Java runtime for macOS x64 (Intel)."),
            r.err());
        assertFalse(r.err().contains("this computer runs"), "the architecture is the Java runtime's, not necessarily the computer's");
        assertTrue(r.err().contains("Rosetta 2"), r.err());
        assertTrue(r.err().contains("Install an arm64 (aarch64) Java 21 and start vanta-launcher-" + V + "-macos-aarch64-all.jar with it."), r.err());
        assertTrue(r.err().contains("On a Mac with an Intel processor there is no ready-made VANTA Launcher download yet."), r.err());
        assertTrue(r.err().contains("--install-official-profile"), r.err());

        // Any other jar started by that Java gives the same advice.
        final FakeHost other = new FakeHost(MAC_INTEL);
        other.headless = true;
        final Outcome windowsJar = start("win", other);
        assertTrue(windowsJar.err().contains("vanta-launcher-" + V + "-macos-aarch64-all.jar"), windowsJar.err());
    }

    @Test
    void thirtyTwoBitJavaIsToldToUseA64BitJavaOrTheBundledRuntime() {
        final FakeHost host = new FakeHost(WINDOWS_32_BIT_JAVA);
        host.headless = true;
        final Outcome r = start("win", host);
        assertEquals(OptionalInt.of(1), r.code());
        assertEquals(0, host.uiLaunches);
        assertTrue(r.err().contains("This jar is for Windows x64, but it was started by a Java runtime for Windows x86 (32-bit)."), r.err());
        assertTrue(r.err().contains("This is a 32-bit Java runtime. On 64-bit Windows, install a 64-bit (x64) Java 21 and start vanta-launcher-"
            + V + "-windows-all.jar with it"), r.err());
        assertTrue(r.err().contains("VANTA-Launcher-" + V + ".msi"), r.err());
        assertTrue(r.err().contains("VANTA-Launcher-" + V + "-windows-portable.zip, which bring their own Java runtime"), r.err());
        assertFalse(r.err().contains("Build the launcher from source"), "a 64-bit Java or the bundled runtime is the fix, not a source build");

        final FakeHost linux = new FakeHost(LINUX_32_BIT_JAVA);
        linux.headless = true;
        final Outcome l = start("linux", linux);
        assertEquals(OptionalInt.of(1), l.code());
        assertTrue(l.err().contains("a Java runtime for Linux x86 (32-bit)."), l.err());
        assertTrue(l.err().contains("install a 64-bit (x64) Java 21 and start vanta-launcher-" + V + "-linux-all.jar with it"), l.err());
        assertTrue(l.err().contains("VANTA-Launcher-" + V + "-linux-x64.tar.gz, which brings its own Java runtime"), l.err());
    }

    @Test
    void cliCommandsWorkWithEveryJarOnEveryPlatform() {
        for (String flag : List.of("--version", "--help", "--install", "--check-update")) {
            final FakeHost host = new FakeHost(LINUX);
            final Outcome r = start("win", host, flag);
            assertEquals(OptionalInt.of(0), r.code(), flag);
            assertEquals(1, host.cliRuns.size(), flag);
            assertEquals(0, host.uiLaunches);
            assertTrue(host.dialogs.isEmpty());
            assertFalse(r.err().contains("This jar is for"), "no platform complaint for " + flag);
        }
    }

    @Test
    void matchingPlatformStartsTheUi() {
        final FakeHost host = new FakeHost(LINUX);
        final Outcome r = start("linux", host);
        assertEquals(OptionalInt.empty(), r.code(), "the UI ran and exited normally: the JVM ends on its own");
        assertEquals(1, host.uiLaunches);
        assertEquals("", r.err());
        assertEquals(OptionalInt.empty(), start("unknown", new FakeHost(WINDOWS)).code(), "IDE runs without build info skip the check");
        assertEquals(OptionalInt.empty(), start("win", new FakeHost(WINDOWS), "--ui").code(), "--ui selects the UI");
    }

    @Test
    void failedUiStartExitsWithOneNotZero() {
        final FakeHost broken = new FakeHost(LINUX);
        broken.uiFailure = new UnsupportedOperationException("Unable to open DISPLAY");
        final Outcome r = start("linux", broken);
        assertEquals(OptionalInt.of(1), r.code());
        assertTrue(r.err().contains("could not start its user interface"), r.err());
        assertTrue(r.err().contains("Unable to open DISPLAY"), r.err());
        assertTrue(broken.cliRuns.isEmpty(), "no --help run that would exit with 0");

        final FakeHost noJavaFx = new FakeHost(LINUX);
        noJavaFx.uiAvailable = false;
        final Outcome missing = start("linux", noJavaFx);
        assertEquals(OptionalInt.of(1), missing.code());
        assertTrue(missing.err().contains("JavaFX is not available"), missing.err());
        assertEquals(0, noJavaFx.uiLaunches);
        assertEquals(1, noJavaFx.dialogs.size());
    }

    @Test
    void platformMapping() {
        assertEquals(Optional.of("win"), PlatformCheck.javafxPlatformFor(WINDOWS));
        assertEquals(Optional.of("linux"), PlatformCheck.javafxPlatformFor(LINUX));
        assertEquals(Optional.of("linux-aarch64"), PlatformCheck.javafxPlatformFor(LINUX_ARM));
        assertEquals(Optional.of("mac"), PlatformCheck.javafxPlatformFor(MAC_INTEL));
        assertEquals(Optional.of("mac-aarch64"), PlatformCheck.javafxPlatformFor(MAC_ARM));
        assertTrue(PlatformCheck.javafxPlatformFor(WINDOWS_ARM).isEmpty());
        assertTrue(PlatformCheck.javafxPlatformFor(new OsInfo("windows", "x86", "10")).isEmpty(), "32-bit Java cannot load x64 natives");
        assertTrue(PlatformCheck.mismatch("win", WINDOWS, V, "https://x").isEmpty());
        assertTrue(PlatformCheck.mismatch("mac-aarch64", MAC_ARM, V, "https://x").isEmpty());
        assertTrue(PlatformCheck.mismatch(PlatformCheck.UNKNOWN, LINUX, V, "https://x").isEmpty());
        assertTrue(PlatformCheck.mismatch("win", new OsInfo("windows", "x86", "10"), V, "https://x").isPresent());
        // The jar this test suite runs from was built for this machine.
        assertTrue(PlatformCheck.mismatch(LauncherVersion.JAVAFX_PLATFORM, OsInfo.detect(), V, "https://x").isEmpty(),
            "build-info javafx.platform " + LauncherVersion.JAVAFX_PLATFORM + " matches the build host");
    }
}
