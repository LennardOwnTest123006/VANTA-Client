package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.install.InstalledClient;
import dev.vanta.launcher.core.install.NotPublishedException;
import dev.vanta.launcher.core.model.ReleaseManifest;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.core.update.UpdateInfo;
import dev.vanta.launcher.core.update.UpdateService;
import dev.vanta.launcher.core.util.LauncherPackaging;
import dev.vanta.launcher.ui.LauncherLinks;
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
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Update checks and the update banner. Downloads are verified by the core before anything is used; a launcher
 * installer is only handed to the operating system after the view asked the user.
 */
public final class UpdateViewModel {

    /** Why the client is not updatable. */
    public enum ClientAvailability {
        /** Not checked yet. */
        UNKNOWN,
        /** No releases URL configured. */
        NOT_CONFIGURED,
        /**
         * No VANTA client is installed: the latest release is known ({@link #latestClientVersionProperty()}), but there is
         * nothing to update. The regular install (PLAY) or "Use with Minecraft Launcher" installs it.
         */
        NOT_INSTALLED,
        /** Manifest found, nothing newer. */
        UP_TO_DATE,
        /** A newer version with a download. */
        UPDATE_AVAILABLE,
        /** A newer version announced without download. */
        ANNOUNCED,
        /** The manifest has no download at all. */
        NOT_PUBLISHED,
        /** The check failed. */
        FAILED
    }

    private final SessionModel session;
    private final LauncherBackend backend;
    private final UiExecutors executors;
    private final Messages messages;
    private final Formats formats;
    private final ErrorMessages errors;
    private final ToastModel toasts;
    private final LauncherLinks links;

    private final ObjectProperty<UpdateInfo> launcherUpdate = new SimpleObjectProperty<>();
    private final ObjectProperty<UpdateInfo> clientUpdate = new SimpleObjectProperty<>();
    private final ObjectProperty<ClientAvailability> clientAvailability = new SimpleObjectProperty<>(ClientAvailability.UNKNOWN);
    private final StringProperty latestClientVersion = new SimpleStringProperty("");
    private final BooleanProperty latestClientDownloadable = new SimpleBooleanProperty(false);
    private final BooleanProperty checking = new SimpleBooleanProperty(false);
    private final BooleanProperty dismissed = new SimpleBooleanProperty(false);
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final DoubleProperty progress = new SimpleDoubleProperty(-1);
    private final StringProperty progressText = new SimpleStringProperty("");
    private final StringProperty checkError = new SimpleStringProperty("");
    private final BooleanBinding bannerVisible;
    private final StringBinding bannerText;
    private boolean checkedOnce;
    private boolean publishingInstalled;
    private boolean installedChangedDuringCheck;

    /**
     * @param session   session
     * @param backend   backend
     * @param executors executors
     * @param messages  messages
     * @param formats   formats
     * @param toasts    toasts
     * @param links     links
     */
    public UpdateViewModel(final SessionModel session, final LauncherBackend backend, final UiExecutors executors, final Messages messages,
                           final Formats formats, final ToastModel toasts, final LauncherLinks links) {
        this.session = Objects.requireNonNull(session, "session");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.formats = Objects.requireNonNull(formats, "formats");
        this.errors = new ErrorMessages(messages, formats);
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.links = Objects.requireNonNull(links, "links");
        bannerVisible = Bindings.createBooleanBinding(() -> !dismissed.get() && (launcherUpdate.get() != null || clientUpdate.get() != null),
            dismissed, launcherUpdate, clientUpdate);
        bannerText = Bindings.createStringBinding(this::computeBannerText, launcherUpdate, clientUpdate);
        // PLAY, "Use with Minecraft Launcher", a client update or a rollback changed what is installed: compare again so
        // the card never shows a stale state (for example "Not installed" next to an installed client).
        session.installedClientProperty().addListener((obs, old, now) -> {
            if (publishingInstalled) {
                return;
            }
            if (checking.get()) {
                // The running check read the installed state before this change: its result is outdated.
                installedChangedDuringCheck = true;
            } else if (checkedOnce && backend.updatesConfigured()) {
                check(false);
            }
        });
    }

    // ---------------------------------------------------------------- properties

    /** @return launcher update or null */
    public ReadOnlyObjectProperty<UpdateInfo> launcherUpdateProperty() {
        return launcherUpdate;
    }

    /** @return client update or null */
    public ReadOnlyObjectProperty<UpdateInfo> clientUpdateProperty() {
        return clientUpdate;
    }

    /** @return client availability */
    public ReadOnlyObjectProperty<ClientAvailability> clientAvailabilityProperty() {
        return clientAvailability;
    }

    /** @return latest client version from the manifest (empty when unknown) */
    public ReadOnlyStringProperty latestClientVersionProperty() {
        return latestClientVersion;
    }

    /** @return whether the latest client release has a download (false: announced only, or unknown) */
    public ReadOnlyBooleanProperty latestClientDownloadableProperty() {
        return latestClientDownloadable;
    }

    /** @return whether a check runs */
    public ReadOnlyBooleanProperty checkingProperty() {
        return checking;
    }

    /** @return whether a download/install runs */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy;
    }

    /** @return download progress (negative = indeterminate) */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress;
    }

    /** @return download progress text */
    public ReadOnlyStringProperty progressTextProperty() {
        return progressText;
    }

    /** @return last check error (empty when none) */
    public ReadOnlyStringProperty checkErrorProperty() {
        return checkError;
    }

    /** @return whether the banner is shown */
    public BooleanBinding bannerVisibleProperty() {
        return bannerVisible;
    }

    /** @return banner headline */
    public StringBinding bannerTextProperty() {
        return bannerText;
    }

    /** @return whether a releases URL is configured */
    public boolean configured() {
        return backend.updatesConfigured();
    }

    /** @return whether a check has completed at least once */
    public boolean checkedOnce() {
        return checkedOnce;
    }

    // ---------------------------------------------------------------- actions

    /**
     * Checks both manifests in the background.
     *
     * <p>"Up to date" is only claimed when both release manifests were found. When a manifest is missing (HTTP 404)
     * or lists no download, an interactive check says that no release is published instead.</p>
     *
     * <p>The client is compared with the installed state read from disk in the same background step
     * ({@link LauncherBackend#installedClient()}); that state is published to the session, so the Home card shows
     * exactly what the check used. Without an installed client no update is offered anywhere (card, banner, dialog):
     * the availability is {@link ClientAvailability#NOT_INSTALLED} and the latest release is only shown.</p>
     *
     * @param interactive whether the user asked (shows an "up to date" or "not published" toast and errors)
     */
    public void check(final boolean interactive) {
        if (checking.get()) {
            return;
        }
        if (!backend.updatesConfigured()) {
            clientAvailability.set(ClientAvailability.NOT_CONFIGURED);
            launcherUpdate.set(null);
            clientUpdate.set(null);
            checkedOnce = true;
            return;
        }
        checking.set(true);
        checkError.set("");
        Async.run(executors, () -> {
            Optional<UpdateInfo> launcher = Optional.empty();
            NotPublishedException launcherNotPublished = null;
            try {
                launcher = backend.checkLauncherUpdate();
            } catch (NotPublishedException e) {
                launcherNotPublished = e;
            }
            final Optional<InstalledClient> installed = backend.installedClient();
            Optional<UpdateInfo> client = Optional.empty();
            ClientAvailability availability;
            String latest = "";
            boolean downloadable = false;
            NotPublishedException clientNotPublished = null;
            try {
                final UpdateService.ClientCheck result = backend.checkClient(installed);
                latest = result.latest().toString();
                downloadable = result.downloadable();
                if (installed.isEmpty()) {
                    availability = ClientAvailability.NOT_INSTALLED;
                } else if (result.update().isPresent()) {
                    client = result.update();
                    availability = client.get().isDownloadable() ? ClientAvailability.UPDATE_AVAILABLE : ClientAvailability.ANNOUNCED;
                } else {
                    availability = ClientAvailability.UP_TO_DATE;
                }
            } catch (NotPublishedException e) {
                availability = ClientAvailability.NOT_PUBLISHED;
                clientNotPublished = e;
            }
            return new Result(launcher, installed, client, availability, latest, downloadable, launcherNotPublished, clientNotPublished);
        }, result -> {
            checking.set(false);
            checkedOnce = true;
            if (installedChangedDuringCheck) {
                // Never publish an installed state older than the session's: compare again with the current one.
                installedChangedDuringCheck = false;
                check(interactive);
                return;
            }
            publishingInstalled = true;
            try {
                session.setInstalledClient(result.installed());
            } finally {
                publishingInstalled = false;
            }
            launcherUpdate.set(result.launcher().orElse(null));
            // An update only exists for an installed client (also when it is announced without a download yet).
            clientUpdate.set(result.installed().isPresent() ? result.client().orElse(null) : null);
            clientAvailability.set(result.availability());
            latestClientVersion.set(result.latest());
            latestClientDownloadable.set(result.downloadable());
            if (result.launcher().isPresent() || clientUpdate.get() != null) {
                dismissed.set(false);
            }
            if (interactive && result.launcher().isEmpty() && result.client().isEmpty()) {
                final List<NotPublishedException> notPublished = result.notPublished();
                if (notPublished.isEmpty()) {
                    toasts.success(messages.get("update.upToDate.title"), result.installed().isPresent()
                        ? messages.format("update.upToDate.message", LauncherVersion.VERSION)
                        : messages.format("update.upToDate.notInstalled.message", LauncherVersion.VERSION, result.latest()));
                } else {
                    // Without a manifest there is nothing to compare with: never claim "up to date" then.
                    toasts.info(messages.get("update.notPublished.title"),
                        String.join("\n", notPublished.stream().map(errors::describe).distinct().toList()));
                }
            }
        }, error -> {
            checking.set(false);
            checkedOnce = true;
            installedChangedDuringCheck = false;
            clientAvailability.set(ClientAvailability.FAILED);
            final String text = errors.describe(error);
            checkError.set(text);
            if (interactive) {
                toasts.error(messages.get("update.toast.checkFailed.title"), text);
            }
        });
    }

    /** Hides the banner until the next check finds something. */
    public void dismissBanner() {
        dismissed.set(true);
    }

    /**
     * Downloads the client update, verifies it and activates it (the previous version is kept for rollback).
     *
     * @param onDone runs on the UI thread after success
     */
    public void installClientUpdate(final Runnable onDone) {
        final UpdateInfo update = clientUpdate.get();
        if (update == null || busy.get() || !update.isDownloadable() || session.installedClient().isEmpty()) {
            return;
        }
        startBusy(update);
        Async.run(executors, () -> backend.installClientUpdate(update, progressListener(), new CancellationToken()), jar -> {
            endBusy();
            clientUpdate.set(null);
            clientAvailability.set(ClientAvailability.UP_TO_DATE);
            toasts.success(messages.format("update.toast.client.installed.title", update.latestVersion().toString()),
                messages.get("update.toast.client.installed.message"));
            session.refreshInstance();
            onDone.run();
        }, error -> {
            endBusy();
            toasts.error(messages.get("update.toast.failed.title"), errors.describe(error));
        });
    }

    /**
     * Downloads and verifies the launcher installer. The verified path is handed to {@code onVerified}; the view
     * asks the user before opening it.
     *
     * @param onVerified receives the verified installer on the UI thread
     */
    public void downloadLauncherUpdate(final Consumer<Path> onVerified) {
        final UpdateInfo update = launcherUpdate.get();
        if (update == null || busy.get() || !update.isDownloadable()) {
            return;
        }
        startBusy(update);
        Async.run(executors, () -> {
            backend.downloadUpdate(update, progressListener(), new CancellationToken());
            executors.onUi(() -> progressText.set(messages.get("update.dialog.verifying")));
            return backend.prepareInstaller(update);
        }, installer -> {
            endBusy();
            toasts.success(messages.get("update.toast.downloaded.title"), messages.format("update.toast.downloaded.message",
                installer.getFileName().toString()));
            onVerified.accept(installer);
        }, error -> {
            endBusy();
            toasts.error(messages.get("update.toast.failed.title"), errors.describe(error));
        });
    }

    /**
     * Fetches the release notes of an update as markdown text.
     *
     * @param update    update
     * @param onLoaded  receives the markdown (UI thread)
     * @param onFailure receives failures (UI thread)
     */
    public void loadChangelog(final UpdateInfo update, final Consumer<String> onLoaded, final Consumer<Throwable> onFailure) {
        final String reference = update.changelog();
        final Optional<URI> raw = links.changelogRaw(reference);
        if (raw.isEmpty()) {
            if (reference != null && !reference.isBlank() && (reference.contains("\n") || !reference.endsWith(".md"))) {
                onLoaded.accept(reference);
            } else {
                onFailure.accept(new java.io.IOException("No release notes reference"));
            }
            return;
        }
        Async.run(executors, () -> backend.fetchText(raw.get()), onLoaded, onFailure);
    }

    /**
     * @param update update
     * @return browser URL of the release notes when resolvable
     */
    public Optional<URI> changelogPage(final UpdateInfo update) {
        return links.changelogPage(update.changelog());
    }

    /**
     * Where to get a launcher update the platform has no self-update file for: the release page from the manifest,
     * else the project's releases page.
     *
     * @param update update
     * @return browser URL
     */
    public Optional<URI> releasePage(final UpdateInfo update) {
        return update.releasePageUri().or(links::releases);
    }

    /**
     * What to do with a verified Windows portable zip. Its top level is a {@value LauncherPackaging#PORTABLE_FOLDER}
     * folder, so it is extracted into the folder that <em>contains</em> the portable folder: extracting it into the
     * portable folder itself would nest a second {@value LauncherPackaging#PORTABLE_FOLDER} folder inside it and leave
     * the old launcher unchanged. A renamed portable folder (or an unknown location) cannot be replaced that way, so
     * the user copies the contents of the zip's folder into it instead. The launcher never unpacks or runs the zip.
     *
     * @param zipName file name of the verified zip
     * @return localised instructions
     */
    public String portableUpdateInstructions(final String zipName) {
        final Path appDirectory = backend.packaging().appDirectory().orElse(null);
        final Path parent = appDirectory == null ? null : appDirectory.getParent();
        final Path folderName = appDirectory == null ? null : appDirectory.getFileName();
        if (parent != null && folderName != null && LauncherPackaging.PORTABLE_FOLDER.equalsIgnoreCase(folderName.toString())) {
            return messages.format("update.confirm.portable.text", zipName, appDirectory.toString(), parent.toString());
        }
        final String folder = appDirectory == null ? messages.get("update.confirm.portable.folderUnknown") : appDirectory.toString();
        return messages.format("update.confirm.portable.copy.text", zipName, folder);
    }

    /**
     * @param update update
     * @return localised "file · size" line
     */
    public String describeFile(final UpdateInfo update) {
        final ReleaseManifest.ReleaseFile file = update.file();
        return messages.format("update.dialog.file", file.name(), formats.bytes(file.size()));
    }

    // ---------------------------------------------------------------- internals

    /**
     * @param launcher             launcher update
     * @param installed            installed client the check compared with
     * @param client               client update
     * @param availability         client availability
     * @param latest               latest client version
     * @param downloadable         whether the latest client release has a download
     * @param launcherNotPublished why the launcher manifest gave no answer (null when it did)
     * @param clientNotPublished   why the client manifest gave no answer (null when it did)
     */
    private record Result(Optional<UpdateInfo> launcher, Optional<InstalledClient> installed, Optional<UpdateInfo> client,
                          ClientAvailability availability, String latest, boolean downloadable,
                          NotPublishedException launcherNotPublished, NotPublishedException clientNotPublished) {

        /** @return the checks that found no published release, launcher first */
        List<NotPublishedException> notPublished() {
            return Stream.of(launcherNotPublished, clientNotPublished).filter(Objects::nonNull).toList();
        }
    }

    private void startBusy(final UpdateInfo update) {
        busy.set(true);
        progress.set(-1);
        progressText.set(messages.format("update.dialog.downloading", update.file().name()));
    }

    private void endBusy() {
        busy.set(false);
        progress.set(-1);
        progressText.set("");
    }

    private DownloadProgressListener progressListener() {
        return new DownloadProgressListener() {
            @Override
            public void onProgress(final DownloadRequest request, final long done, final long total) {
                executors.onUi(() -> {
                    progress.set(total > 0 ? done / (double) total : -1);
                    progressText.set(messages.format("update.dialog.downloading", request.description()) + " "
                        + (total > 0 ? formats.bytes(done) + " / " + formats.bytes(total) : formats.bytes(done)));
                });
            }
        };
    }

    private String computeBannerText() {
        final UpdateInfo l = launcherUpdate.get();
        final UpdateInfo c = clientUpdate.get();
        if (l != null && c != null) {
            return messages.format("update.banner.both", l.latestVersion().toString(), c.latestVersion().toString());
        }
        if (l != null) {
            return messages.format("update.banner.launcher", l.latestVersion().toString());
        }
        if (c != null && session.installedClient().isPresent()) {
            return messages.format("update.banner.client", c.latestVersion().toString());
        }
        return "";
    }
}
