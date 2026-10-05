package dev.vanta.launcher.testutil;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Compiles a tiny stand-in for Fabric's {@code KnotClient} into a jar. Its {@code main} prints every argument and
 * the {@code vanta.launcher} and {@code vanta.launcher.restartable} system properties on one line each and exits with 0
 * (when {@code config/vanta/restart-once.test} exists in the game directory it deletes it and writes
 * {@code config/vanta/restart.request} first, like the in-game "Restart game" button), so launch tests can exercise the
 * real {@link ProcessBuilder} path with the host JDK without Minecraft.
 */
public final class FakeGameJar {

    /** Main class name expected by the Fabric profile. */
    public static final String MAIN_CLASS = "net.fabricmc.loader.impl.launch.knot.KnotClient";

    private FakeGameJar() {
    }

    /**
     * Builds the jar.
     *
     * @param jar destination jar
     * @throws IOException on failure
     */
    public static void build(final Path jar) throws IOException {
        final Path work = Files.createTempDirectory("fake-game");
        final Path src = work.resolve("net/fabricmc/loader/impl/launch/knot/KnotClient.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, """
            package net.fabricmc.loader.impl.launch.knot;
            public final class KnotClient {
                public static void main(String[] args) {
                    System.out.println("FAKE_GAME_START");
                    for (String a : args) {
                        System.out.println("ARG " + a);
                    }
                    System.out.println("PROP vanta.launcher=" + System.getProperty("vanta.launcher"));
                    System.out.println("PROP vanta.launcher.restartable=" + System.getProperty("vanta.launcher.restartable"));
                    System.out.println("CWD " + System.getProperty("user.dir"));
                    // Stand-in for the in-game "Restart game" button: config/vanta/restart-once.test asks for one restart.
                    try {
                        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("user.dir"), "config", "vanta");
                        if (java.nio.file.Files.deleteIfExists(dir.resolve("restart-once.test"))) {
                            java.nio.file.Files.writeString(dir.resolve("restart.request"), "restart");
                            System.out.println("FAKE_GAME_RESTART_REQUESTED");
                        }
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                    System.err.println("FAKE_GAME_STDERR");
                    System.out.println("FAKE_GAME_END");
                }
            }
            """, StandardCharsets.UTF_8);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("No system Java compiler available");
        }
        final Path classes = work.resolve("classes");
        Files.createDirectories(classes);
        final int rc = compiler.run(null, null, null, "-d", classes.toString(), "--release", "21", src.toString());
        if (rc != 0) {
            throw new IOException("Compilation of the fake game failed");
        }
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        Files.createDirectories(jar.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(out, manifest)) {
            final Path classFile = classes.resolve("net/fabricmc/loader/impl/launch/knot/KnotClient.class");
            jos.putNextEntry(new JarEntry("net/fabricmc/loader/impl/launch/knot/KnotClient.class"));
            jos.write(Files.readAllBytes(classFile));
            jos.closeEntry();
        }
    }
}
