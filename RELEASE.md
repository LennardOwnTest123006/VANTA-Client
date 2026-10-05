# Release process

VANTA ships two installable products with independent versions, each as its own GitHub Release:

| Product | Version source | Release tag | Release page |
| --- | --- | --- | --- |
| **VANTA Client** `x.y.z` | `mod_version` in `client/gradle.properties` | `client-vx.y.z` | `https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-vx.y.z` |
| **VANTA Launcher** `x.y.z` | `launcher_version` in `launcher/gradle.properties` | `launcher-vx.y.z` | `https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-vx.y.z` |

The website has a version too (`website/package.json`) but no GitHub Release; Netlify deploys it from the repository.
All releases: [github.com/LennardOwnTest123006/VANTA-Client/releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases).

## Release files

Every file of a release, in manifest order (the first one is the primary download). The names are defined once in
`scripts/release/release-assets.mjs`; the workflow, `bump-version.mjs` and the checks all read them, so a renamed or
missing file fails the release.

**`client-v<version>`**

1. `vanta-client-<version>.jar` — the Fabric mod (primary)
2. `vanta-client-<version>-mods.zip` — `mods/vanta-client-<version>.jar`, `mods/fabric-api-0.141.6+1.21.11.jar`,
   `INSTALL.txt` and `SHA256SUMS` (paths relative to the zip root, `sha256sum -c` compatible); built by
   `scripts/release/build-mods-bundle.sh`
3. `fabric-api-0.141.6+1.21.11.jar` — the unmodified FabricMC Fabric API jar (Apache-2.0, contains
   `LICENSE-fabric-api`), taken from the client build's Gradle cache by `scripts/release/fabric-api-jar.mjs`

**`launcher-v<version>`**

1. `VANTA-Launcher-<version>.msi` — Windows x64 per-user installer with the Java 21 runtime (primary)
2. `VANTA-Launcher-<version>.exe` — Windows x64 installer (exe wrapper)
3. `VANTA-Launcher-<version>-windows-portable.zip` — jpackage app image with the runtime; run
   `VANTA Launcher/VANTA Launcher.exe`
4. `vanta-launcher-<version>-windows-all.jar` — fat jar with the JavaFX natives for Windows x64 (needs Java 21)
5. `VANTA-Launcher-<version>-linux-x64.tar.gz` — jpackage app image with the runtime; run
   `VANTA Launcher/bin/VANTA Launcher`
6. `vanta-launcher-<version>-linux-all.jar` — fat jar with the JavaFX natives for Linux x64 (needs Java 21)
7. `vanta-launcher-<version>-macos-aarch64-all.jar` — fat jar with the JavaFX natives for Apple Silicon macOS
   (needs Java 21; built and command-line smoke-tested on a macOS runner; window not tested; unsigned)

A fat jar contains the JavaFX natives of the platform it was built on only, so the launcher is built on three runners
and the Gradle output `launcher/build/libs/vanta-launcher-<version>-all.jar` is published as three renamed copies.
`scripts/release/check-launcher-jar.mjs` fails the release when a jar has no JavaFX, natives of another operating
system or natives for the wrong CPU.

Both releases also carry `SHA256SUMS.txt` (every file above) and the release manifest `<product>-<version>.json`;
those two are not listed in the manifest's `files[]`. The public URL of every file is
`https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/<tag>/<file>`.

## Release manifests

Every release is described by a JSON manifest in `shared/releases/`, validated by
`shared/schemas/release-manifest.schema.json`. Before the release it looks like this:

```json
{
  "schemaVersion": 1,
  "product": "client",
  "version": "1.0.0",
  "minecraftVersion": "1.21.11",
  "fabricVersion": "0.19.5",
  "fabricApiVersion": "0.141.6+1.21.11",
  "javaVersion": 21,
  "releaseDate": "2026-10-04",
  "channel": "stable",
  "files": [
    { "name": "vanta-client-1.0.0.jar", "downloadUrl": "", "size": 0, "sha256": "" },
    { "name": "vanta-client-1.0.0-mods.zip", "downloadUrl": "", "size": 0, "sha256": "" },
    { "name": "fabric-api-0.141.6+1.21.11.jar", "downloadUrl": "", "size": 0, "sha256": "" }
  ],
  "changelog": "website/content/changelog/client-1.0.0.md"
}
```

`downloadUrl`, `size` and `sha256` are **filled in by the release workflow** from the uploaded files, and the
workflow sets `releaseDate`. They are never typed by hand. An empty `downloadUrl` means "not published yet"; the
website and the launcher display exactly that. For a **stable** release the workflow also writes
`shared/releases/latest/<product>-latest.json`, an identical copy of the newest manifest, which is what the launcher
reads. A **beta** release (GitHub pre-release) leaves `shared/releases/latest/` unchanged, so installed launchers are
never offered a pre-release. Field reference and local checks: [`shared/releases/README.md`](shared/releases/README.md).

## Steps

1. **Versions.** Run `node scripts/release/bump-version.mjs --product <client|launcher|website> --to <version>`. It
   updates `client/gradle.properties` (`mod_version`) and `core/.../VantaVersion.java`, or
   `launcher/gradle.properties` (`launcher_version`), or `website/package.json`, and creates the unpublished manifest
   with exactly the release files above.
2. **Notes.** Write `website/content/changelog/<product>-<version>.md` and update `CHANGELOG.md`.
3. **Commit** to the default branch and wait for CI to pass.
4. **Start the release workflow** (`.github/workflows/release.yml`), either
   - *Actions → Release → Run workflow* with `product`, `version` (must equal the pinned version), `channel`
     (`stable` or `beta`; beta becomes a GitHub pre-release) and `draft`. The workflow builds the selected commit and
     creates the tag `<product>-v<version>` there; or
   - push a tag `client-v<version>` or `launcher-v<version>` (a version with a `-suffix` is released as beta).

   `prepare` refuses to start when the version does not match the pinned one, the manifest is missing, invalid or
   does not list exactly the release files in order, or the tag already exists at another commit (delete the old
   release and tag with `gh release delete <tag> --cleanup-tag --yes`, or release a new version).

   **Concurrency** is per product: a client release and a launcher release may run at the same time; a second
   release of the same product waits for the first (`cancel-in-progress: false`).
5. **What the workflow does.**
   - `client` (Ubuntu): builds and tests the client, checks the jar metadata (Minecraft `~1.21.11`, version), runs the
     headless production game test, stages the Fabric API jar (checked against the checksum Gradle recorded for the
     download, its `fabric.mod.json` and license) and builds the mods bundle (verified with `sha256sum -c` before and
     after zipping).
   - `launcher-windows`: fat jar (JavaFX natives checked, `--version`), the app image zipped as the portable build
     (its bundled runtime runs the jar with `--version`), then the `.msi` and the `.exe`. The installers are built,
     not installed.
   - `launcher-linux`: fat jar (natives checked, `--version`, `--check-java`) and the app image packed as `.tar.gz`
     (`bin/VANTA Launcher --version`, archive listing checked).
   - `launcher-macos` (Apple Silicon runner, `uname -m` must be `arm64`): fat jar for `mac-aarch64` (every JavaFX native
     must be an arm64 Mach-O library), `--version` and `--check-java`. Unit tests also run there but are informational.
   - `publish`: checks that the downloaded artifacts are exactly the release files, runs `build-manifest.mjs` for each
     file with its future public URL, validates the manifest (`check-manifest --published`, `verify-manifest.mjs
     --local dist`, `sha256sum -c`), creates the GitHub Release with the files in manifest order plus
     `SHA256SUMS.txt` and the manifest, then **downloads every public URL again and re-hashes it**
     (`verify-manifest.mjs` against the live links, with retries) and compares the published `SHA256SUMS.txt` and
     manifest byte for byte. A single mismatch fails the run. Only stable launcher releases are marked as the
     repository's "latest release"; client releases never are.
6. **Get the completed manifests back.** The workflow never pushes to the default branch. It hands the results back
   in two ways:
   - always on the branch `ci-artifacts`, folder (label) `release-<tag>/` (for example `release-client-v1.0.0/`):
     `<product>-<version>.json`, `latest/<product>-latest.json` (stable releases only), `SHA256SUMS.txt` and
     `RELEASE-INFO.txt` (tag, commit, run, channel, `public_urls_verified`, and an `apply=` line with the exact copy
     commands); client releases also carry the three client files byte for byte;
   - optionally as a pull request `chore(release): publish manifest for <tag>`. This needs the repository setting
     *Settings → Actions → General → Allow GitHub Actions to create and approve pull requests*; when it is off the step
     fails without failing the release.
7. **Commit them.** For a stable release the maintainer copies the two JSON files into `shared/releases/` and
   `shared/releases/latest/` and commits them to the default branch (or merges the pull request). For a beta release
   only `shared/releases/<product>-<version>.json` is copied; `shared/releases/latest/` stays unchanged. The `apply=`
   line in `RELEASE-INFO.txt` says which files to copy. For the stable `client-v1.0.0`:

   ```bash
   git fetch origin ci-artifacts
   mkdir -p shared/releases/latest
   git show FETCH_HEAD:release-client-v1.0.0/client-1.0.0.json > shared/releases/client-1.0.0.json
   git show FETCH_HEAD:release-client-v1.0.0/latest/client-latest.json > shared/releases/latest/client-latest.json
   node scripts/release/release-assets.mjs check-manifest shared/releases/client-1.0.0.json --published \
     --repo LennardOwnTest123006/VANTA-Client --tag client-v1.0.0
   ```

   Do not edit the values; if one is wrong, fix the cause and run the workflow again.
8. **Date the release notes** in the same commit. The workflow sets `releaseDate` to the day it ran (UTC), so the date
   is only known now. Read it from the committed manifest
   (`node -p "require('./shared/releases/client-1.0.0.json').releaseDate"`) and use exactly that value:
   - `CHANGELOG.md`: change the version heading to `## [<version>] - <releaseDate>`, as Keep a Changelog requires.
     When the client and the launcher of one version are released on different days, put each date on its product
     heading instead;
   - `website/content/changelog/<product>-<version>.md`: set `date:` in the front matter to the same value.
9. **Website and launcher pick it up.** Netlify rebuilds the website from the commit (the Download page reads
   `shared/releases/*.json` at build time and lists every file with size, SHA-256 and its link). The launcher reads
   `shared/releases/latest/` through raw.githubusercontent.com (next section).

A **draft** release has no public links, so the URL verification is skipped and the run prints a warning. After
publishing the draft, run `node scripts/release/verify-manifest.mjs shared/releases/<product>-<version>.json`.

## Hosting `latest/` (launcher releases URL)

The launcher's built-in releases base URL is

```text
https://raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/shared/releases/latest
```

`HEAD` resolves to the repository's default branch, so committing `client-latest.json` and `launcher-latest.json`
there (step 7) is the publication for the launcher; nothing else needs to be hosted. A non-empty `releasesBaseUrl` in
the launcher settings (or `--releases-url`) wins over the environment variable `VANTA_RELEASES_BASE_URL`, which wins
over this default. Until the files exist at that URL, the launcher reports "not published yet".

## Verification

Users and the launcher verify downloads with SHA-256. `node scripts/release/verify-manifest.mjs <manifest>` downloads
each file and checks size and checksum (`--local <dir>` checks local files instead). The launcher refuses to install a
file whose checksum does not match and never executes downloaded files (the only executable it ever starts is the
user's Java runtime, and a Windows installer only after the user confirmed).

## Launcher auto-update

At startup the launcher fetches `launcher-latest.json` and `client-latest.json` from the releases URL in use, shows
the changelog, downloads the new file to `cache/updates/` inside the launcher data directory and verifies the SHA-256
from the manifest. The file depends on the platform:

| Platform | Update file | After the download |
| --- | --- | --- |
| Windows | `VANTA-Launcher-<version>.msi` (`.exe` if a release has no `.msi`) | the launcher asks, then hands the installer to Windows |
| Linux x64 | `VANTA-Launcher-<version>-linux-x64.tar.gz` | shown in its folder; the user extracts it |
| macOS, Apple Silicon | `vanta-launcher-<version>-macos-aarch64-all.jar` | shown in its folder; started with `java -jar` |
| other (Intel macOS, Linux on ARM) | none | the update dialog opens the release page |

Client updates install exactly `vanta-client-<version>.jar` (never the mods bundle or the Fabric API jar) and keep
the last three verified jars under `versions/vanta-client/` so the Versions screen can roll back; rolling back the
launcher itself means installing the previous release from GitHub Releases, where every version stays available with
its checksum.

## Signing (future)

Release artifacts are **not code-signed** yet. Integrity is guaranteed by SHA-256 checksums that the workflow
computes from the uploaded files, checks against the public download links and publishes in three places (the GitHub
Release page, `SHA256SUMS.txt` and the release manifest); the launcher refuses a file whose checksum is missing or does
not match, and the website shows no download link without a published file. Consequences and plan:

- Windows SmartScreen may show "Windows protected your PC" for the unsigned `.msi`/`.exe` installers and the portable
  app; the documentation tells users to choose *More info → Run anyway* only after the checksum matched.
- The macOS jar is neither signed nor notarized, and there is no `.app`/`.dmg`.
- Authenticode (Windows) and notarization (macOS) require a paid certificate / developer account held by the
  project maintainers. When one exists, signing becomes an additional step in the `launcher-windows` (and a future
  macOS packaging) job of `release.yml` with the certificate stored as a GitHub Actions secret; nothing else in the
  release process changes.
- Until then no secrets beyond the automatic `GITHUB_TOKEN` are required for a release, so anyone with write access
  to the repository can cut one.
