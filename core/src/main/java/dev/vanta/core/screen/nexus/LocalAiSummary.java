package dev.vanta.core.screen.nexus;

import dev.vanta.core.ai.LocalAiInstalled;
import dev.vanta.core.ai.LocalAiManifest;
import dev.vanta.core.ai.LocalAiService;
import dev.vanta.core.ai.LocalAiStatus;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.screen.common.LabelValueRow;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.widget.Badge;
import dev.vanta.core.ui.widget.Card;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The honest facts about the Local AI as text, every value taken from the manifest, the install record or the
 * service and never typed by hand: runtime name and tag, model name and quantization, download size, licences,
 * the hosts contacted during the download, disk and RAM requirements, the install folder and the status. Shared by
 * the assistant's Local AI card, the Nexus settings and the setup screen.
 */
public final class LocalAiSummary {
    private LocalAiSummary() {
    }

    /** "llama.cpp b11429 (llama-server)" from the installed record when present, else from the manifest. */
    public static String runtimeLine(LocalAiService service) {
        Optional<LocalAiInstalled> installed = service.installed();
        if (installed.isPresent()) {
            LocalAiManifest.Runtime runtime = installed.get().runtime();
            return runtime.name() + " " + runtime.tag() + " (" + runtime.component() + ")";
        }
        return service.manifest().map(m -> m.runtime().name() + " " + m.runtime().tag() + " (" + m.runtime().component()
                + ")").orElse(Lang.tr("vanta.common.not_available"));
    }

    /** "Qwen3-1.7B Q8_0". */
    public static String modelLine(LocalAiService service) {
        Optional<LocalAiInstalled> installed = service.installed();
        if (installed.isPresent()) {
            return installed.get().model().name() + " " + installed.get().model().quantization();
        }
        return service.manifest().map(m -> m.model().name() + " " + m.model().quantization())
                .orElse(Lang.tr("vanta.common.not_available"));
    }

    /** Total download for this computer, formatted; "n/a" while the manifest is unresolved. */
    public static String sizeLine(LocalAiService service) {
        long bytes = service.downloadBytes();
        return bytes > 0 ? LocalAiService.formatBytes(bytes) : Lang.tr("vanta.common.not_available");
    }

    /** "MIT (llama.cpp), Apache-2.0 (Qwen3-1.7B)". */
    public static String licenceLine(LocalAiService service) {
        return service.manifest().map(m -> m.runtime().license() + " (" + m.runtime().name() + "), "
                + m.model().license() + " (" + m.model().name() + ")").orElse(Lang.tr("vanta.common.not_available"));
    }

    /** The hosts the download contacts, from the manifest URLs ("github.com, huggingface.co"). */
    public static String hostsLine(LocalAiService service) {
        Optional<LocalAiManifest> manifest = service.manifest();
        if (manifest.isEmpty()) {
            return Lang.tr("vanta.common.not_available");
        }
        Set<String> hosts = new LinkedHashSet<>();
        for (LocalAiManifest.RuntimePlatform platform : manifest.get().runtime().platforms().values()) {
            host(platform.url()).ifPresent(hosts::add);
        }
        host(manifest.get().model().url()).ifPresent(hosts::add);
        return hosts.isEmpty() ? Lang.tr("vanta.common.not_available") : String.join(", ", hosts);
    }

    /** "2200 MB disk, 3072 MB RAM". */
    public static String requirementsLine(LocalAiService service) {
        return service.manifest().map(m -> Lang.tr("vanta.localai.card.requirements_value", m.requirements().diskMb(),
                m.requirements().ramMb())).orElse(Lang.tr("vanta.common.not_available"));
    }

    /** The install folder as an absolute path. */
    public static String folderLine(LocalAiService service) {
        return service.paths().root().toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    /** "Installed by the VANTA Launcher" or "Managed by the client". */
    public static String managedLine(LocalAiService service) {
        return Lang.tr(service.isLauncherManaged() ? "vanta.localai.reason.launcher_managed"
                : "vanta.localai.card.client_managed");
    }

    static Optional<String> host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || host.isBlank() ? Optional.empty() : Optional.of(host.toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The status pill text for the current status, e.g. "Vanta Nexus ready" or "Local AI: Not installed". */
    public static String statusText(LocalAiStatus status) {
        return switch (status) {
            case READY -> Lang.tr("vanta.nexus.status.ready");
            case STARTING -> Lang.tr("vanta.nexus.status.starting");
            case BUSY -> Lang.tr("vanta.nexus.status.busy");
            default -> Lang.tr("vanta.nexus.status.local_ai", Lang.tr(status.langKey()));
        };
    }

    /** The badge tone for a status. */
    public static Badge.Tone statusTone(LocalAiStatus status) {
        return switch (status) {
            case READY -> Badge.Tone.SUCCESS;
            case STARTING, BUSY -> Badge.Tone.INFO;
            case INSTALLED -> Badge.Tone.NEUTRAL;
            case NOT_INSTALLED, PARTIAL -> Badge.Tone.WARNING;
            case UNSUPPORTED_PLATFORM, FAILED -> Badge.Tone.DANGER;
        };
    }

    /**
     * The fact rows of the Local AI card. {@code withFolder} adds the install folder and who manages it (the settings
     * card); the assistant's install card leaves them out.
     */
    public static List<LabelValueRow> rows(LocalAiService service, boolean withFolder) {
        List<LabelValueRow> rows = new ArrayList<>();
        rows.add(row("vanta.localai.card.runtime", runtimeLine(service)));
        rows.add(row("vanta.localai.card.model", modelLine(service)));
        rows.add(row("vanta.localai.card.size", sizeLine(service)));
        rows.add(row("vanta.localai.card.licences", licenceLine(service)));
        rows.add(row("vanta.localai.card.hosts", hostsLine(service)));
        rows.add(row("vanta.localai.card.requirements", requirementsLine(service)));
        if (withFolder) {
            rows.add(row("vanta.localai.card.folder", folderLine(service)));
            rows.add(row("vanta.localai.card.managed", managedLine(service)));
        }
        return rows;
    }

    private static LabelValueRow row(String key, String value) {
        LabelValueRow row = new LabelValueRow(Lang.tr(key), value);
        row.setTooltip(value);
        return row;
    }

    /** A card titled "Local AI" with the fact rows. */
    public static Card card(LocalAiService service, boolean withFolder) {
        Card card = new Card(Lang.tr("vanta.localai.card.title")).caption(Lang.tr("vanta.localai.card.caption"));
        card.body().gap(Theme.SPACE_2);
        for (LabelValueRow row : rows(service, withFolder)) {
            card.add(row);
        }
        return card;
    }
}
