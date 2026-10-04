package dev.vanta.core.bridge;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Screenshot capture, implemented by the client with the vanilla {@code Screenshot} helper.
 */
public interface ScreenshotBridge {

    /** Screenshot directory when it exists. */
    Optional<Path> screenshotsDir();

    /**
     * Captures the current frame on the next render pass.
     *
     * @param hideHud  true to hide the VANTA HUD and vanilla GUI for the capture
     * @param onSaved  called on the render thread with the saved file, or empty when the capture failed
     */
    void capture(boolean hideHud, Consumer<Optional<Path>> onSaved);

    /** Opens the screenshot directory in the system file browser. */
    void openScreenshotsFolder();
}
