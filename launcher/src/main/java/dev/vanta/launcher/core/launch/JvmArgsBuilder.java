package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.VersionJson;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the JVM argument list.
 *
 * <p>Order: heap, garbage collector defaults (unless the user picked a GC), launcher identification, the version
 * JSON's {@code arguments.jvm} (rule-filtered and expanded; this is where {@code -cp ${classpath}} and
 * {@code -Djava.library.path=${natives_directory}} come from), then the user's extra arguments. User arguments
 * win over defaults because the JVM honours the last occurrence of a flag.</p>
 */
public final class JvmArgsBuilder {

    /** Launcher identification property. */
    public static final String LAUNCHER_PROPERTY = "vanta.launcher";

    private static final List<String> GC_DEFAULTS = List.of(
        "-XX:+UseG1GC",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:G1NewSizePercent=20",
        "-XX:G1ReservePercent=20",
        "-XX:MaxGCPauseMillis=50",
        "-XX:G1HeapRegionSize=32M"
    );

    private final RuleEvaluator evaluator;
    private final String launcherVersion;

    /**
     * @param evaluator       rule evaluator for the platform
     * @param launcherVersion launcher version placed in {@code -Dvanta.launcher}
     */
    public JvmArgsBuilder(final RuleEvaluator evaluator, final String launcherVersion) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.launcherVersion = Objects.requireNonNull(launcherVersion, "launcherVersion");
    }

    /**
     * @param version   merged version JSON
     * @param expander  placeholder expander ({@code classpath}, {@code natives_directory}, {@code launcher_name}, ...)
     * @param memoryMb  maximum heap in MiB
     * @param extraArgs user supplied extra JVM arguments
     * @return JVM arguments (without the main class)
     */
    public List<String> build(final VersionJson version, final ArgumentExpander expander, final int memoryMb,
                              final List<String> extraArgs) {
        final List<String> user = extraArgs == null ? List.of() : extraArgs;
        final List<String> out = new ArrayList<>();
        out.add("-Xmx" + memoryMb + "M");
        if (user.stream().noneMatch(a -> a.startsWith("-Xms"))) {
            out.add("-Xms" + Math.min(memoryMb, Math.max(512, memoryMb / 4)) + "M");
        }
        if (user.stream().noneMatch(JvmArgsBuilder::selectsGarbageCollector)) {
            out.addAll(GC_DEFAULTS);
        }
        out.add("-Dfile.encoding=UTF-8");
        out.add("-Dstdout.encoding=UTF-8");
        out.add("-Dstderr.encoding=UTF-8");
        out.add("-D" + LAUNCHER_PROPERTY + "=" + launcherVersion);
        // The launcher starts this game itself and restarts it when the game asks (RestartRequest).
        out.add("-D" + RestartRequest.PROPERTY + "=true");

        final List<String> fromJson = expander.expandAll(version.arguments().jvm(), evaluator);
        if (fromJson.stream().noneMatch(a -> a.equals("-cp") || a.equals("-classpath"))) {
            // Legacy version JSON without arguments.jvm
            out.add(expander.expand("-Djava.library.path=${natives_directory}"));
            out.add("-cp");
            out.add(expander.expand("${classpath}"));
        }
        out.addAll(fromJson);
        out.addAll(user);
        return dedupeSystemProperties(out);
    }

    /**
     * @param arg JVM argument
     * @return whether it selects a garbage collector
     */
    static boolean selectsGarbageCollector(final String arg) {
        return arg.startsWith("-XX:+Use") && arg.endsWith("GC");
    }

    /**
     * Keeps the last definition of each {@code -Dkey=} system property so user overrides win without the JVM
     * seeing duplicates.
     */
    private static List<String> dedupeSystemProperties(final List<String> args) {
        final Set<String> seenKeys = new LinkedHashSet<>();
        final List<String> reversed = new ArrayList<>();
        for (int i = args.size() - 1; i >= 0; i--) {
            final String a = args.get(i);
            if (a.startsWith("-D") && a.contains("=")) {
                final String key = a.substring(0, a.indexOf('='));
                if (!seenKeys.add(key)) {
                    continue;
                }
            }
            reversed.add(a);
        }
        final List<String> out = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            out.add(reversed.get(i));
        }
        return out;
    }
}
