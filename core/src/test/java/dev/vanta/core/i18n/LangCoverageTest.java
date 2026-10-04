package dev.vanta.core.i18n;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vanta.core.accessibility.ColorBlindPalette;
import dev.vanta.core.bridge.Cardinal;
import dev.vanta.core.bridge.VanillaOption;
import dev.vanta.core.cosmetics.Badge;
import dev.vanta.core.cosmetics.CosmeticsRegistry;
import dev.vanta.core.cosmetics.HudTheme;
import dev.vanta.core.cosmetics.MenuBackground;
import dev.vanta.core.cosmetics.MenuParticles;
import dev.vanta.core.cosmetics.UiThemeDefinition;
import dev.vanta.core.crosshair.CrosshairPreset;
import dev.vanta.core.crosshair.CrosshairPresets;
import dev.vanta.core.crosshair.CrosshairShape;
import dev.vanta.core.hud.HudAnchor;
import dev.vanta.core.hud.HudPreset;
import dev.vanta.core.hud.HudPresets;
import dev.vanta.core.hud.HudProp;
import dev.vanta.core.hud.HudWidgetType;
import dev.vanta.core.keybinds.VantaKeys;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.notifications.NotificationPosition;
import dev.vanta.core.perf.FpsLimitPreset;
import dev.vanta.core.perf.PerformancePreset;
import dev.vanta.core.profiles.BuiltInProfiles;
import dev.vanta.core.profiles.ProfileImportException;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.search.ActionEntry;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.settings.SettingKind;
import dev.vanta.core.settings.VantaSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every translation key referenced by the domain model and by string literals in the domain sources must exist in
 * {@code en_us.json}.
 */
class LangCoverageTest {
    private static final Pattern LITERAL = Pattern.compile("\"((?:vanta|key)\\.[a-z0-9_.-]+)\"");

    @Test
    void everyModelKeyExists() {
        Set<String> missing = new TreeSet<>();
        for (SettingCategory category : SettingCategory.values()) {
            check(missing, category.langKey(), category.descriptionKey());
        }
        for (Setting<?> setting : VantaSettings.all()) {
            check(missing, setting.titleKey(), setting.descriptionKey());
            if (setting.kind() == SettingKind.ENUM) {
                for (Object option : setting.options()) {
                    check(missing, optionKey(setting, option));
                }
            }
            setting.actionId().ifPresent(id -> check(missing, "vanta.action." + id));
        }
        for (VanillaOption option : VanillaOption.values()) {
            check(missing, option.langKey());
            for (String value : option.enumValues()) {
                check(missing, option.enumValueLangKey(value));
            }
        }
        for (HudWidgetType type : HudWidgetType.values()) {
            check(missing, type.langKey(), type.descriptionKey());
            for (HudProp prop : type.settingsSchema()) {
                check(missing, prop.langKey());
                for (String option : prop.options()) {
                    check(missing, prop.optionLangKey(option));
                }
            }
        }
        for (HudAnchor anchor : HudAnchor.values()) {
            check(missing, anchor.langKey());
        }
        for (HudPreset preset : HudPresets.builtIns()) {
            check(missing, preset.langKey(), preset.descriptionKey());
        }
        for (CrosshairShape shape : CrosshairShape.values()) {
            check(missing, shape.langKey());
        }
        for (CrosshairPreset preset : CrosshairPresets.builtIns()) {
            check(missing, preset.langKey(), preset.descriptionKey());
        }
        for (ScreenId screen : ScreenId.values()) {
            check(missing, screen.langKey(), screen.descriptionKey());
        }
        for (ActionEntry action : ActionEntry.builtIns()) {
            check(missing, action.langKey(), action.descriptionKey());
        }
        check(missing, VantaKeys.CATEGORY);
        for (VantaKeys.Definition key : VantaKeys.all()) {
            check(missing, key.langKey(), key.descriptionKey());
        }
        for (PerformancePreset preset : PerformancePreset.values()) {
            check(missing, preset.langKey(), preset.descriptionKey());
        }
        for (LangKeyed keyed : all(FpsLimitPreset.values(), NotificationPosition.values(), NotificationKind.values(),
                Badge.values(), HudTheme.values(), MenuBackground.values(), MenuParticles.values(),
                ColorBlindPalette.values(), ProfileImportException.Reason.values())) {
            check(missing, keyed.langKey());
        }
        for (Cardinal cardinal : Cardinal.values()) {
            check(missing, cardinal.langKey(), cardinal.axisKey());
        }
        for (UiThemeDefinition theme : new CosmeticsRegistry().themes()) {
            check(missing, theme.langKey(), theme.descriptionKey());
        }
        for (String id : BuiltInProfiles.IDS) {
            check(missing, BuiltInProfiles.nameKey(id), BuiltInProfiles.descriptionKey(id));
        }
        assertTrue(missing.isEmpty(), "Missing lang keys: " + missing);
    }

    @Test
    void everyLiteralKeyInDomainSourcesExists() throws IOException {
        Path root = Path.of("/home/user/VANTA-Client/core/src/main/java/dev/vanta/core");
        if (!Files.isDirectory(root)) {
            return;
        }
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/ui/")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = LITERAL.matcher(source);
                while (m.find()) {
                    String key = m.group(1);
                    if (key.endsWith(".") || key.startsWith("key.keyboard") || key.startsWith("key.mouse")
                            || key.startsWith("vanta.setting.") || key.startsWith("vanta.hud.widget.")) {
                        continue;
                    }
                    check(missing, key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Literal lang keys without translation: " + missing);
    }

    @SuppressWarnings("unchecked")
    private static String optionKey(Setting<?> setting, Object option) {
        return ((Setting<Object>) setting).optionLangKey(option);
    }

    private static void check(Set<String> missing, String... keys) {
        for (String key : keys) {
            if (!Lang.has(key)) {
                missing.add(key);
            }
        }
    }

    private static List<LangKeyed> all(LangKeyed[]... groups) {
        List<LangKeyed> out = new ArrayList<>();
        for (LangKeyed[] group : groups) {
            out.addAll(List.of(group));
        }
        return out;
    }
}
