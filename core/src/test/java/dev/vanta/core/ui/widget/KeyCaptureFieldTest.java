package dev.vanta.core.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.TestCanvas;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiScreen;
import dev.vanta.core.ui.UiTestSupport;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class KeyCaptureFieldTest {

    private final UiTestSupport t = new UiTestSupport();
    private final List<Integer> captured = new ArrayList<>();
    private int cancels;
    private KeyCaptureField field;
    private Button other;
    private UiScreen screen;

    private void host() {
        Column root = new Column().align(Align.START);
        field = root.add(new KeyCaptureField(Keys.RIGHT_SHIFT, null, captured::add).onCancel(() -> cancels++));
        field.size(100, 20);
        other = root.add(new Button("other", null));
        other.size(60, 20);
        screen = t.screen(root, 300, 200);
    }

    @Test
    void clickEntersCaptureAndNextKeyIsEmitted() {
        host();
        assertEquals("Right Shift", field.keyName());
        UiTestSupport.click(screen, 50, 10);
        assertTrue(field.isCapturing());
        assertTrue(field.isFocused());
        assertTrue(screen.keyDown(Keys.F, 0, 0));
        assertEquals(List.of(Keys.F), captured);
        assertFalse(field.isCapturing());
        assertEquals("Right Shift", field.keyName(), "owner updates the name once the binding is applied");
        field.setKey(Keys.F, null);
        assertEquals("F", field.keyName());
        assertEquals(Keys.F, field.keyCode());
    }

    @Test
    void escapeCancelsWithoutClosingTheScreen() {
        host();
        UiTestSupport.click(screen, 50, 10);
        assertTrue(screen.keyDown(Keys.ESCAPE, 0, 0));
        assertFalse(field.isCapturing());
        assertFalse(screen.isClosing());
        assertEquals(1, cancels);
        assertTrue(captured.isEmpty());
        assertTrue(screen.keyDown(Keys.ESCAPE, 0, 0));
        assertTrue(screen.isClosing(), "second Escape closes the screen as usual");
    }

    @Test
    void losingFocusOrClickingAgainCancels() {
        host();
        UiTestSupport.click(screen, 50, 10);
        UiTestSupport.click(screen, 50, 10);
        assertFalse(field.isCapturing());
        assertEquals(1, cancels);
        screen.context().focus().focus(screen.context(), field);
        assertTrue(screen.keyDown(Keys.ENTER, 0, 0), "Enter starts capture from the keyboard");
        assertTrue(field.isCapturing());
        UiTestSupport.click(screen, other.bounds().centerX(), other.bounds().centerY());
        assertFalse(field.isCapturing());
        assertEquals(2, cancels);
        assertFalse(screen.keyDown(Keys.TAB, 0, 0) && field.isCapturing());
    }

    @Test
    void swallowsTypedCharactersWhileCapturing() {
        host();
        UiTestSupport.click(screen, 50, 10);
        assertTrue(screen.charTyped('f', 0));
        assertTrue(screen.keyDown(Keys.TAB, 0, 0), "even Tab is captured as a binding");
        assertEquals(List.of(Keys.TAB), captured);
        assertFalse(screen.charTyped('f', 0));
    }

    @Test
    void rendersStates() {
        KeyCaptureField f = t.place(new KeyCaptureField(Keys.C, "C", null), 0, 0, 100, 20);
        TestCanvas normal = t.render(f);
        assertTrue(normal.hasText("C"));
        f.conflict(true);
        TestCanvas conflict = t.render(f);
        assertEquals(Theme.DEFAULT.danger(), conflict.texts().get(0).argb());
        assertTrue(f.hasConflict());
        f.capturePrompt("Press any key");
        f.startCapture(t.ctx());
        TestCanvas capturing = t.render(f);
        assertTrue(capturing.hasText("Press any key"));
        assertFalse(capturing.hasText("C"));
        assertTrue(f.preferredSize(t.ctx()).w() >= 72);
        f.setEnabled(false);
        f.cancelCapture();
        assertFalse(f.keyDown(t.ctx(), Keys.ENTER, 0, 0));
    }
}
