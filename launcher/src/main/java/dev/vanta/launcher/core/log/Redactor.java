package dev.vanta.launcher.core.log;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

/**
 * Removes secrets from text before it reaches a log file, the console or the UI.
 *
 * <p>Two mechanisms: explicitly registered secrets (access tokens, refresh tokens, device codes) are replaced
 * verbatim, and well-known shapes (command line {@code --accessToken X}, JSON {@code "access_token": "X"},
 * {@code Bearer X}, {@code XBL3.0 x=...}) are masked even when nobody registered the value.</p>
 */
public final class Redactor {

    /** Replacement text. */
    public static final String MASK = "[redacted]";

    private static final Redactor GLOBAL = new Redactor();

    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("(--accessToken\\s+)(\\S+)"),
        Pattern.compile("(--refreshToken\\s+)(\\S+)"),
        Pattern.compile("(\"(?:access_token|refresh_token|id_token|device_code|Token|RpsTicket|identityToken|accessToken|refreshToken)\"\\s*:\\s*\")([^\"]+)(\")"),
        Pattern.compile("(Bearer\\s+)([A-Za-z0-9._\\-+/=]+)"),
        Pattern.compile("(XBL3\\.0 x=)([^\\s\"']+)"),
        Pattern.compile("(d=)(eyJ[A-Za-z0-9._\\-]+)"),
        Pattern.compile("(?<![A-Za-z0-9._\\-])(eyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,})")
    );

    private final Set<String> secrets = new CopyOnWriteArraySet<>();

    /** @return the process-wide redactor used by the logging setup */
    public static Redactor global() {
        return GLOBAL;
    }

    /**
     * Registers a secret value to be masked. Blank and very short values are ignored to avoid masking normal text.
     *
     * @param secret value
     */
    public void register(final String secret) {
        if (secret != null && secret.trim().length() >= 4) {
            secrets.add(secret);
        }
    }

    /**
     * Forgets a secret.
     *
     * @param secret value
     */
    public void unregister(final String secret) {
        if (secret != null) {
            secrets.remove(secret);
        }
    }

    /**
     * Masks all secrets in the text.
     *
     * @param text input (may be {@code null})
     * @return masked text
     */
    public String apply(final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (String s : secrets) {
            out = out.replace(s, MASK);
        }
        for (Pattern p : PATTERNS) {
            out = p.matcher(out).replaceAll(m -> {
                final StringBuilder sb = new StringBuilder();
                if (m.groupCount() >= 2) {
                    sb.append(java.util.regex.Matcher.quoteReplacement(m.group(1))).append(MASK);
                    if (m.groupCount() >= 3 && m.group(3) != null) {
                        sb.append(java.util.regex.Matcher.quoteReplacement(m.group(3)));
                    }
                } else {
                    sb.append(MASK);
                }
                return sb.toString();
            });
        }
        return out;
    }

    /**
     * Masks secrets in every element of a command line.
     *
     * @param command command tokens
     * @return masked copy
     */
    public List<String> apply(final List<String> command) {
        Objects.requireNonNull(command, "command");
        final String joined = apply(String.join("\u0000", command));
        return List.of(joined.split("\u0000", -1));
    }

    /** @return whether at least one secret is registered */
    public boolean hasSecrets() {
        return !secrets.isEmpty();
    }
}
