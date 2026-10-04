import { test, describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, readFileSync, existsSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { buildManifest, upsertFileEntry, updateSums, main } from './build-manifest.mjs';
import { REPO_ROOT } from './lib/repo.mjs';

const URL_BASE = 'https://github.com/example/VANTA-Client/releases/download';

function sha256(buffer) {
  return createHash('sha256').update(buffer).digest('hex');
}

/** Creates a fake repository root with the schema and a gradle.properties, plus a dist dir. */
function makeFixture() {
  const root = mkdtempSync(join(tmpdir(), 'vanta-build-manifest-'));
  mkdirSync(join(root, 'shared', 'schemas'), { recursive: true });
  mkdirSync(join(root, 'shared', 'releases'), { recursive: true });
  mkdirSync(join(root, 'client'), { recursive: true });
  mkdirSync(join(root, 'dist'), { recursive: true });
  writeFileSync(join(root, 'shared', 'schemas', 'release-manifest.schema.json'), readFileSync(join(REPO_ROOT, 'shared', 'schemas', 'release-manifest.schema.json')));
  writeFileSync(join(root, 'client', 'gradle.properties'), 'minecraft_version=1.21.11\nloader_version=0.19.5\nfabric_api_version=0.141.6+1.21.11\nmod_version=1.0.0\n');
  const jar = Buffer.from('not really a jar but fine for hashing');
  writeFileSync(join(root, 'dist', 'vanta-client-1.0.0.jar'), jar);
  return { root, jar };
}

describe('upsertFileEntry / updateSums', () => {
  test('replaces by name and appends new entries', () => {
    const files = [{ name: 'a.jar', downloadUrl: '', size: 0, sha256: '' }];
    const next = upsertFileEntry(files, { name: 'a.jar', downloadUrl: 'https://x/a.jar', size: 5, sha256: 'f'.repeat(64) });
    assert.equal(next.length, 1);
    assert.equal(next[0].size, 5);
    assert.equal(files[0].size, 0, 'input is not mutated');
    const added = upsertFileEntry(next, { name: 'b.msi', downloadUrl: 'https://x/b.msi', size: 1, sha256: 'e'.repeat(64) });
    assert.deepEqual(added.map((f) => f.name), ['a.jar', 'b.msi']);
  });

  test('SHA256SUMS lines are sha256sum compatible and updated in place', () => {
    const first = updateSums('', 'a.jar', 'a'.repeat(64));
    assert.equal(first, `${'a'.repeat(64)}  a.jar\n`);
    const second = updateSums(first, 'b.exe', 'b'.repeat(64));
    assert.equal(second.split('\n').filter(Boolean).length, 2);
    const replaced = updateSums(second, 'a.jar', 'c'.repeat(64));
    assert.match(replaced, new RegExp(`^${'c'.repeat(64)}  a\\.jar$`, 'm'));
    assert.doesNotMatch(replaced, new RegExp('a'.repeat(64)));
    const binaryMarker = updateSums(`${'d'.repeat(64)} *a.jar\n`, 'a.jar', 'e'.repeat(64));
    assert.equal(binaryMarker.split('\n').filter(Boolean).length, 1);
  });
});

describe('buildManifest()', () => {
  let fixture;
  beforeEach(() => {
    fixture = makeFixture();
  });

  test('creates a manifest from the toolchain, hashes the file and writes latest + sums', async () => {
    const file = join(fixture.root, 'dist', 'vanta-client-1.0.0.jar');
    const result = await buildManifest({
      root: fixture.root,
      product: 'client',
      version: '1.0.0',
      file,
      url: `${URL_BASE}/client-v1.0.0/vanta-client-1.0.0.jar`,
      date: '2026-10-04',
    });
    assert.equal(result.created, true);
    assert.equal(result.manifest.minecraftVersion, '1.21.11');
    assert.equal(result.manifest.fabricVersion, '0.19.5');
    assert.equal(result.manifest.fabricApiVersion, '0.141.6+1.21.11');
    assert.equal(result.manifest.javaVersion, 21);
    assert.equal(result.manifest.releaseDate, '2026-10-04');
    assert.equal(result.manifest.channel, 'stable');
    assert.equal(result.manifest.changelog, 'website/content/changelog/client-1.0.0.md');
    assert.equal(result.manifest.files.length, 1);
    assert.equal(result.entry.size, fixture.jar.length);
    assert.equal(result.entry.sha256, sha256(fixture.jar));
    const written = JSON.parse(readFileSync(join(fixture.root, 'shared', 'releases', 'client-1.0.0.json'), 'utf8'));
    assert.deepEqual(written, result.manifest);
    assert.ok(readFileSync(result.manifestPath, 'utf8').endsWith('\n'), 'pretty JSON ends with newline');
    const latest = JSON.parse(readFileSync(join(fixture.root, 'shared', 'releases', 'latest', 'client-latest.json'), 'utf8'));
    assert.deepEqual(latest, result.manifest);
    const sums = readFileSync(join(fixture.root, 'dist', 'SHA256SUMS.txt'), 'utf8');
    assert.equal(sums, `${sha256(fixture.jar)}  vanta-client-1.0.0.jar\n`);
  });

  test('updates an existing manifest, keeps unrelated entries and accumulates launcher files', async () => {
    const releases = join(fixture.root, 'shared', 'releases');
    writeFileSync(join(releases, 'launcher-1.0.0.json'), JSON.stringify({
      schemaVersion: 1,
      product: 'launcher',
      version: '1.0.0',
      minecraftVersion: '1.21.11',
      fabricVersion: '0.19.5',
      fabricApiVersion: '0.141.6+1.21.11',
      javaVersion: 21,
      releaseDate: '2026-01-01',
      channel: 'stable',
      files: [
        { name: 'VANTA-Launcher-1.0.0.msi', downloadUrl: '', size: 0, sha256: '' },
        { name: 'VANTA-Launcher-1.0.0.exe', downloadUrl: '', size: 0, sha256: '' },
      ],
      changelog: 'website/content/changelog/launcher-1.0.0.md',
      notes: 'Known issue: none',
    }));
    const msi = Buffer.from('msi bytes');
    const exe = Buffer.from('exe bytes, a bit longer');
    writeFileSync(join(fixture.root, 'dist', 'VANTA-Launcher-1.0.0.msi'), msi);
    writeFileSync(join(fixture.root, 'dist', 'VANTA-Launcher-1.0.0.exe'), exe);
    const common = { root: fixture.root, product: 'launcher', version: '1.0.0', date: '2026-10-04', sumsPath: join(fixture.root, 'dist', 'SHA256SUMS.txt') };
    const first = await buildManifest({ ...common, file: join(fixture.root, 'dist', 'VANTA-Launcher-1.0.0.msi'), url: `${URL_BASE}/launcher-v1.0.0/VANTA-Launcher-1.0.0.msi` });
    assert.equal(first.created, false);
    assert.equal(first.manifest.files[0].size, msi.length);
    assert.equal(first.manifest.files[1].downloadUrl, '', 'exe untouched so far');
    assert.equal(first.manifest.notes, 'Known issue: none');
    assert.equal(first.manifest.releaseDate, '2026-10-04');
    const second = await buildManifest({ ...common, file: join(fixture.root, 'dist', 'VANTA-Launcher-1.0.0.exe'), url: `${URL_BASE}/launcher-v1.0.0/VANTA-Launcher-1.0.0.exe` });
    assert.deepEqual(second.manifest.files.map((f) => [f.name, f.size]), [
      ['VANTA-Launcher-1.0.0.msi', msi.length],
      ['VANTA-Launcher-1.0.0.exe', exe.length],
    ]);
    const sums = readFileSync(common.sumsPath, 'utf8').trim().split('\n');
    assert.equal(sums.length, 2);
    assert.equal(sums[0], `${sha256(msi)}  VANTA-Launcher-1.0.0.msi`);
    assert.equal(sums[1], `${sha256(exe)}  VANTA-Launcher-1.0.0.exe`);
  });

  test('rejects http URLs, bad versions, unknown products, empty files and mismatched manifests', async () => {
    const file = join(fixture.root, 'dist', 'vanta-client-1.0.0.jar');
    await assert.rejects(buildManifest({ root: fixture.root, product: 'client', version: '1.0.0', file, url: 'http://insecure/x.jar' }), /https URL/);
    await assert.rejects(buildManifest({ root: fixture.root, product: 'client', version: 'v1', file, url: `${URL_BASE}/x.jar` }), /SemVer/);
    await assert.rejects(buildManifest({ root: fixture.root, product: 'website', version: '1.0.0', file, url: `${URL_BASE}/x.jar` }), /--product/);
    await assert.rejects(buildManifest({ root: fixture.root, product: 'client', version: '1.0.0', file: join(fixture.root, 'nope.jar'), url: `${URL_BASE}/x.jar` }), /does not exist/);
    await assert.rejects(buildManifest({ root: fixture.root, product: 'client', version: '1.0.0', file, url: `${URL_BASE}/x.jar`, date: '04.10.2026' }), /YYYY-MM-DD/);
    await assert.rejects(buildManifest({ root: fixture.root, product: 'client', version: '1.0.0', file, url: `${URL_BASE}/x.jar`, channel: 'nightly' }), /channel/);
    writeFileSync(join(fixture.root, 'dist', 'empty.jar'), '');
    await assert.rejects(buildManifest({ root: fixture.root, product: 'client', version: '1.0.0', file: join(fixture.root, 'dist', 'empty.jar'), url: `${URL_BASE}/empty.jar` }), /empty file/);
    writeFileSync(join(fixture.root, 'shared', 'releases', 'client-2.0.0.json'), JSON.stringify({ product: 'launcher', version: '2.0.0' }));
    await assert.rejects(buildManifest({ root: fixture.root, product: 'client', version: '2.0.0', file, url: `${URL_BASE}/x.jar` }), /describes product/);
    assert.equal(existsSync(join(fixture.root, 'shared', 'releases', 'client-1.0.0.json')), false, 'nothing written on failure');
  });

  test('schema validation guards the output (a file name with a path separator is rejected)', async () => {
    const file = join(fixture.root, 'dist', 'vanta-client-1.0.0.jar');
    await assert.rejects(
      buildManifest({ root: fixture.root, product: 'client', version: '1.0.0', file, url: `${URL_BASE}/x.jar`, name: '../evil.jar' }),
      /manifest is invalid/,
    );
  });

  test('--dry-run writes nothing; --no-latest skips the latest copy', async () => {
    const file = join(fixture.root, 'dist', 'vanta-client-1.0.0.jar');
    const dry = await buildManifest({ root: fixture.root, product: 'client', version: '1.0.0', file, url: `${URL_BASE}/x.jar`, dryRun: true });
    assert.equal(dry.manifest.files[0].size, fixture.jar.length);
    assert.equal(existsSync(dry.manifestPath), false);
    assert.equal(existsSync(dry.sumsPath), false);
    const noLatest = await buildManifest({ root: fixture.root, product: 'client', version: '1.0.0', file, url: `${URL_BASE}/x.jar`, latest: false });
    assert.equal(noLatest.latestPath, null);
    assert.equal(existsSync(join(fixture.root, 'shared', 'releases', 'latest')), false);
  });

  test('CLI: parses arguments and reports usage errors', async () => {
    const file = join(fixture.root, 'dist', 'vanta-client-1.0.0.jar');
    const out = [];
    const err = [];
    const code = await main([
      '--product', 'client', '--version', '1.0.0', '--file', file, '--url', `${URL_BASE}/client-v1.0.0/vanta-client-1.0.0.jar`,
      '--root', fixture.root, '--date=2026-10-04', '--sums', join(fixture.root, 'dist', 'SHA256SUMS.txt'),
    ], (m) => out.push(m), (m) => err.push(m));
    assert.equal(code, 0, err.join('\n'));
    assert.match(out.join('\n'), /sha256 [a-f0-9]{64}/);
    assert.ok(existsSync(join(fixture.root, 'shared', 'releases', 'client-1.0.0.json')));
    assert.equal(await main(['--product', 'client'], () => {}, (m) => err.push(m)), 2);
    assert.match(err.join('\n'), /--version is required/);
    assert.equal(await main(['--product', 'client', '--version', '1.0.0', '--file', file, '--url', 'http://x', '--root', fixture.root], () => {}, (m) => err.push(m)), 1);
    assert.equal(await main(['--help'], (m) => out.push(m), () => {}), 0);
  });
});
