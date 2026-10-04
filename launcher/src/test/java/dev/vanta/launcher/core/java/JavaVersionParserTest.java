package dev.vanta.launcher.core.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaVersionParserTest {

    @ParameterizedTest
    @CsvSource({
        "21.0.4, 21", "21, 21", "21-ea, 21", "17.0.9+9-LTS, 17", "1.8.0_392, 8", "1.8.0, 8", "11.0.21, 11", "25-ea+12, 25",
        "garbage, -1", "'', -1"
    })
    void majorVersion(final String version, final int expected) {
        assertEquals(expected, JavaVersionParser.major(version));
    }

    @Test
    void parsesShowSettingsOutput() {
        final String output = """
            Property settings:
                file.encoding = UTF-8
                java.home = /usr/lib/jvm/java-21-openjdk-amd64
                java.vendor = Ubuntu
                java.version = 21.0.4
                java.version.date = 2024-07-16
                os.arch = amd64
                os.name = Linux
                sun.arch.data.model = 64
                user.dir = /tmp
            openjdk version "21.0.4" 2024-07-16
            OpenJDK Runtime Environment (build 21.0.4+7-Ubuntu-1ubuntu224.04)
            """;
        final Map<String, String> props = JavaVersionParser.parseProperties(output);
        assertEquals("21.0.4", props.get("java.version"));
        assertEquals("Ubuntu", props.get("java.vendor"));
        assertEquals("amd64", props.get("os.arch"));
        final Optional<JavaInstall> install = JavaVersionParser.fromProbeOutput(Path.of("/usr/lib/jvm/java-21-openjdk-amd64/bin/java"), output);
        assertTrue(install.isPresent());
        assertEquals(21, install.get().major());
        assertEquals("Ubuntu", install.get().vendor());
        assertEquals("amd64", install.get().arch());
        assertTrue(install.get().is64Bit());
        assertEquals(Path.of("/usr/lib/jvm/java-21-openjdk-amd64"), install.get().home());
        assertTrue(install.get().satisfies(21));
        assertTrue(install.get().describe().contains("Java 21.0.4"));
    }

    @Test
    void fallsBackToBannerAndExecutableLocation() {
        final String output = "openjdk version \"17.0.9\" 2023-10-17\nOpenJDK Runtime Environment Temurin-17.0.9+9\n";
        final Optional<JavaInstall> install = JavaVersionParser.fromProbeOutput(Path.of("/opt/jdk17/bin/java"), output);
        assertTrue(install.isPresent());
        assertEquals(17, install.get().major());
        assertEquals(Path.of("/opt/jdk17"), install.get().home());
    }

    @Test
    void thirtyTwoBitDetection() {
        final String output = "    java.version = 1.8.0_392\n    os.arch = x86\n    sun.arch.data.model = 32\n";
        final JavaInstall install = JavaVersionParser.fromProbeOutput(Path.of("C:\\Java\\bin\\java.exe"), output).orElseThrow();
        assertEquals(8, install.major());
        assertEquals(false, install.is64Bit());
    }

    @Test
    void unparseableOutputYieldsEmpty() {
        assertTrue(JavaVersionParser.fromProbeOutput(Path.of("/x/bin/java"), "Error: could not create the Java Virtual Machine").isEmpty());
    }
}
