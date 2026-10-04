package dev.vanta.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Tolerant, atomic JSON persistence shared by every VANTA store.
 * <ul>
 *   <li>Writes are atomic: the content goes to {@code <file>.tmp} and is then moved over the target.</li>
 *   <li>Reads are tolerant: a missing file yields {@link Optional#empty()}; a corrupt file is moved aside as
 *       {@code <name>.broken-<epochMillis>.json} and also yields empty, so a bad file never crashes the game and the
 *       user keeps a copy of it.</li>
 *   <li>Every object written carries a {@code schemaVersion} field (see {@link Migrations}).</li>
 * </ul>
 */
public final class JsonStore {
    /** Name of the version field present in every store file. */
    public static final String SCHEMA_VERSION = "schemaVersion";

    private final Gson gson;
    private final Clock clock;

    /**
     * @param clock used for the timestamps of backup file names
     */
    public JsonStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    }

    /** The shared Gson instance (pretty printing, no HTML escaping). */
    public Gson gson() {
        return gson;
    }

    /**
     * Reads a JSON object. Missing → empty. Corrupt or not an object → backed up and empty.
     */
    public Optional<JsonObject> readObject(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                CoreLog.warn("{} does not contain a JSON object; moving it aside", file.getFileName());
                backup(file);
                return Optional.empty();
            }
            return Optional.of(element.getAsJsonObject());
        } catch (JsonParseException | IllegalStateException e) {
            CoreLog.warn(e, "{} is corrupt; moving it aside", file.getFileName());
            backup(file);
            return Optional.empty();
        } catch (IOException e) {
            CoreLog.warn(e, "Could not read {}", file);
            return Optional.empty();
        }
    }

    /**
     * Reads a JSON object and applies {@code migrations}. When the file was migrated it is written back immediately so
     * the upgrade happens once.
     */
    public Optional<JsonObject> readMigrated(Path file, Migrations migrations) {
        Optional<JsonObject> raw = readObject(file);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            Migrations.Result result = migrations.migrate(raw.get());
            if (result.status() == Migrations.Status.NEWER_THAN_SUPPORTED) {
                CoreLog.warn("{} was written by a newer VANTA version (schema {} > {}); reading it tolerantly",
                        file.getFileName(), result.fromVersion(), migrations.currentVersion());
            } else if (result.changed()) {
                CoreLog.info("Migrated {} from schema {} to {}", file.getFileName(), result.fromVersion(),
                        result.toVersion());
                writeObject(file, result.json());
            }
            return Optional.of(result.json());
        } catch (RuntimeException e) {
            CoreLog.warn(e, "Migration of {} failed; moving it aside", file.getFileName());
            backup(file);
            return Optional.empty();
        }
    }

    /**
     * Atomically writes {@code json} to {@code file}, creating parent directories.
     *
     * @throws UncheckedIOException when the file cannot be written
     */
    public void writeObject(Path file, JsonObject json) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(json, "json");
        writeText(file, gson.toJson(json) + System.lineSeparator());
    }

    /** Reads and deserialises a value with Gson; same tolerance rules as {@link #readObject(Path)}. */
    public <T> Optional<T> read(Path file, Class<T> type) {
        return readObject(file).flatMap(obj -> {
            try {
                return Optional.ofNullable(gson.fromJson(obj, type));
            } catch (JsonParseException e) {
                CoreLog.warn(e, "{} could not be mapped to {}; moving it aside", file.getFileName(),
                        type.getSimpleName());
                backup(file);
                return Optional.empty();
            }
        });
    }

    /** Serialises {@code value} with Gson and writes it atomically. */
    public void write(Path file, Object value) {
        writeText(file, gson.toJson(value) + System.lineSeparator());
    }

    /** Lists backups created for {@code file}, newest last. */
    public List<Path> backups(Path file) {
        Path dir = file.toAbsolutePath().getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        String prefix = stripExtension(file.getFileName().toString()) + ".broken-";
        List<Path> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith(prefix))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            CoreLog.warn(e, "Could not list backups of {}", file);
        }
        return out;
    }

    /** Reads the {@code schemaVersion} of a JSON object, or {@code fallback} when missing or not a number. */
    public static int schemaVersion(JsonObject json, int fallback) {
        JsonElement v = json.get(SCHEMA_VERSION);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return v.getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Moves a corrupt file to {@code <name>.broken-<epochMillis>.json}. */
    Optional<Path> backup(Path file) {
        String base = stripExtension(file.getFileName().toString());
        Path target = file.resolveSibling(base + ".broken-" + clock.millis() + ".json");
        int attempt = 1;
        while (Files.exists(target)) {
            target = file.resolveSibling(base + ".broken-" + clock.millis() + "-" + attempt++ + ".json");
        }
        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(target);
        } catch (IOException e) {
            CoreLog.warn(e, "Could not back up {}", file);
            return Optional.empty();
        }
    }

    private void writeText(Path file, String text) {
        Path absolute = file.toAbsolutePath();
        Path tmp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try {
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best effort cleanup of the temp file
            }
            throw new UncheckedIOException("Could not write " + absolute, e);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
