package dev.vanta.core.modrinth;

import java.util.Map;
import java.util.Set;

/**
 * What is already present when a plan is made.
 *
 * @param installed        projects in {@code modrinth.json}, by project id → enabled
 * @param manual           project ids of jars installed by other means, identified by their SHA-512 on Modrinth
 * @param fabricApiPresent true when Fabric API is loaded or its jar is in {@code mods/} (VANTA requires it, so this
 *                         is the normal case in game)
 */
public record InstallState(Map<String, Boolean> installed, Set<String> manual, boolean fabricApiPresent) {
    public InstallState {
        installed = Map.copyOf(installed);
        manual = Set.copyOf(manual);
    }

    /** Nothing installed, Fabric API present. */
    public static InstallState none() {
        return new InstallState(Map.of(), Set.of(), true);
    }

    /** Builds the state from the index. */
    public static InstallState of(ModrinthIndex index, Set<String> manual, boolean fabricApiPresent) {
        java.util.Map<String, Boolean> installed = new java.util.LinkedHashMap<>();
        for (InstalledEntry entry : index.entries()) {
            installed.put(entry.projectId(), entry.enabled());
        }
        return new InstallState(installed, manual, fabricApiPresent);
    }

    /** True when the project is present in any form (index or manual). */
    public boolean isPresent(String projectId) {
        return installed.containsKey(projectId) || manual.contains(projectId)
                || (fabricApiPresent && ModrinthConstants.FABRIC_API_PROJECT_ID.equals(projectId));
    }

    /** True when the project is in the index but switched off. */
    public boolean isDisabled(String projectId) {
        return Boolean.FALSE.equals(installed.get(projectId));
    }
}
