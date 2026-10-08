# Release process

VANTA ships two installable products with independent versions, each as its own GitHub Release:

| Product | Version source | Release tag | Release page |
| --- | --- | --- | --- |
| **VANTA Client** `x.y.z` | `mod_version` in `client/gradle.properties` | `client-vx.y.z` | `https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-vx.y.z` |
| **VANTA Launcher** `x.y.z` | `launcher_version` in `launcher/gradle.properties` | `launcher-vx.y.z` | `https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-vx.y.z` |
| **Full release zip** `x.y.z` | `version` input of `bundle.yml` (bundles one published client and one published launcher release, see [Full release zip](#full-release-zip)) | `vx.y.z` | `https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/vx.y.z` |

The website has a version too (`website/package.json`) but no GitHub Release; it is deployed to Netlify by hand from a
build of the repository (`VITE_SITE_URL=https://vanta-client.netlify.app npm run build` in `website/`, see
`website/README.md`); a Netlify project linked to the repository would build it from `netlify.toml` instead.
All releases: [github.com/LennardOwnTest123006/VANTA-Client/releases](https://github.com/LennardOwnTest123006/VANTA-Client/releases).

## Release files

Every file of a release, in manifest order (the first one is the primary download). The names are defined once in
`scripts/release/release-assets.mjs`; the workflow, `bump-version.mjs` and the checks all read them, so a renamed or
missing file fails the release.

**`client-v<version>`**

1. `vanta-client-<version>.jar` — the Fabric mod (primary)
2. `vanta-client-<version>-mods.zip` — `mods/vanta-client-<version>.jar`, `mods/fabric-api-0.141.6+1.21.11.jar`,
   the Performance pack jars that may be redistributed (`mods/<file as published on Modrinth>`, newest 1.21.11 Fabric
   build of every member of `PerformancePack.java` whose licence is on the allow-list of
   `scripts/release/performance-pack.mjs`; EntityCulling's licence forbids redistribution, so it stays an in-game
   download), `INSTALL.txt` (the pack list is written into it at build time), `PERFORMANCE-PACK.txt`,
   `THIRD-PARTY-LICENSES.txt` (every licence text; the release fails when one cannot be fetched), `performance-pack.json`
   (`shared/schemas/performance-pack.schema.json`) and `SHA256SUMS` (paths relative to the zip root, `sha256sum -c`
   compatible); resolved and downloaded by `scripts/release/performance-pack.mjs` (size and SHA-512 verified, Iris must
   require exactly the bundled Sodium), zipped by `scripts/release/build-mods-bundle.sh`. The resolved versions appear
   in the job summary and in the GitHub Release notes (*Performance pack in the mods bundle*); none is stored in the
   repository. The resolver fails the release on purpose (a person then decides) when a member whose licence allows
   bundling has no Fabric build for the pinned Minecraft version, when Iris requires a Sodium version other than the
   newest one, when a member is marked incompatible with another bundled member, when a licence text cannot be fetched
   or comes back as an HTML page, or when a download does not match the published size and SHA-512; a member whose
   licence is not on the allow-list is left out with a printed reason, never an error. Every request has a time limit,
   so a stalled connection fails instead of hanging the job. Sodium is under PolyForm Shield 1.0.0: a person reads its
   terms before each release.
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

**A new version's manifest is committed unpublished first.** `bump-version.mjs` creates
`shared/releases/<product>-<version>.json` with every file listed but no `downloadUrl`, `size` or `sha256`, and that
manifest is committed (step 3) before the release workflow runs, because the workflow refuses to release a version
without one. From that commit until the completed manifest is committed (step 7):

- the website's Download page keeps offering the **newest published release** of the product (the newest manifest
  with at least one published file) and only mentions the new version as not published yet; the changelog page marks
  that version the same way. A product without any published release shows "Not published yet — release pending";
- the launcher keeps reading `shared/releases/latest/<product>-latest.json`, which still describes the previous
  release, so installed launchers see nothing new until step 7 updates `latest/`.

## Steps

1. **Versions.** Run `node scripts/release/bump-version.mjs --product <client|launcher|website> --to <version>
   [--date YYYY-MM-DD]`. It updates `client/gradle.properties` (`mod_version`) and `core/.../VantaVersion.java`, or
   `launcher/gradle.properties` (`launcher_version`), or `website/package.json`, and creates the unpublished manifest
   with exactly the release files above (`--date` sets its preliminary `releaseDate`; the workflow overwrites it).
   For 1.0.1: `--product client --to 1.0.1 --date 2026-10-05` and `--product launcher --to 1.0.1 --date 2026-10-05`.
   A release of one product bumps only that product, for example the launcher-only 1.0.2:
   `--product launcher --to 1.0.2 --date 2026-10-05` (the client stays at 1.0.1). For 1.2.0:
   `--product client --to 1.2.0 --date 2026-10-07` and `--product launcher --to 1.2.0 --date 2026-10-07`.
2. **Notes.** Write `website/content/changelog/<product>-<version>.md` and update `CHANGELOG.md`. Launchers load the
   release notes of an update from
   `https://raw.githubusercontent.com/LennardOwnTest123006/VANTA-Client/HEAD/<changelog path of the manifest>` when
   they show it, so a note added later to the notes of a published version (for example for users of an older
   launcher) reaches every running launcher that offers that version as soon as it is committed to the default
   branch, without a new release.
3. **Commit** to the default branch, including the unpublished manifest, and wait for CI to pass. The website keeps
   offering the previous release meanwhile (see [Release manifests](#release-manifests)).
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
9. **Website and launcher pick it up.** Build the website from the commit (`VITE_SITE_URL=https://vanta-client.netlify.app npm run build`
   in `website/`) and deploy the `dist/` folder to Netlify by hand (`website/README.md`); the Download page reads
   `shared/releases/*.json` at build time, now offers the new version and lists every file with size, SHA-256 and its
   link (sizes are decimal with one decimal place, 1 MB = 1,000,000 bytes, as in the GitHub release notes). The
   launcher reads `shared/releases/latest/` through raw.githubusercontent.com (next section). When the Netlify project
   is linked to the repository instead, `netlify.toml` builds whenever `website/`, `docs/`, `shared/`,
   `assets/screenshots/` or `netlify.toml` changed, and always when Netlify has no cached commit or builds the same
   commit again.

A **draft** release has no public links, so the URL verification is skipped and the run prints a warning. After
publishing the draft, run `node scripts/release/verify-manifest.mjs shared/releases/<product>-<version>.json`.

## Full release zip

Besides the two component releases there is a third download, one zip with every published file of a client release
and a launcher release plus the documentation: `VantaClient-<version>-Release.zip`, published as the GitHub Release
`v<version>` (name "VANTA <version> full release"). It is built by `.github/workflows/bundle.yml` from the **already
published** release files, never from a fresh build: the workflow downloads every file the two committed manifests name,
verifies size and SHA-256 against the manifests, zips them with the repository documentation and re-reads the finished
zip before it uploads anything. The website's Download page offers the zip as a third download next to the launcher
`.msi` and the client `.jar`; it reads only `shared/releases/bundles/*.json`, a subfolder the readers of
`shared/releases/*.json` (website release lists, launcher tooling) never see.

**Layout** (one top-level folder, paths relative to it):

| Path | Content |
| --- | --- |
| `README.txt` | what is inside, which file to take on which system, how to verify, links to the two component releases and the website; generated by `build-bundle.mjs` from the manifests and `release-assets.mjs` |
| `CHANGELOG.md`, `LICENSE` | copies of the repository files |
| `SHA256SUMS.txt` | SHA-256 of every other file in the zip, paths relative to the top-level folder (`sha256sum -c` format) |
| `release-notes/client-<cv>.md`, `release-notes/launcher-<lv>.md` | copies of `website/content/changelog/<product>-<version>.md` |
| `docs/*.md` | copies of every `docs/*.md` |
| `client/` | every file of the client manifest, the release's `SHA256SUMS.txt` and `client-<cv>.json`, byte for byte as attached to `client-v<cv>` |
| `launcher/` | every file of the launcher manifest, its `SHA256SUMS.txt` and `launcher-<lv>.json`, byte for byte as attached to `launcher-v<lv>` |

**Bundle manifest.** The zip is described by `shared/releases/bundles/vanta-<version>.json`, validated by
`shared/schemas/bundle-manifest.schema.json`: `schemaVersion` 1, `kind` `"bundle"`, `version`, `clientVersion`,
`launcherVersion`, `minecraftVersion` (from the client manifest), `releaseDate`, `channel` (`stable` when both
components are stable), `file` (`name`, the https `downloadUrl`, `size` and `sha256` of the zip) and `contents` (every file inside the
zip except `SHA256SUMS.txt`, in zip order, with `path`, `size` and `sha256`; at least one entry). Unlike the release
manifests, a bundle manifest is never committed unpublished: the workflow writes it from the real zip after the
upload, and nobody types a value by hand. CI validates every committed bundle manifest against the schema.

**Prerequisites.** `shared/releases/client-<cv>.json` and `shared/releases/launcher-<lv>.json` are committed with
every file published (step 7 above), and the tag `v<version>` does not exist yet.

**Run** *Actions → Bundle → Run workflow* with `version` (the zip's version and tag; usually the version both products
share), optionally `client_version` and `launcher_version` (default: `version`) and `draft`. The `prepare` job refuses
to start when a version is not SemVer, a component manifest is missing, invalid or not published
(`release-assets.mjs check-manifest --published`), or the tag already exists at another commit. Concurrency is per
version.

**What the `publish` job does**, all through `scripts/release/build-bundle.mjs`:

1. `download`: every file of both manifests from its `downloadUrl` plus each release's `SHA256SUMS.txt` and
   `<product>-<version>.json`, streamed to disk through SHA-256; size and digest of every file are compared with the
   manifest (a mismatch fails the run and is not retried, a failed connection is retried), the release's
   `SHA256SUMS.txt` must list exactly the manifest digests and the attached manifest must equal the committed one.
2. `assemble`: stages the layout above, writes `README.txt` and `SHA256SUMS.txt`, zips it with the system `zip`
   binary (the archive is hundreds of megabytes, `lib/zip-writer.mjs` is an in-memory test helper), re-reads the
   finished zip (exact entry list, every digest in `SHA256SUMS.txt` against the bytes) and writes `dist/SHA256SUMS.txt`
   with the zip's own digest.
3. `manifest`: the bundle manifest from the real zip (size, SHA-256, contents from the central directory; every
   component file must be inside with the component manifest's digest), validated against the schema; `verify --local`
   and `sha256sum -c` check it once more.
4. `notes`: the release body (what the zip is, the contents table, how to verify, links to the component releases and
   the download page, the not-code-signed note, the Minecraft/Fabric/Java line).
5. `softprops/action-gh-release`: tag `v<version>` at the built commit, files in order (the zip, `SHA256SUMS.txt`,
   `vanta-<version>.json`), `make_latest: false` so the stable launcher release keeps GitHub's "latest" badge, then
   **downloads the public zip and re-hashes it** (`build-bundle.mjs verify`, with retries) and compares the published
   `SHA256SUMS.txt` and manifest byte for byte. A draft release skips this and prints a warning.

**Get the manifest back and commit it.** As for the component releases, the workflow never pushes to the default
branch. It publishes the folder `bundle-v<version>/` on the branch `ci-artifacts` with `vanta-<version>.json`,
`SHA256SUMS.txt` and `BUNDLE-INFO.txt` (tag, component versions, commit, release, run, `public_url_verified`, and an
`apply=` line). Copy the manifest into place, check it and commit it; the website build then offers the zip:

```bash
git fetch origin ci-artifacts
mkdir -p shared/releases/bundles
git show FETCH_HEAD:bundle-v1.3.0/vanta-1.3.0.json > shared/releases/bundles/vanta-1.3.0.json
node scripts/release/validate-json.mjs shared/schemas/bundle-manifest.schema.json shared/releases/bundles/vanta-1.3.0.json
node scripts/release/build-bundle.mjs verify shared/releases/bundles/vanta-1.3.0.json   # downloads and re-hashes the zip
```

Do not edit the values; if one is wrong, fix the cause and run the workflow again. The same steps run locally
(`download --out <dir>`, `assemble --from <dir> --out dist`, `manifest --zip ... --url ... --dry-run`) to inspect a zip
before a release; they need `zip` and network access to the GitHub Release files.

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
the changelog, downloads the new file to `cache/updates/<version>/<file name>` inside the launcher data directory
(under the exact name of the release file, so `sha256sum -c --ignore-missing SHA256SUMS.txt` works in that folder;
launcher 1.0.0 and 1.0.1 save it as `cache/updates/<version>-<file name>`) and verifies the SHA-256 from the
manifest. The file replaces exactly the kind of installation that is running:

| Running launcher | Update file | After the download |
| --- | --- | --- |
| Windows x64, installed with the `.msi` or `.exe` | `VANTA-Launcher-<version>.msi` (`.exe` if a release has no `.msi`) | the launcher asks, then hands the installer to Windows |
| Windows x64, portable folder | `VANTA-Launcher-<version>-windows-portable.zip` | shown with instructions; the user closes the launcher and extracts the zip into the folder that contains the old `VANTA Launcher` folder (its parent), replacing the existing files (a renamed folder: copy the contents of the zip's `VANTA Launcher` folder into it) |
| Windows x64, `java -jar` | `vanta-launcher-<version>-windows-all.jar` | shown in its folder; started with `java -jar` |
| Linux x64 app image | `VANTA-Launcher-<version>-linux-x64.tar.gz` | shown in its folder; the user extracts it |
| Linux x64, `java -jar` | `vanta-launcher-<version>-linux-all.jar` | shown in its folder; started with `java -jar` |
| macOS, Apple Silicon | `vanta-launcher-<version>-macos-aarch64-all.jar` | shown in its folder; started with `java -jar` |
| other (Intel macOS, Windows or Linux on ARM) | none | the update dialog opens the release page |

The launcher tells a packaged installation (started by the jpackage launcher, which sets `jpackage.app-path`) from a
plain jar, and the portable folder from an installed one by `<directory of jpackage.app-path>/app/vanta-portable.marker`.
The `launcher-windows` job writes that marker (text `portable`) into the app image right before zipping it as
`VANTA-Launcher-<version>-windows-portable.zip` and checks it in the zip; the `.msi` and `.exe` are built from a fresh
jpackage run without it, which the job also checks. The launcher never unpacks or runs the zip, the archive or a jar.

This selection exists from launcher 1.0.1 on. Launcher 1.0.0 picks the update file by system only (the `.msi` on
Windows, also for the portable folder and a jar; the `.tar.gz` on Linux x64, also for a jar), so it offers every
newer version, 1.0.1 up to 1.3.0 alike, that way. Portable and jar users of 1.0.0 are told in
[Installation → Updating](docs/installation.md#updating) and in the `## Notes` of the launcher release notes to
download the portable zip or their jar from the release page instead; keep that note in the notes of each new
launcher version while 1.0.0 is in use.

A client update is offered only when a client is installed (the `vanta-client-<version>.jar` in the instance's
`mods/` folder); a fresh launcher installs the client through the regular installation or *Use with Minecraft
Launcher* instead.

Client updates install exactly `vanta-client-<version>.jar` (never the mods bundle or the Fabric API jar) and keep
the three newest client versions under `versions/vanta-client/` so the Versions screen can roll back (a copy kept of
a jar put into `mods/` by hand is labelled *Local copy from mods/*: it was not downloaded from a release manifest);
rolling back the launcher itself means installing the previous release from GitHub Releases, where every version
stays available with its checksum.

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
