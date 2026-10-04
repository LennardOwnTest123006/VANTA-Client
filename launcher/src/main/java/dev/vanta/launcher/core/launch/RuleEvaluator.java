package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.Rule;
import dev.vanta.launcher.core.util.OsInfo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates Mojang rules.
 *
 * <p>Semantics (identical to the official launcher): without rules everything is allowed; with rules the result
 * is the action of the <em>last</em> matching rule, and {@code disallow} when none matches. A rule matches when
 * its {@code os} constraint (name, arch, version regex) and all of its {@code features} agree with the context.</p>
 */
public final class RuleEvaluator {

    private final OsInfo os;
    private final Map<String, Boolean> features;

    /**
     * @param os       operating system
     * @param features feature flags ({@code is_demo_user}, {@code has_custom_resolution}, ...); missing = false
     */
    public RuleEvaluator(final OsInfo os, final Map<String, Boolean> features) {
        this.os = Objects.requireNonNull(os, "os");
        this.features = features == null ? Map.of() : Map.copyOf(features);
    }

    /**
     * Evaluator without features (library rules).
     *
     * @param os operating system
     */
    public RuleEvaluator(final OsInfo os) {
        this(os, Map.of());
    }

    /** @return the operating system */
    public OsInfo os() {
        return os;
    }

    /** @return the feature flags */
    public Map<String, Boolean> features() {
        return features;
    }

    /**
     * @param rules rules (may be empty or {@code null})
     * @return whether the rules allow
     */
    public boolean allows(final List<Rule> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (Rule rule : rules) {
            if (matches(rule)) {
                allowed = rule.isAllow();
            }
        }
        return allowed;
    }

    /**
     * @param rule rule
     * @return whether the rule's constraints match this context
     */
    public boolean matches(final Rule rule) {
        final Rule.Os constraint = rule.os();
        if (constraint != null) {
            if (constraint.name() != null && !constraint.name().equalsIgnoreCase(os.name())) {
                return false;
            }
            if (constraint.arch() != null && !constraint.arch().equalsIgnoreCase(os.arch())) {
                return false;
            }
            if (constraint.version() != null && !versionMatches(constraint.version(), os.version())) {
                return false;
            }
        }
        for (Map.Entry<String, Boolean> feature : rule.features().entrySet()) {
            final boolean actual = features.getOrDefault(feature.getKey(), Boolean.FALSE);
            if (actual != Boolean.TRUE.equals(feature.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean versionMatches(final String regex, final String version) {
        try {
            return Pattern.compile(regex).matcher(version == null ? "" : version).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
