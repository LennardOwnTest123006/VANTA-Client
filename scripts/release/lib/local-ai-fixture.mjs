/**
 * Test fixtures for the Local AI manifest: a fake resolved manifest whose archives and model are tiny in-memory files
 * (zip for Windows, tar.gz for Linux and macOS), so the local-ai.mjs and build-bundle.mjs tests run without the
 * internet and still exercise real hashing, extraction and schema validation. Not used by the release itself.
 */
import { createHash } from 'node:crypto';
import { buildTarGz } from './tar-writer.mjs';
import { buildZip } from './zip-writer.mjs';

export const FIXTURE_TAG = 'b11429';
export const FIXTURE_HOST = 'https://example.invalid';

const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');

/** The six fake archives: name, bytes, the server path inside and the platform key. */
export function fixtureArchives(tag = FIXTURE_TAG) {
  const list = [
    ['windows-x64', `llama-${tag}-bin-win-cpu-x64.zip`, 'llama-server.exe'],
    ['windows-arm64', `llama-${tag}-bin-win-cpu-arm64.zip`, 'llama-server.exe'],
    ['linux-x64', `llama-${tag}-bin-ubuntu-x64.tar.gz`, `llama-${tag}-bin-ubuntu-x64/bin/llama-server`],
    ['linux-arm64', `llama-${tag}-bin-ubuntu-arm64.tar.gz`, `llama-${tag}-bin-ubuntu-arm64/bin/llama-server`],
    ['macos-arm64', `llama-${tag}-bin-macos-arm64.tar.gz`, 'bin/llama-server'],
    ['macos-x64', `llama-${tag}-bin-macos-x64.tar.gz`, 'bin/llama-server'],
  ];
  return list.map(([platform, file, serverPath]) => {
    const payload = `fake llama-server for ${platform} ${tag}`;
    const folder = serverPath.includes('/') ? `${serverPath.slice(0, serverPath.lastIndexOf('/'))}/` : '';
    let bytes;
    if (file.endsWith('.zip')) {
      bytes = buildZip([
        { name: 'ggml.dll', data: `ggml ${platform}`, deflate: true },
        { name: serverPath, data: payload, deflate: true },
        { name: 'LICENSE', data: 'MIT License (fake)' },
      ]);
    } else {
      bytes = buildTarGz([
        ...(folder ? [{ name: folder, directory: true }] : []),
        { name: `${folder}libllama.so`, data: `libllama ${platform}` },
        { name: serverPath, data: payload, mode: 0o755 },
        { name: `${folder}LICENSE`, data: 'MIT License (fake)' },
      ]);
    }
    return { platform, file, serverPath, bytes, size: bytes.length, sha256: sha256(bytes) };
  });
}

/** The fake model file (a few kilobytes, deterministic). */
export function fixtureModel() {
  const bytes = Buffer.alloc(4096);
  for (let i = 0; i < bytes.length; i += 1) bytes[i] = (i * 7 + 3) & 0xff;
  bytes.write('GGUF', 0, 'latin1');
  return { file: 'Qwen3-1.7B-Q8_0.gguf', bytes, size: bytes.length, sha256: sha256(bytes) };
}

/**
 * A complete, schema-valid manifest over the fake archives and model, served from `host` (the tests route
 * https://example.invalid to a local server). Pass `template: true` for the unresolved form (sizes 0, model sha256
 * "", serverPath "..." for the tar.gz archives) that `resolve` fills in.
 */
export function fixtureManifest({ host = FIXTURE_HOST, tag = FIXTURE_TAG, template = false, resolvedAt = '2026-10-08T12:00:00Z' } = {}) {
  const archives = fixtureArchives(tag);
  const model = fixtureModel();
  const platforms = {};
  for (const archive of archives) {
    platforms[archive.platform] = {
      file: archive.file,
      url: `${host}/ggml-org/llama.cpp/releases/download/${tag}/${archive.file}`,
      size: template ? 0 : archive.size,
      sha256: archive.sha256,
      serverPath: template && archive.file.endsWith('.tar.gz') ? '...' : archive.serverPath,
    };
  }
  return {
    schemaVersion: 1,
    resolvedAt: template ? '' : resolvedAt,
    runtime: {
      name: 'llama.cpp',
      component: 'llama-server',
      tag,
      license: 'MIT',
      sourceUrl: 'https://github.com/ggml-org/llama.cpp',
      releaseUrl: `https://github.com/ggml-org/llama.cpp/releases/tag/${tag}`,
      platforms,
    },
    model: {
      name: 'Qwen3-1.7B',
      quantization: 'Q8_0',
      file: model.file,
      url: `https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/${model.file}`,
      size: template ? 0 : model.size,
      sha256: template ? '' : model.sha256,
      license: 'Apache-2.0',
      licenseUrl: 'https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/blob/main/LICENSE',
      sourceUrl: 'https://huggingface.co/Qwen/Qwen3-1.7B-GGUF',
      contextSize: 4096,
    },
    requirements: { diskMb: 2200, ramMb: 3072 },
  };
}

/** The GitHub Releases API answer for the fake release (asset sizes, URLs and sha256 digests). */
export function fixtureReleaseJson({ host = FIXTURE_HOST, tag = FIXTURE_TAG, digests = true } = {}) {
  return {
    tag_name: tag,
    html_url: `https://github.com/ggml-org/llama.cpp/releases/tag/${tag}`,
    assets: fixtureArchives(tag).map((archive) => ({
      name: archive.file,
      size: archive.size,
      browser_download_url: `${host}/ggml-org/llama.cpp/releases/download/${tag}/${archive.file}`,
      ...(digests ? { digest: `sha256:${archive.sha256}` } : {}),
    })),
  };
}

/** The Hugging Face tree listing of the fake model repository. */
export function fixtureTreeJson() {
  const model = fixtureModel();
  return [
    { type: 'file', oid: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', size: 11357, path: 'LICENSE' },
    { type: 'file', oid: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', size: 2048, path: 'README.md' },
    { type: 'file', oid: 'cccccccccccccccccccccccccccccccccccccccc', size: 135, path: model.file, lfs: { oid: model.sha256, size: model.size, pointerSize: 135 } },
    { type: 'file', oid: 'dddddddddddddddddddddddddddddddddddddddd', size: 135, path: 'Qwen3-1.7B-Q4_K_M.gguf', lfs: { oid: 'e'.repeat(64), size: 1234567, pointerSize: 135 } },
  ];
}
