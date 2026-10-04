package dev.vanta.launcher.core.util;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalised operating system information using the vocabulary of Mojang version manifests.
 *
 * <ul>
 *   <li>{@link #name()} is one of {@code windows}, {@code osx}, {@code linux} (anything else is passed through)</li>
 *   <li>{@link #arch()} is one of {@code x64}, {@code x86}, {@code arm64} (anything else is passed through)</li>
 *   <li>{@link #version()} is the raw {@code os.version} system property</li>
 * </ul>
 *
 * @param name    Mojang os name
 * @param arch    Mojang architecture name
 * @param version raw operating system version string
 */
public record OsInfo(String name, String arch, String version) {

    /** Mojang name for Windows. */
    public static final String WINDOWS = "windows";
    /** Mojang name for macOS. */
    public static final String OSX = "osx";
    /** Mojang name for Linux. */
    public static final String LINUX = "linux";

    public OsInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arch, "arch");
        version = version == null ? "" : version;
    }

    /**
     * Detects the host operating system from system properties.
     *
     * @return normalised host information
     */
    public static OsInfo detect() {
        return fromProperties(System.getProperty("os.name", ""), System.getProperty("os.arch", ""),
            System.getProperty("os.version", ""));
    }

    /**
     * Normalises raw Java system property values.
     *
     * @param osName    value of {@code os.name}
     * @param osArch    value of {@code os.arch}
     * @param osVersion value of {@code os.version}
     * @return normalised information
     */
    public static OsInfo fromProperties(final String osName, final String osArch, final String osVersion) {
        return new OsInfo(normaliseName(osName), normaliseArch(osArch), osVersion);
    }

    /**
     * Normalises an {@code os.name} value to the Mojang vocabulary.
     *
     * @param osName raw name
     * @return {@code windows}, {@code osx}, {@code linux} or the lower-cased input
     */
    public static String normaliseName(final String osName) {
        final String lower = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (lower.contains("win")) {
            return WINDOWS;
        }
        if (lower.contains("mac") || lower.contains("darwin") || lower.contains("os x")) {
            return OSX;
        }
        if (lower.contains("linux") || lower.contains("unix") || lower.contains("bsd") || lower.contains("sunos")) {
            return LINUX;
        }
        return lower;
    }

    /**
     * Normalises an {@code os.arch} value to the Mojang vocabulary.
     *
     * @param osArch raw architecture
     * @return {@code x64}, {@code x86}, {@code arm64} or the lower-cased input
     */
    public static String normaliseArch(final String osArch) {
        final String lower = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            case "aarch64", "arm64" -> "arm64";
            default -> lower;
        };
    }

    /** @return whether this is Windows */
    public boolean isWindows() {
        return WINDOWS.equals(name);
    }

    /** @return whether this is macOS */
    public boolean isMac() {
        return OSX.equals(name);
    }

    /** @return whether this is Linux */
    public boolean isLinux() {
        return LINUX.equals(name);
    }

    /** @return {@code true} for 64-bit architectures */
    public boolean is64Bit() {
        return !"x86".equals(arch);
    }

    /** @return {@code "64"} or {@code "32"}, the value of the legacy {@code ${arch}} natives placeholder */
    public String bits() {
        return is64Bit() ? "64" : "32";
    }

    /** @return the operating system name used by the Adoptium API ({@code windows}, {@code mac}, {@code linux}) */
    public String adoptiumOs() {
        return isMac() ? "mac" : name;
    }

    /** @return the architecture name used by the Adoptium API ({@code x64}, {@code x86}, {@code aarch64}) */
    public String adoptiumArch() {
        return "arm64".equals(arch) ? "aarch64" : arch;
    }

    /** @return file name of the Java executable on this platform */
    public String javaExecutableName() {
        return isWindows() ? "java.exe" : "java";
    }

    /** @return the class path separator for this platform */
    public String classpathSeparator() {
        return isWindows() ? ";" : ":";
    }
}
