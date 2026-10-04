package dev.vanta.core.screen.profiles;

import dev.vanta.core.config.CoreLog;
import dev.vanta.core.i18n.Lang;
import dev.vanta.core.profiles.BuiltInProfiles;
import dev.vanta.core.profiles.Profile;
import dev.vanta.core.profiles.ProfileCodec;
import dev.vanta.core.profiles.ProfileImportException;
import dev.vanta.core.profiles.ProfileManager;
import dev.vanta.core.screen.VantaServices;
import dev.vanta.core.screen.cosmetics.ThemedScreen;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * File-level profile operations the profiles screen offers on top of {@link ProfileManager}: exporting to
 * {@code config/vanta/exports/}, importing from the clipboard or from {@code config/vanta/imports/}, resetting a
 * built-in profile to its shipped content and validating names. Pure logic without UI so it is unit-tested with a
 * temporary config directory.
 */
public final class ProfileActions {
    /** Folder below the config root that receives exports. */
    public static final String EXPORTS_DIR = "exports";
    /** Folder below the config root the user drops files to import into. */
    public static final String IMPORTS_DIR = "imports";

    /**
     * Result of an export.
     *
     * @param file the written file
     * @param json the pretty-printed JSON that was written (also offered to the clipboard)
     */
    public record Export(Path file, String json) {
    }

    private final VantaServices services;

    public ProfileActions(VantaServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** {@code config/vanta/exports}. */
    public Path exportsDir() {
        return services.paths().root().resolve(EXPORTS_DIR);
    }

    /** {@code config/vanta/imports}. */
    public Path importsDir() {
        return services.paths().root().resolve(IMPORTS_DIR);
    }

    /** Path of a file relative to the game directory for display ({@code config/vanta/exports/x.json}). */
    public String displayPath(Path file) {
        return ThemedScreen.displayPath(services, file);
    }

    /**
     * Writes the profile to {@code config/vanta/exports/vanta-profile-<name>.json} and returns the file and its JSON.
     *
     * @return empty when the profile does not exist
     */
    public Optional<Export> export(String profileId) {
        Optional<Profile> profile = services.profiles().find(profileId);
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        Path target = exportsDir().resolve(ProfileManager.exportFileName(profile.get()));
        if (!services.profiles().exportTo(profileId, target)) {
            return Optional.empty();
        }
        String json = services.jsonStore().gson().toJson(ProfileCodec.toJson(profile.get()));
        return Optional.of(new Export(target, json));
    }

    /**
     * Imports a profile from JSON text (the clipboard). The text goes through the same validation as a file import:
     * size limit, schema version, sanitised fields.
     *
     * @throws ProfileImportException when the text is empty, too large or not a valid profile
     */
    public Profile importFromText(String text) throws ProfileImportException {
        if (text == null || text.isBlank()) {
            throw new ProfileImportException(ProfileImportException.Reason.NOT_JSON, "Clipboard is empty");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ProfileManager.MAX_IMPORT_BYTES) {
            throw new ProfileImportException(ProfileImportException.Reason.TOO_LARGE,
                    "Profile files must be at most 1 MiB");
        }
        Path temp;
        try {
            temp = Files.createTempFile("vanta-profile-import", ".json");
            Files.write(temp, bytes);
        } catch (IOException e) {
            throw new ProfileImportException(ProfileImportException.Reason.IO_ERROR, "Could not stage the import", e);
        }
        try {
            return services.profiles().importFrom(temp);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                CoreLog.warn(e, "Could not delete temporary import file {}", temp);
            }
        }
    }

    /**
     * Imports a profile file.
     *
     * @throws ProfileImportException when the file is missing, too large or invalid
     */
    public Profile importFile(Path file) throws ProfileImportException {
        return services.profiles().importFrom(Objects.requireNonNull(file, "file"));
    }

    /** Creates {@code config/vanta/imports} when missing so the user has a folder to drop files into. */
    public Path ensureImportsDir() {
        Path dir = importsDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            CoreLog.warn(e, "Could not create the imports folder {}", dir);
        }
        return dir;
    }

    /** JSON files in the imports folder, sorted by name (case-insensitive). */
    public List<Path> listImportFiles() {
        Path dir = importsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> out = new ArrayList<>(files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .toList());
            out.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
            return out;
        } catch (IOException e) {
            CoreLog.warn(e, "Could not list the imports folder {}", dir);
            return List.of();
        }
    }

    /** True for one of the five profiles created on first run. */
    public static boolean isBuiltIn(String profileId) {
        return BuiltInProfiles.IDS.contains(profileId);
    }

    /**
     * Restores the shipped content (settings, HUD layout, key overrides, crosshair, cosmetics) of a built-in profile
     * while keeping its current name and icon. When the profile is active the restored content is applied.
     *
     * @return false when the id is not a built-in profile or it no longer exists
     */
    public boolean resetBuiltIn(String profileId) {
        if (!isBuiltIn(profileId)) {
            return false;
        }
        ProfileManager profiles = services.profiles();
        Optional<Profile> existing = profiles.find(profileId);
        if (existing.isEmpty()) {
            return false;
        }
        long now = services.clock().millis();
        Profile shipped = BuiltInProfiles.create(services.settings().registry(), now).stream()
                .filter(p -> p.id().equals(profileId)).findFirst().orElseThrow();
        Profile restored = existing.get().withContent(shipped.settings(), shipped.hud(), shipped.keybinds(),
                shipped.crosshair(), shipped.cosmetics(), now);
        boolean wasActive = profiles.activeId().map(profileId::equals).orElse(false);
        services.jsonStore().writeObject(services.paths().profilesDir().resolve(profileId + ".json"),
                ProfileCodec.toJson(restored));
        profiles.load();
        if (wasActive) {
            profiles.activate(profileId);
        }
        return true;
    }

    /**
     * Validates a profile name for creation, duplication or renaming.
     *
     * @param name        the proposed name
     * @param excludingId id of the profile being renamed (its own current name is allowed), or {@code null}
     * @return the translation key of the problem, or empty when the name is fine
     */
    public Optional<String> validateName(String name, String excludingId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return Optional.of("vanta.profiles.empty_name");
        }
        if (trimmed.length() > Profile.MAX_NAME) {
            return Optional.of("vanta.profiles.name_too_long");
        }
        for (Profile other : services.profiles().list()) {
            if (!other.id().equals(excludingId) && other.name().equalsIgnoreCase(trimmed)) {
                return Optional.of("vanta.profiles.name_taken");
            }
        }
        return Optional.empty();
    }

    /** A free name derived from {@code base}: "PvP (copy)", then "PvP (copy 2)", "PvP (copy 3)", …. */
    public String copyName(String base) {
        String stem = base == null ? "" : base.trim();
        String candidate = fit(Lang.tr("vanta.profiles.copy_name", stem));
        int n = 2;
        while (validateName(candidate, null).isPresent()) {
            candidate = fit(Lang.tr("vanta.profiles.copy_name_numbered", stem, n++));
        }
        return candidate;
    }

    private static String fit(String name) {
        return name.length() > Profile.MAX_NAME ? name.substring(0, Profile.MAX_NAME) : name;
    }
}
