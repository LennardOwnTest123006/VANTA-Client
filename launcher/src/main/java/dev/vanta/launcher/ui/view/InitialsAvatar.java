package dev.vanta.launcher.ui.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.Locale;

/**
 * Round avatar with the player's initials on the accent gradient. Skin heads need the network, so this is the
 * honest offline representation of an account; a neutral grey variant stands for "signed out".
 */
public final class InitialsAvatar extends StackPane {

    private final Label initials = new Label();

    /**
     * @param name player name (empty = signed out)
     * @param size diameter in pixels
     */
    public InitialsAvatar(final String name, final double size) {
        getStyleClass().add("avatar");
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setAlignment(Pos.CENTER);
        initials.getStyleClass().add("avatar-initials");
        initials.setStyle("-fx-font-size: " + Math.round(size * 0.4) + "px;");
        getChildren().add(initials);
        setName(name);
    }

    /**
     * Updates the initials.
     *
     * @param name player name (empty = signed out)
     */
    public void setName(final String name) {
        final String text = initialsOf(name);
        initials.setText(text);
        getStyleClass().remove("avatar-neutral");
        if (text.isEmpty()) {
            getStyleClass().add("avatar-neutral");
            initials.setText("?");
        }
        setAccessibleText(name == null || name.isBlank() ? "" : name);
    }

    /**
     * @param name player name
     * @return up to two upper-case initials (letters and digits around underscores)
     */
    public static String initialsOf(final String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        final String[] parts = name.trim().split("[\\s_\\-.]+");
        final StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(p.charAt(0)));
            }
        }
        if (sb.length() < 2 && parts.length == 1 && parts[0].length() >= 2) {
            // Single word: first letter + next upper-case letter or second letter (e.g. "NovaPlayer" → "NP")
            final String word = parts[0];
            for (int i = 1; i < word.length(); i++) {
                if (Character.isUpperCase(word.charAt(i))) {
                    sb.append(word.charAt(i));
                    break;
                }
            }
            if (sb.length() < 2) {
                sb.append(Character.toUpperCase(word.charAt(1)));
            }
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
