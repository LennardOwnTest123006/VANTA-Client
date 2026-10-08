/**
 * Tests for local-ai.mjs (resolve, verify, prepare, status, LOCAL-AI.txt), the Local AI schema with negative
 * fixtures, the tar reader and the workflow texts that call the script. Everything runs against a local node:http
 * server that plays GitHub (release API + archives) and Hugging Face (tree API + model) with tiny fake archives built
 * in memory; nothing touches the internet.
 */
import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { createServer } from 'node:http';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { REPO_ROOT } from './lib/repo.mjs';
import { extractTarGz, listTarEntries, listTarGz, safeTarName } from './lib/tar.mjs';
import { buildTar, buildTarGz } from './lib/tar-writer.mjs';
import { buildZip } from './lib/zip-writer.mjs';
import { FIXTURE_HOST, FIXTURE_TAG, fixtureArchives, fixtureManifest, fixtureModel, fixtureReleaseJson, fixtureTreeJson } from './lib/local-ai-fixture.mjs';
import { validateWithSchemaFile } from './validate-json.mjs';
import {
  CheckError, Client, MANIFEST_PATH, PLATFORM_KEYS, SCHEMA_PATH, UsageError, archiveKind, extractArchive, findServerPath, githubReleaseApiUrl,
  hfTreeApiUrl, installedMatches, isoInstant, loadLocalAiManifest, localAiNote, main, platformKey, prepareInstall, resolveManifest, statusReport,
  templateValues, validateManifest, verifyManifest,
} from './local-ai.mjs';

const SCHEMA = join(REPO_ROOT, SCHEMA_PATH);
const TEMPLATE = join(REPO_ROOT, MANIFEST_PATH);
const RELEASE_API = `https://api.github.com/repos/ggml-org/llama.cpp/releases/tags/${FIXTURE_TAG}`;
const TREE_API = 'https://huggingface.co/api/models/Qwen/Qwen3-1.7B-GGUF/tree/main';
const MODEL_URL = 'https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf';
const archiveUrl = (file) => `${FIXTURE_HOST}/ggml-org/llama.cpp/releases/download/${FIXTURE_TAG}/${file}`;
const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
const json = (value) => `${JSON.stringify(value, null, 2)}\n`;
const NOW = new Date('2026-10-08T12:34:56.789Z');

/** A fake repository root with the schema and, optionally, a manifest. */
function makeRoot(manifest) {
  const root = mkdtempSync(join(tmpdir(), 'vanta-local-ai-root-'));
  mkdirSync(join(root, 'shared', 'schemas'), { recursive: true });
  mkdirSync(join(root, 'shared', 'local-ai'), { recursive: true });
  writeFileSync(join(root, 'shared', 'schemas', 'local-ai.schema.json'), readFileSync(SCHEMA));
  if (manifest) writeFileSync(join(root, MANIFEST_PATH), json(manifest));
  return root;
}

const scratch = () => mkdtempSync(join(tmpdir(), 'vanta-local-ai-'));

/**
 * GitHub and Hugging Face on 127.0.0.1: routes are keyed by `<host><pathname>` of the real URL; the fetch given to the
 * script rewrites every https URL to the local server. Each route may fail once (failFirst), always (alwaysFail) or
 * answer with other bytes.
 */
function fakeInternet() {
  const routes = new Map();
  const hits = new Map();
  const headersSeen = new Map();
  let server;
  let base;
  const reset = () => {
    routes.clear();
    hits.clear();
    headersSeen.clear();
    const release = fixtureReleaseJson();
    routes.set(new URL(RELEASE_API).host + new URL(RELEASE_API).pathname, { body: release });
    for (const archive of fixtureArchives()) routes.set(`example.invalid/ggml-org/llama.cpp/releases/download/${FIXTURE_TAG}/${archive.file}`, { body: archive.bytes });
    routes.set(new URL(TREE_API).host + new URL(TREE_API).pathname, { body: fixtureTreeJson() });
    routes.set(new URL(MODEL_URL).host + new URL(MODEL_URL).pathname, { body: fixtureModel().bytes });
  };
  reset();
  const start = async () => {
    server = createServer((req, res) => {
      const url = new URL(req.url, 'http://localhost');
      const key = decodeURIComponent(url.pathname.slice(1));
      hits.set(key, (hits.get(key) ?? 0) + 1);
      headersSeen.set(key, req.headers);
      const route = routes.get(key);
      if (!route) {
        res.writeHead(404, { 'content-type': 'text/plain' });
        res.end('not found');
        return;
      }
      if (route.alwaysFail || (route.failFirst && hits.get(key) === 1)) {
        res.writeHead(500, { 'content-type': 'text/plain' });
        res.end('boom');
        return;
      }
      // A paginated answer: `pages` holds one { body, headers } per request, in order (the last one repeats).
      const page = route.pages ? route.pages[Math.min(hits.get(key) - 1, route.pages.length - 1)] : route;
      const body = Buffer.isBuffer(page.body) ? page.body : Buffer.from(JSON.stringify(page.body));
      res.writeHead(200, { 'content-type': Buffer.isBuffer(page.body) ? 'application/octet-stream' : 'application/json', 'content-length': body.length, ...(page.headers ?? {}) });
      res.end(body);
    });
    await new Promise((listening) => server.listen(0, '127.0.0.1', listening));
    base = `http://127.0.0.1:${server.address().port}`;
  };
  const stop = () => new Promise((closed) => server.close(closed));
  const fetchImpl = (url, init) => {
    const u = new URL(url);
    return fetch(`${base}/${u.host}${u.pathname}${u.search}`, init);
  };
  const route = (url) => routes.get(new URL(url).host + new URL(url).pathname);
  const hitsOf = (url) => hits.get(new URL(url).host + new URL(url).pathname) ?? 0;
  const headersOf = (url) => headersSeen.get(new URL(url).host + new URL(url).pathname);
  const client = (options = {}) => new Client({ fetch: fetchImpl, userAgent: 'VANTA tests', delayMs: 0, ...options });
  return { start, stop, reset, routes, route, hitsOf, headersOf, fetchImpl, client };
}

describe('helpers', () => {
  test('platformKey maps Node platform/arch pairs to manifest keys and null for the rest', () => {
    assert.equal(platformKey({ platform: 'win32', arch: 'x64' }), 'windows-x64');
    assert.equal(platformKey({ platform: 'win32', arch: 'arm64' }), 'windows-arm64');
    assert.equal(platformKey({ platform: 'linux', arch: 'x64' }), 'linux-x64');
    assert.equal(platformKey({ platform: 'linux', arch: 'arm64' }), 'linux-arm64');
    assert.equal(platformKey({ platform: 'darwin', arch: 'arm64' }), 'macos-arm64');
    assert.equal(platformKey({ platform: 'darwin', arch: 'x64' }), 'macos-x64');
    assert.equal(platformKey({ platform: 'linux', arch: 'ia32' }), null);
    assert.equal(platformKey({ platform: 'freebsd', arch: 'x64' }), null);
    assert.ok(PLATFORM_KEYS.includes(platformKey()) || platformKey() === null);
  });

  test('archive kinds, API URLs and instants', () => {
    assert.equal(archiveKind('a.zip'), 'zip');
    assert.equal(archiveKind('a.tar.gz'), 'tar.gz');
    assert.throws(() => archiveKind('a.7z'), UsageError);
    assert.equal(githubReleaseApiUrl('https://github.com/ggml-org/llama.cpp', 'b11429'), RELEASE_API);
    assert.equal(githubReleaseApiUrl('https://github.com/ggml-org/llama.cpp/', 'b11429'), RELEASE_API);
    assert.throws(() => githubReleaseApiUrl('https://gitlab.com/x/y', 'b1'), UsageError);
    assert.throws(() => githubReleaseApiUrl('https://github.com/ggml-org/llama.cpp', 'b1/../x'), UsageError);
    assert.deepEqual(hfTreeApiUrl(MODEL_URL), { api: TREE_API, path: 'Qwen3-1.7B-Q8_0.gguf', owner: 'Qwen', repo: 'Qwen3-1.7B-GGUF', revision: 'main' });
    assert.equal(hfTreeApiUrl('https://huggingface.co/o/r/resolve/v2/sub/dir/m.gguf').api, 'https://huggingface.co/api/models/o/r/tree/v2/sub/dir');
    assert.throws(() => hfTreeApiUrl('https://huggingface.co/o/r/blob/main/m.gguf'), UsageError);
    assert.equal(isoInstant(NOW), '2026-10-08T12:34:56Z');
  });

  test('templateValues names every unfilled value and nothing on a resolved manifest', () => {
    assert.deepEqual(templateValues(fixtureManifest()), []);
    const open = templateValues(fixtureManifest({ template: true }));
    assert.ok(open.includes('resolvedAt is empty'));
    assert.ok(open.includes('runtime.platforms.linux-x64.size is 0'));
    assert.ok(open.includes('runtime.platforms.linux-x64.serverPath is "..."'));
    assert.ok(!open.some((line) => line.includes('windows-x64.serverPath')), 'the Windows serverPath is known in the template');
    assert.ok(open.includes('model.size is 0'));
    assert.ok(open.includes('model.sha256 is ""'));
    assert.deepEqual(templateValues(null), ['not a JSON object']);
  });
});

describe('local-ai.schema.json', () => {
  const check = (manifest) => validateWithSchemaFile(SCHEMA, manifest).valid;
  const variant = (mutate) => {
    const m = fixtureManifest();
    mutate(m);
    return m;
  };

  test('accepts the resolved fixture', () => {
    assert.deepEqual(validateWithSchemaFile(SCHEMA, fixtureManifest()).errors, []);
    assert.equal(check(fixtureManifest({ tag: 'v0.6.0' })), true);
  });

  test('rejects the committed template and every single unfilled value', () => {
    assert.equal(check(JSON.parse(readFileSync(TEMPLATE, 'utf8'))), false);
    assert.equal(check(fixtureManifest({ template: true })), false);
    assert.equal(check(variant((m) => { m.resolvedAt = ''; })), false);
    assert.equal(check(variant((m) => { m.resolvedAt = '2026-10-08T12:00:00.000Z'; })), false, 'milliseconds are not the resolver format');
    assert.equal(check(variant((m) => { m.resolvedAt = '2026-10-08 12:00:00'; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].size = 0; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].size = 1.5; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].sha256 = ''; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].sha256 = 'A'.repeat(64); })), false, 'upper-case hex');
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].sha256 = 'a'.repeat(63); })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].serverPath = '...'; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].serverPath = ''; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].serverPath = '/bin/llama-server'; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].serverPath = 'bin/../llama-server'; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].serverPath = 'bin\\llama-server'; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].url = 'http://example.invalid/x.tar.gz'; })), false, 'https only');
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].url = 'https://example.invalid/with space.tar.gz'; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].file = 'llama.7z'; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].file = 'dir/llama.tar.gz'; })), false);
    assert.equal(check(variant((m) => { m.runtime.platforms['linux-x64'].extra = true; })), false);
    assert.equal(check(variant((m) => { delete m.runtime.platforms['linux-x64'].serverPath; })), false);
    assert.equal(check(variant((m) => { delete m.runtime.platforms['macos-x64']; })), false, 'all six platforms are required');
    assert.equal(check(variant((m) => { m.runtime.platforms['freebsd-x64'] = m.runtime.platforms['linux-x64']; })), false, 'no other platform keys');
    assert.equal(check(variant((m) => { m.runtime.tag = 'b 11429'; })), false);
    assert.equal(check(variant((m) => { m.runtime.sourceUrl = 'git@github.com:ggml-org/llama.cpp.git'; })), false);
    assert.equal(check(variant((m) => { m.runtime.license = ''; })), false);
    assert.equal(check(variant((m) => { m.runtime.extra = 1; })), false);
    assert.equal(check(variant((m) => { m.model.size = 0; })), false);
    assert.equal(check(variant((m) => { m.model.sha256 = ''; })), false);
    assert.equal(check(variant((m) => { m.model.file = 'model.bin'; })), false);
    assert.equal(check(variant((m) => { m.model.file = 'models/x.gguf'; })), false);
    assert.equal(check(variant((m) => { m.model.quantization = 'Q8 0'; })), false);
    assert.equal(check(variant((m) => { m.model.contextSize = 0; })), false);
    assert.equal(check(variant((m) => { m.model.contextSize = 4096.5; })), false);
    assert.equal(check(variant((m) => { m.model.licenseUrl = 'ftp://x/y'; })), false);
    assert.equal(check(variant((m) => { m.model.extra = 'x'; })), false);
    assert.equal(check(variant((m) => { delete m.model.licenseUrl; })), false);
    assert.equal(check(variant((m) => { m.requirements.diskMb = 0; })), false);
    assert.equal(check(variant((m) => { delete m.requirements.ramMb; })), false);
    assert.equal(check(variant((m) => { m.requirements.gpu = true; })), false);
    assert.equal(check(variant((m) => { m.schemaVersion = 2; })), false);
    assert.equal(check(variant((m) => { m.unknown = 1; })), false);
    assert.equal(check(variant((m) => { delete m.requirements; })), false);
  });

  test('the committed template is consistent: six platforms, GitHub digests, URLs derived from the tag, unfilled sizes', () => {
    const template = JSON.parse(readFileSync(TEMPLATE, 'utf8'));
    assert.equal(template.schemaVersion, 1);
    assert.equal(template.resolvedAt, '');
    assert.deepEqual(Object.keys(template.runtime.platforms), [...PLATFORM_KEYS]);
    assert.equal(template.runtime.releaseUrl, `${template.runtime.sourceUrl}/releases/tag/${template.runtime.tag}`);
    for (const [key, entry] of Object.entries(template.runtime.platforms)) {
      assert.match(entry.sha256, /^[a-f0-9]{64}$/, `${key}: the SHA-256 GitHub shows for the asset`);
      assert.equal(entry.size, 0, `${key}: size is filled by the resolver`);
      assert.equal(entry.url, `${template.runtime.sourceUrl}/releases/download/${template.runtime.tag}/${entry.file}`, key);
      assert.ok(entry.file.includes(template.runtime.tag), `${key}: the asset name carries the tag`);
      assert.equal(entry.serverPath, entry.file.endsWith('.zip') ? 'llama-server.exe' : '...', key);
      assert.equal(key.startsWith('windows'), entry.file.endsWith('.zip'), `${key}: Windows builds are zips, the others tar.gz`);
    }
    assert.equal(new Set(Object.values(template.runtime.platforms).map((e) => e.sha256)).size, 6, 'six different digests');
    assert.equal(template.model.size, 0);
    assert.equal(template.model.sha256, '');
    assert.equal(template.model.url, `${template.model.sourceUrl}/resolve/main/${template.model.file}`);
    assert.equal(hfTreeApiUrl(template.model.url).api, 'https://huggingface.co/api/models/Qwen/Qwen3-1.7B-GGUF/tree/main');
    assert.equal(githubReleaseApiUrl(template.runtime.sourceUrl, template.runtime.tag), 'https://api.github.com/repos/ggml-org/llama.cpp/releases/tags/b11429');
    assert.equal(validateManifest(template).valid, false);
    assert.throws(() => loadLocalAiManifest(REPO_ROOT), /is not resolved: it still holds template values/);
  });
});

describe('tar reader', () => {
  test('lists ustar entries with prefix, GNU long names, pax paths, modes and types', () => {
    const long = `${'d'.repeat(60)}/${'e'.repeat(60)}/llama-server`;
    const tar = buildTar([
      { name: 'top/', directory: true },
      { name: 'top/bin/llama-server', data: 'ELF', mode: 0o755 },
      { name: 'top/lib/libllama.so', data: 'so', mode: 0o644 },
      { name: 'top/lib/link.so', symlink: 'libllama.so' },
      { name: long, data: 'deep' },
    ]);
    const entries = listTarEntries(tar);
    assert.deepEqual(entries.map((e) => [e.name, e.type, e.size, e.mode]), [
      ['top', 'directory', 0, 0o755],
      ['top/bin/llama-server', 'file', 3, 0o755],
      ['top/lib/libllama.so', 'file', 2, 0o644],
      ['top/lib/link.so', 'symlink', 0, 0o644],
      [long, 'file', 4, 0o644],
    ]);
    assert.equal(entries[3].linkName, 'libllama.so');
    // A pax extended header carrying the path.
    const paxRecord = (() => { const body = 'path=pax/named/file\n'; const length = body.length + 3; return `${length} ${body}`; })();
    const pax = Buffer.concat([
      buildTar([{ name: 'ignored', data: paxRecord }]).subarray(0, 512 + 512),
      buildTar([{ name: 'short', data: 'x' }]),
    ]);
    pax.write('x', 156, 1, 'latin1');
    const paxEntries = listTarEntries(pax);
    assert.deepEqual(paxEntries.map((e) => e.name), ['pax/named/file']);
    assert.throws(() => listTarEntries(buildTar([{ name: '../escape', data: 'x' }])), /'\.\.' segment/);
    assert.throws(() => listTarEntries(buildTar([{ name: '/abs', data: 'x' }])), /absolute/);
    assert.equal(safeTarName('./a/./b/'), 'a/b');
  });

  test('extracts files with their executable bits, recreates safe symlinks and skips links that leave the root', () => {
    const dir = scratch();
    const archive = join(dir, 'a.tar.gz');
    writeFileSync(archive, buildTarGz([
      { name: 'r/', directory: true },
      { name: 'r/bin/llama-server', data: 'ELF', mode: 0o755 },
      { name: 'r/bin/libllama.so', data: 'so' },
      { name: 'r/bin/alias.so', symlink: 'libllama.so' },
      { name: 'r/bin/evil', symlink: '../../../../etc/passwd' },
      { name: 'r/bin/abs', symlink: '/etc/passwd' },
    ]));
    const out = join(dir, 'out');
    const result = extractTarGz(archive, out);
    assert.deepEqual(result.files, ['r/bin/llama-server', 'r/bin/libllama.so']);
    assert.equal(result.skipped.length, 2);
    assert.equal(readFileSync(join(out, 'r', 'bin', 'alias.so'), 'utf8'), 'so');
    assert.equal(existsSync(join(out, 'r', 'bin', 'evil')), false);
    assert.equal(existsSync(join(out, 'r', 'bin', 'abs')), false);
    if (process.platform !== 'win32') {
      assert.notEqual(statSync(join(out, 'r', 'bin', 'llama-server')).mode & 0o111, 0, 'executable');
      assert.equal(statSync(join(out, 'r', 'bin', 'libllama.so')).mode & 0o111, 0, 'not executable');
    }
    assert.equal(listTarGz(archive).entries.length, 6);
    // A plain tar cut inside an entry's data (ungzip passes a non-gzip buffer through).
    const truncated = join(dir, 'trunc.tar.gz');
    writeFileSync(truncated, buildTar([{ name: 'x', data: 'y'.repeat(600) }]).subarray(0, 700));
    assert.throws(() => listTarGz(truncated), /truncated tar archive/);
  });
});

describe('archive inspection', () => {
  test('findServerPath finds llama-server(.exe) in zips and tarballs, refuses none or several', () => {
    const dir = scratch();
    for (const archive of fixtureArchives()) {
      writeFileSync(join(dir, archive.file), archive.bytes);
      assert.equal(findServerPath(join(dir, archive.file), 'llama-server'), archive.serverPath, archive.file);
    }
    writeFileSync(join(dir, 'none.zip'), buildZip([{ name: 'llama-cli.exe', data: 'x' }]));
    assert.throws(() => findServerPath(join(dir, 'none.zip'), 'llama-server'), (e) => e instanceof CheckError && /no llama-server or llama-server\.exe inside/.test(e.message));
    writeFileSync(join(dir, 'two.tar.gz'), buildTarGz([{ name: 'a/llama-server', data: 'x' }, { name: 'b/llama-server', data: 'y' }]));
    assert.throws(() => findServerPath(join(dir, 'two.tar.gz'), 'llama-server'), /2 entries named llama-server/);
    writeFileSync(join(dir, 'dir.zip'), buildZip([{ name: 'llama-server/', data: '' }, { name: 'llama-server/readme', data: 'x' }]));
    assert.throws(() => findServerPath(join(dir, 'dir.zip'), 'llama-server'), /no llama-server/);
  });

  test('extractArchive writes zip and tar.gz contents and refuses names that leave the target', () => {
    const dir = scratch();
    const [win, , linux] = fixtureArchives();
    writeFileSync(join(dir, win.file), win.bytes);
    writeFileSync(join(dir, linux.file), linux.bytes);
    const zipOut = extractArchive(join(dir, win.file), join(dir, 'win'));
    assert.deepEqual(zipOut.files, ['ggml.dll', 'llama-server.exe', 'LICENSE']);
    assert.equal(readFileSync(join(dir, 'win', 'llama-server.exe'), 'utf8'), `fake llama-server for windows-x64 ${FIXTURE_TAG}`);
    const tarOut = extractArchive(join(dir, linux.file), join(dir, 'linux'));
    assert.ok(tarOut.files.includes(linux.serverPath));
    assert.equal(readFileSync(join(dir, 'linux', ...linux.serverPath.split('/')), 'utf8'), `fake llama-server for linux-x64 ${FIXTURE_TAG}`);
    writeFileSync(join(dir, 'evil.zip'), buildZip([{ name: '../evil.txt', data: 'x' }]));
    assert.throws(() => extractArchive(join(dir, 'evil.zip'), join(dir, 'evil')), /unsafe zip entry name/);
    assert.equal(existsSync(join(dir, 'evil.txt')), false);
  });
});

describe('resolve, verify, prepare and status against a local GitHub and Hugging Face', () => {
  const net = fakeInternet();
  before(net.start);
  after(net.stop);

  test('resolve fills sizes, URLs, server paths, the model digest and resolvedAt; the result is schema-valid', async () => {
    net.reset();
    const root = makeRoot();
    const work = scratch();
    const logged = [];
    const resolved = await resolveManifest({ template: fixtureManifest({ template: true }), client: net.client({ githubToken: 'ghs_test' }), workDir: work, root, now: NOW, log: (m) => logged.push(m) });
    assert.deepEqual(resolved, fixtureManifest({ resolvedAt: '2026-10-08T12:34:56Z' }));
    assert.deepEqual(validateWithSchemaFile(SCHEMA, resolved).errors, []);
    assert.equal(net.hitsOf(RELEASE_API), 1);
    assert.equal(net.hitsOf(TREE_API), 1);
    assert.equal(net.hitsOf(MODEL_URL), 1, 'the model is downloaded once to confirm the digest');
    for (const archive of fixtureArchives()) assert.equal(net.hitsOf(archiveUrl(archive.file)), 1, archive.file);
    assert.equal(net.headersOf(RELEASE_API).authorization, 'Bearer ghs_test', 'the GitHub token goes to api.github.com');
    assert.equal(net.headersOf(TREE_API).authorization, undefined, 'and nowhere else');
    assert.equal(net.headersOf(archiveUrl(fixtureArchives()[0].file)).authorization, undefined);
    assert.match(net.headersOf(TREE_API)['user-agent'], /VANTA tests/);
    assert.deepEqual(readdirSync(work), [], 'downloads are removed from the work directory');
    assert.ok(logged.some((line) => /ok {2}linux-x64 .*serverPath llama-b11429-bin-ubuntu-x64\/bin\/llama-server/.test(line)), logged.join('\n'));
    assert.ok(logged.some((line) => /ok {2}model/.test(line)));
  });

  test('resolve with --skip-model-download trusts the Hugging Face LFS metadata and fills a missing URL', async () => {
    net.reset();
    const template = fixtureManifest({ template: true });
    template.runtime.platforms['macos-x64'].url = '...';
    const resolved = await resolveManifest({ template, client: net.client(), workDir: scratch(), root: makeRoot(), now: NOW, skipModelDownload: true });
    assert.equal(net.hitsOf(MODEL_URL), 0);
    assert.equal(resolved.model.sha256, fixtureModel().sha256);
    assert.equal(resolved.model.size, fixtureModel().size);
    assert.equal(resolved.runtime.platforms['macos-x64'].url, archiveUrl(fixtureArchives()[5].file));
    assert.equal(template.runtime.platforms['macos-x64'].url, '...', 'the template object is not modified');
  });

  test('resolve fails on a digest GitHub publishes that differs from the template, before downloading that archive', async () => {
    net.reset();
    const template = fixtureManifest({ template: true });
    template.runtime.platforms['linux-arm64'].sha256 = 'f'.repeat(64);
    await assert.rejects(resolveManifest({ template, client: net.client(), workDir: scratch(), root: makeRoot(), now: NOW }), (e) => e instanceof CheckError && /linux-arm64: GitHub publishes SHA-256 [a-f0-9]{64} for llama-b11429-bin-ubuntu-arm64\.tar\.gz, the manifest says f{64}; the file was not downloaded/.test(e.message));
    assert.equal(net.hitsOf(archiveUrl(fixtureArchives()[3].file)), 0);
  });

  test('resolve fails when a download does not hash to the template digest (no GitHub digest), and deletes the file', async () => {
    net.reset();
    const api = net.route(RELEASE_API);
    api.body = fixtureReleaseJson({ digests: false });
    const template = fixtureManifest({ template: true });
    template.runtime.platforms['windows-arm64'].sha256 = '0'.repeat(64);
    const work = scratch();
    await assert.rejects(resolveManifest({ template, client: net.client(), workDir: work, root: makeRoot(), now: NOW }), /windows-arm64 llama-b11429-bin-win-cpu-arm64\.zip: downloaded SHA-256 [a-f0-9]{64} instead of 0{64}/);
    assert.deepEqual(readdirSync(work).filter((name) => name.includes('arm64')), [], 'nothing left of the bad download');
    assert.equal(net.hitsOf(archiveUrl(fixtureArchives()[1].file)), 1, 'a wrong digest is not retried');
  });

  test('resolve fails on a missing asset, a size GitHub misreports, an archive without the server and a template digest mismatch', async () => {
    net.reset();
    const api = net.route(RELEASE_API);
    const release = fixtureReleaseJson();
    release.assets = release.assets.filter((a) => !a.name.includes('macos-x64'));
    api.body = release;
    await assert.rejects(resolveManifest({ template: fixtureManifest({ template: true }), client: net.client(), workDir: scratch(), root: makeRoot(), now: NOW }), /macos-x64: the release has no asset named llama-b11429-bin-macos-x64\.tar\.gz/);

    net.reset();
    const sized = fixtureReleaseJson();
    sized.assets[0].size += 1;
    net.route(RELEASE_API).body = sized;
    await assert.rejects(resolveManifest({ template: fixtureManifest({ template: true }), client: net.client(), workDir: scratch(), root: makeRoot(), now: NOW }), /windows-x64 llama-b11429-bin-win-cpu-x64\.zip: downloaded \d+ bytes instead of \d+/);

    net.reset();
    const noServer = buildZip([{ name: 'llama-cli.exe', data: 'x' }]);
    const win = fixtureArchives()[0];
    net.route(archiveUrl(win.file)).body = noServer;
    const template = fixtureManifest({ template: true });
    template.runtime.platforms['windows-x64'].sha256 = sha256(noServer);
    const releaseNoServer = fixtureReleaseJson({ digests: false });
    releaseNoServer.assets[0].size = noServer.length;
    net.route(RELEASE_API).body = releaseNoServer;
    await assert.rejects(resolveManifest({ template, client: net.client(), workDir: scratch(), root: makeRoot(), now: NOW }), /llama-b11429-bin-win-cpu-x64\.zip: no llama-server or llama-server\.exe inside the archive/);

    net.reset();
    const pinned = fixtureManifest({ template: true });
    pinned.model.sha256 = '1'.repeat(64);
    await assert.rejects(resolveManifest({ template: pinned, client: net.client(), workDir: scratch(), root: makeRoot(), now: NOW }), /model: the template pins SHA-256 1{64}, Hugging Face publishes/);
  });

  test('resolve fails when Hugging Face does not list the model, lists it without LFS, or serves other bytes', async () => {
    net.reset();
    net.route(TREE_API).body = fixtureTreeJson().filter((item) => item.path !== fixtureModel().file);
    await assert.rejects(resolveManifest({ template: fixtureManifest({ template: true }), client: net.client(), workDir: scratch(), root: makeRoot(), now: NOW }), /Qwen3-1\.7B-Q8_0\.gguf is not in the repository tree/);

    net.reset();
    net.route(TREE_API).body = fixtureTreeJson().map((item) => (item.path === fixtureModel().file ? { ...item, lfs: undefined } : item));
    await assert.rejects(resolveManifest({ template: fixtureManifest({ template: true }), client: net.client(), workDir: scratch(), root: makeRoot(), now: NOW }), /is not stored in LFS/);

    net.reset();
    net.route(MODEL_URL).body = Buffer.from('not the model');
    const work = scratch();
    await assert.rejects(resolveManifest({ template: fixtureManifest({ template: true }), client: net.client(), workDir: work, root: makeRoot(), now: NOW }), /model Qwen3-1\.7B-Q8_0\.gguf: downloaded 13 bytes instead of 4096 and SHA-256/);
    assert.equal(existsSync(join(work, `${fixtureModel().file}.part`)), false);
    assert.equal(existsSync(join(work, fixtureModel().file)), false);
  });

  test('resolve follows a paginated tree listing and retries a 500 once', async () => {
    net.reset();
    const tree = fixtureTreeJson();
    // The model is on the second page; the first page links to it with a Link header, as the Hugging Face API does.
    net.route(TREE_API).pages = [
      { body: tree.slice(0, 2), headers: { link: `<${TREE_API}?cursor=abc>; rel="next"` } },
      { body: tree.slice(2), headers: {} },
    ];
    net.route(archiveUrl(fixtureArchives()[2].file)).failFirst = true;
    const logged = [];
    const log = (m) => logged.push(m);
    const resolved = await resolveManifest({ template: fixtureManifest({ template: true }), client: net.client({ log }), workDir: scratch(), root: makeRoot(), now: NOW, skipModelDownload: true, log });
    assert.equal(resolved.model.sha256, fixtureModel().sha256);
    assert.equal(net.hitsOf(TREE_API), 2, 'two pages');
    assert.equal(net.hitsOf(archiveUrl(fixtureArchives()[2].file)), 2, 'retried after the 500');
    assert.ok(logged.some((line) => /retrying .*ubuntu-x64\.tar\.gz after .*HTTP 500/.test(line)), logged.join('\n'));
  });

  test('resolve refuses a template without the GitHub digests or with the wrong platform set (usage errors)', async () => {
    net.reset();
    const noDigest = fixtureManifest({ template: true });
    noDigest.runtime.platforms['linux-x64'].sha256 = '';
    await assert.rejects(resolveManifest({ template: noDigest, client: net.client(), workDir: scratch(), root: makeRoot(), now: NOW }), (e) => e instanceof UsageError && /linux-x64\.sha256 must be the SHA-256 GitHub shows/.test(e.message));
    const extra = fixtureManifest({ template: true });
    extra.runtime.platforms['freebsd-x64'] = extra.runtime.platforms['linux-x64'];
    await assert.rejects(resolveManifest({ template: extra, client: net.client(), workDir: scratch(), root: makeRoot(), now: NOW }), (e) => e instanceof UsageError && /unexpected freebsd-x64/.test(e.message));
    assert.equal(net.hitsOf(RELEASE_API), 0, 'nothing is fetched for a broken template');
  });

  test('verify passes on the resolved fixture and reports every drift', async () => {
    net.reset();
    const root = makeRoot();
    const ok = await verifyManifest({ manifest: fixtureManifest(), client: net.client(), workDir: scratch(), root });
    assert.deepEqual(ok, { ok: true, problems: [] });
    assert.equal(net.hitsOf(MODEL_URL), 0, 'the model is checked through the API only');
    for (const archive of fixtureArchives()) assert.equal(net.hitsOf(archiveUrl(archive.file)), 1, `${archive.file} re-hashed`);

    const full = await verifyManifest({ manifest: fixtureManifest(), client: net.client(), workDir: scratch(), root, full: true });
    assert.equal(full.ok, true);
    assert.equal(net.hitsOf(MODEL_URL), 1, '--full downloads the model');

    net.reset();
    const [win, , linux] = fixtureArchives();
    net.route(archiveUrl(linux.file)).body = buildTarGz([{ name: 'other/llama-server', data: 'changed' }]);
    const sized = fixtureReleaseJson({ digests: false });
    sized.assets[0].size = 7;
    net.route(RELEASE_API).body = sized;
    net.route(TREE_API).body = fixtureTreeJson().map((item) => (item.path === fixtureModel().file ? { ...item, lfs: { ...item.lfs, size: item.lfs.size + 5 } } : item));
    const bad = await verifyManifest({ manifest: fixtureManifest(), client: net.client(), workDir: scratch(), root });
    assert.equal(bad.ok, false);
    assert.equal(bad.problems.length, 3, bad.problems.join('\n'));
    assert.match(bad.problems[0], new RegExp(`windows-x64: GitHub reports 7 bytes for ${win.file.replace(/\./g, '\\.')}, the manifest says ${win.size}`));
    assert.match(bad.problems[1], /linux-x64 llama-b11429-bin-ubuntu-x64\.tar\.gz: downloaded \d+ bytes instead of \d+ and SHA-256/);
    assert.match(bad.problems[2], /model: Hugging Face publishes 4101 bytes for Qwen3-1\.7B-Q8_0\.gguf, the manifest says 4096/);

    net.reset();
    const moved = fixtureManifest();
    moved.runtime.platforms['macos-arm64'].serverPath = 'elsewhere/llama-server';
    const drift = await verifyManifest({ manifest: moved, client: net.client(), workDir: scratch(), root });
    assert.deepEqual(drift.problems, ['macos-arm64: llama-b11429-bin-macos-arm64.tar.gz holds the server at bin/llama-server, the manifest says elsewhere/llama-server']);

    const template = await verifyManifest({ manifest: fixtureManifest({ template: true }), client: net.client(), workDir: scratch(), root });
    assert.equal(template.ok, false);
    assert.ok(template.problems.some((p) => /not resolved yet/.test(p)));
    assert.ok(template.problems.some((p) => /^schema: \$\.model\.size/.test(p)));
  });

  test('prepare builds the directory layout, verifies everything and skips what is already installed', async () => {
    net.reset();
    const root = makeRoot();
    const manifest = fixtureManifest();
    const dir = join(scratch(), 'local-ai');
    const logged = [];
    const result = await prepareInstall({ manifest, dir, platform: 'linux-x64', client: net.client(), root, now: NOW, log: (m) => logged.push(m) });
    const linux = fixtureArchives()[2];
    assert.deepEqual(result.downloaded, ['models/Qwen3-1.7B-Q8_0.gguf', 'runtime/b11429/linux-x64']);
    assert.deepEqual(result.skipped, []);
    assert.deepEqual(readdirSync(dir).sort(), ['downloads', 'installed.json', 'models', 'runtime']);
    assert.deepEqual(readdirSync(join(dir, 'downloads')), [], 'no archive or .part file is left');
    assert.deepEqual(readFileSync(join(dir, 'models', 'Qwen3-1.7B-Q8_0.gguf')), fixtureModel().bytes);
    const server = join(dir, 'runtime', 'b11429', 'linux-x64', ...linux.serverPath.split('/'));
    assert.equal(readFileSync(server, 'utf8'), `fake llama-server for linux-x64 ${FIXTURE_TAG}`);
    if (process.platform !== 'win32') assert.notEqual(statSync(server).mode & 0o111, 0, 'executable bit set');
    assert.ok(existsSync(join(dir, 'runtime', 'b11429', 'linux-x64', `llama-${FIXTURE_TAG}-bin-ubuntu-x64`, 'bin', 'libllama.so')));
    assert.equal(existsSync(join(dir, 'runtime', 'b11429', 'linux-x64.extracting')), false);
    const installed = JSON.parse(readFileSync(join(dir, 'installed.json'), 'utf8'));
    assert.deepEqual(installed, result.installed);
    assert.equal(installed.schemaVersion, 1);
    assert.equal(installed.platform, 'linux-x64');
    assert.equal(installed.installedAt, '2026-10-08T12:34:56Z');
    assert.equal(installed.verifiedAt, '2026-10-08T12:34:56Z');
    assert.deepEqual(Object.keys(installed), ['schemaVersion', 'platform', 'installedAt', 'verifiedAt', 'runtime', 'model', 'files']);
    assert.deepEqual(installed.runtime, { ...manifest.runtime, platforms: { 'linux-x64': manifest.runtime.platforms['linux-x64'] } }, 'only the installed platform');
    assert.deepEqual(installed.model, manifest.model);
    assert.equal(installed.files.serverSize, statSync(server).size);
    assert.equal(installed.files.modelSize, fixtureModel().size);
    assert.equal(installed.files.serverMtime, Math.floor(statSync(server).mtimeMs));
    assert.equal(installed.files.modelMtime, Math.floor(statSync(join(dir, 'models', 'Qwen3-1.7B-Q8_0.gguf')).mtimeMs));
    assert.equal(installedMatches(installed, manifest, 'linux-x64'), true);
    assert.equal(installedMatches(installed, manifest, 'macos-x64'), false);
    assert.equal(statusReport({ manifest, manifestPath: 'm.json', dir, platform: 'linux-x64', root }).ok, true);

    // Second run: nothing is downloaded again; installedAt is kept, verifiedAt moves.
    const later = new Date('2026-10-09T00:00:00Z');
    const again = await prepareInstall({ manifest, dir, platform: 'linux-x64', client: net.client(), root, now: later });
    assert.deepEqual(again.downloaded, []);
    assert.deepEqual(again.skipped, ['models/Qwen3-1.7B-Q8_0.gguf', 'runtime/b11429/linux-x64']);
    assert.equal(net.hitsOf(MODEL_URL), 1);
    assert.equal(net.hitsOf(archiveUrl(linux.file)), 1);
    assert.equal(again.installed.installedAt, '2026-10-08T12:34:56Z');
    assert.equal(again.installed.verifiedAt, '2026-10-09T00:00:00Z');

    // A tampered model is replaced; a runtime recorded with another digest is extracted again.
    writeFileSync(join(dir, 'models', 'Qwen3-1.7B-Q8_0.gguf'), 'tampered');
    const stale = JSON.parse(readFileSync(join(dir, 'installed.json'), 'utf8'));
    stale.runtime.platforms['linux-x64'].sha256 = 'a'.repeat(64);
    writeFileSync(join(dir, 'installed.json'), json(stale));
    const repaired = await prepareInstall({ manifest, dir, platform: 'linux-x64', client: net.client(), root, now: later });
    assert.deepEqual(repaired.downloaded, ['models/Qwen3-1.7B-Q8_0.gguf', 'runtime/b11429/linux-x64']);
    assert.deepEqual(readFileSync(join(dir, 'models', 'Qwen3-1.7B-Q8_0.gguf')), fixtureModel().bytes);
    assert.equal(net.hitsOf(MODEL_URL), 2);
    assert.equal(net.hitsOf(archiveUrl(linux.file)), 2);
    assert.equal(repaired.installed.installedAt, '2026-10-09T00:00:00Z', 'a repaired install is a new install');
  });

  test('prepare for Windows extracts the zip (no chmod needed) and refuses templates, unknown platforms and a moved server', async () => {
    net.reset();
    const root = makeRoot();
    const dir = join(scratch(), 'win');
    const result = await prepareInstall({ manifest: fixtureManifest(), dir, platform: 'windows-x64', client: net.client(), root, now: NOW });
    assert.equal(readFileSync(join(dir, 'runtime', 'b11429', 'windows-x64', 'llama-server.exe'), 'utf8'), `fake llama-server for windows-x64 ${FIXTURE_TAG}`);
    assert.ok(existsSync(join(dir, 'runtime', 'b11429', 'windows-x64', 'ggml.dll')));
    assert.equal(result.installed.platform, 'windows-x64');

    await assert.rejects(prepareInstall({ manifest: fixtureManifest({ template: true }), dir: join(scratch(), 'x'), platform: 'linux-x64', client: net.client(), root, now: NOW }), (e) => e instanceof UsageError && /not resolved yet/.test(e.message));
    await assert.rejects(prepareInstall({ manifest: fixtureManifest(), dir: join(scratch(), 'x'), platform: 'freebsd-x64', client: net.client(), root, now: NOW }), (e) => e instanceof UsageError && /not available for platform 'freebsd-x64'/.test(e.message));

    const moved = fixtureManifest();
    moved.runtime.platforms['macos-x64'].serverPath = 'other/llama-server';
    const dir2 = join(scratch(), 'mac');
    await assert.rejects(prepareInstall({ manifest: moved, dir: dir2, platform: 'macos-x64', client: net.client(), root, now: NOW }), (e) => e instanceof CheckError && /macos-x64: llama-b11429-bin-macos-x64\.tar\.gz holds the server at bin\/llama-server, the manifest says other\/llama-server/.test(e.message));
    assert.deepEqual(readdirSync(join(dir2, 'downloads')), [], 'the rejected archive is removed');
    assert.equal(existsSync(join(dir2, 'installed.json')), false);

    net.reset();
    const wrongBytes = fixtureManifest();
    wrongBytes.runtime.platforms['macos-arm64'].sha256 = 'b'.repeat(64);
    await assert.rejects(prepareInstall({ manifest: wrongBytes, dir: join(scratch(), 'bad'), platform: 'macos-arm64', client: net.client(), root, now: NOW }), /macos-arm64 llama-b11429-bin-macos-arm64\.tar\.gz: downloaded .*SHA-256 [a-f0-9]{64} instead of b{64}/);
  });

  test('status reports a template, a resolved manifest and complete or incomplete installs', async () => {
    net.reset();
    const root = makeRoot();
    const template = statusReport({ manifest: fixtureManifest({ template: true }), manifestPath: 't.json', root });
    assert.equal(template.ok, false);
    assert.match(template.lines[0], /^manifest t\.json: not resolved yet$/);
    assert.match(template.lines[1], /template values: resolvedAt is empty, runtime\.platforms\.windows-x64\.size is 0/);
    const resolved = statusReport({ manifest: fixtureManifest(), manifestPath: 'm.json', root });
    assert.equal(resolved.ok, true);
    assert.match(resolved.lines[0], /^manifest m\.json: resolved 2026-10-08T12:00:00Z$/);
    assert.match(resolved.lines[1], /runtime llama\.cpp b11429 \(llama-server, MIT\): windows-x64 \d+(\.\d)? k?B, /);
    assert.match(resolved.lines[2], /model Qwen3-1\.7B Q8_0 \(Apache-2\.0\): Qwen3-1\.7B-Q8_0\.gguf 4\.1 kB sha256 [a-f0-9]{64}/);
    const dir = join(scratch(), 'd');
    const missing = statusReport({ manifest: fixtureManifest(), manifestPath: 'm.json', dir, platform: 'linux-x64', root });
    assert.equal(missing.ok, false);
    assert.ok(missing.lines.some((l) => /incomplete/.test(l)));
    assert.ok(missing.lines.some((l) => /installed\.json is missing/.test(l)));
    await prepareInstall({ manifest: fixtureManifest(), dir, platform: 'linux-x64', client: net.client(), root, now: NOW });
    const complete = statusReport({ manifest: fixtureManifest(), manifestPath: 'm.json', dir, platform: 'linux-x64', root });
    assert.equal(complete.ok, true);
    assert.match(complete.lines.at(-1), /\(linux-x64\): complete, installed 2026-10-08T12:34:56Z, verified 2026-10-08T12:34:56Z$/);
    writeFileSync(join(dir, 'models', 'Qwen3-1.7B-Q8_0.gguf'), 'short');
    const broken = statusReport({ manifest: fixtureManifest(), manifestPath: 'm.json', dir, platform: 'linux-x64', root });
    assert.equal(broken.ok, false);
    assert.ok(broken.lines.some((l) => /Qwen3-1\.7B-Q8_0\.gguf has 5 bytes, the manifest says 4096/.test(l)));
    const other = statusReport({ manifest: fixtureManifest(), manifestPath: 'm.json', dir, platform: 'macos-x64', root });
    assert.ok(other.lines.some((l) => /installed\.json does not describe this manifest for this platform/.test(l)));
  });

  test('CLI: resolve writes --out, verify and status exit codes, prepare uses --platform, usage errors exit 2', async () => {
    net.reset();
    const root = makeRoot(fixtureManifest({ template: true }));
    const out = [];
    const err = [];
    const deps = { fetch: net.fetchImpl, log: (m) => out.push(m), logError: (m) => err.push(m), env: { GITHUB_TOKEN: 'ghs_cli' }, now: NOW, delayMs: 0 };
    const target = join(root, 'resolved.json');
    assert.equal(await main(['resolve', '--root', root, '--out', target, '--work', scratch(), '--skip-model-download'], deps), 0, err.join('\n'));
    assert.equal(net.headersOf(RELEASE_API).authorization, 'Bearer ghs_cli');
    const written = JSON.parse(readFileSync(target, 'utf8'));
    assert.deepEqual(written, fixtureManifest({ resolvedAt: '2026-10-08T12:34:56Z' }));
    assert.equal(readFileSync(target, 'utf8'), json(written), '2-space JSON with a trailing newline');
    assert.deepEqual(JSON.parse(readFileSync(join(root, MANIFEST_PATH), 'utf8')), fixtureManifest({ template: true }), 'the template stays as it is with --out');
    assert.match(out.at(-1), /^wrote .*resolved\.json \(resolved 2026-10-08T12:34:56Z\)$/);

    // In place: the template path is overwritten.
    assert.equal(await main(['resolve', '--root', root, '--work', scratch(), '--skip-model-download'], deps), 0, err.join('\n'));
    assert.deepEqual(JSON.parse(readFileSync(join(root, MANIFEST_PATH), 'utf8')), written);

    assert.equal(await main(['status', '--root', root], deps), 0);
    assert.match(out.at(-3), /: resolved 2026-10-08T12:34:56Z$/);
    assert.equal(await main(['status', '--root', makeRoot(fixtureManifest({ template: true }))], deps), 1);
    assert.match(out.at(-3), /not resolved yet$/);

    assert.equal(await main(['verify', target, '--root', root, '--work', scratch(), '--quiet'], deps), 0, err.join('\n'));
    assert.match(out.at(-1), /runtime archives and model verified \(model through the Hugging Face API\)\.$/);
    assert.equal(await main(['verify', target, '--root', root, '--work', scratch(), '--full'], deps), 0, err.join('\n'));
    assert.match(out.at(-1), /\(model downloaded\)\.$/);
    assert.equal(await main(['verify', target, '--root', root, '--work', scratch(), '--skip-model-download'], deps), 0, 'the explicit default');
    const templateFile = join(root, 'template.json');
    writeFileSync(templateFile, json(fixtureManifest({ template: true })));
    assert.equal(await main(['verify', templateFile, '--root', root, '--work', scratch()], deps), 1);
    assert.ok(err.some((line) => /not resolved yet/.test(line)), err.join('\n'));
    net.route(archiveUrl(fixtureArchives()[0].file)).body = Buffer.from('changed');
    assert.equal(await main(['verify', target, '--root', root, '--work', scratch()], deps), 1);
    assert.match(err.at(-1), /does not match its sources\.$/);
    net.reset();

    const dir = join(scratch(), 'cli-install');
    assert.equal(await main(['prepare', '--dir', dir, '--platform', 'macos-arm64', '--manifest', target, '--root', root], deps), 0, err.join('\n'));
    assert.ok(existsSync(join(dir, 'runtime', 'b11429', 'macos-arm64', 'bin', 'llama-server')));
    assert.match(out.at(-1), /Local AI b11429 \+ Qwen3-1\.7B-Q8_0\.gguf ready for macos-arm64 \(2 downloaded, 0 already present\)$/);
    assert.equal(await main(['status', '--manifest', target, '--dir', dir, '--platform', 'macos-arm64', '--root', root], deps), 0);
    assert.equal(await main(['status', '--manifest', target, '--dir', dir, '--platform', 'linux-x64', '--root', root], deps), 1);
    // The current platform is detected when --platform is not given.
    const detected = join(scratch(), 'detected');
    assert.equal(await main(['prepare', '--dir', detected, '--manifest', target, '--root', root], { ...deps, platform: 'win32', arch: 'arm64' }), 0, err.join('\n'));
    assert.ok(existsSync(join(detected, 'runtime', 'b11429', 'windows-arm64', 'llama-server.exe')));
    assert.equal(await main(['prepare', '--dir', join(scratch(), 'bsd'), '--manifest', target, '--root', root], { ...deps, platform: 'freebsd', arch: 'x64' }), 2);
    assert.match(err.at(-1), /Local AI is not available for freebsd\/x64; pass --platform/);

    for (const argv of [
      [], ['explode'], ['verify'], ['verify', 'a.json', 'b.json'], ['prepare'], ['resolve', '--full'], ['verify', 'a.json', '--full', '--skip-model-download'],
      ['prepare', '--dir', 'x', '--skip-model-download'], ['status', '--out', 'x'], ['resolve', '--dir', 'x'], ['prepare', '--dir', 'x', '--platform', 'amiga'],
      ['resolve', '--timeout', '0'], ['resolve', '--bogus'],
    ]) {
      assert.equal(await main(argv, deps), 2, JSON.stringify(argv));
    }
    assert.equal(await main(['prepare', '--dir', join(scratch(), 'x'), '--manifest', join(root, 'nope.json'), '--root', root], deps), 2);
    assert.match(err.at(-1), /cannot read/);
    assert.equal(await main(['--help'], deps), 0);
    assert.match(out.at(-1), /^Usage: node scripts\/release\/local-ai\.mjs <command>/);
  });

  test('Client.download streams through .part and leaves nothing behind on a failure', async () => {
    net.reset();
    const dir = scratch();
    const client = net.client();
    const dest = join(dir, 'model.gguf');
    const result = await client.download(MODEL_URL, dest, { expectedSize: fixtureModel().size, expectedSha256: fixtureModel().sha256 });
    assert.deepEqual(result, { size: fixtureModel().size, sha256: fixtureModel().sha256 });
    assert.deepEqual(readFileSync(dest), fixtureModel().bytes);
    assert.deepEqual(readdirSync(dir), ['model.gguf']);
    await assert.rejects(client.download(`${FIXTURE_HOST}/missing`, join(dir, 'missing.bin')), /HTTP 404/);
    assert.deepEqual(readdirSync(dir), ['model.gguf']);
    net.route(MODEL_URL).alwaysFail = true;
    await assert.rejects(new Client({ fetch: net.fetchImpl, userAgent: 'x', delayMs: 0, attempts: 2 }).download(MODEL_URL, join(dir, 'flaky.bin')), /HTTP 500/);
    assert.equal(net.hitsOf(MODEL_URL), 3, 'one good download plus two failed attempts');
    assert.deepEqual(readdirSync(dir), ['model.gguf']);
    assert.throws(() => new Client({ userAgent: 'x' }), TypeError);
  });
});

describe('LOCAL-AI.txt', () => {
  test('is plain ASCII, wraps at 100 columns and names sizes, hosts, digests and licences from the manifest', () => {
    const manifest = fixtureManifest();
    const note = localAiNote(manifest);
    assert.match(note, /^VANTA Local AI: what Vanta Nexus downloads on first use\n=+\n/);
    assert.match(note, /^[\x09\x0a\x20-\x7e]*$/, 'plain ASCII');
    for (const line of note.split('\n')) assert.ok(line.length <= 100, `line too long: ${line}`);
    assert.doesNotMatch(note, /—/, 'no em dashes');
    assert.match(note, /Runtime: llama\.cpp b11429 \(llama-server\), licence MIT/);
    assert.match(note, /Model: Qwen3-1\.7B Q8_0, licence Apache-2\.0/);
    for (const archive of fixtureArchives()) {
      assert.ok(note.includes(`${archive.platform.padEnd(14)} ${archive.file}  (${archive.size} B)`), archive.file);
      assert.ok(note.includes(`sha256 ${archive.sha256}`), archive.file);
    }
    assert.ok(note.includes(`File:     Qwen3-1.7B-Q8_0.gguf  (4.1 kB)`));
    assert.ok(note.includes(`sha256:   ${fixtureModel().sha256}`));
    assert.ok(note.includes('https://github.com/ggml-org/llama.cpp/releases/tag/b11429'));
    assert.ok(note.includes(MODEL_URL));
    assert.ok(note.includes('https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/blob/main/LICENSE'));
    assert.match(note.replace(/\n\s*/g, ' '), /about 2200 MB of free disk space for the runtime and the model and 3072 MB of RAM/);
    assert.match(note.replace(/\n\s*/g, ' '), /github\.com \(runtime\) and huggingface\.co \(model\); nothing else is contacted/);
    assert.match(note.replace(/\n\s*/g, ' '), /talks to 127\.0\.0\.1 only; nothing you type is sent anywhere/);
    assert.match(note.replace(/\n\s*/g, ' '), /\(resolved 2026-10-08T12:00:00Z\)/);
    assert.doesNotMatch(note, /cloud|API key|account/i);
  });
});

describe('workflows and build files that use local-ai.mjs', () => {
  const CI = readFileSync(join(REPO_ROOT, '.github', 'workflows', 'ci.yml'), 'utf8');
  const RESOLVE = readFileSync(join(REPO_ROOT, '.github', 'workflows', 'local-ai-resolve.yml'), 'utf8');

  /** The text of one top-level job of a workflow. */
  function job(workflow, id) {
    const start = workflow.indexOf(`\n  ${id}:\n`);
    assert.ok(start >= 0, `job ${id} exists`);
    const next = workflow.slice(start + 1).search(/\n {2}[a-z][a-z0-9-]*:\n/);
    return next < 0 ? workflow.slice(start) : workflow.slice(start, start + 1 + next);
  }

  test('local-ai-resolve.yml runs by hand, resolves, validates and hands the manifest back under the label local-ai', () => {
    assert.match(RESOLVE, /^on:\n {2}workflow_dispatch:\n/m);
    assert.doesNotMatch(RESOLVE, /^\s+(push|pull_request|schedule):/m, 'no automatic trigger: the resolver downloads gigabytes');
    assert.match(RESOLVE, /node scripts\/release\/local-ai\.mjs resolve /);
    assert.match(RESOLVE, /node scripts\/release\/validate-json\.mjs shared\/schemas\/local-ai\.schema\.json "\$OUT\/local-ai\.json"/);
    assert.match(RESOLVE, /cat "\$OUT\/local-ai\.json"/);
    assert.match(RESOLVE, /bash scripts\/ci\/publish-screenshots\.sh "\$OUT" local-ai/);
    assert.match(RESOLVE, /cp local-ai\.json <repo>\/shared\/local-ai\//);
    assert.match(RESOLVE, /contents: write/);
    assert.match(RESOLVE, /GITHUB_TOKEN: \$\{\{ secrets\.GITHUB_TOKEN \}\}/);
    assert.match(RESOLVE, /GITHUB_STEP_SUMMARY/);
  });

  test('ci.yml validates the committed manifest in the release scripts job and prepares the cached install before the three jobs that start the game', () => {
    const scripts = job(CI, 'performance-pack');
    assert.match(scripts, /node scripts\/release\/local-ai\.mjs status/);
    assert.match(scripts, /node scripts\/release\/validate-json\.mjs shared\/schemas\/local-ai\.schema\.json shared\/local-ai\/local-ai\.json/);
    assert.match(scripts, /node scripts\/release\/local-ai\.mjs verify shared\/local-ai\/local-ai\.json --skip-model-download/);
    assert.match(scripts, /Local AI manifest not resolved yet/);
    for (const id of ['client-gametest', 'client-gametest-performance', 'launcher-integration']) {
      const text = job(CI, id);
      assert.match(text, /if node scripts\/release\/local-ai\.mjs status; then/, `${id}: guard`);
      assert.match(text, /Local AI manifest not resolved yet/, `${id}: the guard message`);
      assert.match(text, /uses: actions\/cache\/restore@v4/, `${id}: cache restore`);
      assert.match(text, /uses: actions\/cache\/save@v4/, `${id}: cache save right after prepare`);
      assert.match(text, /key: local-ai-linux-x64-\$\{\{ hashFiles\('shared\/local-ai\/local-ai\.json'\) \}\}/, `${id}: cache key on the manifest hash`);
      assert.match(text, /node scripts\/release\/local-ai\.mjs prepare --dir "\$RUNNER_TEMP\/local-ai" --platform linux-x64/, `${id}: prepare`);
      assert.match(text, /echo "VANTA_LOCAL_AI_DIR=\$RUNNER_TEMP\/local-ai" >> "\$GITHUB_ENV"/, `${id}: exported to the test`);
      const guard = text.indexOf('if node scripts/release/local-ai.mjs status; then');
      const restore = text.indexOf('actions/cache/restore@v4');
      const prepare = text.indexOf('local-ai.mjs prepare');
      const save = text.indexOf('actions/cache/save@v4');
      const run = text.indexOf(id === 'launcher-integration' ? 'java -jar launcher/build/libs/vanta-launcher-*-all.jar --install' : 'runProductionClientGametest');
      assert.ok(guard < restore && restore < prepare && prepare < save && save < run, `${id}: guard, restore, prepare, save, then the test`);
    }
  });

  test('the Gradle builds copy the manifest into both products as a tracked input', () => {
    const core = readFileSync(join(REPO_ROOT, 'core', 'build.gradle'), 'utf8');
    const launcher = readFileSync(join(REPO_ROOT, 'launcher', 'build.gradle'), 'utf8');
    assert.match(core, /shared\/local-ai\/local-ai\.json/);
    assert.match(core, /into\('assets\/vanta'\)/);
    assert.match(launcher, /shared\/local-ai\/local-ai\.json/);
    assert.match(core, /tasks\.named\('processResources'/);
    assert.match(launcher, /tasks\.named\('processResources'/);
  });
});
