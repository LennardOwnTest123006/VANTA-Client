package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Edge cases of {@link ModJarCheck}: constant pool shapes, nesting depth and the reading limits. */
class ModJarCheckEdgeCasesTest {
    @TempDir
    Path dir;

    private Path write(String name, byte[] bytes) throws IOException {
        return Files.write(dir.resolve(name), bytes);
    }

    /** Subclass of KeyMapping whose constructor calls the old super constructor. */
    private static final String OLD_SUBCLASS_MOD = """
            package com.example.submod;
            import net.minecraft.class_304;
            public class MyKey extends class_304 {
                public MyKey(String name, int key, String category) {
                    super(name, key, category);
                }
            }
            """;

    /** Old constructor reached through a constructor reference (MethodHandle, MethodType, InvokeDynamic). */
    private static final String OLD_CONSTRUCTOR_REFERENCE_MOD = """
            package com.example.refmod;
            import net.minecraft.class_304;
            public class Keys {
                interface Factory { Object make(String name, int key, String category); }
                public static final long BIG = System.nanoTime() > 0 ? 1234567890123L : 2L;
                public static Object register(long l, double d) {
                    long a = l + 9_876_543_210L;
                    double b = d * 3.14159;
                    Factory f = class_304::new;
                    Runnable r = () -> System.out.println("x" + a + b);
                    r.run();
                    return f.make("key.refmod" + a, 71, "category.refmod" + b);
                }
            }
            """;

    /** A mod's own class with the four-argument descriptor of the old Type constructor. */
    private static final String UNRELATED_TYPE_MOD = """
            package com.example.othermod;
            import net.minecraft.class_3675;
            public class Binding {
                public Binding(String name, class_3675.class_307 type, int key, String category) {}
                public static Object make() {
                    return new Binding("a", class_3675.class_307.KEYSYM, 1, "b");
                }
            }
            """;

    @Test
    void subclassCallingTheOldSuperConstructorIsFlagged() {
        ModJarCheck.Result result = check(OLD_SUBCLASS_MOD);
        assertTrue(result.flagged());
        assertEquals("com.example.submod.MyKey", result.className());
        assertEquals(ModJarCheck.OLD_CONSTRUCTOR_INT, result.descriptor());
    }

    @Test
    void constructorReferenceWithLongDoubleAndInvokeDynamicConstantsIsFlagged() {
        // Long and double constants take two constant pool slots; lambdas and string concatenation add MethodHandle,
        // MethodType and InvokeDynamic entries. A slot error would make the class unreadable and hide the finding.
        ModJarCheck.Result result = check(OLD_CONSTRUCTOR_REFERENCE_MOD);
        assertTrue(result.flagged(), result.toString());
        assertEquals("com.example.refmod.Keys", result.className());
        assertEquals(ModJarCheck.OLD_CONSTRUCTOR_INT, result.descriptor());
    }

    @Test
    void anotherClassWithTheOldTypeDescriptorIsNotFlagged() {
        assertEquals(ModJarCheck.Status.OK, check(UNRELATED_TYPE_MOD).status());
    }

    @Test
    void modernOnlyAndBothApisAreNotFlagged() throws IOException {
        assertEquals(ModJarCheck.Status.OK, check(ModJarFixtures.MODERN_MOD).status());
        assertEquals(ModJarCheck.Status.OK, check(ModJarFixtures.MULTI_VERSION_MOD).status());
        Map<String, byte[]> both = new LinkedHashMap<>(ModJarFixtures.compile(dir, ModJarFixtures.OLD_INT_MOD));
        both.putAll(ModJarFixtures.compile(dir, ModJarFixtures.OLD_TYPE_MOD.replace("oldmod", "oldmod2")));
        both.putAll(ModJarFixtures.compile(dir, ModJarFixtures.MODERN_MOD));
        assertEquals(ModJarCheck.Status.OK, ModJarCheck.check(write("both.jar",
                ModJarFixtures.modJar("both", "Both", "1", both))).status());
    }

    @Test
    void handWrittenClassWithModuleAndPackageConstantsIsParsed() throws IOException {
        Path jar = write("module.jar", ModJarFixtures.modJar("m", "M", "1",
                Map.of("module-info.class", moduleLikeClass())));
        ModJarCheck.Result result = ModJarCheck.check(jar);
        assertTrue(result.flagged(), result.toString());
        assertEquals(1, result.classes());
        assertEquals(ModJarCheck.OLD_CONSTRUCTOR_TYPE, result.descriptor());
    }

    @Test
    void nestedJarsAreReadDownToThreeLevels() throws IOException {
        byte[] old = ModJarFixtures.zip(ModJarFixtures.compile(dir, ModJarFixtures.OLD_TYPE_MOD));
        byte[] level3 = old;
        byte[] level2 = ModJarFixtures.zip(Map.of("META-INF/jars/l3.jar", level3));
        byte[] level1 = ModJarFixtures.zip(Map.of("META-INF/jars/l2.jar", level2));

        ModJarCheck.Result two = ModJarCheck.check(write("two.jar", ModJarFixtures.modJar("two", "Two", "2",
                Map.of("META-INF/jars/l2.jar", level2))));
        assertTrue(two.flagged(), "two levels deep");
        assertEquals("META-INF/jars/l2.jar!/META-INF/jars/l3.jar!/com/example/oldmod/KeybindManager.class",
                two.location());
        assertEquals("Two", two.modName());

        ModJarCheck.Result three = ModJarCheck.check(write("three.jar", ModJarFixtures.modJar("three", "Three", "3",
                Map.of("META-INF/jars/l1.jar", level1))));
        assertTrue(three.flagged(), "three levels deep");

        byte[] level0 = ModJarFixtures.zip(Map.of("META-INF/jars/l1.jar", level1));
        ModJarCheck.Result four = ModJarCheck.check(write("four.jar", ModJarFixtures.modJar("four", "Four", "4",
                Map.of("META-INF/jars/l0.jar", level0))));
        assertEquals(ModJarCheck.Status.OK, four.status(), "a fourth level is not opened");
    }

    @Test
    void classOverEightMibIsSkippedButTheRestIsChecked() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("com/example/Huge.class", new byte[(int) ModJarCheck.MAX_CLASS_BYTES + 1]);
        entries.putAll(ModJarFixtures.compile(dir, ModJarFixtures.OLD_INT_MOD));
        ModJarCheck.Result top = ModJarCheck.check(write("top.jar", ModJarFixtures.modJar("t", "T", "1", entries)));
        assertTrue(top.flagged(), top.toString());

        // The same inside a nested jar, where the entry size is only known by reading it.
        ModJarCheck.Result nested = ModJarCheck.check(write("nested.jar", ModJarFixtures.modJar("n", "N", "1",
                Map.of("META-INF/jars/inner.jar", ModJarFixtures.zip(entries)))));
        assertTrue(nested.flagged(), nested.toString());
    }

    @Test
    void nestedJarOverSixtyFourMibIsSkipped() throws IOException {
        Map<String, byte[]> inner = new LinkedHashMap<>(ModJarFixtures.compile(dir, ModJarFixtures.OLD_INT_MOD));
        inner.put("assets/padding.bin", new byte[(int) ModJarCheck.MAX_NESTED_JAR_BYTES]);
        byte[] big = storedZip(inner);
        assertTrue(big.length > ModJarCheck.MAX_NESTED_JAR_BYTES);
        ModJarCheck.Result result = ModJarCheck.check(write("big.jar", ModJarFixtures.modJar("b", "B", "1",
                Map.of("META-INF/jars/big.jar", big))));
        assertEquals(ModJarCheck.Status.OK, result.status(), result.toString());
    }

    @Test
    void moreThanFiveHundredTwelveMibDecompressedIsNotChecked() throws IOException {
        // A small nested jar that inflates to 520 MiB of zeros (skipped classes, but still read and counted), then
        // the old constructor: the scan stops at the budget and never blocks on a partial read.
        Map<String, byte[]> bomb = new LinkedHashMap<>(ModJarFixtures.compile(dir, ModJarFixtures.OLD_INT_MOD));
        byte[] zeros = new byte[(int) ModJarCheck.MAX_CLASS_BYTES + 1];
        for (int i = 0; i < 65; i++) {
            bomb.put("com/example/Pad" + i + ".class", zeros);
        }
        byte[] nested = ModJarFixtures.zip(bomb);
        assertTrue(nested.length < 4 * 1024 * 1024, "compresses well: " + nested.length);
        ModJarCheck.Result result = ModJarCheck.check(write("bomb.jar", ModJarFixtures.modJar("bomb", "Bomb", "1",
                Map.of("META-INF/jars/bomb.jar", nested))));
        assertEquals(ModJarCheck.Status.NOT_CHECKED, result.status(), result.toString());
        assertTrue(result.detail().contains("MiB"), result.detail());
        assertEquals("bomb", result.modId());
    }

    @Test
    void moreThanFiftyThousandEntriesIsNotChecked() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>(ModJarFixtures.compile(dir, ModJarFixtures.OLD_INT_MOD));
        for (int i = 0; i < ModJarCheck.MAX_ENTRIES; i++) {
            entries.put("assets/e/" + i + ".txt", new byte[0]);
        }
        ModJarCheck.Result result = ModJarCheck.check(write("many.jar", ModJarFixtures.modJar("many", "Many", "1",
                entries)));
        assertEquals(ModJarCheck.Status.NOT_CHECKED, result.status(), result.toString());

        Map<String, byte[]> nested = new LinkedHashMap<>();
        nested.put("META-INF/jars/many.jar", ModJarFixtures.zip(entries));
        ModJarCheck.Result inNested = ModJarCheck.check(write("outer.jar", ModJarFixtures.modJar("o", "O", "1",
                nested)));
        assertEquals(ModJarCheck.Status.NOT_CHECKED, inNested.status(), inNested.toString());
        assertTrue(result.detail().contains("entries") && inNested.detail().contains("entries"), inNested.detail());
    }

    @Test
    void truncatedClassesAtEveryLengthAreSkippedWithoutThrowing() throws IOException {
        byte[] real = ModJarFixtures.compile(dir, ModJarFixtures.OLD_TYPE_MOD).values().iterator().next();
        for (int length = 0; length < real.length; length += Math.max(1, real.length / 97)) {
            byte[] cut = java.util.Arrays.copyOf(real, length);
            ModJarCheck.Result result = ModJarCheck.check(write("cut.jar", ModJarFixtures.modJar("c", "C", "1",
                    Map.of("com/example/oldmod/KeybindManager.class", cut))));
            assertTrue(result.checked(), "length " + length);
        }
    }

    private ModJarCheck.Result check(String source) {
        try {
            return ModJarCheck.check(write("mod.jar", ModJarFixtures.modJar("mod", "Mod", "1",
                    ModJarFixtures.compile(dir, source))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A class file written by hand: its constant pool holds Module (19) and Package (20) constants, as module-info
     * does, followed by a Methodref to the old Type constructor.
     */
    private static byte[] moduleLikeClass() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(0xCAFEBABE);
            out.writeShort(0);
            out.writeShort(65);
            out.writeShort(13); // constant_pool_count: entries 1..12
            out.writeByte(1);
            out.writeUTF("module-info"); // #1
            out.writeByte(7);
            out.writeShort(1); // #2 Class module-info
            out.writeByte(1);
            out.writeUTF("com.example.m"); // #3
            out.writeByte(19);
            out.writeShort(3); // #4 Module
            out.writeByte(1);
            out.writeUTF("com/example/m"); // #5
            out.writeByte(20);
            out.writeShort(5); // #6 Package
            out.writeByte(1);
            out.writeUTF(ModJarCheck.KEY_MAPPING); // #7
            out.writeByte(7);
            out.writeShort(7); // #8 Class class_304
            out.writeByte(1);
            out.writeUTF("<init>"); // #9
            out.writeByte(1);
            out.writeUTF(ModJarCheck.OLD_CONSTRUCTOR_TYPE); // #10
            out.writeByte(12);
            out.writeShort(9);
            out.writeShort(10); // #11 NameAndType
            out.writeByte(10);
            out.writeShort(8);
            out.writeShort(11); // #12 Methodref
            out.writeShort(0x8000); // ACC_MODULE
            out.writeShort(2); // this_class
            out.writeShort(0); // super_class
            out.writeShort(0); // interfaces
            out.writeShort(0); // fields
            out.writeShort(0); // methods
            out.writeShort(0); // attributes
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Zips entries without compression, so the zip is at least as large as its content. */
    private static byte[] storedZip(Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                    ZipEntry entry = new ZipEntry(e.getKey());
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(e.getValue().length);
                    CRC32 crc = new CRC32();
                    crc.update(e.getValue());
                    entry.setCrc(crc.getValue());
                    zip.putNextEntry(entry);
                    zip.write(e.getValue());
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
