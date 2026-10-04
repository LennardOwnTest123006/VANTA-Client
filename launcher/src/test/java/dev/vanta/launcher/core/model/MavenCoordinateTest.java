package dev.vanta.launcher.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenCoordinateTest {

    @Test
    void parsesPlainCoordinate() {
        final MavenCoordinate c = MavenCoordinate.parse("net.fabricmc:fabric-loader:0.19.5");
        assertEquals("net.fabricmc", c.group());
        assertEquals("fabric-loader", c.artifact());
        assertEquals("0.19.5", c.version());
        assertEquals("", c.classifier());
        assertEquals("jar", c.extension());
        assertEquals("net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar", c.path());
        assertEquals("net.fabricmc:fabric-loader", c.key());
        assertEquals("net.fabricmc:fabric-loader:0.19.5", c.toString());
    }

    @Test
    void parsesClassifierAndExtension() {
        final MavenCoordinate c = MavenCoordinate.parse("org.lwjgl:lwjgl:3.3.3:natives-windows-arm64@zip");
        assertEquals("natives-windows-arm64", c.classifier());
        assertEquals("zip", c.extension());
        assertEquals("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows-arm64.zip", c.path());
        assertEquals("org.lwjgl:lwjgl:natives-windows-arm64", c.key());
        assertTrue(c.isNativeClassifier());
        assertEquals("org.lwjgl:lwjgl:3.3.3:natives-windows-arm64@zip", c.toString());
    }

    @Test
    void fabricApiVersionWithPlusIsPreserved() {
        final MavenCoordinate c = MavenCoordinate.parse("net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11");
        assertEquals("fabric-api-0.141.6+1.21.11.jar", c.fileName());
        assertEquals("net/fabricmc/fabric-api/fabric-api/0.141.6+1.21.11/fabric-api-0.141.6+1.21.11.jar", c.path());
    }

    @Test
    void rejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> MavenCoordinate.parse("only:two"));
        assertThrows(IllegalArgumentException.class, () -> MavenCoordinate.parse("a:b:c:d:e"));
        assertThrows(IllegalArgumentException.class, () -> MavenCoordinate.parse(":b:c"));
    }

    @Test
    void comparesVersions() {
        assertTrue(MavenCoordinate.compareVersions("9.7", "9.6") > 0);
        assertTrue(MavenCoordinate.compareVersions("9.7.1", "9.7") > 0);
        assertTrue(MavenCoordinate.compareVersions("9.7", "9.7-beta") > 0);
        assertTrue(MavenCoordinate.compareVersions("4.1.115.Final", "4.1.97.Final") > 0);
        assertEquals(0, MavenCoordinate.compareVersions("1.0.0", "1.0.0"));
        assertTrue(MavenCoordinate.compareVersions("0.16.5+mixin.0.8.7", "0.15.0+mixin.0.8.7") > 0);
        assertFalse(MavenCoordinate.compareVersions("1.10", "1.9") < 0);
    }
}
