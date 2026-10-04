package dev.vanta.launcher.core.auth;

/**
 * How an account was created.
 */
public enum AccountType {
    /** Verified through Microsoft / Xbox Live / Minecraft services. */
    MICROSOFT,
    /** Offline session that reuses the profile of a verified Microsoft account (no multiplayer authentication). */
    OFFLINE,
    /** Development-only offline account (requires developer mode and {@code VANTA_DEV_OFFLINE=1}). */
    DEVELOPMENT
}
