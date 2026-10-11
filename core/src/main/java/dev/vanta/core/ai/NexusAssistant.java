package dev.vanta.core.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.config.CoreLog;
import dev.vanta.core.crosshair.CrosshairColors;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.CrosshairStyle;
import dev.vanta.core.hud.HudLayout;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudStore;
import dev.vanta.core.hud.HudWidgetState;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.perf.PerformanceCenter;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.profiles.ProfileManager;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingKind;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Vanta Nexus assistant: builds the system prompt from the live registries, keeps the last
 * {@value #CONTEXT_TURNS} turns as context, sends the question through the {@link ChatBackend}, validates and applies
 * the returned actions through {@link NexusActions}, records an undo snapshot and the transcript, and returns a
 * {@link NexusReply}. Render-thread only; the backend runs the HTTP call elsewhere and calls back on the main thread.
 * <p>
 * From client 1.6.0 on every question first goes through {@link NexusCommands}: an everyday command it understands
 * ("music off", "make the crosshair a circle", "sort the HUD") is applied at once through the same actions, receipts,
 * transcript and Undo as a model turn, without starting or asking the Local AI; only the rest goes to the model.
 */
public final class NexusAssistant {
    /** Turns (player + assistant) sent as context. */
    public static final int CONTEXT_TURNS = 8;
    /** Longest question accepted. */
    public static final int MAX_QUESTION = 2000;
    /**
     * Rule in the system prompt: the assistant never caps the frame rate on its own. A frame-rate limit or VSync is
     * the player's explicit choice; asked for "more FPS" it removes the cap instead.
     */
    public static final String FRAME_RATE_RULE = "Never turn VSync on or set a frame-rate limit (video.vsync, "
            + "video.framerateLimit, performance.fpsLimitPreset) unless the player asks for exactly that. For more FPS "
            + "use performance.fpsLimitPreset unlimited, which turns VSync off.";
    /**
     * Rule in the system prompt (from client 1.6.0 on): what the 0..1 numbers mean, the relative steps, and how "chat
     * smaller" maps to the vanilla chat size settings. A step ({@code "-0.2"}) does not depend on the current value,
     * so a small model gets "smaller" right the second time too. {@link NexusActions} also reads "50 %" and numbers
     * above 1 as percent and never sets the chat text size below {@value NexusActions#MIN_CHAT_SCALE}.
     */
    public static final String FRACTION_RULE = "Numbers 0..1 (volumes audio.*, opacities, chat size) are fractions: "
            + "0 = off, 0.5 = 50 %, 1 = 100 %. A text value starting with + or - (\"+0.2\", \"-0.2\") changes the "
            + "current number by that much. Chat size is accessibility.chatScale (text) and accessibility.chatWidth: "
            + "smaller = \"-0.2\" on both, bigger = \"+0.2\".";
    /**
     * Few-shot examples at the end of the system prompt (from client 1.6.0 on): the requests players make most, each
     * with the actions it maps to. Only ids that always exist are used.
     */
    public static final String EXAMPLES = """
            Examples (player: actions):
            music off: [{"type":"setting.set","id":"audio.music","value":0}]
            master volume 50 %: [{"type":"setting.set","id":"audio.master","value":0.5}]
            make the crosshair a circle: [{"type":"crosshair.set","shape":"circle"}]
            red dot crosshair: [{"type":"crosshair.set","shape":"dot","color":"red"}]
            make the chat smaller: [{"type":"setting.set","id":"accessibility.chatScale","value":"-0.2"},\
            {"type":"setting.set","id":"accessibility.chatWidth","value":"-0.2"}]
            sort the HUD: [{"type":"hud.arrange"}]
            delete the coordinates: [{"type":"hud.set","element":"coordinates","visible":false}]
            only show FPS and coordinates: [{"type":"hud.only","elements":["fps","coordinates"]}]
            """;
    /** Context size of the Local AI model (the manifest's {@code contextSize}, {@code -c}). */
    public static final int CONTEXT_TOKENS = 4096;
    /**
     * Budget of the system prompt in {@link #estimateTokens} tokens: the context minus the
     * {@value LocalAiClient#MAX_TOKENS}-token answer and room for the {@value #CONTEXT_TURNS} turns of conversation.
     */
    public static final int PROMPT_TOKEN_BUDGET = 2600;

    private final NexusActions actions;
    private final NexusUndo undo;
    private final NexusTranscript transcript;
    private final ChatBackend backend;
    private final HudStore hud;
    private final ProfileManager profiles;
    private final PerformanceCenter performance;
    private final Clock clock;
    private final NexusCommands commands;
    private boolean busy;

    public NexusAssistant(NexusActions actions, NexusUndo undo, NexusTranscript transcript, ChatBackend backend,
                          HudStore hud, ProfileManager profiles, PerformanceCenter performance, Clock clock) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.undo = Objects.requireNonNull(undo, "undo");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.hud = Objects.requireNonNull(hud, "hud");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.performance = Objects.requireNonNull(performance, "performance");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.commands = new NexusCommands(actions, hud);
    }

    public NexusActions actions() {
        return actions;
    }

    public NexusUndo undo() {
        return undo;
    }

    public NexusTranscript transcript() {
        return transcript;
    }

    /** The instant commands tried before the model (from client 1.6.0 on). */
    public NexusCommands commands() {
        return commands;
    }

    /** True while a question is in flight. */
    public boolean isBusy() {
        return busy;
    }

    // ---- prompt and schema ---------------------------------------------------------------------------------------

    /** The reply schema for llama-server (live ids). */
    public JsonObject schema() {
        return actions.schema();
    }

    /** The system prompt built from the live registries. Plain English, compact, no markdown. */
    public String systemPrompt() {
        StringBuilder p = new StringBuilder(4096);
        p.append("You are Vanta Nexus, the local assistant inside the VANTA Minecraft client. You help the player ")
                .append("with their own client only: HUD layout, crosshair, settings (sound and chat size too), ")
                .append("profiles, performance presets, waypoints and Vanta Lab features. You never act in the game ")
                .append("world, never touch other players, keybinds, the network or files.\n");
        p.append("Reply with one JSON object only: {\"message\": string, \"actions\": array}. \"message\" is one or two ")
                .append("plain English sentences for the player, no markdown. \"actions\" lists the changes to make now, ")
                .append("in order. Return an empty array when nothing should change or when you need to ask a question. ")
                .append("Use only the action types and the ids, names and values listed below; never invent names. If a ")
                .append("request is outside these actions, say so briefly.\n\n");
        p.append("Action types:\n");
        p.append("- hud.set {element, visible?, anchor?, x?, y?, scale?, opacity?}: show or hide a widget (visible ")
                .append("false removes it from the screen), anchor it ")
                .append("(top_left, top_center, top_right, middle_left, center, middle_right, bottom_left, bottom_center, ")
                .append("bottom_right), place it at x,y as fractions of the screen (0 = left/top, 1 = right/bottom), ")
                .append("scale 0.5..2, opacity 0.1..1. A widget type that is not on the HUD yet is added.\n");
        p.append("- hud.only {elements: [ids]}: show exactly these widgets and hide every other one (the crosshair stays).\n");
        p.append("- hud.arrange {corner?}: sort, arrange, tidy, organize, order or line up the HUD ")
                .append("(also \"sortieren\", \"ordnen\"): stacks every visible widget without overlap in its ")
                .append("nearest corner (corner auto) or all in top_left, top_right, bottom_left or bottom_right. ")
                .append("Never sort with hud.set.\n");
        if (actions.crosshair().isPresent()) {
            p.append("- crosshair.set {preset?, shape?, size?, thickness?, gap?, color?, outline?, opacity?, ")
                    .append("vanilla?}: preset ").append(String.join(", ", CrosshairPresets.IDS)).append("; shape ")
                    .append(String.join(", ", NexusActions.crosshairShapeIds()))
                    .append("; size 1..").append(CrosshairStyle.MAX_SIZE).append(", thickness 1..")
                    .append(CrosshairStyle.MAX_THICKNESS).append(", gap 0..").append(CrosshairStyle.MAX_GAP)
                    .append(", opacity ").append(NexusActions.format(NexusActions.MIN_CROSSHAIR_OPACITY))
                    .append("..1; color #RRGGBB or ")
                    .append(String.join(", ", CrosshairColors.names()))
                    .append("; vanilla true = Minecraft's own crosshair.\n");
        }
        p.append("- hud.layout.save {name} and hud.layout.load {name}: save the current layout under a name, or load a ")
                .append("saved layout.\n");
        p.append("- hud.preset {preset}: one of ").append(String.join(", ", actions.hudPresetIds())).append(".\n");
        p.append("- profile.switch {name} and profile.create {name, fromCurrent: true}: switch to a profile, or save ")
                .append("the current setup as a new profile.\n");
        p.append("- perf.preset {preset}: one of ").append(String.join(", ", actions.perfPresetIds()))
                .append(". perf.smartBoost {run: true}: measure this PC and pick a preset automatically.\n");
        p.append("- setting.set {id, value}: change one of the settings listed below.\n");
        p.append("  ").append(FRACTION_RULE).append('\n');
        p.append("  ").append(FRAME_RATE_RULE).append('\n');
        p.append("- waypoint.add {name, x, y, z, category?}, waypoint.remove {name}, waypoint.toggle {name, enabled}.\n");
        p.append("- lab.set {feature, enabled}: switch a Vanta Lab feature.\n\n");

        p.append("HUD widgets now (id: type, visible, anchor, scale, opacity):\n");
        HudLayout layout = hud.layout();
        for (HudWidgetState w : layout.widgets()) {
            p.append(w.id()).append(": ").append(w.type().id()).append(", ")
                    .append(w.enabled() ? "visible" : "hidden").append(", ").append(w.anchor().id()).append(", ")
                    .append(NexusActions.format(w.scale())).append(", ").append(NexusActions.format(w.opacity()))
                    .append('\n');
        }
        List<String> addable = actions.addableWidgetTypes();
        if (!addable.isEmpty()) {
            p.append("Widget types not on the HUD (use as element to add them): ").append(String.join(", ", addable))
                    .append('\n');
        }
        actions.crosshairSummary().ifPresent(summary -> p.append("Crosshair now: ").append(summary).append('\n'));
        p.append("Saved HUD layouts: ");
        List<String> layouts = new ArrayList<>();
        for (HudPreset preset : hud.allPresets()) {
            layouts.add(Lang.tr(preset.langKey()));
        }
        p.append(layouts.isEmpty() ? "none" : String.join(", ", layouts)).append("\n\n");

        // On/off and 0..1 settings are listed on one line each (id=current) to keep the prompt within the context.
        p.append("Settings you may change (id: allowed values, current):\n");
        List<String> toggles = new ArrayList<>();
        List<String> fractions = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (Setting<?> setting : actions.allowedSettings()) {
            String current = actions.currentValue(setting);
            if (setting.kind() == SettingKind.BOOL) {
                toggles.add(setting.id() + "=" + current);
            } else if (NexusActions.isFraction(setting)) {
                fractions.add(setting.id() + "=" + current);
            } else {
                others.add(setting.id() + ": " + NexusActions.allowedValues(setting) + ", current " + current);
            }
        }
        if (!toggles.isEmpty()) {
            p.append("On/off, true or false (id=current): ").append(String.join(", ", toggles)).append('\n');
        }
        if (!fractions.isEmpty()) {
            p.append("Fractions, number 0..1 (id=current): ").append(String.join(", ", fractions)).append('\n');
        }
        for (String line : others) {
            p.append(line).append('\n');
        }
        p.append('\n');

        p.append("Profiles: ");
        List<String> names = new ArrayList<>();
        Optional<String> activeId = profiles.activeId();
        for (Profile profile : profiles.list()) {
            names.add(profile.name() + (activeId.isPresent() && activeId.get().equals(profile.id()) ? " (active)" : ""));
        }
        p.append(names.isEmpty() ? "none" : String.join(", ", names)).append('\n');
        p.append("Performance presets: ").append(String.join(", ", actions.perfPresetIds()));
        Optional<PerformancePreset> active = performance.snapshot().activePreset();
        active.ifPresent(preset -> p.append(" (active: ").append(preset.id()).append(')'));
        p.append('\n');
        List<String> waypoints = actions.waypointNames();
        p.append("Waypoints in this world: ").append(waypoints.isEmpty() ? "none"
                : String.join(", ", waypoints)).append('\n');
        List<String> features = actions.labFeatures();
        if (features.isEmpty()) {
            p.append("Lab features: none available\n");
        } else {
            p.append("Lab features (id: state): ");
            List<String> labLines = new ArrayList<>();
            for (String feature : features) {
                labLines.add(feature + ": " + (actions.lab().isEnabled(feature) ? "on" : "off"));
            }
            p.append(String.join(", ", labLines)).append('\n');
        }
        p.append('\n').append(EXAMPLES);
        return p.toString();
    }

    /**
     * Rough token count of a text for the {@value #CONTEXT_TOKENS}-token context: one token per three characters,
     * which over-counts English and the setting ids (Qwen's tokenizer averages closer to four), so a prompt that fits
     * this estimate fits the real context.
     */
    public static int estimateTokens(String text) {
        return text == null ? 0 : (text.length() + 2) / 3;
    }

    /** The request for a question: system prompt, the last {@value #CONTEXT_TURNS} turns, then the question. */
    public LocalAiClient.ChatRequest request(String question) {
        List<LocalAiClient.Message> messages = new ArrayList<>();
        messages.add(LocalAiClient.Message.system(systemPrompt()));
        for (NexusTranscript.Entry entry : transcript.lastEntries(CONTEXT_TURNS * 2)) {
            if (entry.error() || entry.text().isBlank()) {
                continue;
            }
            messages.add(entry.role() == NexusTranscript.Role.USER ? LocalAiClient.Message.user(entry.text())
                    : LocalAiClient.Message.assistant(entry.text()));
        }
        messages.add(LocalAiClient.Message.user(question));
        return LocalAiClient.ChatRequest.of(messages, schema());
    }

    // ---- turns ---------------------------------------------------------------------------------------------------

    /**
     * Sends a question. Exactly one {@code onReply} call follows on the main thread: the applied reply, or a reply
     * carrying the error. A blank question is ignored; one while another is in flight is answered immediately with an
     * error. A question {@link NexusCommands} understands is applied and answered right here, before this method
     * returns, and the backend is not called; every other question goes to the backend (the Local AI).
     */
    public void ask(String question, Consumer<NexusReply> onReply) {
        Objects.requireNonNull(onReply, "onReply");
        String text = question == null ? "" : question.trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > MAX_QUESTION) {
            text = text.substring(0, MAX_QUESTION);
        }
        if (busy) {
            onReply.accept(NexusReply.failed(new LocalAiException(LocalAiException.Kind.BUSY,
                    "a question is still being answered")));
            return;
        }
        NexusCommands.Parse parsed = commands.parse(text);
        if (parsed.understood()) {
            transcript.append(NexusTranscript.Entry.user(text, clock.millis()));
            onReply.accept(applyCommands(text, parsed));
            return;
        }
        LocalAiClient.ChatRequest request = request(text);
        transcript.append(NexusTranscript.Entry.user(text, clock.millis()));
        busy = true;
        String asked = text;
        backend.chat(request, response -> {
            busy = false;
            onReply.accept(handle(asked, response));
        }, error -> {
            busy = false;
            transcript.append(NexusTranscript.Entry.failure(failureText(error), clock.millis()));
            CoreLog.warn("Vanta Nexus could not answer: {} ({})", error.getMessage(), error.kind());
            onReply.accept(NexusReply.failed(error));
        });
    }

    /**
     * Applies an understood instant command as one turn: the actions through {@link NexusActions#execute} (with the
     * end-of-turn overlap repair), one undo turn, the transcript entry with the receipts and a short answer built from
     * what was really applied.
     */
    private NexusReply applyCommands(String question, NexusCommands.Parse parsed) {
        NexusUndo.Turn turn = undo.begin();
        NexusActions.Outcome outcome = actions.execute(parsed.toJsonArray(), turn);
        String message = commands.reply(outcome, parsed.notes());
        boolean undoable = undo.commit(turn, message);
        List<String> applied = new ArrayList<>();
        for (AppliedAction action : outcome.applied()) {
            applied.add(action.summary());
        }
        List<String> rejected = new ArrayList<>();
        for (RejectedAction action : outcome.rejected()) {
            rejected.add(action.summary());
        }
        transcript.append(NexusTranscript.Entry.assistant(message, clock.millis(), applied, rejected));
        CoreLog.info("Vanta Nexus instant command ({} characters) applied without the Local AI: {} applied, {} not "
                + "applied", question.length(), outcome.applied().size(), outcome.rejected().size());
        return new NexusReply(message, outcome.applied(), outcome.rejected(), undoable, Optional.empty(),
                NexusReply.Source.COMMANDS);
    }

    /**
     * The transcript text of a failed model turn. When the model is needed but not installed (or not available on
     * this system), it says which commands work without it (from client 1.6.0 on); otherwise the error's own text.
     */
    static String failureText(LocalAiException error) {
        return switch (error.kind()) {
            case NOT_INSTALLED -> Lang.tr("vanta.nexus.commands.needs_local_ai");
            case UNSUPPORTED_PLATFORM, INVALID_MANIFEST -> Lang.tr("vanta.nexus.commands.local_ai_unavailable",
                    Lang.tr(error.kind().langKey()));
            default -> Lang.tr(error.kind().langKey());
        };
    }

    /**
     * Applies a model reply: parses the JSON, validates and runs the actions, records undo and transcript. Public so
     * tests (and the game test) can feed canned replies.
     */
    public NexusReply handle(String question, LocalAiClient.ChatResponse response) {
        Objects.requireNonNull(response, "response");
        JsonObject json;
        try {
            json = response.contentAsJson();
        } catch (LocalAiException e) {
            transcript.append(NexusTranscript.Entry.failure(Lang.tr(e.kind().langKey()), clock.millis()));
            CoreLog.warn("Vanta Nexus reply was not valid JSON: {}", abbreviate(response.content()));
            return NexusReply.failed(e);
        }
        String message = NexusActions.str(json, "message").orElse("").trim();
        JsonElement actionsElement = json.get("actions");
        JsonArray actionArray = actionsElement != null && actionsElement.isJsonArray()
                ? actionsElement.getAsJsonArray() : new JsonArray();
        NexusUndo.Turn turn = undo.begin();
        NexusActions.Outcome outcome = actions.execute(actionArray, turn);
        boolean undoable = undo.commit(turn, message);
        List<String> applied = new ArrayList<>();
        for (AppliedAction action : outcome.applied()) {
            applied.add(action.summary());
        }
        List<String> rejected = new ArrayList<>();
        for (RejectedAction action : outcome.rejected()) {
            rejected.add(action.summary());
        }
        transcript.append(NexusTranscript.Entry.assistant(message, clock.millis(), applied, rejected));
        return new NexusReply(message, outcome.applied(), outcome.rejected(), undoable, Optional.empty());
    }

    /** Reverts the last turn that changed something. */
    public boolean undoLast() {
        return undo.undoLast();
    }

    /** Clears the conversation (not the undo history). */
    public void clearTranscript() {
        transcript.clear();
    }

    private static String abbreviate(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 160 ? oneLine.substring(0, 160) + "..." : oneLine;
    }
}
