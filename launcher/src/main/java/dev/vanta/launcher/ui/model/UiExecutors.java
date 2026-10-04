package dev.vanta.launcher.ui.model;

import javafx.application.Platform;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The two executors every view model needs: a background pool for blocking core calls and a UI executor that
 * marshals results back to the JavaFX application thread. Tests use {@link #direct()} so everything runs inline.
 */
public final class UiExecutors {

    private final ExecutorService background;
    private final Executor ui;

    /**
     * @param background background executor (blocking core work)
     * @param ui         executor that runs on the UI thread
     */
    public UiExecutors(final ExecutorService background, final Executor ui) {
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    /**
     * Production executors: daemon worker threads and {@link Platform#runLater(Runnable)}.
     *
     * @return executors
     */
    public static UiExecutors javafx() {
        final AtomicInteger counter = new AtomicInteger();
        final ThreadFactory factory = r -> {
            final Thread t = new Thread(r, "vanta-ui-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return new UiExecutors(Executors.newCachedThreadPool(factory), UiExecutors::runOnFx);
    }

    /**
     * Executors that run every task immediately on the calling thread (unit tests, screenshots setup).
     *
     * @return executors
     */
    public static UiExecutors direct() {
        return new UiExecutors(new DirectExecutorService(), Runnable::run);
    }

    /** @return background executor */
    public ExecutorService background() {
        return background;
    }

    /** @return UI executor */
    public Executor ui() {
        return ui;
    }

    /**
     * Runs on the UI thread.
     *
     * @param task task
     */
    public void onUi(final Runnable task) {
        ui.execute(task);
    }

    /** Stops the background pool. */
    public void shutdown() {
        background.shutdownNow();
    }

    private static void runOnFx(final Runnable task) {
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    /** Executor service that runs tasks synchronously. */
    static final class DirectExecutorService extends AbstractExecutorService {

        private volatile boolean shutdown;

        @Override
        public void execute(final Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) {
            return true;
        }
    }
}
