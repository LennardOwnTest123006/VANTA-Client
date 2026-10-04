package dev.vanta.launcher.core.java;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Determines the version of a Java executable. Production runs the executable; tests inject a fake.
 */
@FunctionalInterface
public interface JavaProbe {

    /**
     * @param javaExecutable executable to probe
     * @return install details when the executable is a working Java runtime
     */
    Optional<JavaInstall> probe(Path javaExecutable);
}
