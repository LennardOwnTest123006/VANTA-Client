package dev.vanta.core.screen.common;

import dev.vanta.core.bridge.VanillaScreen;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.ui.UiContext;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Navigation between screens. VANTA screens are opened through the host ({@link dev.vanta.core.ui.UiHost#openScreen}
 * receives the {@link ScreenId}); vanilla screens go through the {@link dev.vanta.core.bridge.GameBridge}.
 * <p>
 * Screens are created by parameterless factories in the {@link dev.vanta.core.screen.ScreenRegistry}, so a request
 * such as "open Settings scrolled to this setting" is carried here as a <em>pending target</em>: the caller stores the
 * target and opens the screen, the opened screen takes the target in its constructor. Targets are consumed once.
 * One navigator instance is shared by all screens registered by the same {@code register(VantaServices)} call.
 */
public final class ScreenNavigator {
    private static final Map<VantaServices, ScreenNavigator> SHARED = new WeakHashMap<>();

    private final VantaServices services;
    private String pendingSettingId;
    private SettingCategory pendingCategory;
    private String pendingKeybindId;
    private String pendingNexusSection;
    private boolean pendingLocalAiReinstall;

    public ScreenNavigator(VantaServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /**
     * The navigator shared by every screen package registered for {@code services}. {@code Screens1},
     * {@code Screens2} and the HUD editors all resolve the same instance here, so a deep link stored by one screen
     * (for example "open Settings at this row") is picked up by the screen another package registered.
     */
    public static ScreenNavigator forServices(VantaServices services) {
        Objects.requireNonNull(services, "services");
        synchronized (SHARED) {
            return SHARED.computeIfAbsent(services, ScreenNavigator::new);
        }
    }

    /** The services this navigator belongs to. */
    public VantaServices services() {
        return services;
    }

    /** Opens a VANTA screen through the host. */
    public void openScreen(UiContext ctx, ScreenId id) {
        Objects.requireNonNull(id, "id");
        ctx.host().openScreen(id);
    }

    /** Opens a vanilla screen through the game bridge. */
    public void openVanilla(VanillaScreen screen) {
        services.game().openVanillaScreen(Objects.requireNonNull(screen, "screen"));
    }

    /** Opens the settings screen on the category of {@code setting}, scrolled to and highlighting its row. */
    public void openSetting(UiContext ctx, Setting<?> setting) {
        stageSetting(setting);
        openScreen(ctx, ScreenId.SETTINGS);
    }

    /** Opens the settings screen on a category. */
    public void openSettingsCategory(UiContext ctx, SettingCategory category) {
        stageCategory(category);
        openScreen(ctx, ScreenId.SETTINGS);
    }

    /** Stores the deep-link target for the next settings screen without opening it (hosts, previews, tests). */
    public void stageSetting(Setting<?> setting) {
        Objects.requireNonNull(setting, "setting");
        pendingSettingId = setting.id();
        pendingCategory = setting.category();
    }

    /** Stores the category the next settings screen opens on without opening it. */
    public void stageCategory(SettingCategory category) {
        pendingSettingId = null;
        pendingCategory = Objects.requireNonNull(category, "category");
    }

    /** Opens the keybinds screen with a mapping to highlight (consumed by the keybinds screen if it supports it). */
    public void openKeybind(UiContext ctx, String keybindId) {
        pendingKeybindId = Objects.requireNonNull(keybindId, "keybindId");
        openScreen(ctx, ScreenId.KEYBINDS);
    }

    /** Takes (and clears) the setting id the settings screen should reveal. */
    public Optional<String> takePendingSetting() {
        String id = pendingSettingId;
        pendingSettingId = null;
        return Optional.ofNullable(id);
    }

    /** Takes (and clears) the category the settings screen should open on. */
    public Optional<SettingCategory> takePendingCategory() {
        SettingCategory category = pendingCategory;
        pendingCategory = null;
        return Optional.ofNullable(category);
    }

    /**
     * Opens Vanta Nexus on a section ({@code NexusSection} id such as {@code waypoints}); the Nexus screen takes
     * the id in its constructor.
     */
    public void openNexus(UiContext ctx, String sectionId) {
        pendingNexusSection = Objects.requireNonNull(sectionId, "sectionId");
        openScreen(ctx, ScreenId.NEXUS);
    }

    /** Stores the Nexus section the next Nexus screen opens on without opening it (hosts, previews, tests). */
    public void stageNexusSection(String sectionId) {
        pendingNexusSection = Objects.requireNonNull(sectionId, "sectionId");
    }

    /** Takes (and clears) the Nexus section id the Nexus screen should open on. */
    public Optional<String> takePendingNexusSection() {
        String id = pendingNexusSection;
        pendingNexusSection = null;
        return Optional.ofNullable(id);
    }

    /**
     * Opens the Local AI setup screen as a reinstall: it offers the install (labelled Reinstall) although the files
     * are installed, so a player can repair or refresh them; the setup screen takes the flag in its constructor.
     */
    public void openLocalAiReinstall(UiContext ctx) {
        pendingLocalAiReinstall = true;
        openScreen(ctx, ScreenId.LOCAL_AI_SETUP);
    }

    /** Marks the next Local AI setup screen as a reinstall without opening it (hosts, previews, tests). */
    public void stageLocalAiReinstall() {
        pendingLocalAiReinstall = true;
    }

    /** Takes (and clears) whether the next Local AI setup screen opens as a reinstall. */
    public boolean takePendingLocalAiReinstall() {
        boolean reinstall = pendingLocalAiReinstall;
        pendingLocalAiReinstall = false;
        return reinstall;
    }

    /** Takes (and clears) the key mapping id the keybinds screen should reveal. */
    public Optional<String> takePendingKeybind() {
        String id = pendingKeybindId;
        pendingKeybindId = null;
        return Optional.ofNullable(id);
    }

    /** Whether a settings target is waiting (tests). */
    public boolean hasPendingSettingsTarget() {
        return pendingSettingId != null || pendingCategory != null;
    }
}
