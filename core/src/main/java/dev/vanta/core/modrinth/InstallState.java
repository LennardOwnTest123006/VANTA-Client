package dev.vanta.core.modrinth;

import java.util.Map;
import java.util.Set;

/**
 * What is already present when a plan is made.
 *
 * @param installed        projects in {@code modrinth.json}, by project id → enabled
 * @param manual           project ids of enabled jars installed by other means, identified by their SHA-512 on
 *                         Modrinth
 * @param loadedSlugs      Modrinth slugs of mods the game has loaded whose jar Modrinth could not identify (a
 *                         CurseForge or GitHub build, a re-zipped or oversized jar); only known for the
 *                         {@link PerformancePack} members, whose mod ids are fixed
 * @param fabricApiPresent true when Fabric API is loaded or its jar is in {@code mods/} (VANTA requires it, so this
 *                         is the normal case in game)
 */
public record InstallState(Map<String, Boolean> installed, Set<String> manual, Set<String> loadedSlugs,
                           boolean fabricApiPresent) {
    public InstallState {
        installed = Map.copyOf(installed);
        manual = Set.copyOf(manual);
        loadedSlugs = Set.copyOf(loadedSlugs);
    }

    /** State without loaded-but-unidentified mods. */
    public InstallState(Map<String, Boolean> installed, Set<String> manual, boolean fabricApiPresent) {
        this(installed, manual, Set.of(), fabricApiPresent);
    }

    /** Nothing installed, Fabric API present. */
    public static InstallState none() {
        return new InstallState(Map.of(), Set.of(), true);
    }

    /** Builds the state from the index. */
    public static InstallState of(ModrinthIndex index, Set<String> manual, boolean fabricApiPresent) {
        return of(index, manual, Set.of(), fabricApiPresent);
    }

    /** Builds the state from the index, with the loaded-but-unidentified mods. */
    public static InstallState of(ModrinthIndex index, Set<String> manual, Set<String> loadedSlugs,
                                  boolean fabricApiPresent) {
        java.util.Map<String, Boolean> installed = new java.util.LinkedHashMap<>();
        for (InstalledEntry entry : index.entries()) {
            installed.put(entry.projectId(), entry.enabled());
        }
        return new InstallState(installed, manual, loadedSlugs, fabricApiPresent);
    }

    /** True when the project is present in any form (index or manual). */
    public boolean isPresent(String projectId) {
        return installed.containsKey(projectId) || manual.contains(projectId)
                || (fabricApiPresent && ModrinthConstants.FABRIC_API_PROJECT_ID.equals(projectId));
    }

    /** True when the game loaded the project's mod from a jar Modrinth did not identify. */
    public boolean isLoaded(String slug) {
        return loadedSlugs.contains(slug);
    }

    /** True when the project is in the index but switched off. */
    public boolean isDisabled(String projectId) {
        return Boolean.FALSE.equals(installed.get(projectId));
    }
}
