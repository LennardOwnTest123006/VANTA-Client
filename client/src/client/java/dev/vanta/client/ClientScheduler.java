package dev.vanta.client;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tiny task queue drained at the end of every client tick.
 * <p>
 * Used for work that must not run inside the callback that requested it, for example opening a VANTA screen from a
 * chat command: the chat screen closes itself <em>after</em> the command executed and would otherwise replace the
 * screen we just opened.
 */
public final class ClientScheduler {
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    /** Runs {@code task} on the render thread at the end of the next client tick. */
    public void nextTick(Runnable task) {
        tasks.add(Objects.requireNonNull(task, "task"));
    }

    /** Executes every queued task (called from the end-of-tick event). */
    public void runPending() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    /** Number of tasks waiting. */
    public int pendingCount() {
        return tasks.size();
    }
}
