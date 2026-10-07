package dev.vanta.core.screen.profiles;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Justify;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.TextField;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Modal prompt for a profile name (create from current, duplicate): title, hint, a validated text field whose
 * problem is shown underneath, Cancel and a confirm button that stays disabled until the name is acceptable.
 * Enter confirms, Escape cancels.
 */
public final class NameDialog extends UiNode {
    /** Dialog width. */
    public static final int WIDTH = 250;
    private static final int PAD = 14;

    private final String title;
    private final String hint;
    private final Function<String, Optional<String>> validator;
    private final Consumer<String> onConfirm;
    private final TextField field;
    private final Button cancel;
    private final Button confirm;
    private final Row buttons;
    private List<String> hintLines = List.of();
    private String error;
    private UiContext openContext;

    private NameDialog(String title, String hint, String initial, String confirmLabel,
                       Function<String, Optional<String>> validator, Consumer<String> onConfirm) {
        this.title = title;
        this.hint = hint == null ? "" : hint;
        this.validator = validator;
        this.onConfirm = onConfirm;
        this.field = new TextField(initial == null ? "" : initial).maxLength(Profile.MAX_NAME)
                .placeholder(Lang.tr("vanta.profiles.name"));
        field.setId("name-dialog.field");
        field.onChange(text -> validate());
        field.onSubmit(text -> submit());
        add(field);
        buttons = new Row(Theme.SPACE_3).justify(Justify.END);
        cancel = Button.secondary(Lang.tr("vanta.common.cancel"), null);
        confirm = Button.primary(confirmLabel, this::submit);
        confirm.setId("name-dialog.confirm");
        buttons.add(cancel);
        buttons.add(confirm);
        add(buttons);
        validate();
    }

    /**
     * Opens the dialog.
     *
     * @param validator    returns the translation key of a problem with the typed name, or empty when fine
     * @param onConfirm    receives the trimmed name after the dialog closed
     */
    public static NameDialog open(UiContext ctx, String title, String hint, String initial, String confirmLabel,
                                  Function<String, Optional<String>> validator, Consumer<String> onConfirm) {
        NameDialog d = new NameDialog(title, hint, initial, confirmLabel, validator, onConfirm);
        d.openContext = ctx;
        d.cancel.onClick(() -> d.close(ctx));
        ctx.popups().open(ctx, d, true, null);
        d.field.selectAll();
        ctx.focus().focus(ctx, d.field);
        return d;
    }

    /** The text field. */
    public TextField field() {
        return field;
    }

    /** The confirm button (disabled while the name is invalid). */
    public Button confirmButton() {
        return confirm;
    }

    /** Current validation problem (translated) or {@code null}. */
    public String error() {
        return error;
    }

    private void validate() {
        error = validator.apply(field.text()).map(Lang::tr).orElse(null);
        confirm.setEnabled(error == null);
    }

    private void submit() {
        validate();
        if (error != null) {
            return;
        }
        UiContext ctx = openContext;
        String name = field.text().trim();
        if (ctx != null) {
            close(ctx);
        }
        onConfirm.accept(name);
    }

    /**
     * Sizes the dialog for the current screen and centres it; runs on every layout pass so the dialog follows a
     * window resize or theme change instead of keeping the coordinates it opened with.
     */
    private void position(UiContext ctx) {
        int w = Math.min(WIDTH, Math.max(140, ctx.screenWidth() - Theme.SPACE_6 * 2));
        hintLines = hint.isEmpty() ? List.of() : CanvasText.wrap(hint, w - PAD * 2, FontKind.UI, ctx.metrics());
        int h = PAD + ctx.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_2 + hintLines.size() * ctx.lineHeight(FontKind.UI)
                + Theme.SPACE_4 + TextField.HEIGHT + Theme.SPACE_2 + ctx.lineHeight(FontKind.UI) + Theme.SPACE_4
                + Button.HEIGHT + PAD;
        setBounds((ctx.screenWidth() - w) / 2, Math.max(0, (ctx.screenHeight() - h) / 2), w, h);
    }

    /** Closes the dialog without confirming. */
    public void close(UiContext ctx) {
        ctx.popups().close(ctx, this);
    }

    private int fieldTop(UiContext ctx) {
        return bounds().y() + PAD + ctx.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_2
                + hintLines.size() * ctx.lineHeight(FontKind.UI) + Theme.SPACE_4;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return bounds().size();
    }

    @Override
    public void layout(UiContext ctx) {
        position(ctx);
        Rect b = bounds();
        field.setBounds(b.x() + PAD, fieldTop(ctx), b.w() - PAD * 2, TextField.HEIGHT);
        field.layout(ctx);
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
        canvas.text(canvas.textClipped(title, b.w() - PAD * 2, FontKind.UI_BOLD), b.x() + PAD, y, theme.textPrimary(),
                FontKind.UI_BOLD, false);
        y += canvas.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_2;
        for (String line : hintLines) {
            canvas.text(line, b.x() + PAD, y, theme.textSecondary(), FontKind.UI, false);
            y += canvas.lineHeight(FontKind.UI);
        }
        int messageY = fieldTop(ctx) + TextField.HEIGHT + Theme.SPACE_2;
        if (error != null) {
            canvas.text(canvas.textClipped(error, b.w() - PAD * 2, FontKind.UI), b.x() + PAD, messageY, theme.danger(),
                    FontKind.UI, false);
        } else {
            canvas.text(Lang.tr("vanta.profiles.name_ok"), b.x() + PAD, messageY, theme.textMuted(), FontKind.UI, false);
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
