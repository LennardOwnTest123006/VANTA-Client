/**
 * Waypoints: saved positions per world, persisted locally in {@code config/vanta/waypoints.json}.
 * <p>
 * {@link dev.vanta.core.waypoints.WaypointStore} owns the data (add, update, remove, enable, queries, sorting);
 * {@link dev.vanta.core.waypoints.WorldKeys} derives the world key from the game bridge;
 * {@link dev.vanta.core.waypoints.WaypointMarkers} computes distance, bearing and range for the markers the client
 * draws. Nothing in this package touches the network.
 */
package dev.vanta.core.waypoints;
