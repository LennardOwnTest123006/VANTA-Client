package dev.vanta.core.bridge;

/**
 * Pressed state of the movement and mouse inputs shown by the keystrokes HUD widget.
 * All values are read from the game's own key mappings; VANTA never sends inputs.
 */
public record KeyStates(boolean forward, boolean left, boolean back, boolean right, boolean jump, boolean sneak,
                        boolean attack, boolean use) {
    /** Nothing pressed. */
    public static final KeyStates NONE = new KeyStates(false, false, false, false, false, false, false, false);
}
