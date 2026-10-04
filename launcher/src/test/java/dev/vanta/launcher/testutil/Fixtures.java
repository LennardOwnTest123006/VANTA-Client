package dev.vanta.launcher.testutil;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads recorded fixtures from {@code src/test/resources/fixtures}.
 */
public final class Fixtures {

    private Fixtures() {
    }

    /**
     * @param name path below {@code fixtures/}
     * @return fixture text
     */
    public static String read(final String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param name path below {@code fixtures/}
     * @return fixture as JSON tree
     */
    public static JsonElement json(final String name) {
        return JsonParser.parseString(read(name));
    }

    /**
     * @param name path below {@code fixtures/}
     * @return fixture bytes
     */
    public static byte[] bytes(final String name) {
        return read(name).getBytes(StandardCharsets.UTF_8);
    }
}
