package dev.vanta.core.modrinth;

import dev.vanta.core.i18n.Lang;
import java.util.Locale;

/**
 * Something the planner or installer wants the player to know: a skipped project, a refused install or a failed file.
 *
 * @param kind    what happened
 * @param subject the project (title or slug) it is about
 * @param detail  second name or reason (may be empty)
 */
public record PlanNote(Kind kind, String subject, String detail) {

    /** Note kinds; each has a translated message {@code vanta.mods.note.<kind>} taking subject and detail. */
    public enum Kind {
        /** The project is already installed; nothing to do. */
        ALREADY_INSTALLED(false),
        /** Fabric API is already present (VANTA requires it), so it is not downloaded again. */
        FABRIC_API_PRESENT(false),
        /** The project has no version for Minecraft 1.21.11 with the right loader; it was skipped. */
        NO_COMPATIBLE_VERSION(true),
        /** The project does not exist. */
        NOT_FOUND(true),
        /** The project type is not something VANTA installs (modpacks, plugins, …). */
        UNSUPPORTED_TYPE(true),
        /** A version declares an installed project incompatible; the install was refused. */
        INCOMPATIBLE(true),
        /** A required dependency has no compatible version; the install was refused. */
        MISSING_DEPENDENCY(true),
        /** A required dependency is an external file Modrinth cannot provide; it was not installed. */
        EXTERNAL_DEPENDENCY(true),
        /** Planning this project failed (network, invalid response). */
        ERROR(true),
        /** Downloading or verifying a file failed; it was not installed. */
        DOWNLOAD_FAILED(true),
        /** Not installed because a dependency in the same plan failed. */
        DEPENDENCY_FAILED(true);

        private final boolean problem;

        Kind(boolean problem) {
            this.problem = problem;
        }

        /** True for notes that mean "something was not installed". */
        public boolean isProblem() {
            return problem;
        }

        /** Translation key. */
        public String langKey() {
            return "vanta.mods.note." + name().toLowerCase(Locale.ROOT);
        }
    }

    public PlanNote {
        subject = subject == null ? "" : subject;
        detail = detail == null ? "" : detail;
    }

    /** The translated message. */
    public String message() {
        return Lang.tr(kind.langKey(), subject, detail);
    }
}
