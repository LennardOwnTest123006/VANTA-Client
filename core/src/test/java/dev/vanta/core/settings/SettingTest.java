package dev.vanta.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonPrimitive;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.cosmetics.Badge;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingTest {
    @Test
    void idsMustBeDotted() {
        assertThrows(IllegalArgumentException.class, () -> Setting.bool("nodots", SettingCategory.GENERAL, true));
        assertThrows(IllegalArgumentException.class, () -> Setting.bool("Bad.Case", SettingCategory.GENERAL, true));
    }

    @Test
    void colorEncodingUsesHex() {
        Setting<Integer> color = Setting.color("hud.testColor", SettingCategory.HUD, 0xFF7C5CFF);
        assertEquals("#FF7C5CFF", color.encode(0xFF7C5CFF).getAsString());
        assertEquals(Optional.of(0xFF7C5CFF), color.decode(new JsonPrimitive("#7C5CFF")));
        assertEquals(Optional.of(0x807C5CFF), color.decode(new JsonPrimitive("#807C5CFF")));
        assertEquals(Optional.of(5), color.decode(new JsonPrimitive(5)));
        assertTrue(color.decode(new JsonPrimitive("#zzz")).isEmpty());
        assertEquals("#FF000000", Setting.formatColor(0xFF000000));
    }

    @Test
    void enumEncodingIsLowerCaseAndCaseInsensitive() {
        Setting<Badge> badge = Setting.enumOf("cosmetics.testBadge", SettingCategory.COSMETICS, Badge.class, Badge.NONE);
        assertEquals("founder", badge.encode(Badge.FOUNDER).getAsString());
        assertEquals(Optional.of(Badge.FOUNDER), badge.decode(new JsonPrimitive("FOUNDER")));
        assertTrue(badge.decode(new JsonPrimitive("nope")).isEmpty());
        assertEquals("vanta.cosmetics.badge.founder", badge.optionLangKey(Badge.FOUNDER));
        assertEquals("FOUNDER", badge.toBridgeValue(Badge.FOUNDER));
    }

    @Test
    void rangesClampAndCoerce() {
        Setting<Integer> range = Setting.intRange("test.range", SettingCategory.GENERAL, 5, 0, 10, 1);
        assertEquals(10, range.normalize(50));
        assertEquals(0, range.normalize(-1));
        assertEquals(Optional.of(7), range.coerce(7.4));
        assertEquals(Optional.of(3), range.decode(new JsonPrimitive("3")));
        Setting<Double> dbl = Setting.doubleRange("test.dbl", SettingCategory.GENERAL, 0.5, 0, 1, 0.1);
        assertEquals(Optional.of(1.0), dbl.coerce(4));
        assertTrue(dbl.coerce("x").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Setting.intRange("t.x", SettingCategory.GENERAL, 0, 5, 1, 1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void vanillaFactoryFollowsKind() {
        Setting<?> fps = Setting.vanilla("video.testFps", SettingCategory.VIDEO, VanillaOption.FRAMERATE_LIMIT);
        assertEquals(SettingKind.INT_RANGE, fps.kind());
        assertEquals(Integer.class, fps.type());
        assertEquals(10.0, fps.min());
        assertEquals(260.0, fps.max());
        assertTrue(fps.isVanilla());
        Setting<?> clouds = Setting.vanilla("video.testClouds", SettingCategory.VIDEO, VanillaOption.CLOUDS);
        assertEquals(SettingKind.ENUM, clouds.kind());
        assertEquals("vanta.option.clouds.fast", ((Setting<String>) clouds).optionLangKey("FAST"));
        assertEquals("FANCY", ((Setting<String>) clouds).normalize("nonsense"));
        Setting<?> vsync = Setting.vanilla("video.testVsync", SettingCategory.VIDEO, VanillaOption.VSYNC);
        assertEquals(SettingKind.BOOL, vsync.kind());
        Setting<?> volume = Setting.vanilla("audio.testVolume", SettingCategory.AUDIO, VanillaOption.MASTER_VOLUME);
        assertEquals(SettingKind.DOUBLE_RANGE, volume.kind());
    }

    @Test
    void stringKeyAndActionSettings() {
        Setting<String> text = Setting.string("test.text", SettingCategory.GENERAL, "x");
        assertEquals(Setting.MAX_STRING_LENGTH, text.normalize("y".repeat(1000)).length());
        Setting<String> key = Setting.key("test.key", SettingCategory.CONTROLS, "key.keyboard.c");
        assertEquals("key.keyboard.unknown", key.normalize("  "));
        Setting<String> action = Setting.action("test.action", SettingCategory.GENERAL, "do_it");
        assertEquals(Optional.of("do_it"), action.actionId());
        assertTrue(!action.isPersisted());
        assertEquals("vanta.setting.test.action.title", action.titleKey());
    }

    @Test
    void validatorComposes() {
        Setting<Integer> even = Setting.intRange("test.even", SettingCategory.GENERAL, 2, 0, 100, 2)
                .validator(v -> v - v % 2);
        assertEquals(100, even.normalize(101));
        assertEquals(6, even.normalize(7));
        assertTrue(even.requiresRestart().needsRestart());
        assertEquals(java.util.List.of("a", "b"), even.keywords("a", "b").keywords());
    }
}
