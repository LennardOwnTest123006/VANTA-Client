package dev.vanta.launcher.core.model;

import dev.vanta.launcher.core.util.Json;
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

    @Test
    void preferredLauncherFileDependsOnPlatform() {
        final ReleaseManifest m = Json.parse(Fixtures.read("release/launcher-latest.json"), ReleaseManifest.class);
        assertEquals("VANTA-Launcher-1.1.0.msi", m.preferredFile(true).orElseThrow().name());
        assertEquals("vanta-launcher-1.1.0-all.jar", m.preferredFile(false).orElseThrow().name());
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
