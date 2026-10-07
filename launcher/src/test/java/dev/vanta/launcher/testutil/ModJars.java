package dev.vanta.launcher.testutil;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Mod jars with real bytecode for the "built for an older Minecraft" check: fixture sources are compiled with the JDK's
 * compiler ({@link ToolProvider#getSystemJavaCompiler()}) against stubs of Minecraft 1.21.8's key binding API in
 * intermediary names, so the class files reference exactly what a mod published on Modrinth references:
 *
 * <ul>
 *   <li>{@code net.minecraft.class_304} (KeyMapping) with the constructors {@code (String, int, String)} and
 *       {@code (String, class_3675.class_307, int, String)} that Minecraft removed in 1.21.9, and the nested
 *       {@code class_304.class_11900} (KeyMapping.Category, new in 1.21.9);</li>
 *   <li>{@code net.minecraft.class_3675} (InputConstants) with the nested {@code class_307} (InputConstants.Type).</li>
 * </ul>
 *
 * <p>The stubs are never put into a mod jar (a real mod does not contain Minecraft's classes).</p>
 */
public final class ModJars {

    /** Creates its key binding with {@code KeyMapping(String, int, String)}, like Smart FPS Booster 1.0.0. */
    public static final String OLD_KEYBINDS = "com.smartclient.fpsbooster.ui.KeybindManager";
    /** Creates its key binding with {@code KeyMapping(String, InputConstants.Type, int, String)}. */
    public static final String OLD_KEYBINDS_WITH_TYPE = "com.example.typed.TypedKeybinds";
    /** Uses the old constructor only when {@code KeyMapping.Category} is missing (a jar for 1.21.8 and 1.21.9+). */
    public static final String MULTI_VERSION = "com.example.multi.Keybinds";
    /** Calls a {@code (String, int, String)} constructor of its own class: nothing to do with key bindings. */
    public static final String UNRELATED = "com.example.unrelated.Settings";
    /** The class {@link #UNRELATED} constructs. */
    public static final String UNRELATED_OPTION = "com.example.unrelated.Option";
    /** Creates its key binding with {@code KeyMapping(String, int, KeyMapping.Category)} only: a mod for 1.21.9+. */
    public static final String NEW_API_ONLY = "com.example.modern.ModernKeybinds";
    /** Uses both String-category constructors in one class. */
    public static final String BOTH_OLD = "com.example.both.BothKeybinds";
    /** Calls a static method of {@code class_304} with the old constructor's descriptor (not a constructor). */
    public static final String LOOKALIKE = "com.example.lookalike.Lookalike";

    private static final Map<String, String> SOURCES = new LinkedHashMap<>();

    static {
        SOURCES.put("net.minecraft.class_3675", """
            package net.minecraft;
            public class class_3675 {
                public static class class_307 {
                    public static final class_307 field_1668 = new class_307();
                }
            }
            """);
        SOURCES.put("net.minecraft.class_304", """
            package net.minecraft;
            public class class_304 {
                public class_304(String name, int code, String category) { }
                public class_304(String name, class_3675.class_307 type, int code, String category) { }
                public class_304(String name, int code, class_11900 category) { }
                public static void method_1234(String name, int code, String category) { }
                public static class class_11900 {
                    public static final class_11900 field_62556 = new class_11900();
                }
            }
            """);
        SOURCES.put(OLD_KEYBINDS, """
            package com.smartclient.fpsbooster.ui;
            import net.minecraft.class_304;
            public final class KeybindManager {
                private static final double SCALE = 2.5;
                public static class_304 toggle;
                public static void register() {
                    long started = System.nanoTime() + 1234567890123L;
                    Runnable later = () -> System.out.println("registered " + started * SCALE);
                    toggle = new class_304("key.smartfpsbooster.toggle", 79, "category.smartfpsbooster");
                    later.run();
                }
            }
            """);
        SOURCES.put(OLD_KEYBINDS_WITH_TYPE, """
            package com.example.typed;
            import net.minecraft.class_304;
            import net.minecraft.class_3675;
            public final class TypedKeybinds {
                public static class_304 zoom;
                public static void register() {
                    zoom = new class_304("key.example.zoom", class_3675.class_307.field_1668, 67, "category.example");
                }
            }
            """);
        SOURCES.put(MULTI_VERSION, """
            package com.example.multi;
            import net.minecraft.class_304;
            public final class Keybinds {
                public static Object category;
                public static class_304 open;
                public static void register(boolean newApi) {
                    if (newApi) {
                        category = class_304.class_11900.field_62556;
                    } else {
                        open = new class_304("key.example.open", 72, "category.example");
                    }
                }
            }
            """);
        SOURCES.put(NEW_API_ONLY, """
            package com.example.modern;
            import net.minecraft.class_304;
            public final class ModernKeybinds {
                public static class_304 open;
                public static void register() {
                    open = new class_304("key.example.open", 72, class_304.class_11900.field_62556);
                }
            }
            """);
        SOURCES.put(BOTH_OLD, """
            package com.example.both;
            import net.minecraft.class_304;
            import net.minecraft.class_3675;
            public final class BothKeybinds {
                public static class_304 a;
                public static class_304 b;
                public static void register() {
                    a = new class_304("key.example.a", 65, "category.example");
                    b = new class_304("key.example.b", class_3675.class_307.field_1668, 66, "category.example");
                }
            }
            """);
        SOURCES.put(LOOKALIKE, """
            package com.example.lookalike;
            import net.minecraft.class_304;
            public final class Lookalike {
                public static void run() {
                    class_304.method_1234("key.example.look", 76, "category.example");
                }
            }
            """);
        SOURCES.put(UNRELATED_OPTION, """
            package com.example.unrelated;
            public final class Option {
                public Option(String key, int value, String group) { }
            }
            """);
        SOURCES.put(UNRELATED, """
            package com.example.unrelated;
            public final class Settings {
                public static Option render = new Option("render", 2, "video");
            }
            """);
    }

    private final Path classes;

    private ModJars(final Path classes) {
        this.classes = classes;
    }

    /**
     * Compiles the fixture sources.
     *
     * @param dir an empty work directory
     * @return the compiled fixtures
     * @throws IOException on failure
     */
    public static ModJars compile(final Path dir) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("The tests need a JDK (javax.tools.ToolProvider.getSystemJavaCompiler() returned null)");
        }
        final Path src = dir.resolve("src");
        final Path out = dir.resolve("classes");
        Files.createDirectories(out);
        final List<String> args = new ArrayList<>(List.of("-d", out.toString(), "-proc:none", "--release", "21", "-nowarn"));
        for (Map.Entry<String, String> e : SOURCES.entrySet()) {
            final Path file = src.resolve(e.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, e.getValue(), StandardCharsets.UTF_8);
            args.add(file.toString());
        }
        final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        final int code = compiler.run(null, diagnostics, diagnostics, args.toArray(String[]::new));
        if (code != 0) {
            throw new IllegalStateException("Fixture compilation failed: " + diagnostics.toString(StandardCharsets.UTF_8));
        }
        return new ModJars(out);
    }

    /**
     * @param binaryName class name, e.g. {@link #OLD_KEYBINDS}
     * @return its class file
     * @throws IOException when it was not compiled
     */
    public byte[] classFile(final String binaryName) throws IOException {
        return Files.readAllBytes(classes.resolve(binaryName.replace('.', '/') + ".class"));
    }

    /**
     * @param binaryName class name
     * @return its zip entry name
     */
    public static String entryName(final String binaryName) {
        return binaryName.replace('.', '/') + ".class";
    }

    /**
     * A mod jar with a {@code fabric.mod.json} and the class files of the given fixtures.
     *
     * @param modId   mod id
     * @param name    mod name
     * @param version mod version
     * @param classNames fixture classes to include
     * @return jar bytes
     * @throws IOException on failure
     */
    public byte[] modJar(final String modId, final String name, final String version, final String... classNames) throws IOException {
        return modJar(modId, name, version, Map.of(), classNames);
    }

    /**
     * @param modId      mod id
     * @param name       mod name
     * @param version    mod version
     * @param extra      further entries (nested jars, malformed classes)
     * @param classNames fixture classes to include
     * @return jar bytes
     * @throws IOException on failure
     */
    public byte[] modJar(final String modId, final String name, final String version, final Map<String, byte[]> extra,
                         final String... classNames) throws IOException {
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("fabric.mod.json", fabricModJson(modId, name, version).getBytes(StandardCharsets.UTF_8));
        for (String c : classNames) {
            entries.put(entryName(c), classFile(c));
        }
        entries.putAll(extra);
        return zip(entries);
    }

    /**
     * @param modId   id
     * @param name    name
     * @param version version
     * @return a minimal {@code fabric.mod.json}
     */
    public static String fabricModJson(final String modId, final String name, final String version) {
        return "{\"schemaVersion\":1,\"id\":\"" + modId + "\",\"name\":\"" + name + "\",\"version\":\"" + version
            + "\",\"environment\":\"client\",\"depends\":{\"minecraft\":\"~1.21.4\"}}";
    }

    /**
     * @param entries entry name to content, in order
     * @return zip bytes (fixed timestamps)
     * @throws IOException on failure
     */
    public static byte[] zip(final Map<String, byte[]> entries) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                final ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(0L);
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
