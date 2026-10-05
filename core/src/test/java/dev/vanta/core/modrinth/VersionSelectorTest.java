package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VersionSelectorTest {
    private static final String SHA = "a".repeat(128);

    private static ModrinthVersion version(String id, String type, String date, List<String> games, List<String> loaders,
                                           String sha) {
        ModrinthFile file = new ModrinthFile("https://cdn.modrinth.com/" + id + ".jar", id + ".jar", true, 10, sha, "");
        return new ModrinthVersion(id, "P", id, id, type, Instant.parse(date), 0, games, loaders, List.of(file), List.of());
    }

    @Test
    void prefersTheNewestReleaseOverANewerBeta() {
        // Real Sodium situation on 2026-10-05: 0.8.15-beta.1 is newer than the 0.8.14 release Iris 1.10.8 pins.
        List<ModrinthVersion> versions = List.of(
                version("beta", "beta", "2026-09-20T21:25:58Z", List.of("1.21.11"), List.of("fabric"), SHA),
                version("release", "release", "2026-08-28T21:00:18Z", List.of("1.21.11"), List.of("fabric"), SHA),
                version("old", "release", "2026-05-18T22:12:48Z", List.of("1.21.11"), List.of("fabric"), SHA));
        assertEquals("release", VersionSelector.best(versions, "1.21.11", ModrinthProjectType.MOD).orElseThrow().id());
    }

    @Test
    void fallsBackToBetaThenAlpha() {
        List<ModrinthVersion> versions = List.of(
                version("alpha", "alpha", "2026-09-30T00:00:00Z", List.of("1.21.11"), List.of("fabric"), SHA),
                version("beta", "beta", "2026-09-01T00:00:00Z", List.of("1.21.11"), List.of("fabric"), SHA));
        assertEquals("beta", VersionSelector.best(versions, "1.21.11", ModrinthProjectType.MOD).orElseThrow().id());
        assertEquals("alpha", VersionSelector.best(versions.subList(0, 1), "1.21.11", ModrinthProjectType.MOD)
                .orElseThrow().id());
    }

    @Test
    void requiresGameVersionLoaderAndChecksum() {
        List<ModrinthVersion> versions = List.of(
                version("other-game", "release", "2026-09-30T00:00:00Z", List.of("1.21.10"), List.of("fabric"), SHA),
                version("neoforge", "release", "2026-09-30T00:00:00Z", List.of("1.21.11"), List.of("neoforge"), SHA),
                version("no-hash", "release", "2026-09-30T00:00:00Z", List.of("1.21.11"), List.of("fabric"), ""),
                version("ok", "release", "2026-01-01T00:00:00Z", List.of("1.21.11"), List.of("fabric", "quilt"), SHA));
        assertEquals("ok", VersionSelector.best(versions, "1.21.11", ModrinthProjectType.MOD).orElseThrow().id());
        assertTrue(VersionSelector.best(versions.subList(0, 3), "1.21.11", ModrinthProjectType.MOD).isEmpty());
    }

    @Test
    void shadersNeedTheIrisLoader() {
        List<ModrinthVersion> versions = List.of(
                version("optifine-only", "release", "2026-09-30T00:00:00Z", List.of("1.21.11"), List.of("optifine"), SHA),
                version("iris", "release", "2026-09-15T00:00:00Z", List.of("1.21.11"), List.of("iris", "optifine"), SHA));
        assertEquals("iris", VersionSelector.best(versions, "1.21.11", ModrinthProjectType.SHADER).orElseThrow().id());
    }

    @Test
    void unsafeFileNamesAreNeverInstallable() {
        ModrinthFile evil = new ModrinthFile("https://cdn.modrinth.com/x", "../../evil.jar", true, 1, SHA, "");
        ModrinthVersion v = new ModrinthVersion("x", "P", "x", "x", "release", Instant.EPOCH, 0, List.of("1.21.11"),
                List.of("fabric"), List.of(evil), List.of());
        assertTrue(VersionSelector.best(List.of(v), "1.21.11", ModrinthProjectType.MOD).isEmpty());
        assertTrue(ModrinthFile.isSafeFilename("sodium-fabric-0.8.14+mc1.21.11.jar"));
        assertTrue(!ModrinthFile.isSafeFilename(".hidden.jar") && !ModrinthFile.isSafeFilename("a/b.jar")
                && !ModrinthFile.isSafeFilename("C:evil.jar"));
    }
}
