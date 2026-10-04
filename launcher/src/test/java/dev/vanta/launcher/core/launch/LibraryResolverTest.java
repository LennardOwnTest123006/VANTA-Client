package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.Download;
import dev.vanta.launcher.core.model.FabricProfileJson;
import dev.vanta.launcher.core.model.Library;
import dev.vanta.launcher.core.model.Rule;
import dev.vanta.launcher.core.model.VersionJson;
import dev.vanta.launcher.core.net.HashAlgorithm;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.testutil.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryResolverTest {

    private static final VersionJson VANILLA = Json.parse(Fixtures.read("1.21.11.json"), VersionJson.class);

    private static Set<String> names(final List<ResolvedLibrary> libs) {
        return libs.stream().map(l -> l.coordinate().toString()).collect(Collectors.toSet());
    }

    @Test
    void linuxGetsLinuxNativesOnly() {
        final Set<String> names = names(new LibraryResolver(new OsInfo("linux", "x64", "")).resolve(VANILLA.libraries()));
        assertTrue(names.contains("org.lwjgl:lwjgl:3.3.3"));
        assertTrue(names.contains("org.lwjgl:lwjgl:3.3.3:natives-linux"));
        assertTrue(names.contains("com.mojang:jtracy:1.0.29:natives-linux"));
        assertTrue(names.contains("io.netty:netty-transport-native-epoll:4.1.115.Final:linux-x86_64"));
        assertFalse(names.contains("org.lwjgl:lwjgl:3.3.3:natives-windows"));
        assertFalse(names.contains("org.lwjgl:lwjgl:3.3.3:natives-macos"));
        assertFalse(names.contains("ca.weblite:java-objc-bridge:1.1"));
        assertFalse(names.stream().anyMatch(n -> n.contains("natives-windows") || n.contains("natives-macos")));
    }

    @Test
    void windowsX64GetsWindowsNativesWithoutArm64OrX86() {
        final Set<String> names = names(new LibraryResolver(new OsInfo("windows", "x64", "10.0")).resolve(VANILLA.libraries()));
        assertTrue(names.contains("org.lwjgl:lwjgl:3.3.3:natives-windows"));
        assertFalse(names.contains("org.lwjgl:lwjgl:3.3.3:natives-windows-arm64"));
        assertFalse(names.contains("org.lwjgl:lwjgl:3.3.3:natives-windows-x86"));
        assertFalse(names.contains("org.lwjgl:lwjgl:3.3.3:natives-linux"));
        assertFalse(names.stream().anyMatch(n -> n.contains("epoll:4.1.115.Final:linux")));
    }

    @Test
    void windowsArm64GetsArm64Natives() {
        final Set<String> names = names(new LibraryResolver(new OsInfo("windows", "arm64", "10.0")).resolve(VANILLA.libraries()));
        assertTrue(names.contains("org.lwjgl:lwjgl:3.3.3:natives-windows-arm64"));
        assertTrue(names.contains("org.lwjgl:lwjgl:3.3.3:natives-windows"), "generic windows natives rule has no arch constraint");
        assertFalse(names.contains("org.lwjgl:lwjgl:3.3.3:natives-windows-x86"));
    }

    @Test
    void macArm64GetsBothMacVariants() {
        final Set<String> names = names(new LibraryResolver(new OsInfo("osx", "arm64", "14.5")).resolve(VANILLA.libraries()));
        assertTrue(names.contains("org.lwjgl:lwjgl:3.3.3:natives-macos"));
        assertTrue(names.contains("org.lwjgl:lwjgl:3.3.3:natives-macos-arm64"));
        assertTrue(names.contains("ca.weblite:java-objc-bridge:1.1"));
        final Set<String> intel = names(new LibraryResolver(new OsInfo("osx", "x64", "13.6")).resolve(VANILLA.libraries()));
        assertFalse(intel.contains("org.lwjgl:lwjgl:3.3.3:natives-macos-arm64"));
    }

    @Test
    void resolvedLibraryCarriesMojangDownloadData() {
        final ResolvedLibrary lwjgl = new LibraryResolver(new OsInfo("linux", "x64", "")).resolve(VANILLA.libraries()).stream()
            .filter(l -> l.coordinate().toString().equals("org.lwjgl:lwjgl:3.3.3")).findFirst().orElseThrow();
        assertEquals("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar", lwjgl.relativePath());
        assertEquals("https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar", lwjgl.url());
        assertEquals(786207L, lwjgl.size());
        assertEquals(HashAlgorithm.SHA1, lwjgl.expectedChecksum().orElseThrow().algorithm());
        assertFalse(lwjgl.nativeJar());
        final ResolvedLibrary natives = new LibraryResolver(new OsInfo("linux", "x64", "")).resolve(VANILLA.libraries()).stream()
            .filter(l -> l.coordinate().toString().equals("org.lwjgl:lwjgl:3.3.3:natives-linux")).findFirst().orElseThrow();
        assertTrue(natives.nativeJar());
    }

    @Test
    void fabricLibrariesResolveAgainstMavenUrl() {
        final FabricProfileJson profile = Json.parse(Fixtures.read("fabric-profile-0.19.5.json"), FabricProfileJson.class);
        final List<ResolvedLibrary> libs = new LibraryResolver(new OsInfo("windows", "x64", "")).resolve(profile.libraries());
        assertEquals(8, libs.size());
        final ResolvedLibrary asm = libs.get(0);
        assertEquals("https://maven.fabricmc.net/org/ow2/asm/asm/9.9/asm-9.9.jar", asm.url());
        assertEquals(HashAlgorithm.SHA256, asm.expectedChecksum().orElseThrow().algorithm(), "sha256 preferred over sha1");
        final ResolvedLibrary loader = libs.stream().filter(l -> l.coordinate().artifact().equals("fabric-loader")).findFirst().orElseThrow();
        assertTrue(loader.expectedChecksum().isEmpty(), "profile entry without hashes needs a sidecar lookup");
        assertEquals("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar", loader.url());
    }

    @Test
    void legacyNativesClassifiersAreHonoured() {
        final Map<String, Download> classifiers = Map.of(
            "natives-windows-64", new Download("org/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-windows-64.jar",
                "https://libraries.minecraft.net/org/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-windows-64.jar", null, 10),
            "natives-linux", new Download("org/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-linux.jar",
                "https://libraries.minecraft.net/org/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-linux.jar", null, 10));
        final Library legacy = new Library("org.lwjgl:lwjgl-platform:2.9.4", new Library.Downloads(null, classifiers),
            List.of(new Rule("allow", null, null)), Map.of("windows", "natives-windows-${arch}", "linux", "natives-linux"), null, null, null, null, 0);
        final List<ResolvedLibrary> win = new LibraryResolver(new OsInfo("windows", "x64", "")).resolve(List.of(legacy));
        assertEquals(1, win.size());
        assertEquals("natives-windows-64", win.get(0).coordinate().classifier());
        assertTrue(win.get(0).nativeJar());
        final List<ResolvedLibrary> mac = new LibraryResolver(new OsInfo("osx", "x64", "")).resolve(List.of(legacy));
        assertTrue(mac.isEmpty(), "no natives for osx in the map and no main artifact");
    }
}
