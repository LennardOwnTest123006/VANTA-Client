package dev.vanta.core.worlds;

import dev.vanta.core.i18n.LangKeyed;
import java.util.Locale;

/** Which saves folder Singleplayer uses (the "Singleplayer worlds" setting). */
public enum WorldsFolderMode implements LangKeyed {
    /** The official Minecraft folder's {@code saves/} when it exists, so the player's existing worlds are listed. */
    MINECRAFT_FOLDER,
    /** The VANTA game folder's own {@code saves/}, separate from the official Minecraft folder. */
    VANTA_FOLDER;

    @Override
    public String langKey() {
        return "vanta.worlds.mode." + name().toLowerCase(Locale.ROOT);
    }
}
