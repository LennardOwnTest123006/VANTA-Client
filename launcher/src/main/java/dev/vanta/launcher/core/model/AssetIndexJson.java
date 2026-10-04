package dev.vanta.launcher.core.model;

import java.util.Map;

/**
 * Mojang asset index ({@code assets/indexes/<id>.json}).
 *
 * @param objects        objects keyed by virtual path
 * @param virtual        legacy flag
 * @param mapToResources legacy flag
 */
public record AssetIndexJson(Map<String, AssetObject> objects, boolean virtual, boolean mapToResources) {

    public AssetIndexJson {
        objects = objects == null ? Map.of() : Map.copyOf(objects);
    }

    /** @return sum of all object sizes */
    public long totalSize() {
        return objects.values().stream().mapToLong(AssetObject::size).sum();
    }

    /**
     * One asset object.
     *
     * @param hash SHA-1 hex (also the object file name)
     * @param size size in bytes
     */
    public record AssetObject(String hash, long size) {

        /** @return relative path below {@code assets/objects}: {@code <hash[0..2]>/<hash>} */
        public String path() {
            return hash.substring(0, 2) + '/' + hash;
        }
    }
}
