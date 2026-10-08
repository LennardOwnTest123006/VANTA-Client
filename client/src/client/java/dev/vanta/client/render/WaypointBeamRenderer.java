package dev.vanta.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.vanta.client.VantaRuntime;
import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.bridge.Vec3d;
import dev.vanta.core.lab.LabFeature;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.waypoints.Waypoint;
import dev.vanta.core.waypoints.WorldKeys;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The world pass of the waypoints, on Fabric's world render events (the {@code rendering.v1.world} API of Fabric API
 * 0.141 for Minecraft 1.21.11):
 * <ol>
 *   <li>{@link WorldRenderEvents#END_EXTRACTION}: every frame the camera position and the view matrix the game hands
 *       to the level renderer are stored in {@link WorldCamera}, which the marker HUD element projects with later in
 *       the same frame;</li>
 *   <li>{@link WorldRenderEvents#BEFORE_TRANSLUCENT}: while the Vanta Lab feature {@link LabFeature#WAYPOINT_BEAMS}
 *       is on, a translucent vertical beam is drawn at every enabled waypoint of the current world and dimension
 *       within the marker distance: a thin column of four quads from y = {@value #BEAM_BOTTOM} to
 *       {@value #BEAM_TOP} in the waypoint's colour, through the game's {@code RenderTypes.debugFilledBox()} layer
 *       (position + colour, translucent), the layer the game itself uses for simple coloured debug geometry and the
 *       one Fabric's own world render test draws its translucent box with: the context's pose stack already carries
 *       the view, so the beam is translated by minus the camera position and expressed in world coordinates.</li>
 * </ol>
 * Nothing here touches the world or other players; the beams are drawn only for the local player's own waypoints.
 * {@link #beamsLastFrame()} reports the beams of the last frame (client game test).
 */
public final class WaypointBeamRenderer {
    /** Lowest y of a beam (the bottom of the overworld build range). */
    public static final double BEAM_BOTTOM = -64.0;
    /** Highest y of a beam (the top of the overworld build range). */
    public static final double BEAM_TOP = 320.0;
    /** Half width of the beam column in blocks. */
    public static final float HALF_WIDTH = 0.15f;
    /** Alpha of the beam colour (0..255). */
    public static final int BEAM_ALPHA = 0x70;

    private static volatile int beamsLastFrame;
    private static volatile long beamsTotal;

    private WaypointBeamRenderer() {
    }

    /** Registers the world render hooks; call once from the client entrypoint. */
    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(WaypointBeamRenderer::endExtraction);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(WaypointBeamRenderer::beforeTranslucent);
    }

    /** Beams drawn in the most recent world frame. */
    public static int beamsLastFrame() {
        return beamsLastFrame;
    }

    /** Beams drawn since start. */
    public static long beamsTotal() {
        return beamsTotal;
    }

    private static void endExtraction(WorldExtractionContext context) {
        Vec3 cameraPos = context.camera().position();
        WorldCamera.captureView(cameraPos.x, cameraPos.y, cameraPos.z, context.viewMatrix());
    }

    private static void beforeTranslucent(WorldRenderContext context) {
        int beams = 0;
        try {
            beams = renderBeams(context);
        } finally {
            beamsLastFrame = beams;
            beamsTotal += beams;
        }
    }

    private static int renderBeams(WorldRenderContext context) {
        VantaRuntime runtime = VantaRuntime.get();
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack matrices = context.matrices();
        MultiBufferSource consumers = context.consumers();
        if (runtime == null || minecraft == null || minecraft.player == null || minecraft.level == null
                || matrices == null || consumers == null) {
            return 0;
        }
        VantaServices services = runtime.services();
        if (!services.lab().isEnabled(LabFeature.WAYPOINT_BEAMS)
                || !services.settings().get(VantaSettings.WAYPOINTS_ENABLED)) {
            return 0;
        }
        GameBridge game = services.game();
        String worldKey = game.worldKey();
        if (WorldKeys.isNone(worldKey)) {
            return 0;
        }
        Optional<Vec3d> player = game.playerPosition();
        if (player.isEmpty()) {
            return 0;
        }
        String dimension = game.dimensionId().orElse(Waypoint.UNKNOWN_DIMENSION);
        List<Waypoint> waypoints = services.waypoints().enabledIn(worldKey, dimension);
        if (waypoints.isEmpty()) {
            return 0;
        }
        double maxDistance = services.settings().get(VantaSettings.WAYPOINTS_MAX_MARKER_DISTANCE);
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().position();
        VertexConsumer buffer = consumers.getBuffer(RenderTypes.debugFilledBox());
        int drawn = 0;
        matrices.pushPose();
        try {
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f pose = matrices.last().pose();
            for (Waypoint waypoint : waypoints) {
                if (waypoint.distanceTo(player.get()) > maxDistance) {
                    continue;
                }
                int color = (BEAM_ALPHA << 24) | (waypoint.color() & 0xFFFFFF);
                column(buffer, pose, (float) waypoint.x(), (float) waypoint.z(), (float) BEAM_BOTTOM, (float) BEAM_TOP,
                        color);
                drawn++;
            }
        } finally {
            matrices.popPose();
        }
        return drawn;
    }

    /** Four vertical quads around (x, z) from {@code bottom} to {@code top}, world coordinates. */
    private static void column(VertexConsumer buffer, Matrix4f pose, float x, float z, float bottom, float top,
                               int color) {
        float x1 = x - HALF_WIDTH;
        float x2 = x + HALF_WIDTH;
        float z1 = z - HALF_WIDTH;
        float z2 = z + HALF_WIDTH;
        quad(buffer, pose, x1, bottom, z1, x2, bottom, z1, x2, top, z1, x1, top, z1, color); // north face
        quad(buffer, pose, x2, bottom, z2, x1, bottom, z2, x1, top, z2, x2, top, z2, color); // south face
        quad(buffer, pose, x1, bottom, z2, x1, bottom, z1, x1, top, z1, x1, top, z2, color); // west face
        quad(buffer, pose, x2, bottom, z1, x2, bottom, z2, x2, top, z2, x2, top, z1, color); // east face
    }

    private static void quad(VertexConsumer buffer, Matrix4f pose, float ax, float ay, float az, float bx, float by,
                             float bz, float cx, float cy, float cz, float dx, float dy, float dz, int color) {
        buffer.addVertex(pose, ax, ay, az).setColor(color);
        buffer.addVertex(pose, bx, by, bz).setColor(color);
        buffer.addVertex(pose, cx, cy, cz).setColor(color);
        buffer.addVertex(pose, dx, dy, dz).setColor(color);
    }
}
