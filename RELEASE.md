# Release process

VANTA ships two installable products with independent versions:

- **VANTA Client** `x.y.z` — the Fabric mod jar (`vanta-client-x.y.z.jar`)
- **VANTA Launcher** `x.y.z` — Windows installer (`VANTA-Launcher-x.y.z.msi` / `.exe`) and portable jar

## Release manifests

Every release is described by a JSON manifest in `shared/releases/` validated by
`shared/schemas/release-manifest.schema.json`:

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
    { "name": "vanta-client-1.0.0.jar", "downloadUrl": "", "size": 0, "sha256": "" }
  ],
  "changelog": "website/content/changelog/client-1.0.0.md"
}
```

`downloadUrl`, `size` and `sha256` are **filled in by the release workflow** from the actual uploaded files.
They are never typed by hand. A manifest with an empty `downloadUrl` means "not published yet" and the website
and launcher display exactly that.

## Steps

1. Update versions: `client/gradle.properties` (`mod_version`), `core/.../VantaVersion.java`,
   `launcher/gradle.properties` (`launcher_version`), `website/package.json` — or run
   `node scripts/release/bump-version.mjs --product <client|launcher|website> --to <version>`, which also
   creates the new, unpublished manifest in `shared/releases/`.
2. Write release notes in `website/content/changelog/<product>-<version>.md` and update `CHANGELOG.md`.
3. Commit and tag: `git tag client-v1.0.0` and/or `git tag launcher-v1.0.0`, then push the tag.
4. The `release.yml` workflow builds the tagged product on Linux (client jar; launcher app image) and Windows
   (launcher installers), computes SHA-256 checksums, creates a GitHub Release with the files and `SHA256SUMS.txt`,
   and opens a pull request with the completed manifest in `shared/releases/` plus
   `shared/releases/latest/<product>-latest.json` (download URLs point at the GitHub Release assets). It needs no
   secrets beyond `GITHUB_TOKEN`; the repository setting "Allow GitHub Actions to create and approve pull requests"
   must be on.
5. Netlify redeploys the website automatically (the download page reads the manifests at build time).

## Verification

Users and the launcher verify downloads with SHA-256. `scripts/release/verify-manifest.mjs <manifest>`
downloads each file and checks size and checksum. The launcher refuses to install a file whose checksum does
not match and never executes downloaded files (the only executable it ever starts is the user's Java runtime).

## Launcher auto-update

The launcher checks `<releasesBaseUrl>/launcher-latest.json` and `<releasesBaseUrl>/client-latest.json` (the
base URL is a launcher setting; empty means "not configured") at startup, shows the changelog, downloads the new
file to `cache/updates/` inside the launcher data directory, verifies the SHA-256 from the manifest and then asks
the user before opening the installer. Client updates keep the last three verified jars under
`versions/vanta-client/` so the Versions screen can roll back; rolling back the launcher itself means installing
the previous installer from GitHub Releases, where every version stays available with its checksum.

## Signing (future)

Release artifacts are **not code-signed** yet. Integrity is guaranteed by SHA-256 checksums that the workflow
computes from the uploaded files and publishes in three places (the GitHub Release page, `SHA256SUMS.txt` and the
release manifest), and both the launcher and the website refuse to show or install a file whose checksum is
missing or does not match. Consequences and plan:

- Windows SmartScreen may warn about the unsigned `.msi`/`.exe` installer; the documentation explains how to verify
  the checksum before continuing.
- Authenticode (Windows) and notarization (macOS) require a paid certificate / developer account held by the
  project maintainers. When one exists, signing becomes an additional step in the `launcher-windows` job of
  `release.yml` with the certificate stored as a GitHub Actions secret; nothing else in the release process changes.
- Until then no secrets are required for a release at all, so anyone with write access to the repository can cut
  one from a clean machine.
