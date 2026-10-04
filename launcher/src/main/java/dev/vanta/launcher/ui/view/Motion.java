package dev.vanta.launcher.ui.view;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Motion tokens (120/200/320 ms, cubic-bezier(0.2, 0.8, 0.2, 1)) and the reduced-motion switch every animation
 * honours. With reduced motion every transition completes instantly.
 */
public final class Motion {

    /** Standard easing from the design tokens. */
    public static final Interpolator EASE = Interpolator.SPLINE(0.2, 0.8, 0.2, 1);
    /** 120 ms. */
    public static final Duration FAST = Duration.millis(120);
    /** 180 ms page transition. */
    public static final Duration PAGE = Duration.millis(180);
    /** 200 ms. */
    public static final Duration BASE = Duration.millis(200);
    /** 320 ms. */
    public static final Duration SLOW = Duration.millis(320);

    private static final BooleanProperty REDUCED = new SimpleBooleanProperty(false);

    private Motion() {
    }

    /** @return the global reduced-motion property */
    public static BooleanProperty reducedProperty() {
        return REDUCED;
    }

    /** @return whether reduced motion is on */
    public static boolean reduced() {
        return REDUCED.get();
    }

    /**
     * Fades and slides a node in (page enter).
     *
     * @param node   node
     * @param onDone runs when finished
     */
    public static void enter(final Node node, final Runnable onDone) {
        if (reduced()) {
            node.setOpacity(1);
            node.setTranslateY(0);
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        node.setOpacity(0);
        node.setTranslateY(10);
        final FadeTransition fade = new FadeTransition(PAGE, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        final TranslateTransition slide = new TranslateTransition(PAGE, node);
        slide.setFromY(10);
        slide.setToY(0);
        final ParallelTransition p = new ParallelTransition(fade, slide);
        p.setInterpolator(EASE);
        if (onDone != null) {
            p.setOnFinished(e -> onDone.run());
        }
        p.play();
    }

    /**
     * Fades a node out.
     *
     * @param node     node
     * @param duration duration
     * @param onDone   runs when finished
     */
    public static void fadeOut(final Node node, final Duration duration, final Runnable onDone) {
        if (reduced()) {
            node.setOpacity(0);
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        final FadeTransition fade = new FadeTransition(duration, node);
        fade.setToValue(0);
        fade.setInterpolator(EASE);
        if (onDone != null) {
            fade.setOnFinished(e -> onDone.run());
        }
        fade.play();
    }

    /**
     * Fades a node in from its current opacity.
     *
     * @param node     node
     * @param duration duration
     */
    public static void fadeIn(final Node node, final Duration duration) {
        if (reduced()) {
            node.setOpacity(1);
            return;
        }
        final FadeTransition fade = new FadeTransition(duration, node);
        fade.setToValue(1);
        fade.setInterpolator(EASE);
        fade.play();
    }

    /**
     * Pulses a node's opacity forever (status dots while busy). Returns the animation so the caller can stop it.
     *
     * @param node node
     * @return running animation, or a stopped one with reduced motion
     */
    public static Animation pulse(final Node node) {
        final FadeTransition pulse = new FadeTransition(Duration.millis(900), node);
        pulse.setFromValue(1);
        pulse.setToValue(0.35);
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        if (!reduced()) {
            pulse.play();
        }
        return pulse;
    }
}
