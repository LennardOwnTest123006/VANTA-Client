package dev.vanta.core.ui;

import dev.vanta.core.VantaVersion;
import dev.vanta.core.ui.widget.IconButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The standard chrome shared by every VANTA screen: a top bar (back button, VANTA mark, title, right-side slot),
 * an optional left category rail, the content area and a footer with the version label.
 * <pre>
 * ┌──────────────────────────────────────────────┐
 * │ ‹  V  Title                       [right slot]│  top bar
 * ├──────────┬───────────────────────────────────┤
 * │ rail     │ content                            │
 * ├──────────┴───────────────────────────────────┤
 * │ VANTA Client 1.0.0 · Minecraft 1.21.11 · …   │  footer
 * └──────────────────────────────────────────────┘
 * </pre>
 */
public class VantaShell extends UiNode {

    /** Top bar height. */
    public static final int TOP_BAR_H = 30;
    /** Footer height. */
    public static final int FOOTER_H = 16;
    /** Rail width when a rail is present. */
    public static final int RAIL_W = 104;
    /** Rail width on narrow screens (icons only). */
    public static final int RAIL_COMPACT_W = 32;
    /** Screens narrower than this collapse the rail to icons. */
    public static final int COMPACT_BREAKPOINT = 520;
    /** Padding around the content area. */
    public static final int CONTENT_PAD = Theme.SPACE_5;
    private static final int MARK = 14;
    private static final int RAIL_ITEM_H = 20;

    /**
     * One rail entry.
     *
     * @param id    stable identifier passed to the selection callback
     * @param icon  icon shown left of the label
     * @param label translated label
     */
    public record RailItem(String id, Icons icon, String label) {
    }

    private final IconButton back;
    private String title;
    private UiNode rightSlot;
    private UiNode content;
    private final List<RailItem> rail = new ArrayList<>();
    private final List<RailButton> railButtons = new ArrayList<>();
    private String selectedRail;
    private Consumer<String> onRailSelect;
    private boolean showFooter = true;
    private String footerText;

    /** Shell with a title and a back action. */
    public VantaShell(String title, Runnable onBack) {
        this.title = title == null ? "" : title;
        this.back = new IconButton(Icons.CHEVRON_LEFT, onBack).sizes(20, 10);
        this.back.setTooltip("Back");
        add(back);
        String[] lines = VantaVersion.menuLabel();
        this.footerText = String.join("  ·  ", lines);
    }

    public VantaShell title(String t) {
        this.title = t == null ? "" : t;
        return this;
    }

    public String title() {
        return title;
    }

    /** Tooltip of the back button (translated by the caller). */
    public VantaShell backTooltip(String tooltip) {
        back.setTooltip(tooltip);
        return this;
    }

    /** The back button (hide it on the root screen). */
    public IconButton backButton() {
        return back;
    }

    /** Node shown at the right end of the top bar (search field, actions). */
    public VantaShell rightSlot(UiNode node) {
        if (rightSlot != null) {
            remove(rightSlot);
        }
        rightSlot = node;
        if (node != null) {
            add(node);
        }
        return this;
    }

    /** The main content node. */
    public VantaShell content(UiNode node) {
        if (content != null) {
            remove(content);
        }
        content = Objects.requireNonNull(node, "content");
        add(node);
        return this;
    }

    public UiNode content() {
        return content;
    }

    /** Configures the left rail; an empty list removes it. */
    public VantaShell rail(List<RailItem> items, String selectedId, Consumer<String> onSelect) {
        for (RailButton b : railButtons) {
            remove(b);
        }
        railButtons.clear();
        rail.clear();
        rail.addAll(items);
        this.selectedRail = selectedId;
        this.onRailSelect = onSelect;
        for (RailItem item : rail) {
            RailButton b = new RailButton(item);
            railButtons.add(b);
            add(b);
        }
        return this;
    }

    /** Currently selected rail id, or {@code null}. */
    public String selectedRail() {
        return selectedRail;
    }

    /** Changes the rail selection and fires the callback when it changed. */
    public void selectRail(UiContext ctx, String id) {
        if (Objects.equals(id, selectedRail)) {
            return;
        }
        selectedRail = id;
        ctx.playClick();
        if (onRailSelect != null) {
            onRailSelect.accept(id);
        }
    }

    public boolean hasRail() {
        return !rail.isEmpty();
    }

    /** Whether the rail is collapsed to icons (narrow screens). */
    public boolean isCompactRail() {
        return hasRail() && bounds().w() < COMPACT_BREAKPOINT;
    }

    /** Current rail width (0 without a rail). */
    public int railWidth() {
        if (!hasRail()) {
            return 0;
        }
        return isCompactRail() ? RAIL_COMPACT_W : RAIL_W;
    }

    /** Toggles the version footer. */
    public VantaShell footer(boolean show) {
        this.showFooter = show;
        return this;
    }

    /** Overrides the footer text (defaults to the three version lines joined). */
    public VantaShell footerText(String text) {
        this.footerText = text == null ? "" : text;
        return this;
    }

    public String footerText() {
        return footerText;
    }

    /** Top bar rectangle. */
    public Rect topBarRect() {
        return bounds().top(TOP_BAR_H);
    }

    /** Footer rectangle (empty when hidden). */
    public Rect footerRect() {
        return showFooter ? bounds().bottomStrip(FOOTER_H) : Rect.EMPTY;
    }

    /** Rail rectangle (empty without a rail). */
    public Rect railRect() {
        if (!hasRail()) {
            return Rect.EMPTY;
        }
        Rect b = bounds();
        int top = b.y() + TOP_BAR_H;
        int bottom = b.bottom() - (showFooter ? FOOTER_H : 0);
        return new Rect(b.x(), top, railWidth(), Math.max(0, bottom - top));
    }

    /** Content rectangle (inside the paddings). */
    public Rect contentRect() {
        Rect b = bounds();
        int left = b.x() + railWidth();
        int top = b.y() + TOP_BAR_H;
        int bottom = b.bottom() - (showFooter ? FOOTER_H : 0);
        return new Rect(left, top, Math.max(0, b.right() - left), Math.max(0, bottom - top)).inset(CONTENT_PAD);
    }

    @Override
    public void layout(UiContext ctx) {
        Rect top = topBarRect();
        int x = top.x() + Theme.SPACE_4;
        back.setBounds(x, top.y() + (TOP_BAR_H - 20) / 2, 20, 20);
        back.layout(ctx);
        if (rightSlot != null) {
            Size s = rightSlot.preferredSize(ctx);
            int w = Math.min(s.w(), top.w() / 2);
            rightSlot.setBounds(top.right() - Theme.SPACE_5 - w, top.y() + (TOP_BAR_H - s.h()) / 2, w, s.h());
            rightSlot.layout(ctx);
        }
        Rect railArea = railRect();
        int ry = railArea.y() + Theme.SPACE_4;
        boolean compact = isCompactRail();
        int railInset = compact ? Theme.SPACE_2 : Theme.SPACE_4;
        for (RailButton b : railButtons) {
            b.setBounds(railArea.x() + railInset, ry, railArea.w() - railInset * 2, RAIL_ITEM_H);
            b.setTooltip(compact ? b.item.label() : null);
            b.layout(ctx);
            ry += RAIL_ITEM_H + Theme.SPACE_1;
        }
        if (content != null) {
            content.setBounds(contentRect());
            content.layout(ctx);
        }
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect top = topBarRect();
        // Top bar surface and hairline.
        canvas.fill(top.x(), top.y(), top.w(), top.h(), Colors.withAlpha(theme.surface1(), theme.panelAlpha()));
        canvas.fill(top.x(), top.bottom() - 1, top.w(), 1, theme.borderSubtle());
        // Accent hairline at the very top.
        canvas.fillGradientH(top.x(), top.y(), top.w(), 1, Colors.withAlpha(theme.gradientStart(), 0.9f),
                Colors.withAlpha(theme.gradientEnd(), 0.9f));
        int markX = back.isVisible() ? back.bounds().right() + Theme.SPACE_3 : top.x() + Theme.SPACE_5;
        VantaMark.draw(canvas, markX, top.y() + (TOP_BAR_H - MARK) / 2, MARK, theme);
        int titleX = markX + MARK + Theme.SPACE_3;
        int titleW = (rightSlot != null ? rightSlot.bounds().x() - Theme.SPACE_4 : top.right() - Theme.SPACE_5) - titleX;
        canvas.text(canvas.textClipped(title, Math.max(0, titleW), FontKind.DISPLAY), titleX,
                top.y() + (TOP_BAR_H - canvas.lineHeight(FontKind.DISPLAY)) / 2, theme.textPrimary(), FontKind.DISPLAY,
                false);
        if (hasRail()) {
            Rect rail = railRect();
            canvas.fill(rail.x(), rail.y(), rail.w(), rail.h(), Colors.withAlpha(theme.surface1(), theme.panelAlpha() * 0.7f));
            canvas.fill(rail.right() - 1, rail.y(), 1, rail.h(), theme.borderSubtle());
        }
        if (showFooter) {
            Rect footer = footerRect();
            canvas.fill(footer.x(), footer.y(), footer.w(), 1, theme.borderSubtle());
            canvas.text(canvas.textClipped(footerText, footer.w() - Theme.SPACE_5 * 2, FontKind.UI),
                    footer.x() + Theme.SPACE_5, footer.y() + (FOOTER_H - canvas.lineHeight(FontKind.UI)) / 2 + 1,
                    theme.textMuted(), FontKind.UI, false);
        }
    }

    /** One rail entry button. */
    private final class RailButton extends UiNode {
        private final RailItem item;
        private AnimatedValue hoverT;

        RailButton(RailItem item) {
            this.item = item;
            setFocusable(true);
            setId("rail." + item.id());
        }

        private boolean isSelected() {
            return item.id().equals(selectedRail);
        }

        @Override
        protected Size measure(UiContext ctx) {
            return new Size(railWidth() - Theme.SPACE_4 * 2, RAIL_ITEM_H);
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            if (hoverT == null) {
                hoverT = ctx.animator().value(0f);
            }
            hoverT.animateTo(isHovered(), Theme.MOTION_FAST);
            Theme theme = ctx.theme();
            Rect r = bounds();
            boolean sel = isSelected();
            float hover = hoverT.get();
            if (sel) {
                canvas.fillRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.accent(), 0.16f));
                canvas.fillGradientV(r.x(), r.y() + 4, 2, r.h() - 8, theme.gradientStart(), theme.gradientEnd());
            } else if (hover > 0f) {
                canvas.fillRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.surface3(), hover));
            }
            int color = sel ? theme.textPrimary() : Colors.lerp(theme.textSecondary(), theme.textPrimary(), hover);
            int iconSize = 9;
            if (isCompactRail()) {
                item.icon().draw(canvas, r.x() + (r.w() - iconSize) / 2, r.y() + (r.h() - iconSize) / 2, iconSize,
                        sel ? theme.accentHover() : color);
            } else {
                int ix = r.x() + Theme.SPACE_4;
                item.icon().draw(canvas, ix, r.y() + (r.h() - iconSize) / 2, iconSize, sel ? theme.accentHover() : color);
                int tx = ix + iconSize + Theme.SPACE_3;
                FontKind f = sel ? FontKind.UI_BOLD : FontKind.UI;
                canvas.text(canvas.textClipped(item.label(), r.right() - tx - Theme.SPACE_2, f), tx,
                        r.y() + (r.h() - canvas.lineHeight(f)) / 2, color, f, false);
            }
            if (isFocused()) {
                canvas.strokeRounded(r.x(), r.y(), r.w(), r.h(), Theme.RADIUS_MD, Colors.withAlpha(theme.borderFocus(), 0.9f));
            }
        }

        @Override
        protected boolean onMouseDown(UiContext ctx, double x, double y, int button) {
            if (button != Keys.MOUSE_LEFT) {
                return false;
            }
            requestFocus(ctx);
            selectRail(ctx, item.id());
            return true;
        }

        @Override
        public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
            if (Keys.isActivate(key)) {
                selectRail(ctx, item.id());
                return true;
            }
            return false;
        }
    }
}
