import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { REPO_ROOT, readToolchain } from './lib/repo.mjs';
import { openZip } from './lib/zip.mjs';

const SCRIPT = join(REPO_ROOT, 'scripts', 'release', 'build-mods-bundle.sh');
const TOOLCHAIN = readToolchain(REPO_ROOT);
const FAPI = `fabric-api-${TOOLCHAIN.fabricApiVersion}.jar`;

const has = (cmd) => spawnSync('bash', ['-c', `command -v ${cmd}`], { encoding: 'utf8' }).status === 0;
const tools = process.platform !== 'win32' && has('zip') && has('unzip') && (has('sha256sum') || has('shasum'));
const skip = tools ? false : 'needs bash, zip, unzip and sha256sum/shasum';

function fixture() {
  const dir = mkdtempSync(join(tmpdir(), 'vanta-mods-bundle-'));
  mkdirSync(join(dir, 'in'));
  const client = join(dir, 'in', 'vanta-client-1.0.0.jar');
  const fapi = join(dir, 'in', FAPI);
  writeFileSync(client, Buffer.from('fake client jar bytes'));
  writeFileSync(fapi, Buffer.from('fake fabric api jar bytes'));
  return { dir, client, fapi, out: join(dir, 'out') };
}

function run(args) {
  return spawnSync('bash', [SCRIPT, ...args], { encoding: 'utf8' });
}

const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');

describe('build-mods-bundle.sh', { skip }, () => {
  test('builds the zip with INSTALL.txt, SHA256SUMS and both jars under mods/', () => {
    const f = fixture();
    const result = run(['--version', '1.0.0', '--client-jar', f.client, '--fabric-api-jar', f.fapi, '--out', f.out]);
    assert.equal(result.status, 0, result.stderr);
    const zipPath = join(f.out, 'vanta-client-1.0.0-mods.zip');
    assert.equal(result.stdout.trim().split('\n').at(-1), zipPath);
    const zip = openZip(zipPath);
    assert.deepEqual(zip.entries.map((e) => e.name), ['INSTALL.txt', 'SHA256SUMS', 'mods/', 'mods/vanta-client-1.0.0.jar', `mods/${FAPI}`]);
    assert.deepEqual(zip.read('mods/vanta-client-1.0.0.jar'), readFileSync(f.client));
    assert.deepEqual(zip.read(`mods/${FAPI}`), readFileSync(f.fapi));
    const install = zip.read('INSTALL.txt').toString('utf8');
    assert.doesNotMatch(install, /\{[A-Z_]+\}/, 'all placeholders filled');
    assert.match(install, /^VANTA Client 1\.0\.0 for Minecraft Java Edition 1\.21\.11\n/);
    assert.match(install, /Fabric Loader 0\.19\.5 or newer for 1\.21\.11/);
    assert.match(install, new RegExp(`- ${FAPI.replace(/[.+]/g, '\\$&')} {3}\\(Fabric API, Apache-2\\.0, required dependency\\)`));
    assert.match(install, /"fabric-loader-1\.21\.11" \(its version id is fabric-loader-0\.19\.5-1\.21\.11\)/);
    assert.match(install, /certutil -hashfile mods\\vanta-client-1\.0\.0\.jar SHA256/);
    assert.match(install, new RegExp(`certutil -hashfile mods\\\\${FAPI.replace(/[.+]/g, '\\$&')} SHA256`));
    assert.match(install, /Get-FileHash mods\\\*\.jar -Algorithm SHA256 \| Format-List Hash, Path\n/);
    const sums = zip.read('SHA256SUMS').toString('utf8');
    assert.equal(sums, [
      `${sha256(readFileSync(f.client))}  mods/vanta-client-1.0.0.jar`,
      `${sha256(readFileSync(f.fapi))}  mods/${FAPI}`,
      `${sha256(Buffer.from(install, 'utf8'))}  INSTALL.txt`,
      '',
    ].join('\n'));
  });

  test('rendered INSTALL.txt equals the reviewed template text for the pinned toolchain', () => {
    const template = readFileSync(join(REPO_ROOT, 'scripts', 'release', 'mods-bundle', 'INSTALL.txt'), 'utf8');
    assert.match(template, /\{VERSION\}/);
    assert.match(template, /\{FABRIC_API_VERSION\}/);
    assert.match(template, /VANTA is a legitimate client: no cheats, no combat automation, no packet manipulation\./);
    const f = fixture();
    assert.equal(run(['--version', '1.0.0', '--client-jar', f.client, '--fabric-api-jar', f.fapi, '--out', f.out]).status, 0);
    const rendered = openZip(join(f.out, 'vanta-client-1.0.0-mods.zip')).read('INSTALL.txt').toString('utf8');
    const expected = template
      .replaceAll('{VERSION}', '1.0.0')
      .replaceAll('{MINECRAFT_VERSION}', TOOLCHAIN.minecraftVersion)
      .replaceAll('{LOADER_VERSION}', TOOLCHAIN.fabricVersion)
      .replaceAll('{FABRIC_API_VERSION}', TOOLCHAIN.fabricApiVersion);
    assert.equal(rendered, expected);
  });

  test('INSTALL.txt walks through launcher-first install, both jar checks on Windows and the zoom key overlap', () => {
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
    assert.doesNotMatch(template, /https?:\/\//, 'no links in the bundle text');
    // Format-List prints Hash and the full Path of each jar; the default table cuts the path (and file name) off.
    assert.match(template, /^ +Get-FileHash mods\\\*\.jar -Algorithm SHA256 \| Format-List Hash, Path$/m);
    assert.match(template, /certutil -hashfile mods\\vanta-client-\{VERSION\}\.jar SHA256/);
    assert.match(template, /certutil -hashfile mods\\fabric-api-\{FABRIC_API_VERSION\}\.jar SHA256/);
    assert.match(template, /compare the hash of BOTH jars/);
    assert.match(template, /^- Right Shift opens the VANTA settings\. All VANTA key bindings are in the keybind manager \(Settings > Controls\)\.$/m);
    assert.doesNotMatch(template, /Everything is in Settings/, 'Settings > Controls holds the key bindings, not every VANTA setting');
    assert.match(template, /C is hold-to-zoom\. Vanilla Minecraft also uses C for "Save Hotbar Activator" \(Creative mode only\)/);
    assert.match(template, /rebind either one in Options > Controls > Key Binds/);
  });

  test('refuses misnamed jars, a Fabric API version other than the pinned one and missing files', () => {
    const f = fixture();
    const wrongName = join(f.dir, 'in', 'client.jar');
    writeFileSync(wrongName, 'x');
    let r = run(['--version', '1.0.0', '--client-jar', wrongName, '--fabric-api-jar', f.fapi, '--out', f.out]);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /client jar must be named vanta-client-1\.0\.0\.jar/);
    const otherFapi = join(f.dir, 'in', 'fabric-api-0.1.0+1.21.11.jar');
    writeFileSync(otherFapi, 'x');
    r = run(['--version', '1.0.0', '--client-jar', f.client, '--fabric-api-jar', otherFapi, '--out', f.out]);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /Fabric API jar must be fabric-api-0\.141\.6\+1\.21\.11\.jar/);
    r = run(['--version', '1.0.0', '--client-jar', join(f.dir, 'missing.jar'), '--fabric-api-jar', f.fapi, '--out', f.out]);
    assert.equal(r.status, 1);
    const empty = join(f.dir, 'in', 'vanta-client-2.0.0.jar');
    writeFileSync(empty, '');
    r = run(['--version', '2.0.0', '--client-jar', empty, '--fabric-api-jar', f.fapi, '--out', f.out]);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /is empty/);
  });

  test('usage errors exit 2', () => {
    assert.equal(run([]).status, 2);
    assert.equal(run(['--version']).status, 2);
    assert.equal(run(['--bogus', 'x']).status, 2);
  });
});
