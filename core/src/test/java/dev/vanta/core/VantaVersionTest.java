package dev.vanta.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VantaVersionTest {
    @Test
    void targetsExactMinecraftVersion() {
        assertEquals("1.21.11", VantaVersion.MINECRAFT);
        assertEquals(21, VantaVersion.JAVA);
        assertTrue(VantaVersion.FABRIC_API.endsWith("+" + VantaVersion.MINECRAFT));
    }

    @Test
    void menuLabelHasThreeLines() {
        String[] label = VantaVersion.menuLabel();
        assertEquals(3, label.length);
        assertEquals("Minecraft 1.21.11", label[1]);
    }
}
