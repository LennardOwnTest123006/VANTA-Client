package dev.vanta.core.screen.profiles;

import dev.vanta.core.i18n.Lang;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.profiles.ProfileImportException;
import dev.vanta.core.ui.Canvas;
import dev.vanta.core.ui.CanvasText;
import dev.vanta.core.ui.Colors;
import dev.vanta.core.ui.FontKind;
import dev.vanta.core.ui.Icons;
import dev.vanta.core.ui.Keys;
import dev.vanta.core.ui.Rect;
import dev.vanta.core.ui.Size;
import dev.vanta.core.ui.Theme;
import dev.vanta.core.ui.UiContext;
import dev.vanta.core.ui.UiNode;
import dev.vanta.core.ui.layout.Row;
import dev.vanta.core.ui.widget.Button;
import dev.vanta.core.ui.widget.ListView;
import dev.vanta.core.ui.widget.Tabs;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Modal "Import profile" dialog with two sources: the clipboard (JSON copied from an export) and the
 * {@code config/vanta/imports} folder (lists its {@code .json} files with Refresh and Open folder). Both routes go
 * through {@link ProfileActions} so the same validation applies; the outcome is shown in a status line.
 */
public final class ImportDialog extends UiNode {
    /** Dialog width. */
    public static final int WIDTH = 320;
    /** Dialog height. */
    public static final int HEIGHT = 196;
    private static final int PAD = 14;
    private static final int LIST_H = 56;

    private final ProfileActions actions;
    private final Supplier<String> clipboard;
    private final Consumer<Profile> onImported;
    private final Tabs tabs;
    private final Button clipboardButton;
    private final ListView<Path> files;
    private final Button refresh;
    private final Button openFolder;
    private final Button importSelected;
    private final Button closeButton;
    private final Row folderButtons;
    private List<String> clipboardHint = List.of();
    private List<String> folderHint = List.of();
    private String status = "";
    private boolean statusIsError;

    private ImportDialog(UiContext ctx, ProfileActions actions, Supplier<String> clipboard,
                         Consumer<Profile> onImported, Runnable openFolderAction) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.onImported = Objects.requireNonNull(onImported, "onImported");
        tabs = new Tabs(List.of(Lang.tr("vanta.profiles.import.from_clipboard"),
                Lang.tr("vanta.profiles.import.from_folder")), 0);
        tabs.onSelect(i -> {
            status = "";
            updateVisibility();
            ctx.requestLayout();
        });
        tabs.setId("import.tabs");
        add(tabs);
        clipboardButton = Button.primary(Lang.tr("vanta.profiles.import.clipboard_button"), this::importClipboard)
                .icon(Icons.UPLOAD);
        clipboardButton.setId("import.clipboard");
        add(clipboardButton);
        files = new ListView<>(actions.listImportFiles(), p -> p.getFileName().toString()).rowHeight(14)
                .emptyText(Lang.tr("vanta.profiles.import.no_files"));
        files.setId("import.files");
        files.onSelect(p -> importSelected.setEnabled(true));
        files.onActivate(this::importFile);
        add(files);
        folderButtons = new Row(Theme.SPACE_3);
        refresh = Button.secondary(Lang.tr("vanta.profiles.import.refresh"), this::refreshFiles).compact(true)
                .icon(Icons.RESET);
        refresh.setId("import.refresh");
        openFolder = Button.secondary(Lang.tr("vanta.profiles.import.open_folder"), openFolderAction).compact(true)
                .icon(Icons.FOLDER);
        importSelected = Button.primary(Lang.tr("vanta.profiles.import.selected"),
                () -> files.selectedItem().ifPresent(this::importFile)).compact(true);
        importSelected.setId("import.selected");
        importSelected.setEnabled(false);
        folderButtons.add(refresh);
        folderButtons.add(openFolder);
        folderButtons.add(importSelected);
        add(folderButtons);
        closeButton = Button.ghost(Lang.tr("vanta.common.close"), () -> close(ctx));
        add(closeButton);
        updateVisibility();
    }

    /**
     * Opens the dialog.
     *
     * @param clipboard        reads the system clipboard
     * @param onImported       receives every successfully imported profile
     * @param openFolderAction opens {@code config/vanta/imports} in the system file browser
     */
    public static ImportDialog open(UiContext ctx, ProfileActions actions, Supplier<String> clipboard,
                                    Consumer<Profile> onImported, Runnable openFolderAction) {
        actions.ensureImportsDir();
        ImportDialog d = new ImportDialog(ctx, actions, clipboard, onImported, openFolderAction);
        d.position(ctx);
        ctx.popups().open(ctx, d, true, null);
        ctx.focus().focus(ctx, d.tabs);
        return d;
    }

    /** Closes the dialog. */
    public void close(UiContext ctx) {
        ctx.popups().close(ctx, this);
    }

    public Tabs tabs() {
        return tabs;
    }

    /** Status line shown after an import attempt (translated), empty before. */
    public String status() {
        return status;
    }

    public boolean isStatusError() {
        return statusIsError;
    }

    public ListView<Path> fileList() {
        return files;
    }

    public Button clipboardButton() {
        return clipboardButton;
    }

    /** Imports from the clipboard; the outcome goes to the status line. */
    public void importClipboard() {
        String text = clipboard.get();
        if (text == null || text.isBlank()) {
            status = Lang.tr("vanta.profiles.import_error.empty_clipboard");
            statusIsError = true;
            return;
        }
        try {
            Profile imported = actions.importFromText(text);
            success(imported);
        } catch (ProfileImportException e) {
            failure(e);
        }
    }

    /** Imports a file from the folder list. */
    public void importFile(Path file) {
        try {
            Profile imported = actions.importFile(file);
            success(imported);
        } catch (ProfileImportException e) {
            failure(e);
        }
    }

    /** Re-reads the imports folder. */
    public void refreshFiles() {
        files.setItems(actions.listImportFiles());
        importSelected.setEnabled(files.selectedItem().isPresent());
    }

    private void success(Profile imported) {
        status = Lang.tr("vanta.profiles.imported", imported.name());
        statusIsError = false;
        onImported.accept(imported);
    }

    private void failure(ProfileImportException e) {
        status = Lang.tr(e.reason().langKey());
        statusIsError = true;
    }

    private boolean clipboardTab() {
        return tabs.selected() == 0;
    }

    private void updateVisibility() {
        boolean clip = clipboardTab();
        clipboardButton.setVisible(clip);
        files.setVisible(!clip);
        folderButtons.setVisible(!clip);
    }

    private void position(UiContext ctx) {
        int w = Math.min(WIDTH, Math.max(160, ctx.screenWidth() - Theme.SPACE_6 * 2));
        int h = Math.min(HEIGHT, Math.max(120, ctx.screenHeight() - Theme.SPACE_4 * 2));
        setBounds((ctx.screenWidth() - w) / 2, (ctx.screenHeight() - h) / 2, w, h);
        clipboardHint = CanvasText.wrap(Lang.tr("vanta.profiles.import.clipboard_hint"), w - PAD * 2, FontKind.UI,
                ctx.metrics());
        folderHint = CanvasText.wrap(Lang.tr("vanta.profiles.import.folder_hint",
                actions.displayPath(actions.importsDir())), w - PAD * 2, FontKind.UI, ctx.metrics());
    }

    private int bodyTop() {
        return bounds().y() + PAD + 11 + Theme.SPACE_3 + Tabs.HEIGHT + Theme.SPACE_3;
    }

    @Override
    protected Size measure(UiContext ctx) {
        return bounds().size();
    }

    @Override
    public void layout(UiContext ctx) {
        Rect b = bounds();
        Size ts = tabs.preferredSize(ctx);
        tabs.setBounds(b.x() + PAD, b.y() + PAD + 11 + Theme.SPACE_3, Math.min(ts.w(), b.w() - PAD * 2), Tabs.HEIGHT);
        tabs.layout(ctx);
        int lh = ctx.lineHeight(FontKind.UI);
        int y = bodyTop();
        if (clipboardTab()) {
            y += clipboardHint.size() * lh + Theme.SPACE_4;
            Size s = clipboardButton.preferredSize(ctx);
            clipboardButton.setBounds(b.x() + PAD, y, Math.min(s.w(), b.w() - PAD * 2), s.h());
            clipboardButton.layout(ctx);
        } else {
            y += folderHint.size() * lh + Theme.SPACE_3;
            files.setBounds(b.x() + PAD, y, b.w() - PAD * 2, LIST_H);
            files.layout(ctx);
            y += LIST_H + Theme.SPACE_3;
            folderButtons.setBounds(b.x() + PAD, y, b.w() - PAD * 2, Button.HEIGHT);
            folderButtons.layout(ctx);
        }
        Size cs = closeButton.preferredSize(ctx);
        closeButton.setBounds(b.right() - PAD - cs.w(), b.bottom() - PAD - cs.h(), cs.w(), cs.h());
        closeButton.layout(ctx);
    }

    @Override
    protected void renderSelf(Canvas canvas, UiContext ctx) {
        Theme theme = ctx.theme();
        Rect b = bounds();
        canvas.fillRounded(b.x() + 2, b.y() + 4, b.w(), b.h(), Theme.RADIUS_LG, Colors.withAlpha(theme.bgVoid(), 0.6f));
        canvas.fillRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.surface2());
        canvas.strokeRounded(b.x(), b.y(), b.w(), b.h(), Theme.RADIUS_LG, theme.borderStrong());
        canvas.text(Lang.tr("vanta.profiles.import.title"), b.x() + PAD, b.y() + PAD, theme.textPrimary(),
                FontKind.UI_BOLD, false);
        int lh = canvas.lineHeight(FontKind.UI);
        int y = bodyTop();
        for (String line : clipboardTab() ? clipboardHint : folderHint) {
            canvas.text(line, b.x() + PAD, y, theme.textSecondary(), FontKind.UI, false);
            y += lh;
        }
        if (!clipboardTab()) {
            Rect list = files.bounds();
            canvas.fillRounded(list.x(), list.y(), list.w(), list.h(), Theme.RADIUS_MD, theme.surface1());
            canvas.strokeRounded(list.x(), list.y(), list.w(), list.h(), Theme.RADIUS_MD, theme.borderSubtle());
        }
        if (!status.isEmpty()) {
            int sy = b.bottom() - PAD - Button.HEIGHT + (Button.HEIGHT - lh) / 2;
            Icons icon = statusIsError ? Icons.ERROR : Icons.SUCCESS;
            int color = statusIsError ? theme.danger() : theme.success();
            icon.draw(canvas, b.x() + PAD, sy, 9, color);
            int maxW = closeButton.bounds().x() - Theme.SPACE_3 - (b.x() + PAD + 9 + Theme.SPACE_2);
            canvas.text(canvas.textClipped(status, Math.max(0, maxW), FontKind.UI), b.x() + PAD + 9 + Theme.SPACE_2, sy,
                    color, FontKind.UI, false);
        }
    }

    @Override
    public boolean keyDown(UiContext ctx, int key, int scancode, int mods) {
        if (key == Keys.ESCAPE) {
            close(ctx);
            return true;
        }
        return false;
    }
}
