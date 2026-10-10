package dev.vanta.core.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.core.config.CoreLog;
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

    private final NexusActions actions;
    private final NexusUndo undo;
    private final NexusTranscript transcript;
    private final ChatBackend backend;
    private final HudStore hud;
    private final ProfileManager profiles;
    private final PerformanceCenter performance;
    private final Clock clock;
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
                .append("with their own client only: HUD layout, settings, profiles, performance presets, waypoints and ")
                .append("Vanta Lab features. You never act in the game world, never touch other players, keybinds, the ")
                .append("network or files.\n");
        p.append("Reply with one JSON object only: {\"message\": string, \"actions\": array}. \"message\" is one or two ")
                .append("plain English sentences for the player, no markdown. \"actions\" lists the changes to make now, ")
                .append("in order. Return an empty array when nothing should change or when you need to ask a question. ")
                .append("Use only the action types and the ids, names and values listed below; never invent names. If a ")
                .append("request is outside these actions, say so briefly.\n\n");
        p.append("Action types:\n");
        p.append("- hud.set {element, visible?, anchor?, x?, y?, scale?, opacity?}: show or hide a widget, anchor it ")
                .append("(top_left, top_center, top_right, middle_left, center, middle_right, bottom_left, bottom_center, ")
                .append("bottom_right), place it at x,y as fractions of the screen (0 = left/top, 1 = right/bottom), ")
                .append("scale 0.5..2, opacity 0.1..1. A widget type that is not on the HUD yet is added.\n");
        p.append("- hud.only {elements: [ids]}: show exactly these widgets and hide every other one (the crosshair stays).\n");
        p.append("- hud.layout.save {name} and hud.layout.load {name}: save the current layout under a name, or load a ")
                .append("saved layout.\n");
        p.append("- hud.preset {preset}: one of ").append(String.join(", ", actions.hudPresetIds())).append(".\n");
        p.append("- profile.switch {name} and profile.create {name, fromCurrent: true}: switch to a profile, or save ")
                .append("the current setup as a new profile.\n");
        p.append("- perf.preset {preset}: one of ").append(String.join(", ", actions.perfPresetIds()))
                .append(". perf.smartBoost {run: true}: measure this PC and pick a preset automatically.\n");
        p.append("- setting.set {id, value}: change one of the settings listed below.\n");
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
        p.append("Saved HUD layouts: ");
        List<String> layouts = new ArrayList<>();
        for (HudPreset preset : hud.allPresets()) {
            layouts.add(Lang.tr(preset.langKey()));
        }
        p.append(layouts.isEmpty() ? "none" : String.join(", ", layouts)).append("\n\n");

        p.append("Settings you may change (id: allowed values, current):\n");
        for (Setting<?> setting : actions.allowedSettings()) {
            p.append(setting.id()).append(": ").append(NexusActions.allowedValues(setting)).append(", current ")
                    .append(actions.currentValue(setting)).append('\n');
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
        return p.toString();
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
     * carrying the error. A blank question or one while another is in flight is answered immediately with an error.
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
        LocalAiClient.ChatRequest request = request(text);
        transcript.append(NexusTranscript.Entry.user(text, clock.millis()));
        busy = true;
        String asked = text;
        backend.chat(request, response -> {
            busy = false;
            onReply.accept(handle(asked, response));
        }, error -> {
            busy = false;
            transcript.append(NexusTranscript.Entry.failure(Lang.tr(error.kind().langKey()), clock.millis()));
            CoreLog.warn("Vanta Nexus could not answer: {} ({})", error.getMessage(), error.kind());
            onReply.accept(NexusReply.failed(error));
        });
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
