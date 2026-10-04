package dev.vanta.client.lang;

import dev.vanta.core.i18n.JsonLangProvider;
import dev.vanta.core.i18n.LangProvider;
import java.util.Optional;
import net.minecraft.locale.Language;

/**
 * {@link LangProvider} backed by the game's active language.
 * <p>
 * Minecraft loads {@code assets/vanta/lang/<code>.json} from the mod jar (and from resource packs) into
 * {@link Language}, so VANTA strings follow the vanilla language setting and can be overridden by packs. The raw
 * (unformatted) pattern is returned because {@code Lang.tr(key, args)} formats it itself; {@code I18n.get} would
 * already have applied {@code String.format} and cannot be used for parameterised strings.
 * <p>
 * Before the first resource reload only the vanilla language file is loaded, so every key missing from the active
 * language falls back to the English bundle shipped inside {@code core}.
 */
public final class MinecraftLangProvider implements LangProvider {
    private final LangProvider fallback = JsonLangProvider.builtIn();

    @Override
    public Optional<String> lookup(String key) {
        Language language = Language.getInstance();
        if (language != null && language.has(key)) {
            return Optional.of(language.getOrDefault(key));
        }
        return fallback.lookup(key);
    }
}
