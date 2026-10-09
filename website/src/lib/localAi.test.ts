import { describe, expect, it } from 'vitest';
import {
  LOCAL_AI_PLATFORM_KEYS,
  isLocalAiResolved,
  loadLocalAiManifest,
  localAi,
  localAiDownloadBytes,
  localAiDownloadHosts,
  localAiModelLabel,
  localAiPlatformLabel,
  localAiRedirectTargets,
  localAiRuntimeLabel,
  localAiRuntimeSizeRange,
  parseLocalAiManifest,
} from './localAi';

/** A small resolved manifest in the shape of shared/local-ai/local-ai.json (fake values). */
function manifestJson(overrides: Record<string, unknown> = {}) {
  const platform = (file: string, size: number, serverPath: string) => ({
    file,
    url: `https://github.com/ggml-org/llama.cpp/releases/download/b11429/${file}`,
    size,
    sha256: 'a'.repeat(64),
    serverPath,
  });
  return {
    schemaVersion: 1,
    resolvedAt: '2026-10-08T12:00:00Z',
    runtime: {
      name: 'llama.cpp',
      component: 'llama-server',
      tag: 'b11429',
      license: 'MIT',
      sourceUrl: 'https://github.com/ggml-org/llama.cpp',
      releaseUrl: 'https://github.com/ggml-org/llama.cpp/releases/tag/b11429',
      platforms: {
        'windows-x64': platform('llama-b11429-bin-win-cpu-x64.zip', 3_000_000, 'llama-server.exe'),
        'linux-x64': platform(
          'llama-b11429-bin-ubuntu-x64.tar.gz',
          2_000_000,
          'llama-b11429/llama-server',
        ),
        'macos-x64': platform(
          'llama-b11429-bin-macos-x64.tar.gz',
          1_000_000,
          'llama-b11429/llama-server',
        ),
      },
    },
    model: {
      name: 'Qwen3-1.7B',
      quantization: 'Q8_0',
      file: 'Qwen3-1.7B-Q8_0.gguf',
      url: 'https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf',
      size: 1_000_000_000,
      sha256: 'b'.repeat(64),
      license: 'Apache-2.0',
      licenseUrl: 'https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/blob/main/LICENSE',
      sourceUrl: 'https://huggingface.co/Qwen/Qwen3-1.7B-GGUF',
      contextSize: 4096,
    },
    requirements: { diskMb: 2200, ramMb: 3072 },
    ...overrides,
  };
}

describe('parseLocalAiManifest', () => {
  it('reads the runtime archives, the model and the requirements', () => {
    const manifest = parseLocalAiManifest(manifestJson(), 'fixture');
    expect(manifest.runtime.platforms.map((p) => p.key)).toEqual([
      'windows-x64',
      'linux-x64',
      'macos-x64',
    ]);
    expect(manifest.runtime.platforms[1]).toMatchObject({
      key: 'linux-x64',
      size: 2_000_000,
      serverPath: 'llama-b11429/llama-server',
    });
    expect(manifest.model).toMatchObject({ name: 'Qwen3-1.7B', size: 1_000_000_000 });
    expect(manifest.requirements).toEqual({ diskMb: 2200, ramMb: 3072 });
    expect(isLocalAiResolved(manifest)).toBe(true);
    expect(localAiRuntimeLabel(manifest)).toBe('llama.cpp b11429');
    expect(localAiModelLabel(manifest)).toBe('Qwen3-1.7B Q8_0');
  });

  it('accepts the unresolved template but reports it as unresolved', () => {
    const json = manifestJson({ resolvedAt: '' });
    for (const platform of Object.values(json.runtime.platforms)) {
      platform.size = 0;
      platform.sha256 = '';
    }
    json.model.size = 0;
    json.model.sha256 = '';
    const manifest = parseLocalAiManifest(json);
    expect(isLocalAiResolved(manifest)).toBe(false);
    expect(localAiRuntimeSizeRange(manifest)).toBeUndefined();
    expect(localAiDownloadBytes(manifest, 'linux-x64')).toBeUndefined();
  });

  it('rejects malformed manifests with the field in the message', () => {
    expect(() => parseLocalAiManifest(null)).toThrow(/root must be an object/);
    expect(() => parseLocalAiManifest(manifestJson({ schemaVersion: 2 }))).toThrow(/schemaVersion/);
    const badHost = manifestJson();
    badHost.model.url = 'http://huggingface.co/x.gguf';
    expect(() => parseLocalAiManifest(badHost)).toThrow(/"url" must be an https URL/);
    const badSha = manifestJson();
    badSha.model.sha256 = 'nope';
    expect(() => parseLocalAiManifest(badSha)).toThrow(/sha256/);
    const unknownPlatform = manifestJson();
    (unknownPlatform.runtime.platforms as Record<string, unknown>)['freebsd-x64'] =
      unknownPlatform.runtime.platforms['linux-x64'];
    expect(() => parseLocalAiManifest(unknownPlatform)).toThrow(/unknown platform key/);
    expect(() => parseLocalAiManifest(manifestJson({ requirements: 7 }))).toThrow(/requirements/);
  });

  it('derives hosts, the size range and the download total from the manifest only', () => {
    const manifest = parseLocalAiManifest(manifestJson());
    expect(localAiDownloadHosts(manifest)).toEqual(['github.com', 'huggingface.co']);
    const range = localAiRuntimeSizeRange(manifest);
    expect(range?.smallest.key).toBe('macos-x64');
    expect(range?.largest.key).toBe('windows-x64');
    expect(localAiDownloadBytes(manifest, 'linux-x64')).toBe(1_002_000_000);
    expect(localAiDownloadBytes(manifest, 'linux-arm64')).toBeUndefined();
  });

  it('names the file hosts github.com and huggingface.co redirect the downloads to, and nothing for an unknown host', () => {
    const manifest = parseLocalAiManifest(manifestJson());
    expect(localAiRedirectTargets(manifest)).toEqual([
      "GitHub's release asset host objects.githubusercontent.com",
      "Hugging Face's CDN hosts",
    ]);
    // Another host would get no invented CDN name; the GitHub phrase stays for the archives.
    const otherModelHost = manifestJson();
    otherModelHost.model.url = 'https://models.example.invalid/Qwen3-1.7B-Q8_0.gguf';
    expect(localAiRedirectTargets(parseLocalAiManifest(otherModelHost))).toEqual([
      "GitHub's release asset host objects.githubusercontent.com",
    ]);
  });

  it('labels every platform key', () => {
    expect(LOCAL_AI_PLATFORM_KEYS.map(localAiPlatformLabel)).toEqual([
      'Windows x64',
      'Windows ARM64',
      'Linux x64',
      'Linux ARM64',
      'macOS Apple Silicon',
      'macOS Intel',
    ]);
  });

  it('loads one module and tolerates a missing file', () => {
    expect(loadLocalAiManifest({})).toBeUndefined();
    const loaded = loadLocalAiManifest({
      '/shared/local-ai/local-ai.json': { default: manifestJson() },
    });
    expect(loaded?.runtime.tag).toBe('b11429');
    expect(() => loadLocalAiManifest({ '/x.json': { default: {} } })).toThrow(/x\.json/);
  });
});

describe('repository Local AI manifest', () => {
  it('exists, names llama-server and a Qwen3 GGUF model and uses only github.com and huggingface.co', () => {
    expect(localAi).toBeDefined();
    const manifest = localAi!;
    expect(manifest.runtime.component).toBe('llama-server');
    expect(manifest.model.file.endsWith('.gguf')).toBe(true);
    expect(manifest.runtime.platforms.map((p) => p.key).sort()).toEqual(
      [...LOCAL_AI_PLATFORM_KEYS].sort(),
    );
    // The binding privacy statement: nothing but these two hosts, ever.
    expect(localAiDownloadHosts(manifest)).toEqual(['github.com', 'huggingface.co']);
    expect(manifest.runtime.license).toBe('MIT');
    expect(manifest.model.license).toBe('Apache-2.0');
    expect(manifest.requirements.diskMb).toBeGreaterThan(0);
    expect(manifest.requirements.ramMb).toBeGreaterThan(0);
    if (isLocalAiResolved(manifest)) {
      // A resolved manifest: the model is far larger than any runtime archive.
      const range = localAiRuntimeSizeRange(manifest);
      expect(range).toBeDefined();
      expect(manifest.model.size).toBeGreaterThan(range!.largest.size);
    }
  });
});
