package dev.vanta.launcher.ui.view;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * An on/off switch (JavaFX has no built-in one). Keyboard: Space or Enter toggles; it takes part in focus traversal
 * and exposes {@code :selected} to the stylesheet.
 */
public final class Switch extends StackPane {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final double WIDTH = 40;
    private static final double HEIGHT = 22;
    private static final double THUMB = 16;

    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected", false);
    private final Region thumb = new Region();
    private boolean animate = true;

    /** Creates an unselected switch. */
    public Switch() {
        getStyleClass().add("switch");
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
        setMinSize(WIDTH, HEIGHT);
        setPrefSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setAlignment(Pos.CENTER_LEFT);
        final Region track = new Region();
        track.getStyleClass().add("track");
        track.setMinSize(WIDTH - 4, HEIGHT - 4);
        track.setPrefSize(WIDTH - 4, HEIGHT - 4);
        track.setMaxSize(WIDTH - 4, HEIGHT - 4);
        thumb.getStyleClass().add("thumb");
        thumb.setMinSize(THUMB, THUMB);
        thumb.setPrefSize(THUMB, THUMB);
        thumb.setMaxSize(THUMB, THUMB);
        thumb.setTranslateX(offset(false));
        getChildren().addAll(track, thumb);
        StackPane.setAlignment(track, Pos.CENTER);
        StackPane.setAlignment(thumb, Pos.CENTER_LEFT);
        setOnMouseClicked(e -> {
            if (!isDisabled()) {
                requestFocus();
                toggle();
            }
        });
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                toggle();
                e.consume();
            }
        });
        selected.addListener((obs, old, now) -> {
            pseudoClassStateChanged(SELECTED, now);
            setAccessibleText(now ? "on" : "off");
            moveThumb(now);
        });
    }

    /** @return selected property */
    public BooleanProperty selectedProperty() {
        return selected;
    }

    /** @return whether on */
    public boolean isSelected() {
        return selected.get();
    }

    /**
     * @param value on/off
     */
    public void setSelected(final boolean value) {
        selected.set(value);
    }

    /**
     * @param enabled whether thumb movement animates (false for reduced motion)
     */
    public void setAnimated(final boolean enabled) {
        this.animate = enabled;
    }

    private void toggle() {
        selected.set(!selected.get());
    }

    private void moveThumb(final boolean on) {
        final double target = offset(on);
        if (!animate) {
            thumb.setTranslateX(target);
            return;
        }
        final TranslateTransition t = new TranslateTransition(Duration.millis(140), thumb);
        t.setToX(target);
        t.setInterpolator(Interpolator.SPLINE(0.2, 0.8, 0.2, 1));
        t.play();
    }

    private static double offset(final boolean on) {
        final double inset = (HEIGHT - THUMB) / 2;
        return on ? WIDTH - THUMB - inset : inset;
    }
}
