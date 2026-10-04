package dev.vanta.core.ui;

import java.util.ArrayList;
import java.util.List;

/** {@link UiHost} that records calls; shared by the UI tests and available to other core test packages. */
public final class TestHost implements UiHost {

    private final List<String> events = new ArrayList<>();
    private String clipboard = "";
    private int clicks;

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
        events.add("setClipboard:" + clipboard);
    }

    @Override
    public String getClipboard() {
        return clipboard;
    }

    @Override
    public void playClick() {
        clicks++;
    }

    @Override
    public void openUrl(String url) {
        events.add("openUrl:" + url);
    }

    @Override
    public void requestLayout() {
        events.add("requestLayout");
    }

    /** Recorded events (excluding clicks). */
    public List<String> events() {
        return List.copyOf(events);
    }

    /** Number of click sounds played. */
    public int clicks() {
        return clicks;
    }

    /** Sets the clipboard silently. */
    public void primeClipboard(String text) {
        clipboard = text;
    }

    /** Whether {@link #closeScreen()} was called. */
    public boolean closed() {
        return events.contains("closeScreen");
    }
}
