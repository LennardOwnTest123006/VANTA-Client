package dev.vanta.launcher.ui.testutil;

import dev.vanta.launcher.ui.Messages;
import dev.vanta.launcher.ui.model.Formats;
import dev.vanta.launcher.ui.model.HomeViewModel;
import dev.vanta.launcher.ui.model.LogBuffer;
import dev.vanta.launcher.ui.model.SessionModel;
import dev.vanta.launcher.ui.model.ToastModel;
import dev.vanta.launcher.ui.model.UiExecutors;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Wires a {@link FakeBackend} with synchronous executors and the real English bundle for view model tests.
 */
public final class TestContext {

    /** Backend. */
    public final FakeBackend backend;
    /** Synchronous executors. */
    public final UiExecutors executors = UiExecutors.direct();
    /** English messages. */
    public final Messages messages = Messages.english();
    /** Formats in UTC. */
    public final Formats formats = new Formats(messages, ZoneOffset.UTC);
    /** Toasts. */
    public final ToastModel toasts = new ToastModel();
    /** Launcher log. */
    public final LogBuffer launcherLog = new LogBuffer(200, FakeBackend.CLOCK);
    /** Game log. */
    public final LogBuffer gameLog = new LogBuffer(200, FakeBackend.CLOCK);
    /** Errors reported by the session. */
    public final List<Throwable> sessionErrors = new ArrayList<>();
    /** Session. */
    public final SessionModel session;

    /**
     * @param dataDir temp directory
     */
    public TestContext(final Path dataDir) {
        backend = new FakeBackend(dataDir);
        session = new SessionModel(backend, executors, sessionErrors::add);
    }

    /** @return a home view model on this context */
    public HomeViewModel home() {
        return new HomeViewModel(session, backend, executors, messages, formats, toasts, launcherLog, gameLog);
    }
}
