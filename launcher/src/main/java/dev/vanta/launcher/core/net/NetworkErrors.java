package dev.vanta.launcher.core.net;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Classifies transport failures. The JDK HTTP client reports most connection problems with dedicated exception
 * types, but a refused HTTPS proxy tunnel ({@code CONNECT} answered with 403/407/502...) surfaces as a plain
 * {@link IOException} ("Tunnel failed, got: 403"), so messages are inspected as well. The cause chain is walked
 * because clients and retry loops wrap the original exception.
 */
public final class NetworkErrors {

    /** Kind of network failure. */
    public enum Kind {
        /** The server answered with an unexpected HTTP status. */
        HTTP_STATUS,
        /** A proxy refused the HTTPS tunnel. */
        PROXY,
        /** TLS handshake or certificate failure. */
        TLS,
        /** The host name could not be resolved. */
        UNKNOWN_HOST,
        /** Connection refused / no route. */
        CONNECT,
        /** Connect or read timeout. */
        TIMEOUT,
        /** Connection reset or closed. */
        SOCKET
    }

    private static final int MAX_DEPTH = 10;
    private static final Pattern PROXY_MESSAGE = Pattern.compile(
        "tunnel failed|unable to tunnel|proxy returns|proxy authentication required|http connect|proxy refused", Pattern.CASE_INSENSITIVE);

    private NetworkErrors() {
    }

    /**
     * @param error any throwable
     * @return whether it (or one of its causes) is a network/transport failure
     */
    public static boolean isNetworkFailure(final Throwable error) {
        return classify(error).isPresent();
    }

    /**
     * @param error any throwable
     * @return the kind of the network failure in the cause chain (see {@link #find})
     */
    public static Optional<Kind> classify(final Throwable error) {
        return find(error).flatMap(NetworkErrors::classifyOne);
    }

    /**
     * Finds the throwable that describes the network failure: the first one in the cause chain with a dedicated type
     * (connect, TLS, timeout, HTTP status ...), else the innermost one whose message reports a refused proxy tunnel
     * (wrappers often repeat the message of their cause).
     *
     * @param error any throwable
     * @return the network failure, when there is one
     */
    public static Optional<Throwable> find(final Throwable error) {
        Throwable byMessage = null;
        Throwable t = error;
        for (int depth = 0; t != null && depth < MAX_DEPTH; depth++) {
            if (classifyByType(t).isPresent()) {
                return Optional.of(t);
            }
            if (isProxyMessage(t)) {
                byMessage = t;
            }
            if (t.getCause() == t) {
                break;
            }
            t = t.getCause();
        }
        return Optional.ofNullable(byMessage);
    }

    /**
     * One-line English explanation of a network failure for logs and the command line.
     *
     * @param error any throwable
     * @return explanation (the plain message when it is not a network failure)
     */
    public static String describe(final Throwable error) {
        final Optional<Throwable> found = find(error);
        final Throwable t = found.orElse(error);
        final String message = t == null || t.getMessage() == null || t.getMessage().isBlank()
            ? (t == null ? "unknown error" : t.getClass().getSimpleName()) : t.getMessage().trim();
        if (found.isEmpty()) {
            return message;
        }
        return switch (classifyOne(t).orElseThrow()) {
            case HTTP_STATUS -> message;
            case PROXY -> "the HTTPS proxy refused the connection (" + message + ")";
            case TLS -> "the secure (TLS) connection failed (" + message + ")";
            case UNKNOWN_HOST -> "unknown host " + message;
            case CONNECT -> "the connection failed (" + message + ")";
            case TIMEOUT -> "the connection timed out (" + message + ")";
            case SOCKET -> "the connection was interrupted (" + message + ")";
        };
    }

    private static Optional<Kind> classifyOne(final Throwable t) {
        final Optional<Kind> byType = classifyByType(t);
        if (byType.isPresent()) {
            return byType;
        }
        return isProxyMessage(t) ? Optional.of(Kind.PROXY) : Optional.empty();
    }

    private static Optional<Kind> classifyByType(final Throwable t) {
        if (t instanceof HttpStatusException) {
            return Optional.of(Kind.HTTP_STATUS);
        }
        if (t instanceof SSLException) {
            return Optional.of(Kind.TLS);
        }
        if (t instanceof UnknownHostException) {
            return Optional.of(Kind.UNKNOWN_HOST);
        }
        if (t instanceof HttpConnectTimeoutException || t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
            return Optional.of(Kind.TIMEOUT);
        }
        if (t instanceof ConnectException || t instanceof NoRouteToHostException) {
            return Optional.of(isProxyMessage(t) ? Kind.PROXY : Kind.CONNECT);
        }
        if (t instanceof SocketException) {
            return Optional.of(Kind.SOCKET);
        }
        return Optional.empty();
    }

    private static boolean isProxyMessage(final Throwable t) {
        return t instanceof IOException && t.getMessage() != null && PROXY_MESSAGE.matcher(t.getMessage()).find();
    }
}
