package dev.vanta.core.modrinth;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks the version to install: the newest version that supports the game version and one of the accepted loaders and
 * has a primary file with a SHA-512, preferring stable releases over betas over alphas. A beta is only chosen when a
 * project has no release for the game version, so a pinned "latest stable" combination such as Iris + Sodium matches
 * what their authors test together.
 */
public final class VersionSelector {
    private static final Comparator<ModrinthVersion> PREFERENCE = Comparator
            .comparingInt((ModrinthVersion v) -> v.channel().ordinal())
            .thenComparing(ModrinthVersion::published, Comparator.reverseOrder());

    private VersionSelector() {
    }

    /**
     * @param versions        candidate versions (any order)
     * @param gameVersion     required Minecraft version
     * @param acceptedLoaders loaders of which one must be listed
     * @return the best installable version, or empty when none fits
     */
    public static Optional<ModrinthVersion> best(List<ModrinthVersion> versions, String gameVersion,
                                                 Collection<String> acceptedLoaders) {
        return versions.stream()
                .filter(v -> isInstallable(v, gameVersion, acceptedLoaders))
                .min(PREFERENCE);
    }

    /** Best version for a project type ({@link ModrinthProjectType#loaders()}). */
    public static Optional<ModrinthVersion> best(List<ModrinthVersion> versions, String gameVersion,
                                                 ModrinthProjectType type) {
        return best(versions, gameVersion, type.loaders());
    }

    /** True when the version fits and its primary file carries a usable SHA-512 and a safe name. */
    public static boolean isInstallable(ModrinthVersion version, String gameVersion, Collection<String> acceptedLoaders) {
        if (!version.supports(gameVersion, acceptedLoaders)) {
            return false;
        }
        return version.primaryFile().map(f -> f.hasSha512() && f.hasSafeFilename()).orElse(false);
    }
}
