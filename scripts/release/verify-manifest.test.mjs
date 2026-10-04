import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { verifyFile, verifyManifest, hashUrl, main } from './verify-manifest.mjs';

const sha = (buf) => createHash('sha256').update(buf).digest('hex');

function manifestFor(files) {
  return {
    schemaVersion: 1,
    product: 'client',
    version: '1.0.0',
    minecraftVersion: '1.21.11',
    fabricVersion: '0.19.5',
    fabricApiVersion: '0.141.6+1.21.11',
    javaVersion: 21,
    releaseDate: '2026-10-04',
    channel: 'stable',
    files,
    changelog: 'website/content/changelog/client-1.0.0.md',
  };
}

/** A fetch stand-in serving in-memory bodies. */
function fakeFetch(routes) {
  return async (url) => {
    const body = routes[url];
    if (body === undefined) return new Response('nope', { status: 404, statusText: 'Not Found' });
    return new Response(body, { status: 200 });
  };
}

describe('verifyFile()', () => {
  const dir = mkdtempSync(join(tmpdir(), 'vanta-verify-'));
  const jar = Buffer.from('jar contents');
  writeFileSync(join(dir, 'vanta-client-1.0.0.jar'), jar);
  const good = { name: 'vanta-client-1.0.0.jar', downloadUrl: 'https://example.invalid/vanta-client-1.0.0.jar', size: jar.length, sha256: sha(jar) };

  test('local directory: ok, mismatch, missing', async () => {
    assert.equal((await verifyFile(good, { localDir: dir })).status, 'ok');
    const wrongSize = await verifyFile({ ...good, size: 1 }, { localDir: dir });
    assert.equal(wrongSize.status, 'mismatch');
    assert.match(wrongSize.message, /expected 1 bytes/);
    const wrongSha = await verifyFile({ ...good, sha256: 'f'.repeat(64) }, { localDir: dir });
    assert.equal(wrongSha.status, 'mismatch');
    const missing = await verifyFile({ ...good, name: 'other.jar' }, { localDir: dir });
    assert.equal(missing.status, 'missing');
    const noDigest = await verifyFile({ ...good, sha256: '', size: 0 }, { localDir: dir });
    assert.equal(noDigest.status, 'mismatch');
    assert.match(noDigest.message, /no size\/sha256/);
  });

  test('download path streams through SHA-256 and follows the manifest URL', async () => {
    const fetchImpl = fakeFetch({ [good.downloadUrl]: jar });
    assert.equal((await verifyFile(good, { fetchImpl })).status, 'ok');
    const tampered = fakeFetch({ [good.downloadUrl]: Buffer.from('jar contentz') });
    assert.equal((await verifyFile(good, { fetchImpl: tampered })).status, 'mismatch');
    const notFound = await verifyFile({ ...good, downloadUrl: 'https://example.invalid/missing.jar' }, { fetchImpl });
    assert.equal(notFound.status, 'error');
    assert.match(notFound.message, /HTTP 404/);
    const direct = await hashUrl(good.downloadUrl, { fetchImpl });
    assert.deepEqual(direct, { size: jar.length, sha256: sha(jar) });
  });

  test('unpublished entries are reported, not downloaded', async () => {
    let called = false;
    const report = await verifyFile({ ...good, downloadUrl: '' }, { fetchImpl: async () => { called = true; return new Response(''); } });
    assert.equal(report.status, 'unpublished');
    assert.equal(called, false);
  });
});

describe('verifyManifest()', () => {
  const dir = mkdtempSync(join(tmpdir(), 'vanta-verify-manifest-'));
  const jar = Buffer.from('client jar');
  const msi = Buffer.from('launcher msi');
  mkdirSync(join(dir, 'dist'));
  writeFileSync(join(dir, 'dist', 'vanta-client-1.0.0.jar'), jar);
  writeFileSync(join(dir, 'dist', 'VANTA-Launcher-1.0.0.msi'), msi);

  test('passes for a consistent manifest and fails on a single mismatch', async () => {
    const manifest = manifestFor([
      { name: 'vanta-client-1.0.0.jar', downloadUrl: 'https://example.invalid/a.jar', size: jar.length, sha256: sha(jar) },
      { name: 'VANTA-Launcher-1.0.0.msi', downloadUrl: 'https://example.invalid/b.msi', size: msi.length, sha256: sha(msi) },
    ]);
    const ok = await verifyManifest(manifest, { localDir: join(dir, 'dist') });
    assert.equal(ok.ok, true);
    assert.deepEqual(ok.reports.map((r) => r.status), ['ok', 'ok']);
    manifest.files[1].sha256 = '0'.repeat(64);
    const bad = await verifyManifest(manifest, { localDir: join(dir, 'dist') });
    assert.equal(bad.ok, false);
    assert.deepEqual(bad.reports.map((r) => r.status), ['ok', 'mismatch']);
  });

  test('unpublished entries fail unless --allow-unpublished', async () => {
    const manifest = manifestFor([{ name: 'vanta-client-1.0.0.jar', downloadUrl: '', size: 0, sha256: '' }]);
    assert.equal((await verifyManifest(manifest, {})).ok, false);
    assert.equal((await verifyManifest(manifest, { allowUnpublished: true })).ok, true);
  });

  test('rejects manifests that do not match the schema before touching the network', async () => {
    const manifest = manifestFor([{ name: 'x.jar', downloadUrl: 'ftp://nope', size: 1, sha256: 'a'.repeat(64) }]);
    await assert.rejects(verifyManifest(manifest, {}), /does not match the schema/);
  });

  test('CLI prints a report and uses exit codes 0/1/2', async () => {
    const manifest = manifestFor([{ name: 'vanta-client-1.0.0.jar', downloadUrl: 'https://example.invalid/a.jar', size: jar.length, sha256: sha(jar) }]);
    const path = join(dir, 'client-1.0.0.json');
    writeFileSync(path, JSON.stringify(manifest));
    const out = [];
    const err = [];
    assert.equal(await main([path, '--local', join(dir, 'dist')], (m) => out.push(m), (m) => err.push(m)), 0);
    assert.match(out.join('\n'), /OK\s+vanta-client-1\.0\.0\.jar/);
    assert.match(out.join('\n'), /All files verified/);
    assert.equal(await main([path, '--local', join(dir)], (m) => out.push(m), (m) => err.push(m)), 1, 'file not in that dir');
    assert.match(err.join('\n'), /MISSING/);
    assert.equal(await main([], () => {}, (m) => err.push(m)), 2);
    assert.equal(await main([path, '--timeout', 'soon'], () => {}, (m) => err.push(m)), 2);
    assert.equal(await main([join(dir, 'missing.json')], () => {}, (m) => err.push(m)), 2);
    assert.equal(await main(['--help'], (m) => out.push(m), () => {}), 0);
  });
});
