package dev.vanta.core.screen.packs;

import dev.vanta.core.bridge.PackInfo;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.TextureRef;
import dev.vanta.core.ui.Theme;
import java.util.Locale;

/**
 * Pack icons. A pack with a {@code pack.png} is drawn from the texture the client registers under
 * {@link #texture(PackInfo)} ({@code vanta:pack_icons/<sanitised id>}); packs without one get a generated
 * monogram tile (first letter of the title on a colour derived from the id) so lists stay scannable.
 */
public final class PackIcons {
    private PackIcons() {
    }

    /**
     * Texture identifier under which the client registers the pack's {@code pack.png} as a dynamic texture. The
     * id is sanitised to the characters Minecraft allows in identifier paths.
     */
    public static TextureRef texture(PackInfo pack) {
        return new TextureRef("vanta", "pack_icons/" + sanitize(pack.id()));
    }

    /** Lower-cases and replaces every character outside {@code [a-z0-9/._-]} with an underscore. */
    public static String sanitize(String id) {
        StringBuilder sb = new StringBuilder(id.length());
        for (char c : id.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-';
            sb.append(ok ? c : '_');
        }
        return sb.isEmpty() ? "pack" : sb.toString();
    }

    /** Draws the icon tile for a pack. */
    public static void draw(Canvas canvas, Theme theme, PackInfo pack, Rect tile) {
        if (pack.hasIcon()) {
            canvas.fillRounded(tile.x(), tile.y(), tile.w(), tile.h(), Theme.RADIUS_MD, theme.surface3());
            canvas.image(texture(pack), tile.x() + 1, tile.y() + 1, tile.w() - 2, tile.h() - 2);
            canvas.strokeRounded(tile.x(), tile.y(), tile.w(), tile.h(), Theme.RADIUS_MD, theme.borderStrong());
            return;
        }
        int accent = monogramColor(theme, pack.id());
        canvas.fillRounded(tile.x(), tile.y(), tile.w(), tile.h(), Theme.RADIUS_MD, Colors.withAlpha(accent, 0.22f));
        canvas.strokeRounded(tile.x(), tile.y(), tile.w(), tile.h(), Theme.RADIUS_MD, Colors.withAlpha(accent, 0.55f));
        String letter = monogram(pack.title());
        FontKind font = tile.h() >= 20 ? FontKind.DISPLAY : FontKind.UI_BOLD;
        canvas.textCentered(letter, tile.centerX(), tile.y() + (tile.h() - canvas.lineHeight(font)) / 2, accent, font,
                false);
    }

    /** First letter of the title (upper case), or {@code #} for titles without letters. */
    public static String monogram(String title) {
        if (title != null) {
            for (int i = 0; i < title.length(); i++) {
                char c = title.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    return String.valueOf(Character.toUpperCase(c));
                }
            }
        }
        return "#";
    }

    /** One of the theme's accent-ish colours, chosen deterministically from the id. */
    public static int monogramColor(Theme theme, String id) {
        int[] palette = {theme.accentHover(), theme.accentBlue(), theme.success(), theme.warning(), theme.info()};
        int index = Math.floorMod(id == null ? 0 : id.hashCode(), palette.length);
        return palette[index];
    }
}
