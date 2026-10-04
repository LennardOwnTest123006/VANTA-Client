package dev.vanta.client.bridge;

import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.bridge.KeybindBridge;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.keybinds.VantaKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

/**
 * {@link KeybindBridge} over {@code Options.keyMappings}: every mapping the vanilla Controls screen shows (vanilla,
 * VANTA and other mods). Rebinding goes through {@link KeyMapping#setKey} followed by {@link KeyMapping#resetMapping()}
 * and {@code options.save()}, which is exactly what the vanilla screen does.
 */
public final class MinecraftKeybindBridge implements KeybindBridge {

    private static Options options() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.options;
    }

    @Override
    public List<KeyBinding> all() {
        Options options = options();
        if (options == null || options.keyMappings == null) {
            return List.of();
        }
        List<KeyBinding> out = new ArrayList<>(options.keyMappings.length);
        for (KeyMapping mapping : options.keyMappings) {
            out.add(toBinding(mapping));
        }
        return out;
    }

    /**
     * Snapshot of one mapping in the core's representation. VANTA's own category is labelled from the VANTA bundle;
     * every other category uses the game's label so other mods' categories read exactly as in the Controls screen.
     */
    static KeyBinding toBinding(KeyMapping mapping) {
        KeyMapping.Category category = mapping.getCategory();
        String categoryId = KeyConversions.categoryId(category.id());
        String categoryName = VantaKeys.CATEGORY.equals(categoryId) ? Lang.tr(VantaKeys.CATEGORY)
                : category.label().getString();
        KeyRef bound = KeyConversions.toRef(KeyBindingHelper.getBoundKeyOf(mapping));
        return new KeyBinding(mapping.getName(), Component.translatable(mapping.getName()).getString(), categoryId,
                categoryName, bound.name(), bound.code(), bound.type(), mapping.isDefault(),
                KeyConversions.isVanillaCategory(category.id()));
    }

    /** The live mapping with this name, if registered. */
    private static Optional<KeyMapping> mapping(String id) {
        Options options = options();
        if (options == null || options.keyMappings == null || id == null) {
            return Optional.empty();
        }
        for (KeyMapping mapping : options.keyMappings) {
            if (mapping.getName().equals(id)) {
                return Optional.of(mapping);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean setKey(String id, KeyRef key) {
        Optional<KeyMapping> mapping = mapping(id);
        if (mapping.isEmpty()) {
            return false;
        }
        mapping.get().setKey(KeyConversions.toKey(key));
        commit();
        return true;
    }

    @Override
    public boolean reset(String id) {
        Optional<KeyMapping> mapping = mapping(id);
        if (mapping.isEmpty()) {
            return false;
        }
        mapping.get().setKey(mapping.get().getDefaultKey());
        commit();
        return true;
    }

    @Override
    public void resetAll() {
        Options options = options();
        if (options == null || options.keyMappings == null) {
            return;
        }
        for (KeyMapping mapping : options.keyMappings) {
            mapping.setKey(mapping.getDefaultKey());
        }
        commit();
    }

    /** Rebuilds the key → mapping lookup table and persists {@code options.txt}. */
    private static void commit() {
        KeyMapping.resetMapping();
        Options options = options();
        if (options != null) {
            options.save();
        }
    }
}
