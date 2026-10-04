package dev.vanta.core.release;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic version (semver.org 2.0.0) with pre-release aware ordering.
 *
 * @param major      major
 * @param minor      minor
 * @param patch      patch
 * @param preRelease dot-separated pre-release identifiers (empty for a release)
 * @param build      build metadata (ignored for ordering), empty string when absent
 */
public record SemVer(int major, int minor, int patch, List<String> preRelease, String build) implements Comparable<SemVer> {
    private static final Pattern PATTERN = Pattern.compile(
            "^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    public SemVer {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version numbers must be non-negative");
        }
        preRelease = List.copyOf(preRelease);
        build = build == null ? "" : build;
    }

    /** Release version without pre-release or build. */
    public static SemVer of(int major, int minor, int patch) {
        return new SemVer(major, minor, patch, List.of(), "");
    }

    /** Parses {@code 1.2.3}, {@code 1.2.3-beta.1}, {@code 1.2.3+build}, optional leading {@code v}. */
    public static Optional<SemVer> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = PATTERN.matcher(text.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            int major = Integer.parseInt(m.group(1));
            int minor = Integer.parseInt(m.group(2));
            int patch = Integer.parseInt(m.group(3));
            List<String> pre = m.group(4) == null ? List.of() : List.of(m.group(4).split("\\."));
            return Optional.of(new SemVer(major, minor, patch, pre, m.group(5) == null ? "" : m.group(5)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses or throws.
     *
     * @throws IllegalArgumentException for invalid input
     */
    public static SemVer parseStrict(String text) {
        return parse(text).orElseThrow(() -> new IllegalArgumentException("Not a semantic version: " + text));
    }

    /** True for pre-release versions. */
    public boolean isPreRelease() {
        return !preRelease.isEmpty();
    }

    /** True when this version sorts after {@code other}. */
    public boolean isNewerThan(SemVer other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(SemVer other) {
        Objects.requireNonNull(other, "other");
        int c = Integer.compare(major, other.major);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(minor, other.minor);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(patch, other.patch);
        if (c != 0) {
            return c;
        }
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return Boolean.compare(other.isPreRelease(), isPreRelease());
        }
        int n = Math.min(preRelease.size(), other.preRelease.size());
        for (int i = 0; i < n; i++) {
            c = compareIdentifier(preRelease.get(i), other.preRelease.get(i));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(preRelease.size(), other.preRelease.size());
    }

    private static int compareIdentifier(String a, String b) {
        boolean aNum = a.chars().allMatch(Character::isDigit);
        boolean bNum = b.chars().allMatch(Character::isDigit);
        if (aNum && bNum) {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        }
        if (aNum) {
            return -1;
        }
        if (bNum) {
            return 1;
        }
        return a.compareTo(b);
    }

    /** Equality ignores build metadata, like the ordering. */
    @Override
    public boolean equals(Object o) {
        return o instanceof SemVer v && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(major).append('.').append(minor).append('.').append(patch);
        if (!preRelease.isEmpty()) {
            sb.append('-').append(String.join(".", preRelease));
        }
        if (!build.isEmpty()) {
            sb.append('+').append(build);
        }
        return sb.toString();
    }

    /** Copy with the patch number increased. */
    public SemVer nextPatch() {
        return of(major, minor, patch + 1);
    }
}
