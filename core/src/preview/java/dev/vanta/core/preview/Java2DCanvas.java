package dev.vanta.core.preview;

import dev.vanta.core.ui.AbstractCanvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TextureRef;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link dev.vanta.core.ui.Canvas} on a {@link BufferedImage}. GUI pixels are multiplied by an integer scale so
 * previews look like the game at that GUI scale; fills stay pixel-aligned, text is anti-aliased like the in-game
 * TTF provider output.
 */
public final class Java2DCanvas extends AbstractCanvas {

    private static final Path CLIENT_ASSETS = Path.of("/home/user/VANTA-Client/client/src/main/resources/assets");

    private final BufferedImage image;
    private final Graphics2D g;
    private final int scale;
    private final AffineTransform base;
    private final Deque<AffineTransform> saved = new ArrayDeque<>();
    private final PreviewFonts fonts;
    private final Map<TextureRef, Optional<BufferedImage>> textures = new HashMap<>();

    /** Canvas of {@code width x height} GUI pixels rendered at {@code scale} device pixels per GUI pixel. */
    public Java2DCanvas(int width, int height, int scale) {
        this(width, height, scale, new PreviewFonts());
    }

    Java2DCanvas(int width, int height, int scale, PreviewFonts fonts) {
        super(width, height);
        if (scale < 1) {
            throw new IllegalArgumentException("scale must be >= 1");
        }
        this.scale = scale;
        this.fonts = fonts;
        this.image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
        this.g = image.createGraphics();
        this.base = AffineTransform.getScaleInstance(scale, scale);
        g.setTransform(base);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    /** Device pixels per GUI pixel. */
    public int scale() {
        return scale;
    }

    /** The rendered image. */
    public BufferedImage image() {
        return image;
    }

    /** Fills the whole surface (used for the preview backdrop). */
    public void clear(int argb) {
        AffineTransform t = g.getTransform();
        g.setTransform(new AffineTransform());
        g.setColor(new Color(argb, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setTransform(t);
    }

    /** Writes the image as PNG. */
    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    // ---------------------------------------------------------------- primitives

    @Override
    protected void fillImpl(int x, int y, int w, int h, int argb) {
        g.setColor(new Color(argb, true));
        g.fillRect(x, y, w, h);
    }

    @Override
    protected void textImpl(String text, int x, int y, int argb, FontKind font, boolean shadow) {
        PreviewFonts.Entry entry = fonts.entry(font);
        g.setFont(entry.font());
        if (shadow) {
            g.setColor(new Color(Colors.shadow(argb), true));
            g.drawString(text, x + 1f, y + entry.baseline() + 1f);
        }
        g.setColor(new Color(argb, true));
        g.drawString(text, (float) x, (float) (y + entry.baseline()));
    }

    @Override
    protected void imageImpl(TextureRef texture, int x, int y, int w, int h, float u, float v, float uw, float vh,
                             int texW, int texH, float alpha) {
        Optional<BufferedImage> img = textures.computeIfAbsent(texture, Java2DCanvas::loadTexture);
        if (img.isEmpty()) {
            // Honest placeholder: outlined box with a cross, so a missing texture is obvious in previews.
            int c = Colors.withAlpha(0xFFFF5C7A, alpha);
            strokeRect(x, y, w, h, 1, c);
            line(x, y, x + w - 1, y + h - 1, c);
            return;
        }
        BufferedImage tex = img.get();
        int sx1;
        int sy1;
        int sx2;
        int sy2;
        if (texW <= 1 && texH <= 1) {
            sx1 = 0;
            sy1 = 0;
            sx2 = tex.getWidth();
            sy2 = tex.getHeight();
        } else {
            float fx = (float) tex.getWidth() / texW;
            float fy = (float) tex.getHeight() / texH;
            sx1 = Math.round(u * fx);
            sy1 = Math.round(v * fy);
            sx2 = Math.round((u + uw) * fx);
            sy2 = Math.round((v + vh) * fy);
        }
        java.awt.Composite previous = g.getComposite();
        if (alpha < 1f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
        }
        g.drawImage(tex, x, y, x + w, y + h, sx1, sy1, sx2, sy2, null);
        g.setComposite(previous);
    }

    private static Optional<BufferedImage> loadTexture(TextureRef ref) {
        String resource = "/assets/" + ref.namespace() + "/" + ref.path();
        try (InputStream in = Java2DCanvas.class.getResourceAsStream(resource)) {
            if (in != null) {
                return Optional.ofNullable(ImageIO.read(in));
            }
        } catch (IOException e) {
            // try the repository
        }
        Path file = CLIENT_ASSETS.resolve(ref.namespace()).resolve(ref.path());
        if (Files.isRegularFile(file)) {
            try {
                return Optional.ofNullable(ImageIO.read(file.toFile()));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- text metrics

    @Override
    public int textWidth(String text, FontKind font) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        PreviewFonts.Entry entry = fonts.entry(font);
        Rectangle2D bounds = entry.font().getStringBounds(text, PreviewFonts.renderContext());
        return (int) Math.ceil(bounds.getWidth() - 0.01);
    }

    @Override
    public int lineHeight(FontKind font) {
        return (font == null ? FontKind.UI : font).lineHeight();
    }

    // ---------------------------------------------------------------- transforms & clipping

    @Override
    protected void onPushTranslate(float dx, float dy) {
        saved.push(g.getTransform());
        g.translate(dx, dy);
    }

    @Override
    protected void onPushScale(float sx, float sy) {
        saved.push(g.getTransform());
        g.scale(sx, sy);
    }

    @Override
    protected void onPopTransform(Transform restored) {
        g.setTransform(saved.pop());
    }

    @Override
    protected void onScissorChanged(Rect deviceClip) {
        AffineTransform current = g.getTransform();
        g.setTransform(base);
        if (deviceClip == null) {
            g.setClip(null);
        } else {
            g.setClip(deviceClip.x(), deviceClip.y(), deviceClip.w(), deviceClip.h());
        }
        g.setTransform(current);
    }

    /** Releases the graphics context. */
    public void dispose() {
        g.dispose();
    }
}
