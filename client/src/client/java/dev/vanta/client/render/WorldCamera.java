package dev.vanta.client.render;

import java.util.Optional;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * The camera of the current world frame, as the level renderer sees it, so the HUD pass can project world positions
 * onto the screen exactly where the player sees them (view bobbing and the zoom's narrowed field of view included):
 * <ul>
 *   <li>the camera position and the view matrix come from Fabric's {@code WorldRenderEvents.END_EXTRACTION} (the
 *       {@code viewMatrix} the game hands to {@code LevelRenderer.renderLevel}), captured by
 *       {@link WaypointBeamRenderer} every frame;</li>
 *   <li>the vertical field of view in degrees comes from the {@code GameRendererMixin} at the return of the world
 *       camera's {@code getFov} call, after the zoom divided it: the value the game builds its projection from;</li>
 *   <li>the projection itself is rebuilt here from that field of view and the window's aspect ratio the way the game
 *       does ({@code perspective(fov, width / height, near, far)}); the near and far planes only affect depth, not
 *       where a point lands on screen.</li>
 * </ul>
 * Render thread only (the world pass and the HUD pass run on it in sequence). Without a capture in the last
 * {@link #MAX_AGE_NANOS} (no world frame, a replaced render pipeline) {@link #isFresh()} is false and nothing is
 * projected.
 */
public final class WorldCamera {
    /** A capture older than this (half a second) is stale; the world pass stopped running. */
    public static final long MAX_AGE_NANOS = 500_000_000L;
    /** Near plane of the rebuilt projection (the game's value). */
    public static final float NEAR = 0.05f;
    /** Far plane of the rebuilt projection; depth only, irrelevant for the screen position. */
    public static final float FAR = 4096f;

    /** A projected point in GUI pixels and its distance along the view axis (positive in front of the camera). */
    public record ScreenPoint(double x, double y, double depth) {
    }

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector4f SCRATCH = new Vector4f();
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static float fovDegrees = 70f;
    private static long capturedAtNanos;
    private static long frames;

    private WorldCamera() {
    }

    /**
     * Stores this frame's camera position and view matrix (world render extraction).
     *
     * @param view the view matrix of the world pass (rotation and bobbing, no camera translation)
     */
    public static void captureView(double x, double y, double z, Matrix4fc view) {
        VIEW.set(view);
        cameraX = x;
        cameraY = y;
        cameraZ = z;
        capturedAtNanos = System.nanoTime();
        frames++;
    }

    /** The world camera's vertical field of view in degrees for this frame (after the zoom), from the FOV hook. */
    public static void onWorldFov(float degrees) {
        if (degrees > 0f && Float.isFinite(degrees)) {
            fovDegrees = degrees;
        }
    }

    /** True when a world frame was captured within {@link #MAX_AGE_NANOS}. */
    public static boolean isFresh() {
        return capturedAtNanos != 0L && System.nanoTime() - capturedAtNanos < MAX_AGE_NANOS;
    }

    /** Number of captured world frames since start (game test diagnostics). */
    public static long frames() {
        return frames;
    }

    /** The field of view the projection uses. */
    public static float fovDegrees() {
        return fovDegrees;
    }

    /** Camera x of the last capture. */
    public static double cameraX() {
        return cameraX;
    }

    /** Camera y of the last capture. */
    public static double cameraY() {
        return cameraY;
    }

    /** Camera z of the last capture. */
    public static double cameraZ() {
        return cameraZ;
    }

    /**
     * Projects a world position onto the screen.
     *
     * @param aspect    framebuffer width divided by height
     * @param guiWidth  GUI width in scaled pixels
     * @param guiHeight GUI height in scaled pixels
     * @return the screen point, or empty when the position is behind the camera or no fresh capture exists
     */
    public static Optional<ScreenPoint> project(double x, double y, double z, float aspect, int guiWidth,
                                                int guiHeight) {
        if (!isFresh() || !(aspect > 0f)) {
            return Optional.empty();
        }
        VIEW_PROJECTION.setPerspective((float) Math.toRadians(fovDegrees), aspect, NEAR, FAR).mul(VIEW);
        Vector4f clip = SCRATCH.set((float) (x - cameraX), (float) (y - cameraY), (float) (z - cameraZ), 1f);
        VIEW_PROJECTION.transform(clip);
        if (clip.w <= 1.0e-4f) {
            return Optional.empty();
        }
        double nx = clip.x / clip.w;
        double ny = clip.y / clip.w;
        return Optional.of(new ScreenPoint((nx + 1.0) * 0.5 * guiWidth, (1.0 - ny) * 0.5 * guiHeight, clip.w));
    }
}
