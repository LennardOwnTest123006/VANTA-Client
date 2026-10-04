package dev.vanta.launcher.core.java;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A detected Java runtime.
 *
 * @param home       installation directory (parent of {@code bin/})
 * @param executable the {@code java} executable
 * @param version    full version string ({@code java.version}, e.g. {@code 21.0.4})
 * @param major      major version (21)
 * @param vendor     {@code java.vendor}
 * @param arch       {@code os.arch} as reported by the runtime
 * @param is64Bit    whether the runtime is 64-bit
 */
public record JavaInstall(Path home, Path executable, String version, int major, String vendor, String arch, boolean is64Bit) {

    public JavaInstall {
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(executable, "executable");
        version = version == null ? "" : version;
        vendor = vendor == null ? "" : vendor;
        arch = arch == null ? "" : arch;
    }

    /**
     * @param required required major version
     * @return whether the runtime satisfies the requirement ({@code major >= required})
     */
    public boolean satisfies(final int required) {
        return major >= required;
    }

    /** @return one line description for the UI */
    public String describe() {
        return "Java " + version + " (" + (vendor.isEmpty() ? "unknown vendor" : vendor) + ", " + (is64Bit ? "64-bit" : "32-bit")
            + ") at " + home;
    }
}
