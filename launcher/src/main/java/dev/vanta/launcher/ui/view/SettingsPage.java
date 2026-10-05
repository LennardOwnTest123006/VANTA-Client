package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.LauncherVersion;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.ui.model.SettingsViewModel;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;

/**
 * Settings: every launcher setting in sectioned cards with inline validation, plus the UI-only preferences
 * (high contrast, reduced motion). Changes are saved explicitly.
 */
public final class SettingsPage extends VBox {

    private final AppContext ctx;
    private final SettingsViewModel vm;
    private final Runnable onThemeChanged;

    /**
     * @param ctx            context
     * @param onThemeChanged re-applies the theme after a save
     */
    public SettingsPage(final AppContext ctx, final Runnable onThemeChanged) {
        this.ctx = ctx;
        this.vm = ctx.settings();
        this.onThemeChanged = onThemeChanged;
        getStyleClass().add("page");
        setSpacing(18);

        final Label unsaved = Ui.badge(ctx.t("settings.unsaved"), "warning");
        Ui.bindVisible(unsaved, vm.dirtyProperty());
        final Button reset = Ui.button(ctx.t("settings.reset"), Icons.Icon.ROTATE_CCW, "ghost", "small");
        reset.setOnAction(e -> {
            vm.resetToDefaults();
            ctx.toasts().info(ctx.t("settings.reset.title"), ctx.t("settings.reset.message"));
        });
        final Button discard = Ui.button(ctx.t("settings.discard"), "secondary", "small");
        discard.disableProperty().bind(vm.dirtyProperty().not());
        discard.setOnAction(e -> vm.discard());
        final Button save = Ui.button(ctx.t("settings.save"), Icons.Icon.CHECK, "primary", "small");
        save.disableProperty().bind(vm.dirtyProperty().not().or(vm.validProperty().not()));
        save.setOnAction(e -> vm.save((settings, prefs) -> {
            ctx.savePrefs(prefs);
            ctx.session().saveSettings(settings, () -> { }, error -> { });
            onThemeChanged.run();
            ctx.toasts().success(ctx.t("settings.saved.title"), ctx.t("settings.saved.message"));
        }, error -> ctx.toasts().error(ctx.t("home.toast.error.title"), vm.errorTextProperty().get())));
        final HBox header = Ui.pageHeader(ctx.t("settings.title"), ctx.t("settings.subtitle"), unsaved, reset, discard, save);

        final Label error = Ui.paragraph("", "field-error");
        error.textProperty().bind(Bindings.createStringBinding(() -> {
            final var problems = vm.validationErrors();
            return problems.isEmpty() ? vm.errorTextProperty().get() : problems.get(0);
        }, vm.memoryMbProperty(), vm.resolutionEnabledProperty(), vm.resolutionWidthProperty(), vm.resolutionHeightProperty(),
            vm.releasesBaseUrlProperty(), vm.errorTextProperty()));
        Ui.bindVisible(error, Bindings.createBooleanBinding(() -> !error.getText().isEmpty(), error.textProperty()));

        final VBox sections = new VBox(16, gameSection(), javaSection(), releasesSection(), advancedSection(), appearanceSection());
        sections.setPadding(Ui.insets(0, 16, 8, 0));
        final ScrollPane scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(320);
        scroll.setMinHeight(0);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, error, scroll);
    }

    // ---------------------------------------------------------------- sections

    private VBox gameSection() {
        final Slider memory = new Slider(LauncherSettings.MIN_MEMORY_MB, vm.maxMemoryMb(), vm.memoryMbProperty().get());
        memory.setBlockIncrement(256);
        memory.setMajorTickUnit(2048);
        memory.setMinorTickCount(7);
        memory.setShowTickMarks(false);
        memory.setMaxWidth(Double.MAX_VALUE);
        memory.valueProperty().addListener((obs, old, now) -> {
            final int rounded = (int) Math.round(now.doubleValue() / 128d) * 128;
            if (vm.memoryMbProperty().get() != rounded) {
                vm.memoryMbProperty().set(rounded);
            }
        });
        vm.memoryMbProperty().addListener((obs, old, now) -> {
            if (Math.abs(memory.getValue() - now.doubleValue()) >= 1) {
                memory.setValue(now.doubleValue());
            }
        });
        final Label memoryValue = Ui.label("", "value-large");
        memoryValue.textProperty().bind(Bindings.createStringBinding(() -> ctx.t("settings.memory.value", Integer.toString(vm.memoryMbProperty().get())),
            vm.memoryMbProperty()));
        memoryValue.setMinWidth(110);
        memoryValue.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(memory, Priority.ALWAYS);
        final HBox memoryRow = new HBox(16, memory, memoryValue);
        memoryRow.setAlignment(Pos.CENTER_LEFT);
        final VBox memoryBlock = new VBox(10, Ui.label(ctx.t("settings.memory.label"), "field-label"), memoryRow,
            Ui.paragraph(vm.memoryHelp(), "field-help"));

        final Switch resolutionSwitch = new Switch();
        resolutionSwitch.selectedProperty().bindBidirectional(vm.resolutionEnabledProperty());
        final TextField width = field(vm.resolutionWidthProperty(), ctx.t("settings.resolution.width"), 96);
        final TextField height = field(vm.resolutionHeightProperty(), ctx.t("settings.resolution.height"), 96);
        width.disableProperty().bind(vm.resolutionEnabledProperty().not());
        height.disableProperty().bind(vm.resolutionEnabledProperty().not());
        final HBox size = new HBox(8, width, Ui.label("×", "text-muted"), height);
        size.setAlignment(Pos.CENTER_LEFT);
        final VBox resolution = row(ctx.t("settings.resolution.label"), ctx.t("settings.resolution.help"), new HBox(14, size, resolutionSwitch));

        final Switch keepOpen = new Switch();
        keepOpen.selectedProperty().bindBidirectional(vm.keepLauncherOpenProperty());
        final VBox keep = row(ctx.t("settings.keepOpen.label"), ctx.t("settings.keepOpen.help"), keepOpen);

        return section(ctx.t("settings.section.game"), memoryBlock, resolution, keep);
    }

    private VBox javaSection() {
        final ComboBox<String> mode = new ComboBox<>();
        mode.getItems().addAll(ctx.t("settings.javaPath.auto", Integer.toString(LauncherVersion.JAVA_MAJOR)), ctx.t("settings.javaPath.manual"));
        mode.getSelectionModel().select(vm.javaAutoProperty().get() ? 0 : 1);
        mode.getSelectionModel().selectedIndexProperty().addListener((obs, old, now) -> vm.javaAutoProperty().set(now.intValue() == 0));
        vm.javaAutoProperty().addListener((obs, old, now) -> mode.getSelectionModel().select(now ? 0 : 1));
        mode.setMaxWidth(Double.MAX_VALUE);

        final TextField path = field(vm.javaPathProperty(), ctx.t("settings.javaPath.prompt"), 0);
        path.getStyleClass().add("mono");
        HBox.setHgrow(path, Priority.ALWAYS);
        final Button browse = Ui.button(ctx.t("settings.javaPath.browse"), Icons.Icon.FOLDER, "secondary", "small");
        browse.setOnAction(e -> {
            final FileChooser chooser = new FileChooser();
            chooser.setTitle(ctx.t("settings.javaPath.chooser"));
            final File picked = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (picked != null) {
                vm.javaPathProperty().set(picked.getAbsolutePath());
                vm.validateJavaPath();
            }
        });
        final Button validate = Ui.button(ctx.t("settings.javaPath.validate"), Icons.Icon.CHECK, "secondary", "small");
        validate.setOnAction(e -> vm.validateJavaPath());
        final HBox pathRow = new HBox(8, path, browse, validate);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        final Label check = Ui.paragraph("", "field-help");
        check.textProperty().bind(vm.javaCheckTextProperty());
        vm.javaCheckProperty().addListener((obs, old, now) -> {
            check.getStyleClass().removeAll("field-help", "field-error", "field-ok");
            check.getStyleClass().add(now == SettingsViewModel.JavaCheck.VALID ? "field-ok"
                : now == SettingsViewModel.JavaCheck.INVALID || now == SettingsViewModel.JavaCheck.TOO_OLD ? "field-error" : "field-help");
        });
        final VBox manual = new VBox(8, pathRow, check);
        Ui.bindVisible(manual, vm.javaAutoProperty().not());
        final VBox javaBlock = new VBox(10, Ui.label(ctx.t("settings.javaPath.label"), "field-label"), mode, manual);

        final TextField jvm = field(vm.jvmArgsProperty(), ctx.t("settings.jvmArgs.prompt"), 0);
        jvm.getStyleClass().add("mono");
        jvm.setMaxWidth(Double.MAX_VALUE);
        final VBox jvmBlock = new VBox(8, Ui.label(ctx.t("settings.jvmArgs.label"), "field-label"), jvm, Ui.paragraph(ctx.t("settings.jvmArgs.help"), "field-help"));

        return section(ctx.t("settings.section.java"), javaBlock, jvmBlock);
    }

    private VBox releasesSection() {
        final TextField clientId = field(vm.msClientIdProperty(), ctx.t("settings.msClientId.prompt"), 0);
        clientId.getStyleClass().add("mono");
        clientId.setMaxWidth(Double.MAX_VALUE);
        final Button docs = Ui.button(ctx.t("settings.msClientId.docs"), Icons.Icon.EXTERNAL, "ghost", "small");
        docs.setOnAction(e -> ctx.opener().browse(ctx.links().clientIdDocs()));
        final Label fromEnv = Ui.paragraph(ctx.t("settings.msClientId.fromEnv"), "field-ok");
        Ui.bindVisible(fromEnv, Bindings.createBooleanBinding(vm::clientIdFromEnvironment, vm.msClientIdProperty()));
        final VBox clientBlock = new VBox(8, Ui.label(ctx.t("settings.msClientId.label"), "field-label"), clientId,
            Ui.paragraph(ctx.t("settings.msClientId.help"), "field-help"), fromEnv, docs);

        // The prompt shows the built-in default: an empty field means "use VANTA_RELEASES_BASE_URL or the default".
        final TextField releases = field(vm.releasesBaseUrlProperty(), vm.defaultReleasesBaseUrl(), 0);
        releases.getStyleClass().add("mono");
        releases.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(releases, Priority.ALWAYS);
        final Button resetReleases = Ui.button(ctx.t("settings.releasesUrl.resetDefault"), Icons.Icon.ROTATE_CCW, "secondary", "small");
        resetReleases.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        resetReleases.disableProperty().bind(Bindings.createBooleanBinding(() -> vm.releasesBaseUrlProperty().get().isBlank(),
            vm.releasesBaseUrlProperty()));
        resetReleases.setOnAction(e -> vm.resetReleasesBaseUrl());
        final HBox releasesRow = new HBox(8, releases, resetReleases);
        releasesRow.setAlignment(Pos.CENTER_LEFT);
        final Label effective = Ui.paragraph("", "field-help", "settings-effective-url");
        effective.textProperty().bind(Bindings.createStringBinding(() -> ctx.t("settings.releasesUrl.effective",
            vm.effectiveReleasesUrlProperty().get(), vm.effectiveReleasesSourceProperty().get()),
            vm.effectiveReleasesUrlProperty(), vm.effectiveReleasesSourceProperty()));
        final VBox releasesBlock = new VBox(8, Ui.label(ctx.t("settings.releasesUrl.label"), "field-label"), releasesRow, effective,
            Ui.paragraph(ctx.t("settings.releasesUrl.help"), "field-help"));

        final Switch autoUpdate = new Switch();
        autoUpdate.selectedProperty().bindBidirectional(vm.autoUpdateCheckProperty());
        final VBox auto = row(ctx.t("settings.autoUpdate.label"), ctx.t("settings.autoUpdate.help"), autoUpdate);

        return section(ctx.t("settings.section.releases"), clientBlock, releasesBlock, auto);
    }

    private VBox advancedSection() {
        final Switch share = new Switch();
        share.selectedProperty().bindBidirectional(vm.shareOfficialFilesProperty());
        final Switch developer = new Switch();
        developer.selectedProperty().bindBidirectional(vm.developerModeProperty());
        final Path dataDir = vm.dataDir();
        // A long path shrinks with an ellipsis in the middle (the tooltip has all of it); the button keeps its label.
        final Label dir = Ui.label(dataDir.toString(), "mono", "text-secondary", "data-dir-path");
        dir.setStyle("-fx-font-size: 12px;");
        dir.setMinWidth(0);
        dir.setMaxWidth(Double.MAX_VALUE);
        dir.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
        dir.setTooltip(Ui.tooltip(dataDir.toString()));
        HBox.setHgrow(dir, Priority.ALWAYS);
        final Button open = Ui.button(ctx.t("settings.dataDir.open"), Icons.Icon.FOLDER, "secondary", "small");
        open.getStyleClass().add("data-dir-open");
        open.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        open.setTooltip(Ui.tooltip(ctx.t("settings.dataDir.open.tooltip")));
        open.setOnAction(e -> ctx.opener().openFolder(dataDir));
        final HBox dirRow = new HBox(10, dir, open);
        dirRow.setAlignment(Pos.CENTER_LEFT);
        return section(ctx.t("settings.section.advanced"),
            row(ctx.t("settings.shareFiles.label"), ctx.t("settings.shareFiles.help"), share),
            row(ctx.t("settings.developerMode.label"), ctx.t("settings.developerMode.help"), developer),
            new VBox(8, Ui.label(ctx.t("settings.dataDir.label"), "field-label"), dirRow));
    }

    private VBox appearanceSection() {
        final ComboBox<String> theme = new ComboBox<>();
        theme.getItems().add(ctx.t("settings.theme.dark"));
        theme.getSelectionModel().select(0);
        theme.setMaxWidth(260);
        final VBox themeBlock = new VBox(8, Ui.label(ctx.t("settings.theme.label"), "field-label"), theme,
            Ui.paragraph(ctx.t("settings.theme.help"), "field-help"));
        final Switch contrast = new Switch();
        contrast.selectedProperty().bindBidirectional(vm.highContrastProperty());
        final Switch motion = new Switch();
        motion.selectedProperty().bindBidirectional(vm.reducedMotionProperty());
        return section(ctx.t("settings.section.appearance"), themeBlock,
            row(ctx.t("settings.highContrast.label"), ctx.t("settings.highContrast.help"), contrast),
            row(ctx.t("settings.reducedMotion.label"), ctx.t("settings.reducedMotion.help"), motion));
    }

    // ---------------------------------------------------------------- helpers

    private VBox section(final String title, final Node... blocks) {
        final VBox card = Ui.card(Ui.label(title, "section-title"));
        card.setSpacing(18);
        card.getChildren().addAll(blocks);
        return card;
    }

    private static VBox row(final String label, final String help, final Node control) {
        final Label l = Ui.label(label, "field-label");
        final Label h = Ui.paragraph(help, "field-help");
        h.setMaxWidth(520);
        final VBox text = new VBox(4, l, h);
        HBox.setHgrow(text, Priority.ALWAYS);
        final HBox line = new HBox(24, text, control);
        line.setAlignment(Pos.CENTER_LEFT);
        return new VBox(line);
    }

    private static TextField field(final javafx.beans.property.StringProperty property, final String prompt, final double width) {
        final TextField f = new TextField();
        f.setPromptText(prompt);
        f.textProperty().bindBidirectional(property);
        if (width > 0) {
            f.setPrefWidth(width);
            f.setMaxWidth(width);
        }
        return f;
    }
}
