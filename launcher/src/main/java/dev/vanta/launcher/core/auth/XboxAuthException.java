package dev.vanta.launcher.core.auth;

/**
 * Xbox Live / XSTS refused the account. The {@code XErr} code is mapped to an actionable message.
 */
public final class XboxAuthException extends AuthException {

    private static final long serialVersionUID = 1L;

    /** The account has no Xbox profile. */
    public static final long XERR_NO_XBOX_ACCOUNT = 2148916233L;
    /** Xbox Live is not available in the account's region. */
    public static final long XERR_REGION_UNAVAILABLE = 2148916235L;
    /** The account needs adult verification (South Korea). */
    public static final long XERR_ADULT_VERIFICATION = 2148916236L;
    /** The account needs adult verification (South Korea, variant). */
    public static final long XERR_ADULT_VERIFICATION_2 = 2148916237L;
    /** The account is a child and must be added to a family. */
    public static final long XERR_CHILD_ACCOUNT = 2148916238L;

    private final long xerr;

    /**
     * @param xerr XErr code (0 when unknown)
     * @param raw  raw response for diagnostics (not shown to the user)
     */
    public XboxAuthException(final long xerr, final String raw) {
        super(messageFor(xerr, raw));
        this.xerr = xerr;
    }

    /** @return XErr code */
    public long xerr() {
        return xerr;
    }

    /**
     * @param xerr XErr code
     * @param raw  raw response
     * @return user facing message
     */
    public static String messageFor(final long xerr, final String raw) {
        if (xerr == XERR_NO_XBOX_ACCOUNT) {
            return "This Microsoft account has no Xbox profile. Sign in once at xbox.com to create one, then try again.";
        }
        if (xerr == XERR_REGION_UNAVAILABLE) {
            return "Xbox Live is not available in your country or region, so Minecraft cannot sign you in.";
        }
        if (xerr == XERR_ADULT_VERIFICATION || xerr == XERR_ADULT_VERIFICATION_2) {
            return "This account requires adult verification on xbox.com before it can be used.";
        }
        if (xerr == XERR_CHILD_ACCOUNT) {
            return "This is a child account. A parent or guardian has to add it to a Microsoft family group before it can play.";
        }
        return "Xbox Live refused the sign-in" + (xerr != 0 ? " (XErr " + xerr + ")" : "")
            + (raw == null || raw.isBlank() ? "." : ": " + raw);
    }
}
