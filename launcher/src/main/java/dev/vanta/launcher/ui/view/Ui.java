package dev.vanta.launcher.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Small factory helpers that keep the page classes readable. Every node gets its look from {@code vanta.css}.
 */
public final class Ui {

    private Ui() {
    }

    /**
     * @param text         text
     * @param styleClasses style classes
     * @return label
     */
    public static Label label(final String text, final String... styleClasses) {
        final Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    /**
     * @param text         text
     * @param styleClasses style classes
     * @return wrapping label that stretches to its container
     */
    public static Label paragraph(final String text, final String... styleClasses) {
        final Label l = label(text, styleClasses);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    /**
     * @param text         text
     * @param styleClasses style classes (added to {@code button})
     * @return button
     */
    public static Button button(final String text, final String... styleClasses) {
        final Button b = new Button(text);
        b.getStyleClass().addAll(styleClasses);
        return b;
    }

    /**
     * @param text         text
     * @param icon         icon
     * @param styleClasses style classes
     * @return button with a leading icon
     */
    public static Button button(final String text, final Icons.Icon icon, final String... styleClasses) {
        final Button b = button(text, styleClasses);
        b.setGraphic(Icons.of(icon, 15, "button-icon"));
        return b;
    }

    /**
     * @param icon           icon
     * @param accessibleText screen reader text (also the tooltip)
     * @param styleClasses   style classes
     * @return icon-only button
     */
    public static Button iconButton(final Icons.Icon icon, final String accessibleText, final String... styleClasses) {
        final Button b = new Button();
        b.getStyleClass().addAll("icon-only");
        b.getStyleClass().addAll(styleClasses);
        b.setGraphic(Icons.of(icon, 16, "button-icon"));
        b.setAccessibleText(accessibleText);
        b.setTooltip(tooltip(accessibleText));
        return b;
    }

    /**
     * @param text text
     * @return tooltip with a short show delay
     */
    public static Tooltip tooltip(final String text) {
        final Tooltip t = new Tooltip(text);
        t.setShowDelay(Duration.millis(350));
        t.setHideDelay(Duration.millis(100));
        t.setWrapText(true);
        t.setMaxWidth(320);
        return t;
    }

    /**
     * @param children children
     * @return card container
     */
    public static VBox card(final Node... children) {
        final VBox card = new VBox(children);
        card.getStyleClass().add("card");
        return card;
    }

    /**
     * Card header: title label on the left, optional trailing node on the right.
     *
     * @param title    title
     * @param trailing trailing node (may be null)
     * @return header row
     */
    public static HBox cardHeader(final String title, final Node trailing) {
        final Label t = label(title, "card-title");
        final HBox row = new HBox(10, t);
        row.setAlignment(Pos.CENTER_LEFT);
        if (trailing != null) {
            row.getChildren().addAll(spacer(), trailing);
        }
        return row;
    }

    /**
     * Page header: title and wrapping subtitle on the left, actions (kept at their preferred size) on the right.
     *
     * @param title    title
     * @param subtitle subtitle
     * @param actions  trailing nodes (may be empty)
     * @return header row
     */
    public static HBox pageHeader(final String title, final String subtitle, final Node... actions) {
        final VBox titles = new VBox(6, label(title, "page-title"), paragraph(subtitle, "page-subtitle"));
        titles.setMinWidth(0);
        HBox.setHgrow(titles, Priority.ALWAYS);
        final HBox row = new HBox(16, titles);
        row.getStyleClass().add("page-header");
        row.setAlignment(Pos.CENTER_LEFT);
        if (actions.length > 0) {
            final HBox trailing = new HBox(8, actions);
            trailing.setAlignment(Pos.CENTER_RIGHT);
            trailing.setMinWidth(Region.USE_PREF_SIZE);
            row.getChildren().add(trailing);
        }
        return row;
    }

    /** @return horizontally growing filler */
    public static Region spacer() {
        final Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    /**
     * @param height fixed height
     * @return vertical gap
     */
    public static Region vgap(final double height) {
        final Region r = new Region();
        r.setMinHeight(height);
        r.setPrefHeight(height);
        r.setMaxHeight(height);
        return r;
    }

    /**
     * @param width fixed width
     * @return horizontal gap
     */
    public static Region hgap(final double width) {
        final Region r = new Region();
        r.setMinWidth(width);
        r.setPrefWidth(width);
        r.setMaxWidth(width);
        return r;
    }

    /**
     * Key/value row used in cards.
     *
     * @param key   key text
     * @param value value node
     * @return row
     */
    public static HBox keyValue(final String key, final Node value) {
        final Label k = label(key, "kv-key");
        k.setMinWidth(Region.USE_PREF_SIZE);
        final HBox row = new HBox(12, k, spacer(), value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * @param text         text
     * @param styleClasses badge variant classes
     * @return badge
     */
    public static Label badge(final String text, final String... styleClasses) {
        final Label b = label(text, "badge");
        b.getStyleClass().addAll(styleClasses);
        return b;
    }

    /**
     * @param node    node
     * @param visible whether visible (also unmanaged when hidden)
     */
    public static void show(final Node node, final boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /**
     * Binds visibility and managed state together.
     *
     * @param node    node
     * @param visible observable
     */
    public static void bindVisible(final Node node, final javafx.beans.value.ObservableValue<Boolean> visible) {
        node.visibleProperty().bind(visible);
        node.managedProperty().bind(visible);
    }

    /**
     * @param top    top
     * @param right  right
     * @param bottom bottom
     * @param left   left
     * @return insets
     */
    public static Insets insets(final double top, final double right, final double bottom, final double left) {
        return new Insets(top, right, bottom, left);
    }

    /**
     * Empty state block (icon, title, text).
     *
     * @param icon  icon
     * @param title title
     * @param text  text
     * @return node
     */
    public static VBox emptyState(final Icons.Icon icon, final String title, final String text) {
        final Label t = label(title, "empty-state-title");
        final Label d = paragraph(text, "empty-state-text");
        d.setAlignment(Pos.CENTER);
        d.setMaxWidth(420);
        final VBox box = new VBox(Icons.of(icon, 28, "empty-icon"), vgap(6), t, d);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /**
     * Callout block.
     *
     * @param variant   {@code warning}, {@code danger}, {@code accent} or empty
     * @param title     title
     * @param text      text
     * @param trailing  optional node below the text
     * @return callout
     */
    public static VBox callout(final String variant, final String title, final String text, final Node trailing) {
        final VBox box = new VBox(4);
        box.getStyleClass().add("callout");
        if (variant != null && !variant.isEmpty()) {
            box.getStyleClass().add(variant);
        }
        if (title != null && !title.isEmpty()) {
            box.getChildren().add(label(title, "callout-title"));
        }
        box.getChildren().add(paragraph(text, "callout-text"));
        if (trailing != null) {
            box.getChildren().addAll(vgap(4), trailing);
        }
        return box;
    }
}
