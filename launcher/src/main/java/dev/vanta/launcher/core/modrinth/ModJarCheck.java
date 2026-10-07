package dev.vanta.launcher.core.modrinth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * "Mod built for an older Minecraft": finds a mod jar that would stop Minecraft 1.21.11 while it starts, by reading the
 * class files' constant pools (no class is loaded or run).
 *
 * <p>Minecraft 1.21.9 replaced the String category of a key binding ({@code KeyMapping}, intermediary
 * {@code net/minecraft/class_304}) with {@code KeyMapping.Category} ({@code class_304$class_11900}); the constructors
 * {@code (String, int, String)} and {@code (String, InputConstants.Type, int, String)} no longer exist. A mod compiled
 * against 1.21.8 or older that still creates its key bindings that way fails with {@link NoSuchMethodError} in its
 * client entrypoint, and Fabric stops the game ("Could not execute entrypoint stage 'client' due to errors"). Mods on
 * Modrinth are in intermediary names, so exactly these names are in their class files.</p>
 *
 * <ul>
 *   <li>Every {@code *.class} entry is read, and recursively every jar in {@code META-INF/jars/} (Fabric's jar-in-jar,
 *       up to {@value FabricModInfo#MAX_NESTED_DEPTH} levels).</li>
 *   <li>Finding: a {@code CONSTANT_Methodref} of {@code class_304.<init>} with one of the two String-category
 *       descriptors.</li>
 *   <li>Not flagged when any class of the same jar (nested jars included) references {@code class_304$class_11900}:
 *       such a jar also uses the 1.21.9+ API and picks the constructor at run time.</li>
 *   <li>A malformed class is skipped. Class entries over 8 MiB and nested jars over 64 MiB are skipped; after 512 MiB
 *       decompressed or 50,000 entries the jar is "not checked". A file that is not a readable zip is "not checked";
 *       that never blocks a mod on its own.</li>
 * </ul>
 */
public final class ModJarCheck {

    /** {@code KeyMapping} in intermediary names. */
    public static final String KEY_MAPPING = "net/minecraft/class_304";
    /** {@code KeyMapping.Category} (new in 1.21.9) in intermediary names. */
    public static final String KEY_MAPPING_CATEGORY = "net/minecraft/class_304$class_11900";
    /** {@code KeyMapping(String, int, String)}: gone since 1.21.9. */
    public static final String OLD_CONSTRUCTOR = "(Ljava/lang/String;ILjava/lang/String;)V";
    /** {@code KeyMapping(String, InputConstants.Type, int, String)}: gone since 1.21.9. */
    public static final String OLD_CONSTRUCTOR_WITH_TYPE = "(Ljava/lang/String;Lnet/minecraft/class_3675$class_307;ILjava/lang/String;)V";
    /** The two constructors with a String category. */
    public static final Set<String> OLD_CONSTRUCTORS = Set.of(OLD_CONSTRUCTOR, OLD_CONSTRUCTOR_WITH_TYPE);
    /** Why a flagged jar is switched off or not installed. */
    public static final String REASON = "built for an older Minecraft: it creates key bindings the way Minecraft did before 1.21.9, "
        + "so Minecraft 1.21.11 would stop while starting";

    static final long MAX_CLASS_BYTES = 8L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;
    static final int MAX_ENTRIES = 50_000;

    private static final int MAGIC = 0xCAFEBABE;

    private ModJarCheck() {
    }

    /** Outcome of a check. */
    public enum Status {
        /** The jar creates key bindings with a constructor Minecraft 1.21.11 does not have. */
        FLAGGED,
        /** Nothing found. */
        PASSED,
        /** The file could not be read, or is too large to read completely (never blocks a mod on its own). */
        NOT_CHECKED
    }

    /**
     * Result of {@link #check}.
     *
     * @param status     outcome
     * @param jar        the checked file
     * @param mod        its top-level {@code fabric.mod.json}, when present
     * @param className  the class that references the old constructor (binary name with dots; empty unless flagged)
     * @param nestedJar  the nested jar that class is in ({@code META-INF/jars/...}; empty for the jar itself)
     * @param descriptor the old constructor's descriptor (empty unless flagged)
     * @param detail     for {@link Status#NOT_CHECKED}: why
     */
    public record Result(Status status, Path jar, Optional<FabricModInfo> mod, String className, String nestedJar, String descriptor,
                         String detail) {

        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(jar, "jar");
            mod = mod == null ? Optional.empty() : mod;
            className = className == null ? "" : className;
            nestedJar = nestedJar == null ? "" : nestedJar;
            descriptor = descriptor == null ? "" : descriptor;
            detail = detail == null ? "" : detail;
        }

        /** @return whether the jar would stop Minecraft 1.21.11 while starting */
        public boolean flagged() {
            return status == Status.FLAGGED;
        }

        /** @return {@link ModJarCheck#REASON} when flagged, else the detail */
        public String reason() {
            return flagged() ? REASON : detail;
        }

        /** @return mod id from {@code fabric.mod.json} (empty when unknown) */
        public String modId() {
            return mod.map(FabricModInfo::id).orElse("");
        }

        /** @return mod name from {@code fabric.mod.json} (the file name when unknown) */
        public String modName() {
            return mod.map(FabricModInfo::name).orElse(jar.getFileName().toString());
        }

        /** @return mod version from {@code fabric.mod.json} (empty when unknown) */
        public String modVersion() {
            return mod.map(FabricModInfo::version).orElse("");
        }
    }

    /**
     * Checks a mod jar (see the class description). Never throws for a bad file.
     *
     * @param jar the file
     * @return result
     */
    public static Result check(final Path jar) {
        Objects.requireNonNull(jar, "jar");
        final Scan scan = new Scan();
        Optional<FabricModInfo> mod = Optional.empty();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            mod = FabricModInfo.read(zip);
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements() && !scan.stopped()) {
                final ZipEntry entry = entries.nextElement();
                if (!scan.countEntry() || entry.isDirectory()) {
                    continue;
                }
                final String name = entry.getName();
                final boolean isClass = isClassFile(name);
                final boolean nested = FabricModInfo.isNestedJar(name);
                if (!isClass && !nested) {
                    continue;
                }
                final long max = isClass ? MAX_CLASS_BYTES : FabricModInfo.MAX_NESTED_JAR_BYTES;
                if (entry.getSize() > max) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    final byte[] bytes = scan.read(in, max);
                    if (bytes != null) {
                        if (isClass) {
                            scan.classFile(bytes, "");
                        } else {
                            scan.nestedJar(bytes, name, 1);
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    // A damaged entry is skipped like a malformed class.
                }
            }
        } catch (IOException | RuntimeException e) {
            return new Result(Status.NOT_CHECKED, jar, mod, "", "", "", "not checked: the file could not be read as a jar ("
                + e.getClass().getSimpleName() + ")");
        }
        if (scan.limitReached) {
            return new Result(Status.NOT_CHECKED, jar, mod, "", "", "", "not checked: the jar is too large to check completely");
        }
        if (scan.findingClass != null && !scan.usesNewApi) {
            return new Result(Status.FLAGGED, jar, mod, scan.findingClass, scan.findingJar, scan.findingDescriptor, "");
        }
        return new Result(Status.PASSED, jar, mod, "", "", "", "");
    }

    private static boolean isClassFile(final String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".class");
    }

    /** State of one {@link #check}: limits, the first finding and whether the 1.21.9+ API is referenced. */
    private static final class Scan {
        long bytes;
        int entries;
        boolean limitReached;
        boolean usesNewApi;
        String findingClass;
        String findingJar = "";
        String findingDescriptor;

        boolean stopped() {
            return limitReached;
        }

        /** @return false when the entry limit is reached */
        boolean countEntry() {
            if (++entries > MAX_ENTRIES) {
                limitReached = true;
                return false;
            }
            return true;
        }

        /**
         * @return the entry's bytes, or null when it is larger than {@code max} (skipped) or the total budget ran out
         */
        byte[] read(final InputStream in, final long max) throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[64 * 1024];
            long size = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                size += n;
                bytes += n;
                if (bytes > MAX_TOTAL_BYTES) {
                    limitReached = true;
                    return null;
                }
                if (size > max) {
                    return null;
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }

        void nestedJar(final byte[] jar, final String path, final int depth) {
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jar))) {
                ZipEntry entry;
                while (!limitReached && (entry = zin.getNextEntry()) != null) {
                    if (!countEntry() || entry.isDirectory()) {
                        continue;
                    }
                    final String name = entry.getName();
                    if (isClassFile(name)) {
                        final byte[] b = read(zin, MAX_CLASS_BYTES);
                        if (b != null) {
                            classFile(b, path);
                        }
                    } else if (depth < FabricModInfo.MAX_NESTED_DEPTH && FabricModInfo.isNestedJar(name)) {
                        final byte[] b = read(zin, FabricModInfo.MAX_NESTED_JAR_BYTES);
                        if (b != null) {
                            nestedJar(b, path + "!/" + name, depth + 1);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                // A damaged nested jar is skipped; what was read from it counts.
            }
        }

        void classFile(final byte[] b, final String jarPath) {
            final ConstantPool pool = ConstantPool.parse(b);
            if (pool == null) {
                return;
            }
            if (pool.mentions(KEY_MAPPING_CATEGORY)) {
                usesNewApi = true;
            }
            if (findingClass == null) {
                pool.oldKeyMappingConstructor().ifPresent(descriptor -> {
                    findingClass = pool.thisClass().replace('/', '.');
                    findingJar = jarPath;
                    findingDescriptor = descriptor;
                });
            }
        }
    }

    /**
     * The constant pool of one class file (JVMS 4.4). Only what the check needs is kept: the tags, the two index
     * operands and the Utf8 strings.
     */
    static final class ConstantPool {
        private final int[] tags;
        private final int[] first;
        private final int[] second;
        private final String[] utf8;
        private final int thisClass;

        private ConstantPool(final int[] tags, final int[] first, final int[] second, final String[] utf8, final int thisClass) {
            this.tags = tags;
            this.first = first;
            this.second = second;
            this.utf8 = utf8;
            this.thisClass = thisClass;
        }

        /**
         * @param b class file bytes
         * @return the pool, or null when the bytes are not a well-formed class file header and constant pool
         */
        static ConstantPool parse(final byte[] b) {
            try {
                final ByteBuffer buf = ByteBuffer.wrap(b);
                if (buf.getInt() != MAGIC) {
                    return null;
                }
                buf.getShort();
                buf.getShort();
                final int count = u2(buf);
                final int[] tags = new int[Math.max(count, 1)];
                final int[] first = new int[tags.length];
                final int[] second = new int[tags.length];
                final String[] utf8 = new String[tags.length];
                for (int i = 1; i < count; i++) {
                    final int tag = buf.get() & 0xFF;
                    tags[i] = tag;
                    switch (tag) {
                        case 1 -> {
                            final int length = u2(buf);
                            utf8[i] = modifiedUtf8(b, buf.position(), length);
                            if (utf8[i] == null) {
                                return null;
                            }
                            buf.position(buf.position() + length);
                        }
                        case 3, 4 -> buf.position(buf.position() + 4);
                        case 5, 6 -> {
                            buf.position(buf.position() + 8);
                            i++; // long and double take two slots
                        }
                        case 7, 8, 16, 19, 20 -> first[i] = u2(buf);
                        case 9, 10, 11, 12, 17, 18 -> {
                            first[i] = u2(buf);
                            second[i] = u2(buf);
                        }
                        case 15 -> {
                            buf.get();
                            first[i] = u2(buf);
                        }
                        default -> {
                            return null;
                        }
                    }
                }
                u2(buf); // access flags
                final int thisClass = u2(buf);
                return new ConstantPool(tags, first, second, utf8, thisClass);
            } catch (BufferUnderflowException | IllegalArgumentException | IndexOutOfBoundsException e) {
                return null;
            }
        }

        /** @return whether a Utf8 constant contains the text (class names, descriptors, signatures, strings) */
        boolean mentions(final String text) {
            for (String s : utf8) {
                if (s != null && s.contains(text)) {
                    return true;
                }
            }
            return false;
        }

        /** @return the descriptor of a referenced {@code class_304.<init>} with a String category */
        Optional<String> oldKeyMappingConstructor() {
            for (int i = 1; i < tags.length; i++) {
                if (tags[i] != 10) {
                    continue;
                }
                final String owner = className(first[i]);
                if (!KEY_MAPPING.equals(owner)) {
                    continue;
                }
                final int nat = second[i];
                if (!valid(nat, 12) || !"<init>".equals(utf8At(first[nat]))) {
                    continue;
                }
                final String descriptor = utf8At(second[nat]);
                if (descriptor != null && OLD_CONSTRUCTORS.contains(descriptor)) {
                    return Optional.of(descriptor);
                }
            }
            return Optional.empty();
        }

        /** @return this class's internal name (empty when the index is invalid) */
        String thisClass() {
            final String name = className(thisClass);
            return name == null ? "" : name;
        }

        private String className(final int index) {
            return valid(index, 7) ? utf8At(first[index]) : null;
        }

        private String utf8At(final int index) {
            return valid(index, 1) ? utf8[index] : null;
        }

        private boolean valid(final int index, final int tag) {
            return index > 0 && index < tags.length && tags[index] == tag;
        }

        private static int u2(final ByteBuffer buf) {
            return buf.getShort() & 0xFFFF;
        }

        /** @return the decoded constant, or null when it is not valid modified UTF-8 */
        private static String modifiedUtf8(final byte[] b, final int offset, final int length) {
            if (offset + length > b.length) {
                throw new BufferUnderflowException();
            }
            boolean ascii = true;
            for (int i = offset; i < offset + length; i++) {
                if (b[i] <= 0) {
                    ascii = false;
                    break;
                }
            }
            if (ascii) {
                return new String(b, offset, length, StandardCharsets.ISO_8859_1);
            }
            final byte[] withLength = new byte[length + 2];
            withLength[0] = (byte) (length >>> 8);
            withLength[1] = (byte) length;
            System.arraycopy(b, offset, withLength, 2, length);
            try {
                return new DataInputStream(new ByteArrayInputStream(withLength)).readUTF();
            } catch (IOException e) {
                return null;
            }
        }
    }
}
