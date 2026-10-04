package dev.vanta.launcher.core.model;

import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.testutil.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionJsonTest {

    @Test
    void parsesRealisticMojangJson() {
        final VersionJson v = Json.parse(Fixtures.read("1.21.11.json"), VersionJson.class);
        assertEquals("1.21.11", v.id());
        assertEquals("release", v.type());
        assertEquals("net.minecraft.client.main.Main", v.mainClass());
        assertEquals("26", v.assetIndexId());
        assertEquals(21, v.javaMajor());
        assertEquals("java-runtime-delta", v.javaVersion().component());
        assertTrue(v.clientDownload().isPresent());
        assertTrue(v.clientDownload().get().url().endsWith("/client.jar"));
        assertEquals(101, v.libraries().size());
        assertEquals(21, v.minimumLauncherVersion());
        assertEquals("client-1.12.xml", v.logging().client().file().id());

        // string and object arguments both parse
        final List<Argument> game = v.arguments().game();
        assertEquals(Argument.of("--username"), game.get(0));
        assertEquals(List.of("${auth_player_name}"), game.get(1).values());
        final Argument demo = game.stream().filter(a -> a.values().contains("--demo")).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, demo.rules().get(0).features().get("is_demo_user"));
        final Argument resolution = game.stream().filter(a -> a.values().contains("--width")).findFirst().orElseThrow();
        assertEquals(List.of("--width", "${resolution_width}", "--height", "${resolution_height}"), resolution.values());

        final Argument firstThread = v.arguments().jvm().get(0);
        assertEquals("osx", firstThread.rules().get(0).os().name());
        assertEquals(List.of("-XstartOnFirstThread"), firstThread.values());
    }

    @Test
    void argumentsRoundTripThroughGson() {
        final VersionJson v = Json.parse(Fixtures.read("1.21.11.json"), VersionJson.class);
        final VersionJson again = Json.parse(Json.toJson(v), VersionJson.class);
        assertEquals(v.arguments(), again.arguments());
        assertEquals(v.libraries(), again.libraries());
        assertEquals(v.downloads(), again.downloads());
    }

    @Test
    void fabricProfileMergesOntoVanilla() {
        final VersionJson vanilla = Json.parse(Fixtures.read("1.21.11.json"), VersionJson.class);
        final FabricProfileJson profile = Json.parse(Fixtures.read("fabric-profile-0.19.5.json"), FabricProfileJson.class);
        assertEquals("fabric-loader-0.19.5-1.21.11", profile.id());
        assertEquals("1.21.11", profile.inheritsFrom());
        assertEquals(FabricProfileJson.KNOT_CLIENT, profile.mainClass());
        assertEquals(8, profile.libraries().size());

        final VersionJson merged = profile.toVersionJson().mergeOnto(vanilla);
        assertEquals("fabric-loader-0.19.5-1.21.11", merged.id());
        assertEquals(FabricProfileJson.KNOT_CLIENT, merged.mainClass());
        assertNull(merged.inheritsFrom());
        assertEquals(vanilla.libraries().size() + 8, merged.libraries().size());
        assertEquals("org.ow2.asm:asm:9.9", merged.libraries().get(0).name(), "Fabric libraries come first");
        assertEquals(vanilla.arguments().game(), merged.arguments().game());
        assertEquals(vanilla.arguments().jvm().size() + 1, merged.arguments().jvm().size());
        assertEquals("-DFabricMcEmu= net.minecraft.client.main.Main ", merged.arguments().jvm().get(merged.arguments().jvm().size() - 1).values().get(0));
        assertEquals("26", merged.assetIndexId());
        assertTrue(merged.clientDownload().isPresent());
        assertEquals(21, merged.javaMajor());
    }

    @Test
    void manifestLookup() {
        final VersionManifest m = Json.parse(Fixtures.read("version_manifest_v2.json"), VersionManifest.class);
        assertEquals("1.21.11", m.latest().release());
        assertTrue(m.find("1.21.11").isPresent());
        assertEquals("release", m.find("1.21.11").get().type());
        assertFalse(m.find("1.21.99").isPresent());
    }

    @Test
    void assetIndexPathsAndTotals() {
        final AssetIndexJson index = Json.parse(Fixtures.read("asset-index-26.json"), AssetIndexJson.class);
        assertEquals(9, index.objects().size());
        final AssetIndexJson.AssetObject obj = index.objects().get("pack.mcmeta");
        assertEquals(obj.hash().substring(0, 2) + "/" + obj.hash(), obj.path());
        assertTrue(index.totalSize() > 0);
    }

    @Test
    void nullSafeDefaults() {
        final VersionJson v = Json.parse("{\"id\":\"x\"}", VersionJson.class);
        assertTrue(v.libraries().isEmpty());
        assertTrue(v.arguments().game().isEmpty());
        assertTrue(v.downloads().isEmpty());
        assertEquals("legacy", v.assetIndexId());
        assertEquals(21, v.javaMajor());
    }
}
