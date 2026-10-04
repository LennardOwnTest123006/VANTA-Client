package dev.vanta.core.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.vanta.core.config.JsonStore;
import dev.vanta.core.config.MutableClock;
import dev.vanta.core.config.VantaPaths;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CosmeticsTest {
    @TempDir
    Path dir;

    @Test
    void builtInThemesLoadWithValidTokens() {
        CosmeticsRegistry registry = new CosmeticsRegistry();
        assertEquals(CosmeticsRegistry.BUILT_IN_THEME_IDS, registry.themes().stream().map(UiThemeDefinition::id).toList());
        UiThemeDefinition dark = registry.findTheme("vanta-dark").orElseThrow();
        assertTrue(dark.tokenOverrides().isEmpty());
        assertTrue(dark.builtIn());
        UiThemeDefinition midnight = registry.findTheme("midnight").orElseThrow();
        assertEquals(Optional.of(0xFF4F8DFF), midnight.token("accent.violet"));
        assertEquals(dark, registry.themeOrDefault("does-not-exist"));
        assertEquals(5, registry.backgrounds().size());
        assertEquals(4, registry.badges().size());
        assertEquals(midnight, UiThemeDefinition.fromJson(midnight.toJson(), true));
    }

    @Test
    void themeValidation() {
        assertThrows(IllegalArgumentException.class, () -> new UiThemeDefinition("Bad Id", "x", Map.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new UiThemeDefinition("ok-id", "x",
                Map.of("../evil", 1), false));
        assertThrows(IllegalArgumentException.class, () -> UiThemeDefinition.fromJson(JsonParser.parseString(
                "{\"id\":\"x1\",\"tokens\":{\"accent.violet\":\"purple\"}}").getAsJsonObject(), false));
        assertThrows(IllegalArgumentException.class, () -> UiThemeDefinition.fromJson(JsonParser.parseString(
                "{\"tokens\":{}}").getAsJsonObject(), false));
    }

    @Test
    void packsLoadFromDirectoryAndInvalidOnesAreReported() throws IOException {
        VantaPaths paths = new VantaPaths(dir);
        paths.createDirectories();
        Files.writeString(paths.cosmeticPacksDir().resolve("good.json"), """
                {"schemaVersion": 1, "id": "my-pack", "name": "My Pack", "author": "Me",
                 "themes": [{"id": "sunset", "name": "Sunset", "tokens": {"accent.violet": "#FF8844"}},
                            {"id": "midnight", "name": "Clash", "tokens": {}}]}
                """);
        Files.writeString(paths.cosmeticPacksDir().resolve("bad.json"), "{\"id\": \"bad\"}");
        Files.writeString(paths.cosmeticPacksDir().resolve("broken.json"), "not json");
        JsonStore json = new JsonStore(MutableClock.standard());
        SettingsStore settings = new SettingsStore(VantaSettings.registry(), json, paths.settingsFile(), Optional.empty());
        CosmeticsRegistry registry = new CosmeticsRegistry();
        CosmeticsStore store = new CosmeticsStore(settings, registry, json, paths);
        List<CosmeticsSelection> seen = new ArrayList<>();
        store.onSelectionChanged(seen::add);
        store.load();
        assertEquals(1, registry.packs().size());
        assertTrue(registry.findTheme("sunset").isPresent());
        assertEquals("Clash".equals(registry.findTheme("midnight").orElseThrow().name()), false,
                "pack theme clashing with a built-in is skipped");
        assertEquals(2, store.loadErrors().size());
        assertEquals(1, seen.size());

        assertTrue(store.selectTheme("sunset"));
        assertFalse(store.selectTheme("missing"));
        assertEquals("sunset", store.activeTheme().id());
        assertEquals("sunset", store.selection().themeId());
        assertEquals(2, seen.size());
        store.selectBadge(Badge.FOUNDER);
        assertEquals(Badge.FOUNDER, store.selection().badge());
        settings.set(VantaSettings.HUD_ENABLED, false);
        assertEquals(3, seen.size(), "non-cosmetic settings do not notify");
        store.save();
        assertTrue(Files.exists(paths.cosmeticsFile()));
        assertTrue(store.isOwned(store.activeTheme()));

        CosmeticsSelection selection = store.selection();
        assertEquals(selection, CosmeticsSelection.fromJson(selection.toJson()));
        CosmeticsSelection tolerant = CosmeticsSelection.fromJson(JsonParser.parseString(
                "{\"theme\":\"../x\",\"badge\":\"nope\",\"menuBackground\":\"solid\"}").getAsJsonObject());
        assertEquals(UiThemeDefinition.DEFAULT_ID, tolerant.themeId());
        assertEquals(Badge.NONE, tolerant.badge());
        assertEquals(MenuBackground.SOLID, tolerant.menuBackground());
        CosmeticsSelection.DEFAULT.applyTo(settings);
        assertEquals(CosmeticsSelection.DEFAULT, store.selection());
    }

    @Test
    void packValidation() {
        assertThrows(IllegalArgumentException.class, () -> CosmeticPack.fromJson(JsonParser.parseString(
                "{\"id\":\"p\",\"name\":\"P\",\"themes\":[]}").getAsJsonObject()), "needs at least one theme");
        assertThrows(IllegalArgumentException.class, () -> CosmeticPack.fromJson(JsonParser.parseString(
                "{\"schemaVersion\": 9, \"id\":\"pk\",\"name\":\"P\",\"themes\":[{\"id\":\"t1\"}]}").getAsJsonObject()));
        CosmeticPack pack = CosmeticPack.fromJson(JsonParser.parseString(
                "{\"id\":\"pk\",\"name\":\"P\",\"themes\":[{\"id\":\"t1\",\"tokens\":{\"bg.base\":\"#000000\"}}]}")
                .getAsJsonObject());
        assertEquals(pack.themes(), CosmeticPack.fromJson(pack.toJson()).themes());
    }

    @Test
    void hudThemesBadgesAndEnums() {
        assertTrue(HudTheme.CLEAN.style().hasPanel());
        assertFalse(HudTheme.MINIMAL.style().hasPanel());
        assertTrue(HudTheme.GLASS.style().blur());
        assertEquals(HudTheme.OUTLINE, HudTheme.fromId("outline"));
        assertEquals(HudTheme.CLEAN, HudTheme.fromId("nope"));
        assertEquals(Badge.SUPPORTER, Badge.fromId("supporter"));
        assertEquals("", Badge.NONE.glyph());
        assertFalse(Badge.FOUNDER.glyph().isEmpty());
        assertEquals("violet_horizon", MenuBackground.VIOLET_HORIZON.id());
        assertEquals("vanta.cosmetics.particles.embers", MenuParticles.EMBERS.langKey());
    }

    @Test
    void particleSystemIsDeterministicAndBounded() {
        ParticleSystem a = new ParticleSystem(MenuParticles.EMBERS, 42);
        ParticleSystem b = new ParticleSystem(MenuParticles.EMBERS, 42);
        for (int i = 0; i < 300; i++) {
            a.update(16, 854, 480);
            b.update(16, 854, 480);
        }
        assertEquals(a.particles(), b.particles(), "same seed, same frames");
        assertTrue(a.count() > 0);
        assertTrue(a.count() <= a.targetCount(854, 480));
        for (ParticleSystem.Particle p : a.particles()) {
            assertTrue(p.alpha() >= 0 && p.alpha() <= 1);
            assertTrue(p.size() > 0);
        }
        ParticleSystem c = new ParticleSystem(MenuParticles.EMBERS, 7);
        for (int i = 0; i < 300; i++) {
            c.update(16, 854, 480);
        }
        assertFalse(a.particles().equals(c.particles()), "different seed, different frames");
        a.setKind(MenuParticles.NONE);
        a.update(16, 854, 480);
        assertEquals(0, a.count());
        assertEquals(0, a.targetCount(854, 480));
        ParticleSystem dust = new ParticleSystem(MenuParticles.DUST, 1);
        dust.update(5000, 100, 100);
        assertTrue(dust.count() <= dust.targetCount(100, 100));
    }
}
