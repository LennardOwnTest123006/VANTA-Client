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
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync, utimesSync, writeFileSync } from 'node:fs';
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
  hfTreeApiUrl, installedMatches, installedRecord, isoInstant, loadLocalAiManifest, localAiNote, main, mirrorBase, mirrorFiles, mirrorManifest,
  platformKey, prepareInstall, resolveManifest, servedName, startFileServer, statusReport, templateValues, validateManifest, verifyManifest,
} from './local-ai.mjs';

const SCHEMA = join(REPO_ROOT, SCHEMA_PATH);
const TEMPLATE = join(REPO_ROOT, MANIFEST_PATH);
const SHARED_FIXTURES = join(REPO_ROOT, 'shared', 'local-ai', 'fixtures');
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

  test('rejects the template and every single unfilled value', () => {
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

  test('the committed manifest is consistent: six platforms, GitHub digests, URLs derived from the tag; resolved or a template', () => {
    const committed = JSON.parse(readFileSync(TEMPLATE, 'utf8'));
    const resolved = templateValues(committed).length === 0;
    assert.equal(committed.schemaVersion, 1);
    assert.deepEqual(Object.keys(committed.runtime.platforms), [...PLATFORM_KEYS]);
    assert.equal(committed.runtime.releaseUrl, `${committed.runtime.sourceUrl}/releases/tag/${committed.runtime.tag}`);
    for (const [key, entry] of Object.entries(committed.runtime.platforms)) {
      assert.match(entry.sha256, /^[a-f0-9]{64}$/, `${key}: the SHA-256 GitHub shows for the asset`);
      assert.equal(entry.url, `${committed.runtime.sourceUrl}/releases/download/${committed.runtime.tag}/${entry.file}`, key);
      assert.ok(entry.file.includes(committed.runtime.tag), `${key}: the asset name carries the tag`);
      assert.equal(key.startsWith('windows'), entry.file.endsWith('.zip'), `${key}: Windows builds are zips, the others tar.gz`);
      if (resolved) {
        assert.ok(Number.isInteger(entry.size) && entry.size > 0, `${key}: size filled by the resolver`);
        assert.equal(entry.serverPath, entry.file.endsWith('.zip') ? 'llama-server.exe' : `${committed.runtime.tag.replace(/^/, 'llama-')}/llama-server`, key);
      } else {
        assert.equal(entry.size, 0, `${key}: size is filled by the resolver`);
        assert.equal(entry.serverPath, entry.file.endsWith('.zip') ? 'llama-server.exe' : '...', key);
      }
    }
    assert.equal(new Set(Object.values(committed.runtime.platforms).map((e) => e.sha256)).size, 6, 'six different digests');
    assert.equal(committed.model.url, `${committed.model.sourceUrl}/resolve/main/${committed.model.file}`);
    assert.equal(hfTreeApiUrl(committed.model.url).api, 'https://huggingface.co/api/models/Qwen/Qwen3-1.7B-GGUF/tree/main');
    assert.equal(githubReleaseApiUrl(committed.runtime.sourceUrl, committed.runtime.tag), 'https://api.github.com/repos/ggml-org/llama.cpp/releases/tags/b11429');
    if (resolved) {
      assert.match(committed.resolvedAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/);
      assert.ok(committed.model.size > 0);
      assert.match(committed.model.sha256, /^[a-f0-9]{64}$/);
      assert.deepEqual(validateManifest(committed).errors, []);
      assert.equal(loadLocalAiManifest(REPO_ROOT).manifest.resolvedAt, committed.resolvedAt);
    } else {
      assert.equal(committed.resolvedAt, '');
      assert.equal(committed.model.size, 0);
      assert.equal(committed.model.sha256, '');
      assert.equal(validateManifest(committed).valid, false);
      assert.throws(() => loadLocalAiManifest(REPO_ROOT), /is not resolved: it still holds template values/);
    }
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

describe('installed.json shared with the client and the launcher (shared/local-ai/fixtures)', () => {
  /** The fixture model bytes: byte i is (i * 7 + 3) & 0xff, the first four bytes spell GGUF (see the fixtures README). */
  const fixtureModelBytes = () => {
    const bytes = Buffer.alloc(4096);
    for (let i = 0; i < bytes.length; i += 1) bytes[i] = (i * 7 + 3) & 0xff;
    bytes.write('GGUF', 0, 'latin1');
    return bytes;
  };
  const fixtureServerBytes = () => Buffer.from('#!/bin/sh\necho fixture llama-server b11429\n', 'ascii');
  const sharedManifest = () => JSON.parse(readFileSync(join(SHARED_FIXTURES, 'manifest.example.json'), 'utf8'));
  const sharedInstalled = () => JSON.parse(readFileSync(join(SHARED_FIXTURES, 'installed.example.json'), 'utf8'));

  test('manifest.example.json is a complete manifest whose sizes and digests are the documented bytes', () => {
    const manifest = sharedManifest();
    assert.deepEqual(validateWithSchemaFile(SCHEMA, manifest).errors, []);
    assert.deepEqual(templateValues(manifest), []);
    assert.equal(manifest.model.size, 4096);
    assert.equal(manifest.model.sha256, sha256(fixtureModelBytes()));
    for (const [key, entry] of Object.entries(manifest.runtime.platforms)) {
      const bytes = Buffer.from(`VANTA Local AI fixture archive ${entry.file}\n`, 'ascii');
      assert.equal(entry.size, bytes.length, key);
      assert.equal(entry.sha256, sha256(bytes), key);
    }
    assert.equal(sharedInstalled().files.serverSize, fixtureServerBytes().length);
  });

  test('installedRecord writes exactly installed.example.json from the fixed inputs (same keys, nesting and values)', () => {
    const manifest = sharedManifest();
    const expected = sharedInstalled();
    const dir = scratch();
    const server = join(dir, 'runtime', 'b11429', 'linux-x64', 'llama-b11429', 'llama-server');
    const model = join(dir, 'models', 'Qwen3-1.7B-Q8_0.gguf');
    mkdirSync(join(dir, 'runtime', 'b11429', 'linux-x64', 'llama-b11429'), { recursive: true });
    mkdirSync(join(dir, 'models'), { recursive: true });
    writeFileSync(server, fixtureServerBytes());
    writeFileSync(model, fixtureModelBytes());
    utimesSync(server, new Date(1791460800000), new Date(1791460800000));
    utimesSync(model, new Date(1791460860000), new Date(1791460860000));
    const record = installedRecord(manifest, 'linux-x64', { installedAt: '2026-10-08T12:00:00Z', verifiedAt: '2026-10-08T12:05:00Z', server, model });
    assert.deepEqual(record, expected);
    assert.deepEqual(Object.keys(record), Object.keys(expected), 'key order as core writes it');
    assert.deepEqual(Object.keys(record.runtime), Object.keys(expected.runtime));
    assert.deepEqual(Object.keys(record.runtime.platforms['linux-x64']), Object.keys(expected.runtime.platforms['linux-x64']));
    assert.deepEqual(Object.keys(record.model), Object.keys(expected.model));
    assert.deepEqual(Object.keys(record.files), ['serverSize', 'serverMtime', 'modelSize', 'modelMtime']);
    assert.equal(JSON.stringify(record, null, 2), JSON.stringify(expected, null, 2), 'byte for byte as JSON text');
    assert.equal(installedMatches(expected, manifest, 'linux-x64'), true);
    assert.equal(installedMatches(expected, manifest, 'windows-x64'), false);
    // The fixture directory is a complete install for status as well.
    writeFileSync(join(dir, 'installed.json'), json(expected));
    const report = statusReport({ manifest, manifestPath: 'm.json', dir, platform: 'linux-x64', root: makeRoot() });
    assert.equal(report.ok, true, report.lines.join('\n'));
  });
});

describe('prepare --keep-downloads, mirror and serve (the CI launcher integration against a local mirror)', () => {
  const net = fakeInternet();
  before(net.start);
  after(net.stop);

  test('prepare --keep-downloads keeps the verified archive and a link to the model under downloads/, and restores a missing archive', async () => {
    net.reset();
    const root = makeRoot();
    const manifest = fixtureManifest();
    const dir = join(scratch(), 'local-ai');
    const linux = fixtureArchives()[2];
    const first = await prepareInstall({ manifest, dir, platform: 'linux-x64', client: net.client(), root, now: NOW, keepDownloads: true });
    assert.deepEqual(first.downloaded, ['models/Qwen3-1.7B-Q8_0.gguf', 'runtime/b11429/linux-x64']);
    assert.deepEqual(first.kept, [`downloads/${linux.file}`, 'downloads/Qwen3-1.7B-Q8_0.gguf']);
    assert.deepEqual(readdirSync(join(dir, 'downloads')).sort(), ['Qwen3-1.7B-Q8_0.gguf', linux.file].sort());
    assert.deepEqual(readFileSync(join(dir, 'downloads', linux.file)), linux.bytes);
    assert.deepEqual(readFileSync(join(dir, 'downloads', 'Qwen3-1.7B-Q8_0.gguf')), fixtureModel().bytes);
    if (process.platform !== 'win32') {
      assert.equal(statSync(join(dir, 'downloads', 'Qwen3-1.7B-Q8_0.gguf')).ino, statSync(join(dir, 'models', 'Qwen3-1.7B-Q8_0.gguf')).ino, 'a hard link, no second copy');
    }
    assert.equal(net.hitsOf(archiveUrl(linux.file)), 1);
    assert.equal(net.hitsOf(MODEL_URL), 1);

    // A second run verifies and keeps everything without a download.
    const second = await prepareInstall({ manifest, dir, platform: 'linux-x64', client: net.client(), root, now: NOW, keepDownloads: true });
    assert.deepEqual(second.downloaded, []);
    assert.deepEqual(second.kept, first.kept);
    assert.equal(net.hitsOf(archiveUrl(linux.file)), 1);
    assert.equal(net.hitsOf(MODEL_URL), 1);

    // The archive was removed (an earlier run without the flag, or an older cache): only the archive is fetched again,
    // the runtime is not extracted again.
    rmSync(join(dir, 'downloads', linux.file));
    const serverBefore = statSync(join(dir, 'runtime', 'b11429', 'linux-x64', ...linux.serverPath.split('/'))).mtimeMs;
    const third = await prepareInstall({ manifest, dir, platform: 'linux-x64', client: net.client(), root, now: NOW, keepDownloads: true });
    assert.deepEqual(third.downloaded, []);
    assert.deepEqual(third.skipped, ['models/Qwen3-1.7B-Q8_0.gguf', 'runtime/b11429/linux-x64']);
    assert.deepEqual(third.kept, first.kept);
    assert.equal(net.hitsOf(archiveUrl(linux.file)), 2, 'the archive alone is downloaded again');
    assert.equal(net.hitsOf(MODEL_URL), 1);
    assert.equal(statSync(join(dir, 'runtime', 'b11429', 'linux-x64', ...linux.serverPath.split('/'))).mtimeMs, serverBefore, 'runtime untouched');
    assert.deepEqual(readFileSync(join(dir, 'downloads', linux.file)), linux.bytes);

    // A wrong file under downloads/ is replaced (the link is removed first: writing through it would change models/ too).
    rmSync(join(dir, 'downloads', 'Qwen3-1.7B-Q8_0.gguf'));
    writeFileSync(join(dir, 'downloads', 'Qwen3-1.7B-Q8_0.gguf'), 'tampered');
    writeFileSync(join(dir, 'downloads', linux.file), 'tampered');
    await prepareInstall({ manifest, dir, platform: 'linux-x64', client: net.client(), root, now: NOW, keepDownloads: true });
    assert.deepEqual(readFileSync(join(dir, 'downloads', 'Qwen3-1.7B-Q8_0.gguf')), fixtureModel().bytes);
    assert.deepEqual(readFileSync(join(dir, 'downloads', linux.file)), linux.bytes);
    assert.equal(net.hitsOf(archiveUrl(linux.file)), 3);
    assert.equal(net.hitsOf(MODEL_URL), 1, 'the model is relinked from models/, not downloaded');

    // Without the flag nothing extra is kept on a fresh directory.
    const plain = await prepareInstall({ manifest, dir: join(scratch(), 'plain'), platform: 'linux-x64', client: net.client(), root, now: NOW });
    assert.deepEqual(plain.kept, []);
    assert.deepEqual(readdirSync(plain.paths.downloadsDir), []);
  });

  test('mirrorManifest replaces only the prepared platform and model URLs; the base must be https or loopback http', () => {
    const manifest = fixtureManifest();
    const mirrored = mirrorManifest(manifest, 'linux-x64', 'http://127.0.0.1:8765');
    const linux = fixtureArchives()[2];
    assert.equal(mirrored.runtime.platforms['linux-x64'].url, `http://127.0.0.1:8765/${linux.file}`);
    assert.equal(mirrored.model.url, 'http://127.0.0.1:8765/Qwen3-1.7B-Q8_0.gguf');
    assert.equal(mirrored.resolvedAt, manifest.resolvedAt);
    assert.deepEqual(mirrored.runtime.platforms['windows-x64'], manifest.runtime.platforms['windows-x64'], 'other platforms unchanged');
    assert.deepEqual({ ...mirrored.runtime.platforms['linux-x64'], url: manifest.runtime.platforms['linux-x64'].url }, manifest.runtime.platforms['linux-x64']);
    assert.deepEqual({ ...mirrored.model, url: manifest.model.url }, manifest.model);
    assert.deepEqual(manifest, fixtureManifest(), 'the input is not modified');
    assert.equal(mirrorBase('http://localhost:9/'), 'http://localhost:9/');
    assert.equal(mirrorBase('https://mirror.example/files'), 'https://mirror.example/files/');
    assert.throws(() => mirrorBase('http://example.com/'), (e) => e instanceof UsageError && /must be https, or http on 127\.0\.0\.1/.test(e.message));
    assert.throws(() => mirrorBase('ftp://127.0.0.1/'), UsageError);
    assert.throws(() => mirrorBase('not a url'), UsageError);
    assert.throws(() => mirrorBase('http://127.0.0.1/?x=1'), UsageError);
    assert.throws(() => mirrorManifest(manifest, 'freebsd-x64', 'http://127.0.0.1/'), UsageError);
    // The mirror is not a committable manifest: the schema insists on https.
    assert.equal(validateWithSchemaFile(SCHEMA, mirrored).valid, false);
  });

  test('serve answers GET and HEAD for bare file names with Content-Length and nothing else', async () => {
    const dir = scratch();
    mkdirSync(join(dir, 'sub'));
    writeFileSync(join(dir, 'a.bin'), Buffer.from([1, 2, 3, 4, 5]));
    writeFileSync(join(dir, 'sub', 'b.bin'), 'nested');
    writeFileSync(join(dir, 'with space.gguf'), 'spaced');
    const logged = [];
    const served = await startFileServer({ dir, port: 0, log: (m) => logged.push(m) });
    try {
      assert.match(served.url, /^http:\/\/127\.0\.0\.1:\d+\/$/);
      const got = await fetch(`${served.url}a.bin`);
      assert.equal(got.status, 200);
      assert.equal(got.headers.get('content-length'), '5');
      assert.equal(got.headers.get('content-type'), 'application/octet-stream');
      assert.deepEqual(Buffer.from(await got.arrayBuffer()), Buffer.from([1, 2, 3, 4, 5]));
      const head = await fetch(`${served.url}a.bin`, { method: 'HEAD' });
      assert.equal(head.status, 200);
      assert.equal(head.headers.get('content-length'), '5');
      assert.equal((await head.arrayBuffer()).byteLength, 0);
      const spaced = await fetch(`${served.url}with%20space.gguf`);
      assert.equal(spaced.status, 200);
      assert.equal(await spaced.text(), 'spaced');
      for (const path of ['', 'missing.bin', 'sub', 'sub/b.bin', '..%2Fa.bin', '.', '%2e%2e']) {
        const r = await fetch(`${served.url}${path}`);
        assert.equal(r.status, 404, path);
      }
      const post = await fetch(`${served.url}a.bin`, { method: 'POST', body: 'x' });
      assert.equal(post.status, 405);
      assert.equal(post.headers.get('allow'), 'GET, HEAD');
      assert.ok(logged.some((line) => /^GET \/a\.bin 200 5$/.test(line)), logged.join('\n'));
    } finally {
      await served.close();
    }
    assert.equal(servedName('/a.bin'), 'a.bin');
    assert.equal(servedName('/'), null);
    assert.equal(servedName('/x/y'), null);
    assert.equal(servedName('/..'), null);
    assert.equal(servedName('a.bin'), null);
    assert.equal(servedName('/%ZZ'), null);
    await assert.rejects(startFileServer({ dir: join(dir, 'nope'), port: 0 }), UsageError);
  });

  test('end to end: prepare --keep-downloads, serve downloads/, mirror, and a second prepare from the mirror alone', async () => {
    net.reset();
    const root = makeRoot(fixtureManifest());
    const prepared = join(scratch(), 'prepared');
    const out = [];
    const err = [];
    const deps = { fetch: net.fetchImpl, log: (m) => out.push(m), logError: (m) => err.push(m), env: {}, now: NOW, delayMs: 0, signals: false };
    assert.equal(await main(['prepare', '--dir', prepared, '--platform', 'linux-x64', '--root', root, '--keep-downloads'], deps), 0, err.join('\n'));
    assert.match(out.at(-1), /\(2 downloaded, 0 already present, 2 kept under downloads\/\)$/);

    // mirror refuses a directory without kept downloads and a template manifest.
    const bare = join(scratch(), 'bare');
    const mirrorPath = join(scratch(), 'mirror.json');
    assert.equal(await main(['mirror', '--dir', bare, '--base', 'http://127.0.0.1:1/', '--out', mirrorPath, '--platform', 'linux-x64', '--root', root], deps), 1);
    assert.match(err.at(-1), /downloads\/llama-b11429-bin-ubuntu-x64\.tar\.gz is missing under .*run `prepare --dir .* --keep-downloads` first/);
    const templateFile = join(root, 'template.json');
    writeFileSync(templateFile, json(fixtureManifest({ template: true })));
    assert.equal(await main(['mirror', '--dir', prepared, '--base', 'http://127.0.0.1:1/', '--out', mirrorPath, '--platform', 'linux-x64', '--manifest', templateFile, '--root', root], deps), 2);
    assert.match(err.at(-1), /is not a resolved manifest/);
    assert.equal(existsSync(mirrorPath), false);

    // serve through the CLI; the test gets the server through onServe and stops it when done.
    let running;
    const serving = main(['serve', '--dir', join(prepared, 'downloads'), '--port', '0'], { ...deps, onServe: (s) => { running = s; } });
    for (let i = 0; i < 200 && !running; i += 1) await new Promise((tick) => setTimeout(tick, 10));
    assert.ok(running, 'the server started');
    try {
      assert.ok(out.some((line) => line === `serving ${join(prepared, 'downloads')} on ${running.url}`), out.join('\n'));
      const head = await fetch(`${running.url}Qwen3-1.7B-Q8_0.gguf`, { method: 'HEAD' });
      assert.equal(head.status, 200);
      assert.equal(head.headers.get('content-length'), String(fixtureModel().size));

      assert.equal(await main(['mirror', '--dir', prepared, '--base', running.url, '--out', mirrorPath, '--platform', 'linux-x64', '--root', root], deps), 0, err.join('\n'));
      const mirror = JSON.parse(readFileSync(mirrorPath, 'utf8'));
      assert.deepEqual(mirror, mirrorManifest(fixtureManifest(), 'linux-x64', running.url));
      assert.match(out.at(-1), /^wrote .*mirror\.json \(mirror of .* for linux-x64; local use only, never commit it\)$/);
      assert.equal(readFileSync(mirrorPath, 'utf8'), json(mirror));

      // What the launcher does in CI, done here with the script's own downloader against the real global fetch:
      // everything comes from 127.0.0.1, nothing from the fake GitHub or Hugging Face, and every SHA-256 still matches.
      // (prepare itself insists on the schema, so a mirror manifest with http URLs is for the launcher only.)
      const fromMirror = join(scratch(), 'from-mirror');
      const hitsBefore = { archive: net.hitsOf(archiveUrl(fixtureArchives()[2].file)), model: net.hitsOf(MODEL_URL) };
      const direct = new Client({ fetch, userAgent: 'VANTA tests', delayMs: 0 });
      const linux = fixtureArchives()[2];
      const archiveResult = await direct.download(mirror.runtime.platforms['linux-x64'].url, join(fromMirror, linux.file), { expectedSize: linux.size, expectedSha256: linux.sha256 });
      const modelResult = await direct.download(mirror.model.url, join(fromMirror, 'Qwen3-1.7B-Q8_0.gguf'), { expectedSize: fixtureModel().size, expectedSha256: fixtureModel().sha256 });
      assert.deepEqual(archiveResult, { size: linux.size, sha256: linux.sha256 });
      assert.deepEqual(modelResult, { size: fixtureModel().size, sha256: fixtureModel().sha256 });
      assert.equal(findServerPath(join(fromMirror, linux.file), 'llama-server'), linux.serverPath, 'the served archive is the real one');
      assert.equal(net.hitsOf(archiveUrl(linux.file)), hitsBefore.archive, 'nothing fetched from the fake GitHub');
      assert.equal(net.hitsOf(MODEL_URL), hitsBefore.model, 'nothing fetched from the fake Hugging Face');
      await assert.rejects(direct.download(`${running.url}missing.gguf`, join(fromMirror, 'missing.gguf')), /HTTP 404/);
      await assert.rejects(prepareInstall({ manifest: mirror, dir: join(fromMirror, 'x'), platform: 'linux-x64', client: direct, root, now: NOW }),
        (e) => e instanceof UsageError && /does not match shared\/schemas\/local-ai\.schema\.json/.test(e.message), 'prepare refuses a mirror manifest');
    } finally {
      await running.close();
    }
    assert.equal(await serving, 0);

    for (const argv of [
      ['mirror', '--dir', prepared, '--out', mirrorPath], ['mirror', '--dir', prepared, '--base', 'http://127.0.0.1/'], ['mirror', '--base', 'x', '--out', 'y'],
      ['serve', '--dir', prepared], ['serve', '--dir', prepared, '--port', '70000'], ['serve', '--dir', prepared, '--port', '8', '--manifest', 'm.json'],
      ['prepare', '--dir', 'x', '--base', 'http://127.0.0.1/'], ['status', '--port', '1'], ['resolve', '--keep-downloads'], ['status', '--keep-downloads'],
    ]) {
      assert.equal(await main(argv, deps), 2, JSON.stringify(argv));
    }
    assert.equal(await main(['serve', '--dir', join(prepared, 'nope'), '--port', '0'], deps), 2);
    assert.match(err.at(-1), /is not a directory/);
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
    const unwrapped = note.replace(/\n\s*/g, ' ');
    // The two sites answer with redirects; the note names the file hosts the download follows instead of claiming
    // that nothing but github.com and huggingface.co is ever contacted.
    assert.match(unwrapped, /github\.com \(runtime\) and huggingface\.co \(model\); each answers with a redirect to its own file host, which the download follows \(GitHub's release asset host objects\.githubusercontent\.com, Hugging Face's CDN hosts\)\. Beyond those two sites and their file hosts, nothing else is contacted for the Local AI\./);
    assert.doesNotMatch(unwrapped, /huggingface\.co \(model\); nothing else is contacted/, 'the old exclusivity claim is gone');
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
      assert.equal((text.match(/key: local-ai-linux-x64-v2-\$\{\{ hashFiles\('shared\/local-ai\/local-ai\.json'\) \}\}/g) ?? []).length, 2, `${id}: restore and save share the cache key on the manifest hash`);
      assert.match(text, /node scripts\/release\/local-ai\.mjs prepare --dir "\$RUNNER_TEMP\/local-ai" --platform linux-x64 --keep-downloads/, `${id}: prepare keeps the downloads for the mirror`);
      assert.match(text, /echo "VANTA_LOCAL_AI_DIR=\$RUNNER_TEMP\/local-ai" >> "\$GITHUB_ENV"/, `${id}: exported to the test`);
      // prepare runs on a cache hit as well (it re-records the restored files' modification times); only the save is
      // limited to a miss.
      const prepareStep = step(text, 'Prepare the Local AI install');
      assert.match(prepareStep, /\n {8}if: steps\.local-ai-manifest\.outputs\.ready == 'true'\n/, `${id}: prepare is guarded by the manifest only`);
      assert.doesNotMatch(prepareStep, /cache-hit/, `${id}: prepare is not skipped on a cache hit`);
      assert.match(prepareStep, /after the cache restore on purpose/, `${id}: says why`);
      const saveStep = text.slice(text.indexOf('actions/cache/save@v4'));
      assert.match(saveStep.slice(0, 400), /steps\.local-ai-cache\.outputs\.cache-hit != 'true'/, `${id}: the save runs on a miss only`);
      const guard = text.indexOf('if node scripts/release/local-ai.mjs status; then');
      const restore = text.indexOf('actions/cache/restore@v4');
      const prepare = text.indexOf('local-ai.mjs prepare');
      const save = text.indexOf('actions/cache/save@v4');
      const run = text.indexOf(id === 'launcher-integration' ? 'java -jar launcher/build/libs/vanta-launcher-*-all.jar --install' : 'runProductionClientGametest');
      assert.ok(guard < restore && restore < prepare && prepare < save && save < run, `${id}: guard, restore, prepare, save, then the test`);
    }
  });

  /** The text of one step of a job, from its `- name:` line to the next step. */
  function step(jobText, namePrefix) {
    const start = jobText.indexOf(`      - name: ${namePrefix}`);
    assert.ok(start >= 0, `step '${namePrefix}' exists`);
    const next = jobText.slice(start + 1).search(/\n {6}- /);
    return next < 0 ? jobText.slice(start) : jobText.slice(start, start + 1 + next);
  }

  test('ci.yml installs the Local AI through the launcher from a local mirror of the prepared files and checks installed.json against the fixture', () => {
    const text = job(CI, 'launcher-integration');
    const install = step(text, 'Install the Local AI through the launcher from a local mirror');
    assert.match(install, /\n {8}if: steps\.local-ai-manifest\.outputs\.ready == 'true'\n/, 'skipped while the manifest is the template');
    assert.match(install, /node scripts\/release\/local-ai\.mjs serve --dir "\$RUNNER_TEMP\/local-ai\/downloads" --port "\$MIRROR_PORT" > "\$RUNNER_TEMP\/local-ai-serve\.log" 2>&1 &/);
    assert.match(install, /trap 'kill "\$SERVE_PID" 2>\/dev\/null \|\| true' EXIT/, 'the server is stopped when the step ends');
    assert.match(install, /curl -sfI "http:\/\/127\.0\.0\.1:\$MIRROR_PORT\/\$ARCHIVE"/, 'waits for the server');
    assert.match(install, /node scripts\/release\/local-ai\.mjs mirror --dir "\$RUNNER_TEMP\/local-ai" --base "http:\/\/127\.0\.0\.1:\$MIRROR_PORT\/" --out "\$RUNNER_TEMP\/local-ai-mirror\.json" --platform linux-x64/);
    assert.match(install, /VANTA_LOCAL_AI_MANIFEST="\$RUNNER_TEMP\/local-ai-mirror\.json" java -jar launcher\/build\/libs\/vanta-launcher-\*-all\.jar --install-local-ai --data-dir "\$RUNNER_TEMP\/vanta"/);
    assert.match(install, /grep -F "Downloaded Local AI runtime \$ARCHIVE"/);
    assert.match(install, /grep -F "Downloaded Local AI model \$MODEL"/);
    assert.match(install, /grep -F "Note: \$RUNNER_TEMP\/vanta\/instances\/vanta-1\.21\.11\/config\/vanta\/local-ai\.json"/);
    assert.match(install, /grep -E "\^GET \/\$ARCHIVE 200 "/, 'the archive came from the mirror');
    assert.match(install, /grep -E "\^GET \/\$MODEL 200 "/, 'the model came from the mirror');

    const check = step(text, 'Local AI status line, installed.json in the client');
    assert.match(check, /\n {8}if: steps\.local-ai-manifest\.outputs\.ready == 'true'\n/);
    assert.match(check, /EXPECTED="Local AI: installed \(\$\(jq -r '\.runtime\.name \+ " " \+ \.runtime\.tag \+ ", " \+ \.model\.name \+ " " \+ \.model\.quantization' "\$MANIFEST"\)\)"/,
      'the exact status line, "Local AI: installed (llama.cpp b11429, Qwen3-1.7B Q8_0)" for the b11429 manifest');
    assert.match(check, /--local-ai-status --data-dir "\$DATA"/);
    assert.match(check, /grep -Fx "\$EXPECTED" "\$RUNNER_TEMP\/local-ai-status\.out"/, 'whole-line match');
    assert.match(check, /grep -Fx "Server: \$DATA\/local-ai\/runtime\/\$TAG\/linux-x64\/\$SERVER_PATH"/);
    assert.match(check, /grep -Fx "Model: \$DATA\/local-ai\/models\/\$MODEL"/);
    assert.match(check, /node "\$RUNNER_TEMP\/check-installed\.mjs" "\$DATA\/local-ai\/installed\.json" shared\/local-ai\/fixtures\/installed\.example\.json "\$MANIFEST"/, 'the key set is compared with the shared fixture');
    assert.match(check, /prefix === 'runtime\.platforms' \? '<platform>' : k/, 'the platform key is normalised before the comparison');
    assert.match(check, /test "\$\(realpath -m "\$\(jq -r \.localAiDir "\$NOTE"\)"\)" = "\$\(realpath -m "\$DATA\/local-ai"\)"/, 'the note points at the launcher folder');
    assert.match(check, /--remove-local-ai --data-dir "\$DATA"/);
    assert.match(check, /grep -F "Removed the Local AI from \$DATA\/local-ai \("/);
    assert.match(check, /test ! -e "\$DATA\/local-ai"/, 'the folder is gone');
    assert.match(check, /grep -Fx "Local AI: not installed \(install it with --install-local-ai\)"/);
    // The embedded node check is the one this repository tests in its own right: same key-path logic as the fixture tests.
    const js = check.slice(check.indexOf("<<'JS'") + 6, check.indexOf('\n          JS\n'));
    assert.match(js, /actual\.platform !== 'linux-x64'/);
    assert.match(js, /actual\.model\?\.sha256 !== manifest\.model\.sha256/);

    // Order inside the job: the launcher's --install (which creates the instance) comes first, then the mirror install,
    // the checks and the removal, and only then the official-profile and the headless launch steps.
    const installIndex = text.indexOf('java -jar launcher/build/libs/vanta-launcher-*-all.jar --install --client-jar');
    const mirrorIndex = text.indexOf('--install-local-ai --data-dir');
    const removeIndex = text.indexOf('--remove-local-ai --data-dir');
    const officialIndex = text.indexOf('--install-official-profile --client-jar');
    const launchIndex = text.indexOf('--launch --dev-offline');
    assert.ok(installIndex < mirrorIndex && mirrorIndex < removeIndex && removeIndex < officialIndex && officialIndex < launchIndex, 'install, mirror install, remove, official profile, launch');
    for (const log of ['local-ai-serve.log', 'local-ai-install.out', 'local-ai-status.out', 'local-ai-remove.out']) {
      assert.ok(text.includes(`\${{ runner.temp }}/${log}`), `${log} is uploaded with the job's logs`);
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
