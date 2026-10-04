package dev.vanta.core.screen.common;

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

/**
 * Clickable navigation row: leading icon tile, title, one-line description and a trailing chevron (or an
 * external-link glyph for rows that leave VANTA). Activates on click, Enter or Space.
 */
public class LinkRow extends UiNode {
    /** Row height. */
    public static final int HEIGHT = 32;
    private static final int TILE = 20;
    private static final int ICON = 10;
    private static final int TRAILING = 9;

    private final Icons icon;
    private String title;
    private String description;
    private boolean external;
    private Runnable onActivate;
    private AnimatedValue hoverT;

    public LinkRow(Icons icon, String title, String description, Runnable onActivate) {
        this.icon = icon == null ? Icons.CHEVRON_RIGHT : icon;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.onActivate = onActivate;
        setFocusable(true);
    }

    /** Marks the row as opening something outside VANTA (vanilla screen, browser). */
    public LinkRow external(boolean on) {
        this.external = on;
        return this;
    }

    public LinkRow onActivate(Runnable action) {
        this.onActivate = action;
        return this;
    }

    public String title() {
        return title;
    }

    public LinkRow setTitle(String t) {
        this.title = t == null ? "" : t;
        return this;
    }

    public LinkRow setDescription(String d) {
        this.description = d == null ? "" : d;
        return this;
    }

    /** Fires the action. */
    public void activate(UiContext ctx) {
        if (!isEffectivelyEnabled()) {
            return;
        }
        ctx.playClick();
        if (onActivate != null) {
            onActivate.run();
        }
    }

    @Override
    protected Size measure(UiContext ctx) {
        return new Size(200, HEIGHT);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        if (hoverT == null) {
            hoverT = ctx.animator().value(0f);
        }
        Theme theme = ctx.theme();
        Rect b = bounds();
        boolean enabled = isEffectivelyEnabled();
        hoverT.animateTo(enabled && isHovered(), Theme.MOTION_FAST);
        float hover = hoverT.get();
        if (hover > 0f) {
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.surface3(), hover));
        }
        int tileX = b.x() + Theme.SPACE_2;
        int tileY = b.y() + (b.h() - TILE) / 2;
        canvas.fillRounded(tileX, tileY, TILE, TILE, Theme.RADIUS_MD, theme.surface2());
        canvas.strokeRounded(tileX, tileY, TILE, TILE, Theme.RADIUS_MD, theme.borderSubtle());
        icon.draw(canvas, tileX + (TILE - ICON) / 2, tileY + (TILE - ICON) / 2, ICON,
                Colors.lerp(theme.accentHover(), theme.textPrimary(), hover * 0.4f));
        int textX = tileX + TILE + Theme.SPACE_4;
        int trailingX = b.right() - Theme.SPACE_3 - TRAILING;
        int textW = Math.max(0, trailingX - Theme.SPACE_4 - textX);
        int lhBold = canvas.lineHeight(FontKind.UI_BOLD);
        int lh = canvas.lineHeight(FontKind.UI);
        boolean twoLines = !description.isEmpty() && b.h() >= lhBold + lh + Theme.SPACE_2;
        int titleY = twoLines ? b.y() + (b.h() - lhBold - lh - 1) / 2 : b.y() + (b.h() - lhBold) / 2;
        int titleColor = enabled ? theme.textPrimary() : theme.textMuted();
        canvas.text(canvas.textClipped(title, textW, FontKind.UI_BOLD), textX, titleY, titleColor, FontKind.UI_BOLD,
                false);
        if (twoLines) {
            canvas.text(canvas.textClipped(description, textW, FontKind.UI), textX, titleY + lhBold + 1,
                    theme.textMuted(), FontKind.UI, false);
        }
        Icons trailing = external ? Icons.EXTERNAL_LINK : Icons.CHEVRON_RIGHT;
        trailing.draw(canvas, trailingX, b.y() + (b.h() - TRAILING) / 2, TRAILING,
                Colors.lerp(theme.textMuted(), theme.textPrimary(), hover));
        if (isFocused()) {
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.borderFocus(), 0.9f));
        }
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT || !isEffectivelyEnabled()) {
            return false;
        }
        requestFocus(ctx);
        activate(ctx);
        return true;
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (Keys.isActivate(key)) {
            activate(ctx);
            return true;
        }
        return false;
    }
}
