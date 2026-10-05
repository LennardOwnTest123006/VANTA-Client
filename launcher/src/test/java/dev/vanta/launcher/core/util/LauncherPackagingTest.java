package dev.vanta.launcher.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Packaging detection with an injected environment: system properties ({@code jpackage.app-path},
 * {@code vanta.launcher.packaged}) and the presence of {@code <app dir>/app/vanta-portable.marker}.
 */
class LauncherPackagingTest {

    @TempDir
    Path tmp;

    private static LauncherPackaging detect(final Map<String, String> props, final Set<Path> files) {
        return LauncherPackaging.detect(props::get, files::contains);
    }

    @Test
    void plainJarHasNoJpackageProperties() {
        final LauncherPackaging p = detect(Map.of(), Set.of());
        assertEquals(LauncherPackaging.Kind.PLAIN_JAR, p.kind());
        assertFalse(p.isPackaged());
        assertFalse(p.isPortable());
        assertTrue(p.appDirectory().isEmpty());
        assertEquals(LauncherPackaging.PLAIN_JAR, detect(Map.of(LauncherPackaging.PACKAGED_PROPERTY, "false"), Set.of()));
        assertEquals(LauncherPackaging.PLAIN_JAR, detect(Map.of(LauncherPackaging.APP_PATH_PROPERTY, "  "), Set.of()));
    }

    @Test
    void windowsInstallWithoutMarkerIsPackaged() {
        final Path exe = tmp.resolve("AppData/Local/VANTA Launcher/VANTA Launcher.exe");
        final LauncherPackaging p = detect(Map.of(LauncherPackaging.APP_PATH_PROPERTY, exe.toString(),
            LauncherPackaging.PACKAGED_PROPERTY, "true"), Set.of());
        assertEquals(LauncherPackaging.Kind.PACKAGED, p.kind());
        assertTrue(p.isPackaged());
        assertFalse(p.isPortable());
        assertEquals(Optional.of(exe.getParent().toAbsolutePath()), p.appDirectory());
    }

    @Test
    void portableMarkerNextToTheAppFolderMeansPortable() {
        final Path dir = tmp.resolve("Games/VANTA Launcher");
        final Path exe = dir.resolve("VANTA Launcher.exe");
        final Path marker = dir.toAbsolutePath().resolve("app").resolve(LauncherPackaging.PORTABLE_MARKER);
        assertEquals(marker, LauncherPackaging.portableMarker(dir.toAbsolutePath()));
        final LauncherPackaging p = detect(Map.of(LauncherPackaging.APP_PATH_PROPERTY, exe.toString(),
            LauncherPackaging.PACKAGED_PROPERTY, "true"), Set.of(marker));
        assertEquals(LauncherPackaging.Kind.PORTABLE, p.kind());
        assertTrue(p.isPortable());
        assertTrue(p.isPackaged());
        assertEquals(Optional.of(dir.toAbsolutePath()), p.appDirectory());
    }

    @Test
    void markerSomewhereElseIsIgnored() {
        final Path dir = tmp.resolve("VANTA Launcher");
        final Map<String, String> props = Map.of(LauncherPackaging.APP_PATH_PROPERTY, dir.resolve("VANTA Launcher.exe").toString());
        // The marker in the wrong place (next to the exe instead of in app/) does not make it portable.
        assertEquals(LauncherPackaging.Kind.PACKAGED, detect(props, Set.of(dir.toAbsolutePath().resolve(LauncherPackaging.PORTABLE_MARKER))).kind());
    }

    @Test
    void linuxAppImageIsPackaged() {
        final Path launcher = tmp.resolve("opt/VANTA Launcher/bin/VANTA Launcher");
        final LauncherPackaging p = detect(Map.of(LauncherPackaging.APP_PATH_PROPERTY, launcher.toString()), Set.of());
        assertEquals(LauncherPackaging.Kind.PACKAGED, p.kind(), "jpackage.app-path alone identifies a jpackage launcher");
    }

    @Test
    void packagedPropertyWithoutAppPathIsPackagedWithoutFolder() {
        final Map<String, String> props = new HashMap<>();
        props.put(LauncherPackaging.PACKAGED_PROPERTY, "TRUE");
        final LauncherPackaging p = detect(props, Set.of());
        assertEquals(LauncherPackaging.Kind.PACKAGED, p.kind());
        assertTrue(p.appDirectory().isEmpty(), "without jpackage.app-path the portable marker cannot be located");
    }

    @Test
    void realFileSystemDetection() throws IOException {
        final Path dir = tmp.resolve("portable/VANTA Launcher");
        Files.createDirectories(dir.resolve("app"));
        final Map<String, String> props = Map.of(LauncherPackaging.APP_PATH_PROPERTY, dir.resolve("VANTA Launcher.exe").toString(),
            LauncherPackaging.PACKAGED_PROPERTY, "true");
        assertEquals(LauncherPackaging.Kind.PACKAGED, LauncherPackaging.detect(props::get, Files::isRegularFile).kind());
        Files.writeString(dir.resolve("app").resolve(LauncherPackaging.PORTABLE_MARKER), "portable\n");
        assertEquals(LauncherPackaging.Kind.PORTABLE, LauncherPackaging.detect(props::get, Files::isRegularFile).kind());
        assertEquals(LauncherPackaging.Kind.PLAIN_JAR, LauncherPackaging.detect().kind(), "the test JVM is not a jpackage launcher");
    }
}
