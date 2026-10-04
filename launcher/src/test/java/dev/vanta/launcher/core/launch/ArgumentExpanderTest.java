package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.Argument;
import dev.vanta.launcher.core.model.Rule;
import dev.vanta.launcher.core.util.OsInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgumentExpanderTest {

    @Test
    void expandsPlaceholders() {
        final ArgumentExpander e = new ArgumentExpander(Map.of("auth_player_name", "Steve", "natives_directory", "C:\\Games\\natives"));
        assertEquals("Steve", e.expand("${auth_player_name}"));
        assertEquals("-Djava.library.path=C:\\Games\\natives", e.expand("-Djava.library.path=${natives_directory}"));
        assertEquals("plain", e.expand("plain"));
        assertTrue(e.unresolved().isEmpty());
    }

    @Test
    void unknownPlaceholdersAreKeptAndReported() {
        final ArgumentExpander e = new ArgumentExpander(Map.of());
        assertEquals("--path ${quickPlayPath}", e.expand("--path ${quickPlayPath}"));
        assertEquals(Set.of("quickPlayPath"), e.unresolved());
    }

    @Test
    void expandAllFiltersByRulesAndFlattensLists() {
        final RuleEvaluator evaluator = new RuleEvaluator(new OsInfo("linux", "x64", ""), Map.of("has_custom_resolution", true));
        final List<Argument> args = List.of(
            Argument.of("--username"),
            Argument.of("${auth_player_name}"),
            new Argument(List.of(new Rule("allow", null, Map.of("is_demo_user", true))), List.of("--demo")),
            new Argument(List.of(new Rule("allow", null, Map.of("has_custom_resolution", true))),
                List.of("--width", "${resolution_width}", "--height", "${resolution_height}")),
            new Argument(List.of(new Rule("allow", new Rule.Os("osx", null, null), null)), List.of("-XstartOnFirstThread")));
        final ArgumentExpander e = new ArgumentExpander(Map.of("auth_player_name", "Alex", "resolution_width", "1920", "resolution_height", "1080"));
        assertEquals(List.of("--username", "Alex", "--width", "1920", "--height", "1080"), e.expandAll(args, evaluator));
    }

    @Test
    void valuesContainingDollarAndBackslashSurvive() {
        final ArgumentExpander e = new ArgumentExpander(Map.of("game_directory", "D:\\Games\\$VANTA\\instance"));
        assertEquals("D:\\Games\\$VANTA\\instance", e.expand("${game_directory}"));
    }
}
