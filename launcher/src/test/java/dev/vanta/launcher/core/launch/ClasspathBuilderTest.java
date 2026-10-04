package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.MavenCoordinate;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClasspathBuilderTest {

    private static ResolvedLibrary lib(final String coordinate) {
        final MavenCoordinate c = MavenCoordinate.parse(coordinate);
        return new ResolvedLibrary(c, c.path(), "https://example.invalid/" + c.path(), null, -1, c.isNativeClassifier());
    }

    @Test
    void fabricFirstThenVanillaThenClientJar() {
        final Path libs = Path.of("/data/libraries");
        final Path client = Path.of("/data/versions/1.21.11/1.21.11.jar");
        final List<Path> cp = new ClasspathBuilder(libs).build(
            List.of(lib("net.fabricmc:fabric-loader:0.19.5"), lib("org.ow2.asm:asm:9.9")),
            List.of(lib("com.mojang:brigadier:1.3.10"), lib("org.lwjgl:lwjgl:3.3.3"), lib("org.lwjgl:lwjgl:3.3.3:natives-linux")),
            client);
        assertEquals(List.of(
            libs.resolve("net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar"),
            libs.resolve("org/ow2/asm/asm/9.9/asm-9.9.jar"),
            libs.resolve("com/mojang/brigadier/1.3.10/brigadier-1.3.10.jar"),
            libs.resolve("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"),
            libs.resolve("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar"),
            client), cp);
    }

    @Test
    void dedupesByGroupArtifactKeepingHigherVersionAndFirstPosition() {
        final Path libs = Path.of("/libs");
        final List<Path> cp = new ClasspathBuilder(libs).build(
            List.of(lib("org.ow2.asm:asm:9.9"), lib("com.google.code.gson:gson:2.10.1")),
            List.of(lib("com.google.code.gson:gson:2.11.0"), lib("org.ow2.asm:asm:9.6"), lib("org.slf4j:slf4j-api:2.0.16")),
            Path.of("/client.jar"));
        assertEquals(List.of(
            libs.resolve("org/ow2/asm/asm/9.9/asm-9.9.jar"),
            libs.resolve("com/google/code/gson/gson/2.11.0/gson-2.11.0.jar"),
            libs.resolve("org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar"),
            Path.of("/client.jar")), cp);
    }

    @Test
    void classifiersAreDistinctKeys() {
        final List<Path> cp = new ClasspathBuilder(Path.of("/libs")).build(List.of(),
            List.of(lib("org.lwjgl:lwjgl:3.3.3"), lib("org.lwjgl:lwjgl:3.3.3:natives-windows"), lib("org.lwjgl:lwjgl:3.3.3:natives-windows-arm64")),
            Path.of("/c.jar"));
        assertEquals(4, cp.size());
    }

    @Test
    void joinUsesSeparator() {
        final String joined = ClasspathBuilder.join(List.of(Path.of("/a.jar"), Path.of("/b.jar")), ":");
        assertEquals("/a.jar:/b.jar", joined);
        assertTrue(ClasspathBuilder.join(List.of(), ";").isEmpty());
    }
}
