package dev.vanta.launcher.ui;

import dev.vanta.launcher.core.util.OsInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decision logic of {@link BrowserOpener} with fakes for {@code java.awt.Desktop}, the operating system command and
 * JavaFX {@code HostServices}.
 */
class BrowserOpenerTest {

    private static final OsInfo LINUX = new OsInfo("linux", "x64", "6.8");
    private static final OsInfo MAC = new OsInfo("osx", "arm64", "15.0");
    private static final OsInfo WINDOWS = new OsInfo("windows", "x64", "10.0");
    private static final URI PAGE = URI.create("https://vanta-client.netlify.app/download?from=launcher&v=1");

    /** Scripted environment that records every call. */
    private static final class Fakes {
        boolean desktopSupported;
        IOException desktopFailure;
        RuntimeException desktopCrash;
        OptionalInt exit = OptionalInt.of(0);
        IOException commandFailure;
        RuntimeException hostFailure;
        final List<String> calls = new ArrayList<>();
        final List<List<String>> commands = new ArrayList<>();
        final List<Duration> timeouts = new ArrayList<>();

        BrowserOpener opener(final OsInfo os) {
            return new BrowserOpener(os, new BrowserOpener.DesktopBrowser() {
                @Override
                public boolean supported() {
                    calls.add("desktop.supported");
                    if (desktopCrash != null) {
                        throw desktopCrash;
                    }
                    return desktopSupported;
                }

                @Override
                public void browse(final URI uri) throws IOException {
                    calls.add("desktop.browse " + uri);
                    if (desktopFailure != null) {
                        throw desktopFailure;
                    }
                }
            }, (command, timeout) -> {
                calls.add("command");
                commands.add(command);
                timeouts.add(timeout);
                if (commandFailure != null) {
                    throw commandFailure;
                }
                return exit;
            }, url -> {
                calls.add("host " + url);
                if (hostFailure != null) {
                    throw hostFailure;
                }
            }, Duration.ofSeconds(7));
        }
    }

    @Test
    void desktopBrowseIsUsedWhenSupported() {
        final Fakes f = new Fakes();
        f.desktopSupported = true;
        final BrowserOpener.Result r = f.opener(LINUX).open(PAGE);
        assertEquals(BrowserOpener.Method.DESKTOP, r.method());
        assertTrue(r.confirmed());
        assertTrue(r.failures().isEmpty());
        assertEquals(List.of("desktop.supported", "desktop.browse " + PAGE), f.calls, "no command, no HostServices");
    }

    @Test
    void failingDesktopFallsBackToTheOperatingSystemCommand() {
        final Fakes f = new Fakes();
        f.desktopSupported = true;
        f.desktopFailure = new IOException("Failed to show URI");
        final BrowserOpener.Result r = f.opener(LINUX).open(PAGE);
        assertEquals(BrowserOpener.Method.COMMAND, r.method());
        assertTrue(r.confirmed());
        assertEquals(List.of(List.of("xdg-open", PAGE.toString())), f.commands, "the address is one argument, no shell");
        assertEquals(List.of(Duration.ofSeconds(7)), f.timeouts);
        assertTrue(r.failures().get(0).contains("Failed to show URI"), r.failures().toString());
        assertFalse(f.calls.stream().anyMatch(c -> c.startsWith("host")));
    }

    @Test
    void eachPlatformHasItsOwnCommand() {
        assertEquals(Optional.of(List.of("xdg-open", PAGE.toString())), BrowserOpener.command(LINUX, PAGE));
        assertEquals(Optional.of(List.of("open", PAGE.toString())), BrowserOpener.command(MAC, PAGE));
        assertEquals(Optional.of(List.of("rundll32", "url.dll,FileProtocolHandler", PAGE.toString())), BrowserOpener.command(WINDOWS, PAGE));
        assertEquals(Optional.empty(), BrowserOpener.command(new OsInfo("haiku", "x64", ""), PAGE), "unknown system: no guess");
        assertEquals(Optional.empty(), BrowserOpener.command(LINUX, URI.create("file:///etc/passwd")), "only web pages go to the command");
        assertEquals(Optional.empty(), BrowserOpener.command(WINDOWS, URI.create("javascript:alert(1)")));

        for (OsInfo os : List.of(MAC, WINDOWS)) {
            final Fakes f = new Fakes();
            final BrowserOpener.Result r = f.opener(os).open(PAGE);
            assertEquals(BrowserOpener.Method.COMMAND, r.method(), os.toString());
            assertEquals(BrowserOpener.command(os, PAGE).orElseThrow(), f.commands.get(0));
            assertEquals("java.awt.Desktop cannot browse on this system", r.failures().get(0));
        }
    }

    @Test
    void nonZeroExitCodeIsAFailure() {
        final Fakes f = new Fakes();
        f.exit = OptionalInt.of(3);
        final BrowserOpener.Result r = f.opener(LINUX).open(PAGE);
        assertEquals(BrowserOpener.Method.HOST_SERVICES, r.method());
        assertTrue(r.opened());
        assertFalse(r.confirmed(), "HostServices cannot report a failure");
        assertTrue(r.failures().contains("xdg-open exited with code 3"), r.failures().toString());
        assertEquals("host " + PAGE, f.calls.get(f.calls.size() - 1));
    }

    @Test
    void missingCommandFallsBackToHostServices() {
        final Fakes f = new Fakes();
        f.commandFailure = new IOException("Cannot run program \"xdg-open\": error=2, No such file or directory");
        final BrowserOpener.Result r = f.opener(LINUX).open(PAGE);
        assertEquals(BrowserOpener.Method.HOST_SERVICES, r.method());
        assertTrue(r.failures().get(1).contains("No such file or directory"), r.failures().toString());
    }

    @Test
    void aCommandStillRunningAfterTheTimeoutStartedTheBrowser() {
        final Fakes f = new Fakes();
        f.exit = OptionalInt.empty();
        final BrowserOpener.Result r = f.opener(LINUX).open(PAGE);
        assertEquals(BrowserOpener.Method.COMMAND, r.method());
        assertFalse(f.calls.stream().anyMatch(c -> c.startsWith("host")));
    }

    @Test
    void everythingFailingReportsNone() {
        final Fakes f = new Fakes();
        f.desktopCrash = new UnsupportedOperationException("headless");
        f.exit = OptionalInt.of(4);
        f.hostFailure = new IllegalStateException("no browser");
        final BrowserOpener.Result r = f.opener(LINUX).open(PAGE);
        assertEquals(BrowserOpener.Method.NONE, r.method());
        assertFalse(r.opened());
        assertEquals(3, r.failures().size(), r.failures().toString());
    }

    @Test
    void unknownSystemsAndNonWebAddressesSkipTheCommand() {
        final Fakes f = new Fakes();
        final BrowserOpener.Result r = f.opener(new OsInfo("haiku", "x64", "")).open(PAGE);
        assertEquals(BrowserOpener.Method.HOST_SERVICES, r.method());
        assertTrue(f.commands.isEmpty());
    }
}
