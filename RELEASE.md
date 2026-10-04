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
   `launcher/gradle.properties` (`launcher_version`), `website/package.json`.
2. Write release notes in `website/content/changelog/<product>-<version>.md` and update `CHANGELOG.md`.
3. Commit and tag: `git tag client-v1.0.0` and/or `git tag launcher-v1.0.0`, then push the tag.
4. The `release.yml` workflow builds the tagged product on Linux (client jar) and Windows (launcher installers),
   computes SHA-256 checksums, creates a GitHub Release with the files and `SHA256SUMS.txt`, and commits the
   completed manifest to `shared/releases/` (download URLs point at the GitHub Release assets).
5. Netlify redeploys the website automatically (the download page reads the manifests at build time).

## Verification

Users and the launcher verify downloads with SHA-256. `scripts/release/verify-manifest.mjs <manifest>`
downloads each file and checks size and checksum. The launcher refuses to install a file whose checksum does
not match and never executes downloaded files (the only executable it ever starts is the user's Java runtime).

## Launcher auto-update

The launcher checks `VITE_RELEASES_BASE_URL`/`manifests/launcher-latest.json` (configurable in launcher
settings) at startup, shows the changelog, downloads the new installer to a temporary directory, verifies the
SHA-256 from the manifest and then asks the user to run it. The previous version stays installed until the
installer replaces it, so rolling back means re-running the previous installer, which remains listed in the
"Versions" screen with its checksum.
