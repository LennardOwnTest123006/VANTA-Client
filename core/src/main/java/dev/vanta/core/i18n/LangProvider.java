package dev.vanta.core.i18n;

import java.util.Optional;

/**
 * Source of translated strings. {@code core} ships {@link JsonLangProvider}; the Fabric client installs a provider that
 * delegates to Minecraft's {@code I18n} so resource packs and the vanilla language setting apply to VANTA strings too.
 */
public interface LangProvider {
    /**
     * @param key translation key, e.g. {@code vanta.menu.play}
     * @return the raw (unformatted) translation, or empty when the key is unknown
     */
    Optional<String> lookup(String key);

    /** True when the key has a translation. */
    default boolean has(String key) {
        return lookup(key).isPresent();
    }
}
