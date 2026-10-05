package dev.vanta.core.modrinth;

import java.util.List;
import java.util.Optional;

/**
 * The one-click Performance pack: well-known Fabric optimisation mods plus Iris for shaders. Only Modrinth slugs are
 * fixed here; the version of each is resolved for Minecraft 1.21.11 + Fabric at install time, and a slug without a
 * compatible version is skipped with a note while the rest installs.
 */
public final class PerformancePack {

    /**
     * One pack member.
     *
     * @param slug        Modrinth slug
     * @param name        display name
     * @param modId       Fabric mod id (to show whether it is loaded right now)
     * @param descriptionKey translation key of the one-line description
     */
    public record Item(String slug, String name, String modId, String descriptionKey) {
    }

    /** Members, in install order (Sodium first: Iris requires it). */
    public static final List<Item> ITEMS = List.of(
            new Item("sodium", "Sodium", "sodium", "vanta.mods.pack.sodium"),
            new Item("lithium", "Lithium", "lithium", "vanta.mods.pack.lithium"),
            new Item("ferrite-core", "FerriteCore", "ferritecore", "vanta.mods.pack.ferrite_core"),
            new Item("immediatelyfast", "ImmediatelyFast", "immediatelyfast", "vanta.mods.pack.immediatelyfast"),
            new Item("entityculling", "EntityCulling", "entityculling", "vanta.mods.pack.entityculling"),
            new Item("iris", "Iris Shaders", "iris", "vanta.mods.pack.iris"));

    private PerformancePack() {
    }

    /** Every slug, in order. */
    public static List<String> slugs() {
        return ITEMS.stream().map(Item::slug).toList();
    }

    /** The member with a slug. */
    public static Optional<Item> item(String slug) {
        return ITEMS.stream().filter(i -> i.slug().equals(slug)).findFirst();
    }

    /** Install requests for the given slugs, in pack order (unknown slugs are ignored). */
    public static List<InstallRequest> requests(java.util.Collection<String> selectedSlugs) {
        return ITEMS.stream().filter(i -> selectedSlugs.contains(i.slug()))
                .map(i -> new InstallRequest(i.slug(), i.name())).toList();
    }
}
