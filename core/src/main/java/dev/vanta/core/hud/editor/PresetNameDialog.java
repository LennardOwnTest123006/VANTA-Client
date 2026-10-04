package dev.vanta.core.hud.editor;

import dev.vanta.core.hud.HudStore;
import dev.vanta.core.i18n.Lang;
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
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.TextField;

import java.util.function.Consumer;

/**
 * Modal "Save as preset" dialog: a name field (Enter saves, Escape cancels) and Save / Cancel buttons. The Save
 * button stays disabled while the name is blank.
 */
public final class PresetNameDialog extends UiNode {

    /** Dialog width. */
    public static final int WIDTH = 240;
    private static final int PAD = 14;
    private static final int ICON = 12;

    private final TextField name;
    private final Button save;
    private final Button cancel;
    private final Consumer<String> onSave;

    private PresetNameDialog(UiContext ctx, Consumer<String> onSave) {
        this.onSave = onSave;
        this.name = new TextField("")
                .placeholder(Lang.tr("vanta.hud.editor.preset_name_placeholder"))
                .maxLength(HudStore.MAX_PRESET_NAME)
                .onChange(text -> refreshSaveState())
                .onSubmit(text -> submit(ctx));
        this.cancel = Button.secondary(Lang.tr("vanta.common.cancel"), () -> close(ctx));
        this.save = Button.primary(Lang.tr("vanta.common.save"), () -> submit(ctx));
        save.setEnabled(false);
        add(name);
        add(cancel);
        add(save);
    }

    /** Opens the dialog as a modal popup and focuses the name field. */
    public static PresetNameDialog open(UiContext ctx, Consumer<String> onSave) {
        PresetNameDialog dialog = new PresetNameDialog(ctx, onSave);
        dialog.position(ctx);
        ctx.popups().open(ctx, dialog, true, null);
        ctx.focus().focus(ctx, dialog.name);
        return dialog;
    }

    /** The name field. */
    public TextField nameField() {
        return name;
    }

    /** The Save button. */
    public Button saveButton() {
        return save;
    }

    private void refreshSaveState() {
        save.setEnabled(!name.text().isBlank());
    }

    private void submit(UiContext ctx) {
        String value = name.text().trim();
        if (value.isEmpty()) {
            return;
        }
        close(ctx);
        if (onSave != null) {
            onSave.accept(value);
        }
    }

    /** Closes the dialog without saving. */
    public void close(UiContext ctx) {
        ctx.popups().close(ctx, this);
    }

    private void position(UiContext ctx) {
        int w = Math.min(WIDTH, Math.max(140, ctx.screenWidth() - Theme.SPACE_6 * 2));
        int h = PAD + ctx.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_2 + ctx.lineHeight(FontKind.UI) + Theme.SPACE_4
                + TextField.HEIGHT + Theme.SPACE_5 + Button.HEIGHT + PAD;
        setBounds((ctx.screenWidth() - w) / 2, (ctx.screenHeight() - h) / 2, w, h);
    }

    @Override
    protected Size measure(UiContext ctx) {
        return bounds().size();
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        int fieldY = b.y() + PAD + ctx.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_2 + ctx.lineHeight(FontKind.UI)
                + Theme.SPACE_4;
        name.setBounds(b.x() + PAD, fieldY, b.w() - PAD * 2, TextField.HEIGHT);
        name.layout(ctx);
        int buttonY = b.bottom() - PAD - Button.HEIGHT;
        Size saveSize = save.preferredSize(ctx);
        Size cancelSize = cancel.preferredSize(ctx);
        int saveX = b.right() - PAD - saveSize.w();
        save.setBounds(saveX, buttonY, saveSize.w(), Button.HEIGHT);
        save.layout(ctx);
        cancel.setBounds(saveX - Theme.SPACE_3 - cancelSize.w(), buttonY, cancelSize.w(), Button.HEIGHT);
        cancel.layout(ctx);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fillRounded(b.x() + 2, b.y() + 4, b.w(), b.h(), Theme.RADIUS_LG, Colors.withAlpha(theme.bgVoid(), 0.6f));
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.surface2());
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.borderStrong());
        int y = b.y() + PAD;
        Icons.STAR.draw(canvas, b.x() + PAD, y - 1, ICON, theme.accentHover());
        int x = b.x() + PAD + ICON + Theme.SPACE_3;
        canvas.text(canvas.textClipped(Lang.tr("vanta.hud.editor.save_preset"), b.right() - PAD - x, FontKind.UI_BOLD),
                x, y, theme.textPrimary(), FontKind.UI_BOLD, false);
        y += canvas.lineHeight(FontKind.UI_BOLD) + Theme.SPACE_2;
        canvas.text(canvas.textClipped(Lang.tr("vanta.hud.editor.preset_name"), b.right() - PAD - x, FontKind.UI), x, y,
                theme.textSecondary(), FontKind.UI, false);
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
