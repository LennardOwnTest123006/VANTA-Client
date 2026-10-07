import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { REPO_ROOT, readToolchain } from './lib/repo.mjs';
import { openZip } from './lib/zip.mjs';
import { isSafeFilename } from './performance-pack.mjs';

const SCRIPT = join(REPO_ROOT, 'scripts', 'release', 'build-mods-bundle.sh');
const TOOLCHAIN = readToolchain(REPO_ROOT);
const FAPI = `fabric-api-${TOOLCHAIN.fabricApiVersion}.jar`;
const PACK_JARS = ['sodium-fabric-9.9.9-test.jar', 'iris-fabric-4.4.4-test.jar'];

const has = (cmd) => spawnSync('bash', ['-c', `command -v ${cmd}`], { encoding: 'utf8' }).status === 0;
const tools = process.platform !== 'win32' && has('zip') && has('unzip') && has('node') && (has('sha256sum') || has('shasum'));
const skip = tools ? false : 'needs bash, node, zip, unzip and sha256sum/shasum';

const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
const sha512 = (bytes) => createHash('sha512').update(bytes).digest('hex');

/** The notices as performance-pack.mjs writes them: one "File:     mods/<jar>" section per third-party jar. */
function notices(files) {
  return `Third-party notices (fake)\n${files.map((f) => `${'-'.repeat(78)}\nSome mod 1.0\nFile:     mods/${f}\nLicence:  X\n\nlicence text\n`).join('')}`;
}

/** A pack dir as performance-pack.mjs writes it: two fake jars, the JSON with their SHA-512, the two text files. */
function packDir(dir, { items = PACK_JARS, excluded = true, breakSha = false, noticeFiles = [...items, FAPI] } = {}) {
  const pack = join(dir, 'pack');
  mkdirSync(join(pack, 'mods'), { recursive: true });
  const jars = {};
  const json = { schemaVersion: 1, gameVersion: TOOLCHAIN.minecraftVersion, resolvedAt: '2026-10-06T12:00:00Z', items: [], excluded: [] };
  for (const [index, file] of items.entries()) {
    const bytes = Buffer.from(`fake pack jar ${file}`);
    jars[file] = bytes;
    writeFileSync(join(pack, 'mods', file), bytes);
    const title = file.startsWith('sodium') ? 'Sodium' : 'Iris Shaders';
    json.items.push({
      slug: file.split('-')[0], title, projectId: `P${index}`, versionId: `V${index}`, versionNumber: file.replace(/^[a-z]+-fabric-|\.jar$/g, ''),
      file, size: bytes.length, sha512: breakSha && index === 0 ? '0'.repeat(128) : sha512(bytes),
      license: { id: index === 0 ? 'LicenseRef-Polyform-Shield-1.0.0' : 'LGPL-3.0-only', name: null, url: null }, sourceUrl: null,
    });
  }
  if (excluded) json.excluded.push({ slug: 'entityculling', title: 'EntityCulling', license: 'LicenseRef-tr7zw-Protective-License', reason: 'licence not in the allow-list' });
  writeFileSync(join(pack, 'performance-pack.json'), `${JSON.stringify(json, null, 2)}\n`);
  writeFileSync(join(pack, 'PERFORMANCE-PACK.txt'), 'Performance pack summary (fake)\n');
  writeFileSync(join(pack, 'THIRD-PARTY-LICENSES.txt'), notices(noticeFiles));
  return { pack, jars, json };
}

function fixture(packOptions) {
  const dir = mkdtempSync(join(tmpdir(), 'vanta-mods-bundle-'));
  mkdirSync(join(dir, 'in'));
  const client = join(dir, 'in', 'vanta-client-1.0.0.jar');
  const fapi = join(dir, 'in', FAPI);
  writeFileSync(client, Buffer.from('fake client jar bytes'));
  writeFileSync(fapi, Buffer.from('fake fabric api jar bytes'));
  const { pack, jars, json } = packDir(dir, packOptions);
  return { dir, client, fapi, pack, jars, json, out: join(dir, 'out') };
}

function run(args) {
  return spawnSync('bash', [SCRIPT, ...args], { encoding: 'utf8' });
}

const args = (f, version = '1.0.0') => ['--version', version, '--client-jar', f.client, '--fabric-api-jar', f.fapi, '--pack-dir', f.pack, '--out', f.out];

/** What the script generates for the three template blocks. */
function packBlocks(json) {
  const list = json.items.map((i) => `   - ${i.file}   (${i.title} ${i.versionNumber}, ${i.license.id})\n`).join('');
  const excluded = json.excluded.length === 0
    ? '   - Every Performance pack mod is in this archive.\n'
    : json.excluded.map((e) => `   - ${e.title} is NOT in this archive: its licence does not allow redistribution\n     (${e.license}). The game offers it with one click under Mods & Shaders\n     (Performance pack card); nothing is downloaded without a click.\n`).join('');
  const slugs = json.items.map((i) => i.slug);
  const titles = json.items.map((i) => i.title);
  const titleList = titles.length === 1 ? titles[0] : `${titles.slice(0, -1).join(', ')} or ${titles.at(-1)}`;
  const notes = `${slugs.includes('sodium') && slugs.includes('iris')
    ? '   - Iris and Sodium belong together: the Iris and Sodium builds in this archive were resolved as a pair\n     (Iris requires Sodium). Always copy or update the two together.\n'
    : ''}   WARNING: your mods folder must not already contain another copy of one of these mods:\n   ${titleList}.\n   Fabric refuses to start when two copies of one mod id are present ("Duplicate mod" error). Delete the\n   older jar before you copy the new one; a leftover ".jar.disabled" file is fine.\n`;
  return { list, excluded, notes };
}

describe('build-mods-bundle.sh', { skip }, () => {
  test('builds the zip with INSTALL.txt, PERFORMANCE-PACK.txt, SHA256SUMS, THIRD-PARTY-LICENSES.txt, every jar under mods/ and performance-pack.json', () => {
    const f = fixture();
    const result = run(args(f));
    assert.equal(result.status, 0, result.stderr);
    const zipPath = join(f.out, 'vanta-client-1.0.0-mods.zip');
    assert.equal(result.stdout.trim().split('\n').at(-1), zipPath);
    const zip = openZip(zipPath);
    assert.deepEqual(zip.entries.map((e) => e.name), [
      'INSTALL.txt', 'PERFORMANCE-PACK.txt', 'SHA256SUMS', 'THIRD-PARTY-LICENSES.txt', 'mods/',
      'mods/vanta-client-1.0.0.jar', `mods/${FAPI}`, ...PACK_JARS.map((j) => `mods/${j}`), 'performance-pack.json',
    ]);
    assert.deepEqual(zip.read('mods/vanta-client-1.0.0.jar'), readFileSync(f.client));
    assert.deepEqual(zip.read(`mods/${FAPI}`), readFileSync(f.fapi));
    for (const jar of PACK_JARS) assert.deepEqual(zip.read(`mods/${jar}`), f.jars[jar], jar);
    assert.equal(zip.read('PERFORMANCE-PACK.txt').toString('utf8'), 'Performance pack summary (fake)\n');
    assert.equal(zip.read('THIRD-PARTY-LICENSES.txt').toString('utf8'), notices([...PACK_JARS, FAPI]));
    assert.deepEqual(JSON.parse(zip.read('performance-pack.json').toString('utf8')), f.json);
    const install = zip.read('INSTALL.txt').toString('utf8');
    assert.doesNotMatch(install, /\{[A-Z_]+\}/, 'all placeholders filled');
    assert.match(install, /^VANTA Client 1\.0\.0 for Minecraft Java Edition 1\.21\.11\n/);
    assert.match(install, /Fabric Loader 0\.19\.5 or newer for 1\.21\.11/);
    assert.match(install, new RegExp(`- ${FAPI.replace(/[.+]/g, '\\$&')} {3}\\(Fabric API, Apache-2\\.0, required dependency\\)`));
    // The pack list and the excluded member come from performance-pack.json at build time.
    assert.match(install, /^ {3}- sodium-fabric-9\.9\.9-test\.jar {3}\(Sodium 9\.9\.9-test, LicenseRef-Polyform-Shield-1\.0\.0\)$/m);
    assert.match(install, /^ {3}- iris-fabric-4\.4\.4-test\.jar {3}\(Iris Shaders 4\.4\.4-test, LGPL-3\.0-only\)$/m);
    assert.match(install, /^ {3}- EntityCulling is NOT in this archive: its licence does not allow redistribution\n {5}\(LicenseRef-tr7zw-Protective-License\)\. The game offers it with one click under Mods & Shaders\n {5}\(Performance pack card\); nothing is downloaded without a click\.$/m);
    // The notes below the list describe this archive: the pairing because both are bundled, the warning naming them.
    assert.match(install, /^ {3}- Iris and Sodium belong together: the Iris and Sodium builds in this archive were resolved as a pair\n {5}\(Iris requires Sodium\)\. Always copy or update the two together\.$/m);
    assert.match(install, /^ {3}WARNING: your mods folder must not already contain another copy of one of these mods:\n {3}Sodium or Iris Shaders\.\n {3}Fabric refuses to start when two copies of one mod id are present \("Duplicate mod" error\)\. Delete the\n {3}older jar before you copy the new one; a leftover "\.jar\.disabled" file is fine\.$/m);
    assert.match(install, /"fabric-loader-1\.21\.11" \(its version id is fabric-loader-0\.19\.5-1\.21\.11\)/);
    assert.match(install, /certutil -hashfile mods\\vanta-client-1\.0\.0\.jar SHA256/);
    assert.match(install, new RegExp(`certutil -hashfile mods\\\\${FAPI.replace(/[.+]/g, '\\$&')} SHA256`));
    assert.match(install, /Get-FileHash mods\\\*\.jar -Algorithm SHA256 \| Format-List Hash, Path\n/);
    for (const line of install.split('\n')) assert.ok(line.length <= 120, `line too long: ${line}`);
    const sums = zip.read('SHA256SUMS').toString('utf8');
    assert.equal(sums, [
      `${sha256(readFileSync(f.client))}  mods/vanta-client-1.0.0.jar`,
      `${sha256(readFileSync(f.fapi))}  mods/${FAPI}`,
      ...PACK_JARS.map((jar) => `${sha256(f.jars[jar])}  mods/${jar}`),
      `${sha256(Buffer.from(install, 'utf8'))}  INSTALL.txt`,
      `${sha256(Buffer.from('Performance pack summary (fake)\n'))}  PERFORMANCE-PACK.txt`,
      `${sha256(Buffer.from(notices([...PACK_JARS, FAPI])))}  THIRD-PARTY-LICENSES.txt`,
      `${sha256(zip.read('performance-pack.json'))}  performance-pack.json`,
      '',
    ].join('\n'));
  });

  test('rendered INSTALL.txt equals the reviewed template text for the pinned toolchain and the pack', () => {
    const template = readFileSync(join(REPO_ROOT, 'scripts', 'release', 'mods-bundle', 'INSTALL.txt'), 'utf8');
    assert.match(template, /\{VERSION\}/);
    assert.match(template, /\{FABRIC_API_VERSION\}/);
    assert.match(template, /^\{PACK_LIST\}\n\{PACK_EXCLUDED\}\n\{PACK_NOTES\}\n/m);
    assert.match(template, /VANTA is a legitimate client: no cheats, no combat automation, no packet manipulation\./);
    for (const excluded of [true, false]) {
      const f = fixture({ excluded });
      const r = run(args(f));
      assert.equal(r.status, 0, r.stderr);
      const rendered = openZip(join(f.out, 'vanta-client-1.0.0-mods.zip')).read('INSTALL.txt').toString('utf8');
      const blocks = packBlocks(f.json);
      const expected = template
        .replaceAll('{VERSION}', '1.0.0')
        .replaceAll('{MINECRAFT_VERSION}', TOOLCHAIN.minecraftVersion)
        .replaceAll('{LOADER_VERSION}', TOOLCHAIN.fabricVersion)
        .replaceAll('{FABRIC_API_VERSION}', TOOLCHAIN.fabricApiVersion)
        .replace('{PACK_LIST}\n', blocks.list)
        .replace('{PACK_EXCLUDED}\n', blocks.excluded)
        .replace('{PACK_NOTES}\n', blocks.notes);
      assert.equal(rendered, expected);
      if (!excluded) assert.match(rendered, /^ {3}- Every Performance pack mod is in this archive\.$/m);
    }
    // Without Iris there is no pairing note, and the warning names only what is in the archive.
    const solo = fixture({ items: [PACK_JARS[0]], noticeFiles: [PACK_JARS[0], FAPI] });
    const r = run(args(solo));
    assert.equal(r.status, 0, r.stderr);
    const rendered = openZip(join(solo.out, 'vanta-client-1.0.0-mods.zip')).read('INSTALL.txt').toString('utf8');
    assert.doesNotMatch(rendered, /Iris and Sodium belong together/);
    assert.match(rendered, /another copy of one of these mods:\n {3}Sodium\.\n/);
    assert.equal(rendered, template
      .replaceAll('{VERSION}', '1.0.0')
      .replaceAll('{MINECRAFT_VERSION}', TOOLCHAIN.minecraftVersion)
      .replaceAll('{LOADER_VERSION}', TOOLCHAIN.fabricVersion)
      .replaceAll('{FABRIC_API_VERSION}', TOOLCHAIN.fabricApiVersion)
      .replace('{PACK_LIST}\n', packBlocks(solo.json).list)
      .replace('{PACK_EXCLUDED}\n', packBlocks(solo.json).excluded)
      .replace('{PACK_NOTES}\n', packBlocks(solo.json).notes));
  });

  test('INSTALL.txt walks through launcher-first install, the Fabric installer per system, every jar check on Windows, the pack warning and the zoom key overlap', () => {
    const template = readFileSync(join(REPO_ROOT, 'scripts', 'release', 'mods-bundle', 'INSTALL.txt'), 'utf8');
    assert.match(template, /^[\x09\x0a\x20-\x7e]*$/, 'plain ASCII so every editor and console shows it correctly');
    for (const line of template.split('\n')) assert.ok(line.length <= 120, `line too long: ${line}`);
    const steps = [...template.matchAll(/^(\d)\. /gm)].map((m) => m[1]);
    assert.deepEqual(steps, ['0', '1', '2', '3']);
    assert.match(template, /^0\. Install the official Minecraft Launcher and start it once/m);
    // Both messages of the Fabric installer when the Minecraft Launcher never ran: it checks the folder first
    // (progress.exception.no.launcher.directory), then the profile file (progress.exception.no.launcher.profile).
    assert.match(template, /"No launcher directory found!" \(no Minecraft folder yet\) or "No launcher profile\.json found!"/);
    assert.match(template, /^1\. Run the Fabric installer[^]*keep "Create profile" checked, click Install\./m);
    assert.match(template, /\(the Fabric installer from fabricmc\.net\)/);
    // Which Fabric installer per system: the Windows .exe needs no Java; the universal .jar (macOS, Linux) does.
    assert.match(template, /^ {3}- Windows: use the Windows installer \(\.exe\) from fabricmc\.net; it needs no separate Java\.$/m);
    assert.match(template, /^ {3}- macOS and Linux: use the universal installer \(\.jar\) and install Java 21 first\./m);
    assert.match(template, /^ +java -jar fabric-installer-<version>\.jar$/m);
    assert.match(template, /Gatekeeper blocks it: System Settings > Privacy & Security > "Open Anyway"/);
    assert.match(template, /installer \(\.jar\) needs Java installed to run, so install Java 21 there first\./);
    // With both profiles files the Fabric installer asks for one launcher and writes only that one.
    assert.match(template, /If both Minecraft Launchers are installed[^]*asks which one to use\. Choose the one you play with; only that one gets the\s+Fabric profile\./);
    // The note sits inside step 1, before step 2.
    const step1 = template.slice(template.indexOf('\n1. '), template.indexOf('\n2. '));
    assert.match(step1, /Windows: use the Windows installer[^]*macOS and Linux: use the universal installer[^]*asks which one to use/);
    // Step 2: every jar, then the pack list, the excluded members and the notes (pairing, duplicate-mod warning), all
    // generated from performance-pack.json at build time so the text never claims something the archive does not hold.
    const step2 = template.slice(template.indexOf('\n2. '), template.indexOf('\n3. '));
    assert.match(step2, /^2\. Copy ALL jars from the "mods" folder of this archive/m);
    assert.match(step2, /Performance pack \(third-party mods under their own licences, see THIRD-PARTY-LICENSES\.txt\):\n\{PACK_LIST\}\n\{PACK_EXCLUDED\}\n\{PACK_NOTES\}$/);
    assert.doesNotMatch(step2, /Sodium|Iris|Lithium|FerriteCore|ImmediatelyFast|EntityCulling/, 'no mod is named in the template itself');
    assert.match(template, /The Performance pack mods are third-party projects\nby their own authors, not part of VANTA; VANTA only ships their unmodified release files\./);
    assert.match(template, /nothing is downloaded without a click/);
    assert.doesNotMatch(template, /https?:\/\//, 'no links in the bundle text');
    // Format-List prints Hash and the full Path of each jar; the default table cuts the path (and file name) off.
    assert.match(template, /^ +Get-FileHash mods\\\*\.jar -Algorithm SHA256 \| Format-List Hash, Path$/m);
    assert.match(template, /certutil -hashfile mods\\vanta-client-\{VERSION\}\.jar SHA256/);
    assert.match(template, /certutil -hashfile mods\\fabric-api-\{FABRIC_API_VERSION\}\.jar SHA256/);
    assert.match(template, /\(repeat for each Performance pack jar in mods\\\)/);
    assert.match(template, /compare the hash of EVERY jar/);
    assert.match(template, /^- Right Shift opens the VANTA settings\. All VANTA key bindings are in the keybind manager \(Settings > Controls\)\.$/m);
    assert.doesNotMatch(template, /Everything is in Settings/, 'Settings > Controls holds the key bindings, not every VANTA setting');
    assert.match(template, /C is hold-to-zoom\. Vanilla Minecraft also uses C for "Save Hotbar Activator" \(Creative mode only\)/);
    assert.match(template, /rebind either one in Options > Controls > Key Binds/);
  });

  test('refuses misnamed jars, a Fabric API version other than the pinned one and missing files', () => {
    const f = fixture();
    const wrongName = join(f.dir, 'in', 'client.jar');
    writeFileSync(wrongName, 'x');
    let r = run(['--version', '1.0.0', '--client-jar', wrongName, '--fabric-api-jar', f.fapi, '--pack-dir', f.pack, '--out', f.out]);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /client jar must be named vanta-client-1\.0\.0\.jar/);
    const otherFapi = join(f.dir, 'in', 'fabric-api-0.1.0+1.21.11.jar');
    writeFileSync(otherFapi, 'x');
    r = run(['--version', '1.0.0', '--client-jar', f.client, '--fabric-api-jar', otherFapi, '--pack-dir', f.pack, '--out', f.out]);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /Fabric API jar must be fabric-api-0\.141\.6\+1\.21\.11\.jar/);
    r = run(['--version', '1.0.0', '--client-jar', join(f.dir, 'missing.jar'), '--fabric-api-jar', f.fapi, '--pack-dir', f.pack, '--out', f.out]);
    assert.equal(r.status, 1);
    const empty = join(f.dir, 'in', 'vanta-client-2.0.0.jar');
    writeFileSync(empty, '');
    r = run(['--version', '2.0.0', '--client-jar', empty, '--fabric-api-jar', f.fapi, '--pack-dir', f.pack, '--out', f.out]);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /is empty/);
  });

  test('refuses a pack dir whose jars do not match performance-pack.json, lack a file or is missing', () => {
    let f = fixture({ breakSha: true });
    let r = run(args(f));
    assert.equal(r.status, 1);
    assert.match(r.stderr, /pack jar 'sodium-fabric-9\.9\.9-test\.jar': SHA-512 [0-9a-f]{128} does not match performance-pack\.json/);
    f = fixture();
    writeFileSync(join(f.pack, 'mods', PACK_JARS[1]), '');
    r = run(args(f));
    assert.equal(r.status, 1);
    assert.match(r.stderr, /pack jar .*iris-fabric-4\.4\.4-test\.jar' is missing or empty/);
    f = fixture();
    r = run(['--version', '1.0.0', '--client-jar', f.client, '--fabric-api-jar', f.fapi, '--pack-dir', join(f.dir, 'nope'), '--out', f.out]);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /pack dir .* does not exist \(run scripts\/release\/performance-pack\.mjs/);
    f = fixture();
    writeFileSync(join(f.pack, 'THIRD-PARTY-LICENSES.txt'), '');
    r = run(args(f));
    assert.equal(r.status, 1);
    assert.match(r.stderr, /pack dir has no THIRD-PARTY-LICENSES\.txt/);
    // A jar without its notice never ships: neither a pack jar nor Fabric API (resolver run without --fabric-api-jar).
    f = fixture({ noticeFiles: [PACK_JARS[0], FAPI] });
    r = run(args(f));
    assert.equal(r.status, 1);
    assert.match(r.stderr, /THIRD-PARTY-LICENSES\.txt has no section for mods\/iris-fabric-4\.4\.4-test\.jar/);
    f = fixture({ noticeFiles: PACK_JARS });
    r = run(args(f));
    assert.equal(r.status, 1);
    assert.match(r.stderr, new RegExp(`THIRD-PARTY-LICENSES\\.txt has no section for mods/${FAPI.replace(/[.+]/g, '\\$&')} \\(run performance-pack\\.mjs with --fabric-api-jar\\)`));
    f = fixture();
    const titled = JSON.parse(readFileSync(join(f.pack, 'performance-pack.json'), 'utf8'));
    titled.items[0].title = 'Sodium\twith a tab';
    writeFileSync(join(f.pack, 'performance-pack.json'), JSON.stringify(titled));
    r = run(args(f));
    assert.equal(r.status, 1);
    assert.match(r.stderr, /title is not plain printable text/);
    f = fixture();
    const json = JSON.parse(readFileSync(join(f.pack, 'performance-pack.json'), 'utf8'));
    json.items[0].file = '../escape.jar';
    writeFileSync(join(f.pack, 'performance-pack.json'), JSON.stringify(json));
    r = run(args(f));
    assert.equal(r.status, 1);
    assert.match(r.stderr, /unsafe pack file '\.\.\/escape\.jar'/);
  });

  test('accepts the pack file names the resolver and the in-game installer accept (spaces, parentheses)', () => {
    // Modrinth names release files freely; isSafeFilename (performance-pack.mjs, core ModrinthFile) lets these through.
    const names = ['sodium-fabric-9.9.9-test.jar', 'Iris Shaders 4.4.4-test.jar', 'ferritecore-8.0.0 (fabric).jar', 'lithium+fabric-1.0.0.jar'];
    for (const name of names) assert.ok(isSafeFilename(name), name);
    const f = fixture({ items: names, noticeFiles: [...names, FAPI] });
    const r = run(args(f));
    assert.equal(r.status, 0, r.stderr);
    const zip = openZip(join(f.out, 'vanta-client-1.0.0-mods.zip'));
    assert.deepEqual(zip.entries.map((e) => e.name), [
      'INSTALL.txt', 'PERFORMANCE-PACK.txt', 'SHA256SUMS', 'THIRD-PARTY-LICENSES.txt', 'mods/',
      'mods/vanta-client-1.0.0.jar', `mods/${FAPI}`, ...names.map((n) => `mods/${n}`), 'performance-pack.json',
    ]);
    for (const name of names) assert.deepEqual(zip.read(`mods/${name}`), f.jars[name], name);
    const sums = zip.read('SHA256SUMS').toString('utf8');
    for (const name of names) assert.ok(sums.includes(`${sha256(f.jars[name])}  mods/${name}\n`), name);
    assert.match(zip.read('INSTALL.txt').toString('utf8'), /^ {3}- ferritecore-8\.0\.0 \(fabric\)\.jar {3}\(/m);
  });

  test('ERE metacharacters, double spaces, an upper-case .JAR and non-ASCII letters in a pack file name survive the notices check, the copy and the zip', () => {
    const names = ['sodium-fabric-9.9.9-test.jar', 'Mod $Name [v1] ^2 {x}.jar', 'lithium  double  space.jar', 'UPPER-1.0.JAR', 'lithium-fabric-ä-日本-0.1.0.jar'];
    for (const name of names) assert.ok(isSafeFilename(name), name);
    const f = fixture({ items: names, noticeFiles: [...names, FAPI] });
    // The fixture derives slug/versionNumber from the file name; those must be plain ASCII (a separate rule), the file name need not.
    const json = JSON.parse(readFileSync(join(f.pack, 'performance-pack.json'), 'utf8'));
    for (const [index, item] of json.items.entries()) { item.slug = `slug${index}`; item.versionNumber = `v${index}`; }
    writeFileSync(join(f.pack, 'performance-pack.json'), JSON.stringify(json));
    const r = run(args(f));
    assert.equal(r.status, 0, r.stderr);
    const zip = openZip(join(f.out, 'vanta-client-1.0.0-mods.zip'));
    assert.deepEqual(zip.entries.map((e) => e.name).filter((n) => n.startsWith('mods/') && n !== 'mods/'), ['mods/vanta-client-1.0.0.jar', `mods/${FAPI}`, ...names.map((n) => `mods/${n}`)]);
    for (const name of names) assert.deepEqual(zip.read(`mods/${name}`), f.jars[name], name);
    const sums = zip.read('SHA256SUMS').toString('utf8');
    for (const name of names) assert.ok(sums.includes(`${sha256(f.jars[name])}  mods/${name}\n`), name);
    // The notices check is anchored on the whole name: a notice for a different name that a metacharacter would match does not count.
    const g = fixture({ items: ['Mod $Name [v1] ^2 {x}.jar'], noticeFiles: ['Mod $Name v1 ^2 x.jar', 'Mod $Name [v1] ^2 {x}.jar.bak', FAPI] });
    const gj = JSON.parse(readFileSync(join(g.pack, 'performance-pack.json'), 'utf8'));
    gj.items[0].slug = 'slug0'; gj.items[0].versionNumber = 'v0';
    writeFileSync(join(g.pack, 'performance-pack.json'), JSON.stringify(gj));
    const missing = run(args(g));
    assert.equal(missing.status, 1);
    assert.match(missing.stderr, /THIRD-PARTY-LICENSES\.txt has no section for mods\/Mod \$Name \[v1\] \^2 \{x\}\.jar/);
  });

  test('rejects every pack file name the resolver rejects, plus a leading dash (a command argument later on)', () => {
    // Path separators and ':', '..', a leading dot, the <>"|?* set, control characters, more than 200 characters
    // (the shared rule), plus a leading dash (an argument to sha512sum/cmp later), a non-jar and an empty name.
    const bad = ['../escape.jar', 'a/b.jar', 'a\\b.jar', 'a:b.jar', '.hidden.jar', 'a..b.jar', 'a?b.jar', 'a<b>.jar', 'a|b.jar', 'a"b.jar',
      'tab\there.jar', 'del\x7f.jar', `${'x'.repeat(197)}.jar`, '-dash.jar', 'not-a-jar.zip', ''];
    for (const name of bad) {
      const f = fixture();
      const json = JSON.parse(readFileSync(join(f.pack, 'performance-pack.json'), 'utf8'));
      json.items[1].file = name;
      writeFileSync(join(f.pack, 'performance-pack.json'), JSON.stringify(json));
      const r = run(args(f));
      assert.equal(r.status, 1, name);
      assert.match(r.stderr, /names an unsafe pack file/, name);
      assert.equal(existsSync(join(f.out, 'vanta-client-1.0.0-mods.zip')), false, name);
    }
  });

  test('usage errors exit 2', () => {
    assert.equal(run([]).status, 2);
    assert.equal(run(['--version']).status, 2);
    assert.equal(run(['--bogus', 'x']).status, 2);
    const f = fixture();
    assert.equal(run(['--version', '1.0.0', '--client-jar', f.client, '--fabric-api-jar', f.fapi, '--out', f.out]).status, 2, '--pack-dir is required');
  });
});
