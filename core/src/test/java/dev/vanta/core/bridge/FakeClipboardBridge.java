package dev.vanta.core.bridge;

/**
 * In-memory clipboard.
 */
public final class FakeClipboardBridge implements ClipboardBridge {
    private String text = "";

    @Override
    public String get() {
        return text;
    }

    @Override
    public void set(String newText) {
        this.text = newText == null ? "" : newText;
    }
}
