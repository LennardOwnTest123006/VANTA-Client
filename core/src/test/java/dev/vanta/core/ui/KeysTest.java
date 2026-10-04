package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeysTest {

    @Test
    void glfwCodes() {
        assertEquals(256, Keys.ESCAPE);
        assertEquals(257, Keys.ENTER);
        assertEquals(258, Keys.TAB);
        assertEquals(259, Keys.BACKSPACE);
        assertEquals(261, Keys.DELETE);
        assertEquals(262, Keys.RIGHT);
        assertEquals(263, Keys.LEFT);
        assertEquals(264, Keys.DOWN);
        assertEquals(265, Keys.UP);
        assertEquals(268, Keys.HOME);
        assertEquals(269, Keys.END);
        assertEquals(32, Keys.SPACE);
        assertEquals(65, Keys.A);
        assertEquals(344, Keys.RIGHT_SHIFT);
    }

    @Test
    void modifiers() {
        assertTrue(Keys.hasShift(Keys.MOD_SHIFT | Keys.MOD_ALT));
        assertTrue(Keys.hasControl(Keys.MOD_CONTROL));
        assertTrue(Keys.hasControl(Keys.MOD_SUPER), "Command acts as Control");
        assertFalse(Keys.hasControl(Keys.MOD_SHIFT));
        assertTrue(Keys.hasAlt(Keys.MOD_ALT));
        assertTrue(Keys.isModifier(Keys.LEFT_SHIFT));
        assertFalse(Keys.isModifier(Keys.A));
        assertTrue(Keys.isActivate(Keys.ENTER) && Keys.isActivate(Keys.SPACE) && Keys.isActivate(Keys.KP_ENTER));
        assertTrue(Keys.isArrow(Keys.UP));
    }

    @Test
    void printable() {
        assertTrue(Keys.isPrintable('a'));
        assertTrue(Keys.isPrintable(0x00E9));
        assertFalse(Keys.isPrintable(10));
        assertFalse(Keys.isPrintable(127));
        assertFalse(Keys.isPrintable(0xD800));
    }

    @Test
    void displayNames() {
        assertEquals("A", Keys.displayName(Keys.A));
        assertEquals("7", Keys.displayName(Keys.KEY_7));
        assertEquals("F3", Keys.displayName(Keys.F3));
        assertEquals("Right Shift", Keys.displayName(Keys.RIGHT_SHIFT));
        assertEquals("Numpad 5", Keys.displayName(Keys.KP_0 + 5));
        assertEquals("Not bound", Keys.displayName(Keys.UNKNOWN));
        assertEquals("Key 999", Keys.displayName(999));
        assertEquals("Space", Keys.displayName(Keys.SPACE));
    }
}
