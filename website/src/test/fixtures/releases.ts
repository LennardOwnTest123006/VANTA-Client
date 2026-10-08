import { type ReleaseManifest, parseReleaseManifest } from '../../lib/releases';

/**
 * Test-only release manifests with the file names of the release contract (RELEASE.md,
 * scripts/release/release-assets.mjs). The "published" variants carry FAKE sizes and checksums so
 * the published state of the download page can be rendered without a real release; none of these
 * values exists anywhere outside the tests.
 */

export const FIXTURE_REPO = 'https://github.com/LennardOwnTest123006/VANTA-Client';

/** Client release file names of a version (release contract). */
export function clientFileNames(version: string): readonly string[] {
  return [
    `vanta-client-${version}.jar`,
    `vanta-client-${version}-mods.zip`,
    'fabric-api-0.141.6+1.21.11.jar',
  ];
}

/** Launcher release file names of a version (release contract). */
export function launcherFileNames(version: string): readonly string[] {
  return [
    `VANTA-Launcher-${version}.msi`,
    `VANTA-Launcher-${version}.exe`,
    `VANTA-Launcher-${version}-windows-portable.zip`,
    `vanta-launcher-${version}-windows-all.jar`,
    `VANTA-Launcher-${version}-linux-x64.tar.gz`,
    `vanta-launcher-${version}-linux-all.jar`,
    `vanta-launcher-${version}-macos-aarch64-all.jar`,
  ];
}

export const CLIENT_FILE_NAMES = clientFileNames('1.0.0');

export const LAUNCHER_FILE_NAMES = launcherFileNames('1.0.0');

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
  /** Release version; file names, tag and changelog path follow it. Defaults to `1.0.0`. */
  readonly version?: string;
  /** Names of files that stay unpublished even when `published` is true. */
  readonly unpublished?: readonly string[];
  /** Replaces the URL builder, e.g. for a mirror that is not a GitHub release. */
  readonly url?: (tag: string, name: string) => string;
  /** Release date `YYYY-MM-DD`; defaults to `2026-10-04`. */
  readonly releaseDate?: string;
}

function manifest(
  product: 'client' | 'launcher',
  names: readonly string[],
  {
    published,
    version = '1.0.0',
    unpublished = [],
    url = fixtureDownloadUrl,
    releaseDate = '2026-10-04',
  }: FixtureOptions,
): ReleaseManifest {
  const tag = `${product}-v${version}`;
  return parseReleaseManifest(
    {
      schemaVersion: 1,
      product,
      version,
      minecraftVersion: '1.21.11',
      fabricVersion: '0.19.5',
      fabricApiVersion: '0.141.6+1.21.11',
      javaVersion: 21,
      releaseDate,
      channel: 'stable',
      files: names.map((name, index) =>
        published && !unpublished.includes(name)
          ? { name, downloadUrl: url(tag, name), size: fakeSize(index), sha256: fakeSha(index) }
          : { name, downloadUrl: '', size: 0, sha256: '' },
      ),
      changelog: `website/content/changelog/${product}-${version}.md`,
    },
    `fixture ${product}-${version}.json`,
  );
}

export function clientFixture(options: FixtureOptions): ReleaseManifest {
  return manifest('client', clientFileNames(options.version ?? '1.0.0'), options);
}

export function launcherFixture(options: FixtureOptions): ReleaseManifest {
  return manifest('launcher', launcherFileNames(options.version ?? '1.0.0'), options);
}
