package dev.vanta.launcher.core.util;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.OptionalLong;

/**
 * Reads the physical memory of the machine (used for the default {@code -Xmx}).
 */
public final class SystemMemory {

    private SystemMemory() {
    }

    /**
     * @return total physical memory in MiB when the JVM exposes it
     */
    public static OptionalLong totalMemoryMb() {
        try {
            final OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
                final long bytes = sun.getTotalMemorySize();
                if (bytes > 0) {
                    return OptionalLong.of(bytes / (1024L * 1024L));
                }
            }
        } catch (RuntimeException | LinkageError e) {
            // fall through: not available on this runtime
        }
        return OptionalLong.empty();
    }
}
