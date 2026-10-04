package dev.vanta.launcher.core.launch;

import dev.vanta.launcher.core.auth.Account;
import dev.vanta.launcher.core.model.InstanceInfo;
import dev.vanta.launcher.core.settings.LauncherSettings;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the {@link LaunchService} needs to start the game.
 *
 * @param instance              installed instance
 * @param account               player
 * @param javaExecutable        Java executable
 * @param settings              launcher settings (memory, JVM args, resolution)
 * @param quickPlaySingleplayer world directory name to open directly (optional)
 * @param quickPlayMultiplayer  server address to join directly (optional)
 */
public record LaunchRequest(InstanceInfo instance, Account account, Path javaExecutable, LauncherSettings settings,
                            String quickPlaySingleplayer, String quickPlayMultiplayer) {

    public LaunchRequest {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(javaExecutable, "javaExecutable");
        Objects.requireNonNull(settings, "settings");
    }

    /**
     * Plain request without quick play.
     *
     * @param instance instance
     * @param account  account
     * @param java     Java executable
     * @param settings settings
     * @return request
     */
    public static LaunchRequest of(final InstanceInfo instance, final Account account, final Path java, final LauncherSettings settings) {
        return new LaunchRequest(instance, account, java, settings, null, null);
    }

    /** @return quick play world when set */
    public Optional<String> quickPlayWorld() {
        return Optional.ofNullable(quickPlaySingleplayer).filter(s -> !s.isBlank());
    }

    /** @return quick play server when set */
    public Optional<String> quickPlayServer() {
        return Optional.ofNullable(quickPlayMultiplayer).filter(s -> !s.isBlank());
    }
}
