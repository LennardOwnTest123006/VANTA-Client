package dev.vanta.core.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ai.ChatBackend;
import dev.vanta.core.ai.LocalAiClient;
import dev.vanta.core.ai.LocalAiException;
import dev.vanta.core.ai.NexusReply;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.lab.LabFeature;
import dev.vanta.core.screen.cosmetics.ServicesFixture;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.waypoints.Waypoint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The composition root wires the real waypoint store, the game bridge's position and the Lab settings into the
 * assistant: an assistant turn with {@code waypoint.add} creates a waypoint in the store (coordinates default to
 * where the player stands), {@code lab.set} flips the setting, and Undo takes both back.
 */
class VantaServicesNexusGlueTest {
    @TempDir
    Path dir;
    private ServicesFixture fx;
    private VantaServices services;
    private final CannedBackend backend = new CannedBackend();

    /** Answers every request with the next canned content, synchronously. */
    static final class CannedBackend implements ChatBackend {
        final List<LocalAiClient.ChatRequest> requests = new ArrayList<>();
        String next = "{\"message\":\"ok\",\"actions\":[]}";

        @Override
        public void chat(LocalAiClient.ChatRequest request, Consumer<LocalAiClient.ChatResponse> onReply,
                         Consumer<LocalAiException> onError) {
            requests.add(request);
            onReply.accept(new LocalAiClient.ChatResponse(next, Optional.empty(), "stop", 1, 1));
        }
    }

    @BeforeEach
    void setUp() {
        fx = new ServicesFixture(dir);
        services = fx.services;
        services.setNexusBackend(backend);
    }

    private NexusReply ask(String question) {
        List<NexusReply> replies = new ArrayList<>();
        services.nexus().ask(question, replies::add);
        assertEquals(1, replies.size());
        return replies.get(0);
    }

    @Test
    void waypointAddCreatesAWaypointInTheStoreAtThePlayerPosition() {
        fx.game.position = new Vec3d(10.5, 64.0, -20.25);
        assertEquals("sp:New World", fx.game.worldKey());
        assertEquals(List.of(), services.waypointBridge().names());
        backend.next = "{\"message\":\"Added.\",\"actions\":[{\"type\":\"waypoint.add\",\"name\":\"Home\","
                + "\"category\":\"Base\"}]}";
        NexusReply reply = ask("Add a waypoint here called Home");
        assertFalse(reply.isError());
        assertEquals(1, reply.applied().size(), reply.rejected().toString());
        assertEquals("waypoint.add", reply.applied().get(0).type());
        Waypoint home = services.waypoints().find("sp:New World", "Home").orElseThrow();
        assertEquals(10.5, home.x(), 1e-9);
        assertEquals(64.0, home.y(), 1e-9);
        assertEquals(-20.25, home.z(), 1e-9);
        assertEquals("Base", home.category());
        assertEquals("minecraft:overworld", home.dimension());
        assertEquals(List.of("Home"), services.waypointBridge().names());
        assertTrue(services.waypointBridge().has("home"));
        assertTrue(services.nexus().systemPrompt().contains("Waypoints in this world: Home"));

        backend.next = "{\"message\":\"Added.\",\"actions\":[{\"type\":\"waypoint.add\",\"name\":\"Portal\","
                + "\"x\":100,\"y\":70,\"z\":-5}]}";
        ask("Add Portal at 100 70 -5");
        Waypoint portal = services.waypoints().find("sp:New World", "Portal").orElseThrow();
        assertEquals(100.0, portal.x(), 1e-9);
        assertEquals(2, services.waypoints().size());

        assertTrue(services.nexus().undoLast(), "undo removes the last added waypoint");
        assertEquals(1, services.waypoints().size());
        assertTrue(services.waypoints().find("sp:New World", "Portal").isEmpty());

        backend.next = "{\"message\":\"Off.\",\"actions\":[{\"type\":\"waypoint.toggle\",\"name\":\"Home\","
                + "\"enabled\":false}]}";
        ask("Hide Home");
        assertFalse(services.waypoints().find("sp:New World", "Home").orElseThrow().enabled());
        backend.next = "{\"message\":\"Gone.\",\"actions\":[{\"type\":\"waypoint.remove\",\"name\":\"Home\"}]}";
        ask("Remove Home");
        assertEquals(0, services.waypoints().size());
    }

    @Test
    void waypointActionsAreRefusedOutsideAWorld() {
        fx.game.onTitleScreen();
        backend.next = "{\"message\":\"Added.\",\"actions\":[{\"type\":\"waypoint.add\",\"name\":\"Home\"}]}";
        NexusReply reply = ask("Add a waypoint here");
        assertEquals(0, reply.applied().size());
        assertEquals(1, reply.rejected().size());
        assertEquals(0, services.waypoints().size());
        assertEquals(List.of(), services.waypointBridge().names());
    }

    @Test
    void labSetFlipsTheSettingAndUndoFlipsItBack() {
        assertFalse(services.lab().isEnabled(LabFeature.DYNAMIC_HUD));
        assertEquals(List.of("dynamic_hud", "animated_crosshair", "waypoint_beams", "frametime_graph",
                "screen_transitions"), services.nexusActions().labFeatures());
        backend.next = "{\"message\":\"On.\",\"actions\":[{\"type\":\"lab.set\",\"feature\":\"dynamic_hud\","
                + "\"enabled\":true}]}";
        NexusReply reply = ask("Turn on the dynamic HUD");
        assertEquals(1, reply.applied().size(), reply.rejected().toString());
        assertTrue(services.lab().isEnabled(LabFeature.DYNAMIC_HUD));
        assertTrue(services.settings().get(VantaSettings.LAB_DYNAMIC_HUD));
        assertTrue(services.nexus().systemPrompt().contains("dynamic_hud: on"));
        assertTrue(services.nexus().undoLast());
        assertFalse(services.lab().isEnabled(LabFeature.DYNAMIC_HUD));

        backend.next = "{\"message\":\"?\",\"actions\":[{\"type\":\"lab.set\",\"feature\":\"teleport\",\"enabled\":true}]}";
        NexusReply refused = ask("Enable teleport");
        assertEquals(1, refused.rejected().size());
    }

    @Test
    void labEffectsAndTheBackendAreExposed() {
        assertNotNull(services.labEffects());
        assertEquals(services.lab(), services.labEffects().settings());
        assertEquals(backend, services.nexusBackend());
        assertEquals(1f, services.labEffects().hudAlpha(fx.clock.millis()), 1e-6f);
    }
}
