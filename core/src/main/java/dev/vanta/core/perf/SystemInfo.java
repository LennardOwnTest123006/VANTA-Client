package dev.vanta.core.perf;

import java.lang.management.ManagementFactory;
import java.util.Objects;

/**
 * Static facts about the machine, shown on the About and Performance screens and used by Smart Boost's starting
 * guess ({@link HardwareTier}).
 *
 * @param totalRamBytes physical memory of the machine, 0 when the JVM does not expose it
 */
public record SystemInfo(String javaVersion, String javaVendor, String osName, String osVersion, String arch,
                         int cpuCount, long maxHeapBytes, long totalRamBytes) {
    public SystemInfo {
        Objects.requireNonNull(javaVersion, "javaVersion");
        Objects.requireNonNull(javaVendor, "javaVendor");
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(osVersion, "osVersion");
        Objects.requireNonNull(arch, "arch");
        totalRamBytes = Math.max(0L, totalRamBytes);
    }

    /** Facts without the physical memory size (reported as unknown). */
    public SystemInfo(String javaVersion, String javaVendor, String osName, String osVersion, String arch,
                      int cpuCount, long maxHeapBytes) {
        this(javaVersion, javaVendor, osName, osVersion, arch, cpuCount, maxHeapBytes, 0L);
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
                Runtime.getRuntime().maxMemory(),
                physicalMemoryBytes());
    }

    /** Physical memory through {@code com.sun.management.OperatingSystemMXBean}, 0 when unavailable. */
    static long physicalMemoryBytes() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                return Math.max(0L, os.getTotalMemorySize());
            }
        } catch (RuntimeException | LinkageError e) {
            // Not every JVM ships the com.sun.management extension; the size is optional.
        }
        return 0L;
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
