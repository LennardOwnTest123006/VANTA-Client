package dev.vanta.core.ai;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Process} stub standing in for llama-server: alive until destroyed (or told to exit), records destroy
 * calls. {@link Factory} records every command the runtime built.
 */
final class FakeProcess extends Process {
    volatile boolean alive = true;
    volatile int exitCode;
    int destroyCalls;
    int forcibleDestroyCalls;

    /** Factory that hands out fake processes and records the commands. */
    static final class Factory implements LocalAiRuntime.ProcessFactory {
        final List<List<String>> commands = new ArrayList<>();
        final List<Path> logFiles = new ArrayList<>();
        final List<FakeProcess> processes = new ArrayList<>();
        /** When set, the next process exits immediately with this code. */
        Integer exitImmediatelyWith;
        /** When set, start() throws. */
        java.io.IOException failWith;

        @Override
        public Process start(List<String> command, Path workingDir, Path logFile) throws java.io.IOException {
            if (failWith != null) {
                throw failWith;
            }
            commands.add(List.copyOf(command));
            logFiles.add(logFile);
            FakeProcess process = new FakeProcess();
            if (exitImmediatelyWith != null) {
                process.alive = false;
                process.exitCode = exitImmediatelyWith;
            }
            processes.add(process);
            return process;
        }

        FakeProcess last() {
            return processes.get(processes.size() - 1);
        }
    }

    @Override
    public OutputStream getOutputStream() {
        return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() {
        return exitCode;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
        return !alive;
    }

    @Override
    public int exitValue() {
        if (alive) {
            throw new IllegalThreadStateException("still running");
        }
        return exitCode;
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public void destroy() {
        destroyCalls++;
        alive = false;
    }

    @Override
    public Process destroyForcibly() {
        forcibleDestroyCalls++;
        alive = false;
        return this;
    }
}
