package dev.vanta.launcher.ui.view;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * The VANTA mark (geometric V with the diamond cut-out) and the path-based wordmark, both taken from
 * {@code assets/brand/*.svg} so the launcher never depends on a font for its logo.
 */
public final class BrandMark {

    /** Mark path on a 64×64 grid (from vanta-mark.svg). */
    public static final String MARK_PATH = "M5 9 H19.5 L32 36.4 L44.5 9 H59 L32 58 Z M32 31.6 L36.6 40.2 L33.2 51.6 L28.4 42.2 Z";
    /** Wordmark letter paths on a 332×72 grid (from vanta-wordmark.svg). */
    private static final String[] WORD_PATHS = {
        "M0 0 H14 L30 48 L46 0 H60 L37 72 H23 Z",
        "M70 72 L94 0 H108 L132 72 H118 L113 56 H89 L84 72 Z M93 44 H109 L101 17 Z",
        "M144 72 V0 H157 L186 46 V0 H199 V72 H186 L157 26 V72 Z",
        "M208 0 H266 V13 H243.5 V72 H230.5 V13 H208 Z",
        "M270 72 L294 0 H308 L332 72 H318 L313 56 H289 L284 72 Z M293 44 H309 L301 17 Z"
    };
    private static final double WORD_WIDTH = 332;
    private static final double WORD_HEIGHT = 72;

    private BrandMark() {
    }

    /**
     * @param size pixel size
     * @return the gradient mark
     */
    public static StackPane mark(final double size) {
        final SVGPath path = new SVGPath();
        path.setContent(MARK_PATH);
        path.setFillRule(FillRule.EVEN_ODD);
        path.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.web("#9B82FF")),
            new Stop(1, Color.web("#4F8DFF"))));
        final Group g = new Group(path);
        final double scale = size / 64d;
        g.getTransforms().add(new Scale(scale, scale));
        final StackPane box = new StackPane(g);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    /**
     * @param height cap height in pixels
     * @return the VANTA wordmark letters
     */
    public static StackPane wordmark(final double height) {
        final Group g = new Group();
        for (String d : WORD_PATHS) {
            final SVGPath p = new SVGPath();
            p.setContent(d);
            p.setFillRule(FillRule.EVEN_ODD);
            p.getStyleClass().add("brand-wordmark");
            p.setFill(Color.web("#F5F5F7"));
            g.getChildren().add(p);
        }
        final double scale = height / WORD_HEIGHT;
        g.getTransforms().add(new Scale(scale, scale));
        final StackPane box = new StackPane(g);
        box.setMinSize(WORD_WIDTH * scale, height);
        box.setPrefSize(WORD_WIDTH * scale, height);
        box.setMaxSize(WORD_WIDTH * scale, height);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    /**
     * @param markSize mark size
     * @param wordHeight wordmark height
     * @return mark + wordmark in a row
     */
    public static HBox lockup(final double markSize, final double wordHeight) {
        final HBox row = new HBox(10, mark(markSize), wordmark(wordHeight));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
