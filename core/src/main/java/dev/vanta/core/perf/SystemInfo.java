package dev.vanta.core.perf;

import java.util.Objects;

/**
 * Static facts about the machine, shown on the About and Performance screens.
 */
public record SystemInfo(String javaVersion, String javaVendor, String osName, String osVersion, String arch,
                         int cpuCount, long maxHeapBytes) {
    public SystemInfo {
        Objects.requireNonNull(javaVersion, "javaVersion");
        Objects.requireNonNull(javaVendor, "javaVendor");
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(osVersion, "osVersion");
        Objects.requireNonNull(arch, "arch");
    }

    /** Facts about the running JVM. */
    public static SystemInfo current() {
        return new SystemInfo(
                System.getProperty("java.version", "unknown"),
                System.getProperty("java.vendor", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                System.getProperty("os.arch", "unknown"),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory());
    }

    /** "Java 21.0.11 (Eclipse Adoptium)". */
    public String javaLabel() {
        return "Java " + javaVersion + " (" + javaVendor + ")";
    }

    /** "Windows 11 10.0 · x64 · 16 threads". */
    public String machineLabel() {
        return osName + " " + osVersion + " · " + arch + " · " + cpuCount + " threads";
    }
}
