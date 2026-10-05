package dev.vanta.launcher.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parsed command line.
 *
 * @param command the command (defaults to {@link CliCommand#HELP})
 * @param options option values keyed by flag (without leading dashes)
 * @param flags   boolean flags present (without leading dashes)
 * @param errors  parse errors (empty when valid)
 */
public record CliArgs(CliCommand command, Map<String, String> options, Set<String> flags, List<String> errors) {

    /** Options that take a value. */
    public static final Set<String> VALUE_OPTIONS = Set.of("client-jar", "username", "data-dir", "java", "memory", "releases-url",
        "world", "server", "exit-after", "resolution", "minecraft-dir");
    /** Boolean flags. */
    public static final Set<String> BOOLEAN_FLAGS = Set.of("dev-offline", "without-client", "no-assets", "verbose", "ui");

    public CliArgs {
        options = options == null ? Map.of() : Map.copyOf(options);
        flags = flags == null ? Set.of() : Set.copyOf(flags);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Parses arguments. Exactly one command flag is expected; options may appear in any order and accept both
     * {@code --name value} and {@code --name=value}.
     *
     * @param args raw arguments
     * @return parsed arguments (check {@link #isValid()})
     */
    public static CliArgs parse(final String[] args) {
        CliCommand command = null;
        final Map<String, String> options = new LinkedHashMap<>();
        final Set<String> flags = new java.util.LinkedHashSet<>();
        final List<String> errors = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            if (!arg.startsWith("-")) {
                errors.add("Unexpected argument '" + arg + "'");
                continue;
            }
            String name = arg;
            String inlineValue = null;
            final int eq = arg.indexOf('=');
            if (eq > 0) {
                name = arg.substring(0, eq);
                inlineValue = arg.substring(eq + 1);
            }
            final Optional<CliCommand> cmd = CliCommand.fromFlag(name);
            if (cmd.isPresent()) {
                if (command != null && command != cmd.get()) {
                    errors.add("Only one command may be given (" + command.flag() + " and " + cmd.get().flag() + ")");
                }
                command = cmd.get();
                continue;
            }
            final String key = name.replaceFirst("^-+", "");
            if (VALUE_OPTIONS.contains(key)) {
                String value = inlineValue;
                if (value == null) {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        errors.add("Option --" + key + " requires a value");
                        continue;
                    }
                    value = args[++i];
                }
                options.put(key, value);
            } else if (BOOLEAN_FLAGS.contains(key)) {
                if (inlineValue != null) {
                    errors.add("Flag --" + key + " does not take a value");
                }
                flags.add(key);
            } else {
                errors.add("Unknown option '" + arg + "'");
            }
        }
        if (command == null) {
            if (!flags.isEmpty() || !options.isEmpty()) {
                errors.add("No command given (expected one of " + commandFlags() + ")");
            }
            command = CliCommand.HELP;
        }
        return new CliArgs(command, options, flags, errors);
    }

    /** @return whether parsing produced no errors */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * @param name option name without dashes
     * @return value when present
     */
    public Optional<String> option(final String name) {
        return Optional.ofNullable(options.get(name)).filter(v -> !v.isBlank());
    }

    /**
     * @param name flag name without dashes
     * @return whether the flag is present
     */
    public boolean has(final String name) {
        return flags.contains(name);
    }

    private static String commandFlags() {
        final StringBuilder sb = new StringBuilder();
        for (CliCommand c : CliCommand.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(c.flag());
        }
        return sb.toString();
    }
}
