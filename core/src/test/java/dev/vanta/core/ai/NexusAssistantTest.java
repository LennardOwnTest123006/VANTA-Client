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

        backend.nextContent = "{\"message\":\"Created.\",\"actions\":[{\"type\":\"hud.preset\",\"preset\":\"recording\"},"
                + "{\"type\":\"profile.create\",\"name\":\"Recording\",\"fromCurrent\":true}]}";
        assistant.ask("Create a recording profile", replies::add);
        NexusReply reply = replies.get(1);
        assertEquals(1, reply.rejected().size(), "a built-in Recording profile exists already");
        assertEquals(RejectedAction.Reason.INVALID_VALUE, reply.rejected().get(0).reason());
        assertTrue(services.hud().layout().byType(HudWidgetType.KEYSTROKES).get(0).enabled());
        assertEquals(NexusHudPreset.RECORDING.apply(HudPresets.defaultPreset().layout()
                        .with(HudPresets.defaultPreset().layout().find("fps").orElseThrow()
                                .withPosition(dev.vanta.core.hud.HudAnchor.BOTTOM_RIGHT, 4, 4))),
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
