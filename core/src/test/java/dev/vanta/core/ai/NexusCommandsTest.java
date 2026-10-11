package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.vanta.core.crosshair.CrosshairShape;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.lab.LabFeature;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The instant commands: a table of English and German phrasings (the players' own words included) with the actions
 * each one must give on the default HUD and crosshair, the texts that must stay "not understood" (they go to the
 * model), and the parts that read the live state.
 */
class NexusCommandsTest {
    @TempDir
    Path dir;
    ServicesFixture fixture;
    VantaServices services;
    NexusCommands commands;

    @BeforeEach
    void setUp() {
        fixture = new ServicesFixture(dir);
        services = fixture.services;
        commands = services.nexus().commands();
    }

    private static final String MUSIC_OFF = "{\"type\":\"setting.set\",\"id\":\"audio.music\",\"value\":0}";
    private static final String CHAT_SMALLER = "{\"type\":\"setting.set\",\"id\":\"accessibility.chatScale\","
            + "\"value\":\"-0.2\"},{\"type\":\"setting.set\",\"id\":\"accessibility.chatWidth\",\"value\":\"-0.2\"}";
    private static final String CHAT_BIGGER = "{\"type\":\"setting.set\",\"id\":\"accessibility.chatScale\","
            + "\"value\":\"+0.2\"},{\"type\":\"setting.set\",\"id\":\"accessibility.chatWidth\",\"value\":\"+0.2\"}";
    private static final String ARRANGE = "{\"type\":\"hud.arrange\"}";

    private static String volume(String id, String value) {
        return "{\"type\":\"setting.set\",\"id\":\"audio." + id + "\",\"value\":" + value + "}";
    }

    private static String hide(String element) {
        return "{\"type\":\"hud.set\",\"element\":\"" + element + "\",\"visible\":false}";
    }

    private static String show(String element) {
        return "{\"type\":\"hud.set\",\"element\":\"" + element + "\",\"visible\":true}";
    }

    private static String move(String element, String anchor) {
        return "{\"type\":\"hud.set\",\"element\":\"" + element + "\",\"anchor\":\"" + anchor + "\"}";
    }

    private static String crosshair(String fields) {
        return "{\"type\":\"crosshair.set\"," + fields + "}";
    }

    /** Phrasing and the actions it must give, as a JSON array without the brackets. */
    private static final String[][] TABLE = {
            // The reported 1.5.0 requests, in the players' own words.
            {"make music off", MUSIC_OFF},
            {"make the crossover circle", crosshair("\"shape\":\"circle\"")},
            {"make the chat smaller", CHAT_SMALLER},
            {"delete the cordinates", hide("coordinates")},
            {"sort the guis", ARRANGE},
            // Sound.
            {"music off", MUSIC_OFF},
            {"Musik aus", MUSIC_OFF},
            {"mach die Musik aus", MUSIC_OFF},
            {"turn off the music", MUSIC_OFF},
            {"Kannst du bitte die Musik ausmachen?", MUSIC_OFF},
            {"please make the music off thanks", MUSIC_OFF},
            {"MUSIC OFF!!!", MUSIC_OFF},
            {"musc off", MUSIC_OFF},
            {"music on", volume("music", "1")},
            {"schalte die musik ein", volume("music", "1")},
            {"musik an", volume("music", "1")},
            {"mute", volume("master", "0")},
            {"mute all sound", volume("master", "0")},
            {"alles stumm", volume("master", "0")},
            {"schalte den Ton aus", volume("master", "0")},
            {"unmute", volume("master", "1")},
            {"music 30", volume("music", "0.3")},
            {"musik 30", volume("music", "0.3")},
            {"music volume 30 %", volume("music", "0.3")},
            {"lautstärke 50", volume("master", "0.5")},
            {"stell die Lautstärke auf 50", volume("master", "0.5")},
            {"master volume 50 %", volume("master", "0.5")},
            {"volume 0.5", volume("master", "0.5")},
            {"gesamtlautstärke 80 %", volume("master", "0.8")},
            {"set the weather to 20 percent", volume("weather", "0.2")},
            {"music to half", volume("music", "0.5")},
            {"musik auf null", volume("music", "0")},
            {"music and weather off", volume("music", "0") + "," + volume("weather", "0")},
            {"mute music and weather", volume("music", "0") + "," + volume("weather", "0")},
            {"regen aus", volume("weather", "0")},
            {"tiere aus", volume("neutral", "0")},
            {"spieler 50", volume("players", "0.5")},
            {"make the music louder", volume("music", "\"+0.2\"")},
            {"turn the music up", volume("music", "\"+0.2\"")},
            {"mach die musik ein bisschen leiser", volume("music", "\"-0.2\"")},
            {"monster leiser", volume("hostile", "\"-0.2\"")},
            {"turn down the volume", volume("master", "\"-0.2\"")},
            {"subtitles on", "{\"type\":\"setting.set\",\"id\":\"audio.subtitles\",\"value\":true}"},
            {"untertitel aus", "{\"type\":\"setting.set\",\"id\":\"audio.subtitles\",\"value\":false}"},
            // Crosshair.
            {"make the crosshair a circle", crosshair("\"shape\":\"circle\"")},
            {"crosshiar circle", crosshair("\"shape\":\"circle\"")},
            {"fadenkreuz kreis", crosshair("\"shape\":\"circle\"")},
            {"red dot crosshair", crosshair("\"color\":\"red\",\"shape\":\"dot\"")},
            {"mach das fadenkreuz zu einem roten punkt", crosshair("\"color\":\"red\",\"shape\":\"dot\"")},
            {"make the crosshair an orange circle", crosshair("\"color\":\"orange\",\"shape\":\"circle\"")},
            {"grünes fadenkreuz", crosshair("\"color\":\"green\"")},
            {"fadenkreuz als quadrat", crosshair("\"shape\":\"square\"")},
            {"crosshair plus dot", crosshair("\"shape\":\"plus_dot\"")},
            {"crosshair cross", crosshair("\"shape\":\"cross\"")},
            {"crosshair bigger", crosshair("\"size\":7")},
            {"fadenkreuz größer", crosshair("\"size\":7")},
            {"crosshair smaller", crosshair("\"size\":3")},
            {"make the crosshair red and bigger", crosshair("\"color\":\"red\",\"size\":7")},
            {"make the crosshair red and a circle", crosshair("\"color\":\"red\",\"shape\":\"circle\"")},
            {"crosshair size 10", crosshair("\"size\":10")},
            {"crosshair thicker", crosshair("\"thickness\":2")},
            {"crosshair gap 3", crosshair("\"gap\":3")},
            {"crosshair opacity 50", crosshair("\"opacity\":0.5")},
            {"vanilla crosshair", crosshair("\"vanilla\":true")},
            {"use the normal minecraft crosshair", crosshair("\"vanilla\":true")},
            {"standard fadenkreuz", crosshair("\"vanilla\":true")},
            // Chat.
            {"chat smaller", CHAT_SMALLER},
            {"mach den chat kleiner", CHAT_SMALLER},
            {"make the chat bigger", CHAT_BIGGER},
            {"chat größer", CHAT_BIGGER},
            {"chat narrower", "{\"type\":\"setting.set\",\"id\":\"accessibility.chatWidth\",\"value\":\"-0.2\"}"},
            {"chat breiter", "{\"type\":\"setting.set\",\"id\":\"accessibility.chatWidth\",\"value\":\"+0.2\"}"},
            {"make the chat text smaller",
                    "{\"type\":\"setting.set\",\"id\":\"accessibility.chatScale\",\"value\":\"-0.2\"}"},
            {"chat opacity 50 %", "{\"type\":\"setting.set\",\"id\":\"accessibility.chatOpacity\",\"value\":0.5}"},
            {"chat deckkraft 70", "{\"type\":\"setting.set\",\"id\":\"accessibility.chatOpacity\",\"value\":0.7}"},
            // Sorting.
            {"sort the HUD", ARRANGE},
            {"tidy up my gui", ARRANGE},
            {"organize my widgets", ARRANGE},
            {"line up the widgets", ARRANGE},
            {"sortiere das hud", ARRANGE},
            {"hud sortieren", ARRANGE},
            {"ordne die guis an", ARRANGE},
            {"räum das hud auf", ARRANGE},
            {"arrange the hud in the top right", "{\"type\":\"hud.arrange\",\"corner\":\"top_right\"}"},
            {"sortiere die hud oben links", "{\"type\":\"hud.arrange\",\"corner\":\"top_left\"}"},
            // Removing, showing, only, moving.
            {"delete the coordinates", hide("coordinates")},
            {"entferne die koordinaten", hide("coordinates")},
            {"lösche die koordinaten", hide("coordinates")},
            {"blende die koordinaten aus", hide("coordinates")},
            {"koordinatn weg", hide("coordinates")},
            {"hide xyz", hide("coordinates")},
            {"cords off", hide("coordinates")},
            {"verstecke die fps", hide("fps")},
            {"rüstung weg", hide("armor")},
            {"effekte ausblenden", hide("potion_effects")},
            {"remove the fps and the ping", hide("fps") + "," + hide("ping")},
            {"fps and ping off", hide("fps") + "," + hide("ping")},
            {"hide fps, ping, coordinates and the clock", hide("fps") + "," + hide("ping") + ","
                    + hide("coordinates") + "," + hide("clock")},
            {"show memory", show("memory")},
            {"zeige den arbeitsspeicher", show("memory")},
            {"add the cps", show("cps")},
            {"only show fps and coordinates", "{\"type\":\"hud.only\",\"elements\":[\"fps\",\"coordinates\"]}"},
            {"show only fps and coords", "{\"type\":\"hud.only\",\"elements\":[\"fps\",\"coordinates\"]}"},
            {"nur fps und koordinaten", "{\"type\":\"hud.only\",\"elements\":[\"fps\",\"coordinates\"]}"},
            {"zeige nur fps, ping und koordinaten",
                    "{\"type\":\"hud.only\",\"elements\":[\"fps\",\"ping\",\"coordinates\"]}"},
            {"move fps to the top right", move("fps", "top_right")},
            {"Move the FPS counter to the bottom right", move("fps", "bottom_right")},
            {"put the fps in the bottom left corner", move("fps", "bottom_left")},
            {"verschiebe die fps nach oben links", move("fps", "top_left")},
            {"fps nach unten rechts", move("fps", "bottom_right")},
            {"ping links unten", move("ping", "bottom_left")},
            {"move fps and ping to the top right", move("fps", "top_right") + "," + move("ping", "top_right")},
            {"move the clock to the top left and the ping to the bottom left",
                    move("clock", "top_left") + "," + move("ping", "bottom_left")},
            {"make my hud minimal", "{\"type\":\"hud.preset\",\"preset\":\"minimal\"}"},
            {"pvp hud", "{\"type\":\"hud.preset\",\"preset\":\"pvp\"}"},
            {"hide the hud", "{\"type\":\"setting.set\",\"id\":\"hud.enabled\",\"value\":false}"},
            // Several commands in one sentence.
            {"make music off and the crosshair a circle", MUSIC_OFF + "," + crosshair("\"shape\":\"circle\"")},
            {"music off, crosshair circle and chat smaller", MUSIC_OFF + "," + crosshair("\"shape\":\"circle\"")
                    + "," + CHAT_SMALLER},
            {"Musik aus und das Fadenkreuz ein Kreis", MUSIC_OFF + "," + crosshair("\"shape\":\"circle\"")},
            {"music off. crosshair circle.", MUSIC_OFF + "," + crosshair("\"shape\":\"circle\"")},
            {"sort the hud and mute", ARRANGE + "," + volume("master", "0")},
    };

    @Test
    void everyPhrasingOfTheTableGivesItsActions() {
        assertTrue(TABLE.length >= 60, "at least 60 phrasings: " + TABLE.length);
        List<String> failures = new ArrayList<>();
        for (String[] row : TABLE) {
            NexusCommands.Parse parse = commands.parse(row[0]);
            JsonArray expected = JsonParser.parseString("[" + row[1] + "]").getAsJsonArray();
            if (!parse.understood()) {
                failures.add("\"" + row[0] + "\" not understood: " + parse.reason());
            } else if (!parse.toJsonArray().equals(expected)) {
                failures.add("\"" + row[0] + "\" gave " + parse.toJsonArray() + " instead of " + expected);
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void everyActionOfTheTableIsValid() {
        for (String[] row : TABLE) {
            NexusUndo.Turn turn = services.nexusUndo().begin();
            NexusActions.Outcome outcome = services.nexusActions().execute(commands.parse(row[0]).toJsonArray(), turn);
            assertEquals(List.of(), outcome.rejected(), row[0]);
            assertFalse(outcome.applied().isEmpty(), row[0]);
            // "sort the HUD" on the already tidy default HUD changes nothing, so there is nothing to undo.
            if (services.nexusUndo().commit(turn, row[0])) {
                assertTrue(services.nexusUndo().undoLast(), row[0]);
            }
            assertEquals(dev.vanta.core.hud.HudPresets.defaultPreset().layout(), services.hud().layout(), row[0]);
        }
    }

    /** Texts that must go to the model: questions, negations, unknown words, ambiguities, contradictions. */
    private static final List<String> NOT_UNDERSTOOD = List.of(
            "", "   ", "hello", "what is my fps", "why is the music off", "is the music off?",
            "don't turn the music off", "can't turn off the music", "make it better", "smaller", "red", "circle",
            "narrower", "fps", "music", "move fps", "hide everything except fps and coordinates",
            "Hide every HUD widget except FPS and coordinates", "create a recording profile",
            "Add a waypoint here called Home", "Hide Home", "Remove Home", "Turn on the dynamic HUD",
            "crosshair red and blue", "make the chat smaller and bigger", "sort the hud left", "make the fps bigger",
            "hide the crosshair", "music off or on", "music 150", "crosshair size 1.5", "crosshair 10",
            "fps scale 2", "remove all widgets", "show all", "minimal", "Strip my HUD down to the essentials", "pinq",
            "blck crosshair", "music off and fps", "fps and sort the hud", "vanilla red crosshair",
            "mute the fps", "show the chat", "move the hud to the left", "hide the hud elements");

    @Test
    void unknownAmbiguousOrContradictoryTextIsNotUnderstood() {
        for (String text : NOT_UNDERSTOOD) {
            NexusCommands.Parse parse = commands.parse(text);
            assertFalse(parse.understood(), "\"" + text + "\" must go to the model, got " + parse.toJsonArray());
            assertTrue(parse.actions().isEmpty());
            assertFalse(parse.reason().isBlank(), text);
        }
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i <= NexusCommands.MAX_WORDS; i++) {
            longText.append("music off and ");
        }
        assertFalse(commands.parse(longText.toString()).understood(), "long texts are left to the model");
    }

    @Test
    void theParseReadsTheLiveHudAndCrosshair() {
        // A hidden widget is shown again by its own id; one that is not on the HUD is added by its type.
        services.hud().setLayout(services.hud().layout().with(services.hud().layout().find("ping").orElseThrow()
                .withEnabled(false)));
        assertEquals(JsonParser.parseString("[" + show("ping") + "]"), commands.parse("show the ping").toJsonArray());
        assertEquals(JsonParser.parseString("[" + show("keystrokes") + "]"), commands.parse("show keystrokes")
                .toJsonArray());
        // Hiding what is not shown and showing what is shown are notes, not actions.
        NexusCommands.Parse hidden = commands.parse("hide the ping and the memory");
        assertTrue(hidden.understood());
        assertTrue(hidden.actions().isEmpty());
        assertEquals(List.of("Ping: already hidden.", "Memory: already hidden."), hidden.notes());
        assertEquals(List.of("FPS: already shown."), commands.parse("zeige die fps an").notes());
        // Moving a hidden widget shows it.
        assertEquals(JsonParser.parseString("[{\"type\":\"hud.set\",\"element\":\"ping\",\"visible\":true,"
                + "\"anchor\":\"top_right\"}]"), commands.parse("ping top right").toJsonArray());
        // The frame time graph is offered only while its Lab feature is on.
        NexusCommands.Parse graph = commands.parse("show the frame time graph");
        assertTrue(graph.actions().isEmpty());
        assertEquals(List.of("Frame time graph: only offered while its Vanta Lab feature is on."), graph.notes());
        services.lab().set(LabFeature.FRAMETIME_GRAPH, true);
        assertEquals(JsonParser.parseString("[" + show("frametime_graph") + "]"),
                commands.parse("show the frame time graph").toJsonArray());

        // Bigger and smaller step from the crosshair's size now, within 1..32.
        services.crosshair().setStyle(services.crosshair().style().withSize(CrosshairStyle.MAX_SIZE - 1)
                .withThickness(1));
        assertEquals(JsonParser.parseString("[" + crosshair("\"size\":" + CrosshairStyle.MAX_SIZE) + "]"),
                commands.parse("crosshair bigger").toJsonArray());
        assertEquals(JsonParser.parseString("[" + crosshair("\"thickness\":1") + "]"),
                commands.parse("crosshair thinner").toJsonArray());
        services.crosshair().setStyle(services.crosshair().style().withSize(1));
        assertEquals(JsonParser.parseString("[" + crosshair("\"size\":1") + "]"),
                commands.parse("crosshair smaller").toJsonArray());
    }

    @Test
    void typosNeedTheFirstLetterAndOneUniqueCloseWord() {
        assertEquals(1, NexusCommands.editDistance("crosshair", "crosshiar"), "a swap is one edit");
        assertEquals(1, NexusCommands.typoDistance("coordinates", "cordinates"));
        assertEquals(2, NexusCommands.typoDistance("koordinaten", "kordinatn"));
        assertEquals(Integer.MAX_VALUE, NexusCommands.typoDistance("ping", "pink"), "short words must match");
        assertEquals(Integer.MAX_VALUE, NexusCommands.typoDistance("music", "nusic"), "the first letter counts");
        assertEquals(Integer.MAX_VALUE, NexusCommands.typoDistance("music", "musicals"));
        assertEquals("groesser fadenkreuz", NexusCommands.normalize("Größer Fadenkreuz"));
        assertEquals("0.5", NexusCommands.normalize("0,5"));
        assertEquals("top left", NexusCommands.normalize("top-left"));
    }

    @Test
    void theAnswerSaysWhatReallyHappened() {
        NexusUndo.Turn turn = services.nexusUndo().begin();
        NexusCommands.Parse parse = commands.parse("chat smaller and music 40 and hide the coordinates");
        NexusActions.Outcome outcome = services.nexusActions().execute(parse.toJsonArray(), turn);
        assertEquals("Done: chat text size 80 %; chat width 80 %; music volume 40 %; coordinates hidden.",
                commands.reply(outcome, parse.notes()));
        // At the readable minimum the answer says so.
        services.settings().setRaw(dev.vanta.core.settings.VantaSettings.ACCESSIBILITY_CHAT_SCALE, 0.5);
        NexusActions.Outcome floor = services.nexusActions().execute(commands.parse("chat text smaller")
                .toJsonArray(), services.nexusUndo().begin());
        assertEquals("Done: chat text size 50 % (the smallest the assistant sets).", commands.reply(floor, List.of()));
        assertEquals("Nothing was changed.", commands.reply(new NexusActions.Outcome(List.of(), List.of()),
                List.of()));
        assertEquals("Done: subtitles on.", commands.reply(services.nexusActions().execute(
                commands.parse("subtitles on").toJsonArray(), services.nexusUndo().begin()), List.of()));
        assertEquals(CrosshairShape.CROSS, services.crosshair().style().shape());
        for (String key : NexusCommands.labelKeys()) {
            assertTrue(Lang.has(key), key);
        }
        assertEquals("coordinates", NexusCommands.widgetName(HudWidgetType.COORDINATES));
        assertEquals("FPS", NexusCommands.widgetName(HudWidgetType.FPS));
        assertEquals("Minecraft version", NexusCommands.widgetName(HudWidgetType.MINECRAFT_VERSION));
    }
}
