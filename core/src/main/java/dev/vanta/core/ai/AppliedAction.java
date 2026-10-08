package dev.vanta.core.ai;

import dev.vanta.core.i18n.Lang;
import java.util.Objects;

/**
 * One change the assistant made, for the "Applied: ..." receipt.
 *
 * @param type   action type, e.g. {@code hud.set}
 * @param target what it acted on (element id, setting id, preset, profile or waypoint name)
 * @param detail what changed, already readable ({@code visible=false, scale=1.5})
 */
public record AppliedAction(String type, String target, String detail) {
    public AppliedAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(detail, "detail");
    }

    /** Translation key of the receipt line: {@code vanta.nexus.action.<type with dots replaced by underscores>}. */
    public String langKey() {
        return "vanta.nexus.action." + type.replace('.', '_');
    }

    /** Translated receipt line. */
    public String summary() {
        return Lang.tr(langKey(), target, detail);
    }
}
