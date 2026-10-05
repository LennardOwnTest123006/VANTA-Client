package dev.vanta.core.screen.mods;

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
import dev.vanta.core.ui.widget.Badge;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * One project in a result or installed list: a letter avatar, the title with the author, a one-line description,
 * a trailing statistic (downloads or version) and a state badge. Click, Enter or Space selects it.
 */
public final class ProjectRow extends UiNode {
    /** Row height. */
    public static final int HEIGHT = 36;
    private static final int AVATAR = 24;
    private static final int BADGE_H = 10;
    private static final int BADGE_PAD = 3;

    /**
     * What a row shows.
     *
     * @param key          stable id (project id or file)
     * @param title        bold title
     * @param byline       muted text after the title ("by jellysquid3", "mods/x.jar")
     * @param description  second line
     * @param trailing     right-aligned statistic
     * @param trailingIcon icon before the statistic (may be {@code null})
     * @param badge        badge text (empty for none)
     * @param badgeTone    badge colour
     * @param avatarLetter letter in the avatar
     * @param avatarColor  avatar fill colour
     * @param dimmed       draw the title muted (disabled or missing items)
     */
    public record Model(String key, String title, String byline, String description, String trailing,
                        Icons trailingIcon, String badge, Badge.Tone badgeTone, String avatarLetter, int avatarColor,
                        boolean dimmed) {
        public Model {
            Objects.requireNonNull(key, "key");
            title = title == null ? "" : title;
            byline = byline == null ? "" : byline;
            description = description == null ? "" : description;
            trailing = trailing == null ? "" : trailing;
            badge = badge == null ? "" : badge;
            badgeTone = badgeTone == null ? Badge.Tone.NEUTRAL : badgeTone;
            avatarLetter = avatarLetter == null ? "?" : avatarLetter;
        }
    }

    private final Model model;
    private final Consumer<ProjectRow> onSelect;
    private boolean selected;
    private boolean last;
    private AnimatedValue hoverT;

    public ProjectRow(Model model, Consumer<ProjectRow> onSelect) {
        this.model = Objects.requireNonNull(model, "model");
        this.onSelect = onSelect;
        setFocusable(true);
        setId("mods.row." + model.key());
    }

    public Model model() {
        return model;
    }

    public boolean isSelected() {
        return selected;
    }

    public ProjectRow selected(boolean value) {
        this.selected = value;
        return this;
    }

    /** Hides the separator under the last row. */
    public ProjectRow last(boolean value) {
        this.last = value;
        return this;
    }

    /** Selects the row (as a click would). */
    public void select(UiContext ctx) {
        if (ctx != null) {
            ctx.playClick();
        }
        if (onSelect != null) {
            onSelect.accept(this);
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
        hoverT.animateTo(isHovered(), Theme.MOTION_FAST);
        float hover = hoverT.get();
        if (selected) {
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.accent(), 0.16f));
            canvas.fillGradientV(b.x(), b.y() + 5, 2, b.h() - 10, theme.gradientStart(), theme.gradientEnd());
        } else if (hover > 0f) {
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.surface3(), 0.7f * hover));
        }
        int ax = b.x() + Theme.SPACE_3;
        int ay = b.y() + (b.h() - AVATAR) / 2;
        drawAvatar(canvas, ax, ay, AVATAR, model.avatarLetter(), model.avatarColor(), model.dimmed());

        int lhBold = canvas.lineHeight(FontKind.UI_BOLD);
        int lh = canvas.lineHeight(FontKind.UI);
        int right = b.right() - Theme.SPACE_3;
        int trailingW = 0;
        if (!model.trailing().isEmpty()) {
            trailingW = canvas.textWidth(model.trailing(), FontKind.UI) + (model.trailingIcon() != null ? 10 : 0);
        }
        int badgeW = model.badge().isEmpty() ? 0 : canvas.textWidth(model.badge(), FontKind.UI) + BADGE_PAD * 2;
        int textX = ax + AVATAR + Theme.SPACE_4;
        int titleY = b.y() + (b.h() - lhBold - lh - 1) / 2;
        int line2Y = titleY + lhBold + 1;

        // Right column: statistic on the first line, badge on the second.
        if (trailingW > 0) {
            int tx = right - trailingW;
            if (model.trailingIcon() != null) {
                model.trailingIcon().draw(canvas, tx, titleY + (lhBold - 8) / 2, 8, theme.textMuted());
                tx += 10;
            }
            canvas.text(model.trailing(), tx, titleY + (lhBold - lh) / 2, theme.textSecondary(), FontKind.UI, false);
        }
        if (badgeW > 0) {
            drawBadge(canvas, theme, right - badgeW, line2Y + (lh - BADGE_H) / 2, badgeW, model.badge(),
                    model.badgeTone());
        }
        int titleRight = right - Math.max(trailingW, 0) - Theme.SPACE_4;
        int line2Right = right - Math.max(badgeW, 0) - Theme.SPACE_4;
        int titleW = Math.max(0, titleRight - textX);
        String title = canvas.textClipped(model.title(), titleW, FontKind.UI_BOLD);
        int titleColor = model.dimmed() ? theme.textMuted() : theme.textPrimary();
        canvas.text(title, textX, titleY, titleColor, FontKind.UI_BOLD, false);
        int bylineX = textX + canvas.textWidth(title, FontKind.UI_BOLD) + Theme.SPACE_3;
        if (!model.byline().isEmpty() && bylineX < titleRight) {
            canvas.text(canvas.textClipped(model.byline(), titleRight - bylineX, FontKind.UI), bylineX,
                    titleY + (lhBold - lh) / 2, theme.textMuted(), FontKind.UI, false);
        }
        canvas.text(canvas.textClipped(model.description(), Math.max(0, line2Right - textX), FontKind.UI), textX, line2Y,
                theme.textSecondary(), FontKind.UI, false);
        if (!last) {
            canvas.fill(b.x() + Theme.SPACE_3, b.bottom() - 1, b.w() - Theme.SPACE_3 * 2, 1,
                    Colors.withAlpha(theme.borderSubtle(), 0.7f));
        }
        if (isFocused()) {
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.borderFocus(), 0.9f));
        }
    }

    /** Rounded square with a centred letter (VANTA does not download project icons). */
    static void drawAvatar(Canvas canvas, int x, int y, int size, String letter, int color, boolean dimmed) {
        int fill = dimmed ? Colors.withAlpha(color, 0.45f) : color;
        canvas.fillRoundedGradientH(x, y, size, size, Theme.RADIUS_LG, fill, Colors.darken(fill, 0.25f));
        FontKind font = size >= 30 ? FontKind.DISPLAY : FontKind.UI_BOLD;
        int lh = canvas.lineHeight(font);
        canvas.textCentered(letter, x + size / 2, y + (size - lh) / 2 + 1, Colors.withAlpha(Colors.WHITE, 0.95f), font,
                false);
    }

    /** Small pill used for states (Installed, Disabled, …). */
    static void drawBadge(Canvas canvas, Theme theme, int x, int y, int w, String text, Badge.Tone tone) {
        int accent = switch (tone) {
            case SUCCESS -> theme.success();
            case WARNING -> theme.warning();
            case DANGER -> theme.danger();
            case INFO -> theme.info();
            case ACCENT -> theme.accentHover();
            case NEUTRAL -> theme.textMuted();
        };
        boolean neutral = tone == Badge.Tone.NEUTRAL;
        canvas.fillRounded(x, y, w, BADGE_H, Theme.RADIUS_SM, neutral ? theme.surface3() : Colors.withAlpha(accent, 0.16f));
        canvas.strokeRounded(x, y, w, BADGE_H, Theme.RADIUS_SM, neutral ? theme.borderStrong()
                : Colors.withAlpha(accent, 0.5f));
        canvas.text(text, x + BADGE_PAD, y + (BADGE_H - canvas.lineHeight(FontKind.UI)) / 2, accent, FontKind.UI, false);
    }

    @Override
    protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
        if (button != Keys.MOUSE_LEFT) {
            return false;
        }
        requestFocus(ctx);
        select(ctx);
        return true;
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
