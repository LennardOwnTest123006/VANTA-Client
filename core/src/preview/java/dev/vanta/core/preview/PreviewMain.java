package dev.vanta.core.preview;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command line entry point of the {@code previewScreens} Gradle task: renders every registered screen to
 * {@code <outDir>/<name>.png}.
 * <p>
 * Usage: {@code PreviewMain <outDir> [--only <name>]}.
 */
public final class PreviewMain {

    private PreviewMain() {
    }

    /** Renders all (or the selected) previews; exits non-zero when any preview fails. */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: PreviewMain <outDir> [--only <name>]");
            System.exit(2);
            return;
        }
        Path outDir = Path.of(args[0]);
        String only = null;
        for (int i = 1; i < args.length - 1; i++) {
            if ("--only".equals(args[i])) {
                only = args[i + 1];
            }
        }
        List<Path> written = render(outDir, only);
        System.out.println("Rendered " + written.size() + " preview image(s) to " + outDir.toAbsolutePath());
        for (Path p : written) {
            System.out.println("  " + p.getFileName());
        }
    }

    /** Renders previews and returns the written files. */
    public static List<Path> render(Path outDir, String only) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Files.createDirectories(outDir);
        List<Path> written = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (ScreenPreviews.Entry entry : ScreenPreviews.all()) {
            if (only != null && !only.equals(entry.name())) {
                continue;
            }
            try {
                BufferedImage settled = ScreenPreview.render(entry.factory().get(), entry.options());
                written.add(ScreenPreview.save(settled, outDir.resolve(entry.name() + ".png")));
                if (entry.frameTimes().length > 0) {
                    List<BufferedImage> frames = ScreenPreview.frames(entry.factory().get(), entry.options(),
                            entry.frameTimes());
                    for (int i = 0; i < frames.size(); i++) {
                        String name = entry.name() + "-t" + entry.frameTimes()[i] + ".png";
                        written.add(ScreenPreview.save(frames.get(i), outDir.resolve(name)));
                    }
                }
            } catch (RuntimeException e) {
                failures.add(entry.name() + ": " + e);
                e.printStackTrace(System.err);
            }
        }
        if (!failures.isEmpty()) {
            throw new IOException("Preview rendering failed for: " + failures);
        }
        return written;
    }
}
