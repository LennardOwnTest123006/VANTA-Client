package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.auth.AuthException;
import dev.vanta.launcher.core.auth.AuthNotConfiguredException;
import dev.vanta.launcher.core.auth.NoGameOwnershipException;
import dev.vanta.launcher.core.auth.XboxAuthException;
import dev.vanta.launcher.core.install.InstallException;
import dev.vanta.launcher.core.install.InsufficientDiskSpaceException;
import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.install.OfficialLauncherNotFoundException;
import dev.vanta.launcher.core.install.ReleasesNotConfiguredException;
import dev.vanta.launcher.core.java.UnsafeArchiveException;
import dev.vanta.launcher.core.net.HttpStatusException;
import dev.vanta.launcher.core.net.IntegrityException;
import dev.vanta.launcher.core.net.NetworkErrors;
import dev.vanta.launcher.ui.Messages;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Maps core exceptions to friendly, localised text. The core already produces safe messages; this class turns the
 * well-known types into the launcher's own wording and keeps technical detail where it helps.
 */
public final class ErrorMessages {

    private final Messages messages;
    private final Formats formats;

    /**
     * @param messages messages
     * @param formats  formats
     */
    public ErrorMessages(final Messages messages, final Formats formats) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.formats = Objects.requireNonNull(formats, "formats");
    }

    /**
     * @param error throwable
     * @return user facing description
     */
    public String describe(final Throwable error) {
        if (error == null) {
            return messages.format("error.unknown", "");
        }
        if ((error instanceof ExecutionException || error instanceof CompletionException) && error.getCause() != null) {
            return describe(error.getCause());
        }
        if (error instanceof InstallException install && !(error instanceof InsufficientDiskSpaceException)) {
            final Throwable cause = install.getCause();
            final String inner = cause == null ? plain(install.getMessage()) : describe(cause);
            return messages.format("error.install.step", install.step().label(), inner);
        }
        if (error instanceof AuthNotConfiguredException) {
            return messages.get("error.notConfigured");
        }
        if (error instanceof XboxAuthException xbox) {
            return xbox(xbox);
        }
        if (error instanceof NoGameOwnershipException) {
            return messages.get("error.auth.noGame");
        }
        if (error instanceof AuthException auth) {
            final String message = plain(auth.getMessage()).toLowerCase(Locale.ROOT);
            if (message.contains("declined")) {
                return messages.get("error.auth.declined");
            }
            if (message.contains("expired before it was used")) {
                return messages.get("error.auth.expired");
            }
            return messages.format("error.auth.generic", plain(auth.getMessage()));
        }
        if (error instanceof InsufficientDiskSpaceException disk) {
            return messages.format("error.diskSpace", formats.bytes(disk.requiredBytes()), formats.bytes(disk.availableBytes()));
        }
        if (error instanceof OfficialLauncherNotFoundException notFound) {
            return messages.format("error.official.notFound", String.valueOf(notFound.minecraftDir()));
        }
        if (error instanceof ReleasesNotConfiguredException releases) {
            return releases.url().isEmpty() ? messages.get("error.releasesUrl.missing")
                : messages.format("error.releasesUrl.invalid", releases.url());
        }
        if (error instanceof NotPublishedException notPublished) {
            return messages.format("error.notPublished", plain(notPublished.getMessage()));
        }
        if (error instanceof IntegrityException) {
            return messages.get("error.integrity");
        }
        if (error instanceof UnsafeArchiveException) {
            return messages.get("error.unsafeArchive");
        }
        final Optional<NetworkErrors.Kind> network = NetworkErrors.classify(error);
        if (network.isPresent() && (network.get() == NetworkErrors.Kind.PROXY || network.get() == NetworkErrors.Kind.TLS)) {
            final String detail = NetworkErrors.find(error).map(Throwable::getMessage).map(ErrorMessages::plain).orElse("");
            return messages.format(network.get() == NetworkErrors.Kind.PROXY ? "error.network.proxy" : "error.network.tls", detail);
        }
        if (error instanceof HttpStatusException http) {
            return messages.format("error.network.status", Integer.toString(http.status()));
        }
        if (error instanceof UnknownHostException || error instanceof ConnectException || error instanceof NoRouteToHostException) {
            return messages.get("error.network.offline");
        }
        if (error instanceof HttpTimeoutException || error instanceof HttpConnectTimeoutException || error instanceof SocketTimeoutException) {
            return messages.get("error.network.timeout");
        }
        if (error instanceof SocketException) {
            return messages.format("error.network", plain(error.getMessage()));
        }
        if (error instanceof CancellationException) {
            return messages.get("error.cancelled");
        }
        if (error instanceof InterruptedException) {
            return messages.get("error.interrupted");
        }
        final String message = error.getMessage();
        return messages.format("error.unknown", message == null || message.isBlank() ? error.getClass().getSimpleName() : message);
    }

    private String xbox(final XboxAuthException xbox) {
        final long code = xbox.xerr();
        if (code == XboxAuthException.XERR_NO_XBOX_ACCOUNT) {
            return messages.get("error.auth.noXbox");
        }
        if (code == XboxAuthException.XERR_REGION_UNAVAILABLE) {
            return messages.get("error.auth.region");
        }
        if (code == XboxAuthException.XERR_ADULT_VERIFICATION || code == XboxAuthException.XERR_ADULT_VERIFICATION_2) {
            return messages.get("error.auth.adult");
        }
        if (code == XboxAuthException.XERR_CHILD_ACCOUNT) {
            return messages.get("error.auth.child");
        }
        final String suffix = code == 0 ? "" : " " + messages.format("error.auth.xbox.code", Long.toString(code));
        return messages.format("error.auth.xbox", suffix);
    }

    private static String plain(final String message) {
        return message == null ? "" : message.trim();
    }
}
