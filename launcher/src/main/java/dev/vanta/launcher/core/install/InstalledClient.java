package dev.vanta.launcher.core.install;

import dev.vanta.launcher.core.update.SemVer;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The VANTA client that is actually installed in the instance, as reported by
 * {@link VantaClientService#installedClient()}. The Home card, the update banner, the update dialog and
 * {@code --check-update} all read this one value, so they can never disagree about whether a client is installed.
 *
 * @param version version: from {@code instance.json} when it records the jar that is present, else from the jar name
 *                ({@code vanta-client-<version>.jar}); {@value VantaClientService#DEV_VERSION} for a local build
 * @param jar     the jar in {@code mods/}; empty when {@code instance.json} records a client whose jar is missing (the
 *                next PLAY or "Verify files" restores it)
 * @param source  where the information came from
 */
public record InstalledClient(String version, Optional<Path> jar, Source source) {

    /** Where the installed state was read from. */
    public enum Source {
        /** {@code instance.json} records the client (written by PLAY / {@code --install}). */
        INSTANCE,
        /**
         * Only a {@code vanta-client-*.jar} in {@code mods/} (for example after "Use with Minecraft Launcher" without a
         * full install, which writes no {@code instance.json}).
         */
        MODS_JAR
    }

    public InstalledClient {
        version = version == null ? "" : version.trim();
        jar = jar == null ? Optional.empty() : jar;
        Objects.requireNonNull(source, "source");
    }

    /** @return the version as SemVer; empty for a development build or an unparseable name */
    public Optional<SemVer> semVer() {
        return SemVer.tryParse(version);
    }

    /** @return whether this is a local development jar ({@code --client-jar}) */
    public boolean isDevelopmentBuild() {
        return VantaClientService.DEV_VERSION.equals(version);
    }

    /**
     * The version updates are compared with: the installed SemVer, or {@code 0.0.0} for a development build or a jar
     * whose name carries no version, so a published release always counts as newer (as it did before 1.0.1).
     *
     * @return comparison version
     */
    public SemVer comparisonVersion() {
        return semVer().orElse(SemVer.of(0, 0, 0));
    }
}
