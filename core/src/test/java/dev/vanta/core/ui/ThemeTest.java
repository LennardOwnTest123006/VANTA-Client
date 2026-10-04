package dev.vanta.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class ThemeTest {

    @Test
    void defaultMatchesCanonicalTokenFile() throws IOException {
        Path tokens = Path.of("..", "shared", "design", "tokens.json");
        assumeTrue(Files.isRegularFile(tokens), "shared/design/tokens.json not available");
        Theme fromFile;
        try (Reader reader = Files.newBufferedReader(tokens, StandardCharsets.UTF_8)) {
            fromFile = Theme.fromTokensJson(reader);
        }
        Map<String, Integer> expected = fromFile.tokens();
        Map<String, Integer> actual = Theme.DEFAULT.tokens();
        for (String name : Theme.tokenNames()) {
            assertEquals(Colors.toHex(expected.get(name)), Colors.toHex(actual.get(name)), "token " + name);
        }
    }

    @Test
    void parsesCosmeticThemeShape() {
        String json = "{\"id\":\"midnight\",\"name\":\"Midnight\",\"colors\":{\"accent.violet\":\"#FF0000\","
                + "\"surface.1\":\"#101010\",\"unknown.token\":\"#123456\"}}";
        Theme t = Theme.fromTokensJson(new StringReader(json));
        assertEquals("midnight", t.id());
        assertEquals("Midnight", t.name());
        assertEquals(0xFFFF0000, t.accent());
        assertEquals(0xFF101010, t.surface1());
        assertEquals(Theme.DEFAULT.accentBlue(), t.accentBlue(), "missing tokens keep defaults");
        assertFalse(t.tokens().containsKey("unknown.token"));
    }

    @Test
    void jsonColorsUseCssAlphaOrder() {
        Theme t = Theme.fromTokensJson(new StringReader("{\"colors\":{\"accent.violetSoft\":\"#7C5CFF80\"}}"));
        assertEquals(0x807C5CFF, t.accentSoft());
    }

    @Test
    void parsesPartialCanonicalShape() {
        String json = "{\"color\":{\"state\":{\"danger\":\"#AA0000\"},\"gradient\":{\"accent\":{\"stops\":[\"#111111\",\"#222222\"]}}}}";
        Theme t = Theme.fromTokensJson(new StringReader(json));
        assertEquals(0xFFAA0000, t.danger());
        assertEquals(0xFF111111, t.gradientStart());
        assertEquals(0xFF222222, t.gradientEnd());
        assertEquals("custom", t.id());
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(JsonParseException.class, () -> Theme.fromTokensJson(new StringReader("[1,2]")));
        assertThrows(JsonParseException.class,
                () -> Theme.fromTokensJson(new StringReader("{\"colors\":{\"accent.violet\":\"#12G\"}}")));
        assertThrows(JsonParseException.class, () -> Theme.fromTokensJson(new StringReader("not json")));
    }

    @Test
    void overridesIgnoreUnknownNames() {
        Theme t = Theme.DEFAULT.withOverrides(Map.of("text.primary", 0xFF123456, "nope", 1));
        assertEquals(0xFF123456, t.textPrimary());
        assertEquals(Theme.DEFAULT.tokens().size(), t.tokens().size());
        assertThrows(IllegalArgumentException.class, () -> t.token("nope"));
        assertEquals(0xFF123456, t.token("text.primary"));
    }

    @Test
    void highContrastBrightensBordersAndText() {
        Theme hc = Theme.highContrast();
        assertTrue(hc.isHighContrast());
        assertEquals(0xFFFFFFFF, hc.textPrimary());
        assertTrue(Colors.luminance(hc.borderSubtle()) > Colors.luminance(Theme.DEFAULT.borderSubtle()));
        assertEquals(1f, hc.panelAlpha(), 1e-6f);
        assertEquals(Theme.DEFAULT.accent(), hc.accent(), "accent stays on brand");
        assertFalse(hc.withHighContrast(false).isHighContrast());
    }

    @Test
    void flagsAndScale() {
        Theme t = Theme.DEFAULT.withScale(1.5f).withReducedMotion(true).withReducedTransparency(true).withLargeText(true);
        assertEquals(1.5f, t.scale(), 1e-6f);
        assertEquals(1.5f * 1.15f, t.effectiveScale(), 1e-5f);
        assertTrue(t.reducedMotion());
        assertTrue(t.reducedTransparency());
        assertFalse(t.wantsBlur());
        assertEquals(1f, t.panelAlpha(), 1e-6f);
        assertEquals(255, Colors.alpha(t.screenDim()));
        assertEquals(0.5f, Theme.DEFAULT.withScale(0.1f).scale(), 1e-6f);
        assertEquals(3f, Theme.DEFAULT.withScale(9f).scale(), 1e-6f);
        assertEquals(Theme.PANEL_ALPHA, Theme.DEFAULT.panelAlpha(), 1e-6f);
        assertNotEquals(Theme.DEFAULT, t);
        assertEquals(Theme.DEFAULT, Theme.DEFAULT.withOverrides(Map.of()));
    }

    @Test
    void tokenNamesCoverEveryAccessor() {
        Theme d = Theme.DEFAULT;
        assertEquals(0xFF07070A, d.bgVoid());
        assertEquals(0xFF0B0B10, d.bgBase());
        assertEquals(0xFF7C5CFF, d.accent());
        assertEquals(0xFF9B82FF, d.accentHover());
        assertEquals(0xFF5B3DE0, d.accentPressed());
        assertEquals(0xFF4F8DFF, d.accentBlue());
        assertEquals(0xFF3DDC97, d.success());
        assertEquals(0xFFF5B942, d.warning());
        assertEquals(0xFFFF5C7A, d.danger());
        assertEquals(0xFF4F8DFF, d.info());
        assertEquals(0xFF9B82FF, d.borderFocus());
        assertEquals(25, Theme.tokenNames().length);
    }
}
