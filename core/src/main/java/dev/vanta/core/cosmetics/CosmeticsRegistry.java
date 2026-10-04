package dev.vanta.core.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.core.config.CoreLog;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Catalogue of every cosmetic: built-in UI themes (from {@code /vanta/presets/themes/}), themes from installed
 * {@link CosmeticPack}s, and the fixed enums for backgrounds, particles, HUD themes and badges.
 * All built-ins are owned by every player; cosmetics never affect gameplay.
 */
public final class CosmeticsRegistry {
    /** Built-in theme ids in display order. */
    public static final List<String> BUILT_IN_THEME_IDS = List.of("vanta-dark", "midnight", "graphite", "aurora",
            "ember");

    private static final String RESOURCE_DIR = "/vanta/presets/themes/";

    private final Map<String, UiThemeDefinition> themes = new LinkedHashMap<>();
    private final Map<String, CosmeticPack> packs = new LinkedHashMap<>();

    /** Registry with the built-in themes loaded. */
    public CosmeticsRegistry() {
        for (String id : BUILT_IN_THEME_IDS) {
            UiThemeDefinition theme = loadBuiltIn(id);
            themes.put(theme.id(), theme);
        }
    }

    /** All themes: built-ins first, then pack themes. */
    public List<UiThemeDefinition> themes() {
        return Collections.unmodifiableList(new ArrayList<>(themes.values()));
    }

    /** Finds a theme by id. */
    public Optional<UiThemeDefinition> findTheme(String id) {
        return Optional.ofNullable(themes.get(id));
    }

    /** The theme for an id, falling back to the default theme. */
    public UiThemeDefinition themeOrDefault(String id) {
        UiThemeDefinition theme = themes.get(id);
        return theme != null ? theme : themes.get(UiThemeDefinition.DEFAULT_ID);
    }

    /** All backgrounds. */
    public List<MenuBackground> backgrounds() {
        return List.of(MenuBackground.values());
    }

    /** All particle kinds. */
    public List<MenuParticles> particles() {
        return List.of(MenuParticles.values());
    }

    /** All HUD themes. */
    public List<HudTheme> hudThemes() {
        return List.of(HudTheme.values());
    }

    /** All badges. */
    public List<Badge> badges() {
        return List.of(Badge.values());
    }

    /** Installed packs. */
    public List<CosmeticPack> packs() {
        return Collections.unmodifiableList(new ArrayList<>(packs.values()));
    }

    /**
     * Adds a pack. Themes whose id clashes with an existing theme are skipped with a warning.
     *
     * @return number of themes added
     */
    public int addPack(CosmeticPack pack) {
        Objects.requireNonNull(pack, "pack");
        if (packs.containsKey(pack.id())) {
            CoreLog.warn("Cosmetic pack {} is already installed; skipping duplicate", pack.id());
            return 0;
        }
        int added = 0;
        for (UiThemeDefinition theme : pack.themes()) {
            if (themes.containsKey(theme.id())) {
                CoreLog.warn("Cosmetic pack {} defines theme {} which already exists; skipping", pack.id(),
                        theme.id());
                continue;
            }
            themes.put(theme.id(), theme);
            added++;
        }
        packs.put(pack.id(), pack);
        return added;
    }

    /** Removes every pack and its themes. */
    public void clearPacks() {
        for (CosmeticPack pack : packs.values()) {
            for (UiThemeDefinition theme : pack.themes()) {
                if (!theme.builtIn()) {
                    themes.remove(theme.id());
                }
            }
        }
        packs.clear();
    }

    /** Parses a pack from JSON text. */
    public static CosmeticPack parsePack(Reader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        return CosmeticPack.fromJson(json);
    }

    private static UiThemeDefinition loadBuiltIn(String id) {
        String resource = RESOURCE_DIR + id + ".json";
        InputStream in = CosmeticsRegistry.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing theme resource " + resource);
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return UiThemeDefinition.fromJson(JsonParser.parseReader(reader).getAsJsonObject(), true);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read theme " + resource, e);
        }
    }
}
