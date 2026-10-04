package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.ui.Messages;
import dev.vanta.launcher.ui.model.ToastModel;
import dev.vanta.launcher.ui.model.UiExecutors;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens URLs, folders and files with the operating system and writes to the clipboard. Failures become toasts; the
 * launcher never crashes because a desktop integration is missing (headless CI, minimal Linux sessions).
 */
public final class SystemOpener {

    private static final Logger LOG = Logger.getLogger("VANTA.UI");

    private final Consumer<String> browser;
    private final UiExecutors executors;
    private final ToastModel toasts;
    private final Messages messages;

    /**
     * @param browser   opens a URL in the default browser (JavaFX {@code HostServices::showDocument})
     * @param executors executors
     * @param toasts    toasts
     * @param messages  messages
     */
    public SystemOpener(final Consumer<String> browser, final UiExecutors executors, final ToastModel toasts, final Messages messages) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Opens a web page.
     *
     * @param uri URL
     */
    public void browse(final URI uri) {
        try {
            browser.accept(uri.toString());
        } catch (RuntimeException e) {
            report(uri.toString(), e);
        }
    }

    /**
     * Opens a directory in the file manager.
     *
     * @param dir directory (created when missing)
     */
    public void openFolder(final Path dir) {
        executors.background().execute(() -> {
            try {
                Files.createDirectories(dir);
                if (!openWithDesktop(dir)) {
                    browser.accept(dir.toUri().toString());
                }
                executors.onUi(() -> toasts.info(messages.get("common.opened.title"), messages.format("common.opened.folder", dir.toString())));
            } catch (IOException | RuntimeException e) {
                executors.onUi(() -> report(dir.toString(), e));
            }
        });
    }

    /**
     * Hands a file (an installer) to the operating system. The caller has already confirmed with the user.
     *
     * @param file      file
     * @param onOpened  runs on the UI thread after the OS accepted the request
     */
    public void openFile(final Path file, final Runnable onOpened) {
        executors.background().execute(() -> {
            try {
                if (!openWithDesktop(file)) {
                    browser.accept(file.toUri().toString());
                }
                executors.onUi(onOpened);
            } catch (IOException | RuntimeException e) {
                executors.onUi(() -> report(file.toString(), e));
            }
        });
    }

    /**
     * Copies text to the system clipboard.
     *
     * @param text text
     * @return whether the clipboard accepted it
     */
    public boolean copy(final String text) {
        try {
            final ClipboardContent content = new ClipboardContent();
            content.putString(text);
            return Clipboard.getSystemClipboard().setContent(content);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Clipboard unavailable", e);
            return false;
        }
    }

    private static boolean openWithDesktop(final Path path) throws IOException {
        try {
            if (!Desktop.isDesktopSupported()) {
                return false;
            }
            final Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) {
                return false;
            }
            desktop.open(path.toFile());
            return true;
        } catch (UnsupportedOperationException | IllegalArgumentException | SecurityException | LinkageError e) {
            LOG.log(Level.FINE, "java.awt.Desktop unavailable: {0}", e.toString());
            return false;
        }
    }

    private void report(final String what, final Exception e) {
        LOG.log(Level.WARNING, "Could not open " + what, e);
        toasts.error(messages.get("home.toast.error.title"), messages.format("error.openFailed", what,
            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }
}
