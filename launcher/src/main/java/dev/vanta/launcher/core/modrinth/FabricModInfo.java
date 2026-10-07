package dev.vanta.launcher.core.modrinth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vanta.launcher.core.log.LauncherLog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * What a Fabric mod jar says about itself in its {@code fabric.mod.json}: id, name and version. The one reader the
 * launcher uses for it (duplicate detection on the Mods page, the start check, crash report recovery).
 *
 * @param id      mod id ({@code "id"})
 * @param name    display name ({@code "name"}, the id when missing)
 * @param version version ({@code "version"}, empty when missing)
 */
public record FabricModInfo(String id, String name, String version) {

    /** The file Fabric reads. */
    public static final String FILE = "fabric.mod.json";
    /** Folder of jars nested in a Fabric mod (jar-in-jar). */
    public static final String NESTED_JARS = "META-INF/jars/";
    /** Nested jars inside nested jars that are followed. */
    static final int MAX_NESTED_DEPTH = 3;
    /** A nested jar larger than this is not read. */
    static final long MAX_NESTED_JAR_BYTES = 64L * 1024 * 1024;
    private static final long MAX_JSON_BYTES = 1024L * 1024;

    private static final Logger LOG = LauncherLog.get("Modrinth");

    public FabricModInfo {
        Objects.requireNonNull(id, "id");
        name = name == null || name.isBlank() ? id : name;
        version = version == null ? "" : version;
    }

    /** @return {@code "<name> <version>"} (only the name when the version is unknown) */
    public String label() {
        return version.isEmpty() ? name : name + " " + version;
    }

    /**
     * @param jar a mod jar
     * @return its {@code fabric.mod.json} (empty when the file is not a readable jar or has no mod id)
     */
    public static Optional<FabricModInfo> read(final Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return read(zip);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "No Fabric mod id in {0}: {1}", new Object[] {jar, e.toString()});
            return Optional.empty();
        }
    }

    /**
     * @param zip an open jar
     * @return its {@code fabric.mod.json} (empty when missing, unreadable or without an id)
     */
    public static Optional<FabricModInfo> read(final ZipFile zip) {
        final ZipEntry entry = zip.getEntry(FILE);
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return parse(in.readNBytes((int) MAX_JSON_BYTES));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Unreadable fabric.mod.json in {0}: {1}", new Object[] {zip.getName(), e.toString()});
            return Optional.empty();
        }
    }

    /**
     * @param json content of a {@code fabric.mod.json}
     * @return id, name and version (empty when it is not an object with an id)
     */
    static Optional<FabricModInfo> parse(final byte[] json) {
        try {
            final JsonElement tree = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
            if (!tree.isJsonObject() || !tree.getAsJsonObject().has("id")) {
                return Optional.empty();
            }
            final JsonObject o = tree.getAsJsonObject();
            final String id = o.get("id").getAsString();
            if (id.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new FabricModInfo(id, string(o, "name"), string(o, "version")));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Every mod id a jar brings: its own and those of the jars nested in {@code META-INF/jars/} (recursively, up to
     * {@value #MAX_NESTED_DEPTH} levels). Fabric names the provider of a failing entrypoint by one of these ids.
     *
     * @param jar a mod jar
     * @return mod ids (empty when the jar cannot be read)
     */
    public static Set<String> providedIds(final Path jar) {
        final Set<String> ids = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            read(zip).ifPresent(i -> ids.add(i.id()));
            final var entries = zip.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry e = entries.nextElement();
                if (isNestedJar(e.getName()) && e.getSize() <= MAX_NESTED_JAR_BYTES) {
                    try (InputStream in = zip.getInputStream(e)) {
                        final byte[] bytes = in.readNBytes((int) MAX_NESTED_JAR_BYTES + 1);
                        if (bytes.length <= MAX_NESTED_JAR_BYTES) {
                            nestedIds(bytes, 1, ids);
                        }
                    } catch (IOException | RuntimeException ex) {
                        LOG.log(Level.FINE, "Unreadable nested jar {0} in {1}", new Object[] {e.getName(), jar});
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Could not read {0}: {1}", new Object[] {jar, e.toString()});
        }
        return ids;
    }

    private static void nestedIds(final byte[] jar, final int depth, final Set<String> ids) throws IOException {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (FILE.equals(e.getName())) {
                    parse(zin.readNBytes((int) MAX_JSON_BYTES)).ifPresent(i -> ids.add(i.id()));
                } else if (depth < MAX_NESTED_DEPTH && isNestedJar(e.getName())) {
                    final byte[] bytes = zin.readNBytes((int) MAX_NESTED_JAR_BYTES + 1);
                    if (bytes.length <= MAX_NESTED_JAR_BYTES) {
                        try {
                            nestedIds(bytes, depth + 1, ids);
                        } catch (IOException | RuntimeException ex) {
                            LOG.log(Level.FINE, "Unreadable nested jar {0}", e.getName());
                        }
                    }
                }
            }
        }
    }

    /**
     * @param entryName zip entry name
     * @return whether it is a jar nested by Fabric's jar-in-jar ({@code META-INF/jars/*.jar})
     */
    static boolean isNestedJar(final String entryName) {
        return entryName.startsWith(NESTED_JARS) && entryName.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static String string(final JsonObject o, final String key) {
        final JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString().trim() : "";
    }
}
