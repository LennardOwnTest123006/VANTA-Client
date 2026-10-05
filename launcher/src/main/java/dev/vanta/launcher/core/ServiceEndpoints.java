package dev.vanta.launcher.core;

import dev.vanta.launcher.core.auth.MicrosoftAuthService;
import dev.vanta.launcher.core.install.FabricService;
import dev.vanta.launcher.core.install.MojangService;
import dev.vanta.launcher.core.java.AdoptiumService;
import dev.vanta.launcher.core.modrinth.ModrinthApi;
import dev.vanta.launcher.core.settings.LauncherSettings;

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
 * @param defaultReleasesBaseUrl releases base URL used when neither {@code settings.json} nor
 *                        {@value LauncherSettings#RELEASES_BASE_URL_ENV} provide one
 * @param modrinthApi     Modrinth API v2 base (ends with a slash)
 */
public record ServiceEndpoints(URI mojangManifest, URI mojangResources, URI fabricMeta, URI fabricMaven, URI adoptiumApi,
                               MicrosoftAuthService.Endpoints auth, String defaultReleasesBaseUrl, URI modrinthApi) {

    /** Production endpoints. */
    public static final ServiceEndpoints DEFAULT = new ServiceEndpoints(MojangService.VERSION_MANIFEST_URL, MojangService.RESOURCES_BASE,
        FabricService.META_BASE, FabricService.MAVEN_BASE, AdoptiumService.API_BASE, MicrosoftAuthService.Endpoints.DEFAULT,
        LauncherSettings.DEFAULT_RELEASES_BASE_URL, ModrinthApi.API_BASE);

    /**
     * Endpoints with the production Modrinth API.
     *
     * @param mojangManifest         Mojang version manifest URL
     * @param mojangResources        Mojang asset objects base
     * @param fabricMeta             Fabric meta API base
     * @param fabricMaven            Fabric Maven base
     * @param adoptiumApi            Adoptium API base
     * @param auth                   authentication endpoints
     * @param defaultReleasesBaseUrl built-in releases base URL
     */
    public ServiceEndpoints(final URI mojangManifest, final URI mojangResources, final URI fabricMeta, final URI fabricMaven,
                            final URI adoptiumApi, final MicrosoftAuthService.Endpoints auth, final String defaultReleasesBaseUrl) {
        this(mojangManifest, mojangResources, fabricMeta, fabricMaven, adoptiumApi, auth, defaultReleasesBaseUrl, ModrinthApi.API_BASE);
    }

    /**
     * Endpoints with the built-in releases base URL.
     *
     * @param mojangManifest  Mojang version manifest URL
     * @param mojangResources Mojang asset objects base
     * @param fabricMeta      Fabric meta API base
     * @param fabricMaven     Fabric Maven base
     * @param adoptiumApi     Adoptium API base
     * @param auth            authentication endpoints
     */
    public ServiceEndpoints(final URI mojangManifest, final URI mojangResources, final URI fabricMeta, final URI fabricMaven,
                            final URI adoptiumApi, final MicrosoftAuthService.Endpoints auth) {
        this(mojangManifest, mojangResources, fabricMeta, fabricMaven, adoptiumApi, auth, LauncherSettings.DEFAULT_RELEASES_BASE_URL,
            ModrinthApi.API_BASE);
    }

    public ServiceEndpoints {
        Objects.requireNonNull(mojangManifest, "mojangManifest");
        Objects.requireNonNull(mojangResources, "mojangResources");
        Objects.requireNonNull(fabricMeta, "fabricMeta");
        Objects.requireNonNull(fabricMaven, "fabricMaven");
        Objects.requireNonNull(adoptiumApi, "adoptiumApi");
        Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(modrinthApi, "modrinthApi");
        defaultReleasesBaseUrl = defaultReleasesBaseUrl == null || defaultReleasesBaseUrl.isBlank()
            ? LauncherSettings.DEFAULT_RELEASES_BASE_URL : defaultReleasesBaseUrl.trim();
    }
}
