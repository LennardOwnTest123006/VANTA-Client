package dev.vanta.core.modrinth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Recognises a Fabric mod jar built for an older Minecraft that would stop Minecraft 1.21.11 while it starts.
 *
 * <p>Minecraft 1.21.9 replaced the key binding category {@code String} with {@code KeyMapping.Category}: the
 * constructors {@code KeyMapping(String, int, String)} and {@code KeyMapping(String, InputConstants.Type, int, String)}
 * are gone from 1.21.11. A mod whose client entrypoint still calls one of them fails with {@code NoSuchMethodError}
 * and Fabric aborts the start ("Could not execute entrypoint stage 'client'"). Fabric still loads such a jar when its
 * {@code fabric.mod.json} says e.g. {@code "minecraft": "~1.21.4"} (read as {@code >=1.21.4 <1.22}), so the jar's own
 * metadata cannot be trusted; this check reads its bytecode instead.
 *
 * <p>Mods on Modrinth are compiled against intermediary names, so the check looks for a {@code CONSTANT_Methodref} to
 * {@code net/minecraft/class_304.<init>} with one of the two {@code String}-category descriptors. A jar that also
 * references {@code net/minecraft/class_304$class_11900} (the 1.21.9+ category type) anywhere supports both APIs and
 * picks the constructor at run time, so it is not flagged.
 *
 * <p>Every {@code *.class} entry is read, and recursively every {@code *.jar} under {@code META-INF/jars/} (Fabric's
 * nested jars, up to depth 3). A malformed class file is skipped, never thrown. A file that is not a readable zip, or
 * that exceeds the reading limits, is {@linkplain Status#NOT_CHECKED not checked}: that result never blocks anything.
 *
 * <p>The launcher has its own implementation of the same definition; both must behave the same.
 */
public final class ModJarCheck {
    /** Intermediary name of {@code KeyMapping}. */
    public static final String KEY_MAPPING = "net/minecraft/class_304";
    /** Intermediary name of {@code KeyMapping.Category}, new in 1.21.9. */
    public static final String KEY_MAPPING_CATEGORY = "net/minecraft/class_304$class_11900";
    /** {@code KeyMapping(String, int, String)}, removed in 1.21.9. */
    public static final String OLD_CONSTRUCTOR_INT = "(Ljava/lang/String;ILjava/lang/String;)V";
    /** {@code KeyMapping(String, InputConstants.Type, int, String)}, removed in 1.21.9. */
    public static final String OLD_CONSTRUCTOR_TYPE =
            "(Ljava/lang/String;Lnet/minecraft/class_3675$class_307;ILjava/lang/String;)V";
    /** Both removed constructor descriptors. */
    public static final Set<String> OLD_CONSTRUCTORS = Set.of(OLD_CONSTRUCTOR_INT, OLD_CONSTRUCTOR_TYPE);
    /** Player-facing English reason for a flagged jar. */
    public static final String REASON = "built for an older Minecraft: it creates key bindings the way Minecraft did "
            + "before 1.21.9, so Minecraft 1.21.11 would stop while starting";

    static final long MAX_CLASS_BYTES = 8L * 1024 * 1024;
    static final long MAX_NESTED_JAR_BYTES = 64L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;
    static final int MAX_ENTRIES = 50_000;
    static final int MAX_DEPTH = 3;
    private static final String NESTED_JARS = "META-INF/jars/";

    /** Outcome of a check. */
    public enum Status {
        /** Read completely; nothing that would stop 1.21.11. */
        OK,
        /** References a key binding constructor that no longer exists in 1.21.11. */
        FLAGGED,
        /** Not a readable zip, or too large to read completely; never a reason to block anything. */
        NOT_CHECKED
    }

    /**
     * Result of {@link #check(Path)}.
     *
     * @param status      the outcome
     * @param modId       {@code id} from the top-level {@code fabric.mod.json}, empty when absent
     * @param modName     {@code name} from it, empty when absent
     * @param modVersion  {@code version} from it, empty when absent
     * @param className   the class (dotted binary name) that calls an old constructor, empty unless flagged
     * @param descriptor  the old constructor descriptor it calls, empty unless flagged
     * @param location    the entry holding that class, nested jars joined with {@code !/}, empty unless flagged
     * @param classes     number of class files parsed
     * @param detail      why a jar was not checked, empty otherwise
     */
    public record Result(Status status, String modId, String modName, String modVersion, String className,
                         String descriptor, String location, int classes, String detail) {
        public Result {
            Objects.requireNonNull(status, "status");
            modId = Objects.requireNonNullElse(modId, "");
            modName = Objects.requireNonNullElse(modName, "");
            modVersion = Objects.requireNonNullElse(modVersion, "");
            className = Objects.requireNonNullElse(className, "");
            descriptor = Objects.requireNonNullElse(descriptor, "");
            location = Objects.requireNonNullElse(location, "");
            detail = Objects.requireNonNullElse(detail, "");
        }

        /** True when the jar would stop Minecraft 1.21.11 while starting. */
        public boolean flagged() {
            return status == Status.FLAGGED;
        }

        /** True when the jar was read completely. */
        public boolean checked() {
            return status != Status.NOT_CHECKED;
        }

        /** {@link #REASON} when flagged, otherwise empty. */
        public String reason() {
            return flagged() ? REASON : "";
        }
    }

    private ModJarCheck() {
    }

    /** Checks a jar file. Never throws: an unreadable file is {@link Status#NOT_CHECKED}. */
    public static Result check(Path jar) {
        Scan scan = new Scan();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!scan.count()) {
                    return scan.notChecked("more than " + MAX_ENTRIES + " entries");
                }
                String name = entry.getName();
                if (entry.isDirectory()) {
                    continue;
                }
                long limit = limitFor(name, 0);
                if (limit < 0 && !name.equals("fabric.mod.json")) {
                    continue;
                }
                if (entry.getSize() > Math.max(limit, MAX_CLASS_BYTES)) {
                    continue;
                }
                byte[] bytes;
                try (InputStream in = zip.getInputStream(entry)) {
                    bytes = scan.read(in, Math.max(limit, MAX_CLASS_BYTES));
                }
                if (scan.exhausted) {
                    return scan.notChecked("more than " + (MAX_TOTAL_BYTES >> 20) + " MiB to read");
                }
                if (bytes == null) {
                    continue;
                }
                if (name.equals("fabric.mod.json")) {
                    scan.metadata(bytes);
                } else {
                    scan.entry(name, name, bytes, 0);
                    if (scan.exhausted || scan.tooManyEntries) {
                        return scan.notChecked(scan.tooManyEntries ? "more than " + MAX_ENTRIES + " entries"
                                : "more than " + (MAX_TOTAL_BYTES >> 20) + " MiB to read");
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return scan.notChecked("not a readable jar: " + e.getMessage());
        }
        return scan.result();
    }

    /** Read limit for an entry, or -1 when the entry is not read at all. */
    private static long limitFor(String name, int depth) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".class")) {
            return MAX_CLASS_BYTES;
        }
        if (lower.endsWith(".jar") && name.startsWith(NESTED_JARS) && depth < MAX_DEPTH) {
            return MAX_NESTED_JAR_BYTES;
        }
        return -1;
    }

    /** State of one top-level jar's scan. */
    private static final class Scan {
        private String modId = "";
        private String modName = "";
        private String modVersion = "";
        private String className = "";
        private String descriptor = "";
        private String location = "";
        private boolean usesNewApi;
        private int classes;
        private int entries;
        private long total;
        private boolean exhausted;
        private boolean tooManyEntries;

        /** Counts an entry; false once the entry limit is passed. */
        boolean count() {
            entries++;
            if (entries > MAX_ENTRIES) {
                tooManyEntries = true;
                return false;
            }
            return true;
        }

        /**
         * Reads an entry fully, or returns {@code null} when it is larger than {@code limit}. Counts every byte
         * against the per-jar budget and sets {@link #exhausted} when that runs out.
         */
        byte[] read(InputStream in, long limit) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            long size = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                size += n;
                total += n;
                if (total > MAX_TOTAL_BYTES) {
                    exhausted = true;
                    return null;
                }
                if (size > limit) {
                    // Too large to check: drain the rest without keeping it (still counted against the budget).
                    while ((n = in.read(buffer)) > 0) {
                        total += n;
                        if (total > MAX_TOTAL_BYTES) {
                            exhausted = true;
                            return null;
                        }
                    }
                    return null;
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }

        /** A class file or nested jar named {@code name}; {@code path} is its full location for the report. */
        void entry(String name, String path, byte[] bytes, int depth) {
            if (name.toLowerCase(Locale.ROOT).endsWith(".class")) {
                classFile(path, bytes);
            } else {
                nestedJar(path, bytes, depth + 1);
            }
        }

        private void nestedJar(String path, byte[] bytes, int depth) {
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!count()) {
                        return;
                    }
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName();
                    long limit = limitFor(name, depth);
                    if (limit < 0) {
                        continue;
                    }
                    byte[] content = read(zip, limit);
                    if (exhausted) {
                        return;
                    }
                    if (content != null) {
                        entry(name, path + "!/" + name, content, depth);
                        if (exhausted || tooManyEntries) {
                            return;
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                // An unreadable nested jar is skipped like a malformed class; the rest of the mod is still checked.
            }
        }

        private void classFile(String path, byte[] bytes) {
            ClassRefs refs = ClassRefs.parse(bytes);
            if (refs == null) {
                return;
            }
            classes++;
            if (refs.usesNewApi) {
                usesNewApi = true;
            }
            if (refs.oldConstructor != null && className.isEmpty()) {
                className = refs.className.replace('/', '.');
                descriptor = refs.oldConstructor;
                location = path;
            }
        }

        void metadata(byte[] bytes) {
            try {
                JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (root != null && root.isJsonObject()) {
                    JsonObject o = root.getAsJsonObject();
                    modId = Json.string(o, "id");
                    modName = Json.string(o, "name");
                    modVersion = Json.string(o, "version");
                }
            } catch (RuntimeException e) {
                // Unreadable metadata only costs the names in the report.
            }
        }

        Result notChecked(String why) {
            return new Result(Status.NOT_CHECKED, modId, modName, modVersion, "", "", "", classes, why);
        }

        Result result() {
            if (!className.isEmpty() && !usesNewApi) {
                return new Result(Status.FLAGGED, modId, modName, modVersion, className, descriptor, location,
                        classes, "");
            }
            return new Result(Status.OK, modId, modName, modVersion, "", "", "", classes, "");
        }
    }

    /** What one class file references, from its constant pool. */
    static final class ClassRefs {
        String className = "";
        String oldConstructor;
        boolean usesNewApi;

        private static final int UTF8 = 1;
        private static final int CLASS = 7;
        private static final int METHODREF = 10;
        private static final int NAME_AND_TYPE = 12;

        /** Parses a class file's constant pool; {@code null} when it is not a well-formed class file. */
        static ClassRefs parse(byte[] bytes) {
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
                if (in.readInt() != 0xCAFEBABE) {
                    return null;
                }
                in.readUnsignedShort(); // minor_version
                in.readUnsignedShort(); // major_version
                int count = in.readUnsignedShort();
                if (count == 0) {
                    return null;
                }
                int[] tags = new int[count];
                String[] utf8 = new String[count];
                int[] first = new int[count];
                int[] second = new int[count];
                ClassRefs refs = new ClassRefs();
                for (int i = 1; i < count; i++) {
                    int tag = in.readUnsignedByte();
                    tags[i] = tag;
                    switch (tag) {
                        case UTF8 -> {
                            utf8[i] = in.readUTF();
                            if (utf8[i].contains(KEY_MAPPING_CATEGORY)) {
                                refs.usesNewApi = true;
                            }
                        }
                        case 3, 4 -> in.readInt(); // Integer, Float
                        case 5, 6 -> { // Long, Double take two slots
                            in.readLong();
                            i++;
                        }
                        case CLASS, 8, 16, 19, 20 -> first[i] = in.readUnsignedShort(); // Class, String, MethodType,
                        // Module, Package
                        case 9, METHODREF, 11, NAME_AND_TYPE, 17, 18 -> { // Field/Method/InterfaceMethodref,
                            // NameAndType, Dynamic, InvokeDynamic
                            first[i] = in.readUnsignedShort();
                            second[i] = in.readUnsignedShort();
                        }
                        case 15 -> { // MethodHandle
                            in.readUnsignedByte();
                            first[i] = in.readUnsignedShort();
                        }
                        default -> {
                            return null;
                        }
                    }
                }
                in.readUnsignedShort(); // access_flags
                int thisClass = in.readUnsignedShort();
                String self = className(tags, utf8, first, thisClass);
                if (self == null) {
                    return null;
                }
                refs.className = self;
                for (int i = 1; i < count; i++) {
                    if (tags[i] != METHODREF) {
                        continue;
                    }
                    String owner = className(tags, utf8, first, first[i]);
                    int nat = second[i];
                    if (owner == null || !valid(tags, nat, NAME_AND_TYPE)) {
                        return null;
                    }
                    if (!KEY_MAPPING.equals(owner)) {
                        continue;
                    }
                    String name = utf8(tags, utf8, first[nat]);
                    String descriptor = utf8(tags, utf8, second[nat]);
                    if (name == null || descriptor == null) {
                        return null;
                    }
                    if (name.equals("<init>") && OLD_CONSTRUCTORS.contains(descriptor) && refs.oldConstructor == null) {
                        refs.oldConstructor = descriptor;
                    }
                }
                return refs;
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        private static boolean valid(int[] tags, int index, int tag) {
            return index > 0 && index < tags.length && tags[index] == tag;
        }

        private static String utf8(int[] tags, String[] utf8, int index) {
            return valid(tags, index, UTF8) ? utf8[index] : null;
        }

        private static String className(int[] tags, String[] utf8, int[] first, int index) {
            return valid(tags, index, CLASS) ? utf8(tags, utf8, first[index]) : null;
        }
    }
}
