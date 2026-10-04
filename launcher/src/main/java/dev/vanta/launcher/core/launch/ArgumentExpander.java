package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.Argument;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code ${placeholder}} tokens in version JSON arguments.
 *
 * <p>Unknown placeholders are left untouched and collected in {@link #unresolved()} so the caller can decide
 * whether to fail (the launcher does for game/JVM arguments it does not understand).</p>
 */
public final class ArgumentExpander {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.\\-]+)}");

    private final Map<String, String> values;
    private final Set<String> unresolved = new LinkedHashSet<>();

    /**
     * @param values placeholder values
     */
    public ArgumentExpander(final Map<String, String> values) {
        this.values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }

    /**
     * Expands a single token.
     *
     * @param token token
     * @return expanded token
     */
    public String expand(final String token) {
        final Matcher m = PLACEHOLDER.matcher(token);
        final StringBuilder sb = new StringBuilder();
        while (m.find()) {
            final String key = m.group(1);
            final String value = values.get(key);
            if (value == null) {
                unresolved.add(key);
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Filters arguments by rules, flattens their values and expands placeholders.
     *
     * @param arguments arguments
     * @param evaluator rule evaluator
     * @return expanded tokens
     */
    public List<String> expandAll(final List<Argument> arguments, final RuleEvaluator evaluator) {
        final List<String> out = new ArrayList<>();
        for (Argument arg : arguments) {
            if (!evaluator.allows(arg.rules())) {
                continue;
            }
            for (String v : arg.values()) {
                out.add(expand(v));
            }
        }
        return out;
    }

    /**
     * Expands a list of plain tokens.
     *
     * @param tokens tokens
     * @return expanded tokens
     */
    public List<String> expandTokens(final List<String> tokens) {
        final List<String> out = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            out.add(expand(t));
        }
        return out;
    }

    /** @return placeholders seen so far that had no value */
    public Set<String> unresolved() {
        return Set.copyOf(unresolved);
    }

    /** @return the placeholder values */
    public Map<String, String> values() {
        return values;
    }
}
