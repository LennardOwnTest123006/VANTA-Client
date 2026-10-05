package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.install.InstalledClient;
import dev.vanta.launcher.core.install.OfficialProfileService;
import dev.vanta.launcher.core.install.VantaClientService;
import dev.vanta.launcher.core.java.JavaInstall;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.ui.Messages;
import dev.vanta.launcher.ui.backend.LauncherBackend;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Versions page model: the component table, the SHA-256 of the active client jar and the kept versions.
 */
public final class VersionsViewModel {

    /** Status of a component row. */
    public enum RowStatus {
        /** Present on disk. */
        INSTALLED,
        /** Not installed yet. */
        NOT_INSTALLED,
        /** Detected on the machine (Java). */
        DETECTED,
        /** Required but absent (Java). */
        MISSING
    }

    /**
     * One row of the component table.
     *
     * @param component localised component name
     * @param version   version text
     * @param status    status
     * @param detail    secondary text
     */
    public record Row(String component, String version, RowStatus status, String detail) {
    }

    /**
     * A kept client version.
     *
     * @param version     version
     * @param installedAt kept since
     * @param available   whether the jar exists
     * @param active      whether it is the active version
     * @param releaseDate release date from its manifest
     * @param localCopy   whether it is a copy of a jar that was in {@code mods/} (for example put there by hand), kept
     *                    before an update replaced it, not a downloaded release
     *                    ({@link VantaClientService.KeptVersion#isLocalCopy()})
     */
    public record Kept(String version, Instant installedAt, boolean available, boolean active, String releaseDate, boolean localCopy) {
    }

    private final SessionModel session;
    private final LauncherBackend backend;
    private final UiExecutors executors;
    private final Messages messages;
    private final Formats formats;
    private final ErrorMessages errors;
    private final ToastModel toasts;

    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final ObservableList<Kept> kept = FXCollections.observableArrayList();
    private final StringProperty sha256 = new SimpleStringProperty("");
    private final StringProperty jarName = new SimpleStringProperty("");
    private final BooleanProperty computingSha = new SimpleBooleanProperty(false);
    private final BooleanProperty rollingBack = new SimpleBooleanProperty(false);
    private final StringBinding emptyTitle;
    private final StringBinding emptyText;

    /**
     * @param session   session
     * @param backend   backend
     * @param executors executors
     * @param messages  messages
     * @param formats   formats
     * @param toasts    toasts
     */
    public VersionsViewModel(final SessionModel session, final LauncherBackend backend, final UiExecutors executors, final Messages messages,
                             final Formats formats, final ToastModel toasts) {
        this.session = Objects.requireNonNull(session, "session");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.formats = Objects.requireNonNull(formats, "formats");
        this.errors = new ErrorMessages(messages, formats);
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        session.instanceProperty().addListener((obs, old, now) -> refresh());
        session.installedClientProperty().addListener((obs, old, now) -> refresh());
        session.javaProperty().addListener((obs, old, now) -> rebuildRows());
        emptyTitle = Bindings.createStringBinding(() -> officialOnly() ? messages.get("versions.empty.officialDone.title")
            : messages.get("versions.empty.title"), session.instanceProperty(), session.installedClientProperty());
        emptyText = Bindings.createStringBinding(this::computeEmptyText, session.instanceProperty(), session.installedClientProperty(),
            session.signInConfiguredProperty(), session.accountProperty());
        rebuildRows();
    }

    /** @return title of the card shown while no instance is installed */
    public StringBinding emptyTitleProperty() {
        return emptyTitle;
    }

    /**
     * What the card says while no instance is installed: press PLAY when PLAY can work here (an account is present or
     * Microsoft sign-in is configured, {@link SessionModel#playPossible()}, the same rule as the Home client card);
     * otherwise PLAY can never be enabled, so it points to "Use with Minecraft Launcher"; after that was used (the
     * client jar is in {@code mods/} but there is no {@code instance.json}) it says where to start the game.
     *
     * @return text of the card
     */
    public StringBinding emptyTextProperty() {
        return emptyText;
    }

    /** @return whether only "Use with Minecraft Launcher" installed something (client jar, no instance.json) */
    private boolean officialOnly() {
        return session.instance().isEmpty() && session.installedClient().isPresent();
    }

    private String computeEmptyText() {
        if (officialOnly()) {
            final String client = session.installedClient().map(InstalledClient::version).filter(v -> !v.isEmpty())
                .map(v -> VantaClientService.DEV_VERSION.equals(v) ? messages.get("client.card.dev") : v).orElse(messages.get("common.unknown"));
            return messages.format("versions.empty.officialDone.text", LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER, client,
                OfficialProfileService.profileName(LauncherVersion.MINECRAFT));
        }
        return session.playPossible()
            ? messages.format("versions.empty.text", LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER)
            : messages.format("versions.empty.text.official", LauncherVersion.MINECRAFT, LauncherVersion.FABRIC_LOADER);
    }

    /** @return component rows */
    public ObservableList<Row> rows() {
        return rows;
    }

    /** @return kept client versions, newest first */
    public ObservableList<Kept> kept() {
        return kept;
    }

    /** @return SHA-256 of the active jar (empty when none) */
    public ReadOnlyStringProperty sha256Property() {
        return sha256;
    }

    /** @return file name of the active jar */
    public ReadOnlyStringProperty jarNameProperty() {
        return jarName;
    }

    /** @return whether the digest is being computed */
    public ReadOnlyBooleanProperty computingShaProperty() {
        return computingSha;
    }

    /** @return whether a rollback runs */
    public ReadOnlyBooleanProperty rollingBackProperty() {
        return rollingBack;
    }

    /** @return whether anything is installed */
    public boolean hasInstance() {
        return session.instance().isPresent();
    }

    /** Reloads the digest and kept versions in the background and rebuilds the rows. */
    public void refresh() {
        rebuildRows();
        computingSha.set(true);
        Async.run(executors, () -> {
            final Optional<Path> jar = backend.activeClientJar();
            final String digest = jar.isPresent() ? backend.sha256(jar.get()) : "";
            final List<VantaClientService.KeptVersion> versions = backend.keptClientVersions();
            return new Loaded(jar.map(p -> p.getFileName().toString()).orElse(""), digest, versions);
        }, loaded -> {
            computingSha.set(false);
            jarName.set(loaded.jarName());
            sha256.set(loaded.sha256());
            final String active = session.installedClient().map(InstalledClient::version).orElse("");
            final List<Kept> list = new ArrayList<>();
            for (VantaClientService.KeptVersion k : loaded.kept()) {
                list.add(new Kept(k.version(), k.installedAt(), k.isAvailable(), k.version().equals(active),
                    k.manifest() == null || k.manifest().releaseDate() == null ? "" : k.manifest().releaseDate(), k.isLocalCopy()));
            }
            kept.setAll(list);
        }, error -> {
            computingSha.set(false);
            toasts.error(messages.get("home.toast.error.title"), errors.describe(error));
        });
    }

    /**
     * Activates a kept version.
     *
     * @param version version
     */
    public void rollback(final String version) {
        if (rollingBack.get()) {
            return;
        }
        rollingBack.set(true);
        Async.run(executors, () -> backend.rollbackClient(version), jar -> {
            rollingBack.set(false);
            toasts.success(messages.format("versions.rollback.toast.title", version), messages.get("versions.rollback.toast.message"));
            session.refreshInstance();
            refresh();
        }, error -> {
            rollingBack.set(false);
            toasts.error(messages.get("versions.rollback.failed.title"), errors.describe(error));
        });
    }

    private record Loaded(String jarName, String sha256, List<VantaClientService.KeptVersion> kept) {
    }

    private void rebuildRows() {
        final Optional<InstanceInfo> instance = session.instance();
        final Optional<JavaInstall> java = session.java();
        final List<Row> list = new ArrayList<>();
        final String installedAt = instance.map(i -> messages.format("versions.detail.installedAt", formats.isoDateTime(i.installedAt()))).orElse("");
        list.add(component("versions.component.minecraft", instance.map(InstanceInfo::minecraftVersion).orElse(LauncherVersion.MINECRAFT),
            instance.isPresent(), installedAt));
        list.add(component("versions.component.fabricLoader", instance.map(InstanceInfo::fabricLoaderVersion).orElse(LauncherVersion.FABRIC_LOADER),
            instance.isPresent(), instance.map(InstanceInfo::fabricProfileId).orElse("")));
        list.add(component("versions.component.fabricApi", instance.map(InstanceInfo::fabricApiVersion).orElse(LauncherVersion.FABRIC_API),
            instance.isPresent(), ""));
        // The same installed-client source as the Home card and the update check (instance.json / the jar in mods/).
        final Optional<InstalledClient> client = session.installedClient();
        final String clientVersion = client.map(InstalledClient::version).filter(v -> !v.isEmpty()).orElse("—");
        list.add(new Row(messages.get("versions.component.client"), VantaClientService.DEV_VERSION.equals(clientVersion)
            ? messages.get("client.card.dev") : clientVersion, client.isPresent() ? RowStatus.INSTALLED : RowStatus.NOT_INSTALLED,
            client.flatMap(InstalledClient::jar).map(p -> p.getFileName().toString())
                .orElse(instance.map(InstanceInfo::vantaClientJar).orElse(""))));
        list.add(new Row(messages.get("versions.component.java"), java.map(JavaInstall::version).orElse(Integer.toString(LauncherVersion.JAVA_MAJOR)),
            java.isPresent() ? RowStatus.DETECTED : RowStatus.MISSING,
            java.map(j -> messages.format("versions.detail.javaPath", j.home().toString()))
                .orElse(messages.format("versions.detail.javaMissing", Integer.toString(LauncherVersion.JAVA_MAJOR)))));
        rows.setAll(list);
    }

    private Row component(final String key, final String version, final boolean installed, final String detail) {
        return new Row(messages.get(key), version, installed ? RowStatus.INSTALLED : RowStatus.NOT_INSTALLED, detail);
    }
}
