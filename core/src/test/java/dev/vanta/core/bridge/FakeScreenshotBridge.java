package dev.vanta.core.bridge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@link ScreenshotBridge} that "saves" to a configurable path without touching the disk.
 */
public final class FakeScreenshotBridge implements ScreenshotBridge {
    public Optional<Path> dir = Optional.of(Path.of("screenshots"));
    public Optional<Path> nextResult = Optional.of(Path.of("screenshots", "2026-10-04_12.00.00.png"));
    public final List<String> actions = new ArrayList<>();

    @Override
    public Optional<Path> screenshotsDir() {
        return dir;
    }

    @Override
    public void capture(boolean hideHud, Consumer<Optional<Path>> onSaved) {
        actions.add("capture:" + hideHud);
        onSaved.accept(nextResult);
    }

    @Override
    public void openScreenshotsFolder() {
        actions.add("openFolder");
    }
}
