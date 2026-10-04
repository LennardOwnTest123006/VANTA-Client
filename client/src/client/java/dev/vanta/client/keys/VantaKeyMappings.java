package dev.vanta.client.keys;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vanta.client.VantaClient;
import dev.vanta.client.bridge.KeyConversions;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.keybinds.VantaKeys;
import dev.vanta.core.settings.SettingsStore;
import dev.vanta.core.settings.VantaSettings;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * VANTA's key mappings: one {@link KeyMapping} per {@link VantaKeys.Definition} in the {@code vanta:vanta} category
 * (shown as "VANTA" in the vanilla Controls screen).
 * <p>
 * Must be created from the client entrypoint, before the game loads {@code options.txt} (Fabric requires modded
 * mappings to be registered before {@code Options} exists). Clicks are polled once per tick with
 * {@link KeyMapping#consumeClick()}; the game only registers clicks while no screen is open.
 * <p>
 * The zoom key is mirrored into the {@code zoom.key} setting in both directions so profiles and the settings screen
 * stay in sync with the vanilla Controls screen.
 */
public final class VantaKeyMappings {
    private final Map<String, KeyMapping> mappings;
    private String lastBoundZoomKey;
    private String lastZoomSetting;

    private VantaKeyMappings(Map<String, KeyMapping> mappings) {
        this.mappings = Collections.unmodifiableMap(mappings);
    }

    /** Creates and registers every VANTA key mapping. Call exactly once from {@code onInitializeClient}. */
    public static VantaKeyMappings register() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vanta", "vanta"));
        Map<String, KeyMapping> mappings = new LinkedHashMap<>();
        for (VantaKeys.Definition definition : VantaKeys.all()) {
            KeyRef key = definition.defaultKey();
            KeyMapping mapping = new KeyMapping(definition.id(), KeyConversions.toType(key.type()),
                    key.isUnbound() ? -1 : key.code(), category);
            KeyBindingHelper.registerKeyBinding(mapping);
            mappings.put(definition.id(), mapping);
        }
        return new VantaKeyMappings(mappings);
    }

    /** The mapping for a {@link VantaKeys} id. */
    public Optional<KeyMapping> get(String id) {
        return Optional.ofNullable(mappings.get(id));
    }

    /** True while the mapping is held down. */
    public boolean isDown(String id) {
        KeyMapping mapping = mappings.get(id);
        return mapping != null && mapping.isDown();
    }

    /** Delivers every click registered since the last poll, in definition order. */
    public void pollClicks(Consumer<String> onClick) {
        Objects.requireNonNull(onClick, "onClick");
        for (Map.Entry<String, KeyMapping> entry : mappings.entrySet()) {
            while (entry.getValue().consumeClick()) {
                onClick.accept(entry.getKey());
            }
        }
    }

    /**
     * Keeps {@code zoom.key} and the zoom mapping identical. A rebinding in the Controls screen wins over the setting;
     * a settings change (profile load, settings screen) is pushed to the mapping and saved to {@code options.txt}.
     */
    public void syncZoomKey(SettingsStore settings) {
        KeyMapping zoom = mappings.get(VantaKeys.ZOOM);
        Minecraft minecraft = Minecraft.getInstance();
        if (zoom == null || minecraft == null || minecraft.options == null) {
            return;
        }
        String bound = KeyBindingHelper.getBoundKeyOf(zoom).getName();
        String setting = settings.get(VantaSettings.ZOOM_KEY);
        if (lastBoundZoomKey == null) {
            // First sync after start-up: options.txt is authoritative.
            lastBoundZoomKey = bound;
            lastZoomSetting = setting;
            if (!bound.equals(setting)) {
                settings.set(VantaSettings.ZOOM_KEY, bound);
                lastZoomSetting = bound;
            }
            return;
        }
        if (!bound.equals(lastBoundZoomKey)) {
            settings.set(VantaSettings.ZOOM_KEY, bound);
            lastBoundZoomKey = bound;
            lastZoomSetting = bound;
            return;
        }
        if (!setting.equals(lastZoomSetting)) {
            Optional<InputConstants.Key> key = KeyConversions.fromVanillaName(setting);
            if (key.isPresent()) {
                zoom.setKey(key.get());
                KeyMapping.resetMapping();
                minecraft.options.save();
                lastBoundZoomKey = key.get().getName();
            } else {
                VantaClient.LOGGER.warn("Ignoring unknown zoom key name '{}' in settings", setting);
            }
            lastZoomSetting = setting;
        }
    }
}
