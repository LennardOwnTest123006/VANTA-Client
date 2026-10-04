package dev.vanta.launcher.core.model;

import java.util.List;
import java.util.Map;

/**
 * Fabric meta {@code /v2/versions/loader/{game}/{loader}/profile/json}.
 *
 * @param id           profile id (e.g. {@code fabric-loader-0.19.5-1.21.11})
 * @param inheritsFrom vanilla version id
 * @param releaseTime  release time
 * @param time         time
 * @param type         release type
 * @param mainClass    {@code net.fabricmc.loader.impl.launch.knot.KnotClient}
 * @param arguments    extra arguments
 * @param libraries    loader libraries (Maven style with {@code url})
 */
public record FabricProfileJson(String id, String inheritsFrom, String releaseTime, String time, String type,
                                String mainClass, Arguments arguments, List<Library> libraries) {

    /** Fabric Knot client main class. */
    public static final String KNOT_CLIENT = "net.fabricmc.loader.impl.launch.knot.KnotClient";

    public FabricProfileJson {
        libraries = libraries == null ? List.of() : List.copyOf(libraries);
        arguments = arguments == null ? Arguments.EMPTY : arguments;
    }

    /**
     * Converts the profile to the version JSON shape so it can be merged onto its parent.
     *
     * @return version JSON view
     */
    public VersionJson toVersionJson() {
        return new VersionJson(id, type, mainClass, inheritsFrom, null, null, Map.of(), libraries, arguments,
            null, null, null, releaseTime, time, 0, 0);
    }
}
