package dev.vanta.client.lab;

import dev.vanta.core.lab.LabEffects;
import dev.vanta.core.screen.VantaServices;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The client side of the Vanta Lab features: feeds the player's own input, movement and attacks into the core
 * {@link LabEffects} (Dynamic HUD, Animated crosshair). The core applies the resulting curves itself (the HUD renderer
 * multiplies {@code hudAlpha}, the crosshair renderer spreads by {@code crosshairSpread}, the screen framework fades by
 * {@code transitionProgress}); this class only reports what happened and when.
 * <p>
 * Sources, all observers of the user's own actions:
 * <ul>
 *   <li>key and mouse button presses while no screen is open: {@code KeyMapping.click} (the existing
 *       {@code KeyMappingMixin} through {@link dev.vanta.client.hud.ClickCounters}); the attack key also counts as an
 *       attack;</li>
 *   <li>the mouse wheel: the existing {@code MouseHandlerMixin} through {@code VantaRuntime.onMouseScroll};</li>
 *   <li>mouse movement: the cursor position ({@code MouseHandler.xpos/ypos}, which GLFW keeps updating while the
 *       cursor is grabbed) and the player's view angles compared once per tick;</li>
 *   <li>movement: the player's horizontal position change per tick in blocks (a jump of more than
 *       {@value #TELEPORT_BLOCKS} blocks counts as a teleport, not as speed).</li>
 * </ul>
 * Dynamic HUD applies while the player is in a world with no screen open. Outside a world and while any screen is
 * open the relay reports input every tick, so the HUD stays fully visible there (a chat screen must not fade the HUD
 * behind it) and the 10 s countdown starts when the player returns to the game.
 */
public final class LabInputs {
    /** Position jumps above this many blocks per tick are teleports, not movement. */
    public static final double TELEPORT_BLOCKS = 8.0;

    private final VantaServices services;
    private double lastCursorX = Double.NaN;
    private double lastCursorY = Double.NaN;
    private float lastYaw = Float.NaN;
    private float lastPitch = Float.NaN;
    private Vec3 lastPosition;

    public LabInputs(VantaServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    private long now() {
        return services.clock().millis();
    }

    private LabEffects effects() {
        return services.labEffects();
    }

    /** Any key press, mouse button, wheel or movement (render thread). */
    public void onInput() {
        effects().onInput(now());
    }

    /** The attack key was pressed (render thread). */
    public void onAttack() {
        long now = now();
        effects().onInput(now);
        effects().onAttack(now);
    }

    /** Once per client tick: cursor and view movement, player speed, and the keep-alive outside gameplay. */
    public void tick(Minecraft client) {
        if (client == null) {
            return;
        }
        long now = now();
        LabEffects effects = effects();
        if (client.mouseHandler != null) {
            double x = client.mouseHandler.xpos();
            double y = client.mouseHandler.ypos();
            if (!Double.isNaN(lastCursorX) && (x != lastCursorX || y != lastCursorY)) {
                effects.onInput(now);
            }
            lastCursorX = x;
            lastCursorY = y;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            lastYaw = Float.NaN;
            lastPitch = Float.NaN;
            lastPosition = null;
            // No world: the HUD is not drawn anyway, and the countdown must not run while the player is in a menu.
            effects.onInput(now);
            effects.onMovement(0.0, now);
            return;
        }
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (!Float.isNaN(lastYaw) && (yaw != lastYaw || pitch != lastPitch)) {
            effects.onInput(now);
        }
        lastYaw = yaw;
        lastPitch = pitch;

        Vec3 position = player.position();
        double speed = 0.0;
        if (lastPosition != null) {
            double dx = position.x - lastPosition.x;
            double dz = position.z - lastPosition.z;
            speed = Math.sqrt(dx * dx + dz * dz);
            if (speed > TELEPORT_BLOCKS) {
                speed = 0.0;
            }
        }
        lastPosition = position;
        effects.onMovement(speed, now);
        if (speed > 0.0) {
            effects.onInput(now);
        }
        if (client.screen != null) {
            // A screen is open (chat, inventory, a VANTA screen): the HUD stays visible behind it.
            effects.onInput(now);
        }
    }
}
