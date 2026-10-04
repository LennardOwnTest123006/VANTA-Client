package dev.vanta.core.ui;

/**
 * Services the environment provides to screens: the client implements it on top of Minecraft, previews and
 * tests use {@link #NONE} or a recording implementation.
 */
public interface UiHost {

    /** Host that does nothing; clipboard reads return an empty string. */
    UiHost NONE = new UiHost() {
        private String clipboard = "";

        @Override
        public void closeScreen() {
        }

        @Override
        public void openScreen(Object screenId) {
        }

        @Override
        public void setClipboard(String text) {
            clipboard = text == null ? "" : text;
        }

        @Override
        public String getClipboard() {
            return clipboard;
        }

        @Override
        public void playClick() {
        }

        @Override
        public void openUrl(String url) {
        }

        @Override
        public void requestLayout() {
        }
    };

    /** Closes the current screen (after the screen's own close transition has finished). */
    void closeScreen();

    /** Opens another VANTA or vanilla screen identified by the domain layer's screen id. */
    void openScreen(Object screenId);

    /** Puts text on the system clipboard. */
    void setClipboard(String text);

    /** Reads the system clipboard; never {@code null}. */
    String getClipboard();

    /** Plays the UI click sound. */
    void playClick();

    /** Opens a URL in the system browser (the host may show a confirmation first). */
    void openUrl(String url);

    /** Asks the host to re-run layout on the next frame (e.g. after the window size changed). */
    void requestLayout();
}
