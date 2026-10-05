package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.ui.BrowserOpener;
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
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens URLs, folders and files with the operating system and writes to the clipboard. Failures become toasts; the
 * launcher never crashes because a desktop integration is missing (headless CI, minimal Linux sessions).
 *
 * <p>Web pages go through {@link BrowserOpener} off the JavaFX thread. When no opener could confirm that the browser
 * got the address, it is shown in a toast, so a click never does nothing. Only when every opener failed is it also
 * copied to the clipboard: after JavaFX {@code HostServices} accepted it (it may well have opened the browser) the
 * clipboard is left alone, so for example the sign-in code copied just before "Open microsoft.com/link" stays there.</p>
 */
public final class SystemOpener {

    private static final Logger LOG = Logger.getLogger("VANTA.UI");

    private final Consumer<String> browser;
    private final BrowserOpener browserOpener;
    private final Predicate<String> clipboard;
    private final UiExecutors executors;
    private final ToastModel toasts;
    private final Messages messages;

    /**
     * @param browser       JavaFX {@code HostServices::showDocument} (folders fall back to it)
     * @param browserOpener opens web pages
     * @param clipboard     copies text to the clipboard (UI thread) and says whether that worked
     *                      ({@link #copyToSystemClipboard} in the application)
     * @param executors     executors
     * @param toasts        toasts
     * @param messages      messages
     */
    public SystemOpener(final Consumer<String> browser, final BrowserOpener browserOpener, final Predicate<String> clipboard,
                        final UiExecutors executors, final ToastModel toasts, final Messages messages) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.browserOpener = Objects.requireNonNull(browserOpener, "browserOpener");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Opens a web page in the background (see {@link BrowserOpener}). Without a confirmed opener the address is shown
     * in a toast; when no opener accepted it at all ({@link BrowserOpener.Method#NONE}) it is also copied to the
     * clipboard.
     *
     * @param uri URL
     */
    public void browse(final URI uri) {
        final String url = uri.toString();
        executors.background().execute(() -> {
            final BrowserOpener.Result result = browserOpener.open(uri);
            if (!result.failures().isEmpty()) {
                LOG.log(result.opened() ? Level.FINE : Level.WARNING, "Opening " + url + ": " + String.join("; ", result.failures()));
            }
            if (result.confirmed()) {
                return;
            }
            executors.onUi(() -> {
                if (result.opened()) {
                    // HostServices never reports a failure: show the address, but keep what the user put on the clipboard.
                    toasts.info(messages.get("browser.unverified.title"), messages.format("browser.unverified.text", url));
                    return;
                }
                final boolean copied = clipboard.test(url);
                toasts.warning(messages.get("browser.failed.title"),
                    messages.format(copied ? "browser.failed.copied" : "browser.failed.notCopied", url));
            });
        });
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
        return clipboard.test(text);
    }

    /**
     * @param text text
     * @return whether the system clipboard (JavaFX, UI thread) accepted it
     */
    static boolean copyToSystemClipboard(final String text) {
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
