---
title: VANTA Launcher
description: The VANTA Launcher explained: Play, Microsoft sign-in, Java detection and Temurin installation, Versions, Logs, Settings, updates and rollback, and the full command line reference.
order: 10
category: Launcher
---

The VANTA Launcher installs and starts Minecraft 1.21.11 with Fabric Loader 0.19.5, Fabric API and the VANTA Client.
It is a JavaFX 21 desktop application for Windows 10/11; the portable jar also runs on Linux and macOS. It does three
things and nothing more: download verified files, sign you in with Microsoft, start the game.

## Home

The Home screen has one big **PLAY** button and tells you why it is disabled when it is: *Sign in first*,
*No Java 21 found*, *Installing…*, *Verifying…* or *Running*. Under it the facts line reads
`Minecraft 1.21.11 · Fabric 0.19.5 · Java 21 (<detected version>)`.

Pressing PLAY runs the launch flow:

1. **Java** — pick the configured or best detected Java 21 (or stop with *No Java 21 found*).
2. **Install / verify** — compare every required file (client jar, libraries, assets, Fabric libraries, Fabric API,
   VANTA jar) with its checksum; download what is missing or wrong. A progress bar shows the current step and bytes.
3. **Account** — refresh the Microsoft/Minecraft token when it has expired.
4. **Launch** — start `java` with the Fabric main class and stream the game output to the Logs screen. The status
   turns to **Running**; a notification reports the exit code when the game closes.

Side cards: **Account** (name, initials avatar, sign in/out), **Java** (detected runtimes, *Install Java 21
(Temurin)*), **Client version** (installed vs. latest from the release manifest, *Update*), and a recent-log snippet.
**Verify files** re-checks the installation without launching; **Open game folder** opens `instances/vanta-1.21.11`.

## Account sign-in

The launcher uses Microsoft's **device code flow**, so you never type a password into it:

1. Click **Sign in**. The launcher shows a short code in large letters with a **Copy** button and a button that
   opens `https://www.microsoft.com/link` (you can also open that address on any other device).
2. Enter the code, sign in to your Microsoft account there and approve the request.
3. The launcher polls Microsoft until the approval arrives (it honours the interval Microsoft requests), then signs
   in to Xbox Live, obtains an XSTS token for Minecraft, logs in to `api.minecraftservices.com`, checks the
   **entitlements** (you must own Minecraft Java Edition) and loads your **profile** (name, UUID, skins).
4. The account appears in the sidebar. Tokens are refreshed silently with the refresh token when they expire; you
   sign in again only when Microsoft invalidates it.

Error messages are specific: *no Xbox Live account* (create one at xbox.com), *child account* (needs to be added to
a family by an adult), *Xbox Live unavailable in your region*, *account does not own Minecraft Java Edition*, *not
configured* (see below). The codes behind them are listed in [Troubleshooting](troubleshooting.md#microsoft-sign-in-errors).

**Where tokens are stored.** `accounts.dat` in the launcher data directory holds the access and refresh tokens (never
a password). On Windows it is encrypted with DPAPI for your user account; on Linux and macOS with AES-256-GCM using a
random key in `key.bin` that only your user can read. Tokens are redacted from every log file.

### Microsoft client id

Microsoft requires every launcher to identify itself with an *application (client) id* registered in Microsoft Entra
and approved by Mojang for Minecraft sign-in. This id is **configuration, not a constant in the code**: the project
maintainers register one and configure it, or you set your own.

- Set it under *Settings → Microsoft client id*, or in the environment variable `VANTA_MS_CLIENT_ID`.
- Without an id the Sign-in button explains that sign-in is not configured and links to this section; nothing fake
  happens and no offline workaround is offered.
- To register your own: create an app registration in the Microsoft Entra admin center for *personal Microsoft
  accounts*, enable *Allow public client flows* (device code), and request Mojang's approval for the Minecraft
  API through their developer form. Only then will `api.minecraftservices.com` accept tokens from your id.

### Offline sessions

VANTA is a legitimate client. An **offline session** (starting the game without contacting Microsoft) is offered only
for an account that has previously signed in successfully; it reuses that account's verified name and UUID, exactly
like Prism Launcher does. The only other case is **developer mode** together with the environment variable
`VANTA_DEV_OFFLINE=1`, which CI uses to run the game headlessly without an account. It is documented here for
completeness and is not a way to play without owning the game.

## Java detection and Temurin installation

The Java card shows the runtime that will be used. The launcher scans `JAVA_HOME`, `PATH`, its own `runtimes/`
folder and the usual vendor directories (including the official Minecraft Launcher's bundled runtimes), probes each
candidate for version, vendor and architecture and prefers an exact Java 21, 64-bit.

**Install Java 21 (Temurin)** downloads the latest Eclipse Temurin 21 JRE for your system from the Adoptium API,
verifies the SHA-256 published with the package, extracts it to `runtimes/temurin-21-<build>` (refusing archive
entries that would escape that folder) and selects it. The CLI equivalent is `--install-java`. More in
[Java 21](java-21.md).

## Versions

A table of the installed components with their versions and the **SHA-256 of the installed VANTA jar**:
Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, VANTA Client x.y.z, Java (path and version).
Below it, **previous client versions** kept for rollback (the last three verified jars) each with a **Roll back**
button that swaps the jar in `mods/` without downloading anything. When no release has been published, the screen
says *No release published* instead of showing empty values.

## Logs

Two tabs, **Launcher** and **Game**, with a filter field, level toggles, **Copy**, **Open logs folder** and **Clear
view**. The game tab shows the live output of the running game (`logs/game-<timestamp>.log` in the data directory;
Minecraft's own `latest.log` is in the instance). The launcher log is `logs/launcher-0.log` (rotating, five files of
2 MB). Access tokens are redacted before anything is written.

## Settings

| Setting | Default | Notes |
| --- | --- | --- |
| Memory (MB) | half of physical RAM, clamped to 2048–8192 (4096 when unknown) | maximum heap (`-Xmx`) for the game; minimum 1024 |
| Java path | automatic | executable or installation directory; validated before saving |
| Extra JVM arguments | none | appended after the launcher's defaults (`-XX:+UseG1GC`, `-Dfile.encoding=UTF-8`) |
| Resolution | game default | initial window size `WxH` |
| Keep launcher open | off | keep the window open while the game runs |
| Microsoft client id | empty → `VANTA_MS_CLIENT_ID` | see [Microsoft client id](#microsoft-client-id) |
| Releases base URL | empty = not configured | where `client-latest.json` / `launcher-latest.json` are published; configured by the project maintainers |
| Check for updates automatically | on | at start, when a releases URL is configured |
| Share official Minecraft files | on | reuse verified libraries/assets from the official `.minecraft` directory read-only |
| Developer mode | off | reveals *Offline session* only when allowed by the rules above; enables `--dev-offline` |
| Theme | VANTA Dark | plus a high-contrast toggle |
| Reset to defaults | — | restores every value in this table |

Settings are stored as pretty-printed JSON with a `schemaVersion` in `settings.json`; writes are atomic.

## Updates and rollback

With a releases base URL configured, the launcher fetches `<releasesBaseUrl>/launcher-latest.json` and
`<releasesBaseUrl>/client-latest.json` (the [release manifests](https://github.com/LennardOwnTest123006/VANTA-Client/blob/main/shared/releases/README.md))
and compares the semantic versions with what is installed.

- **Client update**: the banner shows the changelog; *Update* downloads the jar to `cache/updates/`, verifies the
  SHA-256 from the manifest (a manifest without a digest is refused), keeps a copy under
  `versions/vanta-client/<version>/` and activates it in `mods/`. The last three versions remain for **Roll back**.
- **Launcher update**: the installer is downloaded and verified the same way, then the launcher asks for your
  confirmation before handing the file to the operating system. It never runs the installer itself. Rolling the
  launcher back means installing the previous release from GitHub Releases, where every version stays available with
  its checksum.
- An announced release whose manifest still has an empty download URL is shown as *not downloadable yet*.

## Data directory

| System | Location |
| --- | --- |
| Windows | `%APPDATA%\VANTA Launcher` |
| macOS | `~/Library/Application Support/VANTA Launcher` |
| Linux | `$XDG_DATA_HOME/vanta-launcher` or `~/.local/share/vanta-launcher` |

The environment variable `VANTA_LAUNCHER_HOME` overrides all three. The layout (`instances/`, `libraries/`,
`assets/`, `versions/`, `runtimes/`, `logs/`, `cache/`, `settings.json`, `accounts.dat`, `key.bin`) is described in
[Installation → Where files live](installation.md#5-where-files-live).

## Command line reference

The same jar is the GUI and the CLI: any argument other than `--ui` selects the CLI. CI uses it to test the real
install → launch pipeline; you can use it to script installations or diagnose Java problems.

```text
vanta-launcher --install [--client-jar <path>] [--without-client] [--no-assets]
vanta-launcher --launch [--dev-offline --username <name>] [--world <name> | --server <host>] [--exit-after <seconds>]
vanta-launcher --check-java [--java <path>]
vanta-launcher --install-java
vanta-launcher --check-update [--releases-url <url>]
vanta-launcher --print-command [--dev-offline --username <name>]
vanta-launcher --version | --help
common options: --data-dir <path>  --memory <mb>  --java <path>  --resolution <WxH>  --verbose
```

| Command | What it does |
| --- | --- |
| `--install` | installs Minecraft 1.21.11, Fabric Loader, Fabric API and the VANTA Client; `--client-jar` uses a local jar instead of the release manifest, `--without-client` skips VANTA, `--no-assets` skips the asset download |
| `--launch` | launches the installed instance with the active account; `--dev-offline` requires developer mode and `VANTA_DEV_OFFLINE=1`; `--world`/`--server` use Minecraft's quick play; `--exit-after` stops the game after N seconds (CI) |
| `--check-java` | lists detected runtimes and the one that would be used; `--java` probes a specific path |
| `--install-java` | downloads and installs a verified Temurin 21 into the launcher directory |
| `--check-update` | reads the release manifests and prints available updates |
| `--print-command` | prints the exact game command line with secrets redacted, without launching |
| `--version` | prints the launcher version and `Minecraft 1.21.11 · Fabric 0.19.5 · Java 21` |

Exit codes:

| Code | Meaning |
| --- | --- |
| 0 | success |
| 1 | unexpected failure |
| 2 | invalid command line |
| 3 | not configured (Microsoft client id or releases URL missing) |
| 4 | integrity check failed (checksum mismatch) |
| 5 | network or server failure |
| 6 | no Java 21 runtime found |
| 7 | the requested release is not published yet |
| 8 | cancelled |
| 9 | authentication failed or no account available |
| 10 | the game exited with an error |
| 11 | insufficient disk space |

## Environment variables

| Variable | Purpose |
| --- | --- |
| `VANTA_MS_CLIENT_ID` | Microsoft application (client) id used when the setting is empty |
| `VANTA_LAUNCHER_HOME` | overrides the data directory |
| `VANTA_DEV_OFFLINE` | `1` allows development offline accounts together with developer mode (CI only) |
