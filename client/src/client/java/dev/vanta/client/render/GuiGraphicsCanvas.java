package dev.vanta.client.render;

import dev.vanta.client.perf.FrameProbe;
import dev.vanta.core.ui.AbstractCanvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TextureRef;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The core {@link dev.vanta.core.ui.Canvas} implemented on top of {@link GuiGraphics}.
 * <p>
 * One instance is created per frame for the surface being drawn (a screen, the HUD, a vanilla screen overlay).
 * {@link AbstractCanvas} owns the transform stack, the scissor intersection and the partial-alpha multiplier; this
 * class maps the resulting primitives to the game:
 * <ul>
 *   <li>fills go to {@link GuiGraphics#fill}, vertical gradients to {@link GuiGraphics#fillGradient}; every other shape
 *       (rounded corners, horizontal gradients, lines, circles) is composed of fills by the core defaults so it looks
 *       identical to the Java2D previews;</li>
 *   <li>text is drawn with {@link GuiGraphics#drawString} using a {@link net.minecraft.network.chat.Component} styled with
 *       the VANTA font provider ({@link VantaFonts}), or as a plain string for the vanilla font;</li>
 *   <li>images use {@link GuiGraphics#blit} with {@link RenderPipelines#GUI_TEXTURED}; partial alpha is applied through the
 *       colour tint argument;</li>
 *   <li>transforms push onto {@link GuiGraphics#pose()} so every draw call is transformed by the game itself;</li>
 *   <li>clips are applied with {@link GuiGraphics#enableScissor} under an identity pose, because the game transforms the
 *       scissor rectangle by the current pose while {@link AbstractCanvas} already supplies device coordinates. The game
 *       keeps only one scissor active at a time here; nesting is resolved by the core's intersection logic.</li>
 * </ul>
 * Colours with an alpha below 4 are skipped for text because the vanilla font treats such colours as opaque.
 */
public final class GuiGraphicsCanvas extends AbstractCanvas {
    private final GuiGraphics graphics;
    private final Font font;
    private final boolean allowBlur;
    private boolean nativeScissorActive;
    private boolean blurred;

    /**
     * @param graphics  the frame's graphics
     * @param font      the game font
     * @param width     surface width in GUI pixels
     * @param height    surface height in GUI pixels
     * @param allowBlur whether {@link #blurBackground()} may request the one blur the game allows per frame (only the
     *                  screen canvas may; HUD and overlay canvases pass {@code false})
     */
    public GuiGraphicsCanvas(GuiGraphics graphics, Font font, int width, int height, boolean allowBlur) {
        super(width, height);
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        this.font = Objects.requireNonNull(font, "font");
        this.allowBlur = allowBlur;
    }

    /** The wrapped graphics (for callers that need vanilla drawing next to core drawing). */
    public GuiGraphics graphics() {
        return graphics;
    }

    // ---- primitives --------------------------------------------------------------------------------------------

    @Override
    protected void fillImpl(int x, int y, int w, int h, int argb) {
        FrameProbe.countFill();
        graphics.fill(x, y, x + w, y + h, argb);
    }

    @Override
    public void fillGradientV(int x, int y, int w, int h, int argbTop, int argbBottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int top = applyAlpha(argbTop);
        int bottom = applyAlpha(argbBottom);
        if (Colors.alpha(top) == 0 && Colors.alpha(bottom) == 0) {
            return;
        }
        FrameProbe.countGradient();
        graphics.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    @Override
    protected void textImpl(String text, int x, int y, int argb, FontKind kind, boolean shadow) {
        if (Colors.alpha(argb) < 4) {
            return;
        }
        FrameProbe.countText();
        if (VantaFonts.isCustom(kind)) {
            graphics.drawString(font, VantaFonts.styled(text, kind), x, y, argb, shadow);
        } else {
            graphics.drawString(font, text, x, y, argb, shadow);
        }
    }

    @Override
    protected void imageImpl(TextureRef texture, int x, int y, int w, int h, float u, float v, float uw, float vh,
                             int texW, int texH, float alpha) {
        if (alpha <= 0f) {
            return;
        }
        FrameProbe.countImage();
        Identifier id = Identifier.fromNamespaceAndPath(texture.namespace(), texture.path());
        int regionW = Math.max(1, Math.round(uw));
        int regionH = Math.max(1, Math.round(vh));
        int tint = ARGB.white(Math.min(1f, alpha));
        graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, regionW, regionH, Math.max(1, texW),
                Math.max(1, texH), tint);
    }

    // ---- text metrics ------------------------------------------------------------------------------------------

    @Override
    public int textWidth(String text, FontKind kind) {
        return MinecraftTextMetrics.measure(font, text, kind == null ? FontKind.UI : kind);
    }

    @Override
    public int lineHeight(FontKind kind) {
        return VantaFonts.lineHeight(kind == null ? FontKind.UI : kind);
    }

    // ---- transforms --------------------------------------------------------------------------------------------

    @Override
    protected void onPushTranslate(float dx, float dy) {
        FrameProbe.countTransformPush();
        graphics.pose().pushMatrix();
        graphics.pose().translate(dx, dy);
    }

    @Override
    protected void onPushScale(float sx, float sy) {
        FrameProbe.countTransformPush();
        graphics.pose().pushMatrix();
        graphics.pose().scale(sx, sy);
    }

    @Override
    protected void onPopTransform(Transform restored) {
        graphics.pose().popMatrix();
    }

    // ---- scissor -----------------------------------------------------------------------------------------------

    @Override
    protected void onScissorChanged(Rect deviceClip) {
        FrameProbe.countScissorChange();
        if (nativeScissorActive) {
            graphics.disableScissor();
            nativeScissorActive = false;
        }
        if (deviceClip == null) {
            return;
        }
        int x1 = deviceClip.x();
        int y1 = deviceClip.y();
        int x2 = Math.max(x1, deviceClip.right());
        int y2 = Math.max(y1, deviceClip.bottom());
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        graphics.enableScissor(x1, y1, x2, y2);
        graphics.pose().popMatrix();
        nativeScissorActive = true;
    }

    // ---- blur --------------------------------------------------------------------------------------------------

    /**
     * Requests the menu background blur. The game allows a single blur per frame, so only the screen canvas may ask
     * for it, only once, and only while a world is rendered behind the screen.
     */
    @Override
    public void blurBackground() {
        if (!allowBlur || blurred) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        blurred = true;
        graphics.blurBeforeThisStratum();
    }

    /** Releases a still-active native scissor; call when the frame's drawing is complete. */
    public void finish() {
        if (nativeScissorActive) {
            graphics.disableScissor();
            nativeScissorActive = false;
        }
    }
}
