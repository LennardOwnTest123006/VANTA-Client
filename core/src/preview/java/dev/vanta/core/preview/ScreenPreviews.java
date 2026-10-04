package dev.vanta.core.preview;

import dev.vanta.core.ui.UiScreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Registry of screens rendered by {@link PreviewMain}. Built-in entries are listed here; other agents add their
 * screens either by appending to {@link #builtIn()} or by providing a {@link PreviewProvider} service.
 */
public final class ScreenPreviews {

    /**
     * One preview job.
     *
     * @param name       file name (without extension)
     * @param factory    creates a fresh screen instance
     * @param options    render options
     * @param frameTimes additional timestamps to capture as {@code <name>-t<ms>.png}, or empty
     */
    public record Entry(String name, Supplier<UiScreen> factory, ScreenPreview.Options options, long[] frameTimes) {
        public Entry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(factory, "factory");
            Objects.requireNonNull(options, "options");
            frameTimes = frameTimes == null ? new long[0] : frameTimes.clone();
            if (!name.matches("[a-z0-9][a-z0-9-]*")) {
                throw new IllegalArgumentException("Preview names are kebab-case: " + name);
            }
        }

        /** Entry with standard options and no frame sequence. */
        public static Entry of(String name, Supplier<UiScreen> factory) {
            return new Entry(name, factory, ScreenPreview.Options.standard(), new long[0]);
        }

        /** Entry with custom options. */
        public static Entry of(String name, Supplier<UiScreen> factory, ScreenPreview.Options options) {
            return new Entry(name, factory, options, new long[0]);
        }

        /** Copy that also captures the open transition at the given times. */
        public Entry withFrames(long... times) {
            return new Entry(name, factory, options, times);
        }
    }

    private ScreenPreviews() {
    }

    /** Previews shipped with the UI kit. The gallery renders first. */
    public static List<Entry> builtIn() {
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.of("widget-gallery", WidgetGalleryScreen::new,
                ScreenPreview.Options.standard().size(640, 820).mouseOver(WidgetGalleryScreen.TOOLTIP_TARGET_ID)));
        entries.add(Entry.of("widget-gallery-screen", WidgetGalleryScreen::new,
                ScreenPreview.Options.standard().mouseOver(WidgetGalleryScreen.TOOLTIP_TARGET_ID))
                .withFrames(0L, 100L, 200L));
        entries.add(Entry.of("widget-gallery-bottom", () -> WidgetGalleryScreen.scrolledTo("lists"),
                ScreenPreview.Options.standard().size(640, 820)));
        entries.add(Entry.of("widget-gallery-dialog", WidgetGalleryScreen::withDialog,
                ScreenPreview.Options.standard()));
        entries.add(Entry.of("widget-gallery-select", WidgetGalleryScreen::withSelectOpen,
                ScreenPreview.Options.standard().size(640, 820)));
        entries.add(Entry.of("widget-gallery-high-contrast", WidgetGalleryScreen::new,
                ScreenPreview.Options.standard().size(640, 820).theme(dev.vanta.core.ui.Theme.highContrast())));
        entries.add(Entry.of("widget-gallery-scale-3", WidgetGalleryScreen::new,
                ScreenPreview.Options.standard().size(427, 240).scale(3)));
        // ---- Other agents: add screen previews below this line (or provide a PreviewProvider service). ----
        return entries;
    }

    /** Built-in entries followed by every {@link PreviewProvider} found on the classpath. */
    public static List<Entry> all() {
        List<Entry> entries = new ArrayList<>(builtIn());
        for (PreviewProvider provider : ServiceLoader.load(PreviewProvider.class)) {
            entries.addAll(provider.previews());
        }
        return Collections.unmodifiableList(entries);
    }
}
