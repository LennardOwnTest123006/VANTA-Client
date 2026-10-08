package dev.vanta.core.ai;

import java.nio.file.Path;

/**
 * Path checks shared by the zip and tar.gz extractors: every entry must stay inside the target directory.
 */
final class ArchiveSafety {
    private ArchiveSafety() {
    }

    /**
     * Resolves an archive entry name below {@code root}.
     *
     * @throws LocalAiException {@code INVALID_ARCHIVE} when the entry is absolute or escapes the root
     */
    static Path resolve(Path root, String entryName) throws LocalAiException {
        if (entryName == null || entryName.isBlank()) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE, "archive entry without a name");
        }
        String name = entryName.replace('\\', '/');
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE,
                    "archive entry with an absolute path: " + entryName);
        }
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root) || target.equals(root) && !name.replace("/", "").isEmpty()) {
            throw new LocalAiException(LocalAiException.Kind.INVALID_ARCHIVE,
                    "archive entry escapes the target directory: " + entryName);
        }
        return target;
    }
}
