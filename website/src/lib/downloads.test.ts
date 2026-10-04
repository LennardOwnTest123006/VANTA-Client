import { describe, expect, it } from 'vitest';
import { resolveDownload } from './downloads';
import { parseReleaseManifest } from './releases';

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
