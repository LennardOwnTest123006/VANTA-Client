package dev.vanta.launcher;

import dev.vanta.launcher.core.util.OsInfo;

import java.util.Optional;

/**
 * Compares the JavaFX platform a fat jar was built for ({@code javafx.platform} in {@code build-info.properties},
 * written by the Gradle task {@code generateBuildInfo}) with the platform of the running Java runtime.
 *
 * <p>{@code os.arch} is the architecture of the Java runtime, not necessarily the computer's: an x64 Java on an Apple
 * Silicon Mac runs under Rosetta 2 and reports x64, a 32-bit Java on 64-bit Windows reports x86. The messages
 * therefore talk about "a Java runtime for ..." and tell those users which Java (or which bundled-runtime download)
 * runs the jar they have.</p>
 *
 * <p>A fat jar carries the JavaFX native libraries of exactly one platform. Started on another platform, JavaFX fails
 * deep inside its native loader with an error nobody can act on, or a double-click shows nothing at all.
 * {@link dev.vanta.launcher.Main} therefore runs this check before any JavaFX class is touched and names the file that
 * does work. Command line flags ({@code --help}, {@code --version}, {@code --install}, ...) do not need JavaFX and keep
 * working with every jar.</p>
 */
public final class PlatformCheck {

    /** Value of {@code javafx.platform} when the build information is missing (IDE runs): the check is skipped. */
    public static final String UNKNOWN = "unknown";

    private PlatformCheck() {
    }

    /**
     * @param os running platform
     * @return the JavaFX classifier that runs there ({@code win}, {@code linux}, {@code linux-aarch64}, {@code mac},
     *         {@code mac-aarch64}); empty when JavaFX has no build for it (32-bit Java, an arm64 Java on Windows on ARM,
     *         ...; an x64 Java on Windows on ARM reports x64 and runs the Windows jar under emulation)
     */
    public static Optional<String> javafxPlatformFor(final OsInfo os) {
        final String arch = os.arch();
        if (os.isWindows() && "x64".equals(arch)) {
            return Optional.of("win");
        }
        if (os.isLinux() && "x64".equals(arch)) {
            return Optional.of("linux");
        }
        if (os.isLinux() && "arm64".equals(arch)) {
            return Optional.of("linux-aarch64");
        }
        if (os.isMac() && "x64".equals(arch)) {
            return Optional.of("mac");
        }
        if (os.isMac() && "arm64".equals(arch)) {
            return Optional.of("mac-aarch64");
        }
        return Optional.empty();
    }

    /**
     * @param javafxPlatform JavaFX classifier
     * @return human readable platform, e.g. {@code Windows x64}
     */
    public static String describePlatform(final String javafxPlatform) {
        return switch (javafxPlatform) {
            case "win" -> "Windows x64";
            case "linux" -> "Linux x64";
            case "linux-aarch64" -> "Linux arm64";
            case "mac" -> "macOS x64 (Intel)";
            case "mac-aarch64" -> "macOS on Apple Silicon (aarch64)";
            default -> javafxPlatform;
        };
    }

    /**
     * @param os platform of the running Java runtime
     * @return human readable platform, e.g. {@code Linux x64} or {@code Windows x86 (32-bit)}
     */
    public static String describeRunning(final OsInfo os) {
        return javafxPlatformFor(os).map(PlatformCheck::describePlatform).orElseGet(() -> {
            final String name = os.isWindows() ? "Windows" : os.isMac() ? "macOS" : os.isLinux() ? "Linux" : os.name();
            return name + " " + os.arch() + (os.is64Bit() ? "" : " (32-bit)");
        });
    }

    /**
     * Checks whether this jar's user interface can start here.
     *
     * @param builtFor     {@code javafx.platform} of this build ({@link LauncherVersion#JAVAFX_PLATFORM})
     * @param os           platform of the running Java runtime ({@link OsInfo#detect()})
     * @param version      launcher version (for the file names)
     * @param releasesPage where the downloads are
     * @return an explanation naming the right download when the jar is for another platform; empty when it matches
     *         or the build platform is unknown
     */
    public static Optional<String> mismatch(final String builtFor, final OsInfo os, final String version, final String releasesPage) {
        if (builtFor == null || builtFor.isBlank() || UNKNOWN.equals(builtFor)) {
            return Optional.empty();
        }
        final Optional<String> running = javafxPlatformFor(os);
        if (running.isPresent() && running.get().equals(builtFor)) {
            return Optional.empty();
        }
        final String here = describeRunning(os);
        final StringBuilder sb = new StringBuilder();
        sb.append("This jar is for ").append(describePlatform(builtFor)).append(", but it was started by a Java runtime for ")
            .append(here).append('.')
            .append(" Its user interface cannot start with that Java runtime because the jar contains the JavaFX libraries of ")
            .append(describePlatform(builtFor)).append(" only.\n\n");
        sb.append(rightDownload(running.orElse(""), os, here, version));
        sb.append("\nDownloads: ").append(releasesPage).append('\n');
        sb.append("\nCommand line options such as --help, --version and --install work with every VANTA Launcher jar.");
        return Optional.of(sb.toString());
    }

    private static String rightDownload(final String platform, final OsInfo os, final String here, final String v) {
        return switch (platform) {
            case "win" -> "Download vanta-launcher-" + v + "-windows-all.jar for Windows x64, or the installer VANTA-Launcher-" + v
                + ".msi, or the portable app VANTA-Launcher-" + v + "-windows-portable.zip.";
            case "linux" -> "Download vanta-launcher-" + v + "-linux-all.jar for Linux x64, or the app image VANTA-Launcher-" + v
                + "-linux-x64.tar.gz.";
            case "mac-aarch64" -> "Download vanta-launcher-" + v + "-macos-aarch64-all.jar for macOS on Apple Silicon.";
            // os.arch is the Java runtime's: an x64 Java on an Apple Silicon Mac runs under Rosetta 2 and reports x64.
            case "mac" -> "If this Mac has an Apple chip (M1 or newer; About This Mac shows the chip), this Java runtime is an"
                + " Intel (x64) build running under Rosetta 2. Install an arm64 (aarch64) Java 21 and start vanta-launcher-" + v
                + "-macos-aarch64-all.jar with it. On a Mac with an Intel processor there is no ready-made VANTA Launcher"
                + " download yet. " + noDownload();
            default -> {
                if (!os.is64Bit() && os.isWindows()) {
                    // A 32-bit Java on 64-bit Windows reports x86; JavaFX has no 32-bit build in these jars.
                    yield "This is a 32-bit Java runtime. On 64-bit Windows, install a 64-bit (x64) Java 21 and start vanta-launcher-"
                        + v + "-windows-all.jar with it, or use the installer VANTA-Launcher-" + v + ".msi or the portable app"
                        + " VANTA-Launcher-" + v + "-windows-portable.zip, which bring their own Java runtime. There is no VANTA"
                        + " Launcher download for 32-bit Windows.";
                }
                if (os.isWindows() && "arm64".equals(os.arch())) {
                    // JavaFX has no Windows arm64 build in these jars, but Windows 11 on ARM runs x64 programs under
                    // emulation: the x64 downloads (with their own x64 Java) or an x64 Java work there.
                    yield "This is a Java runtime for Windows on ARM. Windows 11 on ARM runs x64 programs under emulation, so use the"
                        + " installer VANTA-Launcher-" + v + ".msi or the portable app VANTA-Launcher-" + v + "-windows-portable.zip:"
                        + " both are x64 and bring their own x64 Java runtime. Or install an x64 Java 21 and start vanta-launcher-" + v
                        + "-windows-all.jar with it. (Windows 10 on ARM cannot run x64 programs.) There is no native arm64 build of the"
                        + " VANTA Launcher for Windows. Without x64 emulation, set VANTA up for the official Minecraft Launcher with this"
                        + " jar's command line: java -jar <this jar> --install-official-profile";
                }
                if (!os.is64Bit() && os.isLinux()) {
                    yield "This is a 32-bit Java runtime. On 64-bit Linux, install a 64-bit (x64) Java 21 and start vanta-launcher-"
                        + v + "-linux-all.jar with it, or use the app image VANTA-Launcher-" + v + "-linux-x64.tar.gz, which"
                        + " brings its own Java runtime. There is no VANTA Launcher download for 32-bit Linux.";
                }
                yield "There is no ready-made VANTA Launcher download for " + here + " yet. " + noDownload();
            }
        };
    }

    private static String noDownload() {
        return "Build the launcher from source on this computer (see BUILDING.md in the repository), or set VANTA up for the"
            + " official Minecraft Launcher with this jar's command line: java -jar <this jar> --install-official-profile";
    }
}
