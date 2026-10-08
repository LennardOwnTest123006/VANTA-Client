package dev.vanta.core.keybinds;

import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.i18n.LangKeyed;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * VANTA's own key mappings. The client registers one {@code KeyMapping} per {@link Definition} in the
 * {@code key.categories.vanta} category; these constants are the shared ids and defaults.
 */
public final class VantaKeys {
    /** Translation key of the VANTA key category. */
    public static final String CATEGORY = "key.categories.vanta";

    public static final String OPEN_MENU = "key.vanta.open_menu";
    public static final String TOGGLE_HUD = "key.vanta.toggle_hud";
    public static final String HUD_EDITOR = "key.vanta.hud_editor";
    public static final String PERFORMANCE = "key.vanta.performance";
    public static final String ZOOM = "key.vanta.zoom";
    public static final String SCREENSHOT_HUD_FREE = "key.vanta.screenshot_hud_free";
    /** Opens Vanta Nexus (default N). */
    public static final String OPEN_NEXUS = "key.vanta.open_nexus";

    /** GLFW key code of Right Shift. */
    public static final int GLFW_RIGHT_SHIFT = 344;
    /** GLFW key code of C. */
    public static final int GLFW_C = 67;
    /** GLFW key code of N. */
    public static final int GLFW_N = 78;

    /**
     * One VANTA key mapping.
     *
     * @param id         translation key / mapping name
     * @param defaultKey default binding ({@link KeyRef#UNBOUND} for none)
     */
    public record Definition(String id, KeyRef defaultKey) implements LangKeyed {
        public Definition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(defaultKey, "defaultKey");
        }

        @Override
        public String langKey() {
            return id;
        }

        /** Translation key of the short description. */
        public String descriptionKey() {
            return id + ".description";
        }
    }

    private static final List<Definition> ALL = List.of(
            new Definition(OPEN_MENU, KeyRef.keyboard(GLFW_RIGHT_SHIFT, "key.keyboard.right.shift")),
            new Definition(TOGGLE_HUD, KeyRef.UNBOUND),
            new Definition(HUD_EDITOR, KeyRef.UNBOUND),
            new Definition(PERFORMANCE, KeyRef.UNBOUND),
            new Definition(ZOOM, KeyRef.keyboard(GLFW_C, "key.keyboard.c")),
            new Definition(SCREENSHOT_HUD_FREE, KeyRef.UNBOUND),
            new Definition(OPEN_NEXUS, KeyRef.keyboard(GLFW_N, "key.keyboard.n")));

    private VantaKeys() {
    }

    /** Every VANTA key mapping in display order. */
    public static List<Definition> all() {
        return ALL;
    }

    /** Finds a definition by id. */
    public static Optional<Definition> find(String id) {
        return ALL.stream().filter(d -> d.id().equals(id)).findFirst();
    }

    /** True for VANTA's own mapping ids. */
    public static boolean isVantaKey(String id) {
        return id != null && id.startsWith("key.vanta.");
    }
}
