package dev.vanta.core.screen.packs;

import dev.vanta.core.bridge.PackInfo;
import dev.vanta.core.i18n.Lang;
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
import dev.vanta.core.ui.widget.IconButton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * One pack in either column: icon tile, title with badges (Required, Incompatible, source), description and the
 * action buttons for its column (Enable on the left; Move up / Move down / Disable on the right). Selected-column
 * rows can also be dragged to reorder and moved with Shift+↑ / Shift+↓; Enter toggles the pack.
 */
public final class PackRow extends UiNode {
    /** Row height. */
    public static final int HEIGHT = 36;
    private static final int TILE = 24;
    private static final int BUTTON = 16;
    private static final int BADGE_H = 10;
    private static final int BADGE_PAD = 3;

    private final PackInfo pack;
    private final boolean inSelected;
    private final Consumer<PackInfo> onToggle;
    private final IntConsumer onMove;
    private final List<IconButton> buttons = new ArrayList<>();
    private IconButton up;
    private IconButton down;
    private boolean last;
    private boolean dragging;
    private double dragAccumulator;
    private AnimatedValue hoverT;

    /**
     * @param pack       the pack
     * @param inSelected true for the right (enabled) column
     * @param canMoveUp  whether "move up" is possible (selected column only)
     * @param canMoveDown whether "move down" is possible
     * @param onToggle   enables or disables the pack
     * @param onMove     moves the pack by a delta (+1 = higher priority)
     */
    public PackRow(PackInfo pack, boolean inSelected, boolean canMoveUp, boolean canMoveDown, Consumer<PackInfo> onToggle,
                   IntConsumer onMove) {
        this.pack = pack;
        this.inSelected = inSelected;
        this.onToggle = onToggle;
        this.onMove = onMove;
        setFocusable(true);
        setId("pack." + pack.id());
        setTooltip(pack.compatible() ? null : Lang.tr("vanta.resourcepacks.incompatible", pack.compatibilityText()));
        if (inSelected) {
            up = button(Icons.CHEVRON_UP, "vanta.resourcepacks.move_up", () -> onMove.accept(1));
            up.setEnabled(canMoveUp);
            down = button(Icons.CHEVRON_DOWN, "vanta.resourcepacks.move_down", () -> onMove.accept(-1));
            down.setEnabled(canMoveDown);
            if (!pack.required()) {
                button(Icons.CLOSE, "vanta.resourcepacks.disable", () -> onToggle.accept(pack));
            }
        } else {
            IconButton enable = button(Icons.PLUS, "vanta.resourcepacks.enable", () -> onToggle.accept(pack));
            enable.setEnabled(pack.compatible() || true);
        }
    }

    private IconButton button(Icons icon, String tooltipKey, Runnable action) {
        IconButton b = new IconButton(icon, action).sizes(BUTTON, 8);
        b.setTooltip(Lang.tr(tooltipKey));
        b.setId(id() + "." + icon.name().toLowerCase(java.util.Locale.ROOT));
        buttons.add(b);
        add(b);
        return b;
    }

    /** The pack. */
    public PackInfo pack() {
        return pack;
    }

    /** Hides the separator (last row). */
    public PackRow last(boolean isLast) {
        this.last = isLast;
        return this;
    }

    /** Action buttons in order. */
    public List<IconButton> buttons() {
        return List.copyOf(buttons);
    }

    /** Whether a reorder drag is in progress. */
    public boolean isDragging() {
        return dragging;
    }

    private boolean movable() {
        return inSelected && onMove != null && ((up != null && up.isEnabled()) || (down != null && down.isEnabled()));
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(200, HEIGHT);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        int x = b.right() - Theme.SPACE_3;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            IconButton button = buttons.get(i);
            x -= BUTTON;
            button.setBounds(x, b.centerY() - BUTTON / 2, BUTTON, BUTTON);
            button.layout(ctx);
            x -= Theme.SPACE_1;
        }
    }

    private List<Badge> badges() {
        List<Badge> out = new ArrayList<>(3);
        if (pack.required()) {
            out.add(new Badge(Lang.tr("vanta.resourcepacks.required"), Kind.NEUTRAL));
        }
        if (!pack.compatible()) {
            out.add(new Badge(Lang.tr("vanta.resourcepacks.incompatible_badge"), Kind.DANGER));
        }
        if (!"built-in".equals(pack.source())) {
            out.add(new Badge(Lang.tr("vanta.resourcepacks.source." + pack.source()), Kind.NEUTRAL));
        }
        return out;
    }

    private enum Kind { NEUTRAL, DANGER }

    private record Badge(String text, Kind kind) {
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (hoverT == null) {
            hoverT = ctx.animator().value(0f);
        }
        Theme theme = ctx.theme();
        Rect b = bounds();
        hoverT.animateTo(isHovered() || dragging, Theme.MOTION_FAST);
        float hover = hoverT.get();
        if (dragging) {
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.accent(), 0.16f));
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.accentHover(), 0.7f));
        } else if (hover > 0f) {
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.surface3(), 0.7f * hover));
        }
        int tileX = b.x() + Theme.SPACE_3;
        Rect tile = new Rect(tileX, b.y() + (b.h() - TILE) / 2, TILE, TILE);
        PackIcons.draw(canvas, theme, pack, tile);
        int textX = tile.right() + Theme.SPACE_4;
        int buttonsW = buttons.size() * (BUTTON + Theme.SPACE_1) + Theme.SPACE_3;
        int textRight = b.right() - buttonsW - Theme.SPACE_2;
        int textW = Math.max(0, textRight - textX);
        int lhBold = canvas.lineHeight(FontKind.UI_BOLD);
        int lh = canvas.lineHeight(FontKind.UI);
        int titleY = b.y() + (b.h() - lhBold - lh - 1) / 2;
        List<Badge> badges = badges();
        int badgesW = 0;
        for (Badge badge : badges) {
            badgesW += canvas.textWidth(badge.text(), FontKind.UI) + BADGE_PAD * 2 + Theme.SPACE_2;
        }
        String title = canvas.textClipped(pack.title(), Math.max(0, textW - badgesW), FontKind.UI_BOLD);
        int titleColor = pack.compatible() ? theme.textPrimary() : theme.textSecondary();
        canvas.text(title, textX, titleY, titleColor, FontKind.UI_BOLD, false);
        int bx = textX + canvas.textWidth(title, FontKind.UI_BOLD) + Theme.SPACE_2;
        for (Badge badge : badges) {
            int bw = canvas.textWidth(badge.text(), FontKind.UI) + BADGE_PAD * 2;
            if (bx + bw > textRight) {
                break;
            }
            int by = titleY + (lhBold - BADGE_H) / 2;
            boolean danger = badge.kind() == Kind.DANGER;
            canvas.fillRounded(bx, by, bw, BADGE_H, Theme.RADIUS_SM, danger ? Colors.withAlpha(theme.danger(), 0.16f)
                    : theme.surface3());
            canvas.strokeRounded(bx, by, bw, BADGE_H, Theme.RADIUS_SM, danger ? Colors.withAlpha(theme.danger(), 0.5f)
                    : theme.borderStrong());
            canvas.text(badge.text(), bx + BADGE_PAD, by + (BADGE_H - lh) / 2, danger ? theme.danger() : theme.textMuted(),
                    FontKind.UI, false);
            bx += bw + Theme.SPACE_2;
        }
        canvas.text(canvas.textClipped(pack.description(), textW, FontKind.UI), textX, titleY + lhBold + 1,
                theme.textMuted(), FontKind.UI, false);
        if (movable()) {
            Icons.DRAG.draw(canvas, b.x() - 1, b.centerY() - 5, 10, Colors.withAlpha(theme.textMuted(), 0.35f + 0.45f * hover));
        }
        if (!last) {
            canvas.fill(b.x() + Theme.SPACE_3, b.bottom() - 1, b.w() - Theme.SPACE_3 * 2, 1,
                    Colors.withAlpha(theme.borderSubtle(), 0.7f));
        }
        if (isFocused()) {
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.borderFocus(), 0.9f));
        }
    }

    private void toggle(UiContext ctx) {
        if (pack.required() || onToggle == null) {
            return;
        }
        ctx.playClick();
        onToggle.accept(pack);
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT) {
            return false;
        }
        requestFocus(ctx);
        if (movable()) {
            dragging = true;
            dragAccumulator = 0;
            ctx.captureMouse(this);
        }
        return true;
    }

    @Override
    protected boolean onMouseDrag(UiContext ctx, double x, double y, int button, double dx, double dy) {
        if (!dragging) {
            return false;
        }
        dragAccumulator += dy;
        while (dragAccumulator <= -HEIGHT * 0.6 && up != null && up.isEnabled()) {
            dragAccumulator += HEIGHT;
            onMove.accept(1);
        }
        while (dragAccumulator >= HEIGHT * 0.6 && down != null && down.isEnabled()) {
            dragAccumulator -= HEIGHT;
            onMove.accept(-1);
        }
        return true;
    }

    @Override
    protected boolean onMouseUp(UiContext ctx, double x, double y, int button) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        ctx.releaseMouse();
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (Keys.isActivate(key)) {
            toggle(ctx);
            return true;
        }
        if (inSelected && Keys.hasShift(mods) && onMove != null) {
            if (key == Keys.UP && up != null && up.isEnabled()) {
                onMove.accept(1);
                return true;
            }
            if (key == Keys.DOWN && down != null && down.isEnabled()) {
                onMove.accept(-1);
                return true;
            }
        }
        return false;
    }
}
