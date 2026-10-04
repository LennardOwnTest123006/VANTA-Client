package dev.vanta.launcher.ui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimal Markdown reader for release notes: ATX headings, bullet lists, fenced code and paragraphs. Inline
 * emphasis markers are stripped so the text reads cleanly in plain labels. YAML front matter is skipped.
 */
public final class MiniMarkdown {

    /** Block types. */
    public enum Kind {
        /** {@code # Heading}. */
        HEADING_1,
        /** {@code ## Heading}. */
        HEADING_2,
        /** {@code ### Heading} and deeper. */
        HEADING_3,
        /** Paragraph text. */
        PARAGRAPH,
        /** List item (one block per item). */
        BULLET,
        /** Fenced code block. */
        CODE
    }

    /**
     * A rendered block.
     *
     * @param kind kind
     * @param text text with inline markers removed (code keeps its content verbatim)
     */
    public record Block(Kind kind, String text) {
        public Block {
            Objects.requireNonNull(kind, "kind");
            text = text == null ? "" : text;
        }
    }

    private MiniMarkdown() {
    }

    /**
     * Parses markdown into blocks.
     *
     * @param markdown text
     * @return blocks in order
     */
    public static List<Block> parse(final String markdown) {
        final List<Block> blocks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return blocks;
        }
        final String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        int i = 0;
        if (lines.length > 0 && lines[0].trim().equals("---")) {
            i = 1;
            while (i < lines.length && !lines[i].trim().equals("---")) {
                i++;
            }
            i = Math.min(lines.length, i + 1);
        }
        final StringBuilder paragraph = new StringBuilder();
        while (i < lines.length) {
            final String raw = lines[i];
            final String line = raw.strip();
            if (line.startsWith("```")) {
                flush(paragraph, blocks);
                final StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].strip().startsWith("```")) {
                    if (code.length() > 0) {
                        code.append('\n');
                    }
                    code.append(lines[i]);
                    i++;
                }
                blocks.add(new Block(Kind.CODE, code.toString()));
                i++;
                continue;
            }
            if (line.isEmpty()) {
                flush(paragraph, blocks);
                i++;
                continue;
            }
            if (line.startsWith("#")) {
                flush(paragraph, blocks);
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                final String text = inline(line.substring(level).trim());
                blocks.add(new Block(level == 1 ? Kind.HEADING_1 : level == 2 ? Kind.HEADING_2 : Kind.HEADING_3, text));
                i++;
                continue;
            }
            if (isBullet(line)) {
                flush(paragraph, blocks);
                final StringBuilder item = new StringBuilder(inline(line.substring(2).trim()));
                i++;
                while (i < lines.length && !lines[i].isBlank() && !isBullet(lines[i].strip()) && !lines[i].strip().startsWith("#")
                    && (lines[i].startsWith("  ") || lines[i].startsWith("\t"))) {
                    item.append(' ').append(inline(lines[i].strip()));
                    i++;
                }
                blocks.add(new Block(Kind.BULLET, item.toString()));
                continue;
            }
            if (paragraph.length() > 0) {
                paragraph.append(' ');
            }
            paragraph.append(inline(line));
            i++;
        }
        flush(paragraph, blocks);
        return blocks;
    }

    private static boolean isBullet(final String line) {
        return (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) && line.length() > 2;
    }

    private static void flush(final StringBuilder paragraph, final List<Block> blocks) {
        if (paragraph.length() > 0) {
            blocks.add(new Block(Kind.PARAGRAPH, paragraph.toString()));
            paragraph.setLength(0);
        }
    }

    /**
     * Strips inline markers: {@code **bold**}, {@code *em*}, {@code _em_}, {@code `code`}, {@code [text](url)}.
     *
     * @param text text
     * @return plain text
     */
    public static String inline(final String text) {
        String s = text;
        s = s.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        s = s.replaceAll("__([^_]+)__", "$1");
        s = s.replaceAll("(?<![A-Za-z0-9])\\*([^*]+)\\*(?![A-Za-z0-9])", "$1");
        s = s.replaceAll("(?<![A-Za-z0-9])_([^_]+)_(?![A-Za-z0-9])", "$1");
        s = s.replace("`", "");
        return s.trim();
    }
}
