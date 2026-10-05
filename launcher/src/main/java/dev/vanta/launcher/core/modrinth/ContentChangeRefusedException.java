package dev.vanta.launcher.core.modrinth;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * A change to the installed content that the launcher refuses because it would break the game or VANTA.
 */
public final class ContentChangeRefusedException extends IOException {

    private static final long serialVersionUID = 1L;

    /** Why the change was refused. */
    public enum Reason {
        /** Another installed project needs this one ({@link #names()} lists them). */
        REQUIRED_BY,
        /** Fabric API and the VANTA Client are managed by the launcher (Home, Versions). */
        MANAGED
    }

    private final Reason reason;
    private final String title;
    private final transient List<String> names;

    /**
     * @param reason reason
     * @param title  the content that was to be changed
     * @param names  for {@link Reason#REQUIRED_BY}: titles of the projects that need it
     */
    public ContentChangeRefusedException(final Reason reason, final String title, final List<String> names) {
        super(message(reason, title, names));
        this.reason = Objects.requireNonNull(reason, "reason");
        this.title = Objects.requireNonNull(title, "title");
        this.names = List.copyOf(names);
    }

    private static String message(final Reason reason, final String title, final List<String> names) {
        return switch (reason) {
            case REQUIRED_BY -> title + " is required by " + String.join(", ", names) + "; disable or remove "
                + (names.size() == 1 ? "it" : "them") + " first";
            case MANAGED -> title + " is installed and updated by the VANTA Launcher itself";
        };
    }

    /** @return reason */
    public Reason reason() {
        return reason;
    }

    /** @return the content that was to be changed */
    public String title() {
        return title;
    }

    /** @return titles of the projects that need it */
    public List<String> names() {
        return names;
    }
}
