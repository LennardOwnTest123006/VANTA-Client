package dev.vanta.launcher.ui.view;

import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

/**
 * Line icons drawn as {@link SVGPath}s on a 24×24 grid (stroke based, 1.75px). Colours come from the stylesheet via
 * the style class given to {@link #of(Icon, double, String...)}, so the same icon adapts to hover/selected states.
 */
public final class Icons {

    /** Available icons. */
    public enum Icon {
        /** House. */
        HOME("M3 11l9-8 9 8v9a2 2 0 0 1-2 2h-4v-7h-6v7H5a2 2 0 0 1-2-2z"),
        /** Stacked layers. */
        LAYERS("M12 2L2 7l10 5 10-5-10-5z M2 12l10 5 10-5 M2 17l10 5 10-5"),
        /** Terminal prompt. */
        TERMINAL("M4 17l6-6-6-6 M12 19h8"),
        /** Sliders. */
        SLIDERS("M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6"),
        /** Circled i. */
        INFO("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 16v-4 M12 8h.01"),
        /** Globe. */
        GLOBE("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M2 12h20 M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"),
        /** Life buoy. */
        LIFE_BUOY("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4.93 4.93l4.24 4.24 M14.83 14.83l4.24 4.24 M14.83 9.17l4.24-4.24 M4.93 19.07l4.24-4.24"),
        /** Arrow out of a box. */
        EXTERNAL("M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6 M15 3h6v6 M10 14L21 3"),
        /** Filled play triangle. */
        PLAY("M7 4.5v15l12-7.5z"),
        /** Check mark. */
        CHECK("M20 6L9 17l-5-5"),
        /** Check in circle. */
        CHECK_CIRCLE("M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4L12 14.01l-3-3"),
        /** Refresh arrow. */
        REFRESH("M21 12a9 9 0 1 1-2.64-6.36 M21 3v6h-6"),
        /** Rollback arrow. */
        ROTATE_CCW("M3 12a9 9 0 1 0 2.64-6.36 M3 3v6h6"),
        /** Folder. */
        FOLDER("M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"),
        /** Two sheets. */
        COPY("M20 9h-9a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2z M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"),
        /** Cross. */
        CLOSE("M18 6L6 18 M6 6l12 12"),
        /** Download tray. */
        DOWNLOAD("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3"),
        /** Shield. */
        SHIELD("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"),
        /** Person. */
        USER("M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"),
        /** Chip. */
        CPU("M18 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z M15 9H9v6h6z M9 1v3 M15 1v3 M9 20v3 M15 20v3 M20 9h3 M20 14h3 M1 9h3 M1 14h3"),
        /** Box. */
        PACKAGE("M16.5 9.4l-9-5.19 M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z M3.27 6.96L12 12.01l8.73-5.05 M12 22.08V12"),
        /** Warning triangle. */
        ALERT("M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z M12 9v4 M12 17h.01"),
        /** Magnifier. */
        SEARCH("M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z M21 21l-4.35-4.35"),
        /** Arrow up in circle. */
        ARROW_UP_CIRCLE("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M16 12l-4-4-4 4 M12 16V8"),
        /** Door with arrow. */
        LOG_OUT("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9"),
        /** Sparkles. */
        SPARKLES("M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z M5 3v4 M19 17v4 M3 5h4 M17 19h4"),
        /** Document with lines. */
        FILE_TEXT("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8"),
        /** Clock. */
        CLOCK("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M12 6v6l4 2"),
        /** Key. */
        KEY("M21 2l-2 2 M11.39 11.61a5.5 5.5 0 1 1-7.78 7.78 5.5 5.5 0 0 1 7.78-7.78z M11.39 11.61L15.5 7.5 M15.5 7.5l3 3L22 7l-3-3 M15.5 7.5L19 4"),
        /** Code brackets. */
        CODE("M16 18l6-6-6-6 M8 6l-6 6 6 6"),
        /** Hard disk. */
        HARD_DRIVE("M22 12H2 M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z M6 16h.01 M10 16h.01"),
        /** Eraser. */
        ERASER("M20 20H7L3 16c-.8-.8-.8-2.1 0-2.9L13.1 3.1c.8-.8 2.1-.8 2.9 0L21 8.1c.8.8.8 2.1 0 2.9L12.3 19.8 M6.5 11.5l6 6"),
        /** Stop square. */
        STOP("M6 6h12v12H6z"),
        /** Waste bin. */
        TRASH("M3 6h18 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2 M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6 M10 11v6 M14 11v6"),
        /** Chevron down. */
        CHEVRON_DOWN("M6 9l6 6 6-6"),
        /** Heart. */
        HEART("M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"),
        /** Scale (license). */
        SCALE("M12 3v18 M5 21h14 M3 7h18 M5 7l-3 7a4 4 0 0 0 6 0l-3-7z M19 7l-3 7a4 4 0 0 0 6 0l-3-7z");

        private final String path;

        Icon(final String path) {
            this.path = path;
        }

        /** @return SVG path data */
        public String path() {
            return path;
        }
    }

    private Icons() {
    }

    /**
     * Creates an icon node.
     *
     * @param icon         icon
     * @param size         rendered size in pixels
     * @param styleClasses style classes applied to the path (colour comes from CSS)
     * @return node sized exactly {@code size × size}
     */
    public static StackPane of(final Icon icon, final double size, final String... styleClasses) {
        final SVGPath path = new SVGPath();
        path.setContent(icon.path());
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.web("#A1A1AA"));
        path.setStrokeWidth(1.75);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        path.getStyleClass().addAll(styleClasses);
        if (icon == Icon.PLAY) {
            path.setFill(Color.WHITE);
            path.setStroke(Color.TRANSPARENT);
        }
        final double scale = size / 24d;
        final StackPane box = new StackPane();
        box.setAlignment(Pos.CENTER);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        final StackPane inner = new StackPane(path);
        inner.setMinSize(24, 24);
        inner.setPrefSize(24, 24);
        inner.setMaxSize(24, 24);
        inner.getTransforms().add(new Scale(scale, scale, 12, 12));
        box.getChildren().add(inner);
        return box;
    }
}
