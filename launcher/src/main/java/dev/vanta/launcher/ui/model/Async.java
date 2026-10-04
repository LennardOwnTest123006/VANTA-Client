package dev.vanta.launcher.ui.model;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Runs blocking core work on the background executor and delivers the outcome on the UI executor.
 *
 * <p>A cancelled future (via {@link Future#cancel(boolean)}) interrupts the worker; the failure callback then
 * receives a {@link CancellationException} or {@link InterruptedException} so view models can treat both the same.</p>
 */
public final class Async {

    private Async() {
    }

    /**
     * @param executors executors
     * @param work      blocking work
     * @param onSuccess receives the result on the UI thread
     * @param onFailure receives the failure on the UI thread
     * @param <T>       result type
     * @return the future (cancel it to interrupt the work)
     */
    public static <T> Future<?> run(final UiExecutors executors, final Callable<T> work, final Consumer<T> onSuccess,
                                    final Consumer<Throwable> onFailure) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");
        try {
            return executors.background().submit(() -> {
                try {
                    final T result = work.call();
                    executors.onUi(() -> onSuccess.accept(result));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    executors.onUi(() -> onFailure.accept(e));
                } catch (Throwable t) {
                    executors.onUi(() -> onFailure.accept(t));
                }
            });
        } catch (RejectedExecutionException e) {
            // The application is shutting down: there is nobody left to deliver a result to.
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Fire-and-forget variant without result.
     *
     * @param executors executors
     * @param work      work
     * @param onDone    runs on the UI thread after success
     * @param onFailure receives failures on the UI thread
     * @return future
     */
    public static Future<?> run(final UiExecutors executors, final ThrowingRunnable work, final Runnable onDone,
                                final Consumer<Throwable> onFailure) {
        return run(executors, () -> {
            work.run();
            return null;
        }, ignored -> onDone.run(), onFailure);
    }

    /**
     * @param t throwable
     * @return whether the throwable represents a cancellation or interruption
     */
    public static boolean isCancellation(final Throwable t) {
        return t instanceof CancellationException || t instanceof InterruptedException;
    }

    /** Work without a result that may throw. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        /**
         * Runs the work.
         *
         * @throws Exception on failure
         */
        void run() throws Exception;
    }
}
