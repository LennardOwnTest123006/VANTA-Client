package dev.vanta.core.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vanta.core.crosshair.CrosshairColors;
import dev.vanta.core.crosshair.CrosshairShape;
import dev.vanta.core.crosshair.CrosshairStore;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudAnchor;
import dev.vanta.core.hud.HudArranger;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudStore;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.settings.VantaSettings;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instant commands (from client 1.6.0 on): a deterministic, rule-based reading of the everyday requests (sound,
 * crosshair, chat size, the HUD) in English and German, turned into the same action objects the model returns, so
 * {@link NexusAssistant} applies them through {@link NexusActions} at once, without asking the Local AI.
 * <p>
 * The reading is strict on purpose. The text is lower-cased, umlauts are spelled out ({@code ä} is {@code ae}), and it
 * is cut into clauses at "and", "und", "then", "dann", commas, semicolons and the ends of sentences. Every word of every clause must be a
 * known word (a widget, a sound channel, a verb, a colour, ...), a known filler word ("please", "make", "the", "bitte",
 * "mach", "die", ...) or a small typo of a known word (one letter wrong, missing, extra or swapped, two in words of
 * eight letters or more, the first letter right, only one known word that close). A clause may leave out what the
 * clause before it said ("remove the FPS and the ping", "music and weather off", "make the crosshair red and
 * bigger"). Anything else, an unknown word, two meanings in one clause, a contradiction, a number out of range, makes
 * the whole text "not understood", and the assistant asks the model as before: the parser never guesses.
 * <p>
 * Render-thread only, like every core service: the parse reads the live HUD layout and crosshair.
 */
public final class NexusCommands {
    /** Step of "chat smaller / bigger / narrower / wider" on the chat text size and width (as the prompt says). */
    public static final String CHAT_STEP = "0.2";
    /** Step of "louder" and "quieter" on a volume. */
    public static final String VOLUME_STEP = "0.2";
    /** Pixels "crosshair bigger / smaller" add or take away. */
    public static final int CROSSHAIR_SIZE_STEP = 2;
    /** Pixels "crosshair thicker / thinner" add or take away. */
    public static final int CROSSHAIR_THICKNESS_STEP = 1;
    /** Longer texts are left to the model. */
    public static final int MAX_WORDS = 40;

    /** What a text was read as. */
    public record Parse(boolean understood, List<JsonObject> actions, List<String> notes, String reason) {
        public Parse {
            actions = List.copyOf(actions);
            notes = List.copyOf(notes);
            Objects.requireNonNull(reason, "reason");
        }

        static Parse notUnderstood(String reason) {
            return new Parse(false, List.of(), List.of(), reason);
        }

        /** The actions as the JSON array {@link NexusActions#execute} takes. */
        public JsonArray toJsonArray() {
            JsonArray array = new JsonArray();
            for (JsonObject action : actions) {
                array.add(action.deepCopy());
            }
            return array;
        }
    }

    // ---- words ---------------------------------------------------------------------------------------------------

    /** What a known word or phrase means. */
    enum Kind {
        WIDGET, HUD_NOUN, WIDGET_NOUN, PRESET_NOUN, PRESET, POSITION,
        HIDE, SHOW, ON, OFF, ONLY, MOVE, ARRANGE,
        CHANNEL, VOLUME_NOUN, SUBTITLES, MUTE, UNMUTE, LOUDER, QUIETER,
        CROSSHAIR, SHAPE, COLOR, BIGGER, SMALLER, THICKER, THINNER, VANILLA, CUSTOM,
        SIZE_NOUN, THICKNESS_NOUN, GAP_NOUN, OPACITY_NOUN,
        CHAT, TEXT, NARROWER, WIDER,
        NUMBER, PERCENT
    }

    /** The clause domains. */
    private enum Domain { HUD, SOUND, CROSSHAIR, CHAT }

    /**
     * One read word or phrase.
     *
     * @param value  the id it names (widget type, setting id, anchor, shape, colour, preset) or the number
     * @param weak   true for German "an" / "ein" read as "on": also the English article and the German "a", so it is
     *               dropped when the clause says something else
     */
    record Term(Kind kind, String value, boolean weak) {
        Term(Kind kind, String value) {
            this(kind, value, false);
        }
    }

    private record Entry(String[] words, Term term) {
    }

    private static final Map<String, List<Entry>> BY_FIRST_WORD = new HashMap<>();
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Set<String> FILLERS = new HashSet<>();
    private static final Set<String> SEPARATORS = Set.of("and", "und", "then", "dann", ",", ";", "&", ".", "!", "?");
    private static final Set<String> WEAK_ON = Set.of("an", "ein");
    private static final Pattern TOKEN = Pattern.compile("\\d*\\.\\d+|\\d+|[a-z]+|[%,;&.!?]");
    private static final Pattern NUMBER = Pattern.compile("\\d*\\.\\d+|\\d+");

    static {
        filler("please", "pls", "plz", "kindly", "can", "could", "would", "will", "you", "u", "make", "set", "turn",
                "change", "switch", "put", "get", "let", "use", "the", "a", "my", "me", "i", "m", "s", "want",
                "wanna", "like", "need", "to", "now", "just", "it", "its", "this", "some", "bit", "little",
                "slightly", "much", "too", "so", "ok", "okay", "hey", "hi", "nexus", "vanta", "thanks", "thank", "thx",
                "for", "in", "into", "at", "onto", "of", "from", "with", "back", "again", "also", "all", "as", "well",
                "corner", "side", "edge", "level", "color", "colour", "colored", "coloured", "shape", "form", "style",
                "look",
                "bitte", "kannst", "koenntest", "du", "mach", "mache", "machen", "macht", "stell", "stelle", "stellen",
                "setz", "setze", "setzen", "schalte", "schalt", "schalten", "aendere", "aender", "aendern", "nutze",
                "benutze", "verwende", "die", "der", "das", "den", "dem", "des", "eine", "einen", "einem", "einer",
                "eines", "mein", "meine", "meinen", "meinem", "meiner", "meines", "mir", "mich", "ich", "will",
                "moechte", "moecht", "haette", "gern", "gerne", "jetzt", "nun", "mal", "doch", "es", "auf", "zu", "zum",
                "zur", "nach", "im", "ins", "danke", "hallo", "noch", "lass", "lasse", "lassen", "werden", "wird",
                "einfach", "etwas", "bisschen", "wenig", "ganz", "alle", "alles", "komplett", "wieder", "von", "vom",
                "fuer", "mit", "als", "ecke", "seite", "rand", "blende", "farbe", "farbig", "stil", "aussehen");

        // HUD widgets (HudWidgetType ids) and their names in both languages.
        widget(HudWidgetType.FPS, "fps", "frames", "framerate", "frame rate", "fps counter", "fps display",
                "fps anzeige", "fps zähler", "bildrate", "bilder pro sekunde", "frames per second");
        widget(HudWidgetType.PING, "ping", "latency", "latenz", "ping anzeige");
        widget(HudWidgetType.COORDINATES, "coordinates", "coordinate", "coords", "coord", "xyz", "position", "pos",
                "location", "koordinaten", "koordinate", "koords");
        widget(HudWidgetType.DIRECTION, "direction", "facing", "compass", "richtung", "himmelsrichtung",
                "blickrichtung", "kompass");
        widget(HudWidgetType.BIOME, "biome", "biom");
        widget(HudWidgetType.SERVER, "server", "server address", "server ip", "server name", "serveradresse");
        widget(HudWidgetType.CPS, "cps", "clicks", "clicks per second", "click counter", "klicks",
                "klicks pro sekunde");
        widget(HudWidgetType.CLOCK, "clock", "time", "uhr", "uhrzeit", "zeit", "system time");
        widget(HudWidgetType.ARMOR, "armor", "armour", "armor status", "rüstung");
        widget(HudWidgetType.ITEM_DURABILITY, "durability", "item durability", "tool durability", "haltbarkeit");
        widget(HudWidgetType.POTION_EFFECTS, "effects", "potion effects", "potions", "status effects", "effekte",
                "trankeffekte", "tränke", "statuseffekte");
        widget(HudWidgetType.KEYSTROKES, "keystrokes", "key strokes", "keys", "wasd", "tasten", "tastenanschläge");
        widget(HudWidgetType.MEMORY, "memory", "ram", "memory usage", "speicher", "arbeitsspeicher");
        widget(HudWidgetType.CPU, "cpu", "processor", "cpu usage", "prozessor");
        widget(HudWidgetType.ENTITY_COUNT, "entities", "entity", "entity count", "entity counter", "entitäten",
                "wesen");
        widget(HudWidgetType.MINECRAFT_VERSION, "version", "minecraft version", "game version", "mc version");
        widget(HudWidgetType.FRAMETIME_GRAPH, "frame time graph", "frametime graph", "frame graph", "frame time",
                "frametime", "frame time hud graph", "frametime hud graph");
        word(Kind.HUD_NOUN, "", "hud", "huds", "gui", "guis", "ui", "interface", "overlay", "screen", "bildschirm");
        word(Kind.WIDGET_NOUN, "", "widgets", "widget", "elements", "element", "elemente", "hud elements",
                "hud widgets", "hud elemente", "counter", "anzeige", "zähler");
        word(Kind.PRESET_NOUN, "", "preset", "vorlage");
        word(Kind.PRESET, NexusHudPreset.MINIMAL.id(), "minimal", "minimalistic", "minimalist", "clean", "minimale",
                "minimales", "minimalen");
        word(Kind.PRESET, NexusHudPreset.PVP.id(), "pvp");
        word(Kind.PRESET, NexusHudPreset.RECORDING.id(), "recording", "aufnahme");
        word(Kind.PRESET, NexusHudPreset.SURVIVAL.id(), "survival", "überleben");
        word(Kind.PRESET, NexusHudPreset.BUILDING.id(), "building", "bauen");
        word(Kind.PRESET, NexusHudPreset.FULL.id(), "full", "complete", "voll", "volle", "volles", "vollständig",
                "vollständiges");

        // Places on the screen (HudAnchor ids).
        word(Kind.POSITION, HudAnchor.TOP_LEFT.id(), "top left", "upper left", "left top", "topleft", "oben links",
                "links oben", "obere linke", "oberen linken");
        word(Kind.POSITION, HudAnchor.TOP_RIGHT.id(), "top right", "upper right", "right top", "topright",
                "oben rechts", "rechts oben", "obere rechte", "oberen rechten");
        word(Kind.POSITION, HudAnchor.BOTTOM_LEFT.id(), "bottom left", "lower left", "left bottom", "bottomleft",
                "unten links", "links unten", "untere linke", "unteren linken");
        word(Kind.POSITION, HudAnchor.BOTTOM_RIGHT.id(), "bottom right", "lower right", "right bottom",
                "bottomright", "unten rechts", "rechts unten", "untere rechte", "unteren rechten");
        word(Kind.POSITION, HudAnchor.TOP_CENTER.id(), "top", "top center", "top centre", "top middle", "middle top",
                "oben", "oben mitte", "oben mittig", "mitte oben", "obere", "oberen", "oberer");
        word(Kind.POSITION, HudAnchor.BOTTOM_CENTER.id(), "bottom", "bottom center", "bottom centre",
                "bottom middle", "middle bottom", "unten", "unten mitte", "unten mittig", "mitte unten", "untere",
                "unteren", "unterer");
        word(Kind.POSITION, HudAnchor.MIDDLE_LEFT.id(), "left", "middle left", "links", "links mitte", "linke",
                "linken");
        word(Kind.POSITION, HudAnchor.MIDDLE_RIGHT.id(), "right", "middle right", "rechts", "rechts mitte",
                "rechte", "rechten");
        word(Kind.POSITION, HudAnchor.CENTER.id(), "center", "centre", "middle", "screen center", "screen centre",
                "mitte", "mittig");

        // Verbs.
        word(Kind.HIDE, "", "hide", "remove", "delete", "get rid of", "take away", "entferne", "entfernen", "entfern",
                "lösche", "löschen", "lösch", "verstecke", "verstecken", "versteck", "ausblenden", "weg", "wegmachen");
        word(Kind.SHOW, "", "show", "add", "display", "unhide", "bring back", "zeige", "zeig", "zeigen", "anzeigen",
                "einblenden", "hinzufügen", "füge", "füg", "hinzu");
        word(Kind.ON, "", "on", "enable", "enabled", "activate", "einschalten", "anschalten", "aktivieren",
                "aktiviere", "anmachen");
        word(Kind.OFF, "", "off", "aus", "disable", "disabled", "deactivate", "ausschalten", "abschalten",
                "deaktivieren", "deaktiviere", "ausmachen", "stop", "stoppe", "stoppen", "stopp");
        word(Kind.ONLY, "", "only", "solely", "exclusively", "nur", "ausschließlich");
        word(Kind.MOVE, "", "move", "place", "drag", "relocate", "verschiebe", "verschieben", "verschieb", "schiebe",
                "schieb", "bewege", "bewegen", "beweg", "platziere", "platzieren");
        word(Kind.ARRANGE, "", "sort", "sorted", "sorting", "arrange", "rearrange", "tidy", "tidy up", "organize",
                "organise", "order", "reorder", "line up", "clean up", "align", "sortieren", "sortiere", "sortier",
                "ordnen", "ordne", "anordnen", "ordnung", "aufräumen", "räume", "räum");

        // Sound (setting ids of the Audio category).
        channel(VantaSettings.AUDIO_MASTER.id(), "master", "main", "overall", "sound", "sounds", "all sound",
                "all sounds", "audio", "game sound", "game audio", "ton", "töne", "geräusche", "gesamt", "haupt",
                "gesamtlautstärke", "hauptlautstärke", "spielsound");
        channel(VantaSettings.AUDIO_MUSIC.id(), "music", "musik", "soundtrack", "background music",
                "hintergrundmusik", "musiklautstärke");
        channel(VantaSettings.AUDIO_RECORDS.id(), "records", "record", "jukebox", "jukeboxes", "note blocks",
                "note block", "noteblocks", "discs", "music discs", "plattenspieler", "notenblöcke", "notenblock",
                "schallplatten");
        channel(VantaSettings.AUDIO_WEATHER.id(), "weather", "rain", "thunder", "wetter", "regen", "gewitter",
                "donner");
        channel(VantaSettings.AUDIO_BLOCKS.id(), "blocks", "block", "block sounds", "blöcke");
        channel(VantaSettings.AUDIO_HOSTILE.id(), "hostile", "hostile creatures", "hostile mobs", "monsters",
                "monster", "feindliche", "feindliche kreaturen", "feinde", "feindlich");
        channel(VantaSettings.AUDIO_NEUTRAL.id(), "neutral", "friendly", "friendly creatures", "neutral creatures",
                "animals", "freundliche", "freundliche kreaturen", "tiere");
        channel(VantaSettings.AUDIO_PLAYERS.id(), "players", "player", "spieler");
        channel(VantaSettings.AUDIO_AMBIENT.id(), "ambient", "ambience", "ambient sounds", "environment", "umgebung",
                "umgebungsgeräusche", "cave sounds", "höhlengeräusche");
        channel(VantaSettings.AUDIO_VOICE.id(), "voice", "speech", "stimme", "stimmen", "sprache");
        word(Kind.VOLUME_NOUN, "", "volume", "lautstärke", "loudness");
        word(Kind.SUBTITLES, "", "subtitles", "subtitle", "captions", "closed captions", "untertitel");
        word(Kind.MUTE, "", "mute", "muted", "silence", "silent", "stumm", "stummschalten", "lautlos");
        word(Kind.UNMUTE, "", "unmute", "unmuted");
        word(Kind.LOUDER, "", "louder", "turn up", "up", "lauter");
        word(Kind.QUIETER, "", "quieter", "softer", "turn down", "down", "leiser");

        // The crosshair.
        word(Kind.CROSSHAIR, "", "crosshair", "crosshairs", "cross hair", "cross hairs", "crossair", "crossover",
                "reticle", "fadenkreuz", "zielkreuz", "visier");
        word(Kind.SHAPE, CrosshairShape.CIRCLE.id(), "circle", "ring", "round", "circular", "kreis", "rund", "runde",
                "runder", "rundes", "runden", "kreisförmig");
        word(Kind.SHAPE, CrosshairShape.DOT.id(), "dot", "point", "punkt");
        word(Kind.SHAPE, CrosshairShape.CROSS.id(), "cross", "plus", "kreuz");
        word(Kind.SHAPE, CrosshairShape.SQUARE.id(), "square", "box", "quadrat", "viereck", "kasten", "eckig");
        word(Kind.SHAPE, CrosshairShape.CHEVRON.id(), "chevron", "arrow", "caret", "winkel", "pfeil");
        word(Kind.SHAPE, CrosshairShape.PLUS_DOT.id(), "plus dot", "cross dot", "cross with dot", "cross with a dot",
                "kreuz mit punkt");
        color("white", "white", "weiß");
        color("red", "red", "rot");
        color("green", "green", "grün");
        color("blue", "blue", "blau");
        color("yellow", "yellow", "gelb");
        color("cyan", "cyan", "turquoise", "aqua", "türkis");
        color("magenta", "magenta");
        color("black", "black", "schwarz");
        color("orange", "orange");
        color("purple", "purple", "violet", "lila", "violett");
        color("pink", "pink", "rosa");
        color("gray", "gray", "grey", "grau");
        word(Kind.BIGGER, "", "bigger", "larger", "big", "large", "greater", "größer", "größere", "größeren",
                "größeres", "größerer", "groß", "große", "großes", "großen", "vergrößern", "vergrößere", "vergrößer");
        word(Kind.SMALLER, "", "smaller", "small", "tiny", "shrink", "kleiner", "kleinere", "kleineren", "kleineres",
                "kleinerer", "klein", "kleine", "kleines", "kleinen", "verkleinern", "verkleinere", "verkleiner");
        word(Kind.THICKER, "", "thicker", "bolder", "dicker", "fetter");
        word(Kind.THINNER, "", "thinner", "slimmer", "dünner", "feiner");
        word(Kind.VANILLA, "", "vanilla", "default", "normal", "standard", "original", "minecraft", "minecrafts",
                "vanille", "normale", "normales", "normalen");
        word(Kind.CUSTOM, "", "custom");
        word(Kind.SIZE_NOUN, "", "size", "größe");
        word(Kind.THICKNESS_NOUN, "", "thickness", "dicke", "stärke");
        word(Kind.GAP_NOUN, "", "gap", "lücke", "abstand");
        word(Kind.OPACITY_NOUN, "", "opacity", "deckkraft", "alpha");

        // The chat.
        word(Kind.CHAT, "", "chat", "chats", "chatbox", "chat box", "chat window", "chatfenster", "chat fenster");
        word(Kind.TEXT, "", "text", "font", "schrift", "text size", "font size", "textgröße", "schriftgröße");
        word(Kind.NARROWER, "", "narrower", "schmaler", "schmäler", "schmal");
        word(Kind.WIDER, "", "wider", "broader", "breiter", "breit");

        // Numbers in words and percent.
        word(Kind.NUMBER, "0", "zero", "null");
        word(Kind.NUMBER, "50%", "half", "halb", "halbe", "hälfte");
        word(Kind.NUMBER, "100", "hundred", "hundert", "max", "maximum", "maximal");
        word(Kind.NUMBER, "0", "min", "minimum");
        word(Kind.PERCENT, "", "%", "percent", "per cent", "prozent", "pct");
        // German "an" (on) and "ein" (on, also "a"): read as on, dropped when the clause says something else.
        for (String weak : WEAK_ON) {
            add(new Term(Kind.ON, "", true), weak);
        }
    }

    private static void filler(String... words) {
        for (String w : words) {
            FILLERS.add(normalize(w));
        }
    }

    private static void widget(HudWidgetType type, String... phrases) {
        word(Kind.WIDGET, type.id(), phrases);
    }

    private static void channel(String settingId, String... phrases) {
        word(Kind.CHANNEL, settingId, phrases);
    }

    /** A colour with its English names and German stems (with the endings -e, -er, -es, -en, -em). */
    private static void color(String name, String... words) {
        List<String> all = new ArrayList<>();
        for (String w : words) {
            all.add(w);
            if (isGermanColor(w)) {
                for (String ending : List.of("e", "er", "es", "en", "em")) {
                    all.add(w + ending);
                }
            }
        }
        word(Kind.COLOR, name, all.toArray(String[]::new));
    }

    private static boolean isGermanColor(String w) {
        return List.of("weiß", "rot", "grün", "blau", "gelb", "türkis", "schwarz", "lila", "violett", "grau")
                .contains(w);
    }

    private static void word(Kind kind, String value, String... phrases) {
        add(new Term(kind, value), phrases);
    }

    private static void add(Term term, String... phrases) {
        for (String phrase : phrases) {
            List<String> words = tokens(normalize(phrase));
            Entry entry = new Entry(words.toArray(String[]::new), term);
            ENTRIES.add(entry);
            BY_FIRST_WORD.computeIfAbsent(words.get(0), k -> new ArrayList<>()).add(entry);
        }
    }

    // ---- live state ----------------------------------------------------------------------------------------------

    private final NexusActions actions;
    private final HudStore hud;

    public NexusCommands(NexusActions actions, HudStore hud) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.hud = Objects.requireNonNull(hud, "hud");
    }

    // ---- parsing -------------------------------------------------------------------------------------------------

    /** Internal: the text is not one of the instant commands. */
    private static final class NotUnderstood extends Exception {
        private static final long serialVersionUID = 1L;

        NotUnderstood(String reason) {
            super(reason, null, false, false);
        }
    }

    /**
     * Reads a text. Understood texts carry the actions in order (and notes for what needs no action, such as a widget
     * that is already hidden); everything else is {@code understood == false} with the reason, and nothing is changed.
     */
    public Parse parse(String text) {
        try {
            List<List<Term>> clauses = lex(text);
            if (clauses.isEmpty()) {
                throw new NotUnderstood("no command words");
            }
            Plan plan = new Plan();
            for (List<Term> clause : clauses) {
                plan.add(clause);
            }
            plan.finish();
            return build(plan.ops);
        } catch (NotUnderstood e) {
            return Parse.notUnderstood(e.getMessage());
        }
    }

    /** Lower case, umlauts spelled out, accents dropped, a decimal comma made a point, dashes and underscores spaces. */
    static String normalize(String text) {
        String t = text.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("ß", "ss");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        t = t.replaceAll("(\\d),(\\d)", "$1.$2");
        return t.replace('_', ' ').replace('-', ' ');
    }

    private static List<String> tokens(String normalized) {
        List<String> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(normalized);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** The clauses of a text as known terms; throws on the first word that is not known. */
    static List<List<Term>> lex(String text) throws NotUnderstood {
        List<String> words = tokens(normalize(text == null ? "" : text));
        if (words.size() > MAX_WORDS) {
            throw new NotUnderstood("too long for an instant command");
        }
        List<List<Term>> clauses = new ArrayList<>();
        List<Term> current = new ArrayList<>();
        int i = 0;
        while (i < words.size()) {
            String w = words.get(i);
            if (SEPARATORS.contains(w)) {
                if (!current.isEmpty()) {
                    clauses.add(current);
                }
                current = new ArrayList<>();
                i++;
                continue;
            }
            if (NUMBER.matcher(w).matches()) {
                current.add(new Term(Kind.NUMBER, w));
                i++;
                continue;
            }
            Match exact = longestMatch(words, i, false);
            if (exact != null) {
                current.add(exact.term());
                i += exact.length();
                continue;
            }
            if (FILLERS.contains(w)) {
                i++;
                continue;
            }
            Match fuzzy = longestMatch(words, i, true);
            if (fuzzy == null) {
                throw new NotUnderstood("unknown word \"" + w + "\"");
            }
            current.add(fuzzy.term());
            i += fuzzy.length();
        }
        if (!current.isEmpty()) {
            clauses.add(current);
        }
        return clauses;
    }

    private record Match(Term term, int length, int cost) {
    }

    /**
     * The longest phrase starting at {@code start}; with {@code fuzzy} each word may be a small typo. Among phrases of
     * the same length the closest wins; two different meanings equally close are not a match.
     */
    private static Match longestMatch(List<String> words, int start, boolean fuzzy) {
        Match best = null;
        boolean ambiguous = false;
        Iterable<Entry> candidates = fuzzy ? ENTRIES : BY_FIRST_WORD.getOrDefault(words.get(start), List.of());
        for (Entry entry : candidates) {
            int n = entry.words().length;
            if (start + n > words.size()) {
                continue;
            }
            int cost = 0;
            boolean ok = true;
            for (int k = 0; k < n && ok; k++) {
                String want = entry.words()[k];
                String got = words.get(start + k);
                if (want.equals(got)) {
                    continue;
                }
                int d = fuzzy ? typoDistance(want, got) : Integer.MAX_VALUE;
                if (d == Integer.MAX_VALUE) {
                    ok = false;
                } else {
                    cost += d;
                }
            }
            if (!ok) {
                continue;
            }
            if (best == null || n > best.length() || (n == best.length() && cost < best.cost())) {
                best = new Match(entry.term(), n, cost);
                ambiguous = false;
            } else if (n == best.length() && cost == best.cost() && !sameMeaning(entry.term(), best.term())) {
                ambiguous = true;
            }
        }
        return ambiguous ? null : best;
    }

    private static boolean sameMeaning(Term a, Term b) {
        return a.kind() == b.kind() && a.value().equals(b.value());
    }

    /**
     * The typo distance between a known word and a typed one, or {@link Integer#MAX_VALUE} when they are too far
     * apart: known words of five letters or more allow one edit (a letter wrong, missing, extra or two letters
     * swapped), of eight letters or more two; the typed word needs four letters and the same first letter.
     */
    static int typoDistance(String known, String typed) {
        if (known.length() < 5 || typed.length() < 4 || known.charAt(0) != typed.charAt(0)
                || NUMBER.matcher(typed).matches()) {
            return Integer.MAX_VALUE;
        }
        int allowed = known.length() >= 8 ? 2 : 1;
        if (Math.abs(known.length() - typed.length()) > allowed) {
            return Integer.MAX_VALUE;
        }
        int d = editDistance(known, typed);
        return d <= allowed ? d : Integer.MAX_VALUE;
    }

    /** Optimal string alignment distance (Levenshtein plus adjacent swaps). */
    static int editDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    // ---- clause meaning ------------------------------------------------------------------------------------------

    private static Domain nounDomain(Kind kind) {
        return switch (kind) {
            case WIDGET, HUD_NOUN, WIDGET_NOUN, PRESET_NOUN -> Domain.HUD;
            case CHANNEL, VOLUME_NOUN, SUBTITLES -> Domain.SOUND;
            case CROSSHAIR -> Domain.CROSSHAIR;
            case CHAT, TEXT -> Domain.CHAT;
            default -> null;
        };
    }

    private static Domain attributeDomain(Kind kind) {
        return switch (kind) {
            case ARRANGE, ONLY, MOVE, POSITION, PRESET -> Domain.HUD;
            case MUTE, UNMUTE, LOUDER, QUIETER -> Domain.SOUND;
            case SHAPE, COLOR, VANILLA, CUSTOM, THICKER, THINNER, THICKNESS_NOUN, GAP_NOUN -> Domain.CROSSHAIR;
            case NARROWER, WIDER -> Domain.CHAT;
            default -> null;
        };
    }

    /** The domain a clause speaks about and whether it names it ("crosshair") or only shows it ("red"). */
    private record Said(Domain domain, boolean named) {
    }

    /** The domain a clause speaks about, empty when it only has words every domain uses ("off", "bigger", "50"). */
    private static Optional<Said> domainOf(List<Term> terms) throws NotUnderstood {
        Set<Domain> nouns = new LinkedHashSet<>();
        Set<Domain> attributes = new LinkedHashSet<>();
        for (Term term : terms) {
            Domain noun = nounDomain(term.kind());
            if (noun != null) {
                nouns.add(noun);
            }
            Domain attribute = attributeDomain(term.kind());
            if (attribute != null) {
                attributes.add(attribute);
            }
        }
        if (nouns.size() > 1) {
            throw new NotUnderstood("one clause names " + nouns);
        }
        if (nouns.size() == 1) {
            Domain d = nouns.iterator().next();
            for (Domain a : attributes) {
                if (a != d) {
                    throw new NotUnderstood("one clause mixes " + d + " and " + a);
                }
            }
            return Optional.of(new Said(d, true));
        }
        if (attributes.size() > 1) {
            throw new NotUnderstood("one clause mixes " + attributes);
        }
        return attributes.isEmpty() ? Optional.empty() : Optional.of(new Said(attributes.iterator().next(), false));
    }

    /** Drops a weak "an"/"ein" when the clause says something else besides nouns. */
    private static List<Term> dropWeakOn(List<Term> terms) {
        boolean weak = false;
        boolean other = false;
        for (Term term : terms) {
            if (term.weak()) {
                weak = true;
            } else if (nounDomain(term.kind()) == null) {
                other = true;
            }
        }
        if (!weak || !other) {
            return terms;
        }
        List<Term> out = new ArrayList<>();
        for (Term term : terms) {
            if (!term.weak()) {
                out.add(term);
            }
        }
        return out;
    }

    /** A number with its percent sign, read as a fraction 0..1 (50, 50 %, 0.5 are 0.5). */
    private record Amount(double fraction) {
    }

    /**
     * Reads the number at {@code i} (and a following percent sign) as a fraction: a whole number 0 to 100 and any
     * number with a percent sign are percent, a decimal number up to 1 is the fraction itself.
     */
    private static Amount amount(List<Term> terms, int i) throws NotUnderstood {
        String text = terms.get(i).value();
        boolean percent = text.endsWith("%") || (i + 1 < terms.size() && terms.get(i + 1).kind() == Kind.PERCENT);
        double value = Double.parseDouble(text.endsWith("%") ? text.substring(0, text.length() - 1) : text);
        double fraction;
        if (percent || !text.contains(".")) {
            fraction = value / 100.0;
        } else if (value <= 1.0) {
            fraction = value;
        } else {
            fraction = value / 100.0;
        }
        if (fraction < 0.0 || fraction > 1.0) {
            throw new NotUnderstood("number out of range: " + text);
        }
        return new Amount(fraction);
    }

    /** Whole number at {@code i} (crosshair pixels). */
    private static int whole(List<Term> terms, int i) throws NotUnderstood {
        String text = terms.get(i).value();
        if (text.contains(".") || text.endsWith("%") || (i + 1 < terms.size()
                && terms.get(i + 1).kind() == Kind.PERCENT)) {
            throw new NotUnderstood("not a whole number: " + text);
        }
        return Integer.parseInt(text);
    }

    // ---- what a clause says ---------------------------------------------------------------------------------------

    /** HUD verbs. */
    private enum HudVerb { HIDE, SHOW, ONLY, MOVE, ARRANGE, PRESET, HUD_ON, HUD_OFF }

    /** One planned change. */
    private sealed interface Op permits HudOp, SoundOp, SubtitlesOp, CrosshairOp, ChatOp {
    }

    private static final class HudOp implements Op {
        final HudVerb verb;
        final List<HudWidgetType> widgets = new ArrayList<>();
        HudAnchor anchor;
        String preset;
        HudArranger.Target target = HudArranger.Target.AUTO;

        HudOp(HudVerb verb) {
            this.verb = verb;
        }

        HudOp copyFor(List<HudWidgetType> types) {
            HudOp op = new HudOp(verb);
            op.anchor = anchor;
            op.widgets.addAll(types);
            return op;
        }

        boolean takesWidgets() {
            return verb == HudVerb.HIDE || verb == HudVerb.SHOW || verb == HudVerb.ONLY || verb == HudVerb.MOVE;
        }
    }

    private static final class SoundOp implements Op {
        final List<String> channels = new ArrayList<>();
        /** A fraction (Double) or a signed step text ("+0.2"). */
        final Object value;

        SoundOp(Object value) {
            this.value = value;
        }
    }

    private record SubtitlesOp(boolean on) implements Op {
    }

    private static final class CrosshairOp implements Op {
        final Map<String, Object> fields = new LinkedHashMap<>();
    }

    private static final class ChatOp implements Op {
        /** Setting id to a step text or a fraction. */
        final Map<String, Object> changes = new LinkedHashMap<>();
    }

    /** What one clause says: a whole change, only its subjects, or only what to do with subjects said elsewhere. */
    private record Piece(Domain domain, Op op, List<String> subjects, boolean moveWithoutPlace) {
        boolean needsSubjects() {
            return op instanceof HudOp h ? h.takesWidgets() && h.widgets.isEmpty()
                    : op instanceof SoundOp s && s.channels.isEmpty();
        }
    }

    private static void put(Map<String, Object> map, String key, Object value) throws NotUnderstood {
        Object before = map.putIfAbsent(key, value);
        if (before != null && !before.equals(value)) {
            throw new NotUnderstood("contradiction for " + key);
        }
    }

    private Piece hudPiece(List<Term> terms) throws NotUnderstood {
        List<HudWidgetType> widgets = new ArrayList<>();
        boolean hudNoun = false;
        boolean widgetNoun = false;
        boolean presetNoun = false;
        String preset = null;
        HudAnchor position = null;
        int hide = 0;
        int show = 0;
        int only = 0;
        int move = 0;
        int arrange = 0;
        for (Term term : terms) {
            switch (term.kind()) {
                case WIDGET -> {
                    HudWidgetType type = HudWidgetType.fromId(term.value()).orElseThrow();
                    if (!widgets.contains(type)) {
                        widgets.add(type);
                    }
                }
                case HUD_NOUN -> hudNoun = true;
                case WIDGET_NOUN -> widgetNoun = true;
                case PRESET_NOUN -> presetNoun = true;
                case PRESET -> {
                    if (preset != null && !preset.equals(term.value())) {
                        throw new NotUnderstood("two presets");
                    }
                    preset = term.value();
                }
                case POSITION -> {
                    HudAnchor anchor = HudAnchor.fromId(term.value()).orElseThrow();
                    if (position != null && position != anchor) {
                        throw new NotUnderstood("two places");
                    }
                    position = anchor;
                }
                case HIDE, OFF -> hide++;
                case SHOW, ON -> show++;
                case ONLY -> only++;
                case MOVE -> move++;
                case ARRANGE -> arrange++;
                default -> throw new NotUnderstood("\"" + term.kind() + "\" does not fit a HUD command");
            }
        }
        if (arrange > 0) {
            if (!widgets.isEmpty() || preset != null || presetNoun || hide > 0 || only > 0 || move > 0) {
                throw new NotUnderstood("sort with something else");
            }
            HudOp op = new HudOp(HudVerb.ARRANGE);
            if (position != null) {
                Optional<HudArranger.Target> corner = HudArranger.Target.fromId(position.id())
                        .filter(t -> t != HudArranger.Target.AUTO);
                if (corner.isEmpty()) {
                    throw new NotUnderstood("sorting needs a corner, not " + position.id());
                }
                op.target = corner.get();
            }
            return complete(op);
        }
        if (preset != null) {
            if (!(hudNoun || widgetNoun || presetNoun) || !widgets.isEmpty() || position != null || hide > 0
                    || only > 0 || move > 0) {
                throw new NotUnderstood("a preset word without the HUD");
            }
            HudOp op = new HudOp(HudVerb.PRESET);
            op.preset = preset;
            return complete(op);
        }
        if (presetNoun) {
            throw new NotUnderstood("preset without a name");
        }
        if (hide > 0 && show > 0) {
            throw new NotUnderstood("show and hide at once");
        }
        if (only > 0) {
            if (hide > 0 || move > 0 || position != null || widgets.isEmpty()) {
                throw new NotUnderstood("only without widgets");
            }
            HudOp op = new HudOp(HudVerb.ONLY);
            op.widgets.addAll(widgets);
            return complete(op);
        }
        if (position != null) {
            if (hide > 0 || (widgets.isEmpty() && (hudNoun || widgetNoun))) {
                throw new NotUnderstood("a place for the whole HUD");
            }
            HudOp op = new HudOp(HudVerb.MOVE);
            op.anchor = position;
            op.widgets.addAll(widgets);
            return new Piece(Domain.HUD, op, List.of(), false);
        }
        if (move > 0) {
            if (widgets.isEmpty()) {
                throw new NotUnderstood("move without a widget");
            }
            return new Piece(Domain.HUD, null, ids(widgets), true);
        }
        if (hide > 0 || show > 0) {
            boolean visible = show > 0;
            if (widgets.isEmpty() && hudNoun && !widgetNoun) {
                return complete(new HudOp(visible ? HudVerb.HUD_ON : HudVerb.HUD_OFF));
            }
            if (widgets.isEmpty() && (hudNoun || widgetNoun)) {
                throw new NotUnderstood("which widgets?");
            }
            HudOp op = new HudOp(visible ? HudVerb.SHOW : HudVerb.HIDE);
            op.widgets.addAll(widgets);
            return new Piece(Domain.HUD, op, List.of(), false);
        }
        if (widgets.isEmpty()) {
            throw new NotUnderstood("the HUD without a command");
        }
        return new Piece(Domain.HUD, null, ids(widgets), false);
    }

    private static Piece complete(Op op) {
        return new Piece(domainFor(op), op, List.of(), false);
    }

    private static Domain domainFor(Op op) {
        return switch (op) {
            case HudOp h -> Domain.HUD;
            case SoundOp s -> Domain.SOUND;
            case SubtitlesOp s -> Domain.SOUND;
            case CrosshairOp c -> Domain.CROSSHAIR;
            case ChatOp c -> Domain.CHAT;
        };
    }

    private static List<String> ids(List<HudWidgetType> types) {
        List<String> out = new ArrayList<>();
        for (HudWidgetType type : types) {
            out.add(type.id());
        }
        return out;
    }

    private Piece soundPiece(List<Term> terms) throws NotUnderstood {
        List<String> channels = new ArrayList<>();
        boolean volumeNoun = false;
        boolean subtitles = false;
        boolean mute = false;
        int onish = 0;
        int offish = 0;
        Set<Object> values = new LinkedHashSet<>();
        for (int i = 0; i < terms.size(); i++) {
            Term term = terms.get(i);
            switch (term.kind()) {
                case CHANNEL -> {
                    if (!channels.contains(term.value())) {
                        channels.add(term.value());
                    }
                }
                case VOLUME_NOUN -> volumeNoun = true;
                case SUBTITLES -> subtitles = true;
                case MUTE -> {
                    mute = true;
                    offish++;
                    values.add(0.0);
                }
                case OFF -> {
                    offish++;
                    values.add(0.0);
                }
                case HIDE -> offish++;
                case UNMUTE -> {
                    mute = true;
                    onish++;
                    values.add(1.0);
                }
                case ON -> {
                    onish++;
                    values.add(1.0);
                }
                case SHOW -> onish++;
                case LOUDER -> values.add("+" + VOLUME_STEP);
                case QUIETER -> values.add("-" + VOLUME_STEP);
                case NUMBER -> values.add(amount(terms, i).fraction());
                case PERCENT -> {
                    if (i == 0 || terms.get(i - 1).kind() != Kind.NUMBER) {
                        throw new NotUnderstood("percent without a number");
                    }
                }
                default -> throw new NotUnderstood("\"" + term.kind() + "\" does not fit a sound command");
            }
        }
        if (subtitles) {
            if (!channels.isEmpty() || values.stream().anyMatch(v -> !(v instanceof Double d) || (d != 0.0 && d != 1.0))
                    || (onish > 0) == (offish > 0)) {
                throw new NotUnderstood("subtitles need on or off");
            }
            return complete(new SubtitlesOp(onish > 0));
        }
        boolean showOrHide = terms.stream().anyMatch(t -> t.kind() == Kind.SHOW || t.kind() == Kind.HIDE);
        if (showOrHide || values.size() > 1) {
            throw new NotUnderstood("two values for the sound");
        }
        if (channels.isEmpty() && (volumeNoun || mute)) {
            channels.add(VantaSettings.AUDIO_MASTER.id());
        }
        if (values.isEmpty()) {
            if (channels.isEmpty()) {
                throw new NotUnderstood("the volume without a value");
            }
            return new Piece(Domain.SOUND, null, channels, false);
        }
        SoundOp op = new SoundOp(values.iterator().next());
        op.channels.addAll(channels);
        return new Piece(Domain.SOUND, op, List.of(), false);
    }

    private Piece crosshairPiece(List<Term> terms) throws NotUnderstood {
        CrosshairOp op = new CrosshairOp();
        boolean sizeWord = false;
        for (int i = 0; i < terms.size(); i++) {
            Term term = terms.get(i);
            Kind next = i + 1 < terms.size() ? terms.get(i + 1).kind() : null;
            switch (term.kind()) {
                case CROSSHAIR -> {
                    // the noun
                }
                case SHAPE -> put(op.fields, "shape", term.value());
                case COLOR -> put(op.fields, "color", term.value());
                case BIGGER -> put(op.fields, "sizeStep", CROSSHAIR_SIZE_STEP);
                case SMALLER -> put(op.fields, "sizeStep", -CROSSHAIR_SIZE_STEP);
                case THICKER -> put(op.fields, "thicknessStep", CROSSHAIR_THICKNESS_STEP);
                case THINNER -> put(op.fields, "thicknessStep", -CROSSHAIR_THICKNESS_STEP);
                case VANILLA -> put(op.fields, "vanilla", true);
                case CUSTOM -> put(op.fields, "vanilla", false);
                case SIZE_NOUN, THICKNESS_NOUN, GAP_NOUN, OPACITY_NOUN -> {
                    if (next != Kind.NUMBER) {
                        if (term.kind() != Kind.SIZE_NOUN) {
                            throw new NotUnderstood(term.kind() + " without a number");
                        }
                        sizeWord = true;
                        continue;
                    }
                    i++;
                    switch (term.kind()) {
                        case SIZE_NOUN -> put(op.fields, "size", whole(terms, i));
                        case THICKNESS_NOUN -> put(op.fields, "thickness", whole(terms, i));
                        case GAP_NOUN -> put(op.fields, "gap", whole(terms, i));
                        default -> put(op.fields, "opacity", amount(terms, i).fraction());
                    }
                    if (i + 1 < terms.size() && terms.get(i + 1).kind() == Kind.PERCENT) {
                        if (term.kind() != Kind.OPACITY_NOUN) {
                            throw new NotUnderstood("percent for pixels");
                        }
                        i++;
                    }
                }
                default -> throw new NotUnderstood("\"" + term.kind() + "\" does not fit a crosshair command");
            }
        }
        if (sizeWord && !op.fields.containsKey("sizeStep")) {
            throw new NotUnderstood("size without bigger, smaller or a number");
        }
        if (op.fields.containsKey("size") && op.fields.containsKey("sizeStep")
                || op.fields.containsKey("thickness") && op.fields.containsKey("thicknessStep")) {
            throw new NotUnderstood("a size and a step");
        }
        if (Boolean.TRUE.equals(op.fields.get("vanilla")) && op.fields.size() > 1) {
            throw new NotUnderstood("Minecraft's crosshair with a VANTA style");
        }
        if (op.fields.isEmpty()) {
            throw new NotUnderstood("the crosshair without a change");
        }
        return complete(op);
    }

    private Piece chatPiece(List<Term> terms) throws NotUnderstood {
        ChatOp op = new ChatOp();
        boolean textOnly = terms.stream().anyMatch(t -> t.kind() == Kind.TEXT);
        String scale = VantaSettings.ACCESSIBILITY_CHAT_SCALE.id();
        String width = VantaSettings.ACCESSIBILITY_CHAT_WIDTH.id();
        for (int i = 0; i < terms.size(); i++) {
            Term term = terms.get(i);
            switch (term.kind()) {
                case CHAT, TEXT, SIZE_NOUN -> {
                    // nouns
                }
                case BIGGER, SMALLER -> {
                    String step = (term.kind() == Kind.BIGGER ? "+" : "-") + CHAT_STEP;
                    put(op.changes, scale, step);
                    if (!textOnly) {
                        put(op.changes, width, step);
                    }
                }
                case NARROWER -> put(op.changes, width, "-" + CHAT_STEP);
                case WIDER -> put(op.changes, width, "+" + CHAT_STEP);
                case OPACITY_NOUN -> {
                    if (i + 1 >= terms.size() || terms.get(i + 1).kind() != Kind.NUMBER) {
                        throw new NotUnderstood("chat opacity without a number");
                    }
                    i++;
                    put(op.changes, VantaSettings.ACCESSIBILITY_CHAT_OPACITY.id(), amount(terms, i).fraction());
                    if (i + 1 < terms.size() && terms.get(i + 1).kind() == Kind.PERCENT) {
                        i++;
                    }
                }
                default -> throw new NotUnderstood("\"" + term.kind() + "\" does not fit a chat command");
            }
        }
        if (op.changes.isEmpty()) {
            throw new NotUnderstood("the chat without a change");
        }
        return complete(op);
    }

    private Piece piece(List<Term> terms, Domain domain) throws NotUnderstood {
        return switch (domain) {
            case HUD -> hudPiece(terms);
            case SOUND -> soundPiece(terms);
            case CROSSHAIR -> crosshairPiece(terms);
            case CHAT -> chatPiece(terms);
        };
    }

    // ---- clauses together -----------------------------------------------------------------------------------------

    /**
     * Joins the clauses: subjects without a verb wait for the next clause's verb ("music and weather off"), a clause of
     * subjects only continues the clause before it ("remove the FPS and the ping"), words every domain uses continue
     * the crosshair or chat clause before them ("make the crosshair red and bigger"), and the crosshair and chat changes
     * of a text become one change each.
     */
    private final class Plan {
        final List<Op> ops = new ArrayList<>();
        Domain pendingDomain;
        final List<String> pending = new ArrayList<>();
        boolean pendingMove;
        Op previous;
        Domain previousDomain;
        CrosshairOp crosshair;
        ChatOp chat;

        void add(List<Term> raw) throws NotUnderstood {
            List<Term> terms = dropWeakOn(raw);
            Optional<Said> said = domainOf(terms);
            Domain domain;
            if (said.isPresent()) {
                domain = said.get().domain();
                // "red" or "narrower" alone could mean several things: the crosshair and the chat need their name,
                // unless the clause continues one about them ("make the crosshair red and a circle").
                boolean needsName = domain == Domain.CROSSHAIR || domain == Domain.CHAT;
                if (needsName && !said.get().named() && previousDomain != domain) {
                    throw new NotUnderstood("a " + domain + " word without the " + domain);
                }
            } else if (!pending.isEmpty()) {
                domain = pendingDomain;
            } else if (previousDomain == Domain.CROSSHAIR || previousDomain == Domain.CHAT) {
                domain = previousDomain;
            } else {
                throw new NotUnderstood("a clause without a subject");
            }
            Piece piece = piece(terms, domain);
            if (piece.op() == null) {
                subjectsOnly(piece);
            } else if (piece.needsSubjects()) {
                if (pending.isEmpty() || pendingDomain != domain) {
                    throw new NotUnderstood("a command without a subject");
                }
                checkPendingMove(piece.op());
                emit(withSubjects(piece.op(), takePending()));
            } else {
                if (!pending.isEmpty()) {
                    if (pendingDomain != domain || !takesSubjects(piece.op())) {
                        throw new NotUnderstood("subjects without a command");
                    }
                    checkPendingMove(piece.op());
                    emit(withSubjects(piece.op(), prepend(takePending(), piece.op())));
                } else {
                    emit(piece.op());
                }
            }
        }

        private void subjectsOnly(Piece piece) throws NotUnderstood {
            boolean continues = pending.isEmpty() && previousDomain == piece.domain() && previous != null
                    && takesSubjects(previous) && !piece.moveWithoutPlace();
            if (continues) {
                if (previous instanceof HudOp h && h.verb == HudVerb.ONLY) {
                    for (String id : piece.subjects()) {
                        HudWidgetType type = HudWidgetType.fromId(id).orElseThrow();
                        if (!h.widgets.contains(type)) {
                            h.widgets.add(type);
                        }
                    }
                    return;
                }
                emit(withSubjects(previous, piece.subjects()));
                return;
            }
            if (!pending.isEmpty() && pendingDomain != piece.domain()) {
                throw new NotUnderstood("subjects of two kinds without a command");
            }
            pendingDomain = piece.domain();
            pending.addAll(piece.subjects());
            pendingMove |= piece.moveWithoutPlace();
            previous = null;
            previousDomain = piece.domain();
        }

        private void checkPendingMove(Op op) throws NotUnderstood {
            if (pendingMove && !(op instanceof HudOp h && h.verb == HudVerb.MOVE)) {
                throw new NotUnderstood("move without a place");
            }
        }

        private List<String> takePending() {
            List<String> out = new ArrayList<>(pending);
            pending.clear();
            pendingMove = false;
            pendingDomain = null;
            return out;
        }

        private List<String> prepend(List<String> first, Op op) {
            List<String> out = new ArrayList<>(first);
            if (op instanceof HudOp h) {
                for (String id : ids(h.widgets)) {
                    if (!out.contains(id)) {
                        out.add(id);
                    }
                }
            } else if (op instanceof SoundOp s) {
                for (String id : s.channels) {
                    if (!out.contains(id)) {
                        out.add(id);
                    }
                }
            }
            return out;
        }

        private boolean takesSubjects(Op op) {
            return op instanceof HudOp h ? h.takesWidgets() : op instanceof SoundOp;
        }

        private Op withSubjects(Op op, List<String> subjects) {
            if (op instanceof HudOp h) {
                List<HudWidgetType> types = new ArrayList<>();
                for (String id : subjects) {
                    types.add(HudWidgetType.fromId(id).orElseThrow());
                }
                return h.copyFor(types);
            }
            SoundOp s = (SoundOp) op;
            SoundOp copy = new SoundOp(s.value);
            copy.channels.addAll(subjects);
            return copy;
        }

        private void emit(Op op) throws NotUnderstood {
            if (op instanceof CrosshairOp c) {
                if (crosshair == null) {
                    crosshair = c;
                    ops.add(c);
                } else {
                    for (Map.Entry<String, Object> e : c.fields.entrySet()) {
                        put(crosshair.fields, e.getKey(), e.getValue());
                    }
                    if (Boolean.TRUE.equals(crosshair.fields.get("vanilla")) && crosshair.fields.size() > 1) {
                        throw new NotUnderstood("Minecraft's crosshair with a VANTA style");
                    }
                }
            } else if (op instanceof ChatOp c) {
                if (chat == null) {
                    chat = c;
                    ops.add(c);
                } else {
                    for (Map.Entry<String, Object> e : c.changes.entrySet()) {
                        put(chat.changes, e.getKey(), e.getValue());
                    }
                }
            } else {
                ops.add(op);
            }
            previous = op;
            previousDomain = domainFor(op);
        }

        void finish() throws NotUnderstood {
            if (!pending.isEmpty()) {
                throw new NotUnderstood(pendingMove ? "move without a place" : "subjects without a command");
            }
        }
    }

    // ---- actions ---------------------------------------------------------------------------------------------------

    private Parse build(List<Op> ops) {
        List<JsonObject> out = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        HudLayout layout = hud.layout();
        for (Op op : ops) {
            switch (op) {
                case HudOp h -> hudActions(h, layout, out, notes);
                case SoundOp s -> {
                    for (String channel : s.channels) {
                        out.add(settingSet(channel, s.value));
                    }
                }
                case SubtitlesOp s -> out.add(settingSet(VantaSettings.AUDIO_SUBTITLES.id(), s.on()));
                case CrosshairOp c -> out.add(crosshairAction(c));
                case ChatOp c -> {
                    for (Map.Entry<String, Object> e : c.changes.entrySet()) {
                        out.add(settingSet(e.getKey(), e.getValue()));
                    }
                }
            }
        }
        return new Parse(true, out, notes, "");
    }

    private void hudActions(HudOp op, HudLayout layout, List<JsonObject> out, List<String> notes) {
        switch (op.verb) {
            case ARRANGE -> {
                JsonObject a = action("hud.arrange");
                if (op.target != HudArranger.Target.AUTO) {
                    a.addProperty("corner", op.target.id());
                }
                out.add(a);
            }
            case PRESET -> {
                JsonObject a = action("hud.preset");
                a.addProperty("preset", op.preset);
                out.add(a);
            }
            case HUD_ON, HUD_OFF -> out.add(settingSet(VantaSettings.HUD_ENABLED.id(), op.verb == HudVerb.HUD_ON));
            case ONLY -> {
                JsonArray elements = new JsonArray();
                for (HudWidgetType type : op.widgets) {
                    if (actions.isOffered(type)) {
                        elements.add(type.id());
                    } else {
                        notes.add(Lang.tr("vanta.nexus.commands.not_offered", Lang.tr(type.langKey())));
                    }
                }
                if (!elements.isEmpty()) {
                    JsonObject a = action("hud.only");
                    a.add("elements", elements);
                    out.add(a);
                }
            }
            case HIDE -> {
                for (HudWidgetType type : op.widgets) {
                    boolean any = false;
                    for (HudWidgetState w : layout.byType(type)) {
                        if (w.enabled()) {
                            out.add(hudSet(w.id(), false));
                            any = true;
                        }
                    }
                    if (!any) {
                        notes.add(Lang.tr("vanta.nexus.commands.already_hidden", Lang.tr(type.langKey())));
                    }
                }
            }
            case SHOW -> {
                for (HudWidgetType type : op.widgets) {
                    List<HudWidgetState> ofType = layout.byType(type);
                    if (!actions.isOffered(type)) {
                        notes.add(Lang.tr("vanta.nexus.commands.not_offered", Lang.tr(type.langKey())));
                    } else if (ofType.stream().anyMatch(HudWidgetState::enabled)) {
                        notes.add(Lang.tr("vanta.nexus.commands.already_shown", Lang.tr(type.langKey())));
                    } else {
                        out.add(hudSet(ofType.isEmpty() ? type.id() : ofType.get(0).id(), true));
                    }
                }
            }
            case MOVE -> {
                for (HudWidgetType type : op.widgets) {
                    if (!actions.isOffered(type)) {
                        notes.add(Lang.tr("vanta.nexus.commands.not_offered", Lang.tr(type.langKey())));
                        continue;
                    }
                    List<HudWidgetState> ofType = layout.byType(type);
                    Optional<HudWidgetState> target = ofType.stream().filter(HudWidgetState::enabled).findFirst()
                            .or(() -> ofType.stream().findFirst());
                    JsonObject a = action("hud.set");
                    a.addProperty("element", target.map(HudWidgetState::id).orElse(type.id()));
                    if (target.isEmpty() || !target.get().enabled()) {
                        a.addProperty("visible", true);
                    }
                    a.addProperty("anchor", op.anchor.id());
                    out.add(a);
                }
            }
        }
    }

    private JsonObject crosshairAction(CrosshairOp op) {
        CrosshairStyle now = actions.crosshair().map(CrosshairStore::style).orElse(CrosshairStyle.DEFAULT);
        JsonObject a = action("crosshair.set");
        for (Map.Entry<String, Object> e : op.fields.entrySet()) {
            Object v = e.getValue();
            switch (e.getKey()) {
                case "sizeStep" -> a.addProperty("size", clamp(now.size() + (Integer) v, 1, CrosshairStyle.MAX_SIZE));
                case "thicknessStep" -> a.addProperty("thickness", clamp(now.thickness() + (Integer) v, 1,
                        CrosshairStyle.MAX_THICKNESS));
                default -> {
                    if (v instanceof Boolean b) {
                        a.addProperty(e.getKey(), b);
                    } else if (v instanceof Number n) {
                        a.addProperty(e.getKey(), n);
                    } else {
                        a.addProperty(e.getKey(), String.valueOf(v));
                    }
                }
            }
        }
        return a;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static JsonObject action(String type) {
        JsonObject a = new JsonObject();
        a.addProperty("type", type);
        return a;
    }

    private static JsonObject hudSet(String element, boolean visible) {
        JsonObject a = action("hud.set");
        a.addProperty("element", element);
        a.addProperty("visible", visible);
        return a;
    }

    private static JsonObject settingSet(String id, Object value) {
        JsonObject a = action("setting.set");
        a.addProperty("id", id);
        if (value instanceof Boolean b) {
            a.addProperty("value", b);
        } else if (value instanceof Double d) {
            a.addProperty("value", Math.round(d * 10_000) / 10_000.0);
        } else {
            a.addProperty("value", String.valueOf(value));
        }
        return a;
    }

    // ---- the answer ------------------------------------------------------------------------------------------------

    /** Short labels of the settings the commands change, for the answer. */
    private static final Map<String, String> SETTING_LABELS;

    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(VantaSettings.AUDIO_MASTER.id(), "vanta.nexus.commands.label.audio_master");
        labels.put(VantaSettings.AUDIO_MUSIC.id(), "vanta.nexus.commands.label.audio_music");
        labels.put(VantaSettings.AUDIO_RECORDS.id(), "vanta.nexus.commands.label.audio_records");
        labels.put(VantaSettings.AUDIO_WEATHER.id(), "vanta.nexus.commands.label.audio_weather");
        labels.put(VantaSettings.AUDIO_BLOCKS.id(), "vanta.nexus.commands.label.audio_blocks");
        labels.put(VantaSettings.AUDIO_HOSTILE.id(), "vanta.nexus.commands.label.audio_hostile");
        labels.put(VantaSettings.AUDIO_NEUTRAL.id(), "vanta.nexus.commands.label.audio_neutral");
        labels.put(VantaSettings.AUDIO_PLAYERS.id(), "vanta.nexus.commands.label.audio_players");
        labels.put(VantaSettings.AUDIO_AMBIENT.id(), "vanta.nexus.commands.label.audio_ambient");
        labels.put(VantaSettings.AUDIO_VOICE.id(), "vanta.nexus.commands.label.audio_voice");
        labels.put(VantaSettings.AUDIO_SUBTITLES.id(), "vanta.nexus.commands.label.audio_subtitles");
        labels.put(VantaSettings.ACCESSIBILITY_CHAT_SCALE.id(), "vanta.nexus.commands.label.chat_scale");
        labels.put(VantaSettings.ACCESSIBILITY_CHAT_WIDTH.id(), "vanta.nexus.commands.label.chat_width");
        labels.put(VantaSettings.ACCESSIBILITY_CHAT_OPACITY.id(), "vanta.nexus.commands.label.chat_opacity");
        labels.put(VantaSettings.HUD_ENABLED.id(), "vanta.nexus.commands.label.hud_enabled");
        SETTING_LABELS = Collections.unmodifiableMap(labels);
    }

    /** Lang keys of {@link #SETTING_LABELS} (tests check they exist). */
    static List<String> labelKeys() {
        return List.copyOf(SETTING_LABELS.values());
    }

    private static final Pattern PERCENT_DETAIL = Pattern.compile("^\\S+ \\((\\d+) %(?:, (.+))?\\)$");
    private static final Pattern ANCHOR_DETAIL = Pattern.compile("anchor=([a-z_]+)");

    /**
     * The answer to an applied command, built from what really happened: "Done: music volume 0 %." with every applied
     * change, then the notes (a widget that was already hidden) and what was refused.
     */
    public String reply(NexusActions.Outcome outcome, List<String> notes) {
        List<String> done = new ArrayList<>();
        for (AppliedAction applied : outcome.applied()) {
            done.add(describe(applied));
        }
        List<String> parts = new ArrayList<>();
        if (!done.isEmpty()) {
            parts.add(Lang.tr("vanta.nexus.commands.done", String.join("; ", done)));
        }
        parts.addAll(notes);
        if (!outcome.rejected().isEmpty()) {
            List<String> refused = new ArrayList<>();
            for (RejectedAction rejected : outcome.rejected()) {
                refused.add(rejected.summary());
            }
            parts.add(Lang.tr("vanta.nexus.commands.not_applied", String.join("; ", refused)));
        }
        if (parts.isEmpty()) {
            parts.add(Lang.tr("vanta.nexus.commands.nothing"));
        }
        return String.join(" ", parts);
    }

    /** One applied change in a few words. */
    String describe(AppliedAction a) {
        return switch (a.type()) {
            case "setting.set" -> describeSetting(a);
            case "crosshair.set" -> Lang.tr("vanta.nexus.commands.crosshair", a.detail());
            case "hud.set" -> describeHudSet(a);
            case "hud.only" -> {
                List<String> names = new ArrayList<>();
                for (String id : a.target().split(",\\s*")) {
                    names.add(widgetNameOf(id));
                }
                yield Lang.tr("vanta.nexus.commands.hud_only", joinNames(names));
            }
            case "hud.arrange" -> Lang.tr("vanta.nexus.commands.hud_arrange", a.detail());
            case "hud.preset" -> Lang.tr("vanta.nexus.commands.hud_preset", a.detail());
            case "hud.resolve" -> Lang.tr("vanta.nexus.commands.hud_resolve");
            default -> a.summary();
        };
    }

    private String describeSetting(AppliedAction a) {
        String key = SETTING_LABELS.get(a.target());
        String label = key == null ? a.target() : Lang.tr(key);
        String detail = a.detail();
        if (detail.equals("true") || detail.equals("false")) {
            return Lang.tr(detail.equals("true") ? "vanta.nexus.commands.on" : "vanta.nexus.commands.off", label);
        }
        Matcher m = PERCENT_DETAIL.matcher(detail);
        if (m.matches()) {
            String value = m.group(1) + " %";
            return m.group(2) == null ? label + " " + value : label + " " + value + " (" + m.group(2) + ")";
        }
        return label + " " + detail;
    }

    private String describeHudSet(AppliedAction a) {
        String name = widgetNameOf(a.target());
        String detail = a.detail();
        if (detail.contains("visible=false")) {
            return Lang.tr("vanta.nexus.commands.hidden", name);
        }
        Matcher m = ANCHOR_DETAIL.matcher(detail);
        if (m.find()) {
            String place = HudAnchor.fromId(m.group(1)).map(anchor -> Lang.tr(anchor.langKey()).toLowerCase(Locale.ROOT))
                    .orElse(m.group(1));
            return Lang.tr("vanta.nexus.commands.moved", name, place);
        }
        if (detail.contains("visible=true") || detail.contains("added")) {
            return Lang.tr("vanta.nexus.commands.shown", name);
        }
        return Lang.tr("vanta.nexus.commands.changed", name, detail);
    }

    /** "a", "a and b", "a, b and c". */
    private static String joinNames(List<String> names) {
        if (names.size() <= 1) {
            return String.join("", names);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " " + Lang.tr("vanta.nexus.commands.and") + " "
                + names.get(names.size() - 1);
    }

    private String widgetNameOf(String id) {
        return hud.layout().find(id).map(w -> widgetName(w.type()))
                .orElseGet(() -> HudWidgetType.fromId(id).map(NexusCommands::widgetName).orElse(id));
    }

    /** A widget's name for a sentence: "FPS", "coordinates", "Minecraft version". */
    static String widgetName(HudWidgetType type) {
        String name = Lang.tr(type.langKey());
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isLowerCase(name.charAt(1))
                && !name.startsWith("Minecraft")) {
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }
}
