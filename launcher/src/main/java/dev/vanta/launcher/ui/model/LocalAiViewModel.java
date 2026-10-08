package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.ai.LocalAiManifest;
import dev.vanta.launcher.core.ai.LocalAiReport;
import dev.vanta.launcher.core.ai.LocalAiState;
import dev.vanta.launcher.core.install.InstallListener;
import dev.vanta.launcher.core.install.InstallProgress;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.ui.Messages;
import dev.vanta.launcher.ui.backend.LauncherBackend;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * State and actions of the launcher-managed Local AI, shared by the Home status line, the Settings section and the
 * first-start offer: the last {@link LocalAiReport}, texts for the views, install/verify/remove with progress, and the
 * consent flow (ask once; "Not now" switches the automatic install off; an accepted or explicit install remembers the
 * consent so PLAY keeps the files complete).
 *
 * <p>Every blocking call runs through {@link Async}; properties change on the UI thread only.</p>
 */
public final class LocalAiViewModel {

    /** Activity. */
    public enum Activity {
        /** Nothing runs. */
        IDLE,
        /** The quick status check runs. */
        CHECKING,
        /** Downloading and installing. */
        INSTALLING,
        /** Hashing every file. */
        VERIFYING,
        /** Deleting the folder. */
        REMOVING
    }

    /**
     * What the consent dialog shows.
     *
     * @param runtimeLine  runtime: name, tag, file, size, licence, host
     * @param modelLine    model: name, quantisation, file, size, licence, host
     * @param totalSize    formatted total download size
     * @param folder       where the files land
     * @param requirements disk and memory line
     */
    public record Offer(String runtimeLine, String modelLine, String totalSize, String folder, String requirements) {
    }

    private final SessionModel session;
    private final LauncherBackend backend;
    private final UiExecutors executors;
    private final Messages messages;
    private final Formats formats;
    private final ErrorMessages errors;
    private final ToastModel toasts;
    private final LogBuffer launcherLog;

    private final ObjectProperty<LocalAiReport> report = new SimpleObjectProperty<>();
    private final ObjectProperty<Activity> activity = new SimpleObjectProperty<>(Activity.IDLE);
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final StringProperty progressText = new SimpleStringProperty("");
    private final BooleanProperty offeredThisSession = new SimpleBooleanProperty(false);
    private final StringBinding statusText;
    private final StringBinding detailText;
    private final StringBinding sizeText;
    private final BooleanBinding busy;
    private final BooleanBinding installed;
    private final BooleanBinding installable;
    private final BooleanBinding removable;
    private CancellationToken cancellation;
    private Future<?> running;

    /**
     * @param session     session (settings)
     * @param backend     backend
     * @param executors   executors
     * @param messages    messages
     * @param formats     formats
     * @param toasts      notifications
     * @param launcherLog launcher log buffer
     */
    public LocalAiViewModel(final SessionModel session, final LauncherBackend backend, final UiExecutors executors, final Messages messages,
                            final Formats formats, final ToastModel toasts, final LogBuffer launcherLog) {
        this.session = Objects.requireNonNull(session, "session");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.formats = Objects.requireNonNull(formats, "formats");
        this.errors = new ErrorMessages(messages, formats);
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.launcherLog = Objects.requireNonNull(launcherLog, "launcherLog");
        busy = Bindings.createBooleanBinding(() -> activity.get() != Activity.IDLE, activity);
        installed = Bindings.createBooleanBinding(() -> report.get() != null && report.get().isInstalled(), report);
        installable = Bindings.createBooleanBinding(() -> report.get() != null && report.get().installable(), report);
        removable = Bindings.createBooleanBinding(() -> report.get() != null
            && (report.get().isInstalled() || report.get().state() == LocalAiState.PARTIAL || report.get().bytesOnDisk() > 0), report);
        statusText = Bindings.createStringBinding(this::computeStatus, report, activity, progressText);
        detailText = Bindings.createStringBinding(this::computeDetail, report);
        sizeText = Bindings.createStringBinding(this::computeSize, report);
    }

    // ---------------------------------------------------------------- properties

    /** @return the last report (null until the first {@link #refresh()} finished) */
    public ReadOnlyObjectProperty<LocalAiReport> reportProperty() {
        return report;
    }

    /** @return the last report */
    public Optional<LocalAiReport> report() {
        return Optional.ofNullable(report.get());
    }

    /** @return what runs right now */
    public ReadOnlyObjectProperty<Activity> activityProperty() {
        return activity;
    }

    /** @return "Local AI: ready" / "Local AI: not installed" / ... */
    public StringBinding statusTextProperty() {
        return statusText;
    }

    /** @return the versions line (installed, or available in this build) */
    public StringBinding detailTextProperty() {
        return detailText;
    }

    /** @return size on disk, or the download size while nothing is installed */
    public StringBinding sizeTextProperty() {
        return sizeText;
    }

    /** @return whether install, verify or remove runs */
    public BooleanBinding busyProperty() {
        return busy;
    }

    /** @return whether runtime and model are in place */
    public BooleanBinding installedProperty() {
        return installed;
    }

    /** @return whether this build can install the Local AI on this platform */
    public BooleanBinding installableProperty() {
        return installable;
    }

    /** @return whether there is anything to remove */
    public BooleanBinding removableProperty() {
        return removable;
    }

    /** @return install progress fraction (negative = indeterminate) */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress;
    }

    /** @return install progress text */
    public ReadOnlyStringProperty progressTextProperty() {
        return progressText;
    }

    /** @return whether the consent dialog was shown in this session already */
    public ReadOnlyBooleanProperty offeredThisSessionProperty() {
        return offeredThisSession;
    }

    /** @return the Local AI folder */
    public Path folder() {
        return backend.paths().localAiDir();
    }

    // ---------------------------------------------------------------- actions

    /** Re-reads the install state (quick check). */
    public void refresh() {
        if (busy.get()) {
            return;
        }
        activity.set(Activity.CHECKING);
        Async.run(executors, backend::localAiStatus, r -> {
            activity.set(Activity.IDLE);
            report.set(r);
        }, error -> {
            activity.set(Activity.IDLE);
            launcherLog.append(LogLevel.WARN, "Local AI status could not be read: " + errors.describe(error));
        });
    }

    /**
     * First start: refreshes the state, then either hands the consent dialog to the view (automatic install on, not yet
     * accepted, nothing installed, installable), or, when consent was given earlier and files are missing or outdated,
     * installs right away. Runs at most once per session.
     *
     * @param onOffer receives the offer on the UI thread when the player has to be asked
     */
    public void checkOffer(final Consumer<Offer> onOffer) {
        if (offeredThisSession.get()) {
            return;
        }
        offeredThisSession.set(true);
        Async.run(executors, backend::localAiStatus, r -> {
            report.set(r);
            final LauncherSettings settings = backend.settings();
            if (!settings.localAiAutoInstall() || !r.installable()) {
                return;
            }
            final boolean missing = r.state() == LocalAiState.NOT_INSTALLED || r.state() == LocalAiState.PARTIAL;
            if (!settings.localAiConsent()) {
                if (missing) {
                    offer().ifPresent(onOffer);
                }
            } else if (missing || r.outdated()) {
                launcherLog.append(LogLevel.INFO, "Local AI is " + r.state().label() + (r.outdated() ? " (update available)" : "")
                    + "; installing as agreed earlier");
                install();
            }
        }, error -> launcherLog.append(LogLevel.WARN, "Local AI status could not be read: " + errors.describe(error)));
    }

    /** @return the consent dialog content, empty when nothing can be installed on this system */
    public Optional<Offer> offer() {
        final LocalAiReport r = report.get();
        if (r == null || !r.installable()) {
            return Optional.empty();
        }
        final LocalAiManifest m = r.manifest().orElseThrow();
        final LocalAiManifest.PlatformFile platform = m.platform(r.platform()).orElseThrow();
        final String runtimeLine = messages.format("localai.offer.runtime", m.runtime().name(), m.runtime().tag(), platform.file(),
            formats.bytes(platform.size()), m.runtime().license(), hostOf(platform.url()));
        final String modelLine = messages.format("localai.offer.model", m.model().name(), m.model().quantization(), m.model().file(),
            formats.bytes(m.model().size()), m.model().license(), hostOf(m.model().url()));
        final String requirements = messages.format("localai.offer.requirements", formats.memory(m.requirements().diskMb()),
            formats.memory(m.requirements().ramMb()));
        return Optional.of(new Offer(runtimeLine, modelLine, formats.bytes(r.downloadBytes()), folder().toString(), requirements));
    }

    /** The player agreed: remember the consent, then install. */
    public void accept() {
        final LauncherSettings updated = backend.settings().withLocalAiAccepted(true).withInstallLocalAi(true);
        session.saveSettings(updated, this::install, error -> toasts.error(messages.get("home.toast.error.title"), errors.describe(error)));
    }

    /** "Not now": the automatic install is switched off, so the question is not asked again. */
    public void decline() {
        if (!backend.settings().localAiAutoInstall()) {
            return;
        }
        session.saveSettings(backend.settings().withInstallLocalAi(false), () -> launcherLog.append(LogLevel.INFO,
            "Local AI not installed; automatic installation switched off (Settings > Local AI)"),
            error -> toasts.error(messages.get("home.toast.error.title"), errors.describe(error)));
    }

    /** Downloads, verifies and installs; an explicit install counts as consent. */
    public void install() {
        if (busy.get()) {
            return;
        }
        final CancellationToken token = new CancellationToken();
        cancellation = token;
        activity.set(Activity.INSTALLING);
        progress.set(-1);
        progressText.set(messages.get("home.progress.preparing"));
        running = Async.run(executors, () -> backend.installLocalAi(installListener(), token), r -> {
            finish(r);
            if (!backend.settings().localAiConsent()) {
                session.saveSettings(backend.settings().withLocalAiAccepted(true), () -> { }, error -> { });
            }
            if (r.isInstalled()) {
                launcherLog.append(LogLevel.INFO, "Local AI installed: " + r.describeVersions() + " in " + r.dir());
                toasts.success(messages.get("localai.toast.installed.title"), messages.format("localai.toast.installed.message",
                    r.describeVersions(), r.dir().toString()));
            } else {
                final String why = String.join("; ", r.problems());
                launcherLog.append(LogLevel.WARN, "Local AI is " + r.state().label() + " after the install: " + why);
                toasts.error(messages.get("localai.toast.failed.title"), why);
            }
        }, error -> {
            finish(null);
            if (Async.isCancellation(error)) {
                launcherLog.append(LogLevel.INFO, "Local AI install cancelled");
                toasts.info(messages.get("localai.toast.cancelled.title"), messages.get("localai.toast.cancelled.message"));
            } else {
                final String text = errors.describe(error);
                launcherLog.append(LogLevel.ERROR, "Local AI install failed: " + text);
                toasts.error(messages.get("localai.toast.failed.title"), text);
            }
            refresh();
        });
    }

    /** Hashes every installed file again. */
    public void verify() {
        if (busy.get()) {
            return;
        }
        activity.set(Activity.VERIFYING);
        Async.run(executors, backend::verifyLocalAi, r -> {
            finish(r);
            if (r.isInstalled()) {
                toasts.success(messages.get("localai.toast.verified.title"), messages.get("localai.toast.verified.message"));
            } else {
                final String why = String.join("; ", r.problems());
                launcherLog.append(LogLevel.WARN, "Local AI verification: " + why);
                toasts.warning(messages.get("localai.toast.verifyFailed.title"), why);
            }
        }, error -> {
            finish(null);
            toasts.error(messages.get("localai.toast.verifyFailed.title"), errors.describe(error));
        });
    }

    /** Deletes the Local AI folder (the view asked for confirmation) and switches the automatic install off. */
    public void remove() {
        if (busy.get()) {
            return;
        }
        final long bytes = report.get() == null ? 0L : report.get().bytesOnDisk();
        activity.set(Activity.REMOVING);
        Async.run(executors, () -> {
            backend.removeLocalAi();
            return backend.localAiStatus();
        }, r -> {
            finish(r);
            launcherLog.append(LogLevel.INFO, "Removed the Local AI (" + formats.bytes(bytes) + " freed)");
            toasts.success(messages.get("localai.toast.removed.title"), messages.format("localai.toast.removed.message", formats.bytes(bytes)));
            if (backend.settings().localAiAutoInstall()) {
                session.saveSettings(backend.settings().withInstallLocalAi(false), () -> { },
                    error -> toasts.error(messages.get("home.toast.error.title"), errors.describe(error)));
            }
        }, error -> {
            finish(null);
            toasts.error(messages.get("home.toast.error.title"), errors.describe(error));
        });
    }

    /** Cancels a running install. */
    public void cancel() {
        if (cancellation != null) {
            cancellation.cancel();
        }
        if (running != null) {
            running.cancel(true);
        }
    }

    // ---------------------------------------------------------------- internals

    private void finish(final LocalAiReport r) {
        activity.set(Activity.IDLE);
        progress.set(0);
        progressText.set("");
        cancellation = null;
        running = null;
        if (r != null) {
            report.set(r);
        }
    }

    private InstallListener installListener() {
        return new InstallListener() {
            @Override
            public void onProgress(final InstallProgress p) {
                executors.onUi(() -> {
                    if (activity.get() != Activity.INSTALLING) {
                        return;
                    }
                    progress.set(p.total() > 0 && p.done() > 0 ? p.fraction() : -1);
                    progressText.set(p.message() == null ? "" : p.message());
                });
            }

            @Override
            public void onLog(final String message) {
                launcherLog.append(LogLevel.INFO, message);
            }
        };
    }

    private String computeStatus() {
        switch (activity.get()) {
            case INSTALLING -> {
                final String text = progressText.get();
                return messages.get("localai.status.installing") + (text.isEmpty() ? "" : " " + text);
            }
            case VERIFYING -> {
                return messages.get("localai.status.verifying");
            }
            case REMOVING -> {
                return messages.get("localai.status.removing");
            }
            default -> {
                // fall through to the report
            }
        }
        final LocalAiReport r = report.get();
        if (r == null) {
            return messages.get("localai.status.checking");
        }
        return switch (r.state()) {
            case INSTALLED -> r.outdated() ? messages.get("localai.status.updateAvailable") : messages.get("localai.status.ready");
            case PARTIAL -> messages.get("localai.status.partial");
            case UNSUPPORTED_PLATFORM -> messages.format("localai.status.unsupported", r.platform().replace('-', '/'));
            case UNAVAILABLE -> messages.get("localai.status.unavailable");
            default -> messages.get("localai.status.notInstalled");
        };
    }

    private String computeDetail() {
        final LocalAiReport r = report.get();
        if (r == null) {
            return "";
        }
        if (r.installed().isPresent()) {
            final String when = r.installed().get().installedAt().isEmpty() ? "" : formats.isoDateTime(r.installed().get().installedAt());
            return when.isEmpty() ? r.installed().get().describe() : messages.format("localai.detail.installed", r.installed().get().describe(), when);
        }
        return r.manifest().map(m -> messages.format("localai.detail.available", m.describe())).orElse(messages.get("localai.detail.none"));
    }

    private String computeSize() {
        final LocalAiReport r = report.get();
        if (r == null) {
            return "";
        }
        if (r.bytesOnDisk() > 0) {
            return messages.format("localai.size.onDisk", formats.bytes(r.bytesOnDisk()));
        }
        return r.installable() ? messages.format("localai.size.download", formats.bytes(r.downloadBytes())) : "";
    }

    private static String hostOf(final String url) {
        try {
            final String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    /** @return the problems of the last report, for the Settings section */
    public List<String> problems() {
        return report.get() == null ? List.of() : report.get().problems();
    }
}
