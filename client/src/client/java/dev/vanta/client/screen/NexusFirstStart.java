package dev.vanta.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vanta.client.VantaClient;
import dev.vanta.client.VantaRuntime;
import dev.vanta.core.VantaVersion;
import dev.vanta.core.ai.LocalAiService;
import dev.vanta.core.ai.LocalAiStatus;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.modrinth.PackOffer;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.widget.Dialog;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The one-time "Vanta Nexus: Local AI is not installed" notice of the main menu. Shown as a dialog with
 * <em>Open Nexus</em> (opens {@link ScreenId#NEXUS}, where [Install Local AI] lives) and <em>Not now</em>, next to
 * the Performance pack offer: when the VANTA main menu is first shown in a session, only when no other popup is open.
 * <p>
 * It appears when {@code nexus.enabled} is on, the Local AI status is {@link LocalAiStatus#NOT_INSTALLED} in a
 * client-managed folder (a launcher-managed install is never offered here; the launcher handles it), and the player
 * has not dismissed it for this client version. Any way of closing the dialog, Open Nexus included, records the
 * current client version in {@code config/vanta/nexus-first-start.json}; a later client version asks once more.
 * Nothing downloads from here: the install starts only with a click on [Install Local AI] in Nexus.
 * <p>
 * Like the pack offer it never appears in the client game test ({@code -Dvanta.gametest=true}); the decision itself
 * is {@link #decide} so the game test can check it.
 */
public final class NexusFirstStart {
    /** File below {@code config/vanta} that remembers the dismissal. */
    public static final String FILE_NAME = "nexus-first-start.json";
    /** Key of the client version the notice was dismissed for. */
    public static final String KEY_DISMISSED_VERSION = "dismissedVersion";

    private static final String KEY_TITLE = "vanta.nexus.first_start.title";
    private static final String KEY_BODY = "vanta.nexus.first_start.body";
    private static final String KEY_OPEN = "vanta.nexus.first_start.open";
    private static final String KEY_NOT_NOW = "vanta.nexus.first_start.not_now";

    private static boolean shownThisSession;

    private NexusFirstStart() {
    }

    /** Called when the VANTA main menu finished its first layout (also after resizes; shown at most once). */
    public static void onMainMenuShown(VantaScreen screen, VantaRuntime runtime) {
        try {
            showIfWanted(screen, runtime);
        } catch (RuntimeException e) {
            VantaClient.LOGGER.warn("Nexus first-start notice failed", e);
        }
    }

    /** Forgets that the notice was shown in this session (game test). */
    public static void resetSession() {
        shownThisSession = false;
    }

    /**
     * The decision, free of game state so it can be tested.
     *
     * @param nexusEnabled     {@code nexus.enabled}
     * @param status           the Local AI status
     * @param launcherManaged  whether the note points at a launcher install
     * @param dismissedVersion client version the notice was dismissed for (empty when never)
     * @param clientVersion    the running client version
     */
    public static boolean decide(boolean nexusEnabled, LocalAiStatus status, boolean launcherManaged,
                                 Optional<String> dismissedVersion, String clientVersion) {
        Objects.requireNonNull(status, "status");
        if (!nexusEnabled || launcherManaged || status != LocalAiStatus.NOT_INSTALLED) {
            return false;
        }
        return !dismissedVersion.map(v -> v.equals(clientVersion)).orElse(false);
    }

    private static void showIfWanted(VantaScreen screen, VantaRuntime runtime) {
        if (shownThisSession || PackOffer.inGameTest() || screen.screenId() != ScreenId.MAIN_MENU) {
            return;
        }
        VantaServices services = runtime.services();
        if (!services.isLoaded()) {
            return;
        }
        LocalAiService ai = services.localAi();
        if (!services.settings().get(VantaSettings.NEXUS_AUTO_INSTALL)) {
            // "Offer the Local AI install when Nexus opens" is off: no notice; Nexus > Settings still installs.
            return;
        }
        if (!decide(services.settings().get(VantaSettings.NEXUS_ENABLED), ai.status(), ai.isLauncherManaged(),
                readDismissedVersion(services), VantaVersion.CLIENT)) {
            return;
        }
        UiContext ctx = screen.ui().context();
        if (ctx == null || ctx.popups().isOpen()) {
            // The Performance pack offer (or another popup) is up; try again the next time the menu appears.
            return;
        }
        shownThisSession = true;
        Dialog dialog = Dialog.confirm(ctx, text(KEY_TITLE, "Vanta Nexus"),
                text(KEY_BODY, "Vanta Nexus: Local AI is not installed. Open Nexus to install it."),
                text(KEY_OPEN, "Open Nexus"), text(KEY_NOT_NOW, "Not now"), false,
                () -> VantaScreens.open(ScreenId.NEXUS, screen));
        dialog.onDismiss(() -> rememberDismissed(services));
        VantaClient.LOGGER.info("Vanta Nexus first-start notice shown (Local AI not installed)");
    }

    /** The translated text when the language file knows the key, otherwise the plain English fallback. */
    private static String text(String key, String fallback) {
        return Lang.has(key) ? Lang.tr(key) : fallback;
    }

    private static Path file(VantaServices services) {
        return services.paths().root().resolve(FILE_NAME);
    }

    /** The client version the notice was dismissed for, when recorded. */
    static Optional<String> readDismissedVersion(VantaServices services) {
        Optional<JsonObject> json = services.jsonStore().readObject(file(services));
        if (json.isEmpty()) {
            return Optional.empty();
        }
        JsonElement version = json.get().get(KEY_DISMISSED_VERSION);
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isString()
                || version.getAsString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(version.getAsString());
    }

    private static void rememberDismissed(VantaServices services) {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_DISMISSED_VERSION, VantaVersion.CLIENT);
        services.jsonStore().writeObject(file(services), json);
    }
}
