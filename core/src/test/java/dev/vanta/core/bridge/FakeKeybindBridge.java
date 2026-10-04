package dev.vanta.core.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link KeybindBridge} pre-populated with a realistic subset of vanilla mappings plus VANTA's own.
 */
public final class FakeKeybindBridge implements KeybindBridge {
    private final Map<String, KeyBinding> bindings = new LinkedHashMap<>();
    private final Map<String, KeyRef> defaults = new LinkedHashMap<>();

    public FakeKeybindBridge() {
        vanilla("key.forward", "Walk Forwards", "key.categories.movement", "Movement", KeyRef.keyboard(87, "key.keyboard.w"));
        vanilla("key.left", "Strafe Left", "key.categories.movement", "Movement", KeyRef.keyboard(65, "key.keyboard.a"));
        vanilla("key.back", "Walk Backwards", "key.categories.movement", "Movement", KeyRef.keyboard(83, "key.keyboard.s"));
        vanilla("key.right", "Strafe Right", "key.categories.movement", "Movement", KeyRef.keyboard(68, "key.keyboard.d"));
        vanilla("key.jump", "Jump", "key.categories.movement", "Movement", KeyRef.keyboard(32, "key.keyboard.space"));
        vanilla("key.sneak", "Sneak", "key.categories.movement", "Movement", KeyRef.keyboard(340, "key.keyboard.left.shift"));
        vanilla("key.sprint", "Sprint", "key.categories.movement", "Movement", KeyRef.keyboard(341, "key.keyboard.left.control"));
        vanilla("key.attack", "Attack/Destroy", "key.categories.gameplay", "Gameplay", KeyRef.mouse(0));
        vanilla("key.use", "Use Item/Place Block", "key.categories.gameplay", "Gameplay", KeyRef.mouse(1));
        vanilla("key.pickItem", "Pick Block", "key.categories.gameplay", "Gameplay", KeyRef.mouse(2));
        vanilla("key.inventory", "Open/Close Inventory", "key.categories.inventory", "Inventory", KeyRef.keyboard(69, "key.keyboard.e"));
        vanilla("key.drop", "Drop Selected Item", "key.categories.inventory", "Inventory", KeyRef.keyboard(81, "key.keyboard.q"));
        vanilla("key.chat", "Open Chat", "key.categories.multiplayer", "Multiplayer", KeyRef.keyboard(84, "key.keyboard.t"));
        vanilla("key.playerlist", "List Players", "key.categories.multiplayer", "Multiplayer", KeyRef.keyboard(258, "key.keyboard.tab"));
        vanilla("key.command", "Open Command", "key.categories.multiplayer", "Multiplayer", KeyRef.keyboard(47, "key.keyboard.slash"));
        vanilla("key.screenshot", "Take Screenshot", "key.categories.misc", "Miscellaneous", KeyRef.keyboard(291, "key.keyboard.f2"));
        vanilla("key.togglePerspective", "Toggle Perspective", "key.categories.misc", "Miscellaneous", KeyRef.keyboard(294, "key.keyboard.f5"));
        vanilla("key.fullscreen", "Toggle Fullscreen", "key.categories.misc", "Miscellaneous", KeyRef.keyboard(300, "key.keyboard.f11"));
        mod("key.vanta.open_menu", "Open VANTA Menu", "key.categories.vanta", "VANTA", KeyRef.keyboard(344, "key.keyboard.right.shift"));
        mod("key.vanta.toggle_hud", "Toggle HUD", "key.categories.vanta", "VANTA", KeyRef.UNBOUND);
        mod("key.vanta.hud_editor", "Open HUD Editor", "key.categories.vanta", "VANTA", KeyRef.UNBOUND);
        mod("key.vanta.performance", "Open Performance Center", "key.categories.vanta", "VANTA", KeyRef.UNBOUND);
        mod("key.vanta.zoom", "Zoom", "key.categories.vanta", "VANTA", KeyRef.keyboard(67, "key.keyboard.c"));
        mod("key.vanta.screenshot_hud_free", "HUD-free Screenshot", "key.categories.vanta", "VANTA", KeyRef.UNBOUND);
    }

    private void vanilla(String id, String name, String catId, String catName, KeyRef key) {
        add(id, name, catId, catName, key, true);
    }

    private void mod(String id, String name, String catId, String catName, KeyRef key) {
        add(id, name, catId, catName, key, false);
    }

    /** Adds or replaces a mapping whose current binding is also its default. */
    public void add(String id, String name, String catId, String catName, KeyRef key, boolean isVanilla) {
        defaults.put(id, key);
        bindings.put(id, new KeyBinding(id, name, catId, catName, key.name(), key.code(), key.type(), true, isVanilla));
    }

    @Override
    public List<KeyBinding> all() {
        return new ArrayList<>(bindings.values());
    }

    @Override
    public boolean setKey(String id, KeyRef key) {
        KeyBinding current = bindings.get(id);
        if (current == null) {
            return false;
        }
        KeyRef def = defaults.get(id);
        bindings.put(id, current.withKey(key, def.equals(key)));
        return true;
    }

    @Override
    public boolean reset(String id) {
        KeyBinding current = bindings.get(id);
        if (current == null) {
            return false;
        }
        bindings.put(id, current.withKey(defaults.get(id), true));
        return true;
    }

    @Override
    public void resetAll() {
        for (String id : new ArrayList<>(bindings.keySet())) {
            reset(id);
        }
    }
}
