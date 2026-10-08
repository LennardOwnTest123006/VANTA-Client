package dev.vanta.core.screen.nexus;

import dev.vanta.core.ai.InstallStep;
import dev.vanta.core.ai.LocalAiException;
import dev.vanta.core.ai.LocalAiService;
import dev.vanta.core.ai.LocalAiStatus;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.LabelValueRow;
import dev.vanta.core.screen.common.LinkRow;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.SettingRow;
import dev.vanta.core.settings.Setting;
import dev.vanta.core.settings.SettingCategory;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Dialog;
import dev.vanta.core.ui.widget.Label;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Settings section: the six {@code nexus.*} settings as the usual rows, the Local AI status card (what is
 * installed, where, who manages it) with Verify files, Reinstall and Remove Local AI (with confirmation), Install
 * when nothing is installed, and the link to the full Settings screen. Install, Reinstall and Remove are hidden
 * for a launcher-managed install, which the client only reads.
 */
public final class NexusSettingsPanel extends NexusPanel {
    private final LocalAiService localAi;
    private final List<SettingRow> rows = new ArrayList<>();
    private final Badge status = new Badge("", Badge.Tone.NEUTRAL);
    private final Label reason = new Label("", Label.Variant.MUTED).wrap(true);
    private final Card localAiCard;
    private final ChipFlow actions = new ChipFlow();
    private final Button verify;
    private final Button reinstall;
    private final Button remove;
    private final Button install;
    private final List<LabelValueRow> factRows = new ArrayList<>();
    private final Runnable unsubscribe;
    private UiContext lastContext;

    public NexusSettingsPanel(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, NexusSection.SETTINGS);
        this.localAi = services.localAi();

        Card settings = new Card(Lang.tr("vanta.category.nexus")).caption(Lang.tr("vanta.category.nexus.description"));
        settings.setId("nexus.settings.card");
        settings.body().gap(0);
        List<Setting<?>> nexusSettings = services.settingsRegistry().byCategory(SettingCategory.NEXUS);
        for (int i = 0; i < nexusSettings.size(); i++) {
            SettingRow row = new SettingRow(nexusSettings.get(i), services.settings(), id -> { });
            row.last(i == nexusSettings.size() - 1);
            rows.add(row);
            settings.add(row);
        }
        content().add(settings);

        localAiCard = new Card(Lang.tr("vanta.localai.card.title")).caption(Lang.tr("vanta.localai.card.status_caption"));
        localAiCard.setId("nexus.settings.localai");
        localAiCard.body().gap(Theme.SPACE_2);
        status.setId("nexus.settings.localai.status");
        localAiCard.headerSlot(status);
        reason.setId("nexus.settings.localai.reason");
        localAiCard.add(reason);
        rebuildFacts();
        verify = Button.secondary(Lang.tr("vanta.localai.verify"), () -> localAi.verify()).compact(true);
        verify.icon(Icons.CHECK);
        verify.setId("nexus.settings.verify");
        reinstall = Button.secondary(Lang.tr("vanta.localai.reinstall"), this::openReinstall).compact(true);
        reinstall.icon(Icons.DOWNLOAD);
        reinstall.setId("nexus.settings.reinstall");
        remove = Button.danger(Lang.tr("vanta.localai.remove"), this::confirmRemove).compact(true);
        remove.icon(Icons.TRASH);
        remove.setId("nexus.settings.remove");
        install = Button.primary(Lang.tr("vanta.localai.install"), this::openSetup).compact(true);
        install.icon(Icons.DOWNLOAD);
        install.setId("nexus.settings.install");
        actions.add(install);
        actions.add(verify);
        actions.add(reinstall);
        actions.add(remove);
        localAiCard.add(actions);
        content().add(localAiCard);

        content().add(new LinkRow(Icons.GEAR, Lang.tr("vanta.nexus.settings.all"),
                Lang.tr("vanta.nexus.settings.all.description"),
                () -> navigator.openSettingsCategory(lastContext, SettingCategory.NEXUS)).id("nexus.settings.all"));

        unsubscribe = localAi.addListener(new LocalAiService.Listener() {
            @Override
            public void onStatusChanged(LocalAiStatus state, String why) {
                refresh();
            }

            @Override
            public void onProgress(InstallStep step, long bytesDone, long bytesTotal, double bytesPerSecond) {
            }

            @Override
            public void onFinished(Optional<LocalAiException> error) {
                refresh();
            }
        });
        refresh();
    }

    private void rebuildFacts() {
        for (LabelValueRow row : factRows) {
            localAiCard.body().remove(row);
        }
        factRows.clear();
        for (LabelValueRow row : LocalAiSummary.rows(localAi, true)) {
            factRows.add(row);
            localAiCard.add(row);
        }
        // Keep the actions below the facts.
        if (actions.parent() != null) {
            localAiCard.body().remove(actions);
            localAiCard.add(actions);
        }
    }

    // ---- accessors (tests) ---------------------------------------------------------------------------------------

    public List<SettingRow> settingRows() {
        return List.copyOf(rows);
    }

    public Button verifyButton() {
        return verify;
    }

    public Button reinstallButton() {
        return reinstall;
    }

    public Button removeButton() {
        return remove;
    }

    public Button installButton() {
        return install;
    }

    public Badge statusBadge() {
        return status;
    }

    public String reasonText() {
        return reason.text();
    }

    // ---- actions -------------------------------------------------------------------------------------------------

    private void openSetup() {
        navigator().openScreen(lastContext, ScreenId.LOCAL_AI_SETUP);
    }

    /** Opens the setup screen with the install offered although the files are installed (repair or refresh). */
    private void openReinstall() {
        navigator().openLocalAiReinstall(lastContext);
    }

    /** Asks before deleting the client-managed Local AI folder. */
    public Dialog confirmRemove() {
        return Dialog.confirm(lastContext, Lang.tr("vanta.localai.remove.title"),
                Lang.tr("vanta.localai.remove.body", LocalAiSummary.folderLine(localAi)), Lang.tr("vanta.localai.remove"),
                Lang.tr("vanta.common.cancel"), true, localAi::remove);
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    /** Re-reads the status and shows only the buttons that apply. */
    public void refresh() {
        LocalAiStatus state = localAi.status();
        status.setText(LocalAiSummary.statusText(state));
        status.tone(LocalAiSummary.statusTone(state));
        String why = localAi.statusReason();
        reason.setText(why);
        reason.setVisible(!why.isEmpty());
        boolean busy = localAi.isBusy();
        boolean installed = state.isInstalled() || state.isRunning();
        boolean launcher = localAi.isLauncherManaged();
        boolean supported = localAi.isSupported();
        install.setVisible(!installed && !launcher && supported);
        install.setEnabled(!busy);
        verify.setVisible(installed && supported);
        verify.setEnabled(!busy);
        reinstall.setVisible(installed && !launcher && supported);
        reinstall.setEnabled(!busy);
        remove.setVisible(installed && !launcher && supported);
        remove.setEnabled(!busy);
        rebuildFacts();
        for (SettingRow row : rows) {
            row.refresh();
        }
        if (lastContext != null) {
            lastContext.requestLayout();
        }
    }

    @Override
    public void onShown(UiContext ctx) {
        lastContext = ctx;
        refresh();
    }

    @Override
    public void tick(UiContext ctx) {
        lastContext = ctx;
        boolean busy = localAi.isBusy();
        if (verify.isEnabled() == busy) {
            refresh();
        }
    }

    @Override
    public void layout(UiContext ctx) {
        lastContext = ctx;
        super.layout(ctx);
    }

    @Override
    public void dispose() {
        unsubscribe.run();
    }
}
