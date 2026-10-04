package dev.vanta.launcher.core.model;

import java.util.Objects;

/**
 * Maven coordinate {@code group:artifact:version[:classifier][@extension]} and its repository path.
 *
 * @param group      group id
 * @param artifact   artifact id
 * @param version    version
 * @param classifier classifier or empty string
 * @param extension  file extension without dot (defaults to {@code jar})
 */
public record MavenCoordinate(String group, String artifact, String version, String classifier, String extension) {

    public MavenCoordinate {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(version, "version");
        classifier = classifier == null ? "" : classifier;
        extension = extension == null || extension.isBlank() ? "jar" : extension;
    }

    /**
     * Parses a coordinate string.
     *
     * @param coordinate e.g. {@code org.lwjgl:lwjgl:3.3.3:natives-linux} or {@code net.fabricmc:tiny-remapper:0.10@zip}
     * @return coordinate
     * @throws IllegalArgumentException for malformed input
     */
    public static MavenCoordinate parse(final String coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        String rest = coordinate.trim();
        String extension = "jar";
        final int at = rest.indexOf('@');
        if (at >= 0) {
            extension = rest.substring(at + 1);
            rest = rest.substring(0, at);
        }
        final String[] parts = rest.split(":", -1);
        if (parts.length < 3 || parts.length > 4) {
            throw new IllegalArgumentException("Malformed Maven coordinate: " + coordinate);
        }
        for (String p : parts) {
            if (p.isBlank() && !(parts.length == 4 && p == parts[3])) {
                throw new IllegalArgumentException("Malformed Maven coordinate: " + coordinate);
            }
        }
        return new MavenCoordinate(parts[0], parts[1], parts[2], parts.length == 4 ? parts[3] : "", extension);
    }

    /** @return file name {@code artifact-version[-classifier].extension} */
    public String fileName() {
        final StringBuilder sb = new StringBuilder(artifact).append('-').append(version);
        if (!classifier.isEmpty()) {
            sb.append('-').append(classifier);
        }
        return sb.append('.').append(extension).toString();
    }

    /** @return repository relative path with forward slashes */
    public String path() {
        return group.replace('.', '/') + '/' + artifact + '/' + version + '/' + fileName();
    }

    /** @return dedupe key {@code group:artifact[:classifier]} (version-less) */
    public String key() {
        return classifier.isEmpty() ? group + ':' + artifact : group + ':' + artifact + ':' + classifier;
    }

    /** @return whether the classifier marks a native library */
    public boolean isNativeClassifier() {
        return classifier.startsWith("natives-");
    }

    /**
     * Returns the coordinate with a different classifier.
     *
     * @param newClassifier classifier (may be empty)
     * @return coordinate
     */
    public MavenCoordinate withClassifier(final String newClassifier) {
        return new MavenCoordinate(group, artifact, version, newClassifier, extension);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(group).append(':').append(artifact).append(':').append(version);
        if (!classifier.isEmpty()) {
            sb.append(':').append(classifier);
        }
        if (!"jar".equals(extension)) {
            sb.append('@').append(extension);
        }
        return sb.toString();
    }

    /**
     * Compares two version strings segment by segment: numeric segments compare numerically, others lexically,
     * and a release sorts after its pre-release ({@code 9.7} &gt; {@code 9.7-beta}).
     *
     * @param a first version
     * @param b second version
     * @return negative, zero or positive
     */
    public static int compareVersions(final String a, final String b) {
        final String[] as = a.split("[.\\-+_]");
        final String[] bs = b.split("[.\\-+_]");
        final int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            final String x = i < as.length ? as[i] : null;
            final String y = i < bs.length ? bs[i] : null;
            if (x == null) {
                return isNumeric(y) ? -1 : 1; // "9.7" vs "9.7.1" -> shorter is older; "9.7" vs "9.7-beta" -> release newer
            }
            if (y == null) {
                return isNumeric(x) ? 1 : -1;
            }
            final boolean nx = isNumeric(x);
            final boolean ny = isNumeric(y);
            final int c;
            if (nx && ny) {
                c = Long.compare(Long.parseLong(x), Long.parseLong(y));
            } else if (nx) {
                c = 1;
            } else if (ny) {
                c = -1;
            } else {
                c = x.compareTo(y);
            }
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static boolean isNumeric(final String s) {
        if (s == null || s.isEmpty() || s.length() > 18) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
