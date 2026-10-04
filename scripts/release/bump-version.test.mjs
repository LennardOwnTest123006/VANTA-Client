import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, mkdirSync, readFileSync, existsSync, cpSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { planBump, applyChanges, replaceOnce, newestManifest, nextManifest, main } from './bump-version.mjs';
import { REPO_ROOT } from './lib/repo.mjs';

/** Builds a miniature repository with the files the bump touches. */
function makeRepo() {
  const root = mkdtempSync(join(tmpdir(), 'vanta-bump-'));
  mkdirSync(join(root, 'client'), { recursive: true });
  mkdirSync(join(root, 'launcher'), { recursive: true });
  mkdirSync(join(root, 'website'), { recursive: true });
  mkdirSync(join(root, 'core/src/main/java/dev/vanta/core'), { recursive: true });
  mkdirSync(join(root, 'shared/releases'), { recursive: true });
  writeFileSync(join(root, 'client/gradle.properties'), readFileSync(join(REPO_ROOT, 'client/gradle.properties')));
  writeFileSync(join(root, 'launcher/gradle.properties'), readFileSync(join(REPO_ROOT, 'launcher/gradle.properties')));
  writeFileSync(join(root, 'core/src/main/java/dev/vanta/core/VantaVersion.java'), readFileSync(join(REPO_ROOT, 'core/src/main/java/dev/vanta/core/VantaVersion.java')));
  writeFileSync(join(root, 'website/package.json'), JSON.stringify({ name: 'vanta-website', version: '1.0.0', private: true }, null, 2) + '\n');
  writeFileSync(join(root, 'website/package-lock.json'), JSON.stringify({ name: 'vanta-website', version: '1.0.0', lockfileVersion: 3, packages: { '': { name: 'vanta-website', version: '1.0.0' } } }, null, 2) + '\n');
  cpSync(join(REPO_ROOT, 'shared/releases'), join(root, 'shared/releases'), { recursive: true });
  return root;
}

describe('helpers', () => {
  test('replaceOnce demands exactly one match', () => {
    assert.equal(replaceOnce('a=1\nb=2\n', /^a=.*$/m, 'a=9', 'a'), 'a=9\nb=2\n');
    assert.throws(() => replaceOnce('b=2\n', /^a=.*$/m, 'a=9', 'the a key'), /could not find the a key/);
    assert.throws(() => replaceOnce('a=1\na=2\n', /^a=.*$/m, 'a=9', 'a'), /found 2 occurrences/);
  });

  test('newestManifest picks the highest SemVer and ignores other files', () => {
    const dir = mkdtempSync(join(tmpdir(), 'vanta-newest-'));
    for (const v of ['1.0.0', '1.2.0', '1.10.0', '2.0.0-beta.1']) writeFileSync(join(dir, `client-${v}.json`), '{}');
    writeFileSync(join(dir, 'launcher-9.9.9.json'), '{}');
    writeFileSync(join(dir, 'client-latest.json'), '{}');
    writeFileSync(join(dir, 'README.md'), '');
    assert.equal(newestManifest(dir, 'client').version, '2.0.0-beta.1');
    assert.equal(newestManifest(dir, 'launcher').version, '9.9.9');
    assert.equal(newestManifest(dir, 'website'), null);
    assert.equal(newestManifest(join(dir, 'missing'), 'client'), null);
  });

  test('nextManifest renames files and clears publish data', () => {
    const template = { product: 'launcher', version: '1.0.0', channel: 'stable', files: [{ name: 'VANTA-Launcher-1.0.0.msi', downloadUrl: 'https://x/y', size: 5, sha256: 'a'.repeat(64) }], changelog: 'website/content/changelog/launcher-1.0.0.md', releaseDate: '2026-01-01' };
    const next = nextManifest(template, '1.1.0', '2026-10-04');
    assert.equal(next.version, '1.1.0');
    assert.equal(next.releaseDate, '2026-10-04');
    assert.deepEqual(next.files, [{ name: 'VANTA-Launcher-1.1.0.msi', downloadUrl: '', size: 0, sha256: '' }]);
    assert.equal(next.changelog, 'website/content/changelog/launcher-1.1.0.md');
    assert.equal(template.files[0].size, 5, 'template untouched');
  });
});

describe('planBump()', () => {
  test('client: gradle.properties, VantaVersion.java and a new unpublished manifest', () => {
    const root = makeRepo();
    const changes = planBump({ root, product: 'client', to: '1.0.1', date: '2026-10-04' });
    assert.deepEqual(changes.map((c) => [c.kind, c.path.slice(root.length + 1)]), [
      ['edit', 'client/gradle.properties'],
      ['edit', 'core/src/main/java/dev/vanta/core/VantaVersion.java'],
      ['create', 'shared/releases/client-1.0.1.json'],
    ]);
    applyChanges(changes);
    assert.match(readFileSync(join(root, 'client/gradle.properties'), 'utf8'), /^mod_version=1\.0\.1$/m);
    assert.match(readFileSync(join(root, 'client/gradle.properties'), 'utf8'), /^minecraft_version=1\.21\.11$/m, 'toolchain untouched');
    const java = readFileSync(join(root, 'core/src/main/java/dev/vanta/core/VantaVersion.java'), 'utf8');
    assert.match(java, /CLIENT = "1\.0\.1";/);
    assert.match(java, /MINECRAFT = "1\.21\.11";/);
    const manifest = JSON.parse(readFileSync(join(root, 'shared/releases/client-1.0.1.json'), 'utf8'));
    assert.equal(manifest.version, '1.0.1');
    assert.equal(manifest.product, 'client');
    assert.equal(manifest.minecraftVersion, '1.21.11');
    assert.deepEqual(manifest.files, [{ name: 'vanta-client-1.0.1.jar', downloadUrl: '', size: 0, sha256: '' }]);
    assert.equal(manifest.changelog, 'website/content/changelog/client-1.0.1.md');
    assert.equal(manifest.releaseDate, '2026-10-04');
    assert.ok(existsSync(join(root, 'shared/releases/client-1.0.0.json')), 'old manifest kept');
  });

  test('launcher: gradle.properties and manifest with all three file names renamed', () => {
    const root = makeRepo();
    const changes = planBump({ root, product: 'launcher', to: '1.1.0', date: '2026-10-04' });
    applyChanges(changes);
    assert.match(readFileSync(join(root, 'launcher/gradle.properties'), 'utf8'), /^launcher_version=1\.1\.0$/m);
    const manifest = JSON.parse(readFileSync(join(root, 'shared/releases/launcher-1.1.0.json'), 'utf8'));
    assert.deepEqual(manifest.files.map((f) => f.name), ['VANTA-Launcher-1.1.0.msi', 'VANTA-Launcher-1.1.0.exe', 'vanta-launcher-1.1.0-all.jar']);
  });

  test('website: package.json and package-lock.json only', () => {
    const root = makeRepo();
    const changes = planBump({ root, product: 'website', to: '1.0.1' });
    assert.deepEqual(changes.map((c) => c.path.slice(root.length + 1)), ['website/package.json', 'website/package-lock.json']);
    applyChanges(changes);
    assert.equal(JSON.parse(readFileSync(join(root, 'website/package.json'), 'utf8')).version, '1.0.1');
    const lock = JSON.parse(readFileSync(join(root, 'website/package-lock.json'), 'utf8'));
    assert.equal(lock.version, '1.0.1');
    assert.equal(lock.packages[''].version, '1.0.1');
    assert.equal(existsSync(join(root, 'shared/releases/website-1.0.1.json')), false);
  });

  test('refuses downgrades, duplicates, bad input and missing files', () => {
    const root = makeRepo();
    assert.throws(() => planBump({ root, product: 'client', to: '1.0.0' }), /not newer/);
    assert.throws(() => planBump({ root, product: 'client', to: '0.9.0' }), /not newer/);
    assert.throws(() => planBump({ root, product: 'client', to: 'one' }), /SemVer/);
    assert.throws(() => planBump({ root, product: 'core', to: '1.0.1' }), /--product/);
    writeFileSync(join(root, 'shared/releases/client-1.0.5.json'), readFileSync(join(root, 'shared/releases/client-1.0.0.json')));
    assert.throws(() => planBump({ root, product: 'client', to: '1.0.5' }), /not newer/);
    const bare = mkdtempSync(join(tmpdir(), 'vanta-bump-bare-'));
    assert.throws(() => planBump({ root: bare, product: 'launcher', to: '1.0.1' }), /not found/);
  });

  test('CLI: dry run writes nothing, real run writes and prints next steps', () => {
    const root = makeRepo();
    const out = [];
    const err = [];
    assert.equal(main(['--product', 'client', '--to', '1.2.0', '--dry-run', '--root', root], (m) => out.push(m), (m) => err.push(m)), 0);
    assert.equal(existsSync(join(root, 'shared/releases/client-1.2.0.json')), false);
    assert.match(out.join('\n'), /would create\s+shared\/releases\/client-1\.2\.0\.json/);
    assert.equal(main(['--product', 'client', '--to', '1.2.0', '--root', root], (m) => out.push(m), (m) => err.push(m)), 0);
    assert.ok(existsSync(join(root, 'shared/releases/client-1.2.0.json')));
    assert.match(out.join('\n'), /git tag client-v1\.2\.0/);
    assert.equal(main(['--product', 'client'], () => {}, (m) => err.push(m)), 2);
    assert.equal(main(['--product', 'client', '--to', '1.2.0', '--root', root], () => {}, (m) => err.push(m)), 1, 'already exists / not newer');
    assert.equal(main(['--help'], (m) => out.push(m), () => {}), 0);
  });
});
