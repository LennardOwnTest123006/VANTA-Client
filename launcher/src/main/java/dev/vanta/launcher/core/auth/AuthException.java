package dev.vanta.launcher.core.auth;

/**
 * Sign-in failed. Subclasses describe actionable causes; the message is safe to show to the user.
 */
public class AuthException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param message user facing message
     */
    public AuthException(final String message) {
        super(message);
    }

    /**
     * @param message user facing message
     * @param cause   cause
     */
    public AuthException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
