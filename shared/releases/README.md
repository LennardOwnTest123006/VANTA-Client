# Release manifests

One JSON file per published version of each product, validated by
[`shared/schemas/release-manifest.schema.json`](../schemas/release-manifest.schema.json):

| File | Product | Read by |
| --- | --- | --- |
| `client-<version>.json` | VANTA Client (Fabric mod jar) | website download page, launcher install/update |
| `launcher-<version>.json` | VANTA Launcher (Windows `.msi`/`.exe`, portable `-all.jar`, Linux `.tar.gz`) | website download page, launcher self-update |
| `latest/<product>-latest.json` | copy of the newest published manifest per product | launcher update check (`<releasesBaseUrl>/client-latest.json`, `<releasesBaseUrl>/launcher-latest.json`) |

## Who writes what

- **People** create a manifest with `node scripts/release/bump-version.mjs --product <client|launcher> --to <version>`.
  The new file lists the expected file names with an **empty `downloadUrl`, `size: 0` and an empty `sha256`**.
  The website and the launcher read that as "not published yet" and say so; they never invent a link.
- **The release workflow** (`.github/workflows/release.yml`) is the only thing that fills in `downloadUrl`,
  `size` and `sha256`. It runs `scripts/release/build-manifest.mjs` against the artifacts it just built,
  uploads them to the GitHub Release, verifies the manifest with `scripts/release/verify-manifest.mjs`
  and opens a pull request with the completed manifest and `latest/<product>-latest.json`.
- Nobody types a URL, size or digest by hand. If a value is wrong, re-run the workflow.

## Field reference

| Field | Meaning |
| --- | --- |
| `schemaVersion` | always `1` |
| `product` | `client` or `launcher` |
| `version` | SemVer of the product (`1.0.0`, `1.1.0-beta.1`) |
| `minecraftVersion` | Minecraft Java Edition version; `1.21.11` is enforced for the client |
| `fabricVersion` / `fabricApiVersion` | Fabric Loader / Fabric API the release targets or installs |
| `javaVersion` | required Java major version (`21`) |
| `releaseDate` | `YYYY-MM-DD` (UTC) |
| `channel` | `stable` or `beta` (beta releases are GitHub pre-releases) |
| `files[]` | `name`, `downloadUrl` (https or `""`), `size` (bytes), `sha256` (lower-case hex or `""`); the first entry is the primary artifact |
| `changelog` | path of the release notes: `website/content/changelog/<product>-<version>.md` |
| `notes` | optional short note (known issues) |

Download URLs point at GitHub Release assets:
`https://github.com/<owner>/<repo>/releases/download/<product>-v<version>/<file>`.

## Hosting `latest/`

The launcher's update check fetches `<releasesBaseUrl>/client-latest.json` and `<releasesBaseUrl>/launcher-latest.json`.
`releasesBaseUrl` is configuration (launcher settings, or `VITE_RELEASES_BASE_URL` for the website), for example the
raw URL of this directory on the default branch:
`https://raw.githubusercontent.com/<owner>/<repo>/main/shared/releases/latest`. Any static host serving the same two
files works. When no URL is configured the launcher reports "not configured" instead of guessing.

## Checking a manifest locally

```bash
node scripts/release/validate-json.mjs shared/schemas/release-manifest.schema.json shared/releases/*.json
node scripts/release/verify-manifest.mjs shared/releases/client-1.0.0.json            # downloads and re-hashes
node scripts/release/verify-manifest.mjs shared/releases/client-1.0.0.json --local dist  # against local files
```
