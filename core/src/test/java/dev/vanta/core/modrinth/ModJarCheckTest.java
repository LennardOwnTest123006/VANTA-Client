package dev.vanta.core.modrinth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModJarCheckTest {
    @TempDir
    Path dir;

    private Path write(String name, byte[] bytes) throws Exception {
        return Files.write(dir.resolve(name), bytes);
    }

    private Path modJar(String source) throws Exception {
        return write("mod.jar", ModJarFixtures.modJar("smartfpsbooster", "Smart FPS Booster", "1.0.0",
                ModJarFixtures.compile(dir, source)));
    }

    @Test
    void flagsTheOldConstructorWithAnInputType() throws Exception {
        ModJarCheck.Result result = ModJarCheck.check(modJar(ModJarFixtures.OLD_TYPE_MOD));
        assertEquals(ModJarCheck.Status.FLAGGED, result.status());
        assertTrue(result.flagged());
        assertEquals("smartfpsbooster", result.modId());
        assertEquals("Smart FPS Booster", result.modName());
        assertEquals("1.0.0", result.modVersion());
        assertEquals("com.example.oldmod.KeybindManager", result.className());
        assertEquals("(Ljava/lang/String;Lnet/minecraft/class_3675$class_307;ILjava/lang/String;)V",
                result.descriptor());
        assertEquals("com/example/oldmod/KeybindManager.class", result.location());
        assertEquals("built for an older Minecraft: it creates key bindings the way Minecraft did before 1.21.9, so "
                + "Minecraft 1.21.11 would stop while starting", result.reason());
    }

    @Test
    void flagsTheOldConstructorWithAnIntKey() throws Exception {
        ModJarCheck.Result result = ModJarCheck.check(modJar(ModJarFixtures.OLD_INT_MOD));
        assertTrue(result.flagged());
        assertEquals("(Ljava/lang/String;ILjava/lang/String;)V", result.descriptor());
    }

    @Test
    void multiVersionJarThatAlsoUsesTheCategoryApiIsNotFlagged() throws Exception {
        ModJarCheck.Result result = ModJarCheck.check(modJar(ModJarFixtures.MULTI_VERSION_MOD));
        assertEquals(ModJarCheck.Status.OK, result.status());
        assertEquals("", result.reason());
        assertEquals("", result.className());
    }

    @Test
    void categoryApiInAnotherClassOfTheSameJarAlsoCounts() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>(ModJarFixtures.compile(dir, ModJarFixtures.OLD_TYPE_MOD));
        entries.putAll(ModJarFixtures.compile(dir, ModJarFixtures.MODERN_MOD));
        Path jar = write("both.jar", ModJarFixtures.modJar("both", "Both", "2.0", entries));
        assertEquals(ModJarCheck.Status.OK, ModJarCheck.check(jar).status());
    }

    @Test
    void modernJarIsNotFlagged() throws Exception {
        ModJarCheck.Result result = ModJarCheck.check(modJar(ModJarFixtures.MODERN_MOD));
        assertEquals(ModJarCheck.Status.OK, result.status());
        assertTrue(result.classes() >= 1);
    }

    @Test
    void sameDescriptorOnAnotherClassIsNotFlagged() throws Exception {
        ModJarCheck.Result result = ModJarCheck.check(modJar(ModJarFixtures.UNRELATED_MOD));
        assertEquals(ModJarCheck.Status.OK, result.status());
        assertEquals(1, result.classes());
    }

    @Test
    void oldConstructorInANestedJarIsFlagged() throws Exception {
        byte[] inner = ModJarFixtures.modJar("innerlib", "Inner", "0.1", ModJarFixtures.compile(dir,
                ModJarFixtures.OLD_INT_MOD));
        Map<String, byte[]> outer = new LinkedHashMap<>(ModJarFixtures.compile(dir, ModJarFixtures.UNRELATED_MOD));
        outer.put("META-INF/jars/innerlib-0.1.jar", inner);
        Path jar = write("outer.jar", ModJarFixtures.modJar("outer", "Outer Mod", "3.1", outer));
        ModJarCheck.Result result = ModJarCheck.check(jar);
        assertTrue(result.flagged());
        assertEquals("outer", result.modId(), "names come from the top-level fabric.mod.json");
        assertEquals("Outer Mod", result.modName());
        assertEquals("com.example.oldmod.KeybindManager", result.className());
        assertEquals("META-INF/jars/innerlib-0.1.jar!/com/example/oldmod/KeybindManager.class", result.location());
    }

    @Test
    void nestedJarUsingTheCategoryApiMakesTheWholeJarMultiVersion() throws Exception {
        byte[] inner = ModJarFixtures.zip(ModJarFixtures.compile(dir, ModJarFixtures.MODERN_MOD));
        Map<String, byte[]> outer = new LinkedHashMap<>(ModJarFixtures.compile(dir, ModJarFixtures.OLD_INT_MOD));
        outer.put("META-INF/jars/compat.jar", inner);
        Path jar = write("outer.jar", ModJarFixtures.modJar("outer", "Outer", "1", outer));
        assertEquals(ModJarCheck.Status.OK, ModJarCheck.check(jar).status());
    }

    @Test
    void jarOutsideMetaInfJarsIsNotOpened() throws Exception {
        byte[] inner = ModJarFixtures.zip(ModJarFixtures.compile(dir, ModJarFixtures.OLD_INT_MOD));
        Path jar = write("outer.jar", ModJarFixtures.modJar("outer", "Outer", "1", Map.of("assets/x/inner.jar",
                inner)));
        assertEquals(ModJarCheck.Status.OK, ModJarCheck.check(jar).status());
    }

    @Test
    void malformedClassesAreSkipped() throws Exception {
        Map<String, byte[]> classes = ModJarFixtures.compile(dir, ModJarFixtures.OLD_TYPE_MOD);
        byte[] real = classes.values().iterator().next();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("com/example/Garbage.class", "not a class".getBytes(StandardCharsets.UTF_8));
        entries.put("com/example/Empty.class", new byte[0]);
        entries.put("com/example/Truncated.class", Arrays.copyOf(real, 40)); // ends inside the constant pool
        entries.put("com/example/Header.class", Arrays.copyOf(real, 9));
        byte[] badTag = real.clone();
        badTag[10] = (byte) 99; // first constant pool tag: not a valid tag
        entries.put("com/example/BadTag.class", badTag);
        Path broken = write("broken.jar", ModJarFixtures.modJar("broken", "Broken", "1", entries));
        ModJarCheck.Result result = ModJarCheck.check(broken);
        assertEquals(ModJarCheck.Status.OK, result.status());
        assertEquals(0, result.classes());

        entries.putAll(classes);
        Path mixed = write("mixed.jar", ModJarFixtures.modJar("mixed", "Mixed", "1", entries));
        ModJarCheck.Result flagged = ModJarCheck.check(mixed);
        assertTrue(flagged.flagged(), "a malformed class does not hide a real finding");
        assertEquals(1, flagged.classes());
    }

    @Test
    void fileThatIsNotAZipIsNotChecked() throws Exception {
        ModJarCheck.Result result = ModJarCheck.check(write("fake.jar",
                "fake jar content".getBytes(StandardCharsets.UTF_8)));
        assertEquals(ModJarCheck.Status.NOT_CHECKED, result.status());
        assertFalse(result.flagged());
        assertFalse(result.checked());
        assertFalse(result.detail().isEmpty());
        assertEquals(ModJarCheck.Status.NOT_CHECKED, ModJarCheck.check(dir.resolve("missing.jar")).status());
        assertEquals(ModJarCheck.Status.NOT_CHECKED, ModJarCheck.check(write("empty.jar", new byte[0])).status());
    }

    @Test
    void jarWithoutFabricModJsonStillGetsChecked() throws Exception {
        Path jar = write("plain.jar", ModJarFixtures.zip(ModJarFixtures.compile(dir, ModJarFixtures.OLD_INT_MOD)));
        ModJarCheck.Result result = ModJarCheck.check(jar);
        assertTrue(result.flagged());
        assertEquals("", result.modId());
        assertEquals("", result.modName());
    }
}
