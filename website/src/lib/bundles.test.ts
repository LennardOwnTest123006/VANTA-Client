import { describe, expect, it } from 'vitest';
import { bundleContentPaths, bundleFixture } from '../test/fixtures/bundles';
import {
  BundleManifestError,
  bundleMatches,
  bundleReleasePageUrl,
  bundles,
  isBundlePublished,
  latestBundle,
  loadBundleManifests,
  olderBundles,
  parseBundleManifest,
} from './bundles';

const file = {
  name: 'VantaClient-1.3.0-Release.zip',
  downloadUrl:
    'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/v1.3.0/VantaClient-1.3.0-Release.zip',
  size: 251_000_000,
  sha256: 'A'.repeat(64),
};

/** A manifest as the bundle workflow commits it (shared contract). */
const valid = {
  schemaVersion: 1,
  kind: 'bundle',
  version: '1.3.0',
  clientVersion: '1.3.0',
  launcherVersion: '1.3.0',
  minecraftVersion: '1.21.11',
  releaseDate: '2026-10-08',
  channel: 'stable',
  file,
  contents: [
    { path: 'README.txt', size: 2048, sha256: 'b'.repeat(64) },
    { path: 'client/vanta-client-1.3.0.jar', size: 1_772_213, sha256: 'c'.repeat(64) },
  ],
};

const unpublished = {
  ...valid,
  file: { name: file.name, downloadUrl: '', size: 0, sha256: '' },
  contents: [],
};

describe('parseBundleManifest', () => {
  it('accepts a published bundle and lower-cases its checksums', () => {
    const bundle = parseBundleManifest(valid, 'vanta-1.3.0.json');
    expect(bundle.kind).toBe('bundle');
    expect(bundle.version).toBe('1.3.0');
    expect(bundle.file.name).toBe('VantaClient-1.3.0-Release.zip');
    expect(bundle.file.sha256).toBe('a'.repeat(64));
    expect(bundle.contents).toHaveLength(2);
    expect(bundle.contents[1]?.path).toBe('client/vanta-client-1.3.0.jar');
    expect(isBundlePublished(bundle)).toBe(true);
    expect(bundleReleasePageUrl(bundle)).toBe(
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/v1.3.0',
    );
  });

  it('tolerates an unpublished file (empty URL, size 0, empty checksum) and an empty contents list', () => {
    const bundle = parseBundleManifest(unpublished);
    expect(isBundlePublished(bundle)).toBe(false);
    expect(bundle.contents).toEqual([]);
    expect(bundleReleasePageUrl(bundle)).toBeUndefined();
  });

  it.each([
    ['root', null],
    ['root array', []],
    ['schemaVersion', { ...valid, schemaVersion: 2 }],
    ['kind', { ...valid, kind: 'release' }],
    ['version', { ...valid, version: '1.3' }],
    ['clientVersion', { ...valid, clientVersion: 'latest' }],
    ['launcherVersion', { ...valid, launcherVersion: 1 }],
    ['minecraftVersion', { ...valid, minecraftVersion: '' }],
    ['releaseDate', { ...valid, releaseDate: '08.10.2026' }],
    ['channel', { ...valid, channel: 'alpha' }],
    ['file', { ...valid, file: 'VantaClient-1.3.0-Release.zip' }],
    ['file name', { ...valid, file: { ...file, name: ' ' } }],
    ['file url', { ...valid, file: { ...file, downloadUrl: 'http://insecure/x.zip' } }],
    ['file size', { ...valid, file: { ...file, size: -1 } }],
    ['file sha', { ...valid, file: { ...file, sha256: 'zz' } }],
    ['contents', { ...valid, contents: {} }],
    ['entry', { ...valid, contents: ['README.txt'] }],
    ['entry path', { ...valid, contents: [{ path: '', size: 1, sha256: 'b'.repeat(64) }] }],
    [
      'entry size',
      {
        ...valid,
        contents: [{ path: 'a', size: Number.POSITIVE_INFINITY, sha256: 'b'.repeat(64) }],
      },
    ],
    ['entry sha', { ...valid, contents: [{ path: 'a', size: 1, sha256: 'b'.repeat(63) }] }],
  ])('rejects an invalid %s', (_label, input) => {
    expect(() => parseBundleManifest(input, 'bad.json')).toThrow(BundleManifestError);
    expect(() => parseBundleManifest(input, 'bad.json')).toThrow(/bad\.json/);
  });
});

describe('loadBundleManifests', () => {
  it('unwraps Vite JSON modules and sorts newest first', () => {
    const loaded = loadBundleManifests({
      'b/vanta-1.3.0.json': { default: valid },
      'b/vanta-1.10.0.json': { default: { ...valid, version: '1.10.0' } },
      'b/vanta-1.4.0.json': { ...valid, version: '1.4.0' },
    });
    expect(loaded.map((bundle) => bundle.version)).toEqual(['1.10.0', '1.4.0', '1.3.0']);
  });

  it('names the offending file when a manifest is broken', () => {
    expect(() => loadBundleManifests({ 'shared/releases/bundles/oops.json': {} })).toThrow(
      /oops\.json/,
    );
  });
});

describe('latestBundle', () => {
  const published100 = bundleFixture({ published: true });
  const published110 = bundleFixture({ published: true, version: '1.1.0' });
  const unpublished120 = bundleFixture({ published: false, version: '1.2.0' });
  const beta130 = bundleFixture({ published: true, version: '1.3.0', channel: 'beta' });

  it('is the newest published stable bundle, whatever the input order', () => {
    for (const list of [
      [published100, published110, unpublished120, beta130],
      [beta130, unpublished120, published100, published110],
    ]) {
      expect(latestBundle(list)).toBe(published110);
      expect(latestBundle(list, 'beta')).toBe(beta130);
    }
  });

  it('is undefined while no bundle is published, never an unpublished one', () => {
    expect(latestBundle([])).toBeUndefined();
    expect(latestBundle([unpublished120])).toBeUndefined();
    expect(latestBundle([beta130])).toBeUndefined();
  });
});

describe('olderBundles', () => {
  const published090 = bundleFixture({ published: true, version: '0.9.0' });
  const published100 = bundleFixture({ published: true });
  const published110 = bundleFixture({ published: true, version: '1.1.0' });
  const unpublished120 = bundleFixture({ published: false, version: '1.2.0' });
  const beta130 = bundleFixture({ published: true, version: '1.3.0', channel: 'beta' });

  it('lists the published stable bundles older than the offered one, newest first', () => {
    for (const list of [
      [published090, published100, published110, unpublished120, beta130],
      [beta130, unpublished120, published110, published090, published100],
    ]) {
      expect(olderBundles(list)).toEqual([published100, published090]);
      // The beta channel has one published bundle: nothing older.
      expect(olderBundles(list, 'beta')).toEqual([]);
    }
  });

  it('is empty with at most one published bundle', () => {
    expect(olderBundles([])).toEqual([]);
    expect(olderBundles([published110])).toEqual([]);
    expect(olderBundles([published110, unpublished120])).toEqual([]);
    expect(olderBundles([unpublished120, beta130])).toEqual([]);
  });
});

describe('bundleMatches', () => {
  it('compares both versions inside the zip with the offered ones', () => {
    const bundle = bundleFixture({
      published: true,
      clientVersion: '1.3.0',
      launcherVersion: '1.3.1',
    });
    expect(bundleMatches(bundle, '1.3.0', '1.3.1')).toBe(true);
    expect(bundleMatches(bundle, '1.3.1', '1.3.1')).toBe(false);
    expect(bundleMatches(bundle, '1.3.0', '1.3.0')).toBe(false);
    expect(bundleMatches(bundle, undefined, '1.3.1')).toBe(false);
  });
});

describe('fixtures', () => {
  it('describe the zip layout of the bundle workflow', () => {
    const bundle = bundleFixture({ published: true, version: '1.3.0' });
    expect(bundle.file.name).toBe('VantaClient-1.3.0-Release.zip');
    expect(bundle.file.downloadUrl).toBe(
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/v1.3.0/VantaClient-1.3.0-Release.zip',
    );
    const paths = bundle.contents.map((entry) => entry.path);
    expect(paths).toEqual(bundleContentPaths('1.3.0', '1.3.0'));
    expect(paths).toContain('client/vanta-client-1.3.0.jar');
    expect(paths).toContain('launcher/VANTA-Launcher-1.3.0.msi');
    expect(paths).toContain('release-notes/client-1.3.0.md');
    expect(paths).not.toContain('SHA256SUMS.txt');
    expect(bundleFixture({ published: false }).contents).toEqual([]);
  });
});

describe('repository bundles (shared/releases/bundles)', () => {
  it('load whether or not a bundle manifest exists yet', () => {
    // Today the folder may be empty: the bundle workflow writes the first manifest. Whatever is
    // there must be valid, published bundles must be GitHub release assets of the tag v<version>,
    // and the offered bundle is always a published stable one.
    for (const bundle of bundles) {
      expect(bundle.kind).toBe('bundle');
      expect(bundle.minecraftVersion).toBe('1.21.11');
      if (isBundlePublished(bundle)) {
        expect(bundle.file.downloadUrl).toBe(
          `https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/v${bundle.version}/${bundle.file.name}`,
        );
        expect(bundle.file.size).toBeGreaterThan(0);
        expect(bundle.file.sha256).toMatch(/^[0-9a-f]{64}$/);
        expect(bundle.contents.length).toBeGreaterThan(0);
        expect(bundle.contents.map((entry) => entry.path)).toContain(
          `client/vanta-client-${bundle.clientVersion}.jar`,
        );
        expect(bundle.contents.map((entry) => entry.path)).toContain(
          `launcher/VANTA-Launcher-${bundle.launcherVersion}.msi`,
        );
      }
    }
    const offered = latestBundle(bundles);
    if (offered) {
      expect(isBundlePublished(offered)).toBe(true);
      expect(offered.channel).toBe('stable');
    } else {
      expect(bundles.filter((b) => b.channel === 'stable').some(isBundlePublished)).toBe(false);
    }
  });
});
