package dev.vanta.core.screen.nexus;

import dev.vanta.core.ai.InstallStep;
import dev.vanta.core.ai.LocalAiException;
import dev.vanta.core.ai.LocalAiService;
import dev.vanta.core.ai.LocalAiStatus;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.screen.common.VantaUiScreen;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.VantaShell;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.ProgressBar;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The Local AI setup: the Local AI card (what is downloaded, from where, how big, which licences), the seven
 * {@link InstallStep}s with progress bars, bytes and speed, [Install Local AI] (nothing downloads before this click),
 * [Cancel] while installing, and afterwards either "Vanta Nexus ready" with a button to the assistant (the runtime is
 * started right after the install and the INITIALIZING / READY steps follow its status) or the failure reason with
 * [Retry]. All progress arrives through {@link LocalAiService.Listener} on the render thread.
 */
public final class LocalAiSetupScreen extends VantaUiScreen {
    /** What the screen is doing. */
    public enum Phase { IDLE, INSTALLING, STARTING, READY, FAILED }

    private final LocalAiService localAi;
    private final Map<InstallStep, StepRow> steps = new EnumMap<>(InstallStep.class);
    private VantaShell shell;
    private Badge statusBadge;
    private Label message;
    private Button installButton;
    private Button cancelButton;
    private Button retryButton;
    private Button openAssistantButton;
    private ScrollPanel scroll;
    private Phase phase = Phase.IDLE;
    private String failure = "";
    private Runnable unsubscribe;
    private boolean startRequested;

    public LocalAiSetupScreen(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, ScreenId.LOCAL_AI_SETUP);
        this.localAi = services.localAi();
    }

    // ---- accessors (tests) ---------------------------------------------------------------------------------------

    public Phase phase() {
        return phase;
    }

    public Button installButton() {
        return installButton;
    }

    public Button cancelButton() {
        return cancelButton;
    }

    public Button retryButton() {
        return retryButton;
    }

    public Button openAssistantButton() {
        return openAssistantButton;
    }

    /** The row of a step. */
    public StepRow step(InstallStep step) {
        return steps.get(step);
    }

    /** The failure reason shown (empty unless {@link Phase#FAILED}). */
    public String failureText() {
        return failure;
    }

    public String messageText() {
        return message == null ? "" : message.text();
    }

    public ScrollPanel scrollPanel() {
        return scroll;
    }

    // ---- build ---------------------------------------------------------------------------------------------------

    @Override
    protected UiNode build(UiContext ctx) {
        shell = new VantaShell(Lang.tr("vanta.screen.local_ai_setup"), this::close);
        shell.backTooltip(Lang.tr("vanta.common.back"));
        Column content = new Column(Theme.SPACE_4);

        Card card = LocalAiSummary.card(localAi, false);
        card.setId("localai.setup.card");
        statusBadge = new Badge("", Badge.Tone.NEUTRAL);
        statusBadge.setId("localai.setup.status");
        card.headerSlot(statusBadge);
        card.add(new Label(Lang.tr("vanta.localai.card.consent"), Label.Variant.MUTED).wrap(true));
        content.add(card);

        Card stepsCard = new Card(Lang.tr("vanta.localai.setup.steps"));
        stepsCard.setId("localai.setup.steps");
        stepsCard.body().gap(Theme.SPACE_2);
        for (InstallStep step : InstallStep.values()) {
            StepRow row = new StepRow(step);
            steps.put(step, row);
            stepsCard.add(row);
        }
        message = new Label("", Label.Variant.BODY).wrap(true);
        message.setId("localai.setup.message");
        stepsCard.add(message);
        ChipFlow actions = new ChipFlow();
        installButton = Button.primary(Lang.tr("vanta.localai.install"), this::install).compact(true);
        installButton.icon(Icons.DOWNLOAD);
        installButton.setId("localai.setup.install");
        actions.add(installButton);
        cancelButton = Button.secondary(Lang.tr("vanta.common.cancel"), this::cancel).compact(true);
        cancelButton.setId("localai.setup.cancel");
        actions.add(cancelButton);
        retryButton = Button.primary(Lang.tr("vanta.common.try_again"), this::install).compact(true);
        retryButton.icon(Icons.RESET);
        retryButton.setId("localai.setup.retry");
        actions.add(retryButton);
        openAssistantButton = Button.primary(Lang.tr("vanta.localai.setup.open_assistant"), this::openAssistant)
                .compact(true);
        openAssistantButton.icon(Icons.NEXUS);
        openAssistantButton.setId("localai.setup.openAssistant");
        actions.add(openAssistantButton);
        stepsCard.add(actions);
        content.add(stepsCard);

        scroll = new ScrollPanel(content).edgeFade(Colors.withAlpha(ctx.theme().bgBase(), 0.92f));
        shell.content(scroll);
        syncWithService();
        return shell;
    }

    @Override
    protected void onScreenInit() {
        unsubscribe = localAi.addListener(new LocalAiService.Listener() {
            @Override
            public void onStatusChanged(LocalAiStatus status, String reason) {
                onServiceStatus(status, reason);
            }

            @Override
            public void onProgress(InstallStep step, long bytesDone, long bytesTotal, double bytesPerSecond) {
                onServiceProgress(step, bytesDone, bytesTotal, bytesPerSecond);
            }

            @Override
            public void onFinished(Optional<LocalAiException> error) {
                onServiceFinished(error);
            }
        });
        syncWithService();
    }

    @Override
    protected void onScreenClose() {
        if (unsubscribe != null) {
            unsubscribe.run();
            unsubscribe = null;
        }
    }

    // ---- actions -------------------------------------------------------------------------------------------------

    /** Starts the install (the player's click is the consent; nothing downloads before it). */
    public void install() {
        if (localAi.isInstalling() || phase == Phase.STARTING) {
            // Already running: the rows follow the service's events.
            refresh();
            return;
        }
        // Prepared before the call: with a synchronous executor (tests, previews) the whole install and the
        // runtime start finish inside localAi.install(), and their events must land on the INSTALLING phase.
        phase = Phase.INSTALLING;
        failure = "";
        startRequested = false;
        for (StepRow row : steps.values()) {
            row.reset();
        }
        steps.get(InstallStep.CHECKING).active();
        if (!localAi.install()) {
            phase = Phase.FAILED;
            failure = refusalReason();
            for (StepRow row : steps.values()) {
                row.reset();
            }
        }
        refresh();
    }

    /** Why {@link LocalAiService#install()} refused to start: read-only, unsupported, or already running. */
    private String refusalReason() {
        if (localAi.isLauncherManaged()) {
            return Lang.tr("vanta.localai.error.read_only");
        }
        if (!localAi.isSupported()) {
            return localAi.statusReason().isEmpty() ? Lang.tr(localAi.status().langKey()) : localAi.statusReason();
        }
        return Lang.tr("vanta.localai.error.busy");
    }

    /** Asks the running install to stop. */
    public void cancel() {
        localAi.cancelInstall();
    }

    private void openAssistant() {
        navigator().openNexus(context(), NexusSection.ASSISTANT.id());
    }

    // ---- service events ------------------------------------------------------------------------------------------

    /**
     * Shows a progress state without a running install (previews and tests of the step list; the service itself
     * reports progress through its listener).
     */
    public void showProgress(InstallStep step, long bytesDone, long bytesTotal, double bytesPerSecond) {
        onServiceProgress(step, bytesDone, bytesTotal, bytesPerSecond);
    }

    /** Shows a failure state without a running install (previews and tests). */
    public void showFailure(String reason) {
        phase = Phase.FAILED;
        failure = reason == null ? "" : reason;
        refresh();
    }

    private void onServiceProgress(InstallStep step, long done, long total, double bps) {
        if (phase != Phase.INSTALLING) {
            phase = Phase.INSTALLING;
            failure = "";
        }
        for (InstallStep s : InstallStep.values()) {
            StepRow row = steps.get(s);
            if (s.ordinal() < step.ordinal()) {
                row.done();
            } else if (s == step) {
                row.active();
                row.progress(done, total, bps);
            }
        }
        refresh();
    }

    private void onServiceFinished(Optional<LocalAiException> error) {
        if (error.isPresent()) {
            LocalAiException e = error.get();
            phase = Phase.FAILED;
            failure = Lang.tr(e.kind().langKey()) + (e.getMessage() == null || e.getMessage().isBlank() ? ""
                    : ": " + e.getMessage());
            refresh();
            return;
        }
        if (!localAi.isInstalled()) {
            // A verification or removal finished, not an install.
            syncWithService();
            return;
        }
        for (InstallStep s : InstallStep.values()) {
            if (s.ordinal() <= InstallStep.INSTALLING.ordinal()) {
                steps.get(s).done();
            }
        }
        // Start the runtime right away so the player sees Initializing and then Vanta Nexus ready.
        phase = Phase.STARTING;
        steps.get(InstallStep.INITIALIZING).active();
        startRequested = true;
        if (!localAi.start()) {
            phase = Phase.READY;
            steps.get(InstallStep.INITIALIZING).done();
            steps.get(InstallStep.READY).done();
        }
        refresh();
    }

    private void onServiceStatus(LocalAiStatus status, String reason) {
        if (phase == Phase.STARTING) {
            if (status == LocalAiStatus.READY || status == LocalAiStatus.BUSY) {
                steps.get(InstallStep.INITIALIZING).done();
                steps.get(InstallStep.READY).done();
                phase = Phase.READY;
            } else if (status == LocalAiStatus.FAILED) {
                phase = Phase.FAILED;
                failure = reason.isEmpty() ? Lang.tr(status.langKey()) : reason;
            }
        }
        refresh();
    }

    /** Reads the service state once (opening the screen during an install, or with an install already present). */
    private void syncWithService() {
        if (localAi.isInstalling()) {
            phase = Phase.INSTALLING;
        } else if (phase != Phase.FAILED && !startRequested) {
            if (localAi.status().isInstalled() || localAi.status().isRunning()) {
                phase = localAi.status() == LocalAiStatus.READY || localAi.status() == LocalAiStatus.BUSY ? Phase.READY
                        : Phase.READY;
                for (StepRow row : steps.values()) {
                    row.done();
                }
            } else {
                phase = Phase.IDLE;
            }
        }
        refresh();
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    private void refresh() {
        if (statusBadge == null) {
            return;
        }
        LocalAiStatus status = localAi.status();
        statusBadge.setText(LocalAiSummary.statusText(status));
        statusBadge.tone(LocalAiSummary.statusTone(status));
        boolean canInstall = localAi.isSupported() && !localAi.isLauncherManaged() && !localAi.isBusy();
        installButton.setVisible(phase == Phase.IDLE);
        installButton.setEnabled(canInstall);
        cancelButton.setVisible(phase == Phase.INSTALLING);
        retryButton.setVisible(phase == Phase.FAILED);
        retryButton.setEnabled(canInstall);
        openAssistantButton.setVisible(phase == Phase.READY);
        String text;
        Integer color = null;
        switch (phase) {
            case IDLE -> {
                if (localAi.isLauncherManaged()) {
                    text = Lang.tr("vanta.localai.card.launcher_install_hint");
                } else if (!localAi.isSupported()) {
                    text = localAi.statusReason().isEmpty() ? Lang.tr(status.langKey()) : localAi.statusReason();
                    color = theme().danger();
                } else {
                    text = Lang.tr("vanta.localai.setup.idle");
                }
            }
            case INSTALLING -> text = Lang.tr("vanta.localai.setup.installing");
            case STARTING -> text = Lang.tr("vanta.localai.step.initializing");
            case READY -> {
                text = Lang.tr("vanta.localai.step.ready");
                color = theme().success();
            }
            default -> {
                text = failure;
                color = theme().danger();
            }
        }
        message.setText(text);
        if (color != null) {
            message.color(color);
        } else {
            message.variant(Label.Variant.BODY);
        }
        invalidateLayout();
    }

    /** One install step: state glyph, title, progress bar with bytes and speed while active. */
    public final class StepRow extends UiNode {
        private final InstallStep step;
        private final ProgressBar bar = new ProgressBar(0f);
        private boolean isDone;
        private boolean isActive;
        private String detail = "";

        StepRow(InstallStep step) {
            this.step = step;
            setId("localai.setup.step." + step.id());
            bar.setVisible(false);
            add(bar);
        }

        public InstallStep step() {
            return step;
        }

        public boolean isDone() {
            return isDone;
        }

        public boolean isActive() {
            return isActive;
        }

        /** The bytes / speed line shown while the step runs. */
        public String detail() {
            return detail;
        }

        public ProgressBar progressBar() {
            return bar;
        }

        void reset() {
            isDone = false;
            isActive = false;
            detail = "";
            bar.setVisible(false);
            bar.setFraction(0f);
        }

        void done() {
            isDone = true;
            isActive = false;
            bar.setVisible(false);
            detail = "";
        }

        void active() {
            isActive = true;
            isDone = false;
        }

        void progress(long done, long total, double bps) {
            if (total > 0) {
                bar.setVisible(true);
                bar.setFraction((float) Math.min(1.0, done / (double) total));
                detail = Lang.tr("vanta.localai.progress.bytes", Lang.tr(step.langKey()), LocalAiService.formatBytes(done),
                        LocalAiService.formatBytes(total), LocalAiService.formatBytes(Math.round(bps)));
            } else {
                bar.setVisible(false);
                detail = "";
            }
        }

        private boolean showsBar() {
            return isActive && bar.isVisible();
        }

        @Override
        protected Size measure(UiContext ctx) {
            int h = ctx.lineHeight(FontKind.UI) + Theme.SPACE_2;
            if (showsBar()) {
                h += Theme.SPACE_2 + ProgressBar.HEIGHT + Theme.SPACE_1 + ctx.lineHeight(FontKind.UI);
            }
            return new Size(160, h);
        }

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            if (showsBar()) {
                int y = b.y() + ctx.lineHeight(FontKind.UI) + Theme.SPACE_2 + Theme.SPACE_2;
                bar.setBounds(b.x() + Theme.SPACE_6, y, Math.max(0, b.w() - Theme.SPACE_6), ProgressBar.HEIGHT);
                bar.layout(ctx);
            }
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            int lh = canvas.lineHeight(FontKind.UI);
            int y = b.y() + Theme.SPACE_1;
            int glyph = 9;
            int gx = b.x() + Theme.SPACE_2;
            if (isDone) {
                Icons.CHECK.draw(canvas, gx, y, glyph, theme.success());
            } else if (isActive) {
                canvas.fillRounded(gx + 2, y + 2, glyph - 4, glyph - 4, Theme.RADIUS_MD, theme.accentHover());
            } else {
                canvas.strokeRounded(gx + 2, y + 2, glyph - 4, glyph - 4, Theme.RADIUS_MD,
                        Colors.withAlpha(theme.textMuted(), 0.6f));
            }
            int color = isDone ? theme.textSecondary() : isActive ? theme.textPrimary() : theme.textMuted();
            String title = Lang.tr("vanta.localai.setup.step_label", step.number(), InstallStep.count(),
                    Lang.tr(step.langKey()));
            canvas.text(canvas.textClipped(title, b.w() - Theme.SPACE_6, isActive ? FontKind.UI_BOLD : FontKind.UI),
                    b.x() + Theme.SPACE_6, y, color, isActive ? FontKind.UI_BOLD : FontKind.UI, false);
            if (showsBar() && !detail.isEmpty()) {
                canvas.text(canvas.textClipped(detail, b.w() - Theme.SPACE_6, FontKind.UI), b.x() + Theme.SPACE_6,
                        bar.bounds().bottom() + Theme.SPACE_1, theme.textMuted(), FontKind.UI, false);
            }
        }

        @Override
        public String toString() {
            return "StepRow[" + step.id().toLowerCase(Locale.ROOT) + (isDone ? " done" : isActive ? " active" : "") + "]";
        }
    }
}
