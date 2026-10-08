/**
 * Vanta Nexus and the Local AI: the manifest of the llama.cpp runtime and the Qwen3 model, the installer that
 * downloads and verifies them, the {@code llama-server} child process, the HTTP client that talks to it on
 * {@code 127.0.0.1} only, and the assistant that turns the model's JSON replies into validated changes of the HUD,
 * settings, profiles, performance presets, waypoints and lab features.
 * <p>
 * Pure Java: no Minecraft imports, every collaborator injectable, every piece of logic unit-tested with fakes.
 * Nothing in here ever contacts a server other than the one-time download sources named in the manifest and the
 * local {@code llama-server} process.
 */
package dev.vanta.core.ai;
