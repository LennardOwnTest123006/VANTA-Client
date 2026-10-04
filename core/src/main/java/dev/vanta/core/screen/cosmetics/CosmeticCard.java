package dev.vanta.core.screen.cosmetics;

import dev.vanta.core.ui.AnimatedValue;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import java.util.Objects;

/**
 * Selectable card of one cosmetic: a thumbnail node on top, title and caption below, an accent frame and a check
 * mark when selected. Clicking (or Enter / Space while focused) selects it; selecting the selected card does
 * nothing. The thumbnail is any node; {@link Painted} wraps a static painter.
 */
public class CosmeticCard extends UiNode {

    /** Paints a thumbnail into a rectangle. */
    @FunctionalInterface
    public interface Painter {
        void paint(Canvas canvas, UiContext ctx, Rect area);
    }

    /** Thumbnail node drawing with a {@link Painter}. */
    public static final class Painted extends UiNode {
        private final Painter painter;

        public Painted(Painter painter) {
            this.painter = Objects.requireNonNull(painter, "painter");
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            painter.paint(canvas, ctx, bounds());
        }
    }

    /** Card height. */
    public static final int HEIGHT = 94;
    /** Thumbnail height. */
    public static final int THUMB_H = 46;
    private static final int PAD = Theme.SPACE_3;
    private static final int CHECK = 12;

    private final UiNode thumbnail;
    private final String title;
    private final String caption;
    private final String selectedLabel;
    private boolean selected;
    private final Runnable onSelect;
    private AnimatedValue hoverT;

    /**
     * @param thumbnail     node drawn in the top area
     * @param title         translated title
     * @param caption       translated one-line caption (may be empty)
     * @param selectedLabel translated label shown next to the check mark ("Selected", "Equipped")
     * @param selected      whether this is the active option
     * @param onSelect      runs when the user picks this card
     */
    public CosmeticCard(UiNode thumbnail, String title, String caption, String selectedLabel, boolean selected,
                        Runnable onSelect) {
        this.thumbnail = Objects.requireNonNull(thumbnail, "thumbnail");
        this.title = title == null ? "" : title;
        this.caption = caption == null ? "" : caption;
        this.selectedLabel = selectedLabel == null ? "" : selectedLabel;
        this.selected = selected;
        this.onSelect = onSelect;
        setFocusable(true);
        add(thumbnail);
    }

    public String title() {
        return title;
    }

    public boolean isSelected() {
        return selected;
    }

    public UiNode thumbnail() {
        return thumbnail;
    }

    /** Fires the selection (ignored when already selected or disabled). */
    public void select(UiContext ctx) {
        if (selected || !isEffectivelyEnabled()) {
            return;
        }
        ctx.playClick();
        if (onSelect != null) {
            onSelect.run();
        }
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(120, HEIGHT);
    }

    private Rect thumbRect() {
        Rect b = bounds();
        return new Rect(b.x() + PAD, b.y() + PAD, Math.max(0, b.w() - PAD * 2), THUMB_H);
    }

    @Override
    public void layout(UiContext ctx) {
        thumbnail.setBounds(thumbRect());
        thumbnail.layout(ctx);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (hoverT == null) {
            hoverT = ctx.animator().value(0f);
        }
        hoverT.animateTo(isHovered() && !selected, Theme.MOTION_FAST);
        float hover = hoverT.get();
        Theme theme = ctx.theme();
        Rect b = bounds();
        int bg = Colors.lerp(theme.panelBackground(), Colors.withAlpha(theme.surface2(), theme.panelAlpha()), hover);
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, bg);
        int border = selected ? Colors.withAlpha(theme.accent(), 0.85f)
                : Colors.lerp(theme.borderSubtle(), theme.borderStrong(), hover);
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, border);
        if (selected) {
            canvas.fillGradientH(b.x() + Theme.RADIUS_LG, b.y(), b.w() - Theme.RADIUS_LG * 2, 1, theme.gradientStart(),
                    theme.gradientEnd());
        }
        if (isFocused()) {
            Rect ring = b.expand(2);
            canvas.strokeRounded(ring.x(), ring.y(), ring.w(), ring.h(), Canvas.MAX_RADIUS,
                    Colors.withAlpha(theme.borderFocus(), 0.9f));
        }
    }

    @Override
    protected void renderChildren(Canvas canvas, UiContext ctx) {
        super.renderChildren(canvas, ctx);
        Theme theme = ctx.theme();
        Rect b = bounds();
        Rect thumb = thumbRect();
        canvas.strokeRounded(thumb.x(), thumb.y(), thumb.w(), thumb.h(), Theme.RADIUS_MD,
                Colors.withAlpha(theme.borderSubtle(), 0.9f));
        int textX = b.x() + PAD + 1;
        int textW = b.w() - PAD * 2 - 2;
        int y = thumb.bottom() + Theme.SPACE_3;
        int titleW = textW;
        if (selected) {
            int labelW = canvas.textWidth(selectedLabel, FontKind.UI_BOLD);
            int pillW = labelW + CHECK + Theme.SPACE_2 + 6;
            int px = b.right() - PAD - pillW;
            canvas.fillRounded(px, y - 1, pillW, CHECK, Theme.RADIUS_LG, Colors.withAlpha(theme.accent(), 0.22f));
            Icons.CHECK.draw(canvas, px + 3, y - 1 + (CHECK - 8) / 2, 8, theme.accentHover());
            canvas.text(selectedLabel, px + 3 + CHECK, y - 1 + (CHECK - canvas.lineHeight(FontKind.UI_BOLD)) / 2,
                    theme.accentHover(), FontKind.UI_BOLD, false);
            titleW = Math.max(0, px - Theme.SPACE_2 - textX);
        }
        canvas.text(canvas.textClipped(title, titleW, FontKind.UI_BOLD), textX, y, theme.textPrimary(),
                FontKind.UI_BOLD, false);
        y += canvas.lineHeight(FontKind.UI_BOLD) + 1;
        if (!caption.isEmpty()) {
            canvas.text(canvas.textClipped(caption, textW, FontKind.UI), textX, y, theme.textMuted(), FontKind.UI, false);
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT || !isEffectivelyEnabled()) {
            return false;
        }
        requestFocus(ctx);
        select(ctx);
        return true;
    }

    @Override
    public boolean mouseDown(UiContext ctx, double x, double y, int button) {
        // The thumbnail is decorative: clicks anywhere on the card select it.
        if (!isVisible() || !isEnabled()) {
            return false;
        }
        return onMouseDown(ctx, x, y, button);
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (Keys.isActivate(key)) {
            select(ctx);
            return true;
        }
        return false;
    }
}
