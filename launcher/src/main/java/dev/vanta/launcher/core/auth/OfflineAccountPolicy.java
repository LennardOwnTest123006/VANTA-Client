package dev.vanta.launcher.core.auth;

import dev.vanta.launcher.core.settings.LauncherSettings;

import java.util.Map;
import java.util.Optional;

/**
 * Rules for offline sessions. VANTA is a legitimate client: offline play is only offered
 *
 * <ul>
 *   <li>as an "offline session" of a Microsoft account that has signed in successfully before (its verified profile
 *       name and UUID are reused, like Prism Launcher does), or</li>
 *   <li>for development, when {@code developerMode} is enabled in the settings <em>and</em> the environment variable
 *       {@code VANTA_DEV_OFFLINE=1} is set (CI integration tests).</li>
 * </ul>
 */
public final class OfflineAccountPolicy {

    /** Environment variable enabling development offline accounts. */
    public static final String DEV_OFFLINE_ENV = "VANTA_DEV_OFFLINE";

    private OfflineAccountPolicy() {
    }

    /**
     * @param settings settings
     * @param env      environment variables
     * @return whether development offline accounts are enabled
     */
    public static boolean developmentOfflineEnabled(final LauncherSettings settings, final Map<String, String> env) {
        return settings.developerMode() && "1".equals(env.get(DEV_OFFLINE_ENV));
    }

    /**
     * @param settings settings
     * @param env      environment
     * @param store    account store
     * @return whether any offline session may be created
     */
    public static boolean offlineAllowed(final LauncherSettings settings, final Map<String, String> env, final AccountStore store) {
        return developmentOfflineEnabled(settings, env) || store.hasVerifiedMicrosoftAccount();
    }

    /**
     * Creates an offline account according to the policy.
     *
     * @param settings      settings
     * @param env           environment
     * @param store         account store
     * @param requestedName name requested by the user (used only for development accounts)
     * @return offline account
     * @throws AuthException when the policy forbids offline play
     */
    public static Account createOffline(final LauncherSettings settings, final Map<String, String> env, final AccountStore store,
                                        final String requestedName) throws AuthException {
        final Optional<Account> verified = store.verifiedMicrosoftAccount();
        if (verified.isPresent()) {
            final Account v = verified.get();
            return new Account(v.uuid(), v.name(), v.xuid(), Account.OFFLINE_TOKEN, Long.MAX_VALUE, "", AccountType.OFFLINE);
        }
        if (developmentOfflineEnabled(settings, env)) {
            final String name = requestedName == null || requestedName.isBlank() ? "Dev" : requestedName.trim();
            if (!name.matches("[A-Za-z0-9_]{1,16}")) {
                throw new AuthException("Player names may only contain letters, digits and underscores (1-16 characters).");
            }
            return new Account(Account.offlineUuid(name), name, "", Account.OFFLINE_TOKEN, Long.MAX_VALUE, "", AccountType.DEVELOPMENT);
        }
        throw new AuthException("Offline play requires a Microsoft account that has signed in successfully on this computer at least once.");
    }
}
