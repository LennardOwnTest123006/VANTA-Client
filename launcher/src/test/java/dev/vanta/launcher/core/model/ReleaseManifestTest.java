package dev.vanta.launcher.core.model;

import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.LauncherPackaging;
import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.testutil.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseManifestTest {

    @Test
    void parsesPublishedClientManifest() {
        final ReleaseManifest m = Json.parse(Fixtures.read("release/client-latest.json"), ReleaseManifest.class);
        assertEquals(1, m.schemaVersion());
        assertEquals("client", m.product());
        assertEquals("1.0.0", m.version());
        assertEquals("1.21.11", m.minecraftVersion());
        assertEquals("0.19.5", m.fabricVersion());
        assertEquals("0.141.6+1.21.11", m.fabricApiVersion());
        assertEquals(21, m.javaVersion());
        assertTrue(m.isPublished());
        assertEquals("vanta-client-1.0.0.jar", m.fileWithExtension(".jar").orElseThrow().name());
        assertTrue(m.fileWithExtension(".jar").orElseThrow().hasSha256());
        assertEquals(64, m.fileWithExtension(".jar").orElseThrow().sha256().length());
    }

    @Test
    void unpublishedManifestHasNoDownload() {
        final ReleaseManifest m = Json.parse(Fixtures.read("release/client-unpublished.json"), ReleaseManifest.class);
        assertFalse(m.isPublished());
        assertEquals("1.1.0", m.version());
        assertFalse(m.fileWithExtension(".jar").orElseThrow().isPublished());
        assertFalse(m.fileWithExtension(".jar").orElseThrow().hasSha256());
    }

    private static final OsInfo WINDOWS = new OsInfo("windows", "x64", "10.0");
    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8");
    private static final OsInfo LINUX_ARM = new OsInfo("linux", "arm64", "6.8");
    private static final OsInfo MAC_ARM = new OsInfo("osx", "arm64", "15.0");
    private static final OsInfo MAC_INTEL = new OsInfo("osx", "x64", "13.6");

    private static final LauncherPackaging INSTALLED = LauncherPackaging.packaged(java.nio.file.Path.of("C:/Users/p/AppData/Local/VANTA Launcher"));
    private static final LauncherPackaging PORTABLE = LauncherPackaging.portable(java.nio.file.Path.of("D:/Games/VANTA Launcher"));
    private static final LauncherPackaging APP_IMAGE = LauncherPackaging.packaged(java.nio.file.Path.of("/opt/VANTA Launcher/bin"));
    private static final LauncherPackaging JAR = LauncherPackaging.PLAIN_JAR;
    private static final OsInfo WINDOWS_ARM = new OsInfo("windows", "arm64", "10.0");

    @Test
    void launcherAssetDependsOnPlatformAndPackaging() {
        final ReleaseManifest m = Json.parse(Fixtures.read("release/launcher-latest.json"), ReleaseManifest.class);
        // Windows x64: MSI/EXE install -> .msi, portable folder -> portable zip, plain jar -> Windows fat jar.
        assertEquals("VANTA-Launcher-1.1.0.msi", m.launcherAssetFor(WINDOWS, INSTALLED).orElseThrow().name());
        assertEquals("VANTA-Launcher-1.1.0-windows-portable.zip", m.launcherAssetFor(WINDOWS, PORTABLE).orElseThrow().name());
        assertEquals("vanta-launcher-1.1.0-windows-all.jar", m.launcherAssetFor(WINDOWS, JAR).orElseThrow().name());
        // Linux x64: app image -> tar.gz, plain jar -> Linux fat jar.
        assertEquals("VANTA-Launcher-1.1.0-linux-x64.tar.gz", m.launcherAssetFor(LINUX, APP_IMAGE).orElseThrow().name());
        assertEquals("vanta-launcher-1.1.0-linux-all.jar", m.launcherAssetFor(LINUX, JAR).orElseThrow().name());
        // macOS on Apple Silicon: the aarch64 jar however it was started.
        assertEquals("vanta-launcher-1.1.0-macos-aarch64-all.jar", m.launcherAssetFor(MAC_ARM, JAR).orElseThrow().name());
        assertEquals("vanta-launcher-1.1.0-macos-aarch64-all.jar", m.launcherAssetFor(MAC_ARM, INSTALLED).orElseThrow().name());
        // No asset: Intel macOS, Linux and Windows on ARM (every file bundles x64 JavaFX/runtime).
        for (LauncherPackaging p : List.of(JAR, INSTALLED, PORTABLE)) {
            assertTrue(m.launcherAssetFor(MAC_INTEL, p).isEmpty(), "Intel macOS has no asset: the release page is offered instead");
            assertTrue(m.launcherAssetFor(LINUX_ARM, p).isEmpty(), "the Linux files are x64 only");
            assertTrue(m.launcherAssetFor(WINDOWS_ARM, p).isEmpty(), "the Windows files are x64 only");
        }
        assertEquals(m.launcherAssetFor(WINDOWS, INSTALLED), m.preferredFile(WINDOWS, INSTALLED));
        // Only the .msi: the .exe wrapper is gone from launcher 1.4.0 on and is never offered as an update.
        assertEquals(List.of("VANTA-Launcher-2.0.0.msi"), ReleaseManifest.launcherAssetNames("2.0.0", WINDOWS, INSTALLED));
        assertEquals(List.of("VANTA-Launcher-2.0.0-windows-portable.zip"), ReleaseManifest.launcherAssetNames("2.0.0", WINDOWS, PORTABLE));
        assertEquals(List.of("vanta-launcher-2.0.0-windows-all.jar"), ReleaseManifest.launcherAssetNames("2.0.0", WINDOWS, JAR));
        assertEquals(List.of("VANTA-Launcher-2.0.0-linux-x64.tar.gz"), ReleaseManifest.launcherAssetNames("2.0.0", LINUX, APP_IMAGE));
        assertEquals(List.of("vanta-launcher-2.0.0-linux-all.jar"), ReleaseManifest.launcherAssetNames("2.0.0", LINUX, JAR));

        // Without an msi an installed launcher gets nothing (the exe of older releases is never picked); the portable zip
        // and fat jars are never picked for it either.
        final ReleaseManifest exeOnly = new ReleaseManifest(1, "launcher", "1.1.0", "1.21.11", "0.19.5", "0.141.6+1.21.11", 21, "", "stable",
            List.of(file("vanta-launcher-1.1.0-windows-all.jar"), file("VANTA-Launcher-1.1.0-windows-portable.zip"), file("VANTA-Launcher-1.1.0.exe")), "");
        assertTrue(exeOnly.launcherAssetFor(WINDOWS, INSTALLED).isEmpty(), "the .exe is never offered as an update");
        assertTrue(exeOnly.launcherAssetFor(LINUX, JAR).isEmpty(), "the Windows fat jar does not run on Linux");
        assertTrue(exeOnly.launcherAssetFor(LINUX, APP_IMAGE).isEmpty());
        // A portable folder is never "updated" with an installer, nor an installed launcher with the portable zip.
        final ReleaseManifest installersOnly = new ReleaseManifest(1, "launcher", "1.1.0", "1.21.11", "0.19.5", "0.141.6+1.21.11", 21, "", "stable",
            List.of(file("VANTA-Launcher-1.1.0.msi"), file("VANTA-Launcher-1.1.0.exe")), "");
        assertTrue(installersOnly.launcherAssetFor(WINDOWS, PORTABLE).isEmpty());
        assertTrue(installersOnly.launcherAssetFor(WINDOWS, JAR).isEmpty());
    }

    @Test
    void clientJarIsExactlyTheModJar() {
        final ReleaseManifest published = Json.parse(Fixtures.read("release/client-latest.json"), ReleaseManifest.class);
        assertEquals("vanta-client-1.0.0.jar", published.clientJar().orElseThrow().name());
        assertEquals("vanta-client-1.0.0.jar", published.preferredFile(WINDOWS, INSTALLED).orElseThrow().name());

        // Fabric API listed first, the mods bundle and a sources jar: still exactly vanta-client-<version>.jar.
        final ReleaseManifest tricky = new ReleaseManifest(1, "client", "1.0.0", "1.21.11", "0.19.5", "0.141.6+1.21.11", 21, "", "stable",
            List.of(file("fabric-api-0.141.6+1.21.11.jar"), file("vanta-client-1.0.0-sources.jar"), file("vanta-client-1.0.0-mods.zip"),
                file("vanta-client-1.0.0.jar")), "");
        assertEquals("vanta-client-1.0.0.jar", tricky.clientJar().orElseThrow().name());
        assertEquals("fabric-api-0.141.6+1.21.11.jar", tricky.fileWithExtension(".jar").orElseThrow().name(),
            "why the extension lookup must not be used for the client");

        final ReleaseManifest missing = new ReleaseManifest(1, "client", "1.0.0", "1.21.11", "0.19.5", "0.141.6+1.21.11", 21, "", "stable",
            List.of(file("fabric-api-0.141.6+1.21.11.jar"), file("vanta-client-0.9.0.jar")), "");
        assertTrue(missing.clientJar().isEmpty(), "a jar of another version is not this release's client jar");
    }

    @Test
    void releasePageIsDerivedFromGithubAssetUrls() {
        final ReleaseManifest m = Json.parse(Fixtures.read("release/launcher-latest.json"), ReleaseManifest.class);
        assertEquals("https://github.com/vanta-client/vanta/releases/tag/launcher-v1.1.0", m.releasePageUrl().orElseThrow());
        assertTrue(Json.parse(Fixtures.read("release/client-unpublished.json"), ReleaseManifest.class).releasePageUrl().isEmpty(),
            "no URL, no release page");
        final ReleaseManifest elsewhere = new ReleaseManifest(1, "launcher", "1.1.0", "1.21.11", "0.19.5", "x", 21, "", "stable",
            List.of(new ReleaseManifest.ReleaseFile("a.msi", "https://cdn.example/a.msi", 1, "ab")), "");
        assertTrue(elsewhere.releasePageUrl().isEmpty(), "only GitHub release assets map to a release page");
    }

    private static ReleaseManifest.ReleaseFile file(final String name) {
        return new ReleaseManifest.ReleaseFile(name, "https://github.com/vanta-client/vanta/releases/download/x/" + name, 10, "ab".repeat(32));
    }

    @Test
    void parsesTheRepositoryManifestsAndOptionalNotes() throws java.io.IOException {
        final java.nio.file.Path releases = java.nio.file.Path.of("..", "shared", "releases");
        if (java.nio.file.Files.isDirectory(releases)) {
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(releases)) {
                for (java.nio.file.Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    final ReleaseManifest m = Json.read(f, ReleaseManifest.class);
                    assertEquals(1, m.schemaVersion(), f.toString());
                    assertTrue(m.product().equals("client") || m.product().equals("launcher"), f.toString());
                    assertFalse(m.files().isEmpty(), f.toString());
                }
            }
        }
        final ReleaseManifest withNotes = Json.parse("{\"schemaVersion\":1,\"product\":\"client\",\"version\":\"1.0.0\","
            + "\"files\":[],\"notes\":\"Known issue: none\"}", ReleaseManifest.class);
        assertTrue(withNotes.hasNotes());
        assertEquals("Known issue: none", withNotes.notes());
        assertFalse(new ReleaseManifest(1, "client", "1.0.0", "1.21.11", "0.19.5", "0.141.6+1.21.11", 21, "2026-10-04", "stable", List.of(), "x").hasNotes());
    }

    @Test
    void sha256IsNormalised() {
        final ReleaseManifest.ReleaseFile f = new ReleaseManifest.ReleaseFile("a.jar", " https://x/a.jar ", 1, "ABCDEF");
        assertEquals("abcdef", f.sha256());
        assertEquals("https://x/a.jar", f.downloadUrl());
    }
}
