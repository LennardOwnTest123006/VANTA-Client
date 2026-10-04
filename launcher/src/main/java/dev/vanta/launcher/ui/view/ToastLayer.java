package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.ui.model.ToastModel;
import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bottom-right notification stack bound to a {@link ToastModel}. Each toast fades in, stays for its TTL and fades
 * out; the close button dismisses it immediately.
 */
public final class ToastLayer extends VBox {

    private final ToastModel model;
    private final String closeText;
    private final Map<Long, Node> nodes = new HashMap<>();

    /**
     * @param model     toast model
     * @param closeText accessible text of the close button
     */
    public ToastLayer(final ToastModel model, final String closeText) {
        this.model = model;
        this.closeText = closeText;
        getStyleClass().add("toast-layer");
        setPickOnBounds(false);
        setAlignment(Pos.BOTTOM_RIGHT);
        setMaxWidth(Region.USE_PREF_SIZE);
        setFillWidth(false);
        model.toasts().addListener((ListChangeListener<ToastModel.Toast>) change -> {
            while (change.next()) {
                for (ToastModel.Toast removed : change.getRemoved()) {
                    final Node n = nodes.remove(removed.id());
                    if (n != null) {
                        Motion.fadeOut(n, Motion.FAST, () -> getChildren().remove(n));
                    }
                }
                for (ToastModel.Toast added : change.getAddedSubList()) {
                    add(added);
                }
            }
        });
        for (ToastModel.Toast t : model.toasts()) {
            add(t);
        }
    }

    private void add(final ToastModel.Toast toast) {
        final HBox card = new HBox();
        card.getStyleClass().addAll("toast", toast.kind().name().toLowerCase(Locale.ROOT));
        final Region accent = new Region();
        accent.getStyleClass().add("toast-accent");
        accent.setMinHeight(Region.USE_COMPUTED_SIZE);
        final Label title = Ui.paragraph(toast.title(), "toast-title");
        final VBox text = new VBox(2, title);
        if (!toast.message().isEmpty()) {
            text.getChildren().add(Ui.paragraph(toast.message(), "toast-message"));
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        final Button close = Ui.iconButton(Icons.Icon.CLOSE, closeText, "ghost");
        close.setFocusTraversable(false);
        close.setOnAction(e -> model.dismiss(toast.id()));
        card.getChildren().addAll(accent, text, close);
        card.setAccessibleText(toast.title() + ". " + toast.message());
        nodes.put(toast.id(), card);
        getChildren().add(card);
        Motion.enter(card, null);
        final PauseTransition ttl = new PauseTransition(Duration.millis(toast.ttl().toMillis()));
        ttl.setOnFinished(e -> model.dismiss(toast.id()));
        ttl.play();
        card.setOnMouseEntered(e -> ttl.pause());
        card.setOnMouseExited(e -> ttl.play());
    }
}
