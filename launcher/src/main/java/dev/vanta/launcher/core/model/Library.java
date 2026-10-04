package dev.vanta.launcher.core.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A library entry. Mojang entries carry {@code downloads}; Fabric profile entries carry a Maven base {@code url}
 * plus optional hashes. Both shapes share this record.
 *
 * @param name      Maven coordinate {@code group:artifact:version[:classifier]}
 * @param downloads Mojang download block (may be {@code null})
 * @param rules     rules that must allow the library (empty = unconditional)
 * @param natives   legacy natives classifier map keyed by Mojang os name (pre-1.19 format, may be {@code null})
 * @param url       Maven repository base URL (Fabric profile format, may be {@code null})
 * @param sha1      hex SHA-1 (Fabric profile format, may be {@code null})
 * @param sha256    hex SHA-256 (Fabric profile format, may be {@code null})
 * @param md5       hex MD5 (Fabric profile format, informational only)
 * @param size      size in bytes (Fabric profile format, {@code 0} when unknown)
 */
public record Library(String name, Downloads downloads, List<Rule> rules, Map<String, String> natives, String url,
                      String sha1, String sha256, String md5, long size) {

    public Library {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("library name is required");
        }
        rules = rules == null ? List.of() : List.copyOf(rules);
        natives = natives == null ? Map.of() : Map.copyOf(natives);
    }

    /**
     * Minimal Fabric-style library.
     *
     * @param name Maven coordinate
     * @param url  repository base URL
     * @return library
     */
    public static Library ofMaven(final String name, final String url) {
        return new Library(name, null, List.of(), Map.of(), url, null, null, null, 0L);
    }

    /** @return parsed Maven coordinate */
    public MavenCoordinate coordinate() {
        return MavenCoordinate.parse(name);
    }

    /** @return the main artifact download when present */
    public Optional<Download> artifact() {
        return downloads == null ? Optional.empty() : Optional.ofNullable(downloads.artifact());
    }

    /**
     * Download block of a Mojang library.
     *
     * @param artifact    the main artifact
     * @param classifiers legacy natives classifiers (may be {@code null})
     */
    public record Downloads(Download artifact, Map<String, Download> classifiers) {

        public Downloads {
            classifiers = classifiers == null ? Map.of() : Map.copyOf(classifiers);
        }
    }
}
