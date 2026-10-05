package dev.vanta.core.screen.mods;

import dev.vanta.core.modrinth.PerformancePack;
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
import java.util.function.Consumer;

/**
 * One member of the Performance pack card: a check box (ticked = install it), the name and its state ("Not installed",
 * "Installed", "Active"). Installed members cannot be ticked.
 */
public final class PackItemToggle extends UiNode {
    /** Height. */
    public static final int HEIGHT = 22;
    private static final int BOX = 10;

    private final PerformancePack.Item item;
    private final Consumer<PackItemToggle> onChange;
    private boolean checked;
    private boolean installed;
    private String state = "";
    private AnimatedValue hoverT;

    public PackItemToggle(PerformancePack.Item item, boolean checked, Consumer<PackItemToggle> onChange) {
        this.item = item;
        this.checked = checked;
        this.onChange = onChange;
        setFocusable(true);
        setId("mods.pack." + item.slug());
    }

    public PerformancePack.Item item() {
        return item;
    }

    /** Ticked and not installed yet. */
    public boolean isChecked() {
        return checked && !installed;
    }

    /** Raw tick state. */
    public boolean checkedFlag() {
        return checked;
    }

    public PackItemToggle setChecked(boolean value) {
        this.checked = value;
        return this;
    }

    public boolean isInstalled() {
        return installed;
    }

    /** Updates the installed flag and the state text. */
    public PackItemToggle state(boolean isInstalled, String stateText) {
        this.installed = isInstalled;
        this.state = stateText == null ? "" : stateText;
        return this;
    }

    public String stateText() {
        return state;
    }

    /** Flips the tick (ignored for installed members). */
    public void toggle(UiContext ctx) {
        if (installed) {
            return;
        }
        checked = !checked;
        if (ctx != null) {
            ctx.playClick();
        }
        if (onChange != null) {
            onChange.accept(this);
        }
    }

    @Override
    protected Size measure(UiContext ctx) {
        int w = BOX + Theme.SPACE_3 + ctx.textWidth(item.name(), FontKind.UI_BOLD) + Theme.SPACE_3
                + ctx.textWidth(state, FontKind.UI) + Theme.SPACE_4;
        return new Size(w, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (hoverT == null) {
            hoverT = ctx.animator().value(0f);
        }
        Theme theme = ctx.theme();
        Rect b = bounds();
        hoverT.animateTo(isHovered() && !installed, Theme.MOTION_FAST);
        float hover = hoverT.get();
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD,
                Colors.withAlpha(Colors.lerp(theme.surface2(), theme.surface3(), hover), 0.9f));
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD,
                isChecked() ? Colors.withAlpha(theme.accentHover(), 0.6f) : theme.borderSubtle());
        int bx = b.x() + Theme.SPACE_3;
        int by = b.y() + (b.h() - BOX) / 2;
        if (installed) {
            canvas.fillRounded(bx, by, BOX, BOX, Theme.RADIUS_SM, Colors.withAlpha(theme.success(), 0.25f));
            Icons.CHECK.draw(canvas, bx + 1, by + 1, BOX - 2, theme.success());
        } else if (checked) {
            canvas.fillRoundedGradientH(bx, by, BOX, BOX, Theme.RADIUS_SM, theme.gradientStart(), theme.gradientEnd());
            Icons.CHECK.draw(canvas, bx + 1, by + 1, BOX - 2, Colors.WHITE);
        } else {
            canvas.strokeRounded(bx, by, BOX, BOX, Theme.RADIUS_SM, theme.borderStrong());
        }
        int tx = bx + BOX + Theme.SPACE_3;
        int lhBold = canvas.lineHeight(FontKind.UI_BOLD);
        int lh = canvas.lineHeight(FontKind.UI);
        int stateW = canvas.textWidth(state, FontKind.UI);
        int nameMax = Math.max(0, b.right() - Theme.SPACE_3 - stateW - Theme.SPACE_3 - tx);
        String name = canvas.textClipped(item.name(), nameMax, FontKind.UI_BOLD);
        canvas.text(name, tx, b.y() + (b.h() - lhBold) / 2, theme.textPrimary(), FontKind.UI_BOLD, false);
        canvas.textRight(state, b.right() - Theme.SPACE_3, b.y() + (b.h() - lh) / 2,
                installed ? theme.success() : theme.textMuted(), FontKind.UI, false);
        if (isFocused()) {
            canvas.strokeRounded(b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2, Theme.RADIUS_MD,
                    Colors.withAlpha(theme.borderFocus(), 0.9f));
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT) {
            return false;
        }
        requestFocus(ctx);
        toggle(ctx);
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (Keys.isActivate(key)) {
            toggle(ctx);
            return true;
        }
        return false;
    }
}
