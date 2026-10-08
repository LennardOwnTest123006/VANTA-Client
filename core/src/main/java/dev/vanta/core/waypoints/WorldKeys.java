package dev.vanta.core.waypoints;

import dev.vanta.core.bridge.GameBridge;
import dev.vanta.core.i18n.Lang;
import java.util.Locale;
import java.util.Optional;

/**
 * The key that ties waypoints to a world: {@code sp:<level folder name>} for a singleplayer level,
 * {@code mp:<server address>} on a server (lower-case, trimmed) and {@link #NONE} outside a world or when the game
 * cannot say which world is open. Waypoints are never stored under {@link #NONE}.
 */
public final class WorldKeys {
    /** Outside a world, or the world cannot be identified. */
    public static final String NONE = "none";
    /** Prefix of singleplayer keys. */
    public static final String SINGLEPLAYER_PREFIX = "sp:";
    /** Prefix of multiplayer keys. */
    public static final String MULTIPLAYER_PREFIX = "mp:";

    private WorldKeys() {
    }

    /** Key of a singleplayer level by its folder name; {@link #NONE} when blank. */
    public static String singleplayer(String levelFolderName) {
        String name = levelFolderName == null ? "" : levelFolderName.strip();
        return name.isEmpty() ? NONE : SINGLEPLAYER_PREFIX + name;
    }

    /** Key of a server by its address ({@code host[:port]}, lower-cased); {@link #NONE} when blank. */
    public static String multiplayer(String serverAddress) {
        String address = serverAddress == null ? "" : serverAddress.strip().toLowerCase(Locale.ROOT);
        return address.isEmpty() ? NONE : MULTIPLAYER_PREFIX + address;
    }

    /**
     * Derives the key from what the game reports. A singleplayer world without a known level name and a server
     * without an address both yield {@link #NONE}: better no waypoints than waypoints under a wrong world.
     */
    public static String derive(boolean inWorld, boolean singleplayer, Optional<String> levelFolderName,
                                Optional<String> serverAddress) {
        if (!inWorld) {
            return NONE;
        }
        if (singleplayer) {
            return singleplayer(levelFolderName.orElse(""));
        }
        return multiplayer(serverAddress.orElse(""));
    }

    /** {@link #derive} fed from the bridge (render thread only, like every bridge call). */
    public static String forGame(GameBridge game) {
        return derive(game.isInWorld(), game.isSingleplayer(), game.singleplayerLevelName(), game.serverAddress());
    }

    /** True for {@link #NONE}, null and blank keys. */
    public static boolean isNone(String key) {
        return key == null || key.isBlank() || NONE.equals(key);
    }

    /** True for {@code sp:} keys with a name. */
    public static boolean isSingleplayer(String key) {
        return key != null && key.startsWith(SINGLEPLAYER_PREFIX) && key.length() > SINGLEPLAYER_PREFIX.length();
    }

    /** True for {@code mp:} keys with an address. */
    public static boolean isMultiplayer(String key) {
        return key != null && key.startsWith(MULTIPLAYER_PREFIX) && key.length() > MULTIPLAYER_PREFIX.length();
    }

    /** The level name or server address without the prefix; empty string for {@link #NONE}. */
    public static String bareName(String key) {
        if (isSingleplayer(key)) {
            return key.substring(SINGLEPLAYER_PREFIX.length());
        }
        if (isMultiplayer(key)) {
            return key.substring(MULTIPLAYER_PREFIX.length());
        }
        return "";
    }

    /** Translated label for lists: "Singleplayer: <level>", "Server: <address>" or "No world". */
    public static String displayName(String key) {
        if (isSingleplayer(key)) {
            return Lang.tr("vanta.waypoints.world.singleplayer", bareName(key));
        }
        if (isMultiplayer(key)) {
            return Lang.tr("vanta.waypoints.world.multiplayer", bareName(key));
        }
        return Lang.tr("vanta.waypoints.world.none");
    }
}
