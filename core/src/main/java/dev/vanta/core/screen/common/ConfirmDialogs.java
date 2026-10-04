package dev.vanta.core.screen.common;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.widget.Dialog;

/**
 * The confirmation dialogs VANTA screens share, with their translated copy in one place.
 */
public final class ConfirmDialogs {
    private ConfirmDialogs() {
    }

    /** "Quit Minecraft?" */
    public static Dialog quitGame(UiContext ctx, Runnable onConfirm) {
        return Dialog.confirm(ctx, Lang.tr("vanta.menu.quit_confirm.title"), Lang.tr("vanta.menu.quit_confirm.body"),
                Lang.tr("vanta.menu.quit"), Lang.tr("vanta.common.cancel"), true, onConfirm);
    }

    /** "Reset all settings?" */
    public static Dialog resetAllSettings(UiContext ctx, Runnable onConfirm) {
        return Dialog.confirm(ctx, Lang.tr("vanta.dialog.reset_all.title"), Lang.tr("vanta.dialog.reset_all.body"),
                Lang.tr("vanta.settings.reset_all"), Lang.tr("vanta.common.cancel"), true, onConfirm);
    }

    /** "Discard changes?" */
    public static Dialog discardChanges(UiContext ctx, Runnable onConfirm) {
        return Dialog.confirm(ctx, Lang.tr("vanta.dialog.unsaved.title"), Lang.tr("vanta.dialog.unsaved.body"),
                Lang.tr("vanta.dialog.unsaved.discard"), Lang.tr("vanta.common.cancel"), true, onConfirm);
    }

    /** "Clear statistics?" */
    public static Dialog clearStatistics(UiContext ctx, Runnable onConfirm) {
        return Dialog.confirm(ctx, Lang.tr("vanta.stats.confirm_clear.title"),
                Lang.tr("vanta.stats.confirm_clear.body"), Lang.tr("vanta.stats.clear"),
                Lang.tr("vanta.common.cancel"), true, onConfirm);
    }
}
