package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.VersionJson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Builds the game (main class) argument list from {@code arguments.game} or the legacy {@code minecraftArguments}
 * string, evaluating feature rules ({@code is_demo_user}, {@code has_custom_resolution},
 * {@code has_quick_plays_support}, {@code is_quick_play_singleplayer}, ...) and expanding placeholders.
 */
public final class GameArgsBuilder {

    /** Feature: demo user. */
    public static final String FEATURE_DEMO = "is_demo_user";
    /** Feature: explicit window size. */
    public static final String FEATURE_RESOLUTION = "has_custom_resolution";
    /** Feature: quick play log path support. */
    public static final String FEATURE_QUICK_PLAYS = "has_quick_plays_support";
    /** Feature: open a singleplayer world directly. */
    public static final String FEATURE_QUICK_PLAY_SINGLEPLAYER = "is_quick_play_singleplayer";
    /** Feature: join a server directly. */
    public static final String FEATURE_QUICK_PLAY_MULTIPLAYER = "is_quick_play_multiplayer";
    /** Feature: join a Realm directly. */
    public static final String FEATURE_QUICK_PLAY_REALMS = "is_quick_play_realms";

    private final RuleEvaluator evaluator;

    /**
     * @param evaluator evaluator carrying the platform and the enabled features
     */
    public GameArgsBuilder(final RuleEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    /**
     * @param version  merged version JSON
     * @param expander placeholder expander
     * @return game arguments
     */
    public List<String> build(final VersionJson version, final ArgumentExpander expander) {
        final List<String> out = new ArrayList<>();
        if (!version.arguments().game().isEmpty()) {
            out.addAll(expander.expandAll(version.arguments().game(), evaluator));
        } else if (version.minecraftArguments() != null && !version.minecraftArguments().isBlank()) {
            out.addAll(expander.expandTokens(Arrays.asList(version.minecraftArguments().trim().split("\\s+"))));
        }
        return out;
    }
}
