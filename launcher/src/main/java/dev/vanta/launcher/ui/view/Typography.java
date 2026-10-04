package dev.vanta.launcher.ui.view;

import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads the bundled Inter and Space Grotesk faces from the class path so the stylesheet can refer to them by name.
 * Loading is idempotent; failures fall back to the system font without breaking the UI.
 */
public final class Typography {

    private static final Logger LOG = Logger.getLogger("VANTA.UI");
    private static final String BASE = "/dev/vanta/launcher/ui/fonts/";
    private static final List<String> FILES = List.of("Inter-Regular.ttf", "Inter-Medium.ttf", "Inter-SemiBold.ttf", "Inter-Bold.ttf",
        "SpaceGrotesk-Bold.ttf");
    private static volatile List<String> loaded;

    private Typography() {
    }

    /**
     * Loads every bundled font once.
     *
     * @return the family names that were registered (empty when nothing could be loaded)
     */
    public static synchronized List<String> load() {
        if (loaded != null) {
            return loaded;
        }
        final List<String> names = new ArrayList<>();
        for (String file : FILES) {
            try (InputStream in = Typography.class.getResourceAsStream(BASE + file)) {
                if (in == null) {
                    LOG.log(Level.WARNING, "Bundled font missing: {0}", file);
                    continue;
                }
                final Font font = Font.loadFont(in, 14);
                if (font == null) {
                    LOG.log(Level.WARNING, "Bundled font could not be loaded: {0}", file);
                } else {
                    names.add(font.getName());
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Bundled font unreadable: " + file, e);
            }
        }
        loaded = List.copyOf(names);
        return loaded;
    }

    /** @return registered font names (empty before {@link #load()}) */
    public static List<String> loadedNames() {
        return loaded == null ? List.of() : loaded;
    }
}
