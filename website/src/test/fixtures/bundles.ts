import { type BundleManifest, parseBundleManifest } from '../../lib/bundles';
import { FIXTURE_REPO, clientFileNames, fakeSha, fakeSize, launcherFileNames } from './releases';

/**
 * Test-only bundle manifests (`shared/releases/bundles/vanta-<version>.json`) with the zip layout of
 * the bundle workflow. The "published" variant carries FAKE sizes and checksums so the download page
 * can render the bundle card without a real zip; none of these values exists outside the tests.
 */

/** `VantaClient-<version>-Release.zip` */
export function bundleFileName(version: string): string {
  return `VantaClient-${version}-Release.zip`;
}

/** Same shape as the bundle workflow writes: the zip is the asset of the GitHub Release `v<version>`. */
export function bundleDownloadUrl(version: string): string {
  return `${FIXTURE_REPO}/releases/download/v${version}/${bundleFileName(version)}`;
}

/**
 * Paths inside the zip, relative to its top-level folder and in zip order, without SHA256SUMS.txt
 * (which the manifest never lists).
 */
export function bundleContentPaths(clientVersion: string, launcherVersion: string): string[] {
  return [
    'README.txt',
    'CHANGELOG.md',
    'LICENSE',
    `release-notes/client-${clientVersion}.md`,
    `release-notes/launcher-${launcherVersion}.md`,
    'docs/installation.md',
    'docs/troubleshooting.md',
    ...clientFileNames(clientVersion).map((name) => `client/${name}`),
    'client/SHA256SUMS.txt',
    `client/client-${clientVersion}.json`,
    ...launcherFileNames(launcherVersion).map((name) => `launcher/${name}`),
    'launcher/SHA256SUMS.txt',
    `launcher/launcher-${launcherVersion}.json`,
  ];
}

export interface BundleFixtureOptions {
  /** Fill the zip with a fake URL, size and checksum and list its contents. */
  readonly published: boolean;
  /** Bundle version; defaults to `1.0.0`. */
  readonly version?: string;
  /** Client version inside the zip; defaults to the bundle version. */
  readonly clientVersion?: string;
  /** Launcher version inside the zip; defaults to the bundle version. */
  readonly launcherVersion?: string;
  readonly channel?: 'stable' | 'beta';
  /** Replaces the URL builder, e.g. for a mirror that is not a GitHub release. */
  readonly url?: (version: string) => string;
}

export function bundleFixture({
  published,
  version = '1.0.0',
  clientVersion = version,
  launcherVersion = version,
  channel = 'stable',
  url = bundleDownloadUrl,
}: BundleFixtureOptions): BundleManifest {
  const contents = bundleContentPaths(clientVersion, launcherVersion);
  return parseBundleManifest(
    {
      schemaVersion: 1,
      kind: 'bundle',
      version,
      clientVersion,
      launcherVersion,
      minecraftVersion: '1.21.11',
      releaseDate: '2026-10-04',
      channel,
      file: published
        ? {
            name: bundleFileName(version),
            downloadUrl: url(version),
            size: 240_000_000,
            sha256: fakeSha(40),
          }
        : { name: bundleFileName(version), downloadUrl: '', size: 0, sha256: '' },
      contents: published
        ? contents.map((path, index) => ({ path, size: fakeSize(index), sha256: fakeSha(index) }))
        : [],
    },
    `fixture vanta-${version}.json`,
  );
}
