package dev.vanta.launcher.core.install;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The official Minecraft Launcher has not been set up in the given directory: neither {@code launcher_profiles.json}
 * (launcher from minecraft.net) nor {@code launcher_profiles_microsoft_store.json} (launcher from the Microsoft Store /
 * Xbox app) exists. The Minecraft Launcher creates its file the first time it starts; VANTA never creates one.
 */
public final class OfficialLauncherNotFoundException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient Path minecraftDir;

    /**
     * @param minecraftDir the directory that was checked
     */
    public OfficialLauncherNotFoundException(final Path minecraftDir) {
        super("No launcher_profiles.json or launcher_profiles_microsoft_store.json in " + minecraftDir
            + ". Start the Minecraft Launcher once, then try again"
            + " (or pass --minecraft-dir <path> if your Minecraft folder is somewhere else).");
        this.minecraftDir = minecraftDir;
    }

    /** @return the directory that was checked */
    public Path minecraftDir() {
        return minecraftDir;
    }
}
