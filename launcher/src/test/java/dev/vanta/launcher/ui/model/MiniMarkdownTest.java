package dev.vanta.launcher.ui.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniMarkdownTest {

    @Test
    void parsesReleaseNotes() {
        final String md = """
            ---
            product: launcher
            version: 1.1.0
            ---

            # VANTA Launcher 1.1.0

            First **public** release with [docs](https://example.com).

            ## Added

            - Installs Minecraft `1.21.11`
              with verification
            * Microsoft sign-in
            ### Notes
            ```
            java -jar launcher.jar
            ```
            Trailing paragraph
            spanning two lines.
            """;
        final List<MiniMarkdown.Block> blocks = MiniMarkdown.parse(md);
        assertEquals(MiniMarkdown.Kind.HEADING_1, blocks.get(0).kind());
        assertEquals("VANTA Launcher 1.1.0", blocks.get(0).text());
        assertEquals(MiniMarkdown.Kind.PARAGRAPH, blocks.get(1).kind());
        assertEquals("First public release with docs.", blocks.get(1).text());
        assertEquals(MiniMarkdown.Kind.HEADING_2, blocks.get(2).kind());
        assertEquals(MiniMarkdown.Kind.BULLET, blocks.get(3).kind());
        assertEquals("Installs Minecraft 1.21.11 with verification", blocks.get(3).text());
        assertEquals(MiniMarkdown.Kind.BULLET, blocks.get(4).kind());
        assertEquals("Microsoft sign-in", blocks.get(4).text());
        assertEquals(MiniMarkdown.Kind.HEADING_3, blocks.get(5).kind());
        assertEquals(MiniMarkdown.Kind.CODE, blocks.get(6).kind());
        assertEquals("java -jar launcher.jar", blocks.get(6).text());
        assertEquals("Trailing paragraph spanning two lines.", blocks.get(7).text());
        assertEquals(8, blocks.size());
    }

    @Test
    void emptyAndInline() {
        assertTrue(MiniMarkdown.parse("").isEmpty());
        assertTrue(MiniMarkdown.parse(null).isEmpty());
        assertEquals("bold and em and code", MiniMarkdown.inline("**bold** and _em_ and `code`"));
        assertEquals("snake_case_name stays", MiniMarkdown.inline("snake_case_name stays"));
        assertEquals(List.of(new MiniMarkdown.Block(MiniMarkdown.Kind.PARAGRAPH, "Just text")), MiniMarkdown.parse("Just text"));
    }
}
