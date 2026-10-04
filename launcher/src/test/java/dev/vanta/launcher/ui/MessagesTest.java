package dev.vanta.launcher.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every string the UI shows comes from the bundle: this test scans the UI sources for message keys and checks that
 * each one exists, that no value is empty and that every pattern is valid {@link MessageFormat} syntax.
 */
class MessagesTest {

    private static final Pattern KEY_CALL = Pattern.compile("\\b(?:t|get|format|has)\\(\"([a-z][A-Za-z0-9_.-]*[A-Za-z0-9])\"");
    private static final Pattern DYNAMIC_PREFIX = Pattern.compile("\\b(?:t|get|format)\\(\"([a-z][A-Za-z0-9_.-]*\\.)\"\\s*\\+");

    @Test
    void everyReferencedKeyExists() throws IOException {
        final Messages m = Messages.english();
        final Path root = Path.of("src/main/java/dev/vanta/launcher/ui");
        assertTrue(Files.isDirectory(root), "test runs from the launcher project directory");
        final TreeSet<String> missing = new TreeSet<>();
        final TreeSet<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                final String source = Files.readString(file, StandardCharsets.UTF_8);
                final Matcher matcher = KEY_CALL.matcher(source);
                while (matcher.find()) {
                    final String key = matcher.group(1);
                    if (!key.contains(".")) {
                        continue;
                    }
                    used.add(key);
                    if (!m.has(key)) {
                        missing.add(file.getFileName() + ": " + key);
                    }
                }
                final Matcher dynamic = DYNAMIC_PREFIX.matcher(source);
                while (dynamic.find()) {
                    final String prefix = dynamic.group(1);
                    assertTrue(m.keys().stream().anyMatch(k -> k.startsWith(prefix)), "no keys for dynamic prefix " + prefix);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing message keys: " + missing);
        assertTrue(used.size() > 150, "the UI should use a substantial part of the bundle, used=" + used.size());
    }

    @Test
    void bundleValuesAreWellFormed() throws IOException {
        final Properties props = new Properties();
        try (var in = Files.newBufferedReader(Path.of("src/main/resources/dev/vanta/launcher/ui/i18n/launcher_en.properties"), StandardCharsets.UTF_8)) {
            props.load(in);
        }
        final List<String> problems = new ArrayList<>();
        for (Map.Entry<Object, Object> e : props.entrySet()) {
            final String key = (String) e.getKey();
            final String value = (String) e.getValue();
            if (value.isBlank()) {
                problems.add(key + " is empty");
            }
            if (value.contains("TODO") || value.toLowerCase(Locale.ROOT).contains("lorem")) {
                problems.add(key + " contains placeholder text");
            }
            try {
                new MessageFormat(value, Locale.ENGLISH);
            } catch (IllegalArgumentException ex) {
                problems.add(key + " is not a valid MessageFormat pattern: " + ex.getMessage());
            }
        }
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void formattingAndFallback() {
        final Messages m = Messages.load(Locale.GERMANY);
        assertEquals(Locale.ENGLISH, m.locale(), "unknown locales fall back to English");
        assertEquals("Step 3 of 9 · Downloading libraries", m.format("home.progress.step", "3", "9", "Downloading libraries"));
        assertEquals("What's new", m.format("update.banner.whatsNew"));
        assertEquals("What's new", m.format("update.banner.whatsNew", new Object[0]));
        assertFalse(m.has("does.not.exist"));
        assertTrue(m.keys().contains("app.title"));
    }
}
