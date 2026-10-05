import { describe, expect, it } from 'vitest';
import { clientFixture, launcherFixture } from '../test/fixtures/releases';
import {
  compareVersionsDesc,
  githubReleasePageUrl,
  isPublished,
  latestRelease,
  loadReleaseManifests,
  parseReleaseManifest,
  pickFile,
  ReleaseManifestError,
  releasePageUrl,
  releases,
} from './releases';

const valid = {
  schemaVersion: 1,
  product: 'client',
  version: '1.0.0',
  minecraftVersion: '1.21.11',
  fabricVersion: '0.19.5',
  fabricApiVersion: '0.141.6+1.21.11',
  javaVersion: 21,
  releaseDate: '2026-10-04',
  channel: 'stable',
  files: [{ name: 'vanta-client-1.0.0.jar', downloadUrl: '', size: 0, sha256: '' }],
  changelog: 'website/content/changelog/client-1.0.0.md',
};

describe('parseReleaseManifest', () => {
  it('accepts a manifest matching RELEASE.md', () => {
    const manifest = parseReleaseManifest(valid, 'client-1.0.0.json');
    expect(manifest.product).toBe('client');
    expect(manifest.files[0]?.name).toBe('vanta-client-1.0.0.jar');
    expect(manifest.javaVersion).toBe(21);
    expect(isPublished(manifest)).toBe(false);
  });

  it('lower-cases checksums and accepts published files', () => {
    const published = parseReleaseManifest({
      ...valid,
      files: [
        {
          name: 'x.jar',
          downloadUrl: 'https://github.com/o/r/releases/download/v1/x.jar',
          size: 1234,
          sha256: 'A'.repeat(64),
        },
      ],
    });
    expect(published.files[0]?.sha256).toBe('a'.repeat(64));
    expect(isPublished(published)).toBe(true);
  });

  it.each([
    ['root', null],
    ['schemaVersion', { ...valid, schemaVersion: 2 }],
    ['product', { ...valid, product: 'server' }],
    ['version', { ...valid, version: '1.0' }],
    ['releaseDate', { ...valid, releaseDate: '04.10.2026' }],
    ['channel', { ...valid, channel: 'nightly' }],
    ['files', { ...valid, files: [] }],
    ['file url', { ...valid, files: [{ ...valid.files[0], downloadUrl: 'http://insecure' }] }],
    ['file sha', { ...valid, files: [{ ...valid.files[0], sha256: 'zz' }] }],
    ['file size', { ...valid, files: [{ ...valid.files[0], size: -1 }] }],
    ['changelog type', { ...valid, changelog: 5 }],
  ])('rejects an invalid %s', (_label, input) => {
    expect(() => parseReleaseManifest(input, 'bad.json')).toThrow(ReleaseManifestError);
    expect(() => parseReleaseManifest(input, 'bad.json')).toThrow(/bad\.json/);
  });
});

describe('compareVersionsDesc', () => {
  it('sorts newest first and treats pre-releases as older', () => {
    const versions = ['1.0.0-beta.1', '1.2.0', '1.0.0', '1.10.0', '0.9.9'];
    expect([...versions].sort(compareVersionsDesc)).toEqual([
      '1.10.0',
      '1.2.0',
      '1.0.0',
      '1.0.0-beta.1',
      '0.9.9',
    ]);
  });
});

describe('loadReleaseManifests', () => {
  it('unwraps Vite JSON modules and sorts by product then version', () => {
    const loaded = loadReleaseManifests({
      'a/launcher-1.0.0.json': { default: { ...valid, product: 'launcher' } },
      'a/client-1.1.0.json': { default: { ...valid, version: '1.1.0' } },
      'a/client-1.0.0.json': valid,
    });
    expect(loaded.map((m) => `${m.product}@${m.version}`)).toEqual([
      'client@1.1.0',
      'client@1.0.0',
      'launcher@1.0.0',
    ]);
    expect(latestRelease(loaded, 'client')?.version).toBe('1.1.0');
    expect(latestRelease(loaded, 'launcher')?.version).toBe('1.0.0');
    expect(latestRelease(loaded, 'client', 'beta')).toBeUndefined();
  });

  it('names the offending file when a manifest is broken', () => {
    expect(() => loadReleaseManifests({ 'shared/releases/oops.json': {} })).toThrow(/oops\.json/);
  });
});

describe('pickFile', () => {
  it('prefers extensions in order and falls back to the first file', () => {
    const manifest = parseReleaseManifest({
      ...valid,
      product: 'launcher',
      files: [
        { name: 'VANTA-Launcher-1.0.0.exe', downloadUrl: '', size: 0, sha256: '' },
        { name: 'VANTA-Launcher-1.0.0.msi', downloadUrl: '', size: 0, sha256: '' },
      ],
    });
    expect(pickFile(manifest, ['.msi', '.exe'])?.name).toBe('VANTA-Launcher-1.0.0.msi');
    expect(pickFile(manifest, ['.dmg'])?.name).toBe('VANTA-Launcher-1.0.0.exe');
    expect(pickFile(manifest)?.name).toBe('VANTA-Launcher-1.0.0.exe');
  });
});

describe('repository manifests (shared/releases)', () => {
  it('contain a stable client and launcher release for Minecraft 1.21.11', () => {
    const client = latestRelease(releases, 'client');
    const launcher = latestRelease(releases, 'launcher');
    expect(client?.minecraftVersion).toBe('1.21.11');
    expect(client?.fabricVersion).toBe('0.19.5');
    expect(client?.javaVersion).toBe(21);
    expect(launcher?.minecraftVersion).toBe('1.21.11');
  });
});

describe('githubReleasePageUrl', () => {
  it.each([
    [
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v1.0.0/vanta-client-1.0.0.jar',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.0',
    ],
    [
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v1.0.0/fabric-api-0.141.6+1.21.11.jar',
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.0',
    ],
    [
      'https://github.com/o/r.js/releases/download/launcher-v1.0.0/VANTA-Launcher-1.0.0.msi',
      'https://github.com/o/r.js/releases/tag/launcher-v1.0.0',
    ],
  ])('derives the release page of %s', (url, page) => {
    expect(githubReleasePageUrl(url)).toBe(page);
  });

  it.each([
    ['empty', ''],
    ['http', 'http://github.com/o/r/releases/download/v1/x.jar'],
    ['another host', 'https://example.com/o/r/releases/download/v1/x.jar'],
    ['a look-alike host', 'https://github.com.evil.example/o/r/releases/download/v1/x.jar'],
    ['a subdomain', 'https://api.github.com/o/r/releases/download/v1/x.jar'],
    ['no file segment', 'https://github.com/o/r/releases/download/v1'],
    ['a trailing slash', 'https://github.com/o/r/releases/download/v1/'],
    ['extra segments', 'https://github.com/o/r/releases/download/v1/sub/x.jar'],
    ['a query string', 'https://github.com/o/r/releases/download/v1/x.jar?raw=1'],
    ['a fragment', 'https://github.com/o/r/releases/download/v1/x.jar#x'],
    ['the latest alias', 'https://github.com/o/r/releases/latest/download/x.jar'],
    ['a dot-dot tag', 'https://github.com/o/r/releases/download/../x.jar'],
    ['a dot-dot repository', 'https://github.com/o/../releases/download/v1/x.jar'],
    ['an invalid owner', 'https://github.com/-o/r/releases/download/v1/x.jar'],
    ['surrounding spaces', ' https://github.com/o/r/releases/download/v1/x.jar'],
    ['a release page', 'https://github.com/o/r/releases/tag/v1'],
  ])('returns undefined for %s', (_label, url) => {
    expect(githubReleasePageUrl(url)).toBeUndefined();
  });
});

describe('releasePageUrl', () => {
  it('is undefined while nothing is published', () => {
    expect(releasePageUrl(clientFixture({ published: false }))).toBeUndefined();
  });

  it('uses the first file with a GitHub release asset URL', () => {
    expect(releasePageUrl(launcherFixture({ published: true }))).toBe(
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.0',
    );
    const partly = clientFixture({ published: true, unpublished: ['vanta-client-1.0.0.jar'] });
    expect(releasePageUrl(partly)).toBe(
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.0.0',
    );
  });

  it('shows no page for downloads hosted somewhere else', () => {
    const mirror = clientFixture({
      published: true,
      url: (tag, name) => `https://mirror.example/${tag}/${name}`,
    });
    expect(isPublished(mirror)).toBe(true);
    expect(releasePageUrl(mirror)).toBeUndefined();
  });
});
