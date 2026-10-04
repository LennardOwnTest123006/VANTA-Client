package dev.vanta.core.ui;

import java.util.Objects;

/**
 * Reference to a texture by Minecraft identifier, e.g. {@code vanta:textures/gui/logo.png}. The client resolves it
 * to an {@code Identifier}; the Java2D preview resolves it to a PNG on the classpath or in the repository.
 *
 * @param namespace identifier namespace
 * @param path      identifier path including the file extension
 */
public record TextureRef(String namespace, String path) {

    /** The VANTA logo mark shipped by the client at 256x256. */
    public static final TextureRef VANTA_LOGO = new TextureRef("vanta", "textures/gui/logo.png");
    /** The VANTA wordmark shipped by the client at 512x128. */
    public static final TextureRef VANTA_WORDMARK = new TextureRef("vanta", "textures/gui/wordmark.png");

    public TextureRef {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (namespace.isEmpty() || path.isEmpty()) {
            throw new IllegalArgumentException("namespace and path must not be empty");
        }
    }

    /** Parses {@code namespace:path}; a missing namespace defaults to {@code minecraft}. */
    public static TextureRef parse(String id) {
        Objects.requireNonNull(id, "id");
        int colon = id.indexOf(':');
        if (colon < 0) {
            return new TextureRef("minecraft", id);
        }
        return new TextureRef(id.substring(0, colon), id.substring(colon + 1));
    }

    /** {@code namespace:path}. */
    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
