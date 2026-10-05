package dev.vanta.launcher.ui.view;

import dev.vanta.launcher.core.util.OsInfo;
import dev.vanta.launcher.ui.BrowserOpener;
import dev.vanta.launcher.ui.Messages;
import dev.vanta.launcher.ui.model.ToastModel;
import dev.vanta.launcher.ui.model.UiExecutors;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A click on a link never does nothing: when no opener confirms that the browser got the address, it is shown in a
 * toast, and when every opener failed it is also copied to the clipboard (launcher 1.0.1 only printed HostServices
 * failures to stderr).
 */
class SystemOpenerTest {

    private static final URI PAGE = URI.create("https://vanta-client.netlify.app/docs/launcher");
    private final Messages messages = Messages.english();
    private final ToastModel toasts = new ToastModel();
    private final List<String> clipboard = new ArrayList<>();
    private final List<String> hostServices = new ArrayList<>();

    private SystemOpener opener(final boolean desktopWorks, final int exit, final boolean hostWorks, final boolean clipboardWorks) {
        final BrowserOpener browser = new BrowserOpener(new OsInfo("linux", "x64", "6.8"), new BrowserOpener.DesktopBrowser() {
            @Override
            public boolean supported() {
                return desktopWorks;
            }

            @Override
            public void browse(final URI uri) {
            }
        }, (command, timeout) -> {
            if (exit < 0) {
                throw new IOException("xdg-open not found");
            }
            return OptionalInt.of(exit);
        }, url -> {
            if (!hostWorks) {
                throw new IllegalStateException("no HostServices");
            }
            hostServices.add(url);
        }, Duration.ofSeconds(1));
        return new SystemOpener(hostServices::add, browser, text -> {
            if (clipboardWorks) {
                clipboard.add(text);
            }
            return clipboardWorks;
        }, UiExecutors.direct(), toasts, messages);
    }

    @Test
    void confirmedOpenShowsNothing() {
        opener(true, 0, true, true).browse(PAGE);
        opener(false, 0, true, true).browse(PAGE);
        assertTrue(toasts.toasts().isEmpty());
        assertTrue(clipboard.isEmpty(), "the clipboard is left alone when the browser opened");
        assertTrue(hostServices.isEmpty());
    }

    @Test
    void unconfirmedHostServicesShowsTheAddressButKeepsTheClipboard() {
        // The sign-in dialog: "Copy code", then "Open microsoft.com/link". HostServices accepted the address and may
        // well have opened the browser, so the code on the clipboard must not be replaced by the address.
        clipboard.add("ABCD1234");
        opener(false, 3, true, true).browse(PAGE);
        assertEquals(List.of(PAGE.toString()), hostServices);
        assertEquals(List.of("ABCD1234"), clipboard, "the clipboard is only written when every opener failed");
        final ToastModel.Toast toast = toasts.toasts().get(0);
        assertEquals(ToastModel.Kind.INFO, toast.kind());
        assertEquals(messages.get("browser.unverified.title"), toast.title());
        assertEquals(messages.format("browser.unverified.text", PAGE.toString()), toast.message());
        assertFalse(toast.message().contains("clipboard"), toast.message());
    }

    @Test
    void nothingWorkedCopiesTheAddressAndShowsIt() {
        opener(false, -1, false, true).browse(PAGE);
        assertEquals(List.of(PAGE.toString()), clipboard);
        final ToastModel.Toast toast = toasts.toasts().get(0);
        assertEquals(ToastModel.Kind.WARNING, toast.kind());
        assertEquals(messages.get("browser.failed.title"), toast.title());
        assertEquals(messages.format("browser.failed.copied", PAGE.toString()), toast.message());

        opener(false, -1, false, false).browse(PAGE);
        assertEquals(messages.format("browser.failed.notCopied", PAGE.toString()), toasts.toasts().get(1).message(),
            "without a clipboard the toast still shows the address");
    }
}
