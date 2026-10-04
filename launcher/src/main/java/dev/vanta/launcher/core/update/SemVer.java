package dev.vanta.launcher.core.update;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic version ({@code MAJOR.MINOR.PATCH[-prerelease][+build]}) with SemVer 2.0 precedence.
 *
 * @param major      major
 * @param minor      minor
 * @param patch      patch
 * @param preRelease pre-release identifiers (empty for a release)
 * @param build      build metadata (ignored for precedence)
 */
public record SemVer(int major, int minor, int patch, List<String> preRelease, String build) implements Comparable<SemVer> {

    private static final Pattern PATTERN = Pattern.compile(
        "^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$");

    public SemVer {
        preRelease = preRelease == null ? List.of() : List.copyOf(preRelease);
        build = build == null ? "" : build;
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Negative version component");
        }
    }

    /**
     * Release version.
     *
     * @param major major
     * @param minor minor
     * @param patch patch
     * @return version
     */
    public static SemVer of(final int major, final int minor, final int patch) {
        return new SemVer(major, minor, patch, List.of(), "");
    }

    /**
     * Parses a version string (an optional leading {@code v} is accepted).
     *
     * @param text version
     * @return version
     * @throws IllegalArgumentException for malformed input
     */
    public static SemVer parse(final String text) {
        final Matcher m = PATTERN.matcher(Objects.requireNonNull(text, "text").trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Not a semantic version: '" + text + "'");
        }
        final List<String> pre = m.group(4) == null ? List.of() : List.of(m.group(4).split("\\."));
        return new SemVer(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), pre,
            m.group(5) == null ? "" : m.group(5));
    }

    /**
     * Lenient parse.
     *
     * @param text version
     * @return version when valid
     */
    public static Optional<SemVer> tryParse(final String text) {
        try {
            return text == null ? Optional.empty() : Optional.of(parse(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** @return whether this is a pre-release */
    public boolean isPreRelease() {
        return !preRelease.isEmpty();
    }

    /**
     * @param other other version
     * @return whether this version is newer
     */
    public boolean isNewerThan(final SemVer other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(final SemVer o) {
        int c = Integer.compare(major, o.major);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(minor, o.minor);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(patch, o.patch);
        if (c != 0) {
            return c;
        }
        if (preRelease.isEmpty() && o.preRelease.isEmpty()) {
            return 0;
        }
        if (preRelease.isEmpty()) {
            return 1; // release > pre-release
        }
        if (o.preRelease.isEmpty()) {
            return -1;
        }
        final int n = Math.min(preRelease.size(), o.preRelease.size());
        for (int i = 0; i < n; i++) {
            final String a = preRelease.get(i);
            final String b = o.preRelease.get(i);
            final boolean na = a.chars().allMatch(Character::isDigit);
            final boolean nb = b.chars().allMatch(Character::isDigit);
            if (na && nb) {
                c = Long.compare(Long.parseLong(a), Long.parseLong(b));
            } else if (na) {
                c = -1; // numeric identifiers have lower precedence
            } else if (nb) {
                c = 1;
            } else {
                c = a.compareTo(b);
            }
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(preRelease.size(), o.preRelease.size());
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof SemVer s && compareTo(s) == 0 && build.equals(s.build);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease, build);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append(major).append('.').append(minor).append('.').append(patch);
        if (!preRelease.isEmpty()) {
            sb.append('-').append(String.join(".", preRelease));
        }
        if (!build.isEmpty()) {
            sb.append('+').append(build);
        }
        return sb.toString();
    }
}
