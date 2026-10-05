import { type ReleaseManifest, parseReleaseManifest } from '../../lib/releases';

/**
 * Test-only release manifests with the file names of the release contract (RELEASE.md,
 * scripts/release/release-assets.mjs). The "published" variants carry FAKE sizes and checksums so
 * the published state of the download page can be rendered without a real release; none of these
 * values exists anywhere outside the tests.
 */

export const FIXTURE_REPO = 'https://github.com/LennardOwnTest123006/VANTA-Client';

export const CLIENT_FILE_NAMES = [
  'vanta-client-1.0.0.jar',
  'vanta-client-1.0.0-mods.zip',
  'fabric-api-0.141.6+1.21.11.jar',
] as const;

export const LAUNCHER_FILE_NAMES = [
  'VANTA-Launcher-1.0.0.msi',
  'VANTA-Launcher-1.0.0.exe',
  'VANTA-Launcher-1.0.0-windows-portable.zip',
  'vanta-launcher-1.0.0-windows-all.jar',
  'VANTA-Launcher-1.0.0-linux-x64.tar.gz',
  'vanta-launcher-1.0.0-linux-all.jar',
  'vanta-launcher-1.0.0-macos-aarch64-all.jar',
] as const;

/** Fake, deterministic 64-hex "checksum" for the n-th fixture file. */
export function fakeSha(index: number): string {
  const seed = (index + 1).toString(16);
  return seed.repeat(Math.ceil(64 / seed.length)).slice(0, 64);
}

/** Fake size in bytes for the n-th fixture file (1.2 MB, 2.4 MB, …). */
export function fakeSize(index: number): number {
  return (index + 1) * 1_200_000;
}

/** Same shape as `releaseDownloadUrl` in scripts/release/release-assets.mjs (names are not encoded). */
export function fixtureDownloadUrl(tag: string, name: string): string {
  return `${FIXTURE_REPO}/releases/download/${tag}/${name}`;
}

interface FixtureOptions {
  /** Fill every file with a fake URL, size and checksum. */
  readonly published: boolean;
  /** Names of files that stay unpublished even when `published` is true. */
  readonly unpublished?: readonly string[];
  /** Replaces the URL builder, e.g. for a mirror that is not a GitHub release. */
  readonly url?: (tag: string, name: string) => string;
}

function manifest(
  product: 'client' | 'launcher',
  names: readonly string[],
  { published, unpublished = [], url = fixtureDownloadUrl }: FixtureOptions,
): ReleaseManifest {
  const tag = `${product}-v1.0.0`;
  return parseReleaseManifest(
    {
      schemaVersion: 1,
      product,
      version: '1.0.0',
      minecraftVersion: '1.21.11',
      fabricVersion: '0.19.5',
      fabricApiVersion: '0.141.6+1.21.11',
      javaVersion: 21,
      releaseDate: '2026-10-04',
      channel: 'stable',
      files: names.map((name, index) =>
        published && !unpublished.includes(name)
          ? { name, downloadUrl: url(tag, name), size: fakeSize(index), sha256: fakeSha(index) }
          : { name, downloadUrl: '', size: 0, sha256: '' },
      ),
      changelog: `website/content/changelog/${product}-1.0.0.md`,
    },
    `fixture ${product}-1.0.0.json`,
  );
}

export function clientFixture(options: FixtureOptions): ReleaseManifest {
  return manifest('client', CLIENT_FILE_NAMES, options);
}

export function launcherFixture(options: FixtureOptions): ReleaseManifest {
  return manifest('launcher', LAUNCHER_FILE_NAMES, options);
}
