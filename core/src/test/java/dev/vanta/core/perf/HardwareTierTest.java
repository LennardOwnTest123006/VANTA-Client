package dev.vanta.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.perf.HardwareTier.GpuClass;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HardwareTierTest {
    private static final long GIB = HardwareTier.GIB;

    private static SystemInfo machine(int threads, double heapGib) {
        return new SystemInfo("21", "test", "Linux", "6", "x64", threads, (long) (heapGib * GIB), 16 * GIB);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "llvmpipe (LLVM 15.0.7, 256 bits)|SOFTWARE",
            "zink Vulkan 1.3(llvmpipe (LLVM 17, 256 bits))|SOFTWARE",
            "Google SwiftShader|SOFTWARE",
            "Microsoft Basic Render Driver|SOFTWARE",
            "GDI Generic|SOFTWARE",
            "NVIDIA GeForce RTX 3060/PCIe/SSE2|DISCRETE",
            "NVIDIA GeForce GTX 1050 Ti/PCIe/SSE2|DISCRETE",
            "Quadro P2000/PCIe/SSE2|DISCRETE",
            "AMD Radeon RX 6700 XT (radeonsi, navi22, LLVM 15.0.7)|DISCRETE",
            "AMD Radeon Pro 5500M OpenGL Engine|DISCRETE",
            "Intel(R) Arc(TM) A770 Graphics|DISCRETE",
            "Mesa Intel(R) UHD Graphics 620 (KBL GT2)|INTEGRATED",
            "Intel(R) Iris(R) Xe Graphics|INTEGRATED",
            "Intel(R) HD Graphics 4000|INTEGRATED",
            "Intel(R) Arc(TM) Graphics|INTEGRATED",
            "AMD Radeon(TM) Graphics|INTEGRATED",
            "AMD Radeon Vega 8 Graphics|INTEGRATED",
            "AMD Radeon 780M (radeonsi, gfx1103_r1)|INTEGRATED",
            "Apple M2|UNKNOWN",
            "Some Future GPU 9000|UNKNOWN"})
    void classifiesRendererStrings(String renderer, GpuClass expected) {
        assertEquals(expected, HardwareTier.classifyGpu(Optional.of(renderer)));
    }

    @Test
    void missingRendererIsUnknown() {
        assertEquals(GpuClass.UNKNOWN, HardwareTier.classifyGpu(Optional.empty()));
        assertEquals(GpuClass.UNKNOWN, HardwareTier.classifyGpu(Optional.of("  ")));
    }

    @ParameterizedTest(name = "{0}: {1} threads, {2} GiB -> start {3}, cap {4}")
    @CsvSource(delimiter = '|', value = {
            // software rendering: Max FPS and nothing above it
            "llvmpipe (LLVM 15)|16|8.0|BOOST|BOOST",
            // integrated
            "Intel(R) UHD Graphics 620|4|4.0|LOW|HIGH",
            "Intel(R) UHD Graphics 620|8|4.0|BALANCED|HIGH",
            "AMD Radeon(TM) Graphics|12|2.0|BALANCED|BALANCED",
            // discrete
            "NVIDIA GeForce RTX 4070/PCIe/SSE2|16|4.0|HIGH|ULTRA",
            "NVIDIA GeForce RTX 4070/PCIe/SSE2|6|4.0|BALANCED|HIGH",
            "NVIDIA GeForce RTX 4070/PCIe/SSE2|16|3.0|BALANCED|HIGH",
            "NVIDIA GeForce RTX 4070/PCIe/SSE2|16|2.0|BALANCED|BALANCED",
            // unknown
            "|8|4.0|BALANCED|HIGH",
            "|8|2.0|BALANCED|BALANCED"})
    void startAndCapFollowTheTable(String renderer, int threads, double heapGib, PerformancePreset start,
                                   PerformancePreset cap) {
        HardwareTier.Guess guess = HardwareTier.classify(machine(threads, heapGib),
                Optional.ofNullable(renderer).filter(r -> !r.isBlank()));
        assertEquals(start, guess.start());
        assertEquals(cap, guess.cap());
        assertTrue(guess.start().ordinal() <= guess.cap().ordinal());
    }

    @Test
    void softwareRenderingNeverStepsUpAndTheDefaultLauncherHeapAllowsUltra() {
        assertFalse(HardwareTier.classify(machine(16, 8), Optional.of("llvmpipe")).allowStepUp());
        // -Xmx4096M (the VANTA launcher default) reports about 4.0 GiB.
        SystemInfo launcherDefault = new SystemInfo("21", "t", "Windows", "11", "x64", 12, 4096L << 20);
        assertEquals(PerformancePreset.ULTRA,
                HardwareTier.classify(launcherDefault, Optional.of("NVIDIA GeForce RTX 3060")).cap());
        assertEquals(0L, launcherDefault.totalRamBytes(), "the short constructor reports unknown RAM");
    }
}
