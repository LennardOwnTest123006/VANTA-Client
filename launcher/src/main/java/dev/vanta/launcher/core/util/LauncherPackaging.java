package dev.vanta.launcher.core.util;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * How the running launcher was installed. The self-update picks the release file that replaces exactly this kind of
 * installation (see {@code ReleaseManifest.launcherAssetNames}).
 *
 * <ul>
 *   <li>{@link Kind#PACKAGED}: started by a jpackage launcher (Windows MSI/EXE install, Linux app image). jpackage sets
 *       the system property {@value #APP_PATH_PROPERTY} to the launcher executable, and the build adds
 *       {@code -D}{@value #PACKAGED_PROPERTY}{@code =true} to the packaged JVM options.</li>
 *   <li>{@link Kind#PORTABLE}: a packaged launcher whose app directory contains {@value #PORTABLE_MARKER}, i.e.
 *       {@code <directory of jpackage.app-path>/app/vanta-portable.marker}. The release workflow adds that file to the
 *       Windows portable zip only; the MSI/EXE are built from a fresh app image without it.</li>
 *   <li>{@link Kind#PLAIN_JAR}: {@code java -jar vanta-launcher-<v>-<system>-all.jar} (no jpackage launcher).</li>
 * </ul>
 *
 * @param kind         installation kind
 * @param appDirectory the directory of the launcher executable (for the portable folder: the folder the portable zip's
 *                     {@value #PORTABLE_FOLDER} folder replaces, so the zip is extracted into its parent); empty for a
 *                     plain jar or when the packaged launcher did not report its path
 */
public record LauncherPackaging(Kind kind, Optional<Path> appDirectory) {

    /** System property set by jpackage launchers: the path of the launcher executable. */
    public static final String APP_PATH_PROPERTY = "jpackage.app-path";
    /** System property the build passes to packaged launchers ({@code --java-options -Dvanta.launcher.packaged=true}). */
    public static final String PACKAGED_PROPERTY = "vanta.launcher.packaged";
    /** File name of the portable marker inside {@code <app directory>/app/}. */
    public static final String PORTABLE_MARKER = "vanta-portable.marker";
    /**
     * The top-level folder of the Windows portable zip (the jpackage app name, {@code --name} in {@code build.gradle}):
     * the zip holds {@code VANTA Launcher/VANTA Launcher.exe}, {@code VANTA Launcher/app/...} and
     * {@code VANTA Launcher/runtime/...}.
     */
    public static final String PORTABLE_FOLDER = "VANTA Launcher";

    /** Installation kinds. */
    public enum Kind {
        /** {@code java -jar} with a fat jar. */
        PLAIN_JAR,
        /** jpackage installer or app image (no portable marker). */
        PACKAGED,
        /** Windows portable zip (packaged app image with the portable marker). */
        PORTABLE
    }

    /** A plain jar run. */
    public static final LauncherPackaging PLAIN_JAR = new LauncherPackaging(Kind.PLAIN_JAR, Optional.empty());

    public LauncherPackaging {
        Objects.requireNonNull(kind, "kind");
        appDirectory = appDirectory == null ? Optional.empty() : appDirectory;
    }

    /**
     * @param appDirectory directory of the launcher executable (may be null)
     * @return a packaged installation
     */
    public static LauncherPackaging packaged(final Path appDirectory) {
        return new LauncherPackaging(Kind.PACKAGED, Optional.ofNullable(appDirectory));
    }

    /**
     * @param appDirectory directory of the launcher executable
     * @return a portable installation
     */
    public static LauncherPackaging portable(final Path appDirectory) {
        return new LauncherPackaging(Kind.PORTABLE, Optional.of(appDirectory));
    }

    /**
     * Detects the packaging of the running launcher from the system properties and the file system.
     *
     * @return packaging
     */
    public static LauncherPackaging detect() {
        return detect(System::getProperty, Files::isRegularFile);
    }

    /**
     * Detects the packaging from injected sources (tests).
     *
     * @param properties   system property lookup (returns null when absent)
     * @param isRegularFile whether a path is an existing regular file
     * @return packaging
     */
    public static LauncherPackaging detect(final Function<String, String> properties, final Predicate<Path> isRegularFile) {
        final String appPath = trimmed(properties.apply(APP_PATH_PROPERTY));
        final boolean packagedFlag = "true".equalsIgnoreCase(trimmed(properties.apply(PACKAGED_PROPERTY)));
        if (appPath.isEmpty() && !packagedFlag) {
            return PLAIN_JAR;
        }
        final Optional<Path> appDirectory = appDirectoryOf(appPath);
        if (appDirectory.isPresent() && isRegularFile.test(portableMarker(appDirectory.get()))) {
            return portable(appDirectory.get());
        }
        return new LauncherPackaging(Kind.PACKAGED, appDirectory);
    }

    /**
     * @param appDirectory directory of the launcher executable
     * @return {@code <appDirectory>/app/vanta-portable.marker}
     */
    public static Path portableMarker(final Path appDirectory) {
        return appDirectory.resolve("app").resolve(PORTABLE_MARKER);
    }

    /** @return whether a jpackage launcher started this JVM (installer, app image or portable folder) */
    public boolean isPackaged() {
        return kind != Kind.PLAIN_JAR;
    }

    /** @return whether this is the Windows portable folder */
    public boolean isPortable() {
        return kind == Kind.PORTABLE;
    }

    private static Optional<Path> appDirectoryOf(final String appPath) {
        if (appPath.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(Path.of(appPath).toAbsolutePath().getParent());
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static String trimmed(final String value) {
        return value == null ? "" : value.trim();
    }
}
