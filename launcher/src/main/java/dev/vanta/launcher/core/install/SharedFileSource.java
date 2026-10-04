package dev.vanta.launcher.core.install;

import dev.vanta.launcher.core.net.Checksum;
import dev.vanta.launcher.core.net.DownloadRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read-only reuse of files from the official Minecraft Launcher directory ({@code .minecraft}). Before downloading
 * a library or asset, a matching file (same relative path, same digest) is copied from there instead. The official
 * directory is never written to.
 */
public final class SharedFileSource {

    private static final Logger LOG = Logger.getLogger("VANTA.Shared");

    private final Path officialLibraries;
    private final Path officialAssets;

    /**
     * @param officialMinecraftDir the {@code .minecraft} directory
     */
    public SharedFileSource(final Path officialMinecraftDir) {
        Objects.requireNonNull(officialMinecraftDir, "officialMinecraftDir");
        this.officialLibraries = officialMinecraftDir.resolve("libraries");
        this.officialAssets = officialMinecraftDir.resolve("assets");
    }

    /**
     * Tries to satisfy a library download from the official directory.
     *
     * @param request      request whose target lies below {@code librariesDir}
     * @param librariesDir our libraries root
     * @return whether the file was copied
     */
    public boolean copyLibrary(final DownloadRequest request, final Path librariesDir) {
        return copy(request, librariesDir, officialLibraries);
    }

    /**
     * Tries to satisfy an asset object download from the official directory.
     *
     * @param request   request whose target lies below {@code assetsDir}
     * @param assetsDir our assets root
     * @return whether the file was copied
     */
    public boolean copyAsset(final DownloadRequest request, final Path assetsDir) {
        return copy(request, assetsDir, officialAssets);
    }

    private boolean copy(final DownloadRequest request, final Path ourRoot, final Path theirRoot) {
        final Optional<Checksum> checksum = request.expectedChecksum();
        if (checksum.isEmpty() || !Files.isDirectory(theirRoot)) {
            return false;
        }
        final Path target = request.target().toAbsolutePath().normalize();
        final Path root = ourRoot.toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            return false;
        }
        final Path candidate = theirRoot.resolve(root.relativize(target).toString());
        try {
            if (!Files.isRegularFile(candidate)) {
                return false;
            }
            if (request.hasExpectedSize() && Files.size(candidate) != request.expectedSize()) {
                return false;
            }
            if (!checksum.get().matches(candidate)) {
                return false;
            }
            Files.createDirectories(target.getParent());
            final Path tmp = dev.vanta.launcher.core.util.AtomicFiles.tempSibling(target);
            Files.copy(candidate, tmp, StandardCopyOption.REPLACE_EXISTING);
            dev.vanta.launcher.core.util.AtomicFiles.move(tmp, target);
            return true;
        } catch (IOException e) {
            LOG.log(Level.FINE, "Could not reuse " + candidate, e);
            return false;
        }
    }
}
