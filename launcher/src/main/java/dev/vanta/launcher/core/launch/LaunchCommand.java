package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.log.Redactor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A fully assembled game command line.
 *
 * @param command          executable followed by all arguments
 * @param workingDirectory process working directory (the instance directory)
 * @param environment      extra environment variables
 * @param secrets          values that must never be printed (access tokens)
 */
public record LaunchCommand(List<String> command, Path workingDirectory, Map<String, String> environment, Set<String> secrets) {

    public LaunchCommand {
        command = List.copyOf(Objects.requireNonNull(command, "command"));
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        secrets = secrets == null ? Set.of() : Set.copyOf(secrets);
    }

    /** @return the command with secrets masked, safe for logs and the UI */
    public List<String> redacted() {
        final Redactor redactor = new Redactor();
        secrets.forEach(redactor::register);
        return redactor.apply(command);
    }

    /** @return a shell-like single line representation with secrets masked */
    public String toDisplayString() {
        final StringBuilder sb = new StringBuilder();
        for (String token : redacted()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(quoteIfNeeded(token));
        }
        return sb.toString();
    }

    private static String quoteIfNeeded(final String token) {
        if (token.isEmpty() || token.chars().anyMatch(c -> Character.isWhitespace(c) || c == '"' || c == '\'')) {
            return '"' + token.replace("\"", "\\\"") + '"';
        }
        return token;
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
