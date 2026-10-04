package dev.vanta.launcher.ui.model;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.java.JavaInstall;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.settings.Resolution;
import dev.vanta.launcher.ui.Messages;
import dev.vanta.launcher.ui.backend.LauncherBackend;
import dev.vanta.launcher.ui.prefs.UiPreferences;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * Editable copy of {@link LauncherSettings} (plus the UI-only preferences) with validation. {@link #load} fills
 * the properties, {@link #toSettings()} maps them back; {@link #dirtyProperty()} tells the view whether there is
 * something to save.
 */
public final class SettingsViewModel {

    /** Theme id of the high-contrast variant. */
    public static final String THEME_HIGH_CONTRAST = "vanta-dark-contrast";
    /** Upper bound of the memory slider when the machine memory is unknown. */
    public static final int FALLBACK_MAX_MEMORY_MB = 16384;

    /** Java path validation outcome. */
    public enum JavaCheck {
        /** Not checked yet / automatic mode. */
        NONE,
        /** Probe running. */
        CHECKING,
        /** Valid Java 21+. */
        VALID,
        /** Works but too old. */
        TOO_OLD,
        /** Not a Java runtime. */
        INVALID
    }

    private final LauncherBackend backend;
    private final UiExecutors executors;
    private final Messages messages;
    private final Formats formats;

    private final IntegerProperty memoryMb = new SimpleIntegerProperty(LauncherSettings.DEFAULT_MEMORY_MB);
    private final BooleanProperty javaAuto = new SimpleBooleanProperty(true);
    private final StringProperty javaPath = new SimpleStringProperty("");
    private final ObjectProperty<JavaCheck> javaCheck = new SimpleObjectProperty<>(JavaCheck.NONE);
    private final StringProperty javaCheckText = new SimpleStringProperty("");
    private final StringProperty jvmArgs = new SimpleStringProperty("");
    private final BooleanProperty resolutionEnabled = new SimpleBooleanProperty(false);
    private final StringProperty resolutionWidth = new SimpleStringProperty("");
    private final StringProperty resolutionHeight = new SimpleStringProperty("");
    private final BooleanProperty keepLauncherOpen = new SimpleBooleanProperty(false);
    private final StringProperty msClientId = new SimpleStringProperty("");
    private final StringProperty releasesBaseUrl = new SimpleStringProperty("");
    private final BooleanProperty autoUpdateCheck = new SimpleBooleanProperty(true);
    private final BooleanProperty shareOfficialFiles = new SimpleBooleanProperty(true);
    private final BooleanProperty developerMode = new SimpleBooleanProperty(false);
    private final BooleanProperty highContrast = new SimpleBooleanProperty(false);
    private final BooleanProperty reducedMotion = new SimpleBooleanProperty(false);
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final StringProperty errorText = new SimpleStringProperty("");
    private final BooleanBinding valid;

    private LauncherSettings baseline;
    private UiPreferences baselinePrefs = UiPreferences.defaults();
    private boolean loading;

    /**
     * @param backend   backend
     * @param executors executors
     * @param messages  messages
     * @param formats   formats
     */
    public SettingsViewModel(final LauncherBackend backend, final UiExecutors executors, final Messages messages, final Formats formats) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.formats = Objects.requireNonNull(formats, "formats");
        this.baseline = backend.settings();
        valid = Bindings.createBooleanBinding(() -> validationErrors().isEmpty(), memoryMb, javaAuto, javaPath, resolutionEnabled,
            resolutionWidth, resolutionHeight, releasesBaseUrl);
        for (javafx.beans.Observable o : new javafx.beans.Observable[] {memoryMb, javaAuto, javaPath, jvmArgs, resolutionEnabled,
            resolutionWidth, resolutionHeight, keepLauncherOpen, msClientId, releasesBaseUrl, autoUpdateCheck, shareOfficialFiles,
            developerMode, highContrast, reducedMotion}) {
            o.addListener(obs -> markDirty());
        }
        javaAuto.addListener((obs, old, now) -> {
            if (now) {
                javaCheck.set(JavaCheck.NONE);
                javaCheckText.set("");
            }
        });
        load(baseline, baselinePrefs);
    }

    // ---------------------------------------------------------------- properties

    /** @return heap in MiB */
    public IntegerProperty memoryMbProperty() {
        return memoryMb;
    }

    /** @return automatic Java selection */
    public BooleanProperty javaAutoProperty() {
        return javaAuto;
    }

    /** @return manual Java path */
    public StringProperty javaPathProperty() {
        return javaPath;
    }

    /** @return Java validation state */
    public ReadOnlyObjectProperty<JavaCheck> javaCheckProperty() {
        return javaCheck;
    }

    /** @return Java validation text */
    public ReadOnlyStringProperty javaCheckTextProperty() {
        return javaCheckText;
    }

    /** @return extra JVM args, space separated */
    public StringProperty jvmArgsProperty() {
        return jvmArgs;
    }

    /** @return whether a resolution override is set */
    public BooleanProperty resolutionEnabledProperty() {
        return resolutionEnabled;
    }

    /** @return width text */
    public StringProperty resolutionWidthProperty() {
        return resolutionWidth;
    }

    /** @return height text */
    public StringProperty resolutionHeightProperty() {
        return resolutionHeight;
    }

    /** @return keep launcher open */
    public BooleanProperty keepLauncherOpenProperty() {
        return keepLauncherOpen;
    }

    /** @return Microsoft client id */
    public StringProperty msClientIdProperty() {
        return msClientId;
    }

    /** @return releases base URL */
    public StringProperty releasesBaseUrlProperty() {
        return releasesBaseUrl;
    }

    /** @return auto update check */
    public BooleanProperty autoUpdateCheckProperty() {
        return autoUpdateCheck;
    }

    /** @return share official files */
    public BooleanProperty shareOfficialFilesProperty() {
        return shareOfficialFiles;
    }

    /** @return developer mode */
    public BooleanProperty developerModeProperty() {
        return developerMode;
    }

    /** @return high contrast theme */
    public BooleanProperty highContrastProperty() {
        return highContrast;
    }

    /** @return reduced motion (UI preference) */
    public BooleanProperty reducedMotionProperty() {
        return reducedMotion;
    }

    /** @return whether there are unsaved changes */
    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    /** @return whether the current values can be saved */
    public BooleanBinding validProperty() {
        return valid;
    }

    /** @return last save error */
    public ReadOnlyStringProperty errorTextProperty() {
        return errorText;
    }

    /** @return the memory slider maximum (physical RAM or a fallback) */
    public int maxMemoryMb() {
        final OptionalLong total = backend.totalMemoryMb();
        if (total.isEmpty()) {
            return FALLBACK_MAX_MEMORY_MB;
        }
        final long rounded = Math.max(LauncherSettings.MIN_MEMORY_MB * 2L, (total.getAsLong() / 1024L) * 1024L);
        return (int) Math.min(Integer.MAX_VALUE, rounded);
    }

    /** @return localised memory help text with the RAM hint */
    public String memoryHelp() {
        final OptionalLong total = backend.totalMemoryMb();
        if (total.isEmpty()) {
            return messages.get("settings.memory.helpUnknown");
        }
        return messages.format("settings.memory.help", formats.memory(total.getAsLong()),
            formats.memory(LauncherSettings.defaultMemoryMb(total.getAsLong())));
    }

    /** @return whether the client id currently comes from the environment variable */
    public boolean clientIdFromEnvironment() {
        final String env = backend.env().get(LauncherSettings.MS_CLIENT_ID_ENV);
        return msClientId.get().isBlank() && env != null && !env.isBlank();
    }

    /** @return the data directory */
    public Path dataDir() {
        return backend.paths().dataDir();
    }

    // ---------------------------------------------------------------- load / save

    /**
     * Fills the properties from settings and preferences.
     *
     * @param settings settings
     * @param prefs    UI preferences
     */
    public void load(final LauncherSettings settings, final UiPreferences prefs) {
        loading = true;
        try {
            baseline = Objects.requireNonNull(settings, "settings");
            baselinePrefs = Objects.requireNonNull(prefs, "prefs");
            memoryMb.set(settings.memoryMb());
            javaAuto.set(settings.javaPath().isEmpty());
            javaPath.set(settings.javaPath());
            jvmArgs.set(String.join(" ", settings.jvmArgs()));
            final Optional<Resolution> res = settings.resolutionOverride();
            resolutionEnabled.set(res.isPresent());
            resolutionWidth.set(res.map(r -> Integer.toString(r.width())).orElse(""));
            resolutionHeight.set(res.map(r -> Integer.toString(r.height())).orElse(""));
            keepLauncherOpen.set(settings.keepLauncherOpen());
            msClientId.set(settings.msClientId());
            releasesBaseUrl.set(settings.releasesBaseUrl());
            autoUpdateCheck.set(settings.autoUpdateCheck());
            shareOfficialFiles.set(settings.shareOfficialMinecraftFiles());
            developerMode.set(settings.developerMode());
            highContrast.set(THEME_HIGH_CONTRAST.equals(settings.theme()));
            reducedMotion.set(prefs.reducedMotion());
            javaCheck.set(JavaCheck.NONE);
            javaCheckText.set("");
            errorText.set("");
        } finally {
            loading = false;
        }
        dirty.set(false);
    }

    /** Restores the machine defaults into the editor (not saved until {@link #save}). */
    public void resetToDefaults() {
        final LauncherSettings defaults = backend.defaultSettings();
        final LauncherSettings stored = backend.settings();
        final UiPreferences storedPrefs = baselinePrefs;
        load(defaults, UiPreferences.defaults());
        baseline = stored;
        baselinePrefs = storedPrefs;
        dirty.set(!defaults.equals(stored) || !UiPreferences.defaults().equals(storedPrefs));
    }

    /** Discards edits. */
    public void discard() {
        load(backend.settings(), baselinePrefs);
    }

    /**
     * @return validation problems (empty when valid)
     */
    public List<String> validationErrors() {
        final List<String> problems = new ArrayList<>();
        if (memoryMb.get() < LauncherSettings.MIN_MEMORY_MB || memoryMb.get() > Math.max(maxMemoryMb(), FALLBACK_MAX_MEMORY_MB * 4)) {
            problems.add(messages.format("settings.error.memoryRange", Integer.toString(LauncherSettings.MIN_MEMORY_MB),
                Integer.toString(Math.max(maxMemoryMb(), FALLBACK_MAX_MEMORY_MB * 4))));
        }
        if (resolutionEnabled.get() && parseResolution().isEmpty()) {
            problems.add(messages.get("settings.error.resolution"));
        }
        final String url = releasesBaseUrl.get().trim();
        if (!url.isEmpty() && !isHttpUrl(url)) {
            problems.add(messages.get("settings.releasesUrl.invalid"));
        }
        return problems;
    }

    /**
     * Maps the editor to settings. Call only when {@link #validProperty()} is true.
     *
     * @return settings
     */
    public LauncherSettings toSettings() {
        final LauncherSettings base = baseline;
        return new LauncherSettings(base.schemaVersion(), memoryMb.get(), javaAuto.get() ? "" : javaPath.get().trim(),
            parseJvmArgs(jvmArgs.get()), resolutionEnabled.get() ? parseResolution().orElse(null) : null, keepLauncherOpen.get(),
            msClientId.get().trim(), releasesBaseUrl.get().trim(), autoUpdateCheck.get(), developerMode.get(), shareOfficialFiles.get(),
            highContrast.get() ? THEME_HIGH_CONTRAST : LauncherSettings.DEFAULT_THEME);
    }

    /** @return UI preferences from the editor */
    public UiPreferences toPreferences() {
        return baselinePrefs.withReducedMotion(reducedMotion.get());
    }

    /**
     * Saves settings through the backend.
     *
     * @param onSaved   receives the saved settings and preferences on the UI thread
     * @param onFailure receives failures
     */
    public void save(final SavedCallback onSaved, final Consumer<Throwable> onFailure) {
        final List<String> problems = validationErrors();
        if (!problems.isEmpty()) {
            errorText.set(problems.get(0));
            return;
        }
        final LauncherSettings settings = toSettings();
        final UiPreferences prefs = toPreferences();
        Async.run(executors, () -> {
            backend.saveSettings(settings);
            return settings;
        }, saved -> {
            baseline = saved;
            baselinePrefs = prefs;
            dirty.set(false);
            errorText.set("");
            onSaved.saved(saved, prefs);
        }, error -> {
            errorText.set(messages.format("settings.error.saveFailed", error.getMessage() == null ? error.toString() : error.getMessage()));
            onFailure.accept(error);
        });
    }

    /** Probes the manual Java path in the background. */
    public void validateJavaPath() {
        final String path = javaPath.get().trim();
        if (javaAuto.get() || path.isEmpty()) {
            javaCheck.set(JavaCheck.NONE);
            javaCheckText.set("");
            return;
        }
        javaCheck.set(JavaCheck.CHECKING);
        javaCheckText.set(messages.get("common.loading"));
        Async.run(executors, () -> backend.probeJava(Path.of(path)), result -> applyJavaCheck(result), error -> applyJavaCheck(Optional.empty()));
    }

    private void applyJavaCheck(final Optional<JavaInstall> result) {
        if (result.isEmpty()) {
            javaCheck.set(JavaCheck.INVALID);
            javaCheckText.set(messages.get("settings.javaPath.invalid"));
        } else if (!result.get().satisfies(LauncherVersion.JAVA_MAJOR)) {
            javaCheck.set(JavaCheck.TOO_OLD);
            javaCheckText.set(messages.format("settings.javaPath.tooOld", Integer.toString(result.get().major()), LauncherVersion.MINECRAFT,
                Integer.toString(LauncherVersion.JAVA_MAJOR)));
        } else {
            javaCheck.set(JavaCheck.VALID);
            javaCheckText.set(messages.format("settings.javaPath.valid", result.get().describe()));
        }
    }

    // ---------------------------------------------------------------- helpers

    private void markDirty() {
        if (!loading) {
            dirty.set(true);
        }
    }

    private Optional<Resolution> parseResolution() {
        try {
            final int w = Integer.parseInt(resolutionWidth.get().trim());
            final int h = Integer.parseInt(resolutionHeight.get().trim());
            return Optional.of(new Resolution(w, h));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Splits a JVM argument line on whitespace, honouring double quotes.
     *
     * @param text text
     * @return arguments
     */
    public static List<String> parseJvmArgs(final String text) {
        final List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        final StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    private static boolean isHttpUrl(final String value) {
        final String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        try {
            return java.net.URI.create(value).getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Receives the result of a successful save. */
    @FunctionalInterface
    public interface SavedCallback {
        /**
         * @param settings saved settings
         * @param prefs    saved UI preferences
         */
        void saved(LauncherSettings settings, UiPreferences prefs);
    }
}
