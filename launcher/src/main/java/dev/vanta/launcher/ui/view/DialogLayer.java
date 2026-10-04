package dev.vanta.launcher.ui.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;

/**
 * In-window modal dialogs: a dimmed backdrop over the whole window with the dialog card centred. Focus is trapped
 * inside the dialog (Tab cycles), Esc closes, clicking the backdrop closes when the dialog allows it.
 */
public final class DialogLayer extends StackPane {

    /** A dialog that can be shown in the layer. */
    public interface Dialog {
        /** @return the dialog card */
        Node node();

        /** Called when the layer is about to show the dialog (focus the first control here). */
        default void onShown() {
        }

        /** Called when the dialog closes for any reason. */
        default void onClosed() {
        }

        /** @return whether Esc / backdrop click may close the dialog */
        default boolean closable() {
            return true;
        }
    }

    private final Region backdrop = new Region();
    private Dialog current;
    private Node previousFocus;

    /** Creates an empty, hidden layer. */
    public DialogLayer() {
        backdrop.getStyleClass().add("dialog-backdrop");
        backdrop.setOnMouseClicked(e -> {
            if (current != null && current.closable()) {
                close();
            }
        });
        setAlignment(Pos.CENTER);
        setVisible(false);
        setManaged(false);
        addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
    }

    /** @return whether a dialog is open */
    public boolean isOpen() {
        return current != null;
    }

    /**
     * Shows a dialog (closing any open one first).
     *
     * @param dialog dialog
     */
    public void show(final Dialog dialog) {
        if (current != null) {
            closeInternal();
        }
        current = dialog;
        previousFocus = getScene() == null ? null : getScene().getFocusOwner();
        final Node card = dialog.node();
        if (card instanceof Region region) {
            clampHeight(region);
            heightProperty().addListener((obs, old, now) -> {
                if (current == dialog) {
                    clampHeight(region);
                }
            });
        }
        StackPane.setAlignment(card, Pos.CENTER);
        getChildren().setAll(backdrop, card);
        setVisible(true);
        setManaged(true);
        card.setOpacity(0);
        Motion.enter(card, null);
        dialog.onShown();
        if (getScene() != null && (getScene().getFocusOwner() == null || !isInside(getScene().getFocusOwner()))) {
            final List<Node> focusables = focusables(card);
            if (!focusables.isEmpty()) {
                focusables.get(0).requestFocus();
            }
        }
    }

    /** Closes the open dialog. */
    public void close() {
        if (current == null) {
            return;
        }
        closeInternal();
    }

    private void closeInternal() {
        final Dialog d = current;
        current = null;
        getChildren().clear();
        setVisible(false);
        setManaged(false);
        d.onClosed();
        if (previousFocus != null && previousFocus.getScene() != null) {
            previousFocus.requestFocus();
        }
        previousFocus = null;
    }

    /** Keeps the card at its preferred height unless the window is smaller; then it shrinks and scrolls inside. */
    private void clampHeight(final Region card) {
        final double available = getHeight() - 48;
        final double preferred = card.prefHeight(card.getWidth() > 0 ? card.getWidth() : card.getMaxWidth());
        card.setMaxHeight(available > 0 && preferred > available ? available : Region.USE_PREF_SIZE);
    }

    private void onKey(final KeyEvent e) {
        if (current == null) {
            return;
        }
        if (e.getCode() == KeyCode.ESCAPE && current.closable()) {
            close();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.TAB) {
            final List<Node> focusables = focusables(current.node());
            if (focusables.isEmpty()) {
                e.consume();
                return;
            }
            final Node owner = getScene() == null ? null : getScene().getFocusOwner();
            int index = focusables.indexOf(owner);
            if (index < 0) {
                index = e.isShiftDown() ? 0 : -1;
            }
            final int next = e.isShiftDown() ? (index - 1 + focusables.size()) % focusables.size() : (index + 1) % focusables.size();
            focusables.get(next).requestFocus();
            e.consume();
        }
    }

    private boolean isInside(final Node node) {
        Node n = node;
        while (n != null) {
            if (n == this) {
                return true;
            }
            n = n.getParent();
        }
        return false;
    }

    /**
     * @param root root node
     * @return focus-traversable, visible, enabled descendants in document order
     */
    static List<Node> focusables(final Node root) {
        final List<Node> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(final Node node, final List<Node> out) {
        if (!node.isVisible() || node.isDisabled()) {
            return;
        }
        if (node.isFocusTraversable() && !(node instanceof Parent p && isContainerOnly(p))) {
            out.add(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, out);
            }
        }
    }

    private static boolean isContainerOnly(final Parent p) {
        return p.getClass() == StackPane.class || p.getClass() == javafx.scene.layout.VBox.class || p.getClass() == javafx.scene.layout.HBox.class
            || p instanceof javafx.scene.control.ScrollPane;
    }
}
