package dev.vanta.launcher.core.java;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parses Java version strings and the output of {@code java -XshowSettings:properties -version}.
 */
public final class JavaVersionParser {

    private JavaVersionParser() {
    }

    /**
     * Extracts the major version from a {@code java.version} string.
     *
     * <ul>
     *   <li>{@code 21.0.4}, {@code 21}, {@code 21-ea}, {@code 17.0.9+9-LTS} → 21/21/21/17</li>
     *   <li>{@code 1.8.0_392} → 8</li>
     * </ul>
     *
     * @param version version string
     * @return major version, {@code -1} when unparseable
     */
    public static int major(final String version) {
        if (version == null || version.isBlank()) {
            return -1;
        }
        final String v = version.trim();
        final String[] parts = v.split("[.\\-+_]");
        try {
            if (parts[0].equals("1") && parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Parses the property dump of {@code java -XshowSettings:properties -version} (written to stderr).
     *
     * @param output combined stdout/stderr output
     * @return property map ({@code java.version}, {@code java.vendor}, {@code os.arch}, {@code java.home},
     * {@code sun.arch.data.model} when present)
     */
    public static Map<String, String> parseProperties(final String output) {
        final Map<String, String> props = new HashMap<>();
        if (output == null) {
            return props;
        }
        for (String rawLine : output.split("\\R")) {
            final String line = rawLine.trim();
            final int eq = line.indexOf(" = ");
            if (eq <= 0) {
                continue;
            }
            final String key = line.substring(0, eq).trim();
            final String value = line.substring(eq + 3).trim();
            if (key.matches("[a-zA-Z0-9._]+") && !props.containsKey(key)) {
                props.put(key, value);
            }
        }
        return props;
    }

    /**
     * Builds a {@link JavaInstall} from probe output.
     *
     * @param executable probed executable
     * @param output     probe output
     * @return install when the output contains a parseable {@code java.version}
     */
    public static Optional<JavaInstall> fromProbeOutput(final Path executable, final String output) {
        final Map<String, String> props = parseProperties(output);
        String version = props.get("java.version");
        if (version == null) {
            version = versionFromBanner(output);
        }
        final int major = major(version);
        if (major < 0) {
            return Optional.empty();
        }
        final String vendor = props.getOrDefault("java.vendor", "");
        final String arch = props.getOrDefault("os.arch", "");
        final String dataModel = props.get("sun.arch.data.model");
        final boolean is64 = dataModel != null ? "64".equals(dataModel) : !arch.toLowerCase(Locale.ROOT).matches("x86|i[3-6]86|arm");
        final Path home = props.containsKey("java.home") ? Path.of(props.get("java.home")) : homeOf(executable);
        return Optional.of(new JavaInstall(home, executable, version, major, vendor, arch, is64));
    }

    /**
     * Derives the installation home from an executable path ({@code <home>/bin/java}).
     *
     * @param executable executable
     * @return home
     */
    public static Path homeOf(final Path executable) {
        final Path bin = executable.getParent() != null ? executable.getParent() : executable.toAbsolutePath().getParent();
        return bin != null && bin.getParent() != null ? bin.getParent() : executable.toAbsolutePath();
    }

    private static String versionFromBanner(final String output) {
        // e.g. openjdk version "21.0.4" 2024-07-16 LTS
        if (output == null) {
            return null;
        }
        for (String line : output.split("\\R")) {
            final int q1 = line.indexOf('"');
            if (line.contains(" version ") && q1 >= 0) {
                final int q2 = line.indexOf('"', q1 + 1);
                if (q2 > q1) {
                    return line.substring(q1 + 1, q2);
                }
            }
        }
        return null;
    }
}
