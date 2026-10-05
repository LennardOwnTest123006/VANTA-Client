/**
 * Modrinth integration: a small client for the public Modrinth API v2 ({@link dev.vanta.core.modrinth.ModrinthClient}),
 * version selection for Minecraft 1.21.11, an install planner that resolves required dependencies
 * ({@link dev.vanta.core.modrinth.InstallPlanner}), a verifying installer that writes into {@code mods/},
 * {@code shaderpacks/} and {@code resourcepacks/} ({@link dev.vanta.core.modrinth.ModrinthInstaller}), the
 * {@code config/vanta/modrinth.json} index shared with the VANTA launcher ({@link dev.vanta.core.modrinth.ModrinthIndex})
 * and the asynchronous facade the in-game screen uses ({@link dev.vanta.core.modrinth.ModrinthService}).
 * <p>
 * Rules: files are only downloaded from the URL Modrinth returns for a version file, every download is checked against
 * the SHA-512 Modrinth returns and deleted on mismatch, and files VANTA did not install are never deleted.
 */
package dev.vanta.core.modrinth;
