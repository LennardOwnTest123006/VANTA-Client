---
title: Java 21
description: Why VANTA needs Java 21, how to check which Java you have, how to install it and how to fix PATH and JAVA_HOME problems.
order: 3
category: Getting started
---

## Why Java 21

Minecraft Java Edition has required Java 21 since version 1.20.5, and 1.21.11 is no exception. VANTA Client and the
VANTA Launcher are compiled for Java 21 as well (`--release 21`), so:

- **Java 17 or older** cannot start the game or the launcher (`UnsupportedClassVersionError`, "class file version 65").
- **Java 22 and newer** are not tested. The launcher prefers an exact 21 and only falls back to a newer major
  version when no 21 exists; Fabric Loader and most mods work on newer Java, but if something misbehaves, install 21.
- Only **64-bit** Java is useful: Minecraft needs more memory than a 32-bit JVM can address.

## How to check

```bash
java -version
```

Typical good output:

```text
openjdk version "21.0.4" 2024-07-16 LTS
OpenJDK Runtime Environment Temurin-21.0.4+7 (build 21.0.4+7-LTS)
OpenJDK 64-Bit Server VM Temurin-21.0.4+7 (build 21.0.4+7-LTS, mixed mode, sharing)
```

The first number after `version` must be `21`, and the third line should say `64-Bit`. The launcher shows the same
information on the Home screen's Java card and lists every runtime it found under *Settings → Java*, or on the
command line:

```bash
vanta-launcher --check-java
```

## Install options

**Let the launcher do it (recommended).** On the Home screen click **Install Java 21 (Temurin)**, or run
`vanta-launcher --install-java`. The launcher asks the Adoptium API for the latest Eclipse Temurin 21 JRE for your
operating system and CPU, downloads it, verifies the SHA-256 that Adoptium publishes for the package, unpacks it
into `runtimes/temurin-21-<build>` inside the launcher data directory (rejecting any archive entry that would escape
that folder) and selects it. Nothing is installed system-wide and nothing else is executed.

**Install it yourself.** Any Java 21 distribution works; the launcher detects these locations automatically:

| System | Detected locations |
| --- | --- |
| all | `JAVA_HOME`, every directory on `PATH`, the launcher's own `runtimes/` |
| Windows | `C:\Program Files\Java`, `Eclipse Adoptium`, `Microsoft`, `Zulu`, `BellSoft`, `Amazon Corretto`, the official Minecraft Launcher runtimes (`%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime` and `C:\Program Files (x86)\Minecraft Launcher\runtime`) |
| macOS | `/Library/Java/JavaVirtualMachines/*/Contents/Home` and the same under your home directory |
| Linux | `/usr/lib/jvm/*` |

Common ways to install Java 21:

| System | Command / source |
| --- | --- |
| Windows (winget) | `winget install EclipseAdoptium.Temurin.21.JRE` |
| Windows (installer) | Eclipse Temurin 21 from adoptium.net, or Microsoft Build of OpenJDK 21 |
| macOS (Homebrew) | `brew install --cask temurin@21` |
| Ubuntu / Debian | `sudo apt install openjdk-21-jre` |
| Fedora | `sudo dnf install java-21-openjdk` |
| Arch | `sudo pacman -S jre21-openjdk` |

The official Minecraft Launcher also ships a Java 21 runtime ("java-runtime-delta"); the VANTA Launcher finds and
can use it, so if you have the official launcher installed you may already be done.

When you play through the official Minecraft Launcher (*Use with Minecraft Launcher* or a manual Fabric profile), the
Minecraft Launcher downloads and uses its own Java runtime for the game. You then need an installed Java 21 only to
start a launcher jar, or, on macOS and Linux, to run the universal Fabric installer
(`java -jar fabric-installer-<version>.jar`) for the [manual installation](installation.md#c-manual-installation).
The Windows installers, the Windows portable app and the Linux app image bring their own Java runtime, and the
Windows Fabric installer (`.exe`) needs no separate Java.

A JRE (runtime) is enough to play. A JDK is only needed to [build VANTA from source](building-from-source.md).

## Choosing a specific Java in the launcher

*Settings → Java path* accepts either the `java` executable or the installation directory. The launcher probes it
(`java -XshowSettings:properties -version`) and shows version, vendor and architecture before saving; a runtime that
is not Java 21 is flagged. Leave the field empty to return to automatic detection.

## Troubleshooting PATH and JAVA_HOME

**`java` is not recognized / command not found.** Java is installed but its `bin` folder is not on `PATH`. On
Windows: *Settings → System → About → Advanced system settings → Environment Variables*, edit `Path` for your user
and add e.g. `C:\Program Files\Eclipse Adoptium\jre-21.0.4.7-hotspot\bin`. Reopen the terminal afterwards. The VANTA
Launcher does not need `PATH` to be correct — it scans the directories above — so this only matters for starting a
launcher jar (`java -jar vanta-launcher-<version>-<system>-all.jar`) and for building.

**`java -version` shows 8 or 17 although 21 is installed.** Several Javas are installed and an older `bin` comes
first on `PATH`. Either move the Java 21 entry above it, or set `JAVA_HOME` to the Java 21 directory and put
`%JAVA_HOME%\bin` (Windows) / `$JAVA_HOME/bin` (Linux, macOS) at the front of `PATH`. In the launcher simply pick the
right runtime in Settings; it lists all of them.

**`JAVA_HOME` points to a `bin` folder or to `java.exe`.** `JAVA_HOME` must be the installation directory (the one
that contains `bin/` and `lib/`), not the executable.

**The launcher says "no Java 21 runtime found" although `java -version` shows 21.** The launcher could not run the
probe within its timeout, or the runtime is 32-bit. Check `logs/launcher-0.log` for the probe output, or set the path
explicitly in Settings. [Troubleshooting](troubleshooting.md#the-launcher-cannot-find-java) has the full list.

**Error: `UnsupportedClassVersionError ... class file version 65.0`.** The game or the launcher was started with
Java 17 or older. Use Java 21.
