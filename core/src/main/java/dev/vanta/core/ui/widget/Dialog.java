package dev.vanta.core.ui.widget;

import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Row;

import java.util.List;

/**
 * Modal dialog shown through the popup layer: title, wrapped message, optional icon and a row of buttons.
 * Use {@link #confirm} / {@link #info}; Escape acts as cancel.
 */
public class Dialog extends UiNode {

    /** Default dialog width. */
    public static final int WIDTH = 240;
    private static final int PAD = 14;
    private static final int ICON = 12;

    private final String title;
    private final String message;
    private final Icons icon;
    private final Integer iconColor;
    private final Row buttons;
    private Runnable onDismiss;
    private List<String> lines = List.of();

    private Dialog(String title, String message, Icons icon, Integer iconColor) {
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
        this.icon = icon;
        this.iconColor = iconColor;
        this.buttons = new Row(Theme.SPACE_3).justify(dev.vanta.core.ui.layout.Justify.END);
        add(buttons);
    }

    /**
     * Opens a confirmation dialog. {@code onConfirm} runs after the dialog closed; cancelling runs nothing.
     *
     * @param danger renders the confirm button in the danger style
     */
    public static Dialog confirm(UiContext ctx, String title, String message, String confirmLabel, String cancelLabel,
                                 boolean danger, Runnable onConfirm) {
        Dialog d = new Dialog(title, message, danger ? Icons.WARNING : Icons.INFO,
                danger ? ctx.theme().danger() : ctx.theme().info());
        Button cancel = Button.secondary(cancelLabel, () -> d.close(ctx));
        Button confirm = (danger ? Button.danger(confirmLabel, null) : Button.primary(confirmLabel, null))
                .onClick(() -> {
                    d.close(ctx);
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                });
        if (danger) {
            d.buttons.add(cancel);
            d.buttons.add(confirm);
            d.open(ctx, cancel);
        } else {
            d.buttons.add(cancel);
            d.buttons.add(confirm);
            d.open(ctx, confirm);
        }
        return d;
    }

    /** Opens an informational dialog with one button. */
    public static Dialog info(UiContext ctx, String title, String message, String okLabel) {
        Dialog d = new Dialog(title, message, Icons.INFO, ctx.theme().info());
        Button ok = Button.primary(okLabel, () -> d.close(ctx));
        d.buttons.add(ok);
        d.open(ctx, ok);
        return d;
    }

    /** Runs when the dialog closes for any reason. */
    public Dialog onDismiss(Runnable r) {
        this.onDismiss = r;
        return this;
    }

    private void open(UiContext ctx, UiNode initialFocus) {
        position(ctx);
        ctx.popups().open(ctx, this, true, () -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
        if (initialFocus != null) {
            ctx.focus().focus(ctx, initialFocus);
        }
    }

    /** Closes the dialog. */
    public void close(UiContext ctx) {
        ctx.popups().close(ctx, this);
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    /** The button row. */
    public Row buttonRow() {
        return buttons;
    }

    private int textLeft() {
        return icon != null ? PAD + ICON + Theme.SPACE_3 : PAD;
    }

    private void position(UiContext ctx) {
        int w = Math.min(WIDTH, Math.max(120, ctx.screenWidth() - Theme.SPACE_6 * 2));
        int textW = w - textLeft() - PAD;
        lines = CanvasText.wrap(message, textW, FontKind.UI, ctx.metrics());
        int h = PAD + ctx.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_2
                + lines.size() * ctx.lineHeight(FontKind.UI) + Theme.SPACE_5 + Button.HEIGHT + PAD;
        int x = (ctx.screenWidth() - w) / 2;
        int y = (ctx.screenHeight() - h) / 2;
        setBounds(x, y, w, h);
    }

    @Override
    protected Size measure(UiContext ctx) {
        return bounds().size();
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        buttons.setBounds(b.x() + PAD, b.bottom() - PAD - Button.HEIGHT, b.w() - PAD * 2, Button.HEIGHT);
        buttons.layout(ctx);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fillRounded(b.x() + 2, b.y() + 4, b.w(), b.h(), Theme.RADIUS_LG, Colors.withAlpha(theme.bgVoid(), 0.6f));
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.surface2());
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.borderStrong());
        int y = b.y() + PAD;
        if (icon != null) {
            icon.draw(canvas, b.x() + PAD, y - 1, ICON, iconColor != null ? iconColor : theme.textSecondary());
        }
        int x = b.x() + textLeft();
        canvas.text(canvas.textClipped(title, b.right() - PAD - x, FontKind.UI_BOLD), x, y, theme.textPrimary(),
                FontKind.UI_BOLD, false);
        y += canvas.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_2;
        for (String line : lines) {
            canvas.text(line, x, y, theme.textSecondary(), FontKind.UI, false);
            y += canvas.lineHeight(FontKind.UI);
        }
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (key == Keys.ESCAPE) {
            close(ctx);
            return true;
        }
        return false;
    }
}
