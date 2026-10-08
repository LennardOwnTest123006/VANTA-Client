package dev.vanta.core.screen.profiles;

import com.google.gson.JsonElement;
import dev.vanta.core.bridge.KeyBinding;
import dev.vanta.core.bridge.KeyRef;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.ai.NexusHudPreset;
import dev.vanta.core.profiles.BuiltInProfiles;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Icons;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only facts about a {@link Profile} the profile cards display: whether it is one of the built-ins, its icon,
 * description and the names of the HUD, performance and crosshair presets it contains. {@link #unsavedChanges}
 * compares the live configuration with the active profile.
 *
 * @param profile       the profile
 * @param builtIn       true for the five profiles created on first run
 * @param icon          icon shown on the card
 * @param description   translated description (built-ins) or the generic custom-profile line
 * @param hudPreset     translated HUD preset name, or the "custom layout" label
 * @param perfPreset    translated performance preset name
 * @param crosshair     translated crosshair preset name, or "Custom"
 */
public record ProfileSummary(Profile profile, boolean builtIn, Icons icon, String description, String hudPreset,
                             String perfPreset, String crosshair) {

    public ProfileSummary {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(icon, "icon");
    }

    /** Builds the summary of a profile. */
    public static ProfileSummary of(Profile profile) {
        boolean builtIn = BuiltInProfiles.IDS.contains(profile.id());
        String description = builtIn ? Lang.tr(BuiltInProfiles.descriptionKey(profile.id()))
                : Lang.tr("vanta.profiles.custom_description");
        return new ProfileSummary(profile, builtIn, iconFor(profile.icon()), description, hudPresetName(profile),
                perfPresetName(profile), crosshairName(profile));
    }

    /** Maps a profile icon id to a drawable icon (unknown ids fall back to the profile glyph). */
    public static Icons iconFor(String iconId) {
        String id = iconId == null ? "" : iconId.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "crosshair" -> Icons.CROSSHAIR;
            case "grid" -> Icons.GRID;
            case "chart" -> Icons.CHART;
            case "play" -> Icons.PLAY;
            case "star" -> Icons.STAR;
            case "gear" -> Icons.GEAR;
            case "palette" -> Icons.PALETTE;
            case "keyboard" -> Icons.KEYBOARD;
            case "package" -> Icons.PACKAGE;
            case "sliders" -> Icons.SLIDERS;
            default -> Icons.PROFILE;
        };
    }

    private static String hudPresetName(Profile profile) {
        if (profile.hud().isEmpty()) {
            return Lang.tr("vanta.profiles.hud_unchanged");
        }
        for (HudPreset preset : HudPresets.builtIns()) {
            if (preset.layout().equals(profile.hud())) {
                return Lang.tr(preset.langKey());
            }
        }
        for (NexusHudPreset preset : NexusHudPreset.values()) {
            if (BuiltInProfiles.nexusHud(preset).equals(profile.hud())) {
                return Lang.tr(preset.langKey());
            }
        }
        return Lang.tr("vanta.profiles.hud_custom", profile.hud().size());
    }

    private static String perfPresetName(Profile profile) {
        JsonElement element = profile.settings().get(VantaSettings.PERFORMANCE_PRESET.id());
        Optional<PerformancePreset> preset = element == null ? Optional.empty()
                : VantaSettings.PERFORMANCE_PRESET.decode(element);
        return preset.map(p -> Lang.tr(p.langKey())).orElse(Lang.tr("vanta.perf.custom"));
    }

    private static String crosshairName(Profile profile) {
        return CrosshairPresets.matching(profile.crosshair()).map(p -> Lang.tr(p.langKey()))
                .orElse(Lang.tr("vanta.common.custom"));
    }

    /**
     * Areas in which the live configuration differs from the active profile, as translated labels
     * ("Settings", "HUD", "Crosshair", "Key bindings"). Empty when there is no active profile or nothing changed.
     */
    public static List<String> unsavedChanges(VantaServices services) {
        Optional<Profile> active = services.profiles().active();
        if (active.isEmpty()) {
            return List.of();
        }
        Profile profile = active.get();
        List<String> out = new ArrayList<>();
        if (settingsDiffer(profile, services.settings().snapshot(true))) {
            out.add(Lang.tr("vanta.profiles.area.settings"));
        }
        if (!profile.hud().isEmpty() && !profile.hud().equals(services.hud().layout())) {
            out.add(Lang.tr("vanta.profiles.area.hud"));
        }
        if (!profile.crosshair().equals(services.crosshair().style())) {
            out.add(Lang.tr("vanta.profiles.area.crosshair"));
        }
        if (keybindsDiffer(profile, services.keybinds().all())) {
            out.add(Lang.tr("vanta.profiles.area.keybinds"));
        }
        return out;
    }

    private static boolean settingsDiffer(Profile profile, Map<String, JsonElement> current) {
        for (Map.Entry<String, JsonElement> entry : profile.settings().entrySet()) {
            JsonElement live = current.get(entry.getKey());
            if (live != null && !live.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean keybindsDiffer(Profile profile, List<KeyBinding> bindings) {
        for (Map.Entry<String, KeyRef> entry : profile.keybinds().entrySet()) {
            for (KeyBinding binding : bindings) {
                if (!binding.id().equals(entry.getKey())) {
                    continue;
                }
                KeyRef live = binding.boundKey();
                KeyRef stored = entry.getValue();
                boolean same = live.isUnbound() && stored.isUnbound() || live.sameInput(stored);
                if (!same) {
                    return true;
                }
            }
        }
        return false;
    }
}
