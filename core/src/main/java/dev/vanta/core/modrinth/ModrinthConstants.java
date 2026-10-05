package dev.vanta.core.modrinth;

import dev.vanta.core.VantaVersion;
import dev.vanta.core.screen.VantaLinks;
import java.net.URI;

/**
 * Fixed facts about the Modrinth API that VANTA relies on.
 */
public final class ModrinthConstants {
    /** Base of the public API, version 2 (ends with a slash so relative paths resolve below it). */
    public static final URI API_BASE = URI.create("https://api.modrinth.com/v2/");
    /** The Minecraft version every search, version lookup and install is restricted to. */
    public static final String GAME_VERSION = VantaVersion.MINECRAFT;
    /** Modrinth project id of Fabric API (slug {@code fabric-api}); VANTA itself requires Fabric API. */
    public static final String FABRIC_API_PROJECT_ID = "P7dR8mSH";
    /** Modrinth slug of Fabric API. */
    public static final String FABRIC_API_SLUG = "fabric-api";
    /** Fabric mod id of Fabric API. */
    public static final String FABRIC_API_MOD_ID = "fabric-api";
    /** Fabric mod id of Iris. */
    public static final String IRIS_MOD_ID = "iris";

    private ModrinthConstants() {
    }

    /**
     * The User-Agent Modrinth asks API clients to send: it names the project, its version and a contact page.
     *
     * @param clientVersion the VANTA Client version
     */
    public static String userAgent(String clientVersion) {
        return "LennardOwnTest123006/VANTA-Client/" + clientVersion + " (" + VantaLinks.REPOSITORY + ")";
    }

    /** The User-Agent for this build. */
    public static String userAgent() {
        return userAgent(VantaVersion.CLIENT);
    }
}
