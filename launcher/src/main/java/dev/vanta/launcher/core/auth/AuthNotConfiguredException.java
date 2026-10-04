package dev.vanta.launcher.core.auth;

import dev.vanta.launcher.core.settings.LauncherSettings;

/**
 * No Microsoft application (client) id is configured, so the device code flow cannot start.
 */
public final class AuthNotConfiguredException extends AuthException {

    private static final long serialVersionUID = 1L;

    /** Message explaining how to configure sign-in. */
    public static final String MESSAGE = "Microsoft sign-in is not configured. VANTA needs an Azure application (client) id "
        + "that has been approved by Mojang for Minecraft authentication. Register an application in the Microsoft Entra "
        + "admin center (public client, 'Personal Microsoft accounts', device code flow enabled), request access to the "
        + "Minecraft API, then set \"msClientId\" in the launcher settings or the " + LauncherSettings.MS_CLIENT_ID_ENV
        + " environment variable.";

    /** Creates the exception with the standard message. */
    public AuthNotConfiguredException() {
        super(MESSAGE);
    }
}
