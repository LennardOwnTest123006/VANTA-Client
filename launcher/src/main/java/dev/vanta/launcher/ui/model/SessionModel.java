package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.java.JavaInstall;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.ui.backend.LauncherBackend;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Observable state shared by every page: settings, the active account, detected Java runtimes and the installed
 * instance. Values are read from the {@link LauncherBackend} on the background executor and published on the UI
 * thread; pages bind to the properties and never query the backend themselves.
 */
public final class SessionModel {

    private final LauncherBackend backend;
    private final UiExecutors executors;
    private final Consumer<Throwable> errorSink;

    private final ObjectProperty<LauncherSettings> settings = new SimpleObjectProperty<>();
    private final ObjectProperty<Account> account = new SimpleObjectProperty<>();
    private final ObservableList<JavaInstall> javaInstalls = FXCollections.observableArrayList();
    private final ObjectProperty<JavaInstall> java = new SimpleObjectProperty<>();
    private final ObjectProperty<InstanceInfo> instance = new SimpleObjectProperty<>();
    private final BooleanProperty detectingJava = new SimpleBooleanProperty(false);
    private final BooleanProperty signInConfigured = new SimpleBooleanProperty(false);
    private final BooleanProperty offlineAllowed = new SimpleBooleanProperty(false);
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);

    /**
     * @param backend   backend
     * @param executors executors
     * @param errorSink receives background failures (UI thread)
     */
    public SessionModel(final LauncherBackend backend, final UiExecutors executors, final Consumer<Throwable> errorSink) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.errorSink = Objects.requireNonNull(errorSink, "errorSink");
        settings.set(backend.settings());
    }

    // ---------------------------------------------------------------- properties

    /** @return current settings (never null after construction) */
    public ReadOnlyObjectProperty<LauncherSettings> settingsProperty() {
        return settings;
    }

    /** @return current settings */
    public LauncherSettings settings() {
        return settings.get();
    }

    /** @return active account or null */
    public ReadOnlyObjectProperty<Account> accountProperty() {
        return account;
    }

    /** @return active account */
    public Optional<Account> account() {
        return Optional.ofNullable(account.get());
    }

    /** @return detected runtimes, newest first */
    public ObservableList<JavaInstall> javaInstalls() {
        return javaInstalls;
    }

    /** @return the runtime that will be used, or null */
    public ReadOnlyObjectProperty<JavaInstall> javaProperty() {
        return java;
    }

    /** @return the runtime that will be used */
    public Optional<JavaInstall> java() {
        return Optional.ofNullable(java.get());
    }

    /** @return installed instance or null */
    public ReadOnlyObjectProperty<InstanceInfo> instanceProperty() {
        return instance;
    }

    /** @return installed instance */
    public Optional<InstanceInfo> instance() {
        return Optional.ofNullable(instance.get());
    }

    /** @return whether Java detection is running */
    public ReadOnlyBooleanProperty detectingJavaProperty() {
        return detectingJava;
    }

    /** @return whether a Microsoft client id is configured */
    public ReadOnlyBooleanProperty signInConfiguredProperty() {
        return signInConfigured;
    }

    /** @return whether the core policy allows an offline session */
    public ReadOnlyBooleanProperty offlineAllowedProperty() {
        return offlineAllowed;
    }

    /** @return whether the first full refresh completed */
    public ReadOnlyBooleanProperty loadedProperty() {
        return loaded;
    }

    // ---------------------------------------------------------------- refresh

    /**
     * Snapshot read on the background thread.
     *
     * @param settings      settings
     * @param account       active account
     * @param javaInstalls  detected runtimes
     * @param java          picked runtime
     * @param instance      installed instance
     * @param signInEnabled whether a client id is configured
     * @param offline       whether offline sessions are allowed
     */
    record Snapshot(LauncherSettings settings, Optional<Account> account, List<JavaInstall> javaInstalls, Optional<JavaInstall> java,
                    Optional<InstanceInfo> instance, boolean signInEnabled, boolean offline) {
    }

    /** Reloads everything (settings, account, Java, instance) in the background. */
    public void refreshAll() {
        detectingJava.set(true);
        Async.run(executors, () -> {
            final LauncherSettings s = backend.settings();
            final Optional<Account> a = backend.activeAccount();
            final List<JavaInstall> detected = backend.detectJava();
            final Optional<JavaInstall> picked = backend.pickJava(detected);
            final Optional<InstanceInfo> i = backend.loadInstance();
            return new Snapshot(s, a, detected, picked, i, backend.signInConfigured(), backend.offlineSessionAllowed());
        }, snap -> {
            settings.set(snap.settings());
            account.set(snap.account().orElse(null));
            javaInstalls.setAll(snap.javaInstalls());
            java.set(snap.java().orElse(null));
            instance.set(snap.instance().orElse(null));
            signInConfigured.set(snap.signInEnabled());
            offlineAllowed.set(snap.offline());
            detectingJava.set(false);
            loaded.set(true);
        }, error -> {
            detectingJava.set(false);
            loaded.set(true);
            errorSink.accept(error);
        });
    }

    /** Re-detects Java runtimes in the background. */
    public void refreshJava() {
        detectingJava.set(true);
        Async.run(executors, () -> {
            final List<JavaInstall> detected = backend.detectJava();
            return new Snapshot(backend.settings(), Optional.empty(), detected, backend.pickJava(detected), Optional.empty(), false, false);
        }, snap -> {
            javaInstalls.setAll(snap.javaInstalls());
            java.set(snap.java().orElse(null));
            detectingJava.set(false);
        }, error -> {
            detectingJava.set(false);
            errorSink.accept(error);
        });
    }

    /** Reloads the instance metadata in the background. */
    public void refreshInstance() {
        Async.run(executors, backend::loadInstance, i -> instance.set(i.orElse(null)), errorSink);
    }

    /** Reloads settings-derived flags after a settings change. */
    public void refreshSettings() {
        Async.run(executors, () -> new Snapshot(backend.settings(), backend.activeAccount(), List.of(), Optional.empty(),
            Optional.empty(), backend.signInConfigured(), backend.offlineSessionAllowed()), snap -> {
            settings.set(snap.settings());
            account.set(snap.account().orElse(null));
            signInConfigured.set(snap.signInEnabled());
            offlineAllowed.set(snap.offline());
        }, errorSink);
        refreshJava();
    }

    // ---------------------------------------------------------------- mutations (UI thread)

    /**
     * Publishes a freshly signed-in or refreshed account.
     *
     * @param signedIn account
     */
    public void setAccount(final Account signedIn) {
        account.set(signedIn);
        Async.run(executors, backend::offlineSessionAllowed, offlineAllowed::set, errorSink);
    }

    /**
     * Publishes an instance written by an install.
     *
     * @param installed instance
     */
    public void setInstance(final InstanceInfo installed) {
        instance.set(installed);
    }

    /**
     * Publishes a runtime installed by the launcher and re-detects.
     *
     * @param installed runtime
     */
    public void setJava(final JavaInstall installed) {
        java.set(installed);
        if (javaInstalls.stream().noneMatch(j -> j.home().equals(installed.home()))) {
            javaInstalls.add(0, installed);
        }
    }

    /**
     * Signs the active account out (removes it from the store) in the background.
     *
     * @param onDone runs on the UI thread afterwards
     */
    public void signOut(final Runnable onDone) {
        final Account current = account.get();
        if (current == null) {
            onDone.run();
            return;
        }
        Async.run(executors, () -> {
            backend.removeAccount(current.uuid());
            return backend.activeAccount();
        }, next -> {
            account.set(next.orElse(null));
            Async.run(executors, backend::offlineSessionAllowed, offlineAllowed::set, errorSink);
            onDone.run();
        }, errorSink);
    }

    /**
     * Makes a stored account active.
     *
     * @param uuid account uuid
     */
    public void switchAccount(final String uuid) {
        Async.run(executors, () -> {
            backend.setActiveAccount(uuid);
            return backend.activeAccount();
        }, next -> account.set(next.orElse(null)), errorSink);
    }

    /**
     * Persists a Java override and republishes the runtime.
     *
     * @param install runtime to use
     * @param onDone  runs on the UI thread afterwards
     */
    public void selectJava(final JavaInstall install, final Runnable onDone) {
        Async.run(executors, () -> {
            final LauncherSettings updated = backend.settings().withJavaPath(install.home().toString());
            backend.saveSettings(updated);
            return updated;
        }, updated -> {
            settings.set(updated);
            java.set(install);
            onDone.run();
        }, errorSink);
    }

    /**
     * Persists settings and republishes them.
     *
     * @param updated   settings
     * @param onDone    runs on the UI thread afterwards
     * @param onFailure receives failures
     */
    public void saveSettings(final LauncherSettings updated, final Runnable onDone, final Consumer<Throwable> onFailure) {
        Async.run(executors, () -> {
            backend.saveSettings(updated);
            return updated;
        }, saved -> {
            settings.set(saved);
            onDone.run();
            refreshSettings();
        }, onFailure);
    }

    /**
     * Loads all stored accounts (for the account switcher).
     *
     * @param onLoaded receives the list on the UI thread
     */
    public void loadAccounts(final Consumer<List<Account>> onLoaded) {
        Async.run(executors, backend::accounts, onLoaded, errorSink);
    }

    /** @return the backend (for view models) */
    LauncherBackend backend() {
        return backend;
    }

    /** @return executors */
    UiExecutors executors() {
        return executors;
    }

    /**
     * Convenience for tests: synchronously reads the instance.
     *
     * @return instance
     * @throws IOException when unreadable
     */
    Optional<InstanceInfo> readInstance() throws IOException {
        return backend.loadInstance();
    }
}
