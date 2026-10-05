package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.ui.LauncherLinks;
import dev.vanta.launcher.ui.Messages;
import dev.vanta.launcher.ui.backend.LauncherBackend;
import dev.vanta.launcher.ui.model.Formats;
import dev.vanta.launcher.ui.model.HomeViewModel;
import dev.vanta.launcher.ui.model.LogBuffer;
import dev.vanta.launcher.ui.model.LogsViewModel;
import dev.vanta.launcher.ui.model.NavigationModel;
import dev.vanta.launcher.ui.model.SessionModel;
import dev.vanta.launcher.ui.model.SettingsViewModel;
import dev.vanta.launcher.ui.model.SignInViewModel;
import dev.vanta.launcher.ui.model.ToastModel;
import dev.vanta.launcher.ui.model.UiExecutors;
import dev.vanta.launcher.ui.model.UpdateViewModel;
import dev.vanta.launcher.ui.model.VersionsViewModel;
import dev.vanta.launcher.ui.prefs.UiPreferences;
import dev.vanta.launcher.ui.prefs.UiPreferencesStore;

import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Everything the views share: the backend seam, executors, messages, the view models and the system opener.
 * Built once by {@link dev.vanta.launcher.ui.LauncherApp} (production) or by the screenshot/smoke harness (fakes).
 */
public final class AppContext {

    private final LauncherBackend backend;
    private final UiExecutors executors;
    private final Messages messages;
    private final Formats formats;
    private final LauncherLinks links;
    private final ToastModel toasts;
    private final NavigationModel navigation;
    private final LogBuffer launcherLog;
    private final LogBuffer gameLog;
    private final SessionModel session;
    private final HomeViewModel home;
    private final SignInViewModel signIn;
    private final SettingsViewModel settings;
    private final VersionsViewModel versions;
    private final LogsViewModel logs;
    private final UpdateViewModel updates;
    private final UiPreferencesStore prefsStore;
    private final SystemOpener opener;
    private UiPreferences prefs;

    /**
     * @param backend     backend
     * @param executors   executors
     * @param messages    messages
     * @param links       links
     * @param launcherLog launcher log buffer
     * @param gameLog     game log buffer
     * @param prefsStore  UI preferences store
     * @param browser     opens URLs in the browser
     */
    public AppContext(final LauncherBackend backend, final UiExecutors executors, final Messages messages, final LauncherLinks links,
                      final LogBuffer launcherLog, final LogBuffer gameLog, final UiPreferencesStore prefsStore, final Consumer<String> browser) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.links = Objects.requireNonNull(links, "links");
        this.launcherLog = Objects.requireNonNull(launcherLog, "launcherLog");
        this.gameLog = Objects.requireNonNull(gameLog, "gameLog");
        this.prefsStore = Objects.requireNonNull(prefsStore, "prefsStore");
        this.formats = new Formats(messages, ZoneId.systemDefault());
        this.toasts = new ToastModel();
        this.navigation = new NavigationModel();
        this.prefs = prefsStore.load();
        Motion.reducedProperty().set(prefs.reducedMotion());
        this.opener = new SystemOpener(browser, dev.vanta.launcher.ui.BrowserOpener.system(backend.os(), browser),
            SystemOpener::copyToSystemClipboard, executors, toasts, messages);
        final dev.vanta.launcher.ui.model.ErrorMessages errors = new dev.vanta.launcher.ui.model.ErrorMessages(messages, formats);
        this.session = new SessionModel(backend, executors, error -> toasts.error(messages.get("home.toast.error.title"), errors.describe(error)));
        this.home = new HomeViewModel(session, backend, executors, messages, formats, toasts, launcherLog, gameLog);
        this.signIn = new SignInViewModel(backend, executors, messages, formats);
        this.settings = new SettingsViewModel(backend, executors, messages, formats);
        this.settings.load(backend.settings(), prefs);
        this.versions = new VersionsViewModel(session, backend, executors, messages, formats, toasts);
        this.logs = new LogsViewModel(executors, launcherLog, gameLog);
        this.updates = new UpdateViewModel(session, backend, executors, messages, formats, toasts, links);
    }

    /** @return backend */
    public LauncherBackend backend() {
        return backend;
    }

    /** @return executors */
    public UiExecutors executors() {
        return executors;
    }

    /** @return messages */
    public Messages messages() {
        return messages;
    }

    /** @return formats */
    public Formats formats() {
        return formats;
    }

    /** @return links */
    public LauncherLinks links() {
        return links;
    }

    /** @return toasts */
    public ToastModel toasts() {
        return toasts;
    }

    /** @return navigation */
    public NavigationModel navigation() {
        return navigation;
    }

    /** @return launcher log */
    public LogBuffer launcherLog() {
        return launcherLog;
    }

    /** @return game log */
    public LogBuffer gameLog() {
        return gameLog;
    }

    /** @return session */
    public SessionModel session() {
        return session;
    }

    /** @return home view model */
    public HomeViewModel home() {
        return home;
    }

    /** @return sign-in view model */
    public SignInViewModel signIn() {
        return signIn;
    }

    /** @return settings view model */
    public SettingsViewModel settings() {
        return settings;
    }

    /** @return versions view model */
    public VersionsViewModel versions() {
        return versions;
    }

    /** @return logs view model */
    public LogsViewModel logs() {
        return logs;
    }

    /** @return update view model */
    public UpdateViewModel updates() {
        return updates;
    }

    /** @return system opener */
    public SystemOpener opener() {
        return opener;
    }

    /** @return current UI preferences */
    public UiPreferences prefs() {
        return prefs;
    }

    /**
     * Remembers the current page without touching the disk; {@link #savePrefs} persists it later.
     *
     * @param pageId page id
     */
    public void rememberPage(final String pageId) {
        this.prefs = prefs.withLastPage(pageId);
    }

    /**
     * Stores UI preferences (in memory and on disk).
     *
     * @param updated preferences
     */
    public void savePrefs(final UiPreferences updated) {
        this.prefs = updated;
        Motion.reducedProperty().set(updated.reducedMotion());
        prefsStore.save(updated);
    }

    /**
     * Convenience: short message lookup.
     *
     * @param key key
     * @return text
     */
    public String t(final String key) {
        return messages.get(key);
    }

    /**
     * Convenience: formatted message.
     *
     * @param key  key
     * @param args arguments
     * @return text
     */
    public String t(final String key, final Object... args) {
        return messages.format(key, args);
    }
}
