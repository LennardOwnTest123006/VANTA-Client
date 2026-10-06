package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.ui.model.ToastModel;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Notification stack bound to a {@link ToastModel}. Each toast fades in, stays for its TTL and fades out; the close
 * button or a click anywhere on it dismisses it immediately.
 *
 * <p>Where the stack lies depends on the window: in a wide window ({@value #TOP_RIGHT_BELOW} px and more) it sits in the
 * bottom-right corner, over empty space. In a smaller window that corner holds controls (at the 960 x 600 minimum the
 * Mods page's lower Install buttons and the installed pane), so the stack moves to the top-right corner, below the
 * update banner and the page header ({@link #topInsetProperty()}), where nothing but the page's title row lies. A card
 * is at most {@value #MAX_WIDTH} px wide and never wider than {@value #WIDTH_SHARE_PERCENT} percent of the window, so it
 * never reaches the Install column.</p>
 */
public final class ToastLayer extends VBox {

    /** Longest time the mouse resting on a toast keeps it from timing out. */
    public static final Duration HOVER_LIMIT = Duration.seconds(8);
    /** Widest a toast card gets. */
    public static final double MAX_WIDTH = 360;
    /** Largest share of the window width a toast card takes, in percent. */
    public static final int WIDTH_SHARE_PERCENT = 30;
    /** Windows narrower than this anchor the stack top-right instead of bottom-right. */
    public static final double TOP_RIGHT_BELOW = 1100;
    /** Distance from the window edges (and below the banner/header when anchored top-right). */
    public static final double EDGE = 24;

    private final ToastModel model;
    private final String closeText;
    private final Map<Long, Node> nodes = new HashMap<>();
    private final DoubleProperty sceneWidth = new SimpleDoubleProperty(0);
    private final DoubleProperty topInset = new SimpleDoubleProperty(EDGE);
    private final DoubleBinding cardWidth = Bindings.createDoubleBinding(() -> cardWidth(sceneWidth.get()), sceneWidth);

    /**
     * @param model     toast model
     * @param closeText accessible text of the close button
     */
    public ToastLayer(final ToastModel model, final String closeText) {
        this.model = model;
        this.closeText = closeText;
        getStyleClass().add("toast-layer");
        setPickOnBounds(false);
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);
        setFillWidth(false);
        sceneProperty().addListener((obs, old, now) -> bindSceneWidth(now));
        bindSceneWidth(getScene());
        sceneWidth.addListener((obs, old, now) -> place());
        topInset.addListener((obs, old, now) -> place());
        place();
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

    /**
     * @param sceneWidth window (scene) width in pixels; 0 or less when unknown
     * @return the width of a toast card: at most {@value #MAX_WIDTH} px and at most {@value #WIDTH_SHARE_PERCENT} percent
     *     of the window
     */
    public static double cardWidth(final double sceneWidth) {
        if (sceneWidth <= 0) {
            return MAX_WIDTH;
        }
        return Math.min(MAX_WIDTH, Math.floor(sceneWidth * WIDTH_SHARE_PERCENT / 100.0));
    }

    /**
     * @param sceneWidth window (scene) width in pixels; 0 or less when unknown
     * @return whether the stack is anchored top-right (small window) rather than bottom-right
     */
    public static boolean anchorsTop(final double sceneWidth) {
        return sceneWidth > 0 && sceneWidth < TOP_RIGHT_BELOW;
    }

    /**
     * The y coordinate (in the coordinates of the parent {@link StackPane}) below which the stack lies when it is anchored
     * top-right: the bottom edge of the update banner and of the page header, whichever is lower. The owner keeps it up
     * to date; it is ignored while the stack is anchored bottom-right.
     *
     * @return the top inset property
     */
    public DoubleProperty topInsetProperty() {
        return topInset;
    }

    /** @return whether the stack currently lies in the top-right corner */
    public boolean isAnchoredTop() {
        return anchorsTop(sceneWidth.get());
    }

    private void bindSceneWidth(final Scene scene) {
        sceneWidth.unbind();
        if (scene == null) {
            sceneWidth.set(0);
        } else {
            sceneWidth.bind(scene.widthProperty());
        }
    }

    /** Anchors the stack to the corner that fits the window and keeps it clear of the banner and header. */
    private void place() {
        if (anchorsTop(sceneWidth.get())) {
            StackPane.setAlignment(this, Pos.TOP_RIGHT);
            setAlignment(Pos.TOP_RIGHT);
            setPadding(new Insets(Math.max(EDGE, topInset.get()), EDGE, EDGE, 0));
        } else {
            StackPane.setAlignment(this, Pos.BOTTOM_RIGHT);
            setAlignment(Pos.BOTTOM_RIGHT);
            setPadding(new Insets(0, EDGE, EDGE, 0));
        }
    }

    private void add(final ToastModel.Toast toast) {
        final HBox card = new HBox();
        card.getStyleClass().addAll("toast", toast.kind().name().toLowerCase(Locale.ROOT));
        card.prefWidthProperty().bind(cardWidth);
        card.maxWidthProperty().bind(cardWidth);
        final Region accent = new Region();
        accent.getStyleClass().add("toast-accent");
        accent.setMinHeight(Region.USE_COMPUTED_SIZE);
        final Label title = Ui.paragraph(toast.title(), "toast-title");
        final VBox text = new VBox(2, title);
        if (!toast.message().isEmpty()) {
            text.getChildren().add(Ui.paragraph(toast.message(), "toast-message"));
        }
        text.setMinWidth(0);
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
        // Even in its corner a toast may lie over something a click was meant for. A click anywhere on the toast
        // dismisses it, so no click vanishes silently, and the pause while the mouse rests on it ends by itself: a mouse
        // parked over a toast on the way to a control underneath must not keep the toast open for ever.
        card.setOnMouseClicked(e -> {
            model.dismiss(toast.id());
            e.consume();
        });
        final PauseTransition hoverLimit = new PauseTransition(HOVER_LIMIT);
        hoverLimit.setOnFinished(e -> ttl.play());
        card.setOnMouseEntered(e -> {
            ttl.pause();
            hoverLimit.playFromStart();
        });
        card.setOnMouseExited(e -> {
            hoverLimit.stop();
            ttl.play();
        });
    }
}
