package dev.vanta.launcher.core.install;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The steps of an installation and the bytes still to download. Computed after the version JSON is known so the
 * disk space check happens before any large transfer.
 *
 * @param steps          steps in order
 * @param remainingBytes bytes that still have to be downloaded (verified files excluded)
 */
public record InstallPlan(List<InstallStep> steps, long remainingBytes) {

    /** Safety margin added on top of the estimate (temporary files, logs, world saves). */
    public static final long SAFETY_MARGIN_BYTES = 512L * 1024L * 1024L;

    public InstallPlan {
        steps = List.copyOf(steps);
        remainingBytes = Math.max(0L, remainingBytes);
    }

    /**
     * Builds the step list for a request.
     *
     * @param request request
     * @return steps
     */
    public static List<InstallStep> stepsFor(final InstallRequest request) {
        final java.util.ArrayList<InstallStep> steps = new java.util.ArrayList<>();
        steps.add(InstallStep.MANIFEST);
        steps.add(InstallStep.VERSION_JSON);
        steps.add(InstallStep.CLIENT_JAR);
        steps.add(InstallStep.LIBRARIES);
        if (request.includeAssets()) {
            steps.add(InstallStep.ASSETS);
        }
        steps.add(InstallStep.FABRIC_PROFILE);
        steps.add(InstallStep.FABRIC_LIBRARIES);
        steps.add(InstallStep.FABRIC_API);
        if (request.includeVantaClient()) {
            steps.add(InstallStep.VANTA_CLIENT);
        }
        if (request.includePerformancePack()) {
            steps.add(InstallStep.PERFORMANCE_PACK);
        }
        if (request.includeLocalAi()) {
            steps.add(InstallStep.LOCAL_AI);
        }
        steps.add(InstallStep.FINALIZE);
        return steps;
    }

    /** @return bytes required including the safety margin */
    public long requiredBytes() {
        return remainingBytes + SAFETY_MARGIN_BYTES;
    }

    /**
     * Verifies that the file store of a directory has room for this plan.
     *
     * @param directory directory on the target file store (created when missing)
     * @throws InsufficientDiskSpaceException when space is short
     * @throws IOException                    when the file store cannot be queried
     */
    public void checkDiskSpace(final Path directory) throws IOException {
        Files.createDirectories(directory);
        final FileStore store = Files.getFileStore(directory);
        final long usable = store.getUsableSpace();
        if (usable >= 0 && usable < requiredBytes()) {
            throw new InsufficientDiskSpaceException(requiredBytes(), usable);
        }
    }
}
