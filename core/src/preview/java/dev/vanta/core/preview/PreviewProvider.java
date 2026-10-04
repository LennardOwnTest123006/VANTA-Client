package dev.vanta.core.preview;

import java.util.List;

/**
 * Service interface other modules implement to contribute previews. Register implementations in
 * {@code META-INF/services/dev.vanta.core.preview.PreviewProvider} inside the preview source set.
 */
public interface PreviewProvider {

    /** The previews this provider contributes. */
    List<ScreenPreviews.Entry> previews();
}
