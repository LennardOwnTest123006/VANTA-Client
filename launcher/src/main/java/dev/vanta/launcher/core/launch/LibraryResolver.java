package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.Download;
import dev.vanta.launcher.core.model.Library;
import dev.vanta.launcher.core.model.MavenCoordinate;
import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.util.OsInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes the effective libraries for a platform.
 *
 * <ul>
 *   <li>Rules are evaluated per library ({@link RuleEvaluator}).</li>
 *   <li>Modern (1.19+) natives are ordinary library entries with a {@code natives-*} classifier guarded by rules —
 *       they come out of the same path.</li>
 *   <li>Legacy {@code natives} + {@code downloads.classifiers} entries (pre-1.19) are still honoured so the
 *       resolver works for any version JSON shape.</li>
 *   <li>Fabric profile libraries (name + Maven base {@code url}) are resolved to {@code url + mavenPath}.</li>
 * </ul>
 */
public final class LibraryResolver {

    /** Mojang library repository. */
    public static final String MOJANG_LIBRARIES_BASE = "https://libraries.minecraft.net/";

    private final RuleEvaluator evaluator;
    private final OsInfo os;

    /**
     * @param os target platform
     */
    public LibraryResolver(final OsInfo os) {
        this.os = Objects.requireNonNull(os, "os");
        this.evaluator = new RuleEvaluator(os);
    }

    /**
     * Resolves libraries for the platform.
     *
     * @param libraries library entries (vanilla or Fabric)
     * @return effective libraries in declaration order
     */
    public List<ResolvedLibrary> resolve(final List<Library> libraries) {
        final List<ResolvedLibrary> out = new ArrayList<>();
        for (Library lib : libraries) {
            if (!evaluator.allows(lib.rules())) {
                continue;
            }
            resolveMain(lib).ifPresent(out::add);
            resolveLegacyNative(lib).ifPresent(out::add);
        }
        return out;
    }

    private Optional<ResolvedLibrary> resolveMain(final Library lib) {
        final MavenCoordinate coordinate = lib.coordinate();
        final Optional<Download> artifact = lib.artifact();
        if (artifact.isPresent()) {
            final Download d = artifact.get();
            final String path = d.path() != null && !d.path().isBlank() ? d.path() : coordinate.path();
            final Checksum checksum = d.hasSha1() ? Checksum.sha1(d.sha1()) : null;
            return Optional.of(new ResolvedLibrary(coordinate, path, d.url(), checksum, d.size() > 0 ? d.size() : -1L,
                coordinate.isNativeClassifier()));
        }
        if (lib.downloads() != null && lib.downloads().artifact() == null && !lib.downloads().classifiers().isEmpty()) {
            // natives-only entry (legacy); handled by resolveLegacyNative
            return Optional.empty();
        }
        final String base = lib.url() != null && !lib.url().isBlank() ? ensureSlash(lib.url()) : MOJANG_LIBRARIES_BASE;
        final Checksum checksum;
        if (lib.sha256() != null && !lib.sha256().isBlank()) {
            checksum = Checksum.sha256(lib.sha256());
        } else if (lib.sha1() != null && !lib.sha1().isBlank()) {
            checksum = Checksum.sha1(lib.sha1());
        } else {
            checksum = null;
        }
        return Optional.of(new ResolvedLibrary(coordinate, coordinate.path(), base + coordinate.path(), checksum,
            lib.size() > 0 ? lib.size() : -1L, coordinate.isNativeClassifier()));
    }

    private Optional<ResolvedLibrary> resolveLegacyNative(final Library lib) {
        if (lib.natives().isEmpty() || lib.downloads() == null) {
            return Optional.empty();
        }
        final String key = lib.natives().get(os.name());
        if (key == null) {
            return Optional.empty();
        }
        final String classifier = key.replace("${arch}", os.bits());
        final Map<String, Download> classifiers = lib.downloads().classifiers();
        final Download d = classifiers.get(classifier);
        final MavenCoordinate coordinate = lib.coordinate().withClassifier(classifier);
        if (d == null) {
            return Optional.of(new ResolvedLibrary(coordinate, coordinate.path(), MOJANG_LIBRARIES_BASE + coordinate.path(),
                null, -1L, true));
        }
        final String path = d.path() != null && !d.path().isBlank() ? d.path() : coordinate.path();
        return Optional.of(new ResolvedLibrary(coordinate, path, d.url(), d.hasSha1() ? Checksum.sha1(d.sha1()) : null,
            d.size() > 0 ? d.size() : -1L, true));
    }

    private static String ensureSlash(final String url) {
        return url.endsWith("/") ? url : url + "/";
    }
}
