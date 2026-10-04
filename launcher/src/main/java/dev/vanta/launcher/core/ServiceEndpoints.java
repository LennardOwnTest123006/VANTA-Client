package dev.vanta.launcher.core;

import dev.vanta.launcher.core.auth.MicrosoftAuthService;
import dev.vanta.launcher.core.install.FabricService;
import dev.vanta.launcher.core.install.MojangService;
import dev.vanta.launcher.core.java.AdoptiumService;

import java.net.URI;
import java.util.Objects;

/**
 * Remote endpoints used by the launcher. Production uses {@link #DEFAULT}; integration tests and mirrors can
 * substitute their own.
 *
 * @param mojangManifest  Mojang version manifest URL
 * @param mojangResources Mojang asset objects base (ends with a slash)
 * @param fabricMeta      Fabric meta API base (ends with a slash)
 * @param fabricMaven     Fabric Maven base (ends with a slash)
 * @param adoptiumApi     Adoptium API base (ends with a slash)
 * @param auth            Microsoft / Xbox / Minecraft authentication endpoints
 */
public record ServiceEndpoints(URI mojangManifest, URI mojangResources, URI fabricMeta, URI fabricMaven, URI adoptiumApi,
                               MicrosoftAuthService.Endpoints auth) {

    /** Production endpoints. */
    public static final ServiceEndpoints DEFAULT = new ServiceEndpoints(MojangService.VERSION_MANIFEST_URL, MojangService.RESOURCES_BASE,
        FabricService.META_BASE, FabricService.MAVEN_BASE, AdoptiumService.API_BASE, MicrosoftAuthService.Endpoints.DEFAULT);

    public ServiceEndpoints {
        Objects.requireNonNull(mojangManifest, "mojangManifest");
        Objects.requireNonNull(mojangResources, "mojangResources");
        Objects.requireNonNull(fabricMeta, "fabricMeta");
        Objects.requireNonNull(fabricMaven, "fabricMaven");
        Objects.requireNonNull(adoptiumApi, "adoptiumApi");
        Objects.requireNonNull(auth, "auth");
    }
}
