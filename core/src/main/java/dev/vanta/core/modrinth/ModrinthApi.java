package dev.vanta.core.modrinth;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * The Modrinth operations VANTA needs. {@link ModrinthClient} implements it over HTTP; tests and screen previews use
 * an in-memory implementation. Every method blocks and must be called off the render thread.
 */
public interface ModrinthApi {

    /** {@code GET /search}. */
    ModrinthSearchResult search(SearchRequest request) throws ModrinthException;

    /** {@code GET /project/{idOrSlug}}; {@link ModrinthException.Kind#NOT_FOUND} when it does not exist. */
    ModrinthProject project(String idOrSlug) throws ModrinthException;

    /** {@code GET /project/{idOrSlug}/version} filtered by loaders and game versions (newest first). */
    List<ModrinthVersion> versions(String idOrSlug, List<String> loaders, List<String> gameVersions)
            throws ModrinthException;

    /** {@code GET /version/{id}}. */
    ModrinthVersion version(String versionId) throws ModrinthException;

    /** {@code POST /version_files} with SHA-512 hashes: the version each known file belongs to, keyed by hash. */
    Map<String, ModrinthVersion> versionsByHash(Collection<String> sha512Hashes) throws ModrinthException;

    /**
     * Downloads {@code file} from {@link ModrinthFile#url()} to {@code target}. The content is checked against
     * {@link ModrinthFile#sha512()} before it is moved into place atomically; on a mismatch nothing is left on disk and
     * {@link ModrinthException.Kind#HASH_MISMATCH} is thrown.
     *
     * @param bytesRead receives the running byte count
     */
    void download(ModrinthFile file, Path target, LongConsumer bytesRead) throws ModrinthException;
}
