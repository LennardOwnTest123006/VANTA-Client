package dev.vanta.core.ai;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The platform key of a Local AI runtime archive: {@code <os>-<arch>} with os in windows, linux, macos and arch in
 * x64, arm64. Detection reads {@code os.name} and {@code os.arch} (injectable for tests); anything else is an
 * unsupported platform and gets an honest "not available" state instead of a crash.
 *
 * @param os   operating system family
 * @param arch CPU architecture
 */
public record Platform(Os os, Arch arch) {
    /** Operating system families llama.cpp publishes CPU builds for. */
    public enum Os {
        WINDOWS("windows"), LINUX("linux"), MACOS("macos");

        private final String id;

        Os(String id) {
            this.id = id;
        }

        /** Lower-case id used in the platform key. */
        public String id() {
            return id;
        }
    }

    /** CPU architectures llama.cpp publishes CPU builds for. */
    public enum Arch {
        X64("x64"), ARM64("arm64");

        private final String id;

        Arch(String id) {
            this.id = id;
        }

        /** Lower-case id used in the platform key. */
        public String id() {
            return id;
        }
    }

    public Platform {
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(arch, "arch");
    }

    /** The manifest key, e.g. {@code windows-x64}. */
    public String key() {
        return os.id() + "-" + arch.id();
    }

    /** True on Windows (executables end in {@code .exe}, no executable bit). */
    public boolean isWindows() {
        return os == Os.WINDOWS;
    }

    /** True for a platform whose archive is a zip (Windows); the others ship tar.gz archives. */
    public boolean usesZip() {
        return os == Os.WINDOWS;
    }

    /** The platform the JVM runs on, empty when llama.cpp has no CPU build for it. */
    public static Optional<Platform> current() {
        return detect(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    /** Human-readable "os/arch" of the running JVM for the "not available for ..." message. */
    public static String systemLabel() {
        return systemLabel(System.getProperty("os.name", "?"), System.getProperty("os.arch", "?"));
    }

    /** Human-readable "os/arch" for the "not available for ..." message. */
    public static String systemLabel(String osName, String osArch) {
        return osName + "/" + osArch;
    }

    /**
     * Normalises {@code os.name} / {@code os.arch} values: Windows*, Linux, Mac OS X / macOS / Darwin; amd64 and
     * x86_64 become x64, aarch64 and arm64 become arm64.
     */
    public static Optional<Platform> detect(String osName, String osArch) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
        Os family;
        if (os.contains("mac") || os.contains("darwin") || os.contains("os x")) {
            family = Os.MACOS; // checked first: "Darwin" contains "win"
        } else if (os.contains("win")) {
            family = Os.WINDOWS;
        } else if (os.contains("linux")) {
            family = Os.LINUX;
        } else {
            return Optional.empty();
        }
        Arch cpu;
        switch (arch) {
            case "amd64", "x86_64", "x64", "x86-64" -> cpu = Arch.X64;
            case "aarch64", "arm64" -> cpu = Arch.ARM64;
            default -> {
                return Optional.empty();
            }
        }
        return Optional.of(new Platform(family, cpu));
    }

    /** Parses a manifest key such as {@code linux-arm64}. */
    public static Optional<Platform> parse(String key) {
        if (key == null) {
            return Optional.empty();
        }
        int dash = key.indexOf('-');
        if (dash <= 0 || dash == key.length() - 1) {
            return Optional.empty();
        }
        String osId = key.substring(0, dash);
        String archId = key.substring(dash + 1);
        Os os = null;
        for (Os candidate : Os.values()) {
            if (candidate.id().equals(osId)) {
                os = candidate;
            }
        }
        Arch arch = null;
        for (Arch candidate : Arch.values()) {
            if (candidate.id().equals(archId)) {
                arch = candidate;
            }
        }
        return os == null || arch == null ? Optional.empty() : Optional.of(new Platform(os, arch));
    }

    @Override
    public String toString() {
        return key();
    }
}
