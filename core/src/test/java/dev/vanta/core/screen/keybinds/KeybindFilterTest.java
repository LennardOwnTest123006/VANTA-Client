package dev.vanta.core.screen.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.bridge.FakeKeybindBridge;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.keybinds.KeybindModel;
import dev.vanta.core.keybinds.VantaKeys;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeybindFilterTest {
    @Test
    void sectionsKeepCategoryOrderAndDropEmptyOnes() {
        FakeKeybindBridge bridge = new FakeKeybindBridge();
        KeybindModel model = new KeybindModel(bridge);
        List<KeybindModel.Category> all = KeybindFilter.NONE.sections(model);
        assertEquals(model.categories().size(), all.size());
        assertEquals(VantaKeys.CATEGORY, all.get(0).id());
        assertTrue(KeybindFilter.NONE.isEmpty());

        KeybindFilter query = KeybindFilter.NONE.withQuery("hud");
        List<KeybindModel.Category> hud = query.sections(model);
        assertEquals(1, hud.size());
        assertTrue(hud.get(0).bindings().stream().allMatch(b -> b.displayName().toLowerCase().contains("hud")));
        assertEquals(hud.get(0).bindings().size(), query.count(model));
        assertFalse(query.isEmpty());

        assertTrue(KeybindFilter.NONE.withConflictsOnly(true).sections(model).isEmpty());
        bridge.setKey("key.vanta.toggle_hud", KeyRef.keyboard(87, "key.keyboard.w"));
        model.refresh();
        List<KeybindModel.Category> conflicts = KeybindFilter.NONE.withConflictsOnly(true).sections(model);
        assertEquals(2, conflicts.size());
        assertEquals(List.of("key.vanta.toggle_hud"), conflicts.get(0).bindings().stream().map(b -> b.id()).toList());
        assertEquals(List.of("key.forward"), conflicts.get(1).bindings().stream().map(b -> b.id()).toList());
        assertEquals(1, new KeybindFilter("forward", true).count(model));
        assertEquals(0, new KeybindFilter("zoom", true).count(model));
        assertEquals("", new KeybindFilter(null, false).query());
    }
}
