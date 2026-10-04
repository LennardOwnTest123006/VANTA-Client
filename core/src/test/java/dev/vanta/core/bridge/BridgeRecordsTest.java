package dev.vanta.core.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class BridgeRecordsTest {
    @Test
    void cardinalFromYawFollowsMinecraftConvention() {
        assertEquals(Cardinal.SOUTH, Cardinal.fromYaw(0));
        assertEquals(Cardinal.WEST, Cardinal.fromYaw(90));
        assertEquals(Cardinal.NORTH, Cardinal.fromYaw(180));
        assertEquals(Cardinal.EAST, Cardinal.fromYaw(270));
        assertEquals(Cardinal.EAST, Cardinal.fromYaw(-90));
        assertEquals(Cardinal.NORTH, Cardinal.fromYaw(-180));
        assertEquals(Cardinal.SOUTH, Cardinal.fromYaw(359));
        assertEquals(Cardinal.SOUTH, Cardinal.fromYaw(720 + 10));
        assertEquals(-90.0, Cardinal.normalizeYaw(270), 1e-9);
        assertEquals("north", Cardinal.NORTH.minecraftName());
    }

    @Test
    void effectDurationFormatting() {
        assertEquals("1:30", new EffectInfo("Speed", 0, 1800, 0, false).formatDuration());
        assertEquals("0:05", new EffectInfo("Speed", 0, 100, 0, false).formatDuration());
        assertEquals("1:00:00", new EffectInfo("Speed", 0, 72000, 0, false).formatDuration());
        assertEquals("∞", new EffectInfo("Speed", 0, 0, 0, true).formatDuration());
        assertEquals("", new EffectInfo("Speed", 0, 0, 0, false).levelLabel());
        assertEquals("II", new EffectInfo("Speed", 0, 0, 1, false).levelLabel());
        assertEquals("11", new EffectInfo("Speed", 0, 0, 10, false).levelLabel());
    }

    @Test
    void itemInfoDurability() {
        ItemInfo sword = new ItemInfo("Diamond Sword", 780, 1561);
        assertTrue(sword.hasDurability());
        assertEquals(0.4997, sword.durabilityFraction(), 0.001);
        assertTrue(ItemInfo.EMPTY.isEmpty());
        assertEquals(1.0, new ItemInfo("Stick", 0, 0).durabilityFraction());
    }

    @Test
    void keyRefSerialisation() {
        KeyRef key = KeyRef.keyboard(344, "key.keyboard.right.shift");
        assertEquals(key, KeyRef.parse(key.serialize()));
        assertEquals(KeyRef.UNBOUND, KeyRef.parse("garbage"));
        assertTrue(KeyRef.UNBOUND.isUnbound());
        assertTrue(KeyRef.mouse(0).sameInput(KeyRef.mouse(0)));
        assertFalse(KeyRef.mouse(0).sameInput(KeyRef.keyboard(0, "key.keyboard.a")));
        assertEquals("key.mouse.left", KeyRef.mouse(0).name());
        assertEquals("key.mouse.5", KeyRef.mouse(4).name());
    }

    @Test
    void vanillaOptionNormalisation() {
        assertEquals(Optional.of(32), VanillaOption.RENDER_DISTANCE.normalize(99));
        assertEquals(Optional.of(2), VanillaOption.RENDER_DISTANCE.normalize(-5));
        assertEquals(Optional.of("FANCY"), VanillaOption.GRAPHICS_MODE.normalize("fancy"));
        assertTrue(VanillaOption.GRAPHICS_MODE.normalize("ultra").isEmpty());
        assertTrue(VanillaOption.VSYNC.normalize("true").isEmpty());
        assertEquals(Optional.of(true), VanillaOption.VSYNC.normalize(true));
        assertEquals(Optional.of(1.0), VanillaOption.MASTER_VOLUME.normalize(7.5));
        assertEquals(Optional.of(VanillaOption.RENDER_DISTANCE), VanillaOption.fromId("render_distance"));
        for (VanillaOption option : VanillaOption.values()) {
            assertTrue(option.normalize(option.vanillaDefault()).isPresent(), option + " default must be valid");
        }
    }

    @Test
    void vec3dMath() {
        Vec3d a = new Vec3d(0, 0, 0);
        Vec3d b = new Vec3d(3, 4, 0);
        assertEquals(5.0, a.distanceTo(b), 1e-9);
        assertEquals(3.0, a.horizontalDistanceTo(b), 1e-9, "vertical component ignored");
        assertEquals(5.0, a.horizontalDistanceTo(new Vec3d(3, 100, 4)), 1e-9);
        assertEquals(-1, new Vec3d(-0.5, 0, 0).blockX());
    }

    @Test
    void fakeBridgesBehave() {
        FakeGameBridge game = new FakeGameBridge();
        assertEquals(Optional.of(Cardinal.NORTH), game.facing());
        game.onTitleScreen();
        assertTrue(game.facing().isEmpty());
        assertTrue(game.playerPosition().isEmpty());
        game.openVanillaScreen(VanillaScreen.OPTIONS);
        assertEquals("openVanillaScreen:OPTIONS", game.actions.get(0));

        FakeOptionsBridge options = new FakeOptionsBridge();
        assertTrue(options.set(VanillaOption.RENDER_DISTANCE, 8));
        assertEquals(8, options.getInt(VanillaOption.RENDER_DISTANCE, 0));
        assertFalse(options.withoutSupportFor(VanillaOption.PANORAMA_SPEED).set(VanillaOption.PANORAMA_SPEED, 1.0));

        FakeKeybindBridge keys = new FakeKeybindBridge();
        assertTrue(keys.setKey("key.vanta.zoom", KeyRef.keyboard(90, "key.keyboard.z")));
        assertFalse(keys.find("key.vanta.zoom").orElseThrow().isDefault());
        keys.resetAll();
        assertTrue(keys.find("key.vanta.zoom").orElseThrow().isDefault());

        FakeResourcePackBridge packs = new FakeResourcePackBridge();
        assertTrue(packs.setEnabled("programmer_art", true));
        assertTrue(packs.hasPendingChanges());
        assertTrue(packs.move("programmer_art", -1));
        assertEquals(0, packs.packs().stream().filter(p -> p.id().equals("programmer_art")).findFirst()
                .orElseThrow().position());
        packs.apply();
        assertFalse(packs.hasPendingChanges());
        assertFalse(packs.setEnabled("vanilla", false), "required pack");
    }
}
