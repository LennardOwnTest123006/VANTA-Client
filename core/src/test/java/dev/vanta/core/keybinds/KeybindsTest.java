package dev.vanta.core.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.bridge.KeyType;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KeybindsTest {
    private static KeyBinding binding(String id, String category, KeyRef key, boolean vanilla) {
        return new KeyBinding(id, id, category, category, key.name(), key.code(), key.type(), true, vanilla);
    }

    @Test
    void detectorGroupsSameInputAndIgnoresUnbound() {
        KeyRef shift = KeyRef.keyboard(340, "key.keyboard.left.shift");
        List<KeyBinding> bindings = List.of(
                binding("key.sneak", "movement", shift, true),
                binding("key.sprint", "movement", KeyRef.keyboard(341, "key.keyboard.left.control"), true),
                binding("mod.a", "mod", shift, false),
                binding("mod.b", "mod", KeyRef.UNBOUND, false),
                binding("mod.c", "mod", KeyRef.UNBOUND, false),
                binding("mod.d", "other", KeyRef.mouse(4), false),
                binding("mod.e", "other", KeyRef.mouse(4), false));
        List<ConflictDetector.Conflict> conflicts = ConflictDetector.detect(bindings);
        assertEquals(2, conflicts.size());
        ConflictDetector.Conflict shiftConflict = ConflictDetector.conflictFor(conflicts, "key.sneak").orElseThrow();
        assertEquals(ConflictDetector.Severity.MEDIUM, shiftConflict.severity(), "vanilla vs mod, different category");
        assertTrue(shiftConflict.involves("mod.a"));
        ConflictDetector.Conflict mouse = ConflictDetector.conflictFor(conflicts, "mod.d").orElseThrow();
        assertEquals(ConflictDetector.Severity.HIGH, mouse.severity(), "same category");
        assertEquals(KeyType.MOUSE, mouse.key().type());
        assertTrue(ConflictDetector.conflictFor(conflicts, "mod.b").isEmpty(), "unbound never conflicts");
        assertEquals(4, ConflictDetector.involvedCount(conflicts));
        assertEquals(ConflictDetector.Severity.HIGH, ConflictDetector.severityOf(List.of(
                binding("key.a", "x", shift, true), binding("key.b", "y", shift, true))), "two vanilla keys");
    }

    @Test
    void formatterProducesReadableLabels() {
        assertEquals("Right Shift", KeybindFormatter.format(KeyRef.keyboard(344, "key.keyboard.right.shift")));
        assertEquals("C", KeybindFormatter.format(KeyRef.keyboard(67, "key.keyboard.c")));
        assertEquals("F3", KeybindFormatter.humanize("key.keyboard.f3"));
        assertEquals("Left Super", KeybindFormatter.humanize("key.keyboard.left.win"));
        assertEquals("Left Click", KeybindFormatter.format(KeyRef.mouse(0)));
        assertEquals("Mouse 5", KeybindFormatter.format(KeyRef.mouse(4)));
        assertEquals("Not bound", KeybindFormatter.format(KeyRef.UNBOUND));
        assertEquals("Scancode 12", KeybindFormatter.format(KeyType.SCANCODE, 12, "scancode.12"));
        assertEquals("Space", KeybindFormatter.format(KeyType.KEYSYM, 32, KeyRef.UNKNOWN_NAME), "falls back to GLFW code");
        assertEquals("key.keyboard.f12", KeybindFormatter.glfwKeyName(301));
        assertEquals("key.keyboard.keypad.5", KeybindFormatter.glfwKeyName(325));
        assertEquals("key.keyboard.7", KeybindFormatter.glfwKeyName('7'));
        assertEquals(KeyRef.UNKNOWN_NAME, KeybindFormatter.glfwKeyName(99999));
    }

    @Test
    void modelGroupsVantaFirstAndRebinds() {
        FakeKeybindBridge bridge = new FakeKeybindBridge();
        KeybindModel model = new KeybindModel(bridge);
        AtomicInteger changes = new AtomicInteger();
        model.onChange(changes::incrementAndGet);
        assertEquals(VantaKeys.CATEGORY, model.categories().get(0).id());
        assertTrue(model.categories().get(0).isVanta());
        assertEquals(VantaKeys.all().size(), model.categories().get(0).bindings().size());
        assertTrue(model.conflicts().isEmpty());
        assertEquals(0, model.modifiedCount());

        assertTrue(model.setKey(VantaKeys.ZOOM, KeyRef.keyboard(69, "key.keyboard.e")));
        assertEquals(1, model.modifiedCount());
        assertEquals(1, model.conflicts().size(), "zoom now collides with inventory");
        assertEquals(ConflictDetector.Severity.MEDIUM, model.conflictFor(VantaKeys.ZOOM).orElseThrow().severity());
        assertEquals(1, changes.get());
        assertEquals(Optional.of(KeyRef.keyboard(69, "key.keyboard.e")),
                Optional.ofNullable(model.vantaBindings().get(VantaKeys.ZOOM)));

        List<KeyBinding> found = model.search("zoom");
        assertEquals(VantaKeys.ZOOM, found.get(0).id());
        assertEquals(model.all().size(), model.search("").size());
        assertTrue(model.search("inventory").stream().anyMatch(b -> b.id().equals("key.inventory")));

        assertTrue(model.reset(VantaKeys.ZOOM));
        assertTrue(model.conflicts().isEmpty());
        assertFalse(model.setKey("key.unknown", KeyRef.UNBOUND));
        model.setKey("key.forward", KeyRef.UNBOUND);
        assertTrue(model.find("key.forward").orElseThrow().isUnbound());
        model.resetAll();
        assertEquals(0, model.modifiedCount());
    }

    @Test
    void vantaKeysHaveStableDefaults() {
        assertEquals(KeyRef.keyboard(344, "key.keyboard.right.shift"),
                VantaKeys.find(VantaKeys.OPEN_MENU).orElseThrow().defaultKey());
        assertEquals(KeyRef.keyboard(67, "key.keyboard.c"), VantaKeys.find(VantaKeys.ZOOM).orElseThrow().defaultKey());
        assertTrue(VantaKeys.find(VantaKeys.TOGGLE_HUD).orElseThrow().defaultKey().isUnbound());
        assertTrue(VantaKeys.isVantaKey("key.vanta.zoom"));
        assertFalse(VantaKeys.isVantaKey("key.forward"));
        assertEquals(6, VantaKeys.all().size());
    }
}
