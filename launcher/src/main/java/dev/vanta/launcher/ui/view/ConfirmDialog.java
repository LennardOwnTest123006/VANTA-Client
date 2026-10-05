package dev.vanta.launcher.ui.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Confirmation card (title, text, optional details, confirm/cancel) for the {@link DialogLayer}.
 */
public final class ConfirmDialog implements DialogLayer.Dialog {

    private final VBox card = new VBox();
    private final Button confirm;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean confirmed;

    /**
     * @param title       title
     * @param text        body text
     * @param confirmText confirm button text
     * @param cancelText  cancel button text
     * @param onConfirm   runs when confirmed (dialog closes first)
     * @param onCancel    runs when cancelled/closed
     * @param close       closes the dialog
     */
    public ConfirmDialog(final String title, final String text, final String confirmText, final String cancelText,
                         final Runnable onConfirm, final Runnable onCancel, final Runnable close) {
        this(title, text, null, confirmText, cancelText, onConfirm, onCancel, close);
    }

    /**
     * @param title       title
     * @param text        body text
     * @param details     optional node shown between the text and the buttons (wide dialog when present)
     * @param confirmText confirm button text
     * @param cancelText  cancel button text
     * @param onConfirm   runs when confirmed (dialog closes first)
     * @param onCancel    runs when cancelled/closed
     * @param close       closes the dialog
     */
    public ConfirmDialog(final String title, final String text, final Node details, final String confirmText, final String cancelText,
                         final Runnable onConfirm, final Runnable onCancel, final Runnable close) {
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        card.getStyleClass().add("dialog");
        confirm = Ui.button(confirmText, "primary");
        confirm.setDefaultButton(true);
        confirm.setOnAction(e -> {
            confirmed = true;
            close.run();
        });
        final Button cancel = Ui.button(cancelText, "secondary");
        cancel.setOnAction(e -> close.run());
        final HBox actions = new HBox(10, Ui.spacer(), cancel, confirm);
        actions.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().addAll(Ui.label(title, "dialog-title"), Ui.paragraph(text, "dialog-text"));
        if (details != null) {
            card.getStyleClass().add("wide");
            card.setSpacing(10);
            card.getChildren().add(details);
        }
        card.getChildren().addAll(Ui.vgap(4), actions);
    }

    @Override
    public Node node() {
        return card;
    }

    @Override
    public void onShown() {
        confirm.requestFocus();
    }

    @Override
    public void onClosed() {
        if (confirmed) {
            onConfirm.run();
        } else {
            onCancel.run();
        }
    }
}
