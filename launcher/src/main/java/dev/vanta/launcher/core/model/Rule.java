package dev.vanta.launcher.core.model;

import java.util.Map;

/**
 * A rule from a Mojang version JSON ({@code libraries[].rules}, {@code arguments.*[].rules}).
 *
 * @param action   {@code allow} or {@code disallow}
 * @param os       optional operating system constraint
 * @param features optional feature flags that all have to match
 */
public record Rule(String action, Os os, Map<String, Boolean> features) {

    /** Action value that allows. */
    public static final String ALLOW = "allow";
    /** Action value that disallows. */
    public static final String DISALLOW = "disallow";

    public Rule {
        action = action == null ? ALLOW : action;
        features = features == null ? Map.of() : Map.copyOf(features);
    }

    /** @return whether this rule allows when it matches */
    public boolean isAllow() {
        return ALLOW.equalsIgnoreCase(action);
    }

    /**
     * Operating system constraint of a rule.
     *
     * @param name    {@code windows}, {@code osx} or {@code linux}; {@code null} for any
     * @param arch    {@code x86}, {@code x64}, {@code arm64}; {@code null} for any
     * @param version regular expression matched against {@code os.version}; {@code null} for any
     */
    public record Os(String name, String arch, String version) {
    }
}
