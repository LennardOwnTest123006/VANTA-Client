package dev.vanta.core.preview;

import dev.vanta.core.ui.UiHost;

import java.util.ArrayList;
import java.util.List;

/** Host for previews: records navigation and clipboard calls, plays no sounds. */
public final class PreviewHost implements UiHost {

    private final List<String> events = new ArrayList<>();
    private String clipboard = "";

    @Override
    public void closeScreen() {
        events.add("closeScreen");
    }

    @Override
    public void openScreen(Object screenId) {
        events.add("openScreen:" + screenId);
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
        events.add("openUrl:" + url);
    }

    @Override
    public void requestLayout() {
    }

    /** Recorded host calls. */
    public List<String> events() {
        return List.copyOf(events);
    }
}
