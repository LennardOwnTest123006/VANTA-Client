/**
 * The VANTA retained-mode UI toolkit, independent of Minecraft.
 * <p>
 * Rendering goes through {@link dev.vanta.core.ui.Canvas} (implemented by the client on {@code GuiGraphics} and by
 * the preview module on Java2D); {@link dev.vanta.core.ui.AbstractCanvas} supplies the transform, scissor and alpha
 * bookkeeping so a backend only implements fills, text and images. Screens extend
 * {@link dev.vanta.core.ui.UiScreen}, build a tree of {@link dev.vanta.core.ui.UiNode}s (containers in
 * {@code dev.vanta.core.ui.layout}, widgets in {@code dev.vanta.core.ui.widget}) and usually wrap it in the standard
 * {@link dev.vanta.core.ui.VantaShell} chrome. Colors, spacing and motion come from {@link dev.vanta.core.ui.Theme},
 * which mirrors {@code shared/design/tokens.json}; time comes from a {@link dev.vanta.core.ui.Clock} so every
 * animation is deterministic in tests.
 * <p>
 * Coordinates are GUI-scaled pixels (ints for geometry, doubles for pointer input); key codes are GLFW values from
 * {@link dev.vanta.core.ui.Keys}; colors are packed ARGB ints (see {@link dev.vanta.core.ui.Colors}).
 */
package dev.vanta.core.ui;
