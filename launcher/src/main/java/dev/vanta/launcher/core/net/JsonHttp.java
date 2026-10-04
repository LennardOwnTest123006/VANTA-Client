package dev.vanta.launcher.core.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.vanta.launcher.core.util.Json;

import java.io.IOException;
import java.net.URI;

/**
 * Small helper for JSON APIs on top of a {@link Downloader} (retries) or raw transport.
 */
public final class JsonHttp {

    private final Downloader downloader;

    /**
     * @param downloader downloader used for GET requests
     */
    public JsonHttp(final Downloader downloader) {
        this.downloader = downloader;
    }

    /**
     * GETs and parses a JSON document.
     *
     * @param url  URL
     * @param type target type
     * @param <T>  target type
     * @return parsed value
     * @throws IOException          on transport failure or malformed JSON
     * @throws InterruptedException when interrupted
     */
    public <T> T get(final URI url, final Class<T> type) throws IOException, InterruptedException {
        final String text = downloader.fetchString(url);
        try {
            return Json.parse(text, type);
        } catch (JsonParseException e) {
            throw new IOException("Unexpected response from " + url + ": " + e.getMessage(), e);
        }
    }

    /**
     * GETs and parses a JSON document with a generic type.
     *
     * @param url  URL
     * @param type target type token
     * @param <T>  target type
     * @return parsed value
     * @throws IOException          on transport failure or malformed JSON
     * @throws InterruptedException when interrupted
     */
    public <T> T get(final URI url, final TypeToken<T> type) throws IOException, InterruptedException {
        final String text = downloader.fetchString(url);
        try {
            final T value = Json.GSON.fromJson(text, type.getType());
            if (value == null) {
                throw new IOException("Empty response from " + url);
            }
            return value;
        } catch (JsonParseException e) {
            throw new IOException("Unexpected response from " + url + ": " + e.getMessage(), e);
        }
    }

    /**
     * GETs a JSON tree.
     *
     * @param url URL
     * @return element
     * @throws IOException          on failure
     * @throws InterruptedException when interrupted
     */
    public JsonElement getTree(final URI url) throws IOException, InterruptedException {
        final String text = downloader.fetchString(url);
        try {
            return Json.tree(text);
        } catch (JsonParseException e) {
            throw new IOException("Unexpected response from " + url + ": " + e.getMessage(), e);
        }
    }
}
