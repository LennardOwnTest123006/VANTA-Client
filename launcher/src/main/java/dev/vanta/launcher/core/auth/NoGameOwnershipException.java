package dev.vanta.launcher.core.auth;

/**
 * The Microsoft account signed in successfully but does not own Minecraft: Java Edition.
 */
public final class NoGameOwnershipException extends AuthException {

    private static final long serialVersionUID = 1L;

    /**
     * @param accountHint something identifying the account (e.g. Xbox gamertag or "this account")
     */
    public NoGameOwnershipException(final String accountHint) {
        super(accountHint + " does not own Minecraft: Java Edition. Buy the game on minecraft.net or sign in with the "
            + "Microsoft account that owns it.");
    }
}
