---
product: launcher
version: 1.4.0
date: 2026-10-08
title: VANTA Launcher 1.4.0
minecraftVersion: 1.21.11
---

Release of the VANTA Launcher. It can install the **Local AI** that Vanta Nexus in VANTA Client 1.4.0 runs on your PC: `llama-server` from llama.cpp and the Qwen3-1.7B model, downloaded once from github.com and huggingface.co only after you agree, verified by SHA-256 and kept in the launcher's own folder, where the game uses them read-only. The Windows installer is the `.msi` only from this release on. Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11 and Java 21 are unchanged, and the launcher installs VANTA Client 1.4.0.

## Added

- **Local AI install.** At the first start of 1.4.0 the launcher asks once: *Install the Local AI?* The dialog names the runtime (llama.cpp b11429, the archive for your system with its size, MIT) and the model (Qwen3-1.7B Q8_0, Qwen3-1.7B-Q8_0.gguf, 1.83 GB, Apache-2.0), the hosts github.com and huggingface.co, the total download, the folder, that it needs about 2200 MB of free disk space and 3072 MB of free memory while the assistant runs, and that nothing you type is sent anywhere. *Download and install* downloads both files; *Not now* switches the automatic installation off. Nothing downloads before you agree
- **Settings → Local AI**: status and installed versions, size on disk, the folder with *Open*, *Install* (or *Update* when the manifest names newer files), *Cancel*, *Verify files* (re-hashes the model, checks the server executable by presence and size), *Remove* (asks first; deletes only the Local AI folder and switches the automatic installation off) and the switch *Install the Local AI automatically* (offers the download once at the first start and keeps the files complete with every install; nothing is downloaded before you agree)
- **Home**: the line *Local AI: ready*, *not installed*, *incomplete*, *not available for <system>* or *not available in this build*, with *Install Local AI* (opens the dialog) or *Local AI settings*
- **Install pipeline step "Installing the Local AI"** after the performance pack, with the usual progress: Checking Local AI, Downloading Local AI runtime, Downloading Local AI model, Verifying files, Installing. It runs with PLAY, *PLAY via Minecraft Launcher* and `--install` once you agreed and the switch is on, and like the performance pack it never fails the game install: a problem is logged as "Local AI skipped: <reason>"
- **Note for the client.** Every game folder the launcher sets up gets `config/vanta/local-ai.json` (`{"localAiDir": "<absolute path>"}`) next to `minecraft-folder.json`, written with PLAY, *PLAY via Minecraft Launcher*, `--install-official-profile` and before every PLAY. VANTA Client 1.4.0 then starts `llama-server` from the launcher's folder and uses it read-only; Install and Remove in the game are hidden. The launcher itself never runs anything it downloaded
- **Command line**: `--install-local-ai` (downloads and installs; prints the runtime and model lines, the steps, then `Local AI: installed (llama.cpp b11429, Qwen3-1.7B Q8_0)`, `Folder: ...`, `Size on disk: ...`, `Server: ...`, `Model: ...` and `Note: ...`; exit 0, 4 on a SHA-256 or size mismatch, 3 when the build has no usable manifest, 1 on an unsupported system), `--local-ai-status` (the same status block, exit 0 for every state), `--remove-local-ai` (`Removed the Local AI from <dir> (<n> freed)` or `Local AI: not installed`), and for `--install` the flags `--with-local-ai` (adds the step and counts as your consent) and `--without-local-ai` (skips it). `--install` prints a `Local AI: on (...)` or `Local AI: off (...)` line. `--check-update` and the other commands are unchanged
- `VANTA_LOCAL_AI_MANIFEST=<file>` replaces the embedded Local AI manifest for a run (used by CI, which installs through the launcher from a local mirror of the prepared files)

## Improved

- Every Local AI download carries its SHA-256 from the manifest; the model download gets a 6 hour timeout per request (the runtime archive 1 hour) while the 60 second stall watchdog stays. A file whose digest or size does not match is refused and deleted, and nothing of it is recorded
- The archive is extracted into a staging folder and moved into `local-ai/runtime/<tag>/<platform>/` only when the server executable is really inside; on Linux and macOS it gets the executable bit. `local-ai/installed.json` records what was installed in exactly the shape VANTA Client 1.4.0 reads, so an install made by the launcher, by the client or by the release tooling reads alike
- The quick check at start compares sizes and modification times only, so PLAY never hashes the 1.83 GB model; *Verify files* and every install hash it again
- The version shown by a launcher built without build information is 1.4.0

## Removed

- **The `.exe` installer.** From 1.4.0 on the Windows installer is `VANTA-Launcher-<version>.msi` only; the same installer as `.exe` is no longer built. The updater offers the `.msi` on Windows and the `.tar.gz` on Linux x64; the `.exe` of releases before 1.4.0 stays on their release pages

## Notes

- Updating from an older launcher: it offers the `.msi` on Windows and the `.tar.gz` on Linux x64; portable and jar users download their file from the release `launcher-v1.4.0` and compare its SHA-256
- The Local AI is optional. Without it VANTA Client 1.4.0 works in full; only the Vanta Nexus assistant needs it, and the client can install its own copy from Nexus when the launcher has none
- Sizes in this text come from the Local AI manifest `shared/local-ai/local-ai.json` (runtime archive 11.5 MB for macOS Intel to 19.4 MB for Windows x64, model 1,834,426,016 bytes). The launcher installs the archive for your system only; on a system without one (anything but Windows, Linux and macOS on x64 and ARM64) it reports "not available for <system>" and installs nothing
- `--install-local-ai` and `--with-local-ai` record your consent in `settings.json` (`localAiAccepted`); `--remove-local-ai` and *Remove* switch `installLocalAi` off so the next start does not download again. The CLI otherwise never writes settings
- Microsoft sign-in inside the launcher still needs an application (client) id approved by Mojang. Until the project has one, PLAY goes via the official Minecraft Launcher, which signs you in
- Nothing is code-signed yet, so Windows SmartScreen may warn; compare the SHA-256 from `SHA256SUMS.txt` first
