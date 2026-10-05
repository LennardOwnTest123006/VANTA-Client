package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.modrinth.ContentChangeRefusedException;
import dev.vanta.launcher.core.modrinth.ContentType;
import dev.vanta.launcher.core.modrinth.ModrinthModels;
import dev.vanta.launcher.core.modrinth.ModrinthService;
import dev.vanta.launcher.core.net.CancellationToken;
import dev.vanta.launcher.core.net.DownloadProgressListener;
import dev.vanta.launcher.core.net.DownloadRequest;
import dev.vanta.launcher.ui.Messages;
import dev.vanta.launcher.ui.backend.LauncherBackend;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * State behind the Mods page: Modrinth search (Mods, Shaders, Resource packs) and the content installed in the VANTA
 * instance. Everything that touches the network or the disk runs on the background executor; lists change on the UI
 * thread only. Because "Use with Minecraft Launcher" points the official profile at the same instance, everything
 * installed here also loads there.
 */
public final class ModsViewModel {

    private final LauncherBackend backend;
    private final UiExecutors executors;
    private final Messages messages;
    private final ToastModel toasts;
    private final LogBuffer launcherLog;
    private final ErrorMessages errors;

    private final ObjectProperty<ContentType> tab = new SimpleObjectProperty<>(ContentType.MOD);
    private final StringProperty query = new SimpleStringProperty("");
    private final ObservableList<ModrinthModels.SearchHit> results = FXCollections.observableArrayList();
    private final IntegerProperty totalHits = new SimpleIntegerProperty(0);
    private final BooleanProperty searching = new SimpleBooleanProperty(false);
    private final BooleanProperty searched = new SimpleBooleanProperty(false);
    private final StringProperty searchError = new SimpleStringProperty("");
    private final ObservableList<ModrinthService.InstalledContent> installed = FXCollections.observableArrayList();
    private final ObservableSet<String> installing = FXCollections.observableSet();
    private final BooleanProperty updating = new SimpleBooleanProperty(false);
    private final StringProperty statusText = new SimpleStringProperty("");
    private final BooleanProperty changedSinceStart = new SimpleBooleanProperty(false);
    private int searchGeneration;

    /**
     * @param backend     backend
     * @param executors   executors
     * @param messages    messages
     * @param formats     formats
     * @param toasts      notifications
     * @param launcherLog launcher log
     */
    public ModsViewModel(final LauncherBackend backend, final UiExecutors executors, final Messages messages, final Formats formats,
                         final ToastModel toasts, final LogBuffer launcherLog) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.launcherLog = Objects.requireNonNull(launcherLog, "launcherLog");
        this.errors = new ErrorMessages(messages, Objects.requireNonNull(formats, "formats"));
        tab.addListener((obs, old, now) -> search());
    }

    // ---------------------------------------------------------------- properties

    /** @return the selected tab */
    public ObjectProperty<ContentType> tabProperty() {
        return tab;
    }

    /** @return the search text */
    public StringProperty queryProperty() {
        return query;
    }

    /** @return search results of the current tab */
    public ObservableList<ModrinthModels.SearchHit> results() {
        return results;
    }

    /** @return number of matching projects */
    public ReadOnlyIntegerProperty totalHitsProperty() {
        return totalHits;
    }

    /** @return whether a search runs */
    public ReadOnlyBooleanProperty searchingProperty() {
        return searching;
    }

    /** @return whether at least one search finished */
    public ReadOnlyBooleanProperty searchedProperty() {
        return searched;
    }

    /** @return why the last search failed ("" when it did not) */
    public ReadOnlyStringProperty searchErrorProperty() {
        return searchError;
    }

    /** @return content in the instance */
    public ObservableList<ModrinthService.InstalledContent> installed() {
        return installed;
    }

    /** @return project ids being installed */
    public ObservableSet<String> installing() {
        return installing;
    }

    /** @return whether "Update all" runs */
    public ReadOnlyBooleanProperty updatingProperty() {
        return updating;
    }

    /** @return progress / status line ("Downloading Sodium …") */
    public ReadOnlyStringProperty statusTextProperty() {
        return statusText;
    }

    /** @return whether content changed while the launcher runs (the game needs a restart to load it) */
    public ReadOnlyBooleanProperty changedSinceStartProperty() {
        return changedSinceStart;
    }

    /** @return whether more results can be loaded */
    public boolean hasMore() {
        return results.size() < totalHits.get();
    }

    // ---------------------------------------------------------------- search

    /** Searches the current tab with the current text (blank: most downloaded first). */
    public void search() {
        load(0);
    }

    /** Loads the next page of results. */
    public void loadMore() {
        if (!searching.get() && hasMore()) {
            load(results.size());
        }
    }

    private void load(final int offset) {
        final int generation = ++searchGeneration;
        final ContentType type = tab.get();
        final String text = query.get() == null ? "" : query.get().trim();
        searching.set(true);
        searchError.set("");
        Async.run(executors, () -> backend.searchModrinth(type, text, offset), page -> {
            if (generation != searchGeneration) {
                return;
            }
            searching.set(false);
            searched.set(true);
            if (offset == 0) {
                results.setAll(page.hits());
            } else {
                results.addAll(page.hits());
            }
            totalHits.set(page.totalHits());
        }, error -> {
            if (generation != searchGeneration) {
                return;
            }
            searching.set(false);
            searched.set(true);
            if (offset == 0) {
                results.clear();
                totalHits.set(0);
            }
            searchError.set(errors.describe(error));
        });
    }

    // ---------------------------------------------------------------- installed content

    /** Reads the installed content again. */
    public void refreshInstalled() {
        Async.run(executors, backend::installedContent, (List<ModrinthService.InstalledContent> items) -> installed.setAll(items),
            error -> toasts.error(messages.get("mods.toast.readFailed.title"), errors.describe(error)));
    }

    /**
     * @param type content type
     * @return installed content of that type
     */
    public List<ModrinthService.InstalledContent> installedOf(final ContentType type) {
        return installed.stream().filter(c -> c.type() == type).toList();
    }

    /**
     * @param hit search result
     * @return whether the launcher installs it itself (Fabric API), so the browser offers no install
     */
    public boolean isIncluded(final ModrinthModels.SearchHit hit) {
        return ModrinthService.isFabricApi(hit.projectId()) || ModrinthService.isFabricApi(hit.slug());
    }

    /**
     * @param hit search result
     * @return whether the project is installed (tracked in modrinth.json)
     */
    public boolean isInstalled(final ModrinthModels.SearchHit hit) {
        return installed.stream().anyMatch(c -> c.tracked() && c.projectId().equals(hit.projectId()));
    }

    /**
     * Installs a project and its required dependencies.
     *
     * @param hit search result
     */
    public void install(final ModrinthModels.SearchHit hit) {
        if (installing.contains(hit.projectId()) || updating.get()) {
            return;
        }
        final ContentType type = ContentType.fromId(hit.projectType()).orElse(tab.get());
        installing.add(hit.projectId());
        statusText.set(messages.format("mods.status.resolving", hit.title()));
        Async.run(executors, () -> backend.installFromModrinth(type, hit.projectId(), progress(), this::log, new CancellationToken()), result -> {
            installing.remove(hit.projectId());
            statusText.set("");
            changedSinceStart.set(true);
            final long extra = result.applied().stream().filter(a -> !a.item().projectId().equals(hit.projectId())).count();
            toasts.success(messages.format("mods.toast.installed.title", hit.title()), extra == 0 ? messages.get("mods.toast.installed.message")
                : messages.format("mods.toast.installed.withDependencies", Long.toString(extra)));
            result.warnings().forEach(w -> launcherLog.append(LogLevel.WARN, w));
            refreshInstalled();
        }, error -> {
            installing.remove(hit.projectId());
            statusText.set("");
            launcherLog.append(LogLevel.ERROR, "Installing " + hit.title() + " failed: " + errors.describe(error));
            toasts.error(messages.format("mods.toast.installFailed.title", hit.title()), errors.describe(error));
        });
    }

    /**
     * @param item    content
     * @param enabled new state
     */
    public void setEnabled(final ModrinthService.InstalledContent item, final boolean enabled) {
        Async.run(executors, () -> backend.setContentEnabled(item, enabled), () -> {
            changedSinceStart.set(true);
            refreshInstalled();
        }, error -> {
            toasts.error(messages.format(enabled ? "mods.toast.enableFailed.title" : "mods.toast.disableFailed.title", item.title()),
                describeRefusal(error));
            refreshInstalled();
        });
    }

    /**
     * @param item content to delete
     */
    public void remove(final ModrinthService.InstalledContent item) {
        Async.run(executors, () -> backend.removeContent(item), () -> {
            changedSinceStart.set(true);
            toasts.info(messages.format("mods.toast.removed.title", item.title()), messages.get("mods.toast.restartHint"));
            refreshInstalled();
        }, error -> toasts.error(messages.format("mods.toast.removeFailed.title", item.title()), describeRefusal(error)));
    }

    /** Updates every Modrinth project in the instance to its newest compatible version. */
    public void updateAll() {
        if (updating.get()) {
            return;
        }
        updating.set(true);
        statusText.set(messages.get("mods.status.checkingUpdates"));
        Async.run(executors, () -> backend.updateAllContent(progress(), this::log, new CancellationToken()), result -> {
            updating.set(false);
            statusText.set("");
            final long changed = result.applied().stream().filter(a -> a.downloaded() || !a.replaced().isEmpty()).count();
            if (changed > 0) {
                changedSinceStart.set(true);
            }
            toasts.success(messages.get("mods.toast.updated.title"), changed == 0 ? messages.get("mods.toast.updated.none")
                : messages.format("mods.toast.updated.message", Long.toString(changed)));
            result.warnings().forEach(w -> launcherLog.append(LogLevel.WARN, w));
            refreshInstalled();
        }, error -> {
            updating.set(false);
            statusText.set("");
            toasts.error(messages.get("mods.toast.updateFailed.title"), errors.describe(error));
        });
    }

    /**
     * @param hit search result
     * @return e.g. {@code by CaffeineMC · 236.8M downloads}
     */
    public String byline(final ModrinthModels.SearchHit hit) {
        final String downloads = compact(hit.downloads());
        return hit.author().isEmpty() ? messages.format("mods.result.downloads", downloads)
            : messages.format("mods.result.byline", hit.author(), downloads);
    }

    /**
     * @param n count
     * @return e.g. {@code 236.8M}, {@code 12.4k}, {@code 950}
     */
    public static String compact(final long n) {
        if (n >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", n / 1_000_000d);
        }
        if (n >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", n / 1_000d);
        }
        return Long.toString(n);
    }

    private String describeRefusal(final Throwable error) {
        if (error instanceof ContentChangeRefusedException refused) {
            return switch (refused.reason()) {
                case REQUIRED_BY -> messages.format("mods.refused.requiredBy", refused.title(), String.join(", ", refused.names()));
                case MANAGED -> messages.format("mods.refused.managed", refused.title());
            };
        }
        return errors.describe(error);
    }

    private void log(final String line) {
        launcherLog.append(LogLevel.INFO, line);
    }

    private DownloadProgressListener progress() {
        return new DownloadProgressListener() {
            @Override
            public void onProgress(final DownloadRequest request, final long done, final long total) {
                if (done == 0 || done == total) {
                    executors.onUi(() -> statusText.set(messages.format("mods.status.downloading", request.description())));
                }
            }

            @Override
            public void onComplete(final DownloadRequest request, final boolean skipped, final long bytes) {
                executors.onUi(() -> statusText.set(messages.format(skipped ? "mods.status.verified" : "mods.status.downloaded",
                    request.description())));
            }
        };
    }
}
