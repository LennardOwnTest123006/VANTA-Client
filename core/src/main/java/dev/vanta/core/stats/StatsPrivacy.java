package dev.vanta.core.stats;

import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;

/**
 * What the statistics tracker is allowed to record. Everything stays on this computer regardless.
 *
 * @param enabled      record anything at all
 * @param trackServers remember server hostnames
 * @param trackWorlds  remember world names
 */
public record StatsPrivacy(boolean enabled, boolean trackServers, boolean trackWorlds) {
    /** Record everything (local only). */
    public static final StatsPrivacy ALL = new StatsPrivacy(true, true, true);
    /** Record nothing. */
    public static final StatsPrivacy NONE = new StatsPrivacy(false, false, false);

    /** Reads the flags from the settings. */
    public static StatsPrivacy fromSettings(SettingsStore settings) {
        return new StatsPrivacy(settings.get(VantaSettings.PRIVACY_STATS_ENABLED),
                settings.get(VantaSettings.PRIVACY_STATS_TRACK_SERVERS),
                settings.get(VantaSettings.PRIVACY_STATS_TRACK_WORLDS));
    }
}
