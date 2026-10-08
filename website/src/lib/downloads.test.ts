import { describe, expect, it } from 'vitest';
import {
  CLIENT_FILE_NAMES,
  LAUNCHER_FILE_NAMES,
  clientFixture,
  launcherFixture,
} from '../test/fixtures/releases';
import {
  describeReleaseFile,
  isWindowsSetupFile,
  launcherCrossPlatformFiles,
  launcherSetupFiles,
  modsBundleFile,
  primaryFile,
  resolveDownload,
} from './downloads';
import { parseReleaseManifest, releases } from './releases';

const base = {
  schemaVersion: 1,
  product: 'launcher',
  version: '1.0.0',
  minecraftVersion: '1.21.11',
  fabricVersion: '0.19.5',
  fabricApiVersion: '0.141.6+1.21.11',
  javaVersion: 21,
  releaseDate: '2026-10-04',
  channel: 'stable',
};

describe('resolveDownload', () => {
  it('is missing without a manifest', () => {
    expect(resolveDownload(undefined, undefined)).toEqual({ state: 'missing' });
  });

  it('is pending when neither manifest nor environment has a URL', () => {
    const manifest = parseReleaseManifest({
      ...base,
      files: [{ name: 'VANTA-Launcher-1.0.0.msi', downloadUrl: '', size: 0, sha256: '' }],
    });
    const result = resolveDownload(manifest, undefined);
    expect(result.state).toBe('pending');
    if (result.state === 'pending') expect(result.file?.name).toBe('VANTA-Launcher-1.0.0.msi');
  });

  it('uses the manifest URL of the preferred file', () => {
    const manifest = parseReleaseManifest({
      ...base,
      files: [
        { name: 'VANTA-Launcher-1.0.0.exe', downloadUrl: 'https://x/l.exe', size: 2, sha256: '' },
        { name: 'VANTA-Launcher-1.0.0.msi', downloadUrl: 'https://x/l.msi', size: 1, sha256: '' },
      ],
    });
    const result = resolveDownload(manifest, undefined);
    expect(result).toMatchObject({
      state: 'available',
      url: 'https://x/l.msi',
      source: 'manifest',
    });
  });

  it('lets the environment override the manifest URL but keeps the file facts', () => {
    const manifest = parseReleaseManifest({
      ...base,
      files: [{ name: 'VANTA-Launcher-1.0.0.msi', downloadUrl: '', size: 777, sha256: '' }],
    });
    const result = resolveDownload(manifest, 'https://mirror.example/l.msi');
    expect(result).toMatchObject({
      state: 'available',
      url: 'https://mirror.example/l.msi',
      source: 'env',
      file: { size: 777 },
    });
  });
});

describe('primaryFile', () => {
  it('offers exactly vanta-client-<version>.jar for the client, whatever the file order', () => {
    const reordered = parseReleaseManifest({
      ...base,
      product: 'client',
      files: [
        { name: 'fabric-api-0.141.6+1.21.11.jar', downloadUrl: '', size: 0, sha256: '' },
        { name: 'vanta-client-1.0.0-mods.zip', downloadUrl: '', size: 0, sha256: '' },
        { name: 'vanta-client-1.0.0-sources.jar', downloadUrl: '', size: 0, sha256: '' },
        { name: 'vanta-client-1.0.0.jar', downloadUrl: '', size: 0, sha256: '' },
      ],
    });
    expect(primaryFile(reordered)?.name).toBe('vanta-client-1.0.0.jar');
    expect(primaryFile(clientFixture({ published: false }))?.name).toBe('vanta-client-1.0.0.jar');
  });

  it('never falls back to another jar, Fabric API, a sources jar or the mods bundle', () => {
    const published = (name: string) => ({
      name,
      downloadUrl: `https://x/${name}`,
      size: 1,
      sha256: '',
    });
    const renamed = parseReleaseManifest({
      ...base,
      product: 'client',
      files: [
        published('fabric-api-0.141.6+1.21.11.jar'),
        published('vanta-client-1.0.0-sources.jar'),
        published('vanta-client-1.0.0-mods.zip'),
        published('vanta-client-renamed.jar'),
      ],
    });
    expect(primaryFile(renamed)).toBeUndefined();
    expect(resolveDownload(renamed, undefined)).toEqual({ state: 'pending', file: undefined });
    // A version bump that missed the file entry: the jar of the old version is not offered either.
    const stale = parseReleaseManifest({
      ...base,
      product: 'client',
      version: '1.0.1',
      files: [published('vanta-client-1.0.0.jar'), published('vanta-client-1.0.0-mods.zip')],
    });
    expect(primaryFile(stale)).toBeUndefined();
    expect(resolveDownload(stale, undefined).state).toBe('pending');
    const onlyDependencies = parseReleaseManifest({
      ...base,
      product: 'client',
      files: [
        { name: 'fabric-api-0.141.6+1.21.11.jar', downloadUrl: '', size: 0, sha256: '' },
        { name: 'vanta-client-1.0.0-mods.zip', downloadUrl: '', size: 0, sha256: '' },
      ],
    });
    expect(primaryFile(onlyDependencies)).toBeUndefined();
    expect(resolveDownload(onlyDependencies, undefined)).toEqual({
      state: 'pending',
      file: undefined,
    });
  });

  it('offers the .msi for the launcher', () => {
    expect(primaryFile(launcherFixture({ published: false }))?.name).toBe(
      'VANTA-Launcher-1.0.0.msi',
    );
  });
});

describe('modsBundleFile', () => {
  it('finds the mods folder bundle of the client', () => {
    expect(modsBundleFile(clientFixture({ published: false }))?.name).toBe(
      'vanta-client-1.0.0-mods.zip',
    );
    expect(modsBundleFile(launcherFixture({ published: false }))).toBeUndefined();
    expect(modsBundleFile(undefined)).toBeUndefined();
  });
});

describe('launcher file groups', () => {
  it('splits a launcher release into Windows setup files and the cross-platform files', () => {
    const manifest = launcherFixture({ published: true });
    expect(launcherSetupFiles(manifest).map((file) => file.name)).toEqual([
      'VANTA-Launcher-1.0.0.msi',
      'VANTA-Launcher-1.0.0.exe',
      'VANTA-Launcher-1.0.0-windows-portable.zip',
    ]);
    expect(launcherCrossPlatformFiles(manifest).map((file) => file.name)).toEqual([
      'vanta-launcher-1.0.0-windows-all.jar',
      'VANTA-Launcher-1.0.0-linux-x64.tar.gz',
      'vanta-launcher-1.0.0-linux-all.jar',
      'vanta-launcher-1.0.0-macos-aarch64-all.jar',
    ]);
    // Every file lands in exactly one group.
    expect(launcherSetupFiles(manifest).length + launcherCrossPlatformFiles(manifest).length).toBe(
      manifest.files.length,
    );
  });

  it('works without the .exe, as launcher releases from 1.4.0 on are published', () => {
    const files = LAUNCHER_FILE_NAMES.filter((name) => !name.endsWith('.exe'));
    const manifest = parseReleaseManifest({
      ...base,
      files: files.map((name) => ({ name, downloadUrl: '', size: 0, sha256: '' })),
    });
    expect(launcherSetupFiles(manifest).map((file) => file.name)).toEqual([
      'VANTA-Launcher-1.0.0.msi',
      'VANTA-Launcher-1.0.0-windows-portable.zip',
    ]);
    expect(launcherCrossPlatformFiles(manifest)).toHaveLength(4);
    expect(isWindowsSetupFile('VANTA-Launcher-1.4.0.MSI')).toBe(true);
    expect(isWindowsSetupFile('vanta-launcher-1.4.0-windows-all.jar')).toBe(false);
    expect(isWindowsSetupFile('VANTA-Launcher-1.4.0-linux-x64.tar.gz')).toBe(false);
  });

  it('classifies every launcher file of the repository manifests', () => {
    for (const manifest of releases.filter((m) => m.product === 'launcher')) {
      const setup = launcherSetupFiles(manifest);
      expect(setup.length, manifest.version).toBeGreaterThan(0);
      expect(setup[0]?.name.endsWith('.msi'), manifest.version).toBe(true);
      expect(launcherCrossPlatformFiles(manifest).length, manifest.version).toBeGreaterThan(0);
      for (const file of launcherCrossPlatformFiles(manifest)) {
        expect(file.name, manifest.version).toMatch(/\.jar$|\.tar\.gz$/);
      }
    }
  });
});

describe('describeReleaseFile', () => {
  it.each([
    ['vanta-client-1.0.0.jar', 'VANTA Client mod'],
    ['vanta-client-1.0.0-mods.zip', 'Mods folder bundle (VANTA, Fabric API, Performance pack)'],
    ['fabric-api-0.141.6+1.21.11.jar', 'Fabric API (required dependency)'],
    ['VANTA-Launcher-1.0.0.msi', 'Windows installer'],
    ['VANTA-Launcher-1.0.0.exe', 'Windows installer'],
    ['VANTA-Launcher-1.0.0-windows-portable.zip', 'Windows portable app with Java'],
    ['vanta-launcher-1.0.0-windows-all.jar', 'Windows jar (needs Java 21)'],
    ['VANTA-Launcher-1.0.0-linux-x64.tar.gz', 'Linux app with Java'],
    ['vanta-launcher-1.0.0-linux-all.jar', 'Linux jar (needs Java 21)'],
    ['vanta-launcher-1.0.0-macos-aarch64-all.jar', 'macOS Apple Silicon jar (needs Java 21)'],
    ['vanta-client-1.0.0-sources.jar', 'Source code jar'],
    ['VANTA-CLIENT-2.3.4.JAR', 'VANTA Client mod'],
    ['notes.txt', 'Release file'],
  ])('labels %s as "%s"', (name, label) => {
    expect(describeReleaseFile(name).label).toBe(label);
  });

  it('says which files include Java and which need it installed', () => {
    for (const name of [
      'VANTA-Launcher-1.0.0.msi',
      'VANTA-Launcher-1.0.0.exe',
      'VANTA-Launcher-1.0.0-windows-portable.zip',
      'VANTA-Launcher-1.0.0-linux-x64.tar.gz',
    ]) {
      expect(describeReleaseFile(name).note).toMatch(/Java 21 included/);
    }
    for (const name of [
      'vanta-launcher-1.0.0-windows-all.jar',
      'vanta-launcher-1.0.0-linux-all.jar',
      'vanta-launcher-1.0.0-macos-aarch64-all.jar',
    ]) {
      expect(describeReleaseFile(name).label).toMatch(/needs Java 21/);
      expect(describeReleaseFile(name).note).toMatch(/only/);
    }
    expect(describeReleaseFile('notes.txt').note).toBeUndefined();
  });

  it('knows every file of the release contract and of the repository manifests', () => {
    const names = [
      ...CLIENT_FILE_NAMES,
      ...LAUNCHER_FILE_NAMES,
      ...releases.flatMap((manifest) => manifest.files.map((file) => file.name)),
    ];
    for (const name of names) {
      expect(describeReleaseFile(name).label, name).not.toBe('Release file');
    }
  });
});
