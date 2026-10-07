import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, mkdirSync, readFileSync, existsSync, cpSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { planBump, applyChanges, replaceOnce, newestManifest, nextManifest, main, previousVersion, staleMentions, mentionReport } from './bump-version.mjs';
import { REPO_ROOT } from './lib/repo.mjs';

/** Replaces the one match of `pattern` (which must exist) with `value`. */
function pin(text, pattern, value) {
  assert.match(text, pattern);
  return text.replace(pattern, value);
}

/**
 * Builds a miniature repository with the files the bump touches, pinned to the 1.0.0 baseline (the real files with
 * every product version set back to 1.0.0 and only the 1.0.0 manifests), so these tests keep passing after the
 * repository itself has been bumped.
 */
function makeRepo() {
  const root = mkdtempSync(join(tmpdir(), 'vanta-bump-'));
  mkdirSync(join(root, 'client'), { recursive: true });
  mkdirSync(join(root, 'launcher'), { recursive: true });
  mkdirSync(join(root, 'website'), { recursive: true });
  mkdirSync(join(root, 'core/src/main/java/dev/vanta/core'), { recursive: true });
  mkdirSync(join(root, 'shared/releases'), { recursive: true });
  const real = (path) => readFileSync(join(REPO_ROOT, path), 'utf8');
  writeFileSync(join(root, 'client/gradle.properties'), pin(real('client/gradle.properties'), /^mod_version=.*$/m, 'mod_version=1.0.0'));
  writeFileSync(join(root, 'launcher/gradle.properties'), pin(real('launcher/gradle.properties'), /^launcher_version=.*$/m, 'launcher_version=1.0.0'));
  writeFileSync(join(root, 'core/src/main/java/dev/vanta/core/VantaVersion.java'),
    pin(real('core/src/main/java/dev/vanta/core/VantaVersion.java'), /CLIENT = "[^"]*";/, 'CLIENT = "1.0.0";'));
  writeFileSync(join(root, 'website/package.json'), JSON.stringify({ name: 'vanta-website', version: '1.0.0', private: true }, null, 2) + '\n');
  writeFileSync(join(root, 'website/package-lock.json'), JSON.stringify({ name: 'vanta-website', version: '1.0.0', lockfileVersion: 3, packages: { '': { name: 'vanta-website', version: '1.0.0' } } }, null, 2) + '\n');
  for (const name of ['client-1.0.0.json', 'launcher-1.0.0.json']) {
    cpSync(join(REPO_ROOT, 'shared/releases', name), join(root, 'shared/releases', name));
  }
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
    assert.deepEqual(manifest.files, [
      { name: 'vanta-client-1.0.1.jar', downloadUrl: '', size: 0, sha256: '' },
      { name: 'vanta-client-1.0.1-mods.zip', downloadUrl: '', size: 0, sha256: '' },
      { name: 'fabric-api-0.141.6+1.21.11.jar', downloadUrl: '', size: 0, sha256: '' },
    ]);
    assert.equal(manifest.changelog, 'website/content/changelog/client-1.0.1.md');
    assert.equal(manifest.releaseDate, '2026-10-04');
    assert.ok(existsSync(join(root, 'shared/releases/client-1.0.0.json')), 'old manifest kept');
  });

  test('launcher: gradle.properties and a manifest listing every release file of the new version', () => {
    const root = makeRepo();
    const changes = planBump({ root, product: 'launcher', to: '1.1.0', date: '2026-10-04' });
    applyChanges(changes);
    assert.match(readFileSync(join(root, 'launcher/gradle.properties'), 'utf8'), /^launcher_version=1\.1\.0$/m);
    const manifest = JSON.parse(readFileSync(join(root, 'shared/releases/launcher-1.1.0.json'), 'utf8'));
    assert.deepEqual(manifest.files.map((f) => f.name), [
      'VANTA-Launcher-1.1.0.msi',
      'VANTA-Launcher-1.1.0.exe',
      'VANTA-Launcher-1.1.0-windows-portable.zip',
      'vanta-launcher-1.1.0-windows-all.jar',
      'VANTA-Launcher-1.1.0-linux-x64.tar.gz',
      'vanta-launcher-1.1.0-linux-all.jar',
      'vanta-launcher-1.1.0-macos-aarch64-all.jar',
    ]);
    assert.ok(manifest.files.every((f) => f.downloadUrl === '' && f.size === 0 && f.sha256 === ''));
  });

  test('the new manifest takes the toolchain from client/gradle.properties and fresh asset names', () => {
    const root = makeRepo();
    writeFileSync(join(root, 'client/gradle.properties'),
      readFileSync(join(root, 'client/gradle.properties'), 'utf8').replace(/^fabric_api_version=.*$/m, 'fabric_api_version=0.142.0+1.21.11'));
    applyChanges(planBump({ root, product: 'client', to: '1.0.1', date: '2026-10-04' }));
    const manifest = JSON.parse(readFileSync(join(root, 'shared/releases/client-1.0.1.json'), 'utf8'));
    assert.equal(manifest.fabricApiVersion, '0.142.0+1.21.11');
    assert.equal(manifest.files[2].name, 'fabric-api-0.142.0+1.21.11.jar');
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

describe('stale version mentions', () => {
  /** The fixture repo plus prose that names the 1.0.0 client the way README.md, docs/ and the website source do. */
  function makeRepoWithProse() {
    const root = makeRepo();
    const write = (rel, text) => {
      mkdirSync(join(root, rel, '..'), { recursive: true });
      writeFileSync(join(root, rel), text);
    };
    write('README.md', [
      '# VANTA', 'The latest releases are VANTA Client 1.0.0 and VANTA Launcher 1.0.0.', '',
      '| client-v1.0.0 | 2026-01-01 |', 'Unpack vanta-client-1.0.0-mods.zip and copy the jars.',
      'Not a mention: 11.0.0, 1.0.0.5, 2.1.0.0 and 1.0.01.', 'Fabric API 0.141.6+1.21.11 stays.', '',
    ].join('\n'));
    write('RELEASE.md', 'Tag client-v1.0.0 after the bump.\n');
    write('client/README.md', 'Builds vanta-client-1.0.0.jar\n');
    write('launcher/README.md', 'Nothing versioned here.\n');
    write('shared/releases/README.md', 'client-1.0.0.json is the newest manifest.\n');
    write('docs/installation.md', '# Install\n\nDownload vanta-client-1.0.0.jar from the release.\nThen vanta-client-1.0.0-mods.zip.\n');
    write('docs/faq.md', '# FAQ\n\nNo version here.\n');
    write('docs/CHANGELOG.md', '## 1.0.0\n');
    write('CHANGELOG.md', '## client 1.0.0 - 2026-01-01\n');
    write('website/content/changelog/client-1.0.0.md', 'version: 1.0.0\n');
    write('website/content/news/2026-01-01-client-1.0.0.md', 'VANTA Client 1.0.0 is out.\n');
    write('website/src/pages/DownloadPage.tsx', 'const zip = "vanta-client-1.0.0-mods.zip";\n');
    write('website/src/lib/releases.test.ts', "expect(latest.version).toBe('1.0.0');\n");
    mkdirSync(join(root, 'website/src/assets'), { recursive: true });
    writeFileSync(join(root, 'website/src/assets/logo.png'), Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0]), Buffer.from('1.0.0')]));
    return root;
  }

  test('previousVersion() reads the version the bump replaces', () => {
    const root = makeRepo();
    assert.equal(previousVersion({ root, product: 'client' }), '1.0.0');
    assert.equal(previousVersion({ root, product: 'launcher' }), '1.0.0');
    assert.equal(previousVersion({ root, product: 'website' }), '1.0.0');
    assert.equal(previousVersion({ root: mkdtempSync(join(tmpdir(), 'vanta-bump-empty-')), product: 'client' }), null);
  });

  test('staleMentions() lists every prose line naming the old version and skips history, binaries and other numbers', () => {
    const root = makeRepoWithProse();
    const mentions = staleMentions({ root, from: '1.0.0' });
    assert.deepEqual(mentions.map((m) => `${m.path}:${m.line}`), [
      'README.md:2', 'README.md:4', 'README.md:5',
      'RELEASE.md:1',
      'client/README.md:1',
      'shared/releases/README.md:1',
      'docs/installation.md:3', 'docs/installation.md:4',
      'website/src/lib/releases.test.ts:1',
      'website/src/pages/DownloadPage.tsx:1',
    ]);
    assert.equal(mentions[0].text, 'The latest releases are VANTA Client 1.0.0 and VANTA Launcher 1.0.0.');
    assert.ok(mentions.every((m) => !/CHANGELOG|website\/content|releases\/client-1\.0\.0\.json|\.png$/.test(m.path)));
    assert.deepEqual(staleMentions({ root, from: '9.9.9' }), []);
    assert.deepEqual(staleMentions({ root, from: '' }), []);
    assert.deepEqual(staleMentions({ root: mkdtempSync(join(tmpdir(), 'vanta-bump-empty-')), from: '1.0.0' }), []);
  });

  test('mentionReport() is a checklist, or one line when nothing is left', () => {
    const report = mentionReport('client', '1.0.0', [{ path: 'README.md', line: 2, text: 'VANTA Client 1.0.0' }]);
    assert.match(report[0], /^Files that still mention 1\.0\.0 \(the client version before this bump\)/);
    assert.equal(report.at(-1), '  [ ] README.md:2  VANTA Client 1.0.0');
    assert.deepEqual(mentionReport('launcher', '1.0.0', []), ['No file under README.md, RELEASE.md, launcher/README.md, client/README.md, shared/releases/README.md, docs/ and website/src still mentions 1.0.0 (the launcher version before this bump).']);
  });

  test('the CLI prints the report after the checklist for client and launcher bumps, never editing the prose', () => {
    const root = makeRepoWithProse();
    const out = [];
    assert.equal(main(['--product', 'client', '--to', '1.1.0', '--root', root], (m) => out.push(m), () => {}), 0);
    const text = out.join('\n');
    assert.ok(text.indexOf('Next steps:') < text.indexOf('Files that still mention 1.0.0 (the client version before this bump)'));
    assert.match(text, /names the previous client version \(listed below\)/);
    assert.match(text, /^ {2}\[ \] README\.md:2 {2}The latest releases are VANTA Client 1\.0\.0 and VANTA Launcher 1\.0\.0\.$/m);
    assert.match(text, /^ {2}\[ \] docs\/installation\.md:3 {2}Download vanta-client-1\.0\.0\.jar from the release\.$/m);
    assert.match(text, /^ {2}\[ \] website\/src\/pages\/DownloadPage\.tsx:1 /m);
    // History, binaries and the old manifest never appear as checklist entries (the "Next steps" line that says to
    // write the changelog is the only place these names belong).
    const entries = out.filter((line) => line.startsWith('  [ ] '));
    assert.equal(entries.length, 10);
    for (const entry of entries) assert.doesNotMatch(entry, /CHANGELOG\.md|website\/content|logo\.png|shared\/releases\/client-1\.0\.0\.json/);
    assert.match(readFileSync(join(root, 'README.md'), 'utf8'), /VANTA Client 1\.0\.0/, 'prose untouched');
    // A dry run reports too (the previous version is read before anything would be written).
    const dry = [];
    assert.equal(main(['--product', 'launcher', '--to', '1.1.0', '--dry-run', '--root', root], (m) => dry.push(m), () => {}), 0);
    assert.match(dry.join('\n'), /Files that still mention 1\.0\.0 \(the launcher version before this bump\)/);
    // The website bump has no prose of its own to check.
    const site = [];
    assert.equal(main(['--product', 'website', '--to', '1.0.1', '--root', root], (m) => site.push(m), () => {}), 0);
    assert.doesNotMatch(site.join('\n'), /still mention/);
  });
});
