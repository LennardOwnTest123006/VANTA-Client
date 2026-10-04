package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Keeps the Java font metrics and the Minecraft font provider JSON files in sync. */
class FontKindTest {

    private static JsonObject providerJson(String fontId) throws IOException {
        String path = "/assets/" + fontId.replace(':', '/').replaceFirst("/", "/font/") + ".json";
        try (InputStream in = FontKindTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing font provider json " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void vantaFontsHaveMatchingProviderJson() throws IOException {
        for (FontKind kind : new FontKind[] {FontKind.UI, FontKind.UI_BOLD, FontKind.DISPLAY}) {
            JsonObject json = providerJson(kind.fontId());
            JsonArray providers = json.getAsJsonArray("providers");
            JsonObject ttf = providers.get(0).getAsJsonObject();
            assertEquals("ttf", ttf.get("type").getAsString());
            assertEquals(kind.size(), ttf.get("size").getAsFloat(), 1e-6f, kind + " size");
            assertEquals(kind.baselineShift(), ttf.getAsJsonArray("shift").get(1).getAsFloat(), 1e-6f, kind + " shift");
            String file = ttf.get("file").getAsString();
            String resource = "/assets/" + file.replace(':', '/').replaceFirst("/", "/font/");
            try (InputStream ttfStream = FontKindTest.class.getResourceAsStream(resource)) {
                assertNotNull(ttfStream, "bundled TTF missing: " + resource);
            }
            JsonObject fallback = providers.get(providers.size() - 1).getAsJsonObject();
            assertEquals("reference", fallback.get("type").getAsString(), "vanilla glyph fallback");
            assertEquals("minecraft:default", fallback.get("id").getAsString());
        }
    }

    @Test
    void minecraftKindMatchesVanillaMetrics() {
        assertEquals("minecraft:default", FontKind.MINECRAFT.fontId());
        assertEquals(9, FontKind.MINECRAFT.lineHeight());
        assertEquals(9, FontKind.UI.lineHeight());
        assertTrue(FontKind.UI.isBody());
        assertTrue(FontKind.UI_BOLD.isBody());
        assertEquals(14, FontKind.DISPLAY.lineHeight());
    }
}
