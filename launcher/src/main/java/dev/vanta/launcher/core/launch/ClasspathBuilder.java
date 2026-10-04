package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.model.MavenCoordinate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the game class path: Fabric loader libraries first, then vanilla libraries, then the client jar.
 * Libraries sharing {@code group:artifact[:classifier]} are de-duplicated keeping the higher version while
 * preserving the position of the first occurrence.
 */
public final class ClasspathBuilder {

    private final Path librariesDir;

    /**
     * @param librariesDir libraries root
     */
    public ClasspathBuilder(final Path librariesDir) {
        this.librariesDir = Objects.requireNonNull(librariesDir, "librariesDir");
    }

    /**
     * @param fabricLibraries  Fabric profile libraries (resolved)
     * @param vanillaLibraries vanilla libraries (resolved)
     * @param clientJar        the Minecraft client jar
     * @return ordered class path entries
     */
    public List<Path> build(final List<ResolvedLibrary> fabricLibraries, final List<ResolvedLibrary> vanillaLibraries,
                            final Path clientJar) {
        final Map<String, ResolvedLibrary> byKey = new LinkedHashMap<>();
        addAll(byKey, fabricLibraries);
        addAll(byKey, vanillaLibraries);
        final List<Path> out = new ArrayList<>(byKey.size() + 1);
        for (ResolvedLibrary lib : byKey.values()) {
            out.add(lib.file(librariesDir));
        }
        out.add(Objects.requireNonNull(clientJar, "clientJar"));
        return out;
    }

    /**
     * Joins class path entries with the platform separator.
     *
     * @param entries   entries
     * @param separator {@code ;} on Windows, {@code :} elsewhere
     * @return class path string
     */
    public static String join(final List<Path> entries, final String separator) {
        final StringBuilder sb = new StringBuilder();
        for (Path p : entries) {
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(p.toString());
        }
        return sb.toString();
    }

    private static void addAll(final Map<String, ResolvedLibrary> byKey, final List<ResolvedLibrary> libs) {
        for (ResolvedLibrary lib : libs) {
            final String key = lib.coordinate().key();
            final ResolvedLibrary existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, lib);
            } else if (MavenCoordinate.compareVersions(lib.coordinate().version(), existing.coordinate().version()) > 0) {
                byKey.put(key, lib); // LinkedHashMap keeps the original insertion position
            }
        }
    }
}
