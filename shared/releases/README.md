# Release manifests

One JSON file per published version of each product, validated by
[`shared/schemas/release-manifest.schema.json`](../schemas/release-manifest.schema.json):

| File | Product | Read by |
| --- | --- | --- |
| `client-<version>.json` | VANTA Client: the mod jar, the mods bundle and the Fabric API jar | website download page, launcher install/update |
| `launcher-<version>.json` | VANTA Launcher: Windows `.msi`/`.exe`/portable `.zip`, Linux `.tar.gz`, one fat jar per platform | website download page, launcher self-update |
| `latest/<product>-latest.json` | identical copy of the newest **stable** manifest per product (beta releases never write it) | launcher update check (`<releasesBaseUrl>/client-latest.json`, `<releasesBaseUrl>/launcher-latest.json`) |
| `bundles/vanta-<version>.json` | the full release zip `VantaClient-<version>-Release.zip` (both products' published files plus the docs), validated by [`bundle-manifest.schema.json`](../schemas/bundle-manifest.schema.json); written by the bundle workflow (`RELEASE.md`, "Full release zip") | website download page only (the launcher and the readers of `shared/releases/*.json` never see this folder) |

## Release files

`files[]` lists exactly these files, in this order (the first entry is the primary download). The names come from
[`scripts/release/release-assets.mjs`](../../scripts/release/release-assets.mjs), the single source of truth used by
the release workflow, `bump-version.mjs` and the checks below.

Client (`client-v<version>`):

1. `vanta-client-<version>.jar`: the Fabric mod
2. `vanta-client-<version>-mods.zip`: `mods/vanta-client-<version>.jar`, `mods/fabric-api-<fabric api>.jar`, from
   client 1.2.0 on the Performance pack jars whose licences allow redistribution (`mods/<file as published on Modrinth>`,
   resolved live at release time; EntityCulling excluded) with `THIRD-PARTY-LICENSES.txt`, `PERFORMANCE-PACK.txt` and
   `performance-pack.json`, plus `INSTALL.txt` and `SHA256SUMS` (paths relative to the zip root, `sha256sum -c`
   compatible); see `RELEASE.md`
3. `fabric-api-<fabric api>.jar`: the unmodified Fabric API jar from FabricMC (Apache-2.0, contains `LICENSE-fabric-api`)

Launcher (`launcher-v<version>`):

1. `VANTA-Launcher-<version>.msi`: Windows x64 per-user installer with the Java 21 runtime
2. `VANTA-Launcher-<version>.exe`: Windows x64 installer (exe wrapper)
3. `VANTA-Launcher-<version>-windows-portable.zip`: app image with the runtime; run `VANTA Launcher/VANTA Launcher.exe`
4. `vanta-launcher-<version>-windows-all.jar`: fat jar with the JavaFX natives for Windows x64 (needs Java 21)
5. `VANTA-Launcher-<version>-linux-x64.tar.gz`: app image with the runtime; run `VANTA Launcher/bin/VANTA Launcher`
6. `vanta-launcher-<version>-linux-all.jar`: fat jar with the JavaFX natives for Linux x64 (needs Java 21)
7. `vanta-launcher-<version>-macos-aarch64-all.jar`: fat jar with the JavaFX natives for Apple Silicon macOS (needs
   Java 21; built and command-line smoke-tested on a macOS runner, window not tested, unsigned)

A fat jar only carries the JavaFX natives of the platform it was built on, so each platform has its own jar;
`scripts/release/check-launcher-jar.mjs` fails the release when a jar has no JavaFX, natives of another operating
system, or natives for the wrong CPU. Every release also has `SHA256SUMS.txt` and `<product>-<version>.json` attached;
those two are not listed in `files[]`.

Download URLs are always `https://github.com/<owner>/<repo>/releases/download/<product>-v<version>/<file>`.

## Who writes what

- **People** create a manifest with `node scripts/release/bump-version.mjs --product <client|launcher> --to <version>`.
  The new file lists the release files above with an **empty `downloadUrl`, `size: 0` and an empty `sha256`**.
  The website and the launcher read that as "not published yet" and say so; they never invent a link.
- **The release workflow** (`.github/workflows/release.yml`) is the only thing that fills in `downloadUrl`, `size`
  and `sha256`:
  1. `prepare` refuses to start when the manifest does not list exactly the release files in order
     (`release-assets.mjs check-manifest`), so stale entries cannot reach a release.
  2. The build jobs produce the files (client on Linux, including the headless game test; launcher on Windows,
     Linux and Apple Silicon macOS) and check each one.
  3. `publish` runs `build-manifest.mjs` for every file, checks the result (`check-manifest --published`,
     `verify-manifest.mjs --local`), creates the GitHub Release, then **downloads every public URL again and
     re-hashes it** (`verify-manifest.mjs` without `--local`). The run fails if a single link is broken.
     Stable releases also write `latest/<product>-latest.json`; beta releases do not (next section).
- Nobody types a URL, size or digest by hand. If a value is wrong, re-run the workflow.

## How the completed manifest gets back into the repository

The workflow never pushes to the default branch. It hands the manifest back in two ways:

1. **Branch `ci-artifacts`, folder `release-<tag>/`** (always, e.g. `release-client-v1.0.0/`):
   `<product>-<version>.json`, `latest/<product>-latest.json` (stable releases only), `SHA256SUMS.txt` and
   `RELEASE-INFO.txt` (tag, channel, commit, run, whether the public links were verified, and the copy command to
   apply). Client releases also carry the three client files, byte for byte as released. Copy the JSON files into
   place and commit them (a stable release):

   ```bash
   git fetch origin ci-artifacts
   mkdir -p shared/releases/latest
   git show FETCH_HEAD:release-client-v1.0.0/client-1.0.0.json > shared/releases/client-1.0.0.json
   git show FETCH_HEAD:release-client-v1.0.0/latest/client-latest.json > shared/releases/latest/client-latest.json
   ```

2. **A pull request** `chore(release): publish manifest for <tag>` with the same files (optional). It needs the
   repository setting "Allow GitHub Actions to create and approve pull requests"; when it is off, that step fails
   without failing the release.

**`latest/` only follows stable releases.** Installed launchers read `latest/<product>-latest.json` and offer any
newer version they find there as an update; they do not look at `channel`. A beta release (channel `beta`, published
as a GitHub pre-release) therefore never touches `latest/`: `build-manifest.mjs` skips the copy for a beta manifest
(the workflow also passes `--no-latest`), the run fails if `latest/` changed anyway, and neither the hand-back folder
nor the pull request contains a `latest/` file. Copy only `<product>-<version>.json` to `shared/releases/` and leave
`shared/releases/latest/` as it is; never copy a beta manifest into `latest/` by hand.

Once the manifest is on the default branch, the website is built from that commit and deployed to Netlify by hand
(the Download page reads the manifests at build time; see `website/README.md`); once a stable release's `latest/`
file is there too, the launcher's update check sees the release.

## Field reference

| Field | Meaning |
| --- | --- |
| `schemaVersion` | always `1` |
| `product` | `client` or `launcher` |
| `version` | SemVer of the product (`1.0.0`, `1.1.0-beta.1`) |
| `minecraftVersion` | Minecraft Java Edition version; `1.21.11` is enforced for the client |
| `fabricVersion` / `fabricApiVersion` | Fabric Loader / Fabric API the release targets or installs |
| `javaVersion` | required Java major version (`21`) |
| `releaseDate` | `YYYY-MM-DD` (UTC); the workflow sets the real date |
| `channel` | `stable` or `beta` (beta releases are GitHub pre-releases and never written to `latest/`) |
| `files[]` | `name`, `downloadUrl` (https or `""`), `size` (bytes), `sha256` (lower-case hex or `""`); the first entry is the primary artifact |
| `changelog` | path of the release notes: `website/content/changelog/<product>-<version>.md` |
| `notes` | optional short note (known issues) |

## Hosting `latest/`

The launcher's update check fetches `<releasesBaseUrl>/client-latest.json` and `<releasesBaseUrl>/launcher-latest.json`
(the newest stable release of each product). Its built-in default `releasesBaseUrl` is this directory on the default branch:
`https://raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/shared/releases/latest`
(`HEAD` resolves to the default branch). A non-empty `releasesBaseUrl` in the launcher settings wins over the
`VANTA_RELEASES_BASE_URL` environment variable, which wins over the default. Any static host serving the same two
files works. The website reads `shared/releases/*.json` at build time; `VITE_RELEASES_BASE_URL` only adds a link to
such a host.

GitHub has a single "latest release" per repository. Stable launcher releases are marked latest; client releases
never are, so `https://github.com/LennardOwnTest123006/VANTA-Client/releases` lists both and the "latest" badge stays
on the launcher.

## Checking a manifest locally

```bash
node scripts/release/validate-json.mjs shared/schemas/release-manifest.schema.json shared/releases/*.json
node scripts/release/release-assets.mjs check-manifest shared/releases/client-1.0.0.json   # names and order
node scripts/release/verify-manifest.mjs shared/releases/client-1.0.0.json             # downloads and re-hashes
node scripts/release/verify-manifest.mjs shared/releases/client-1.0.0.json --local dist  # against local files
```
