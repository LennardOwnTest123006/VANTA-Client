package dev.vanta.launcher.core.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A signed-in player.
 *
 * @param uuid         profile UUID (dashed)
 * @param name         profile name
 * @param xuid         Xbox user id (empty when unknown)
 * @param accessToken  Minecraft services access token (never logged)
 * @param expiresAt    expiry of the access token (epoch milliseconds)
 * @param refreshToken Microsoft refresh token (never logged; empty for offline accounts)
 * @param type         account type
 */
public record Account(String uuid, String name, String xuid, String accessToken, long expiresAt, String refreshToken,
                      AccountType type) {

    /** Access token value used for offline sessions. */
    public static final String OFFLINE_TOKEN = "0";

    public Account {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        xuid = xuid == null ? "" : xuid;
        accessToken = accessToken == null ? "" : accessToken;
        refreshToken = refreshToken == null ? "" : refreshToken;
        type = type == null ? AccountType.MICROSOFT : type;
    }

    /** @return whether the access token has expired (with a one minute safety margin) */
    public boolean isExpired(final Instant now) {
        return type == AccountType.MICROSOFT && now.plusSeconds(60).toEpochMilli() >= expiresAt;
    }

    /** @return whether this account has a refresh token */
    public boolean canRefresh() {
        return type == AccountType.MICROSOFT && !refreshToken.isEmpty();
    }

    /** @return {@code uuid} without dashes as the game expects for {@code --uuid} */
    public String undashedUuid() {
        return uuid.replace("-", "");
    }

    /** @return the {@code --userType} argument value */
    public String userType() {
        return type == AccountType.MICROSOFT ? "msa" : "legacy";
    }

    /**
     * Builds the offline-mode UUID for a player name the same way the vanilla server does for offline players.
     *
     * @param name player name
     * @return dashed UUID
     */
    public static String offlineUuid(final String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Inserts dashes into a 32-character UUID.
     *
     * @param undashed 32 hex characters
     * @return dashed UUID
     */
    public static String dashUuid(final String undashed) {
        final String s = undashed.replace("-", "");
        if (s.length() != 32) {
            throw new IllegalArgumentException("Not a UUID: " + undashed);
        }
        return s.substring(0, 8) + '-' + s.substring(8, 12) + '-' + s.substring(12, 16) + '-' + s.substring(16, 20) + '-' + s.substring(20);
    }

    /**
     * Copy with new tokens (after a refresh).
     *
     * @param newAccessToken  access token
     * @param newExpiresAt    expiry
     * @param newRefreshToken refresh token
     * @return copy
     */
    public Account withTokens(final String newAccessToken, final long newExpiresAt, final String newRefreshToken) {
        return new Account(uuid, name, xuid, newAccessToken, newExpiresAt, newRefreshToken, type);
    }

    /**
     * Copy with a different type.
     *
     * @param newType type
     * @return copy
     */
    public Account withType(final AccountType newType) {
        return new Account(uuid, name, xuid, accessToken, expiresAt, refreshToken, newType);
    }

    /** Safe representation without secrets. */
    @Override
    public String toString() {
        return "Account[" + name + ", " + uuid + ", " + type + "]";
    }
}
