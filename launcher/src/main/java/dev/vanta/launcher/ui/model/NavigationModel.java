package dev.vanta.launcher.ui.model;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import java.util.Objects;

/**
 * Which page the main area shows.
 */
public final class NavigationModel {

    /** Pages of the launcher. */
    public enum Page {
        /** Play, status, account, Java, client cards. */
        HOME("nav.home"),
        /** Installed components and rollback. */
        VERSIONS("nav.versions"),
        /** Launcher and game logs. */
        LOGS("nav.logs"),
        /** Settings. */
        SETTINGS("nav.settings"),
        /** About, licenses, links. */
        ABOUT("nav.about");

        private final String titleKey;

        Page(final String titleKey) {
            this.titleKey = titleKey;
        }

        /** @return message key of the page title */
        public String titleKey() {
            return titleKey;
        }
    }

    private final ReadOnlyObjectWrapper<Page> current = new ReadOnlyObjectWrapper<>(Page.HOME);

    /** @return current page */
    public ReadOnlyObjectProperty<Page> currentProperty() {
        return current.getReadOnlyProperty();
    }

    /** @return current page */
    public Page current() {
        return current.get();
    }

    /**
     * Shows a page.
     *
     * @param page page
     */
    public void navigate(final Page page) {
        current.set(Objects.requireNonNull(page, "page"));
    }
}
