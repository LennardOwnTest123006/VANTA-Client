package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.config.MutableClock;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.settings.VantaSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NexusAssistantTest {
    @TempDir
    Path dir;
    ServicesFixture fixture;
    VantaServices services;
    final FakeNexusIntegrations.Waypoints waypoints = new FakeNexusIntegrations.Waypoints("Home", "Nether hub");
    final FakeNexusIntegrations.Lab lab = new FakeNexusIntegrations.Lab("dynamic_hud", "animated_crosshair");

    /** A backend that records requests and answers from a queue, synchronously. */
    static final class FakeBackend implements ChatBackend {
        final List<LocalAiClient.ChatRequest> requests = new ArrayList<>();
        String nextContent;
        LocalAiException nextError;
        Consumer<LocalAiClient.ChatResponse> pendingReply;

        @Override
        public void chat(LocalAiClient.ChatRequest request, Consumer<LocalAiClient.ChatResponse> onReply,
                         Consumer<LocalAiException> onError) {
            requests.add(request);
            if (nextError != null) {
                onError.accept(nextError);
            } else if (nextContent != null) {
                onReply.accept(new LocalAiClient.ChatResponse(nextContent, Optional.empty(), "stop", 10, 5));
            } else {
                pendingReply = onReply;
            }
        }
    }

    final FakeBackend backend = new FakeBackend();
    NexusAssistant assistant;

    @BeforeEach
    void setUp() {
        fixture = new ServicesFixture(dir);
        services = fixture.services;
        services.setWaypoints(waypoints, waypoints);
        services.setLabToggle(lab);
        assistant = new NexusAssistant(services.nexusActions(), services.nexusUndo(), services.nexusTranscript(),
                backend, services.hud(), services.profiles(), services.performance(), fixture.clock);
    }

    @Test
    void systemPromptListsTheLiveRegistries() {
        String prompt = assistant.systemPrompt();
        assertTrue(prompt.startsWith("You are Vanta Nexus"));
        assertTrue(prompt.contains("fps: fps, visible, top_left, 1, 1"), prompt);
        assertTrue(prompt.contains("coordinates: coordinates, visible"), prompt);
        assertTrue(prompt.contains("Widget types not on the HUD"), prompt);
        assertTrue(prompt.contains("memory"), prompt);
        assertTrue(prompt.contains("Saved HUD layouts: Default, Minimal, PvP, Streamer, Performance"), prompt);
        assertTrue(prompt.contains("hud.globalScale: number 0.5..2, current 1"), prompt);
        assertTrue(prompt.contains("video.renderDistance: integer 2..32, current 10"), prompt);
        assertTrue(prompt.contains("performance.perfPreset: one of boost, low, balanced, high, ultra, current balanced"),
                prompt);
        assertFalse(prompt.contains("controls.autoJump"), "forbidden categories are not offered");
        assertFalse(prompt.contains("privacy."), "forbidden categories are not offered");
        assertTrue(prompt.contains("Profiles: Default (active), PvP, Survival, Building, Recording, Performance, Minimal"),
                prompt);
        assertTrue(prompt.contains("Performance presets: boost, low, balanced, high, ultra"), prompt);
        assertTrue(prompt.contains("Waypoints in this world: Home, Nether hub"), prompt);
        assertTrue(prompt.contains("Lab features (id: state): dynamic_hud: off, animated_crosshair: off"), prompt);
        assertTrue(prompt.contains("hud.preset {preset}: one of minimal, pvp, recording, survival, building, full"));
        assertFalse(prompt.contains("—"), "no em dashes in new prose");
        assertTrue(prompt.length() < 8000, "fits a 4096-token context with room for the answer: " + prompt.length());

        services.hud().setLayout(services.hud().layout().with(services.hud().layout().find("fps").orElseThrow()
                .withEnabled(false).withScale(1.5)));
        assertTrue(assistant.systemPrompt().contains("fps: fps, hidden, top_left, 1.5, 1"), "live state");
    }

    @Test
    void systemPromptOffersSoundChatCrosshairAndArranging() {
        String prompt = assistant.systemPrompt();
        assertTrue(prompt.contains("- hud.arrange {corner?}: sort, arrange, tidy, organize, order or line up the HUD "
                + "(also \"sortieren\", \"ordnen\")"), prompt);
        assertTrue(prompt.contains("- crosshair.set {preset?, shape?, size?, thickness?, gap?, color?, outline?, "
                + "opacity?, vanilla?}: preset default, dot, thin, bold, circle, precision; shape cross, dot, circle, "
                + "square, chevron, plus_dot; size 1..32, thickness 1..8, gap 0..16, opacity 0.1..1"), prompt);
        assertTrue(prompt.contains("Crosshair now: custom, shape cross, size 5, thickness 1, gap 1, color white, "
                + "outline on, opacity 1"), prompt);
        assertTrue(prompt.contains(NexusAssistant.FRACTION_RULE), prompt);
        assertTrue(prompt.contains("audio.music=1"), prompt);
        assertTrue(prompt.contains("audio.subtitles=false"), prompt);
        assertTrue(prompt.contains("accessibility.chatScale=1"), prompt);
        assertTrue(prompt.contains("accessibility.chatHeightUnfocused=0.44"), prompt);
        assertTrue(prompt.contains("On/off, true or false (id=current): hud.enabled=true"), prompt);
        assertTrue(prompt.endsWith(NexusAssistant.EXAMPLES), "the examples come last");
        assertFalse(prompt.contains("audio.openVanilla"), "buttons are not settings");
        assertFalse(prompt.contains("—"), "no em dashes in new prose");
        assertTrue(NexusAssistant.estimateTokens(prompt) <= NexusAssistant.PROMPT_TOKEN_BUDGET,
                "estimated " + NexusAssistant.estimateTokens(prompt) + " tokens");
        assertEquals(NexusAssistant.CONTEXT_TOKENS, 4096);
        assertTrue(NexusAssistant.PROMPT_TOKEN_BUDGET + LocalAiClient.MAX_TOKENS < NexusAssistant.CONTEXT_TOKENS,
                "room for the answer and the conversation");
    }

    @Test
    void aFullyStockedClientStillFitsTheContext() {
        for (String type : services.nexusActions().addableWidgetTypes()) {
            services.nexusActions().applyOne(com.google.gson.JsonParser.parseString(
                    "{\"type\":\"hud.set\",\"element\":\"" + type + "\",\"visible\":true}").getAsJsonObject(),
                    services.nexusUndo().begin(), new ArrayList<>());
        }
        for (int i = 0; i < 4; i++) {
            services.hud().saveUserPreset("My layout number " + i);
        }
        for (int i = 0; i < 10; i++) {
            waypoints.enabled.put("Waypoint number " + i, true);
        }
        String prompt = assistant.systemPrompt();
        assertTrue(prompt.contains("memory: memory, visible"), "every widget type is on the HUD");
        assertTrue(NexusAssistant.estimateTokens(prompt) <= NexusAssistant.PROMPT_TOKEN_BUDGET + 200,
                "estimated " + NexusAssistant.estimateTokens(prompt) + " tokens for " + prompt.length() + " chars");
    }

    @Test
    void everyFewShotExampleIsAValidTurn() {
        int examples = 0;
        for (String line : NexusAssistant.EXAMPLES.split("\n")) {
            int start = line.indexOf(": [");
            if (start < 0) {
                continue;
            }
            examples++;
            com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(line.substring(start + 2))
                    .getAsJsonArray();
            NexusUndo.Turn turn = services.nexusUndo().begin();
            NexusActions.Outcome outcome = services.nexusActions().execute(array, turn);
            assertEquals(List.of(), outcome.rejected(), line);
            assertFalse(outcome.applied().isEmpty(), line);
            services.nexusUndo().commit(turn, line);
        }
        assertTrue(examples >= 6 && examples <= 10, "6 to 10 examples: " + examples);
        assertEquals(0.0, fixture.options.getDouble(dev.vanta.core.bridge.VanillaOption.MUSIC_VOLUME, -1), 1e-9);
        assertEquals(0.5, fixture.options.getDouble(dev.vanta.core.bridge.VanillaOption.MASTER_VOLUME, -1), 1e-9);
        assertEquals(0.8, fixture.options.getDouble(dev.vanta.core.bridge.VanillaOption.CHAT_SCALE, -1), 1e-9);
        assertEquals(dev.vanta.core.crosshair.CrosshairShape.DOT, services.crosshair().style().shape());
    }

    @Test
    void theReportedRequestsWorkThroughAModelReply() {
        List<NexusReply> replies = new ArrayList<>();
        backend.nextContent = "{\"message\":\"Music is off.\",\"actions\":[{\"type\":\"setting.set\","
                + "\"id\":\"audio.music\",\"value\":0}]}";
        assistant.ask("make music off", replies::add);
        assertEquals(0.0, fixture.options.getDouble(dev.vanta.core.bridge.VanillaOption.MUSIC_VOLUME, -1), 1e-9);
        backend.nextContent = "{\"message\":\"Circle.\",\"actions\":[{\"type\":\"crosshair.set\","
                + "\"shape\":\"circle\"}]}";
        assistant.ask("make the crossover circle", replies::add);
        assertEquals(dev.vanta.core.crosshair.CrosshairShape.CIRCLE, services.crosshair().style().shape());
        backend.nextContent = "{\"message\":\"Smaller.\",\"actions\":[{\"type\":\"setting.set\","
                + "\"id\":\"accessibility.chatScale\",\"value\":0.8},{\"type\":\"setting.set\","
                + "\"id\":\"accessibility.chatWidth\",\"value\":0.8}]}";
        assistant.ask("make the chat smaller", replies::add);
        assertEquals(0.8, fixture.options.getDouble(dev.vanta.core.bridge.VanillaOption.CHAT_WIDTH, -1), 1e-9);
        backend.nextContent = "{\"message\":\"Removed.\",\"actions\":[{\"type\":\"hud.set\","
                + "\"element\":\"coordinates\",\"visible\":false}]}";
        assistant.ask("delete the cordinates", replies::add);
        assertFalse(services.hud().layout().find("coordinates").orElseThrow().enabled());
        backend.nextContent = "{\"message\":\"Sorted.\",\"actions\":[{\"type\":\"hud.arrange\"}]}";
        assistant.ask("sort the guis", replies::add);
        assertTrue(dev.vanta.core.hud.HudArranger.overlaps(services.hud().layout(), 854, 480, 1.0).isEmpty());
        for (NexusReply reply : replies) {
            assertFalse(reply.isError());
            assertEquals(List.of(), reply.rejected(), reply.message());
            assertTrue(reply.undoable(), reply.message());
        }
        assertEquals(5, services.nexusUndo().depth());
        List<NexusTranscript.Entry> entries = services.nexusTranscript().entries();
        // Direction and biome close the gap the hidden coordinates left.
        assertEquals(List.of("HUD arranged (auto): 8 widgets without overlap, 2 moved"),
                entries.get(entries.size() - 1).applied());
        assertEquals(36, services.hud().layout().find("direction").orElseThrow().offsetY());
    }

    @Test
    void requestsCarrySystemContextAndTheQuestion() {
        for (int i = 0; i < 12; i++) {
            services.nexusTranscript().append(NexusTranscript.Entry.user("q" + i, i));
            services.nexusTranscript().append(NexusTranscript.Entry.assistant("a" + i, i, List.of(), List.of()));
        }
        services.nexusTranscript().append(NexusTranscript.Entry.failure("boom", 99));
        LocalAiClient.ChatRequest request = assistant.request("Only show FPS and coordinates");
        List<LocalAiClient.Message> messages = request.messages();
        assertEquals("system", messages.get(0).role());
        assertEquals("user", messages.get(messages.size() - 1).role());
        assertEquals("Only show FPS and coordinates", messages.get(messages.size() - 1).content());
        long context = messages.stream().filter(m -> !m.role().equals("system")).count() - 1;
        assertTrue(context <= NexusAssistant.CONTEXT_TURNS * 2, "at most 8 turns of context: " + context);
        assertTrue(messages.stream().noneMatch(m -> m.content().equals("q0")), "oldest turns drop out");
        assertTrue(messages.stream().noneMatch(m -> m.content().equals("boom")), "failures are not context");
        assertTrue(request.schema().isPresent());
        assertEquals(0.2, request.temperature(), 1e-9);
        assertEquals(512, request.maxTokens());
    }

    @Test
    void askAppliesTheModelsActionsAndRecordsEverything() {
        backend.nextContent = """
                {"message": "Minimal HUD: FPS and coordinates only.",
                 "actions": [{"type": "hud.only", "elements": ["fps", "coordinates"]},
                             {"type": "setting.set", "id": "hud.globalScale", "value": 1.25},
                             {"type": "hud.set", "element": "radar", "visible": true},
                             {"type": "lab.set", "feature": "dynamic_hud", "enabled": true}]}
                """;
        List<NexusReply> replies = new ArrayList<>();
        assistant.ask("  Make my HUD minimal  ", replies::add);
        assertEquals(1, replies.size());
        NexusReply reply = replies.get(0);
        assertFalse(reply.isError());
        assertEquals("Minimal HUD: FPS and coordinates only.", reply.message());
        assertEquals(3, reply.applied().size());
        assertEquals(1, reply.rejected().size());
        assertEquals(RejectedAction.Reason.UNKNOWN_ELEMENT, reply.rejected().get(0).reason());
        assertTrue(reply.undoable());
        assertFalse(assistant.isBusy());
        assertFalse(services.hud().layout().find("armor").orElseThrow().enabled());
        assertEquals(1.25, services.settings().get(VantaSettings.HUD_GLOBAL_SCALE), 1e-9);
        assertTrue(lab.isEnabled("dynamic_hud"));

        assertEquals(1, backend.requests.size());
        assertEquals("Make my HUD minimal", backend.requests.get(0).messages().get(1).content());
        List<NexusTranscript.Entry> entries = services.nexusTranscript().entries();
        assertEquals(2, entries.size());
        assertEquals(NexusTranscript.Role.USER, entries.get(0).role());
        assertEquals("Make my HUD minimal", entries.get(0).text());
        assertEquals("Minimal HUD: FPS and coordinates only.", entries.get(1).text());
        assertEquals(3, entries.get(1).applied().size());
        assertEquals(List.of("No HUD widget named “radar”"), entries.get(1).rejected());
        assertTrue(services.nexusTranscript().isDirty());

        assertTrue(assistant.undoLast());
        assertTrue(services.hud().layout().find("armor").orElseThrow().enabled());
        assertEquals(1.0, services.settings().get(VantaSettings.HUD_GLOBAL_SCALE), 1e-9);
        assertFalse(lab.isEnabled("dynamic_hud"));
    }

    @Test
    void theExampleQuestionsMapToRealChanges() {
        backend.nextContent = "{\"message\":\"Moved.\",\"actions\":[{\"type\":\"hud.set\",\"element\":\"fps\","
                + "\"x\":1,\"y\":1}]}";
        List<NexusReply> replies = new ArrayList<>();
        assistant.ask("Move the FPS counter to the bottom right", replies::add);
        assertEquals(dev.vanta.core.hud.HudAnchor.BOTTOM_RIGHT,
                services.hud().layout().find("fps").orElseThrow().anchor());
        // The bottom-right corner already held the item durability widget: the turn ends with the two stacked, FPS
        // keeping the requested corner spot (from client 1.6.0 on).
        NexusReply moved = replies.get(0);
        assertEquals(List.of("hud.set", "hud.resolve"), moved.applied().stream().map(AppliedAction::type).toList());
        assertEquals(4, services.hud().layout().find("fps").orElseThrow().offsetY());
        assertEquals(20, services.hud().layout().find("item_durability").orElseThrow().offsetY());
        assertTrue(dev.vanta.core.hud.HudArranger.overlaps(services.hud().layout(), 854, 480, 1.0).isEmpty());

        backend.nextContent = "{\"message\":\"Created.\",\"actions\":[{\"type\":\"hud.preset\",\"preset\":\"recording\"},"
                + "{\"type\":\"profile.create\",\"name\":\"Recording\",\"fromCurrent\":true}]}";
        assistant.ask("Create a recording profile", replies::add);
        NexusReply reply = replies.get(1);
        assertEquals(1, reply.rejected().size(), "a built-in Recording profile exists already");
        assertEquals(RejectedAction.Reason.INVALID_VALUE, reply.rejected().get(0).reason());
        assertTrue(services.hud().layout().byType(HudWidgetType.KEYSTROKES).get(0).enabled());
        assertEquals(NexusHudPreset.RECORDING.apply(HudPresets.defaultPreset().layout()
                        .with(HudPresets.defaultPreset().layout().find("fps").orElseThrow()
                                .withPosition(dev.vanta.core.hud.HudAnchor.BOTTOM_RIGHT, 4, 4))
                        .with(HudPresets.defaultPreset().layout().find("item_durability").orElseThrow()
                                .withPosition(dev.vanta.core.hud.HudAnchor.BOTTOM_RIGHT, 4, 20))),
                services.hud().layout());
    }

    @Test
    void busyBlankAndFailedTurnsAreHandled() {
        List<NexusReply> replies = new ArrayList<>();
        assistant.ask("   ", replies::add);
        assertTrue(replies.isEmpty(), "blank questions are ignored");
        assertTrue(backend.requests.isEmpty());

        assistant.ask("first", replies::add);
        assertTrue(assistant.isBusy());
        assistant.ask("second", replies::add);
        assertEquals(1, replies.size());
        assertEquals(LocalAiException.Kind.BUSY, replies.get(0).error().orElseThrow().kind());
        backend.pendingReply.accept(new LocalAiClient.ChatResponse("{\"message\":\"ok\",\"actions\":[]}",
                Optional.empty(), "stop", 1, 1));
        assertEquals(2, replies.size());
        assertEquals("ok", replies.get(1).message());
        assertFalse(replies.get(1).undoable(), "nothing changed");
        assertFalse(assistant.isBusy());

        backend.nextError = new LocalAiException(LocalAiException.Kind.NETWORK, "down");
        assistant.ask("third", replies::add);
        assertEquals(LocalAiException.Kind.NETWORK, replies.get(2).error().orElseThrow().kind());
        List<NexusTranscript.Entry> entries = services.nexusTranscript().entries();
        assertTrue(entries.get(entries.size() - 1).error());
        assertEquals("Connection problem", entries.get(entries.size() - 1).text());

        backend.nextError = null;
        backend.nextContent = "this is not json";
        assistant.ask("fourth", replies::add);
        assertEquals(LocalAiException.Kind.INVALID_RESPONSE, replies.get(3).error().orElseThrow().kind());

        MutableClock clock = fixture.clock;
        clock.advance(1);
        backend.nextContent = "{\"message\":\"no actions field\"}";
        assistant.ask("fifth", replies::add);
        assertEquals("no actions field", replies.get(4).message());
        assertTrue(replies.get(4).applied().isEmpty());
    }
}
