package dev.vanta.core.bridge;

import java.util.List;

/**
 * Resource pack management, implemented by the client on top of {@code PackRepository}.
 * Changes are staged until {@link #apply()} reloads resources, exactly like the vanilla screen.
 */
public interface ResourcePackBridge {

    /** All available packs (enabled and disabled). */
    List<PackInfo> packs();

    /**
     * Enables or disables a pack.
     *
     * @return true when the pack exists and the state changed
     */
    boolean setEnabled(String id, boolean enabled);

    /**
     * Moves an enabled pack within the selected list.
     *
     * @param delta negative = lower priority, positive = higher priority
     * @return true when the pack moved
     */
    boolean move(String id, int delta);

    /** True when staged changes differ from the active selection. */
    boolean hasPendingChanges();

    /** Applies staged changes and reloads resources. */
    void apply();

    /** Discards staged changes. */
    void discard();

    /** Opens the {@code resourcepacks} folder in the system file browser. */
    void openPackFolder();

    /** Opens the vanilla resource pack screen. */
    void openVanillaScreen();
}
