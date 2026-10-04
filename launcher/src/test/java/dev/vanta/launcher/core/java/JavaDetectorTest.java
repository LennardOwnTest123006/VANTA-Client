package dev.vanta.launcher.core.java;

import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.OsInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaDetectorTest {

    @TempDir
    Path tmp;

    private static final OsInfo HOST = new OsInfo(OsInfo.detect().name(), "x64", "6.8");

    private Path fakeJava(final Path home, final String version) throws IOException {
        return fakeJava(home, version, HOST.javaExecutableName());
    }

    /** Creates a fake Java executable with an explicit file name (e.g. {@code java} for a macOS layout test on Windows). */
    private Path fakeJava(final Path home, final String version, final String executableName) throws IOException {
        final Path exe = home.resolve("bin").resolve(executableName);
        Files.createDirectories(exe.getParent());
        Files.writeString(exe, "#!/bin/sh\necho " + version + "\n");
        exe.toFile().setExecutable(true);
        return exe;
    }

    /** Probe that answers from the directory name: {@code jdk-21.0.4} → version 21.0.4. */
    private static final JavaProbe NAME_PROBE = exe -> {
        final Path home = JavaVersionParser.homeOf(exe);
        final String name = home.getFileName().toString();
        if (!name.startsWith("jdk-")) {
            return Optional.empty();
        }
        final String version = name.substring(4);
        return Optional.of(new JavaInstall(home, exe, version, JavaVersionParser.major(version), "Test", "amd64", !version.endsWith("-32")));
    };

    @Test
    void collectsCandidatesFromJavaHomePathAndRoots() throws IOException {
        final Path jvmRoot = tmp.resolve("jvm");
        final Path j21 = fakeJava(jvmRoot.resolve("jdk-21.0.4"), "21.0.4");
        final Path j17 = fakeJava(jvmRoot.resolve("jdk-17.0.9"), "17.0.9");
        final Path j25 = fakeJava(tmp.resolve("home/jdk-25.0.1"), "25.0.1");
        final Path onPath = fakeJava(tmp.resolve("pathdir/jdk-21.0.2"), "21.0.2");
        fakeJava(tmp.resolve("broken/not-a-jdk"), "x");
        final LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        final Path own = fakeJava(paths.runtimesDir().resolve("jdk-21.0.4-temurin"), "21.0.4");

        final Map<String, String> env = new HashMap<>();
        env.put("JAVA_HOME", j25.getParent().getParent().toString());
        env.put("PATH", onPath.getParent() + HOST.classpathSeparator() + tmp.resolve("nonexistent/bin"));
        env.put("ProgramFiles", tmp.resolve("pf").toString());
        env.put("ProgramFiles(x86)", tmp.resolve("pf86").toString());
        env.put("LOCALAPPDATA", tmp.resolve("localappdata").toString());
        final JavaDetector detector = new JavaDetector(HOST, env, paths, NAME_PROBE, List.of(jvmRoot, tmp.resolve("broken")));

        final List<Path> candidates = detector.candidateExecutables();
        assertEquals(j25.toRealPath(), candidates.get(0), "JAVA_HOME first");
        assertEquals(onPath.toRealPath(), candidates.get(1), "PATH second");
        assertTrue(candidates.contains(j21.toRealPath()));
        assertTrue(candidates.contains(j17.toRealPath()));
        assertTrue(candidates.contains(own.toRealPath()));

        final List<JavaInstall> detected = detector.detect();
        assertEquals(5, detected.size(), "the broken candidate is dropped by the probe");
        assertEquals(25, detected.get(0).major(), "sorted newest first");

        final JavaInstall pick = detector.pick(21).orElseThrow();
        assertEquals(21, pick.major(), "exact major preferred over newer");
        assertEquals("21.0.4", pick.version(), "newest build of the exact major");
    }

    @Test
    void pickFallsBackToNewerMajorAndPrefers64Bit() {
        final JavaInstall j17 = new JavaInstall(Path.of("/j17"), Path.of("/j17/bin/java"), "17.0.9", 17, "v", "amd64", true);
        final JavaInstall j25 = new JavaInstall(Path.of("/j25"), Path.of("/j25/bin/java"), "25.0.1", 25, "v", "amd64", true);
        final JavaInstall j23 = new JavaInstall(Path.of("/j23"), Path.of("/j23/bin/java"), "23.0.2", 23, "v", "amd64", true);
        assertEquals(j23, JavaDetector.pick(List.of(j17, j25, j23), 21).orElseThrow(), "lowest major that still satisfies");
        assertTrue(JavaDetector.pick(List.of(j17), 21).isEmpty());
        final JavaInstall j21x86 = new JavaInstall(Path.of("/a"), Path.of("/a/bin/java"), "21.0.5", 21, "v", "x86", false);
        final JavaInstall j21x64 = new JavaInstall(Path.of("/b"), Path.of("/b/bin/java"), "21.0.1", 21, "v", "amd64", true);
        assertEquals(j21x64, JavaDetector.pick(List.of(j21x86, j21x64), 21).orElseThrow());
    }

    @Test
    void userPathAcceptsHomeBinOrExecutable() throws IOException {
        final Path exe = fakeJava(tmp.resolve("jdk-21.0.4"), "21.0.4");
        final JavaDetector detector = new JavaDetector(HOST, Map.of(), new LauncherPaths(tmp.resolve("data")), NAME_PROBE, List.of());
        assertEquals(exe, detector.executableFromUserPath(tmp.resolve("jdk-21.0.4")).orElseThrow());
        assertEquals(exe, detector.executableFromUserPath(tmp.resolve("jdk-21.0.4/bin")).orElseThrow());
        assertEquals(exe, detector.executableFromUserPath(exe).orElseThrow());
        assertTrue(detector.executableFromUserPath(tmp.resolve("nowhere")).isEmpty());
        assertEquals(21, detector.probeUserPath(tmp.resolve("jdk-21.0.4")).orElseThrow().major());
    }

    @Test
    void macContentsHomeLayout() throws IOException {
        final OsInfo mac = new OsInfo("osx", "arm64", "14");
        final Path root = tmp.resolve("JavaVirtualMachines");
        final Path exe = fakeJava(root.resolve("jdk-21.0.4.jdk/Contents/Home"), "21.0.4", mac.javaExecutableName());
        assertEquals(exe, JavaDetector.executableIn(root.resolve("jdk-21.0.4.jdk"), mac));
        final JavaDetector detector = new JavaDetector(mac, Map.of(), new LauncherPaths(tmp.resolve("data")), e -> Optional.of(
            new JavaInstall(JavaVersionParser.homeOf(e), e, "21.0.4", 21, "Eclipse Adoptium", "aarch64", true)), List.of(root));
        assertTrue(detector.candidateExecutables().contains(exe.toRealPath()));
        assertFalse(detector.detect().isEmpty());
    }

    @Test
    void realProbeFindsTheHostJdk() {
        final Path javaHome = Path.of(System.getProperty("java.home"));
        final OsInfo host = OsInfo.detect();
        final Optional<JavaInstall> install = new ProcessJavaProbe().probe(JavaDetector.executableIn(javaHome, host));
        assertTrue(install.isPresent(), "the JDK running the tests must be probeable");
        assertEquals(Runtime.version().feature(), install.get().major());
        assertTrue(install.get().satisfies(21));
        assertTrue(new ProcessJavaProbe().probe(tmp.resolve("missing/bin/java")).isEmpty());
    }
}
