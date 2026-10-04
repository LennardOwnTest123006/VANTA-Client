package dev.vanta.launcher.core.install;

/**
 * Progress snapshot of an installation.
 *
 * @param step      current step
 * @param stepIndex 0-based index of the step within the plan
 * @param stepCount number of steps in the plan
 * @param done      completed units in the current step (files)
 * @param total     total units in the current step (files), {@code 0} when unknown
 * @param bytes     bytes transferred so far in this installation
 * @param message   human readable detail (e.g. current file)
 */
public record InstallProgress(InstallStep step, int stepIndex, int stepCount, long done, long total, long bytes, String message) {

    public InstallProgress {
        message = message == null ? step.label() : message;
    }

    /** @return overall fraction in [0,1] (steps weighted equally, current step by its file count) */
    public double fraction() {
        if (stepCount <= 0) {
            return 0d;
        }
        final double within = total > 0 ? Math.min(1d, (double) done / (double) total) : 0d;
        return Math.min(1d, (stepIndex + within) / stepCount);
    }
}
