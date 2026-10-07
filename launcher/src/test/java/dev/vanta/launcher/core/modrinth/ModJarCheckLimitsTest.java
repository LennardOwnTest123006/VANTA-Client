package dev.vanta.launcher.core.modrinth;

import dev.vanta.launcher.testutil.ModJars;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check against what it must not flag and against hostile jars: the 1.21.9+ constructor alone, a look-alike
 * descriptor, nesting limits, oversized entries, a decompression budget, too many entries and hand-made class files
 * with broken indices.
 */
class ModJarCheckLimitsTest {

    @TempDir
    static Path tmp;
    private static ModJars fixtures;

    @BeforeAll
    static void compile() throws IOException {
        fixtures = ModJars.compile(tmp.resolve("fixtures"));
    }

    private static Path write(final String name, final byte[] bytes) throws IOException {
        final Path p = tmp.resolve(name);
        Files.write(p, bytes);
        return p;
    }

    @Test
    void aJarUsingOnlyTheNewConstructorIsNotFlagged() throws IOException {
        final ModJarCheck.ConstantPool pool = ModJarCheck.ConstantPool.parse(fixtures.classFile(ModJars.NEW_API_ONLY));
        assertNotNull(pool);
        assertTrue(pool.oldKeyMappingConstructor().isEmpty());
        assertTrue(pool.mentions(ModJarCheck.KEY_MAPPING_CATEGORY));
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("modern.jar",
            fixtures.modJar("modern", "Modern", "1", ModJars.NEW_API_ONLY))).status());
    }

    @Test
    void aClassUsingBothOldConstructorsIsFlagged() throws IOException {
        final ModJarCheck.Result r = ModJarCheck.check(write("both.jar", fixtures.modJar("both", "Both", "1", ModJars.BOTH_OLD)));
        assertEquals(ModJarCheck.Status.FLAGGED, r.status());
        assertEquals(ModJars.BOTH_OLD, r.className());
        assertTrue(ModJarCheck.OLD_CONSTRUCTORS.contains(r.descriptor()), r.descriptor());
    }

    @Test
    void aMethodOfKeyMappingWithTheSameDescriptorIsNotAConstructor() throws IOException {
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("lookalike.jar",
            fixtures.modJar("lookalike", "Lookalike", "1", ModJars.LOOKALIKE, ModJars.UNRELATED, ModJars.UNRELATED_OPTION))).status());
    }

    @Test
    void nestedJarsAreFollowedThreeLevelsDeepAndNoFurther() throws IOException {
        byte[] jar = fixtures.modJar("level4", "Level 4", "1", ModJars.OLD_KEYBINDS);
        for (int level = 3; level >= 1; level--) {
            jar = fixtures.modJar("level" + level, "Level " + level, "1", Map.of("META-INF/jars/level" + (level + 1) + ".jar", jar));
        }
        // outer -> level1 -> level2 -> level3 -> level4 (the class is four jars deep): not followed.
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("four-deep.jar",
            fixtures.modJar("outer", "Outer", "1", Map.of("META-INF/jars/level1.jar", jar)))).status());

        byte[] three = fixtures.modJar("level3", "Level 3", "1", ModJars.OLD_KEYBINDS);
        for (int level = 2; level >= 1; level--) {
            three = fixtures.modJar("level" + level, "Level " + level, "1", Map.of("META-INF/jars/level" + (level + 1) + ".jar", three));
        }
        final ModJarCheck.Result r = ModJarCheck.check(write("three-deep.jar",
            fixtures.modJar("outer", "Outer", "1", Map.of("META-INF/jars/level1.jar", three))));
        assertEquals(ModJarCheck.Status.FLAGGED, r.status());
        assertEquals("META-INF/jars/level1.jar!/META-INF/jars/level2.jar!/META-INF/jars/level3.jar", r.nestedJar());
    }

    @Test
    void aClassEntryOverEightMegabytesIsSkipped() throws IOException {
        final byte[] old = fixtures.classFile(ModJars.OLD_KEYBINDS);
        // Trailing bytes after a class file do not matter to the constant pool: the size alone decides.
        final byte[] justUnder = Arrays.copyOf(old, (int) ModJarCheck.MAX_CLASS_BYTES);
        final byte[] over = Arrays.copyOf(old, (int) ModJarCheck.MAX_CLASS_BYTES + 1);
        assertEquals(ModJarCheck.Status.FLAGGED, ModJarCheck.check(write("big-class-ok.jar", fixtures.modJar("big", "Big", "1",
            Map.of(ModJars.entryName(ModJars.OLD_KEYBINDS), justUnder)))).status());
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("big-class.jar", fixtures.modJar("big", "Big", "1",
            Map.of(ModJars.entryName(ModJars.OLD_KEYBINDS), over)))).status());
        // Inside a nested jar too.
        final byte[] inner = fixtures.modJar("inner", "Inner", "1", Map.of(ModJars.entryName(ModJars.OLD_KEYBINDS), over));
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("big-nested-class.jar", fixtures.modJar("big", "Big", "1",
            Map.of("META-INF/jars/inner.jar", inner)))).status());
    }

    @Test
    void aNestedJarOverSixtyFourMegabytesIsSkippedAndTheRestIsStillChecked() throws IOException {
        // A nested jar of 64 MiB + 1 KiB (stored, so it really is that large) that holds an old key binding: skipped.
        final ByteArrayOutputStream innerBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(innerBytes)) {
            stored(zip, ModJars.entryName(ModJars.OLD_KEYBINDS), fixtures.classFile(ModJars.OLD_KEYBINDS));
            stored(zip, "pad.bin", new byte[(int) FabricModInfo.MAX_NESTED_JAR_BYTES + 1024]);
        }
        final byte[] inner = innerBytes.toByteArray();
        assertTrue(inner.length > FabricModInfo.MAX_NESTED_JAR_BYTES);
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("huge-nested.jar", fixtures.modJar("huge", "Huge", "1",
            Map.of("META-INF/jars/huge.jar", inner)))).status());
        // The same jar with an old key binding of its own is still flagged.
        assertEquals(ModJarCheck.Status.FLAGGED, ModJarCheck.check(write("huge-nested-and-old.jar", fixtures.modJar("huge", "Huge", "1",
            Map.of("META-INF/jars/huge.jar", inner), ModJars.OLD_KEYBINDS_WITH_TYPE))).status());
    }

    @Test
    void moreThanTheDecompressionBudgetIsNotChecked() throws IOException {
        // 65 class entries of 8 MiB of zeros each (tiny when deflated): 520 MiB decompressed, over the 512 MiB budget.
        final Path jar = tmp.resolve("bomb.jar");
        final byte[] zeros = new byte[(int) ModJarCheck.MAX_CLASS_BYTES];
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(ModJars.entryName(ModJars.OLD_KEYBINDS)));
            zip.write(fixtures.classFile(ModJars.OLD_KEYBINDS));
            zip.closeEntry();
            for (int i = 0; i < 65; i++) {
                zip.putNextEntry(new ZipEntry("pad/Zero" + i + ".class"));
                zip.write(zeros);
                zip.closeEntry();
            }
        }
        assertTrue(Files.size(jar) < 2 * 1024 * 1024, "a small file");
        final ModJarCheck.Result r = ModJarCheck.check(jar);
        assertEquals(ModJarCheck.Status.NOT_CHECKED, r.status(), "never blocks on its own");
        assertTrue(r.reason().startsWith("not checked"), r.reason());
    }

    @Test
    void moreThanFiftyThousandEntriesIsNotChecked() throws IOException {
        final Path jar = tmp.resolve("many.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(ModJars.entryName(ModJars.OLD_KEYBINDS)));
            zip.write(fixtures.classFile(ModJars.OLD_KEYBINDS));
            zip.closeEntry();
            for (int i = 0; i < ModJarCheck.MAX_ENTRIES; i++) {
                zip.putNextEntry(new ZipEntry("e/" + i));
                zip.closeEntry();
            }
        }
        assertEquals(ModJarCheck.Status.NOT_CHECKED, ModJarCheck.check(jar).status());

        final Path fewer = tmp.resolve("fewer.jar");
        try (OutputStream out = Files.newOutputStream(fewer); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(ModJars.entryName(ModJars.OLD_KEYBINDS)));
            zip.write(fixtures.classFile(ModJars.OLD_KEYBINDS));
            zip.closeEntry();
            for (int i = 0; i < ModJarCheck.MAX_ENTRIES - 1; i++) {
                zip.putNextEntry(new ZipEntry("e/" + i));
                zip.closeEntry();
            }
        }
        assertEquals(ModJarCheck.Status.FLAGGED, ModJarCheck.check(fewer).status(), "exactly 50,000 entries are still checked");
    }

    @Test
    void handMadeClassFilesWithBrokenIndicesNeverThrow() throws IOException {
        // #1 Methodref -> class #2, name-and-type #9 (beyond the pool); #2 Class -> #3; #3 Utf8 KeyMapping;
        // #4 Methodref -> class #2, name-and-type #1 (not a NameAndType); this_class #99.
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream d = new DataOutputStream(bytes);
        d.writeInt(0xCAFEBABE);
        d.writeShort(0);
        d.writeShort(65);
        d.writeShort(5);
        d.writeByte(10);
        d.writeShort(2);
        d.writeShort(9);
        d.writeByte(7);
        d.writeShort(3);
        d.writeByte(1);
        d.writeUTF(ModJarCheck.KEY_MAPPING);
        d.writeByte(10);
        d.writeShort(2);
        d.writeShort(1);
        d.writeShort(0x21);
        d.writeShort(99);
        final byte[] dangling = bytes.toByteArray();
        final ModJarCheck.ConstantPool pool = ModJarCheck.ConstantPool.parse(dangling);
        assertNotNull(pool);
        assertTrue(pool.oldKeyMappingConstructor().isEmpty());
        assertEquals("", pool.thisClass());

        // A constant count far beyond the bytes, a Utf8 running past the end, invalid modified UTF-8 and a long as the
        // last constant (its second slot is outside the pool).
        final byte[] hugeCount = Arrays.copyOf(dangling, dangling.length);
        hugeCount[8] = (byte) 0xFF;
        hugeCount[9] = (byte) 0xFF;
        final byte[] utf8PastEnd = Arrays.copyOf(fixtures.classFile(ModJars.OLD_KEYBINDS), 200);
        final byte[] badUtf8 = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 65, 0, 2, 1, 0, 2, (byte) 0xFF, (byte) 0xFF, 0, 0x21, 0, 1};
        final byte[] trailingLong = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 65, 0, 2, 5, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0x21, 0, 1};
        for (byte[] b : new byte[][] {hugeCount, utf8PastEnd, badUtf8}) {
            assertEquals(null, ModJarCheck.ConstantPool.parse(b));
        }
        assertNotNull(ModJarCheck.ConstantPool.parse(trailingLong), "a long in the last slot does not run past the pool");

        final Path jar = write("hand-made.jar", fixtures.modJar("hand", "Hand", "1", Map.of("a/Dangling.class", dangling,
            "a/HugeCount.class", hugeCount, "a/PastEnd.class", utf8PastEnd, "a/BadUtf8.class", badUtf8, "a/Long.class", trailingLong,
            "a/Text.class", "CAFEBABE".getBytes(StandardCharsets.US_ASCII))));
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(jar).status());
    }

    @Test
    void aTruncatedJarIsNotChecked() throws IOException {
        final byte[] full = fixtures.modJar("smartfpsbooster", "Smart FPS Booster", "1.0.0", ModJars.OLD_KEYBINDS);
        final ModJarCheck.Result r = ModJarCheck.check(write("truncated.jar", Arrays.copyOf(full, full.length / 2)));
        assertEquals(ModJarCheck.Status.NOT_CHECKED, r.status(), "a download cut off half-way never blocks on its own");
    }

    private static void stored(final ZipOutputStream zip, final String name, final byte[] content) throws IOException {
        final ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(content.length);
        e.setCompressedSize(content.length);
        final CRC32 crc = new CRC32();
        crc.update(content);
        e.setCrc(crc.getValue());
        zip.putNextEntry(e);
        zip.write(content);
        zip.closeEntry();
    }
}
