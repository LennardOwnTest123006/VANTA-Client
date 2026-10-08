package dev.vanta.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlatformTest {
    @Test
    void detectsTheSixSupportedPlatforms() {
        assertEquals("windows-x64", Platform.detect("Windows 11", "amd64").orElseThrow().key());
        assertEquals("windows-arm64", Platform.detect("Windows 11", "aarch64").orElseThrow().key());
        assertEquals("linux-x64", Platform.detect("Linux", "x86_64").orElseThrow().key());
        assertEquals("linux-arm64", Platform.detect("Linux", "arm64").orElseThrow().key());
        assertEquals("macos-arm64", Platform.detect("Mac OS X", "aarch64").orElseThrow().key());
        assertEquals("macos-x64", Platform.detect("Mac OS X", "x86_64").orElseThrow().key());
        assertEquals("macos-x64", Platform.detect("Darwin", "x64").orElseThrow().key());
    }

    @Test
    void unsupportedSystemsAreEmptyNotAnError() {
        assertTrue(Platform.detect("SunOS", "sparcv9").isEmpty());
        assertTrue(Platform.detect("Linux", "riscv64").isEmpty());
        assertTrue(Platform.detect("Windows 10", "x86").isEmpty(), "32-bit Windows has no llama.cpp CPU build");
        assertTrue(Platform.detect(null, null).isEmpty());
        assertEquals("FreeBSD/amd64", Platform.systemLabel("FreeBSD", "amd64"));
    }

    @Test
    void parsesAndPrintsKeys() {
        Platform linux = Platform.parse("linux-arm64").orElseThrow();
        assertEquals(Platform.Os.LINUX, linux.os());
        assertEquals(Platform.Arch.ARM64, linux.arch());
        assertEquals("linux-arm64", linux.toString());
        assertFalse(linux.isWindows());
        assertFalse(linux.usesZip());
        Platform windows = Platform.parse("windows-x64").orElseThrow();
        assertTrue(windows.isWindows());
        assertTrue(windows.usesZip());
        assertTrue(Platform.parse("linux").isEmpty());
        assertTrue(Platform.parse("linux-").isEmpty());
        assertTrue(Platform.parse("android-arm64").isEmpty());
        assertTrue(Platform.parse("linux-x86").isEmpty());
        assertTrue(Platform.parse(null).isEmpty());
    }

    @Test
    void currentPlatformIsDetectedOnThisBuildMachine() {
        // The test machine (Linux/Windows/macOS on x64 or arm64) is one of the supported platforms.
        assertTrue(Platform.current().isPresent(), Platform.systemLabel());
    }
}
