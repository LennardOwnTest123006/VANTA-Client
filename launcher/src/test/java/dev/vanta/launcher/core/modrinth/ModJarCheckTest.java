package dev.vanta.launcher.core.modrinth;

import dev.vanta.launcher.testutil.ModJars;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Built for an older Minecraft" on real bytecode: fixtures compiled with javax.tools against stubs of the intermediary
 * names a Modrinth mod references ({@link ModJars}).
 */
class ModJarCheckTest {

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
    void theFixturesReferenceTheConstructorsMinecraft1218Had() throws IOException {
        final ModJarCheck.ConstantPool pool = ModJarCheck.ConstantPool.parse(fixtures.classFile(ModJars.OLD_KEYBINDS));
        assertNotNull(pool, "javac output parses (long/double, lambdas and string concatenation included)");
        assertEquals("com/smartclient/fpsbooster/ui/KeybindManager", pool.thisClass());
        assertEquals(ModJarCheck.OLD_CONSTRUCTOR, pool.oldKeyMappingConstructor().orElseThrow());
        assertFalse(pool.mentions(ModJarCheck.KEY_MAPPING_CATEGORY));
    }

    @Test
    void aJarCreatingKeyBindingsWithTheStringCategoryIsFlagged() throws IOException {
        final Path jar = write("smart-fps-booster-1.0.0+mc1.21.4.jar",
            fixtures.modJar("smartfpsbooster", "Smart FPS Booster", "1.0.0+mc1.21.4", ModJars.OLD_KEYBINDS));
        final ModJarCheck.Result r = ModJarCheck.check(jar);
        assertEquals(ModJarCheck.Status.FLAGGED, r.status());
        assertTrue(r.flagged());
        assertEquals("smartfpsbooster", r.modId());
        assertEquals("Smart FPS Booster", r.modName());
        assertEquals("1.0.0+mc1.21.4", r.modVersion());
        assertEquals(ModJars.OLD_KEYBINDS, r.className());
        assertEquals("(Ljava/lang/String;ILjava/lang/String;)V", r.descriptor());
        assertEquals("", r.nestedJar());
        assertEquals("built for an older Minecraft: it creates key bindings the way Minecraft did before 1.21.9, so Minecraft 1.21.11"
            + " would stop while starting", r.reason());
    }

    @Test
    void theConstructorWithAnInputTypeIsFlaggedToo() throws IOException {
        final Path jar = write("typed.jar", fixtures.modJar("typed", "Typed Keys", "2.0", ModJars.OLD_KEYBINDS_WITH_TYPE));
        final ModJarCheck.Result r = ModJarCheck.check(jar);
        assertEquals(ModJarCheck.Status.FLAGGED, r.status());
        assertEquals("(Ljava/lang/String;Lnet/minecraft/class_3675$class_307;ILjava/lang/String;)V", r.descriptor());
        assertEquals(ModJars.OLD_KEYBINDS_WITH_TYPE, r.className());
    }

    @Test
    void aJarThatAlsoUsesKeyMappingCategoryIsNotFlagged() throws IOException {
        // The class picks the constructor at run time.
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("multi.jar",
            fixtures.modJar("multi", "Multi", "1", ModJars.MULTI_VERSION))).status());
        // Another class of the same jar uses the 1.21.9+ API: the jar supports both.
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("multi-split.jar",
            fixtures.modJar("multi", "Multi", "1", ModJars.OLD_KEYBINDS, ModJars.MULTI_VERSION))).status());
        // Also when that class is in a nested jar.
        final byte[] inner = fixtures.modJar("multi-new", "Multi new", "1", ModJars.MULTI_VERSION);
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("multi-nested.jar", fixtures.modJar("multi", "Multi", "1",
            Map.of("META-INF/jars/multi-new.jar", inner), ModJars.OLD_KEYBINDS))).status());
    }

    @Test
    void aNestedJarIsChecked() throws IOException {
        final byte[] inner = fixtures.modJar("inner-keys", "Inner keys", "1", ModJars.OLD_KEYBINDS_WITH_TYPE);
        final ModJarCheck.Result r = ModJarCheck.check(write("outer.jar",
            fixtures.modJar("outer", "Outer", "3.1", Map.of("META-INF/jars/inner-keys.jar", inner))));
        assertEquals(ModJarCheck.Status.FLAGGED, r.status());
        assertEquals("outer", r.modId(), "mod id, name and version come from the top-level fabric.mod.json");
        assertEquals("Outer", r.modName());
        assertEquals("3.1", r.modVersion());
        assertEquals("META-INF/jars/inner-keys.jar", r.nestedJar());
        assertEquals(ModJars.OLD_KEYBINDS_WITH_TYPE, r.className());

        // Two levels deep.
        final byte[] middle = fixtures.modJar("middle", "Middle", "1", Map.of("META-INF/jars/inner-keys.jar", inner));
        final ModJarCheck.Result deep = ModJarCheck.check(write("deep.jar",
            fixtures.modJar("deep", "Deep", "1", Map.of("META-INF/jars/middle.jar", middle))));
        assertEquals(ModJarCheck.Status.FLAGGED, deep.status());
        assertEquals("META-INF/jars/middle.jar!/META-INF/jars/inner-keys.jar", deep.nestedJar());
        // A jar outside META-INF/jars/ is not loaded by Fabric and not followed.
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("elsewhere.jar",
            fixtures.modJar("elsewhere", "Elsewhere", "1", Map.of("libs/inner-keys.jar", inner)))).status());
    }

    @Test
    void aStringIntStringConstructorOfAnotherClassIsNotFlagged() throws IOException {
        final ModJarCheck.Result r = ModJarCheck.check(write("unrelated.jar",
            fixtures.modJar("unrelated", "Unrelated", "1", ModJars.UNRELATED, ModJars.UNRELATED_OPTION)));
        assertEquals(ModJarCheck.Status.PASSED, r.status());
        assertFalse(r.flagged());
        assertEquals("", r.reason());
    }

    @Test
    void aMalformedClassIsSkipped() throws IOException {
        final byte[] truncated = java.util.Arrays.copyOf(fixtures.classFile(ModJars.OLD_KEYBINDS), 40);
        final byte[] badTag = fixtures.classFile(ModJars.UNRELATED_OPTION).clone();
        badTag[10] = (byte) 99; // the first constant's tag
        final Map<String, byte[]> broken = Map.of("a/Truncated.class", truncated, "a/BadTag.class", badTag,
            "a/NotAClass.class", "hello".getBytes(StandardCharsets.UTF_8), "a/Empty.class", new byte[0]);
        assertEquals(ModJarCheck.Status.PASSED, ModJarCheck.check(write("broken.jar", fixtures.modJar("broken", "Broken", "1", broken))).status(),
            "malformed classes are skipped, nothing is thrown");
        assertEquals(ModJarCheck.Status.FLAGGED, ModJarCheck.check(write("broken-and-old.jar",
            fixtures.modJar("broken", "Broken", "1", broken, ModJars.OLD_KEYBINDS))).status(), "the other classes are still checked");
        assertEquals(null, ModJarCheck.ConstantPool.parse(truncated));
        assertEquals(null, ModJarCheck.ConstantPool.parse(badTag));
    }

    @Test
    void aFileThatIsNotAZipIsNotChecked() throws IOException {
        final ModJarCheck.Result r = ModJarCheck.check(write("not-a-zip.jar", "not a zip".getBytes(StandardCharsets.UTF_8)));
        assertEquals(ModJarCheck.Status.NOT_CHECKED, r.status());
        assertFalse(r.flagged(), "never blocks on its own");
        assertTrue(r.reason().startsWith("not checked"), r.reason());
        assertEquals("not-a-zip.jar", r.modName(), "without fabric.mod.json the file name stands in");
        assertEquals(ModJarCheck.Status.NOT_CHECKED, ModJarCheck.check(tmp.resolve("missing.jar")).status());
    }

    @Test
    void aJarWithoutFabricModJsonIsStillChecked() throws IOException {
        final ModJarCheck.Result r = ModJarCheck.check(write("plain.jar",
            ModJars.zip(Map.of(ModJars.entryName(ModJars.OLD_KEYBINDS), fixtures.classFile(ModJars.OLD_KEYBINDS)))));
        assertEquals(ModJarCheck.Status.FLAGGED, r.status());
        assertEquals("", r.modId());
        assertEquals("plain.jar", r.modName());
    }
}
