package dev.vanta.core.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingsRegistry;
import dev.vanta.core.settings.SettingsSearchIndex;
import dev.vanta.core.settings.VantaSettings;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SearchIndexTest {

    private static SearchIndex<String> widgetIndex() {
        SearchIndex<String> index = new SearchIndex<>();
        for (HudWidgetType type : HudWidgetType.values()) {
            index.add("widget:" + type.id(), Lang.tr(type.langKey()), List.of(type.id()), Lang.tr(type.descriptionKey()));
        }
        for (Setting<?> setting : VantaSettings.all()) {
            index.add("setting:" + setting.id(), Lang.tr(setting.titleKey()), setting.keywords(),
                    Lang.tr(setting.descriptionKey()));
        }
        return index;
    }

    @Test
    void fpsFindsWidgetAndFpsLimitSetting() {
        List<SearchHit<String>> hits = widgetIndex().search("fps");
        List<String> ids = hits.stream().map(SearchHit::item).toList();
        assertEquals("widget:fps", ids.get(0), "exact title match ranks first: " + ids);
        assertTrue(ids.contains("setting:performance.fpsLimitPreset"), ids.toString());
        assertTrue(ids.contains("setting:video.framerateLimit"), ids.toString());
        SearchHit<String> top = hits.get(0);
        assertEquals(List.of(new MatchRange(0, 3)), top.highlights());
        assertTrue(top.matchedFields().contains(SearchField.TITLE));
    }

    @Test
    void crosshairFindsCrosshairSettingsAndFuzzyTypoWorks() {
        SearchIndex<String> index = widgetIndex();
        List<String> exact = index.search("crosshair").stream().map(SearchHit::item).toList();
        assertTrue(exact.contains("setting:cosmetics.crosshairPreset"), exact.toString());
        assertTrue(exact.contains("setting:cosmetics.openCrosshair"), exact.toString());
        assertTrue(exact.contains("widget:crosshair"), exact.toString());

        List<String> fuzzy = index.search("crosshiar").stream().map(SearchHit::item).toList();
        assertFalse(fuzzy.isEmpty(), "fuzzy query should match");
        assertTrue(fuzzy.contains("widget:crosshair"), fuzzy.toString());
        assertTrue(fuzzy.contains("setting:cosmetics.crosshairPreset"), fuzzy.toString());
    }

    @Test
    void shortWordsNeverFuzz() {
        SearchIndex<String> index = new SearchIndex<>();
        index.add("a", "Fog", List.of(), "");
        index.add("b", "FPS", List.of(), "");
        assertTrue(index.search("fpz").isEmpty());
        assertEquals(1, index.search("fps").size());
    }

    @Test
    void allQueryTokensMustMatchAndTitleOutranksDescription() {
        SearchIndex<String> index = new SearchIndex<>();
        index.add("title", "Render distance", List.of(), "Chunks drawn around you");
        index.add("desc", "Simulation distance", List.of(), "Chunks in which render happens");
        index.add("other", "Mouse sensitivity", List.of(), "Camera speed");
        List<SearchHit<String>> hits = index.search("render distance");
        assertEquals(List.of("title", "desc"), hits.stream().map(SearchHit::item).toList());
        assertTrue(hits.get(0).score() > hits.get(1).score());
        assertTrue(index.search("render mouse").isEmpty(), "AND semantics");
        assertTrue(index.search("   ").isEmpty());
        assertEquals(1, index.search("distance", 1).size());
    }

    @Test
    void prefixAndCamelCaseTokens() {
        assertEquals(List.of("fps", "limit", "preset"), Fuzzy.words("fpsLimitPreset"));
        assertEquals(List.of("1", "21", "11"), Fuzzy.words("1.21.11"));
        assertEquals(List.of("hud", "editor"), Fuzzy.words("HUD-Editor"));
        SearchIndex<String> index = new SearchIndex<>();
        index.add("x", "Notification duration", List.of(), "");
        assertEquals(1, index.search("notif").size());
        assertEquals(List.of(new MatchRange(0, 5)), index.search("notif").get(0).highlights());
    }

    @Test
    void damerauDistanceHandlesTranspositions() {
        assertEquals(1, Fuzzy.damerauDistance("crosshair", "crosshiar", 2));
        assertEquals(0, Fuzzy.damerauDistance("same", "same", 1));
        assertEquals(2, Fuzzy.damerauDistance("abcd", "abxy", 1), "early exit returns max+1");
        assertEquals(1, Fuzzy.damerauDistance("kitten", "kittens", 1));
    }

    @Test
    void mergesOverlappingHighlights() {
        List<MatchRange> merged = SearchIndex.merge(List.of(new MatchRange(5, 8), new MatchRange(0, 3),
                new MatchRange(2, 6)));
        assertEquals(List.of(new MatchRange(0, 8)), merged);
    }

    @Test
    void fiveHundredItemsSearchUnderOneMillisecond() {
        SearchIndex<Integer> index = new SearchIndex<>();
        String[] words = {"render", "distance", "shadow", "entity", "particle", "volume", "menu", "scale", "hud",
                "widget", "profile", "crosshair", "notification", "keybind", "performance"};
        for (int i = 0; i < 500; i++) {
            String title = words[i % words.length] + " " + words[(i * 7) % words.length] + " " + i;
            index.add(i, title, List.of(words[(i * 3) % words.length]), "description " + words[(i * 5) % words.length]);
        }
        for (int i = 0; i < 300; i++) {
            index.search("render dist");
            index.search("crosshiar");
        }
        int iterations = 2000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            index.search(i % 2 == 0 ? "render dist" : "crosshiar");
        }
        double averageMs = (System.nanoTime() - start) / 1e6 / iterations;
        assertTrue(averageMs < 1.0, String.format(Locale.ROOT, "average %.3f ms per search", averageMs));
    }

    @Test
    void settingsSearchIndexAndGlobalSearch() {
        SettingsRegistry registry = VantaSettings.registry();
        SettingsSearchIndex settings = new SettingsSearchIndex(registry);
        assertEquals(registry.size(), settings.size());
        assertEquals(VantaSettings.ACCESSIBILITY_REDUCED_MOTION, settings.search("reduce motion").get(0).item());

        FakeKeybindBridge keys = new FakeKeybindBridge();
        GlobalSearch global = new GlobalSearch(registry, ActionEntry.builtIns(), keys::all);
        List<GlobalSearchResult> results = global.search("zoom", 10);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> r instanceof GlobalSearchResult.SettingResult s
                && s.setting().equals(VantaSettings.ZOOM_ENABLED)));
        assertTrue(results.stream().anyMatch(r -> r instanceof GlobalSearchResult.KeybindResult k
                && k.binding().id().equals("key.vanta.zoom")));
        List<GlobalSearchResult> editor = global.search("hud editor", 5);
        assertTrue(editor.stream().anyMatch(r -> r instanceof GlobalSearchResult.ActionResult a
                && a.action().id().equals("open_hud_editor")), editor.toString());
        assertTrue(editor.size() <= 5);
    }
}
