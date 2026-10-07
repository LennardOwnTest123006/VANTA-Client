package dev.vanta.core.modrinth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Real bytecode for {@link ModJarCheck} tests: compiles mod sources with {@code javax.tools} against stubs of the
 * intermediary-named Minecraft classes (only the old and new {@code class_304} constructors matter) and jars the mod's
 * classes, without the stubs, like a mod published on Modrinth.
 */
final class ModJarFixtures {
    /** Stubs: {@code KeyMapping} (class_304) with both pre-1.21.9 constructors and the 1.21.9+ Category one. */
    static final Map<String, String> MINECRAFT_STUBS = Map.of(
            "net/minecraft/class_3675.java", """
                    package net.minecraft;
                    public class class_3675 {
                        public static class class_307 {
                            public static final class_307 KEYSYM = new class_307();
                        }
                    }
                    """,
            "net/minecraft/class_304.java", """
                    package net.minecraft;
                    public class class_304 {
                        public class_304(String name, int key, String category) {}
                        public class_304(String name, class_3675.class_307 type, int key, String category) {}
                        public class_304(String name, int key, class_11900 category) {}
                        public class_304(String name, class_3675.class_307 type, int key, class_11900 category) {}
                        public static class class_11900 {
                            public class_11900(String id) {}
                        }
                    }
                    """);

    /** Like Smart FPS Booster: {@code new KeyMapping(String, InputConstants.Type, int, String)}. */
    static final String OLD_TYPE_MOD = """
            package com.example.oldmod;
            import net.minecraft.class_304;
            import net.minecraft.class_3675;
            public class KeybindManager {
                public static Object register() {
                    return new class_304("key.oldmod.menu", class_3675.class_307.KEYSYM, 71, "category.oldmod");
                }
            }
            """;

    /** {@code new KeyMapping(String, int, String)}. */
    static final String OLD_INT_MOD = """
            package com.example.oldmod;
            import net.minecraft.class_304;
            public class KeybindManager {
                public static Object register() {
                    return new class_304("key.oldmod.menu", 71, "category.oldmod");
                }
            }
            """;

    /** Chooses the constructor at run time: references both APIs. */
    static final String MULTI_VERSION_MOD = """
            package com.example.multimod;
            import net.minecraft.class_304;
            public class KeybindManager {
                public static Object register(boolean modern) {
                    if (modern) {
                        return new class_304("key.multimod.menu", 71, new class_304.class_11900("multimod"));
                    }
                    return new class_304("key.multimod.menu", 71, "category.multimod");
                }
            }
            """;

    /** Only the 1.21.9+ API. */
    static final String MODERN_MOD = """
            package com.example.modernmod;
            import net.minecraft.class_304;
            public class KeybindManager {
                public static Object register() {
                    return new class_304("key.modernmod.menu", 71, new class_304.class_11900("modernmod"));
                }
            }
            """;

    /** Calls a {@code (String, int, String)} constructor of its own class, not of KeyMapping. */
    static final String UNRELATED_MOD = """
            package com.example.othermod;
            public class Other {
                public Other(String name, int key, String category) {}
                public static Object make() {
                    return new Other("a", 1, "b");
                }
            }
            """;

    private ModJarFixtures() {
    }

    /**
     * Compiles {@code source} (one class, path derived from its package and first public class) against the stubs and
     * returns the mod's class files only, by entry name ({@code com/example/.../X.class}).
     */
    static Map<String, byte[]> compile(Path workDir, String source) {
        try {
            Path src = Files.createTempDirectory(workDir, "src");
            Path out = Files.createTempDirectory(workDir, "classes");
            List<Path> files = new ArrayList<>();
            for (Map.Entry<String, String> stub : MINECRAFT_STUBS.entrySet()) {
                files.add(write(src.resolve(stub.getKey()), stub.getValue()));
            }
            String pkg = source.lines().filter(l -> l.startsWith("package ")).findFirst().orElseThrow()
                    .substring("package ".length()).replace(";", "").trim();
            String cls = source.lines().map(String::trim).filter(l -> l.startsWith("public class "))
                    .findFirst().orElseThrow().substring("public class ".length()).split("[ {]")[0];
            files.add(write(src.resolve(pkg.replace('.', '/') + "/" + cls + ".java"), source));

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("tests need a JDK (javax.tools compiler)");
            }
            List<String> args = new ArrayList<>(List.of("-d", out.toString(), "--release", "21", "-nowarn"));
            files.forEach(f -> args.add(f.toString()));
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            int status = compiler.run(null, errors, errors, args.toArray(String[]::new));
            if (status != 0) {
                throw new IllegalStateException("fixture did not compile:\n"
                        + errors.toString(StandardCharsets.UTF_8));
            }
            Map<String, byte[]> classes = new LinkedHashMap<>();
            try (Stream<Path> walk = Files.walk(out)) {
                for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                    String name = out.relativize(p).toString().replace('\\', '/');
                    if (!name.startsWith("net/minecraft/")) {
                        classes.put(name, Files.readAllBytes(p));
                    }
                }
            }
            return classes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A minimal {@code fabric.mod.json}. */
    static byte[] fabricModJson(String id, String name, String version) {
        return ("{\"schemaVersion\": 1, \"id\": \"" + id + "\", \"name\": \"" + name + "\", \"version\": \"" + version
                + "\", \"depends\": {\"minecraft\": \"~1.21.4\"}}").getBytes(StandardCharsets.UTF_8);
    }

    /** A mod jar: {@code fabric.mod.json} plus the given entries. */
    static byte[] modJar(String id, String name, String version, Map<String, byte[]> entries) {
        Map<String, byte[]> all = new LinkedHashMap<>();
        all.put("fabric.mod.json", fabricModJson(id, name, version));
        all.putAll(entries);
        return zip(all);
    }

    /** Zips entries in order. */
    static byte[] zip(Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey()));
                    zip.write(e.getValue());
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
