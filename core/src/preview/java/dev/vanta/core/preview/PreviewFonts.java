package dev.vanta.core.preview;

import dev.vanta.core.ui.FontKind;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads the bundled TTFs for the Java2D canvas and derives per-{@link FontKind} sizes that reproduce
 * Minecraft's TTF glyph provider: the provider's {@code size} is the font's ascent + descent in GUI pixels and
 * the baseline sits at {@code ascent + shift} below the text origin.
 */
final class PreviewFonts {

    /** Resolved font for one kind. */
    record Entry(Font font, int baseline) {
    }

    private static final FontRenderContext FRC = new FontRenderContext(new AffineTransform(), true, true);
    private static final Path REPO_FONTS = Path.of("/home/user/VANTA-Client/assets/fonts");

    private final Map<FontKind, Entry> entries = new EnumMap<>(FontKind.class);

    PreviewFonts() {
        Font interMedium = load("inter_medium.ttf", REPO_FONTS.resolve("inter/Inter-Medium.ttf"));
        Font interBold = load("inter_bold.ttf", REPO_FONTS.resolve("inter/Inter-Bold.ttf"));
        Font grotesk = load("space_grotesk_bold.ttf", REPO_FONTS.resolve("space-grotesk/SpaceGrotesk-Bold.ttf"));
        entries.put(FontKind.UI, derive(interMedium, FontKind.UI));
        entries.put(FontKind.UI_BOLD, derive(interBold, FontKind.UI_BOLD));
        entries.put(FontKind.DISPLAY, derive(grotesk, FontKind.DISPLAY));
        // Vanilla glyphs are 7 px tall capitals on a 9 px line; a monospaced face at this size is the closest stand-in.
        Font mono = new Font(Font.MONOSPACED, Font.BOLD, 10).deriveFont(9.6f);
        entries.put(FontKind.MINECRAFT, new Entry(mono, 7));
    }

    private static Font load(String bundledName, Path repoFallback) {
        try (InputStream in = PreviewFonts.class.getResourceAsStream("/assets/vanta/font/" + bundledName)) {
            if (in != null) {
                return Font.createFont(Font.TRUETYPE_FONT, in);
            }
        } catch (IOException | FontFormatException e) {
            // fall through to the repository copy
        }
        if (Files.isRegularFile(repoFallback)) {
            try (InputStream in = Files.newInputStream(repoFallback)) {
                return Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (IOException | FontFormatException e) {
                // fall through to the platform font
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    }

    /** Scales so that ascent + descent equals the provider size, then places the baseline like Minecraft does. */
    private static Entry derive(Font base, FontKind kind) {
        Font probe = base.deriveFont(100f);
        LineMetrics lm = probe.getLineMetrics("Hgp", FRC);
        float heightPerPoint = (lm.getAscent() + lm.getDescent()) / 100f;
        float em = kind.size() / heightPerPoint;
        Font sized = base.deriveFont(em);
        LineMetrics sizedMetrics = sized.getLineMetrics("Hgp", FRC);
        int baseline = Math.round(sizedMetrics.getAscent() + kind.baselineShift());
        return new Entry(sized, baseline);
    }

    Entry entry(FontKind kind) {
        return entries.get(kind == null ? FontKind.UI : kind);
    }

    static FontRenderContext renderContext() {
        return FRC;
    }
}
