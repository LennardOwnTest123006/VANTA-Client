package dev.vanta.core.screen.nexus;

import dev.vanta.core.ai.InstallStep;
import dev.vanta.core.ai.LocalAiException;
import dev.vanta.core.ai.LocalAiService;
import dev.vanta.core.ai.LocalAiStatus;
import dev.vanta.core.ai.NexusAssistant;
import dev.vanta.core.ai.NexusTranscript;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.notifications.NotificationKind;
import dev.vanta.core.screen.ScreenId;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.common.InfoBanner;
import dev.vanta.core.screen.common.ScreenNavigator;
import dev.vanta.core.settings.VantaSettings;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Align;
import dev.vanta.core.ui.layout.Column;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.layout.ScrollPanel;
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.Card;
import dev.vanta.core.ui.widget.Label;
import dev.vanta.core.ui.widget.ProgressBar;
import dev.vanta.core.ui.widget.TextField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The AI Assistant section: the Local AI status pill, the transcript (player and assistant bubbles, "Applied"
 * receipts with Undo, rejected actions explained, failures named), the example prompt chips while the chat is
 * empty, the Local AI card with [Install Local AI] while nothing is installed, and the single-line prompt field
 * (Enter sends). Questions go through {@link NexusAssistant#ask}; the reply arrives on the render thread and the UI
 * never blocks.
 */
public final class AssistantPanel extends NexusPanel {
    /** The example prompts offered while the transcript is empty. */
    public static final List<String> EXAMPLE_PROMPTS = List.of("Make my HUD minimal", "Only show FPS and coordinates",
            "Create a recording profile", "Move the FPS counter to the bottom right");

    private final NexusAssistant assistant;
    private final LocalAiService localAi;
    private final NexusTranscript transcript;
    private final Column root = new Column(Theme.SPACE_3);
    private final Badge statusPill = new Badge("", Badge.Tone.NEUTRAL);
    private final Label statusReason = new Label("", Label.Variant.MUTED);
    private final Button clear;
    private final Column chat = new Column(Theme.SPACE_3);
    private final ScrollPanel chatScroll = new ScrollPanel(chat);
    private final TextField input;
    private final Button send;
    private final List<Runnable> unsubscribe = new ArrayList<>();
    private boolean pending;
    private boolean scrollToEnd;
    private Button installButton;
    private Button undoButton;

    public AssistantPanel(VantaServices services, ScreenNavigator navigator) {
        super(services, navigator, NexusSection.ASSISTANT);
        this.assistant = services.nexus();
        this.localAi = services.localAi();
        this.transcript = services.nexusTranscript();
        remove(scrollPanel());
        add(root);

        Row status = new Row(Theme.SPACE_3).align(Align.CENTER);
        statusPill.setId("nexus.assistant.status");
        status.add(statusPill);
        statusReason.flex(1f);
        statusReason.setId("nexus.assistant.reason");
        status.add(statusReason);
        clear = Button.ghost(Lang.tr("vanta.nexus.assistant.clear"), this::clearChat).compact(true);
        clear.setId("nexus.assistant.clear");
        status.add(clear);
        root.add(status);

        chatScroll.flex(1f);
        chatScroll.setId("nexus.assistant.transcript");
        root.add(chatScroll);

        Row prompt = new Row(Theme.SPACE_3).align(Align.CENTER);
        input = new TextField().placeholder(Lang.tr("vanta.nexus.assistant.placeholder"))
                .maxLength(NexusAssistant.MAX_QUESTION);
        input.flex(1f);
        input.setId("nexus.assistant.input");
        input.onSubmit(this::send);
        prompt.add(input);
        send = Button.primary(Lang.tr("vanta.nexus.assistant.send"), () -> send(input.text())).compact(true);
        send.icon(Icons.CHEVRON_RIGHT);
        send.setId("nexus.assistant.send");
        prompt.add(send);
        root.add(prompt);

        unsubscribe.add(localAi.addListener(new LocalAiService.Listener() {
            @Override
            public void onStatusChanged(LocalAiStatus state, String reason) {
                refresh();
            }

            @Override
            public void onProgress(InstallStep step, long bytesDone, long bytesTotal, double bytesPerSecond) {
            }

            @Override
            public void onFinished(Optional<LocalAiException> error) {
                refresh();
            }
        }));
        unsubscribe.add(transcript.onChanged(entries -> rebuildChat()));
        unsubscribe.add(services.settings().onChange(VantaSettings.NEXUS_ENABLED, (before, after) -> refresh()));
        refresh();
    }

    // ---- accessors (tests) ---------------------------------------------------------------------------------------

    public TextField inputField() {
        return input;
    }

    public Button sendButton() {
        return send;
    }

    public Badge statusPill() {
        return statusPill;
    }

    public Label statusReasonLabel() {
        return statusReason;
    }

    /** The Install button of the Local AI card, when the card is shown. */
    public Optional<Button> installButton() {
        return Optional.ofNullable(installButton);
    }

    /** The Undo button of the last applied turn, when shown. */
    public Optional<Button> undoButton() {
        return Optional.ofNullable(undoButton);
    }

    /** The transcript scroll panel. */
    public ScrollPanel chatScroll() {
        return chatScroll;
    }

    /** The bubbles currently shown, oldest first. */
    public List<Bubble> bubbles() {
        List<Bubble> out = new ArrayList<>();
        for (UiNode child : chat.children()) {
            if (child instanceof Bubble bubble) {
                out.add(bubble);
            }
        }
        return out;
    }

    /** True while a question is in flight. */
    public boolean isPending() {
        return pending;
    }

    // ---- actions -------------------------------------------------------------------------------------------------

    /** Sends a question through the assistant (ignored while another one is in flight or when blank). */
    public void send(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || pending || assistant.isBusy()) {
            return;
        }
        input.setText("");
        pending = true;
        assistant.ask(text, reply -> {
            pending = false;
            rebuildChat();
            updateInputState();
            if (!reply.isError() && !reply.applied().isEmpty()) {
                services().notifications().nexusApplied(reply.applied().size());
            }
        });
        rebuildChat();
        updateInputState();
    }

    /** Reverts the last turn that changed something. */
    public void undoLast() {
        if (assistant.undoLast()) {
            services().notifications().post(NotificationKind.INFO, Lang.tr("vanta.nexus.undone.title"),
                    Lang.tr("vanta.nexus.undone.body"));
        }
        rebuildChat();
    }

    private void clearChat() {
        assistant.clearTranscript();
        rebuildChat();
    }

    private void openSetup() {
        navigator().openScreen(context(), ScreenId.LOCAL_AI_SETUP);
    }

    private UiContext context() {
        return lastContext;
    }

    private UiContext lastContext;

    // ---- state ---------------------------------------------------------------------------------------------------

    /** Re-reads the Local AI status and rebuilds the chat column. */
    public void refresh() {
        LocalAiStatus status = localAi.status();
        statusPill.setText(LocalAiSummary.statusText(status));
        statusPill.tone(LocalAiSummary.statusTone(status));
        String reason = localAi.statusReason();
        statusReason.setText(reason);
        statusReason.setVisible(!reason.isEmpty());
        statusReason.setTooltip(reason.isEmpty() ? null : reason);
        rebuildChat();
        updateInputState();
    }

    private void updateInputState() {
        boolean enabled = !pending && localAi.isEnabled();
        input.setEnabled(enabled);
        send.setEnabled(enabled);
    }

    private boolean showsInstallCard(LocalAiStatus status) {
        return !status.isInstalled() && !status.isRunning();
    }

    private void rebuildChat() {
        chat.clearChildren();
        installButton = null;
        undoButton = null;
        if (!localAi.isEnabled()) {
            InfoBanner off = new InfoBanner(InfoBanner.Tone.WARNING, Lang.tr("vanta.nexus.assistant.disabled.title"),
                    Lang.tr("vanta.nexus.assistant.disabled.body"));
            Button turnOn = Button.primary(Lang.tr("vanta.nexus.assistant.disabled.enable"),
                    () -> services().settings().set(VantaSettings.NEXUS_ENABLED, true)).compact(true);
            turnOn.setId("nexus.assistant.enable");
            off.action(turnOn);
            off.setId("nexus.assistant.disabled");
            chat.add(off);
        }
        LocalAiStatus status = localAi.status();
        if (showsInstallCard(status)) {
            chat.add(installCard(status));
        }
        List<NexusTranscript.Entry> entries = transcript.entries();
        if (entries.isEmpty() && !pending) {
            chat.add(welcome());
        }
        int lastAssistant = -1;
        for (int i = entries.size() - 1; i >= 0; i--) {
            NexusTranscript.Entry entry = entries.get(i);
            if (entry.role() == NexusTranscript.Role.ASSISTANT && !entry.error()) {
                lastAssistant = i;
                break;
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            NexusTranscript.Entry entry = entries.get(i);
            Bubble bubble = new Bubble(entry);
            if (i == lastAssistant && !pending && services().nexusUndo().canUndo()) {
                undoButton = Button.secondary(Lang.tr("vanta.nexus.assistant.undo"), this::undoLast).compact(true);
                undoButton.icon(Icons.RESET);
                undoButton.setId("nexus.assistant.undo");
                bubble.action(undoButton);
            }
            chat.add(bubble);
        }
        if (pending) {
            chat.add(new Bubble(Bubble.Kind.PENDING, Lang.tr("vanta.nexus.assistant.thinking"), List.of(), List.of()));
        }
        scrollToEnd = true;
        if (lastContext != null) {
            lastContext.requestLayout();
        }
    }

    private UiNode welcome() {
        Column column = new Column(Theme.SPACE_3);
        column.add(new Label(Lang.tr("vanta.nexus.assistant.welcome"), Label.Variant.BODY).wrap(true));
        column.add(new Label(Lang.tr("vanta.nexus.assistant.try"), Label.Variant.MUTED).wrap(true));
        ChipFlow chips = new ChipFlow();
        for (int i = 0; i < EXAMPLE_PROMPTS.size(); i++) {
            String prompt = EXAMPLE_PROMPTS.get(i);
            Button chip = Button.secondary(prompt, () -> send(prompt)).compact(true);
            chip.setId("nexus.assistant.chip." + i);
            chips.add(chip);
        }
        column.add(chips);
        return column;
    }

    private UiNode installCard(LocalAiStatus status) {
        Card card = LocalAiSummary.card(localAi, false);
        card.setId("nexus.assistant.localai");
        String reason = localAi.statusReason();
        if (status == LocalAiStatus.UNSUPPORTED_PLATFORM || status == LocalAiStatus.FAILED) {
            Label problem = new Label(reason.isEmpty() ? Lang.tr(status.langKey()) : reason, Label.Variant.BODY)
                    .wrap(true);
            problem.setId("nexus.assistant.localai.problem");
            card.add(problem);
            return card;
        }
        if (localAi.isLauncherManaged()) {
            card.add(new Label(Lang.tr("vanta.localai.card.launcher_install_hint"), Label.Variant.MUTED).wrap(true));
            return card;
        }
        card.add(new Label(Lang.tr("vanta.localai.card.consent"), Label.Variant.MUTED).wrap(true));
        Row actions = new Row(Theme.SPACE_3);
        installButton = Button.primary(Lang.tr("vanta.localai.install"), this::openSetup).compact(true);
        installButton.icon(Icons.DOWNLOAD);
        installButton.setId("nexus.assistant.install");
        installButton.setEnabled(localAi.isSupported() && !localAi.isBusy());
        actions.add(installButton);
        card.add(actions);
        return card;
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------------

    @Override
    public void onShown(UiContext ctx) {
        lastContext = ctx;
        refresh();
    }

    @Override
    public void tick(UiContext ctx) {
        lastContext = ctx;
        // The server status moves without events while a question runs (BUSY) and when the idle timeout stops it.
        LocalAiStatus status = localAi.status();
        String text = LocalAiSummary.statusText(status);
        if (!text.equals(statusPill.text())) {
            statusPill.setText(text);
            statusPill.tone(LocalAiSummary.statusTone(status));
            ctx.requestLayout();
        }
    }

    @Override
    public void dispose() {
        for (Runnable r : unsubscribe) {
            r.run();
        }
        unsubscribe.clear();
    }

    @Override
    public void layout(UiContext ctx) {
        lastContext = ctx;
        Rect b = bounds();
        chatScroll.edgeFade(Colors.withAlpha(ctx.theme().bgBase(), 0.92f));
        root.setBounds(b);
        root.layout(ctx);
        if (scrollToEnd) {
            scrollToEnd = false;
            chatScroll.setScrollY(ctx, chatScroll.maxScroll(), false);
            root.layout(ctx);
        }
    }

    // ---- bubbles -------------------------------------------------------------------------------------------------

    /** One transcript entry: the text, its receipts and an optional action button (Undo). */
    public static final class Bubble extends UiNode {
        /** Who wrote the bubble. */
        public enum Kind { USER, ASSISTANT, ERROR, PENDING }

        private static final int PAD = Theme.SPACE_4;
        private final Kind kind;
        private final String text;
        private final List<String> applied;
        private final List<String> rejected;
        private List<String> lines = List.of();
        private List<String> receiptLines = List.of();
        private Button action;
        private ProgressBar progress;

        Bubble(NexusTranscript.Entry entry) {
            this(entry.role() == NexusTranscript.Role.USER ? Kind.USER : entry.error() ? Kind.ERROR : Kind.ASSISTANT,
                    entry.text(), entry.applied(), entry.rejected());
        }

        Bubble(Kind kind, String text, List<String> applied, List<String> rejected) {
            this.kind = kind;
            this.text = text == null ? "" : text;
            this.applied = List.copyOf(applied);
            this.rejected = List.copyOf(rejected);
            setId("nexus.bubble." + kind.name().toLowerCase(java.util.Locale.ROOT));
            if (kind == Kind.PENDING) {
                progress = ProgressBar.indeterminate();
                add(progress);
            }
        }

        public Kind kind() {
            return kind;
        }

        public String text() {
            return text;
        }

        public List<String> applied() {
            return applied;
        }

        public List<String> rejected() {
            return rejected;
        }

        void action(Button button) {
            this.action = button;
            add(button);
        }

        private String header() {
            return switch (kind) {
                case USER -> Lang.tr("vanta.nexus.assistant.you");
                case PENDING, ASSISTANT -> Lang.tr("vanta.nexus.assistant.name");
                case ERROR -> Lang.tr("vanta.nexus.assistant.problem");
            };
        }

        private List<String> receipts() {
            List<String> out = new ArrayList<>();
            for (String line : applied) {
                out.add(Lang.tr("vanta.nexus.receipt.applied", line));
            }
            for (String line : rejected) {
                out.add(Lang.tr("vanta.nexus.receipt.rejected", line));
            }
            return out;
        }

        private int textWidth(int width) {
            return Math.max(20, width - PAD * 2);
        }

        private int heightFor(UiContext ctx, int width) {
            int lh = ctx.lineHeight(FontKind.UI);
            List<String> wrapped = text.isEmpty() ? List.of() : CanvasText.wrap(text, textWidth(width), FontKind.UI,
                    ctx.metrics());
            int receipts = 0;
            for (String receipt : receipts()) {
                receipts += CanvasText.wrap(receipt, textWidth(width) - Theme.SPACE_5, FontKind.UI, ctx.metrics()).size();
            }
            int h = PAD + lh + Theme.SPACE_1 + wrapped.size() * lh;
            if (receipts > 0) {
                h += Theme.SPACE_2 + receipts * lh;
            }
            if (progress != null) {
                h += Theme.SPACE_2 + ProgressBar.HEIGHT;
            }
            if (action != null) {
                h += Theme.SPACE_3 + Button.HEIGHT;
            }
            return h + PAD;
        }

        @Override
        protected Size measure(UiContext ctx) {
            return measure(ctx, -1);
        }

        @Override
        protected Size measure(UiContext ctx, int availableWidth) {
            int w = explicitWidth() > 0 ? explicitWidth() : availableWidth > 0 ? availableWidth
                    : Math.max(bounds().w(), 240);
            return new Size(w, heightFor(ctx, w));
        }

        @Override
        public void layout(UiContext ctx) {
            Rect b = bounds();
            lines = text.isEmpty() ? List.of() : CanvasText.wrap(text, textWidth(b.w()), FontKind.UI, ctx.metrics());
            List<String> wrappedReceipts = new ArrayList<>();
            for (String receipt : receipts()) {
                wrappedReceipts.addAll(CanvasText.wrap(receipt, textWidth(b.w()) - Theme.SPACE_5, FontKind.UI,
                        ctx.metrics()));
            }
            receiptLines = wrappedReceipts;
            if (progress != null) {
                progress.setBounds(b.x() + PAD, b.bottom() - PAD - ProgressBar.HEIGHT, Math.max(0, b.w() - PAD * 2),
                        ProgressBar.HEIGHT);
                progress.layout(ctx);
            }
            if (action != null) {
                Size s = action.preferredSize(ctx);
                action.setBounds(b.x() + PAD, b.bottom() - PAD - Button.HEIGHT, Math.min(s.w(), b.w() - PAD * 2),
                        Button.HEIGHT);
                action.layout(ctx);
            }
        }

        @Override
        protected void renderSelf(Canvas canvas, UiContext ctx) {
            Theme theme = ctx.theme();
            Rect b = bounds();
            int background = switch (kind) {
                case USER -> Colors.withAlpha(theme.accent(), 0.16f);
                case ERROR -> Colors.withAlpha(theme.danger(), 0.12f);
                default -> theme.surface2();
            };
            int border = switch (kind) {
                case USER -> Colors.withAlpha(theme.accent(), 0.4f);
                case ERROR -> Colors.withAlpha(theme.danger(), 0.4f);
                default -> theme.borderSubtle();
            };
            canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, background);
            canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, border);
            int lh = canvas.lineHeight(FontKind.UI);
            int x = b.x() + PAD;
            int y = b.y() + PAD;
            int headerColor = switch (kind) {
                case USER -> theme.accentHover();
                case ERROR -> theme.danger();
                default -> theme.textMuted();
            };
            canvas.text(canvas.textClipped(header(), b.w() - PAD * 2, FontKind.UI_BOLD), x, y, headerColor,
                    FontKind.UI_BOLD, false);
            y += lh + Theme.SPACE_1;
            int textColor = kind == Kind.PENDING ? theme.textMuted() : theme.textPrimary();
            for (String line : lines) {
                canvas.text(line, x, y, textColor, FontKind.UI, false);
                y += lh;
            }
            if (!receiptLines.isEmpty()) {
                y += Theme.SPACE_2;
                int appliedLines = 0;
                for (String a : applied) {
                    appliedLines += CanvasText.wrap(Lang.tr("vanta.nexus.receipt.applied", a),
                            textWidth(b.w()) - Theme.SPACE_5, FontKind.UI, ctx.metrics()).size();
                }
                for (int i = 0; i < receiptLines.size(); i++) {
                    boolean ok = i < appliedLines;
                    int color = ok ? theme.success() : theme.warning();
                    (ok ? Icons.CHECK : Icons.WARNING).draw(canvas, x, y, 8, color);
                    canvas.text(receiptLines.get(i), x + Theme.SPACE_5, y, ok ? theme.textSecondary() : color,
                            FontKind.UI, false);
                    y += lh;
                }
            }
        }
    }
}
