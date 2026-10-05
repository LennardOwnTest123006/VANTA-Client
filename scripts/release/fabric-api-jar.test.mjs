import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { locateFabricApiJar, main } from './fabric-api-jar.mjs';
import { buildZip } from './lib/zip-writer.mjs';

const VERSION = '0.141.6+1.21.11';

function fabricApiJar({ id = 'fabric-api', version = VERSION, license = 'Apache-2.0', withLicense = true } = {}) {
  const entries = [
    { name: 'fabric.mod.json', data: JSON.stringify({ schemaVersion: 1, id, version, license }, null, 2), deflate: true },
    { name: 'META-INF/jars/fabric-api-base.jar', data: 'nested' },
  ];
  if (withLicense) entries.push({ name: 'LICENSE-fabric-api', data: 'Apache License 2.0' });
  return buildZip(entries);
}

/** Places `bytes` where Gradle would cache it; `recordedSha1` defaults to the real SHA-1. */
function cache(home, bytes, { recordedSha1, group = 'net.fabricmc.fabric-api' } = {}) {
  const sha1 = recordedSha1 ?? createHash('sha1').update(bytes).digest('hex');
  const dir = join(home, 'caches', 'modules-2', 'files-2.1', group, 'fabric-api', VERSION, sha1);
  mkdirSync(dir, { recursive: true });
  const path = join(dir, `fabric-api-${VERSION}.jar`);
  writeFileSync(path, bytes);
  return path;
}

const home = () => mkdtempSync(join(tmpdir(), 'vanta-gradle-home-'));

describe('locateFabricApiJar()', () => {
  test('finds the single cached jar and verifies SHA-1, metadata and license', () => {
    const h = home();
    const bytes = fabricApiJar();
    const path = cache(h, bytes);
    // Unrelated files with similar names are ignored.
    mkdirSync(join(h, 'caches', 'modules-2', 'other'), { recursive: true });
    writeFileSync(join(h, 'caches', 'modules-2', 'other', `fabric-api-${VERSION}-sources.jar`), 'src');
    const jar = locateFabricApiJar({ version: VERSION, gradleUserHome: h });
    assert.equal(jar.path, path);
    assert.equal(jar.size, bytes.length);
    assert.equal(jar.sha1, createHash('sha1').update(bytes).digest('hex'));
    assert.equal(jar.sha256, createHash('sha256').update(bytes).digest('hex'));
  });

  test('fails when the jar is missing or cached twice', () => {
    const h = home();
    assert.throws(() => locateFabricApiJar({ version: VERSION, gradleUserHome: h }), /is not in .*build the client first/);
    cache(h, fabricApiJar());
    cache(h, fabricApiJar({ license: 'Apache-2.0' }), { group: 'net.fabricmc.copy' });
    assert.throws(() => locateFabricApiJar({ version: VERSION, gradleUserHome: h }), /expected exactly one .* found 2/);
  });

  test('fails when the bytes do not match the SHA-1 Gradle recorded', () => {
    const h = home();
    cache(h, fabricApiJar(), { recordedSha1: '0'.repeat(40) });
    assert.throws(() => locateFabricApiJar({ version: VERSION, gradleUserHome: h }), /does not match the checksum Gradle recorded/);
  });

  test('fails for a jar outside a <sha1> directory', () => {
    const h = home();
    const dir = join(h, 'caches', 'modules-2', 'loose');
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, `fabric-api-${VERSION}.jar`), fabricApiJar());
    assert.throws(() => locateFabricApiJar({ version: VERSION, gradleUserHome: h }), /not inside a Gradle files-2\.1 <sha1> directory/);
  });

  test('checks fabric.mod.json and LICENSE-fabric-api', () => {
    for (const [options, message] of [
      [{ id: 'not-fabric' }, /id is "not-fabric"/],
      [{ version: '0.1.0+1.21.11' }, /version is "0\.1\.0\+1\.21\.11"/],
      [{ license: 'MIT' }, /license is "MIT"/],
      [{ withLicense: false }, /does not contain LICENSE-fabric-api/],
    ]) {
      const h = home();
      cache(h, fabricApiJar(options));
      assert.throws(() => locateFabricApiJar({ version: VERSION, gradleUserHome: h }), message);
    }
  });
});

describe('CLI', () => {
  test('copies the verified jar unchanged and prints its path last', () => {
    const h = home();
    const bytes = fabricApiJar();
    cache(h, bytes);
    const dest = join(mkdtempSync(join(tmpdir(), 'vanta-fapi-dest-')), 'dist');
    const out = [];
    const err = [];
    assert.equal(main(['--version', VERSION, '--gradle-user-home', h, '--copy-to', dest], (l) => out.push(l), (l) => err.push(l)), 0);
    const copied = join(dest, `fabric-api-${VERSION}.jar`);
    assert.equal(out.at(-1), copied);
    assert.ok(existsSync(copied));
    assert.deepEqual(readFileSync(copied), bytes);
    assert.match(out[0], /matches the Gradle cache record/);
  });

  test('defaults: version from client/gradle.properties, Gradle home from GRADLE_USER_HOME', () => {
    const h = home();
    cache(h, fabricApiJar());
    const out = [];
    assert.equal(main([], (l) => out.push(l), () => {}, { GRADLE_USER_HOME: h }), 0);
    assert.match(out.at(-1), /fabric-api-0\.141\.6\+1\.21\.11\.jar$/);
  });

  test('exit codes 1 (not found) and 2 (usage)', () => {
    const h = home();
    const err = [];
    assert.equal(main(['--gradle-user-home', h], () => {}, (l) => err.push(l)), 1);
    assert.match(err.join('\n'), /is not in/);
    assert.equal(main(['stray'], () => {}, () => {}), 2);
    assert.equal(main(['--bogus'], () => {}, () => {}), 2);
  });
});
