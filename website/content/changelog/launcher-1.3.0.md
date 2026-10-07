---
product: launcher
version: 1.3.0
date: 2026-10-07
title: VANTA Launcher 1.3.0
minecraftVersion: 1.21.11
---

Release of the VANTA Launcher. The profile *VANTA 1.21.11* in the official Minecraft Launcher carried only `-Xmx`, so the game ran on plain JVM defaults there and could freeze for short moments; it now gets the same tuned memory and garbage-collector arguments as PLAY. The default memory follows the PC's RAM, and the launcher tells VANTA Client 1.3.0 which Minecraft folder holds your worlds, so Singleplayer can list them. Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11 and Java 21 are unchanged, and the launcher installs VANTA Client 1.3.0.

## Added

- **Minecraft folder note** for the client's *Singleplayer worlds* setting: `config/vanta/minecraft-folder.json` in VANTA's game folder, `{"minecraftDir": "<absolute path>"}`. *PLAY via Minecraft Launcher*, *Use with Minecraft Launcher* and `--install-official-profile` record the Minecraft folder the profile went to (also a custom `--minecraft-dir`), and the confirmation lists the file. Before PLAY the launcher records the detected Minecraft folder when it exists and no note names an existing folder yet. Nothing in the Minecraft folder is changed, and an identical file is not rewritten

## Improved

- The profile's `javaArgs` hold the memory and garbage-collector arguments PLAY uses: `-Xmx<memory>M`, `-Xms` (a quarter of it, at least 512 MiB), `-XX:+UseG1GC`, `-XX:+UnlockExperimentalVMOptions`, `-XX:G1NewSizePercent=20`, `-XX:G1ReservePercent=20`, `-XX:MaxGCPauseMillis=50` and `-XX:G1HeapRegionSize=32M`. `-Xms` and the G1 arguments are left out when your extra JVM arguments set `-Xms` or select a garbage collector
- Your *Extra JVM arguments* from the launcher's Settings are added to the profile when they fit its single line: no spaces or quotes, no class path, `-jar` or module switch, and an option such as `--add-opens` only together with its value. The log names every argument that is left out
- The line ends with `-Dvanta.javaArgs=<checksum>`, a system property that marks it as VANTA's. A later setup replaces `javaArgs` only when VANTA wrote them (missing, empty, exactly the `-Xmx<n>M` of launcher 1.2.1 and earlier, or a matching checksum); JVM arguments you edited in the Minecraft Launcher are kept, and the log says so
- Default memory: half of the physical memory, between 2048 and 8192 MiB, but never more than half of it (4096 MiB when the memory is unknown). A PC with less than 4 GiB gets half its memory, at least 1024 MiB. A `settings.json` without a memory value, or with one below 1024 MiB, gets this default instead of 4096 MiB; a stored value of 1024 MiB or more is kept

## Fixed

- **Short freezes when playing through the Minecraft Launcher.** A profile with `javaArgs` runs with exactly those arguments instead of the Minecraft Launcher's defaults. With only `-Xmx<memory>M` the game ran with a 200 ms garbage-collection pause target and a heap that starts small and grows. The profile now gets PLAY's tuning

## Notes

- Run *PLAY via Minecraft Launcher* or *Use with Minecraft Launcher* once after updating, so the profile gets the new JVM arguments and the Minecraft folder note. If you edited the profile's JVM arguments in the Minecraft Launcher, they stay; empty them there if you want VANTA's line
- Singleplayer worlds are a setting of VANTA Client 1.3.0 (*Settings → General → Singleplayer worlds*); nothing is moved, copied or deleted. Worlds made with an earlier VANTA stay in `instances/vanta-1.21.11/saves` in the data directory
- Updating from launcher 1.0.0: it offers the `.msi` on Windows and the `.tar.gz` on Linux x64; portable and jar users download their file from the release `launcher-v1.3.0` and compare its SHA-256
- Microsoft sign-in inside the launcher still needs an application (client) id approved by Mojang. Until the project has one, PLAY goes via the official Minecraft Launcher, which signs you in
- Nothing is code-signed yet, so Windows SmartScreen may warn; compare the SHA-256 from `SHA256SUMS.txt` first
