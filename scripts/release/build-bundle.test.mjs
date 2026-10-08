/**
 * Tests for build-bundle.mjs: the full release zip (assemble), its manifest, verify, the download step against a
 * local node:http server, the release notes and the bundle schema. No network, temp dirs with small fake files.
 */
import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { createServer } from 'node:http';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fixtureManifest } from './lib/local-ai-fixture.mjs';
import { REPO_ROOT } from './lib/repo.mjs';
import { openZip } from './lib/zip.mjs';
import { buildZip } from './lib/zip-writer.mjs';
import { localAiNote } from './local-ai.mjs';
import { formatSize, releaseAssetNames } from './release-assets.mjs';
import { validateWithSchemaFile } from './validate-json.mjs';
import {
  CheckError, UsageError, WEBSITE_DOWNLOAD_URL, assembleBundle, bundleReadme, checkReleaseSums, downloadBundleInputs,
  downloadRelease, loadPublishedManifest, main, parseSums, releaseBaseUrl, releaseNotes, releasePageUrl, verifyBundle,
  wrapText, writeBundleManifest,
} from './build-bundle.mjs';

const TOOLCHAIN = { minecraftVersion: '1.21.11', fabricVersion: '0.19.5', fabricApiVersion: '0.141.6+1.21.11', javaVersion: 21 };
const GITHUB_BASE = 'https://github.com/example/VANTA-Client/releases/download/';
const BUNDLE_SCHEMA = join(REPO_ROOT, 'shared', 'schemas', 'bundle-manifest.schema.json');
const BUNDLE_URL = 'https://github.com/example/VANTA-Client/releases/download/v1.0.0/VantaClient-1.0.0-Release.zip';

const has = (cmd) => spawnSync('bash', ['-c', `command -v ${cmd}`], { encoding: 'utf8' }).status === 0;
const zipTools = process.platform !== 'win32' && has('zip');
const skipZip = zipTools ? false : 'needs the zip binary';

const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');

/** The release files of one product with deterministic fake bytes of different sizes. */
function releaseFiles(product, version) {
  return releaseAssetNames(product, version, TOOLCHAIN).map((name, index) => ({
    name,
    bytes: Buffer.from(`${product} ${version} ${name} ${'x'.repeat(16 * (index + 1))}`),
  }));
}

function manifestFor(product, version, files, { urlBase = GITHUB_BASE, channel = 'stable' } = {}) {
  return {
    schemaVersion: 1,
    product,
    version,
    ...TOOLCHAIN,
    releaseDate: '2026-10-07',
    channel,
    files: files.map((file) => ({ name: file.name, downloadUrl: `${urlBase}${product}-v${version}/${file.name}`, size: file.bytes.length, sha256: sha256(file.bytes) })),
    changelog: `website/content/changelog/${product}-${version}.md`,
  };
}

const sumsText = (files) => `${files.map((file) => `${sha256(file.bytes)}  ${file.name}`).join('\n')}\n`;
const json = (value) => `${JSON.stringify(value, null, 2)}\n`;

/**
 * A fake repository root: the three schemas, published manifests of client and launcher, a resolved Local AI manifest
 * (or the template with `localAiTemplate`), CHANGELOG.md, LICENSE, two docs pages (and a non-Markdown file that must
 * be left out) and the two release notes.
 */
function makeRoot({ cv = '1.0.0', lv = '1.0.0', urlBase, launcherChannel = 'stable', launcherUnpublished = false, localAiTemplate = false } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'vanta-bundle-root-'));
  mkdirSync(join(root, 'shared', 'schemas'), { recursive: true });
  mkdirSync(join(root, 'shared', 'releases'), { recursive: true });
  mkdirSync(join(root, 'shared', 'local-ai'), { recursive: true });
  for (const schema of ['release-manifest.schema.json', 'bundle-manifest.schema.json', 'local-ai.schema.json']) {
    copyFileSync(join(REPO_ROOT, 'shared', 'schemas', schema), join(root, 'shared', 'schemas', schema));
  }
  writeFileSync(join(root, 'shared', 'local-ai', 'local-ai.json'), json(fixtureManifest({ template: localAiTemplate })));
  const client = { files: releaseFiles('client', cv) };
  client.manifest = manifestFor('client', cv, client.files, { urlBase });
  const launcher = { files: releaseFiles('launcher', lv) };
  launcher.manifest = manifestFor('launcher', lv, launcher.files, { urlBase, channel: launcherChannel });
  if (launcherUnpublished) launcher.manifest.files = launcher.manifest.files.map((file) => ({ ...file, downloadUrl: '', size: 0, sha256: '' }));
  writeFileSync(join(root, 'shared', 'releases', `client-${cv}.json`), json(client.manifest));
  writeFileSync(join(root, 'shared', 'releases', `launcher-${lv}.json`), json(launcher.manifest));
  writeFileSync(join(root, 'CHANGELOG.md'), '# Changelog\n\n## [1.0.0] - 2026-10-07\n\nFake entry.\n');
  writeFileSync(join(root, 'LICENSE'), 'MIT License\n\nCopyright (c) 2026 VANTA Client contributors\n');
  mkdirSync(join(root, 'docs'));
  writeFileSync(join(root, 'docs', 'index.md'), '# Documentation\n');
  writeFileSync(join(root, 'docs', 'launcher.md'), '# Launcher\n');
  writeFileSync(join(root, 'docs', 'notes.txt'), 'not a Markdown page\n');
  mkdirSync(join(root, 'website', 'content', 'changelog'), { recursive: true });
  writeFileSync(join(root, 'website', 'content', 'changelog', `client-${cv}.md`), `---\nproduct: client\nversion: ${cv}\n---\n\nClient notes.\n`);
  writeFileSync(join(root, 'website', 'content', 'changelog', `launcher-${lv}.md`), `---\nproduct: launcher\nversion: ${lv}\n---\n\nLauncher notes.\n`);
  return { root, client, launcher, cv, lv };
}

/** The --from directory as `download` leaves it: the files, SHA256SUMS.txt and the manifest of each product. */
function makeInputs(fixture, dirName = 'in') {
  const from = join(fixture.root, dirName);
  for (const [product, data] of Object.entries({ client: fixture.client, launcher: fixture.launcher })) {
    mkdirSync(join(from, product), { recursive: true });
    for (const file of data.files) writeFileSync(join(from, product, file.name), file.bytes);
    writeFileSync(join(from, product, 'SHA256SUMS.txt'), sumsText(data.files));
    writeFileSync(join(from, product, `${product}-${data.manifest.version}.json`), json(data.manifest));
  }
  return from;
}

/** Every path the zip of the default fixture must hold, relative to its top-level folder, in zip order. */
function expectedPaths(fixture) {
  return [
    'README.txt', 'CHANGELOG.md', 'LICENSE', 'SHA256SUMS.txt', 'LOCAL-AI.txt',
    `release-notes/client-${fixture.cv}.md`, `release-notes/launcher-${fixture.lv}.md`,
    'docs/index.md', 'docs/launcher.md',
    'local-ai/local-ai.json',
    ...fixture.client.files.map((file) => `client/${file.name}`), 'client/SHA256SUMS.txt', `client/client-${fixture.cv}.json`,
    ...fixture.launcher.files.map((file) => `launcher/${file.name}`), 'launcher/SHA256SUMS.txt', `launcher/launcher-${fixture.lv}.json`,
  ];
}

const assembleArgs = (fixture, from, out) => ({ version: '1.0.0', clientVersion: fixture.cv, launcherVersion: fixture.lv, fromDir: from, outDir: out, root: fixture.root });

describe('assemble, manifest and verify on one zip', { skip: skipZip }, () => {
  const fixture = makeRoot();
  const from = makeInputs(fixture);
  const out = join(fixture.root, 'out');
  const folder = 'VantaClient-1.0.0-Release';
  const logged = [];
  let result;
  let zip;

  before(async () => {
    result = await assembleBundle({ ...assembleArgs(fixture, from, out), log: (line) => logged.push(line) });
    zip = openZip(result.zipPath);
  });

  test('the zip holds exactly the layout, in order, without directory entries', () => {
    assert.equal(result.zipPath, join(out, `${folder}.zip`));
    assert.deepEqual(zip.entries.map((entry) => entry.name), expectedPaths(fixture).map((path) => `${folder}/${path}`));
    assert.ok(zip.entries.every((entry) => !entry.directory));
    for (const file of fixture.client.files) assert.deepEqual(zip.read(`${folder}/client/${file.name}`), file.bytes, file.name);
    for (const file of fixture.launcher.files) assert.deepEqual(zip.read(`${folder}/launcher/${file.name}`), file.bytes, file.name);
    assert.deepEqual(zip.read(`${folder}/CHANGELOG.md`), readFileSync(join(fixture.root, 'CHANGELOG.md')));
    assert.deepEqual(zip.read(`${folder}/LICENSE`), readFileSync(join(fixture.root, 'LICENSE')));
    assert.deepEqual(zip.read(`${folder}/docs/launcher.md`), readFileSync(join(fixture.root, 'docs', 'launcher.md')));
    assert.deepEqual(zip.read(`${folder}/release-notes/client-1.0.0.md`), readFileSync(join(fixture.root, 'website', 'content', 'changelog', 'client-1.0.0.md')));
    assert.deepEqual(zip.read(`${folder}/local-ai/local-ai.json`), readFileSync(join(fixture.root, 'shared', 'local-ai', 'local-ai.json')), 'the committed Local AI manifest byte for byte');
    assert.equal(zip.read(`${folder}/LOCAL-AI.txt`).toString('utf8'), localAiNote(fixtureManifest()), 'LOCAL-AI.txt generated from that manifest');
    assert.match(zip.read(`${folder}/LOCAL-AI.txt`).toString('utf8'), /^VANTA Local AI: what Vanta Nexus downloads on first use\n/);
    assert.equal(zip.read(`${folder}/client/SHA256SUMS.txt`).toString('utf8'), sumsText(fixture.client.files));
    assert.deepEqual(JSON.parse(zip.read(`${folder}/launcher/launcher-1.0.0.json`).toString('utf8')), fixture.launcher.manifest);
    // Only the zip and its checksum file are left in the output folder.
    assert.deepEqual(readdirSync(out).sort(), ['SHA256SUMS.txt', `${folder}.zip`]);
  });

  test('SHA256SUMS.txt lists every other entry with its digest, and sha256sum -c accepts it', () => {
    const sums = parseSums(zip.read(`${folder}/SHA256SUMS.txt`).toString('utf8'));
    const others = expectedPaths(fixture).filter((path) => path !== 'SHA256SUMS.txt');
    assert.deepEqual([...sums.keys()], others, 'same order as the zip');
    for (const path of others) assert.equal(sums.get(path), sha256(zip.read(`${folder}/${path}`)), path);
    if (has('unzip') && has('sha256sum')) {
      const check = mkdtempSync(join(tmpdir(), 'vanta-bundle-check-'));
      const r = spawnSync('bash', ['-ec', 'unzip -q "$1" -d "$2" && cd "$2/$3" && sha256sum -c --quiet SHA256SUMS.txt', '_', result.zipPath, check, folder], { encoding: 'utf8' });
      assert.equal(r.status, 0, r.stderr);
    }
  });

  test('the checksum of the zip itself is written next to it and the entries are printed', () => {
    const zipBytes = readFileSync(result.zipPath);
    assert.equal(readFileSync(result.sumsPath, 'utf8'), `${sha256(zipBytes)}  ${folder}.zip\n`);
    assert.deepEqual(result.zip, { name: `${folder}.zip`, size: zipBytes.length, sha256: sha256(zipBytes) });
    assert.deepEqual(result.entries.map((entry) => entry.path), expectedPaths(fixture));
    for (const path of expectedPaths(fixture)) assert.ok(logged.some((line) => line.startsWith(`  ${path}  `)), `printed ${path}`);
    assert.match(logged.at(-1), /\.zip: \d+ entries, \d+ bytes, sha256 [a-f0-9]{64}$/);
  });

  test('README.txt is generated from the manifests: links, every file with its description, verification, no claims', () => {
    const readme = zip.read(`${folder}/README.txt`).toString('utf8');
    assert.equal(readme, bundleReadme({ version: '1.0.0', client: fixture.client.manifest, launcher: fixture.launcher.manifest, docFiles: ['index.md', 'launcher.md'] }));
    assert.match(readme, /^VANTA 1\.0\.0 full release\n=+\n/);
    // Sentences are word-wrapped, so they are matched on the unwrapped text.
    const unwrapped = readme.replace(/\n\s*/g, ' ');
    assert.match(unwrapped, /VANTA Client 1\.0\.0 and VANTA Launcher 1\.0\.0 for Minecraft Java Edition 1\.21\.11 \(Fabric Loader 0\.19\.5, Fabric API 0\.141\.6\+1\.21\.11, Java 21\)/);
    assert.match(unwrapped, /byte for byte the file attached to its GitHub Release; nothing was rebuilt/);
    assert.match(readme, /^ {2}VANTA Client 1\.0\.0: +https:\/\/github\.com\/example\/VANTA-Client\/releases\/tag\/client-v1\.0\.0$/m);
    assert.match(readme, /^ {2}VANTA Launcher 1\.0\.0: +https:\/\/github\.com\/example\/VANTA-Client\/releases\/tag\/launcher-v1\.0\.0$/m);
    assert.ok(readme.includes(WEBSITE_DOWNLOAD_URL));
    for (const file of [...fixture.client.files, ...fixture.launcher.files]) assert.match(readme, new RegExp(`^ {2}${file.name.replace(/[.+]/g, '\\$&')}  \\(\\d+ B\\)\\n {6}\\S`, 'm'), file.name);
    assert.match(unwrapped, /Windows x64 installer \(per-user, no admin rights\), bundles the Java 21 runtime\. Recommended for Windows\./);
    assert.match(unwrapped, /The VANTA Client Fabric mod for Minecraft 1\.21\.11\. Put it in your mods folder together with Fabric API\./);
    assert.match(readme, /the documentation \(2 Markdown pages/);
    assert.match(unwrapped, /LOCAL-AI\.txt +what Vanta Nexus downloads on first use of the Local AI \(the llama-server runtime and the model: files, sizes, sources, SHA-256, licences\); none of it is in this archive/);
    assert.match(unwrapped, /local-ai\/ +local-ai\.json, the manifest the client and the launcher use for those Local AI downloads/);
    assert.match(unwrapped, /The Local AI runtime and model are not in this archive: LOCAL-AI\.txt says what the game downloads for them/);
    assert.match(unwrapped, /the 3 files of the client release, its SHA256SUMS\.txt and its manifest client-1\.0\.0\.json/);
    assert.match(readme, /the 7 files of the launcher release/);
    assert.match(readme, /^ {4}Linux: {4}sha256sum -c SHA256SUMS\.txt$/m);
    assert.match(readme, /^ {4}macOS: {4}shasum -a 256 -c SHA256SUMS\.txt$/m);
    assert.match(readme, /certutil -hashfile <file> SHA256/);
    assert.match(unwrapped, /not code-signed yet, so Windows SmartScreen or macOS may warn before opening them\. Compare the SHA-256 first\./);
    assert.match(readme, /not affiliated with Mojang or Microsoft/);
    assert.doesNotMatch(readme, /`/, 'plain text, no Markdown code spans');
    // The Local AI is real and local; the README never promises a cloud service or anything beyond LOCAL-AI.txt.
    assert.doesNotMatch(readme, /cloud|API key|account|artificial intelligence|machine learning/i);
    assert.match(readme, /^[\x09\x0a\x20-\x7e]*$/, 'plain ASCII');
    for (const line of readme.split('\n')) assert.ok(line.length <= 120, `line too long: ${line}`);
  });

  test('manifest: schema-valid, size and sha256 of the real zip, contents equal to the entries', async () => {
    const bundlesDir = join(fixture.root, 'shared', 'releases', 'bundles');
    const written = await writeBundleManifest({
      version: '1.0.0', clientVersion: '1.0.0', launcherVersion: '1.0.0', zipPath: result.zipPath, url: BUNDLE_URL, date: '2026-10-08', outDir: bundlesDir, root: fixture.root,
    });
    assert.equal(written.path, join(bundlesDir, 'vanta-1.0.0.json'));
    const manifest = JSON.parse(readFileSync(written.path, 'utf8'));
    assert.deepEqual(manifest, written.manifest);
    assert.deepEqual(validateWithSchemaFile(BUNDLE_SCHEMA, manifest).errors, []);
    const zipBytes = readFileSync(result.zipPath);
    assert.deepEqual(manifest.file, { name: `${folder}.zip`, downloadUrl: BUNDLE_URL, size: zipBytes.length, sha256: sha256(zipBytes) });
    assert.equal(manifest.schemaVersion, 1);
    assert.equal(manifest.kind, 'bundle');
    assert.equal(manifest.version, '1.0.0');
    assert.equal(manifest.clientVersion, '1.0.0');
    assert.equal(manifest.launcherVersion, '1.0.0');
    assert.equal(manifest.minecraftVersion, '1.21.11');
    assert.equal(manifest.releaseDate, '2026-10-08');
    assert.equal(manifest.channel, 'stable');
    assert.deepEqual(Object.keys(manifest), ['schemaVersion', 'kind', 'version', 'clientVersion', 'launcherVersion', 'minecraftVersion', 'releaseDate', 'channel', 'file', 'contents']);
    const others = expectedPaths(fixture).filter((path) => path !== 'SHA256SUMS.txt');
    assert.deepEqual(manifest.contents.map((entry) => entry.path), others);
    for (const entry of manifest.contents) {
      const bytes = zip.read(`${folder}/${entry.path}`);
      assert.equal(entry.size, bytes.length, entry.path);
      assert.equal(entry.sha256, sha256(bytes), entry.path);
    }
    // The component files inside the zip carry the component manifests' digests.
    for (const file of fixture.client.files) assert.equal(manifest.contents.find((e) => e.path === `client/${file.name}`).sha256, sha256(file.bytes));
    // The repository validator accepts the written file.
    const r = spawnSync(process.execPath, [join(REPO_ROOT, 'scripts', 'release', 'validate-json.mjs'), BUNDLE_SCHEMA, written.path], { encoding: 'utf8' });
    assert.equal(r.status, 0, r.stderr);
    // The CLI prints the summary without --date (today) and without writing on --dry-run.
    const out2 = [];
    assert.equal(await main(['manifest', '--version', '1.0.0', '--zip', result.zipPath, '--url', BUNDLE_URL, '--out', join(fixture.root, 'elsewhere'), '--root', fixture.root, '--dry-run'], (m) => out2.push(m), (m) => out2.push(m)), 0);
    assert.match(out2[0], new RegExp(`^${folder}\\.zip: ${zipBytes.length} bytes, sha256 ${sha256(zipBytes)}, ${others.length} files inside$`));
    assert.match(out2[1], /^would write /);
    assert.equal(existsSync(join(fixture.root, 'elsewhere')), false);
    assert.match(JSON.parse(out2.slice(2).join('\n')).releaseDate, /^\d{4}-\d{2}-\d{2}$/);
  });

  test('manifest: refuses a non-https url, a misnamed zip, a zip without the release files and a beta component makes a beta bundle', async () => {
    const base = { version: '1.0.0', clientVersion: '1.0.0', launcherVersion: '1.0.0', zipPath: result.zipPath, url: BUNDLE_URL, root: fixture.root, dryRun: true };
    await assert.rejects(writeBundleManifest({ ...base, url: 'http://example.com/x.zip' }), UsageError);
    await assert.rejects(writeBundleManifest({ ...base, date: '8.10.2026' }), UsageError);
    const renamed = join(fixture.root, 'VantaClient-1.0.1-Release.zip');
    copyFileSync(result.zipPath, renamed);
    await assert.rejects(writeBundleManifest({ ...base, zipPath: renamed }), /must be named VantaClient-1\.0\.0-Release\.zip/);
    await assert.rejects(writeBundleManifest({ ...base, version: '1.0.1', zipPath: renamed }), /outside the top-level folder VantaClient-1\.0\.1-Release\//);
    // A zip with the right folder but only a README and a SHA256SUMS.txt that matches it: the release files are missing.
    const readme = Buffer.from('partial');
    const partial = join(fixture.root, 'partial', 'VantaClient-1.0.0-Release.zip');
    mkdirSync(join(fixture.root, 'partial'));
    writeFileSync(partial, buildZip([{ name: `${folder}/README.txt`, data: readme }, { name: `${folder}/SHA256SUMS.txt`, data: `${sha256(readme)}  README.txt\n` }]));
    await assert.rejects(writeBundleManifest({ ...base, zipPath: partial }), (error) => error instanceof CheckError && /client\/vanta-client-1\.0\.0\.jar is not in the zip/.test(error.message)
      && /LOCAL-AI\.txt is not in the zip/.test(error.message) && /local-ai\/local-ai\.json is not in the zip/.test(error.message));
    // A SHA256SUMS.txt that disagrees with the bytes.
    writeFileSync(partial, buildZip([{ name: `${folder}/README.txt`, data: readme }, { name: `${folder}/SHA256SUMS.txt`, data: `${'0'.repeat(64)}  README.txt\n` }]));
    await assert.rejects(writeBundleManifest({ ...base, zipPath: partial }), /README\.txt is listed with 0{64}/);
    // Beta launcher: the bundle is beta too.
    const beta = makeRoot({ launcherChannel: 'beta' });
    const betaOut = join(beta.root, 'out');
    const built = await assembleBundle(assembleArgs(beta, makeInputs(beta), betaOut));
    const written = await writeBundleManifest({ ...base, zipPath: built.zipPath, root: beta.root });
    assert.equal(written.manifest.channel, 'beta');
    assert.deepEqual(validateWithSchemaFile(BUNDLE_SCHEMA, written.manifest).errors, []);
  });

  test('verify: passes on the real zip, fails on a tampered copy, reports an unpublished manifest', async () => {
    const { manifest } = await writeBundleManifest({ version: '1.0.0', clientVersion: '1.0.0', launcherVersion: '1.0.0', zipPath: result.zipPath, url: BUNDLE_URL, root: fixture.root, dryRun: true });
    const ok = await verifyBundle(manifest, { localZip: result.zipPath, root: fixture.root });
    assert.equal(ok.status, 'ok');
    assert.deepEqual(ok.actual, { size: manifest.file.size, sha256: manifest.file.sha256 });
    const tampered = join(fixture.root, 'tampered.zip');
    writeFileSync(tampered, Buffer.concat([readFileSync(result.zipPath), Buffer.from([0])]));
    const bad = await verifyBundle(manifest, { localZip: tampered, root: fixture.root });
    assert.equal(bad.status, 'mismatch');
    assert.match(bad.message, new RegExp(`expected ${manifest.file.size} bytes / ${manifest.file.sha256}, got ${manifest.file.size + 1} bytes`));
    // Download path: the body comes from the manifest's URL and is hashed as a stream.
    const served = { [BUNDLE_URL]: readFileSync(result.zipPath) };
    const fetchImpl = async (url) => (served[url] ? new Response(served[url], { status: 200 }) : new Response('nope', { status: 404, statusText: 'Not Found' }));
    assert.equal((await verifyBundle(manifest, { fetchImpl, root: fixture.root })).status, 'ok');
    served[BUNDLE_URL] = Buffer.from('something else');
    assert.equal((await verifyBundle(manifest, { fetchImpl, root: fixture.root })).status, 'mismatch');
    const unpublished = { ...manifest, file: { ...manifest.file, downloadUrl: '', size: 0, sha256: '' } };
    assert.equal((await verifyBundle(unpublished, { root: fixture.root })).status, 'unpublished');
    await assert.rejects(verifyBundle({ ...manifest, kind: 'release' }, { localZip: result.zipPath, root: fixture.root }), UsageError);
    // CLI: exit codes 0/1 and the report lines.
    const manifestPath = join(fixture.root, 'vanta-1.0.0.json');
    writeFileSync(manifestPath, json(manifest));
    const out = [];
    const err = [];
    assert.equal(await main(['verify', manifestPath, '--local', result.zipPath, '--root', fixture.root], (m) => out.push(m), (m) => err.push(m)), 0);
    assert.match(out.join('\n'), /OK\s+VantaClient-1\.0\.0-Release\.zip\s+\d+ bytes, sha256 [a-f0-9]{64}\nBundle verified\./);
    assert.equal(await main(['verify', manifestPath, '--local', tampered, '--root', fixture.root], (m) => out.push(m), (m) => err.push(m)), 1);
    assert.match(err.join('\n'), /MISMATCH\s+VantaClient-1\.0\.0-Release\.zip/);
    writeFileSync(manifestPath, json(unpublished));
    assert.equal(await main(['verify', manifestPath, '--root', fixture.root], (m) => out.push(m), (m) => err.push(m)), 1);
    assert.match(err.at(-1), /UNPUBLISHED/);
    assert.equal(await main(['verify', manifestPath, '--local', join(fixture.root, 'missing.zip'), '--root', fixture.root], () => {}, (m) => err.push(m)), 2);
  });

  test('notes: Markdown with the contents table, verification, links and the toolchain line, no AI claims', async () => {
    const { manifest } = await writeBundleManifest({ version: '1.0.0', clientVersion: '1.0.0', launcherVersion: '1.0.0', zipPath: result.zipPath, url: BUNDLE_URL, root: fixture.root, dryRun: true });
    const notes = releaseNotes({ version: '1.0.0', clientVersion: '1.0.0', launcherVersion: '1.0.0', manifest, root: fixture.root });
    assert.match(notes, /^One zip with every published file of VANTA Client 1\.0\.0 and VANTA Launcher 1\.0\.0/);
    assert.ok(notes.includes('[client-v1.0.0](https://github.com/example/VANTA-Client/releases/tag/client-v1.0.0)'));
    assert.ok(notes.includes('[launcher-v1.0.0](https://github.com/example/VANTA-Client/releases/tag/launcher-v1.0.0)'));
    assert.ok(notes.includes(`[website download page](${WEBSITE_DOWNLOAD_URL})`));
    assert.match(notes, /## Contents of `VantaClient-1\.0\.0-Release\.zip`\n\n\| Path \| Size \| SHA-256 \|\n\| --- \| --- \| --- \|\n/);
    for (const entry of manifest.contents) assert.ok(notes.includes(`| \`${entry.path}\` | ${formatSize(entry.size)} | \`${entry.sha256}\` |`), entry.path);
    assert.ok(!notes.includes('| `SHA256SUMS.txt` |'), 'the zip-level checksum file is not a row');
    assert.match(notes, /## Verify your download\n\n```\n[a-f0-9]{64}  VantaClient-1\.0\.0-Release\.zip\n```\n/);
    assert.ok(notes.includes(`${manifest.file.sha256}  VantaClient-1.0.0-Release.zip`));
    assert.match(notes, /certutil -hashfile VantaClient-1\.0\.0-Release\.zip SHA256/);
    assert.match(notes, /not code-signed yet, so Windows SmartScreen or macOS may warn before opening them\. Compare the SHA-256 first\./);
    assert.match(notes, /Also attached: `SHA256SUMS.txt` \(SHA-256 of the zip\) and `vanta-1\.0\.0\.json`/);
    assert.match(notes, /\nMinecraft 1\.21\.11 · Fabric Loader 0\.19\.5 · Java 21\. Not affiliated with Mojang or Microsoft\.\n$/);
    assert.ok(notes.includes('| `LOCAL-AI.txt` |') && notes.includes('| `local-ai/local-ai.json` |'), 'the two Local AI files are rows of the contents table');
    assert.doesNotMatch(notes, /cloud|API key|artificial intelligence|machine learning/i);
    assert.doesNotMatch(notes, /—/, 'no em dashes');
    assert.throws(() => releaseNotes({ version: '1.0.1', clientVersion: '1.0.0', launcherVersion: '1.0.0', manifest, root: fixture.root }), /has version 1\.0\.0, expected 1\.0\.1/);
    const manifestPath = join(fixture.root, 'notes-manifest.json');
    writeFileSync(manifestPath, json(manifest));
    const out = [];
    assert.equal(await main(['notes', '--version', '1.0.0', '--manifest', manifestPath, '--root', fixture.root], (m) => out.push(m)), 0);
    assert.equal(`${out.join('\n')}\n`, notes);
  });

  test('assemble through the CLI reports the files it wrote', async () => {
    const fresh = makeRoot();
    const out = [];
    const code = await main(['assemble', '--version', '1.0.0', '--from', makeInputs(fresh), '--out', join(fresh.root, 'dist'), '--root', fresh.root], (m) => out.push(m), (m) => out.push(m));
    assert.equal(code, 0, out.join('\n'));
    assert.equal(out.at(-2), `wrote ${join(fresh.root, 'dist', 'VantaClient-1.0.0-Release.zip')}`);
    assert.equal(out.at(-1), `wrote ${join(fresh.root, 'dist', 'SHA256SUMS.txt')}`);
    assert.ok(statSync(join(fresh.root, 'dist', 'VantaClient-1.0.0-Release.zip')).size > 0);
  });
});

describe('assemble refuses inputs that do not match the manifests', { skip: skipZip }, () => {
  test('tampered, missing, extra or stale input files, a wrong SHA256SUMS.txt, an unpublished manifest, missing notes', async () => {
    let fixture = makeRoot();
    let from = makeInputs(fixture);
    const out = join(fixture.root, 'out');
    const jar = join(from, 'client', 'vanta-client-1.0.0.jar');
    writeFileSync(jar, 'tampered');
    await assert.rejects(assembleBundle(assembleArgs(fixture, from, out)), (error) => error instanceof CheckError && /client\/vanta-client-1\.0\.0\.jar: expected \d+ bytes/.test(error.message));
    assert.equal(existsSync(join(out, 'VantaClient-1.0.0-Release.zip')), false);
    assert.ok(!existsSync(out) || readdirSync(out).length === 0, 'no staging folder is left behind');

    fixture = makeRoot();
    from = makeInputs(fixture);
    writeFileSync(join(from, 'launcher', 'extra.txt'), 'x');
    await assert.rejects(assembleBundle(assembleArgs(fixture, from, out)), /unexpected file: extra\.txt/);

    fixture = makeRoot();
    from = makeInputs(fixture);
    writeFileSync(join(from, 'launcher', 'SHA256SUMS.txt'), `${'a'.repeat(64)}  VANTA-Launcher-1.0.0.msi\n`);
    await assert.rejects(assembleBundle(assembleArgs(fixture, from, out)), /SHA256SUMS\.txt of launcher 1\.0\.0 does not match its manifest/);

    fixture = makeRoot();
    from = makeInputs(fixture);
    writeFileSync(join(from, 'client', 'client-1.0.0.json'), json({ ...fixture.client.manifest, releaseDate: '2020-01-01' }));
    await assert.rejects(assembleBundle(assembleArgs(fixture, from, out)), /client\/client-1\.0\.0\.json differs from the committed manifest/);

    fixture = makeRoot({ launcherUnpublished: true });
    from = makeInputs(fixture);
    await assert.rejects(assembleBundle(assembleArgs(fixture, from, out)), (error) => error instanceof CheckError && /launcher-1\.0\.0\.json is not published/.test(error.message));

    fixture = makeRoot();
    from = makeInputs(fixture);
    writeFileSync(join(fixture.root, 'website', 'content', 'changelog', 'launcher-1.0.0.md'), '');
    await assert.rejects(assembleBundle(assembleArgs(fixture, from, out)), (error) => error instanceof UsageError && /launcher-1\.0\.0\.md is empty/.test(error.message));

    // The zip never ships the unresolved Local AI template (or no manifest at all).
    fixture = makeRoot({ localAiTemplate: true });
    from = makeInputs(fixture);
    await assert.rejects(assembleBundle(assembleArgs(fixture, from, out)), (error) => error instanceof UsageError && /shared\/local-ai\/local-ai\.json is not resolved: it still holds template values/.test(error.message));
    assert.equal(existsSync(join(out, 'VantaClient-1.0.0-Release.zip')), false);
    fixture = makeRoot();
    from = makeInputs(fixture);
    rmSync(join(fixture.root, 'shared', 'local-ai', 'local-ai.json'));
    await assert.rejects(assembleBundle(assembleArgs(fixture, from, out)), (error) => error instanceof UsageError && /shared\/local-ai\/local-ai\.json is missing/.test(error.message));

    fixture = makeRoot();
    await assert.rejects(assembleBundle(assembleArgs(fixture, join(fixture.root, 'nowhere'), out)), /is not a directory/);
    await assert.rejects(assembleBundle({ ...assembleArgs(fixture, makeInputs(fixture), out), clientVersion: '9.9.9' }), (error) => error instanceof UsageError && /client-9\.9\.9\.json is missing/.test(error.message));
  });
});

describe('download against a local http server', () => {
  const fixture = makeRoot({ urlBase: 'https://example.invalid/' });
  /** path -> { body, failFirst?, alwaysFail? } */
  const routes = new Map();
  const hits = new Map();
  let server;
  let base;

  for (const [product, data] of Object.entries({ client: fixture.client, launcher: fixture.launcher })) {
    const tag = `${product}-v${data.manifest.version}`;
    for (const file of data.files) routes.set(`/${tag}/${file.name}`, { body: file.bytes });
    routes.set(`/${tag}/SHA256SUMS.txt`, { body: Buffer.from(sumsText(data.files)) });
    routes.set(`/${tag}/${product}-${data.manifest.version}.json`, { body: Buffer.from(json(data.manifest)) });
  }

  before(async () => {
    server = createServer((req, res) => {
      const path = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
      hits.set(path, (hits.get(path) ?? 0) + 1);
      const route = routes.get(path);
      if (!route) {
        res.writeHead(404, { 'content-type': 'text/plain' });
        res.end('not found');
        return;
      }
      if (route.alwaysFail || (route.failFirst && hits.get(path) === 1)) {
        res.writeHead(500, { 'content-type': 'text/plain' });
        res.end('boom');
        return;
      }
      res.writeHead(200, { 'content-type': 'application/octet-stream', 'content-length': route.body.length });
      res.end(route.body);
    });
    await new Promise((listening) => server.listen(0, '127.0.0.1', listening));
    base = `http://127.0.0.1:${server.address().port}`;
  });
  after(() => new Promise((closed) => server.close(closed)));

  /** The manifests carry https URLs (the schema demands them); the test routes them to the local server. */
  const fetchImpl = (url, init) => fetch(url.replace(/^https:\/\/example\.invalid/, base), init);
  const options = { root: fixture.root, fetchImpl, attempts: 3, retryDelayMs: 1 };

  test('downloads and verifies every file of both releases plus SHA256SUMS.txt and the manifest, retrying a failed request', async () => {
    routes.get('/launcher-v1.0.0/VANTA-Launcher-1.0.0.exe').failFirst = true;
    routes.get('/client-v1.0.0/SHA256SUMS.txt').failFirst = true;
    const out = join(fixture.root, 'downloaded');
    const logged = [];
    const result = await downloadBundleInputs({ clientVersion: '1.0.0', launcherVersion: '1.0.0', outDir: out, ...options, log: (m) => logged.push(m) });
    for (const [product, data] of Object.entries({ client: fixture.client, launcher: fixture.launcher })) {
      assert.deepEqual(readdirSync(join(out, product)).sort(), [...data.files.map((f) => f.name), 'SHA256SUMS.txt', `${product}-1.0.0.json`].sort(), product);
      for (const file of data.files) assert.deepEqual(readFileSync(join(out, product, file.name)), file.bytes, file.name);
      assert.equal(readFileSync(join(out, product, 'SHA256SUMS.txt'), 'utf8'), sumsText(data.files));
      assert.deepEqual(JSON.parse(readFileSync(join(out, product, `${product}-1.0.0.json`), 'utf8')), data.manifest);
      assert.deepEqual(result[product].map((entry) => entry.name), [...data.files.map((f) => f.name), 'SHA256SUMS.txt', `${product}-1.0.0.json`]);
      for (const [index, file] of data.files.entries()) assert.deepEqual(result[product][index], { name: file.name, size: file.bytes.length, sha256: sha256(file.bytes) });
    }
    assert.equal(hits.get('/launcher-v1.0.0/VANTA-Launcher-1.0.0.exe'), 2, 'retried once after the 500');
    assert.equal(hits.get('/client-v1.0.0/SHA256SUMS.txt'), 2);
    assert.equal(hits.get('/client-v1.0.0/vanta-client-1.0.0.jar'), 1);
    assert.ok(logged.some((line) => /VANTA-Launcher-1\.0\.0\.exe: attempt 1 failed \(HTTP 500 Internal Server Error\); retrying in 1 ms/.test(line)), logged.join('\n'));
    assert.ok(logged.some((line) => /^ {2}ok {2}client\/fabric-api-0\.141\.6\+1\.21\.11\.jar {2}\d+ bytes, sha256 [a-f0-9]{64}$/.test(line)));
    delete routes.get('/launcher-v1.0.0/VANTA-Launcher-1.0.0.exe').failFirst;
    delete routes.get('/client-v1.0.0/SHA256SUMS.txt').failFirst;
  });

  test('a file whose bytes do not match the manifest fails at once and leaves nothing behind', async () => {
    const path = '/client-v1.0.0/vanta-client-1.0.0-mods.zip';
    const good = routes.get(path).body;
    routes.set(path, { body: Buffer.from('not the released bytes') });
    hits.delete(path);
    const out = join(fixture.root, 'mismatch');
    await assert.rejects(downloadRelease('client', '1.0.0', out, options), (error) => error instanceof CheckError
      && new RegExp(`^vanta-client-1\\.0\\.0-mods\\.zip: expected ${good.length} bytes / ${sha256(good)}, got 22 bytes / ${sha256('not the released bytes')}$`).test(error.message));
    assert.equal(hits.get(path), 1, 'a wrong file is not retried');
    assert.equal(existsSync(join(out, 'client', 'vanta-client-1.0.0-mods.zip')), false);
    assert.equal(existsSync(join(out, 'client', 'vanta-client-1.0.0-mods.zip.part')), false);
    assert.ok(existsSync(join(out, 'client', 'vanta-client-1.0.0.jar')), 'the file before it was kept');
    routes.set(path, { body: good });
  });

  test('a SHA256SUMS.txt or a manifest on the release that disagrees with the committed manifest fails', async () => {
    const sums = '/launcher-v1.0.0/SHA256SUMS.txt';
    const goodSums = routes.get(sums).body;
    routes.set(sums, { body: Buffer.from(goodSums.toString('utf8').replace(/^[a-f0-9]{64}/m, 'f'.repeat(64))) });
    await assert.rejects(downloadRelease('launcher', '1.0.0', join(fixture.root, 'bad-sums'), options), /SHA256SUMS\.txt of launcher 1\.0\.0 does not match its manifest: VANTA-Launcher-1\.0\.0\.msi is listed with f{64}/);
    routes.set(sums, { body: goodSums });
    const manifest = '/launcher-v1.0.0/launcher-1.0.0.json';
    const goodManifest = routes.get(manifest).body;
    routes.set(manifest, { body: Buffer.from(json({ ...fixture.launcher.manifest, notes: 'edited' })) });
    await assert.rejects(downloadRelease('launcher', '1.0.0', join(fixture.root, 'bad-manifest'), options), /launcher\/launcher-1\.0\.0\.json attached to the release differs from shared\/releases\/launcher-1\.0\.0\.json/);
    routes.set(manifest, { body: Buffer.from('{not json') });
    await assert.rejects(downloadRelease('launcher', '1.0.0', join(fixture.root, 'bad-json'), options), /attached to the release is not JSON/);
    routes.set(manifest, { body: goodManifest });
  });

  test('an HTTP error is retried up to the attempt limit, then fails; a 404 fails the same way', async () => {
    const path = '/client-v1.0.0/vanta-client-1.0.0.jar';
    routes.get(path).alwaysFail = true;
    hits.delete(path);
    await assert.rejects(downloadRelease('client', '1.0.0', join(fixture.root, 'flaky'), { ...options, attempts: 2 }), /HTTP 500 Internal Server Error/);
    assert.equal(hits.get(path), 2);
    delete routes.get(path).alwaysFail;
    const missing = makeRoot({ urlBase: 'https://example.invalid/', cv: '1.0.1' });
    await assert.rejects(downloadRelease('client', '1.0.1', join(fixture.root, 'missing'), { ...options, root: missing.root, attempts: 1 }), /HTTP 404 Not Found/);
  });

  test('an unpublished manifest is refused before any request', async () => {
    const unpublished = makeRoot({ urlBase: 'https://example.invalid/', launcherUnpublished: true });
    const before = [...hits.values()].reduce((a, b) => a + b, 0);
    await assert.rejects(downloadRelease('launcher', '1.0.0', join(unpublished.root, 'out'), { ...options, root: unpublished.root }), (error) => error instanceof CheckError && /launcher-1\.0\.0\.json is not published: VANTA-Launcher-1\.0\.0\.msi, .* have no downloadUrl, size and sha256/.test(error.message));
    assert.equal([...hits.values()].reduce((a, b) => a + b, 0), before);
  });
});

describe('helpers', () => {
  test('parseSums reads sha256sum output (text and binary markers) and rejects other lines', () => {
    const sums = parseSums(`${'a'.repeat(64)}  a.jar\n${'b'.repeat(64)} *b.msi\n\n`);
    assert.deepEqual([...sums], [['a.jar', 'a'.repeat(64)], ['b.msi', 'b'.repeat(64)]]);
    assert.throws(() => parseSums('garbage\n'), CheckError);
    assert.throws(() => parseSums(`${'A'.repeat(64)}  a.jar\n`), CheckError);
  });

  test('checkReleaseSums requires exactly the manifest files with the manifest digests', () => {
    const files = releaseFiles('client', '1.0.0');
    const manifest = manifestFor('client', '1.0.0', files);
    checkReleaseSums(manifest, sumsText(files));
    assert.throws(() => checkReleaseSums(manifest, sumsText(files.slice(1))), /vanta-client-1\.0\.0\.jar is not listed/);
    assert.throws(() => checkReleaseSums(manifest, `${sumsText(files)}${'c'.repeat(64)}  extra.jar\n`), /extra\.jar is listed but not in the manifest/);
  });

  test('releaseBaseUrl and releasePageUrl', () => {
    const files = releaseFiles('launcher', '1.0.0');
    const manifest = manifestFor('launcher', '1.0.0', files);
    assert.equal(releaseBaseUrl(manifest), `${GITHUB_BASE}launcher-v1.0.0/`);
    assert.equal(releasePageUrl(releaseBaseUrl(manifest)), 'https://github.com/example/VANTA-Client/releases/tag/launcher-v1.0.0');
    assert.equal(releasePageUrl('https://example.invalid/launcher-v1.0.0/'), 'https://example.invalid/launcher-v1.0.0/');
    const mixed = { ...manifest, files: manifest.files.map((f, i) => (i === 0 ? { ...f, downloadUrl: `https://elsewhere.invalid/${f.name}` } : f)) };
    assert.throws(() => releaseBaseUrl(mixed), /not served from one folder/);
    const renamed = { ...manifest, files: manifest.files.map((f, i) => (i === 0 ? { ...f, downloadUrl: `${GITHUB_BASE}launcher-v1.0.0/other.msi` } : f)) };
    assert.throws(() => releaseBaseUrl(renamed), /does not end in its name/);
  });

  test('the committed 1.3.0 manifests are published and point at the repository releases', () => {
    const client = loadPublishedManifest(REPO_ROOT, 'client', '1.3.0').manifest;
    const launcher = loadPublishedManifest(REPO_ROOT, 'launcher', '1.3.0').manifest;
    assert.equal(releasePageUrl(releaseBaseUrl(client)), 'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/client-v1.3.0');
    assert.equal(releasePageUrl(releaseBaseUrl(launcher)), 'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.3.0');
    const readme = bundleReadme({ version: '1.3.0', client, launcher, docFiles: readdirSync(join(REPO_ROOT, 'docs')).filter((f) => f.endsWith('.md')) });
    for (const line of readme.split('\n')) assert.ok(line.length <= 120, `line too long: ${line}`);
    assert.doesNotMatch(readme, /cloud|API key|artificial intelligence|machine learning/i);
  });

  test('wrapText keeps words whole and indents every line', () => {
    assert.deepEqual(wrapText('one two three four', { width: 12, indent: '  ' }), ['  one two', '  three four']);
    assert.deepEqual(wrapText('', { width: 12 }), []);
    assert.deepEqual(wrapText('averyveryverylongword', { width: 5 }), ['averyveryverylongword']);
  });
});

describe('bundle-manifest.schema.json', () => {
  const valid = {
    schemaVersion: 1,
    kind: 'bundle',
    version: '1.3.0',
    clientVersion: '1.3.0',
    launcherVersion: '1.3.0',
    minecraftVersion: '1.21.11',
    releaseDate: '2026-10-08',
    channel: 'stable',
    file: { name: 'VantaClient-1.3.0-Release.zip', downloadUrl: 'https://github.com/x/y/releases/download/v1.3.0/VantaClient-1.3.0-Release.zip', size: 10, sha256: 'a'.repeat(64) },
    contents: [{ path: 'client/vanta-client-1.3.0.jar', size: 5, sha256: 'b'.repeat(64) }, { path: 'docs/faq.md', size: 1, sha256: 'c'.repeat(64) }],
  };
  const check = (manifest) => validateWithSchemaFile(BUNDLE_SCHEMA, manifest).valid;

  test('accepts a published and an unpublished manifest', () => {
    assert.deepEqual(validateWithSchemaFile(BUNDLE_SCHEMA, valid).errors, []);
    assert.equal(check({ ...valid, file: { ...valid.file, downloadUrl: '', size: 0, sha256: '' } }), true);
    assert.equal(check({ ...valid, channel: 'beta', version: '1.4.0-beta.1' }), true);
  });

  test('rejects a wrong kind, version, url, digest, path or shape', () => {
    assert.equal(check({ ...valid, kind: 'release' }), false);
    assert.equal(check({ ...valid, schemaVersion: 2 }), false);
    assert.equal(check({ ...valid, version: '1.3' }), false);
    assert.equal(check({ ...valid, clientVersion: 'v1.3.0' }), false);
    assert.equal(check({ ...valid, launcherVersion: '' }), false);
    assert.equal(check({ ...valid, minecraftVersion: '1.21.11-pre1' }), false);
    assert.equal(check({ ...valid, releaseDate: '2026-13-40' }), false);
    assert.equal(check({ ...valid, channel: 'nightly' }), false);
    assert.equal(check({ ...valid, file: { ...valid.file, downloadUrl: 'http://github.com/x/y/z.zip' } }), false);
    assert.equal(check({ ...valid, file: { ...valid.file, downloadUrl: 'ftp://github.com/x/y/z.zip' } }), false);
    assert.equal(check({ ...valid, file: { ...valid.file, name: 'vanta-1.3.0.zip' } }), false);
    assert.equal(check({ ...valid, file: { ...valid.file, sha256: 'A'.repeat(64) } }), false);
    assert.equal(check({ ...valid, file: { ...valid.file, size: -1 } }), false);
    assert.equal(check({ ...valid, contents: [] }), false);
    assert.equal(check({ ...valid, contents: [{ ...valid.contents[0], path: '/client/x.jar' }] }), false);
    assert.equal(check({ ...valid, contents: [{ ...valid.contents[0], path: 'client/../x.jar' }] }), false);
    assert.equal(check({ ...valid, contents: [{ ...valid.contents[0], path: 'client\\x.jar' }] }), false);
    assert.equal(check({ ...valid, contents: [{ ...valid.contents[0], size: 0 }] }), false);
    assert.equal(check({ ...valid, contents: [{ ...valid.contents[0], sha256: '' }] }), false);
    assert.equal(check({ ...valid, contents: [{ ...valid.contents[0], extra: 1 }] }), false);
    assert.equal(check({ ...valid, unknown: true }), false);
    const { contents, ...withoutContents } = valid;
    assert.equal(check(withoutContents), false);
  });
});

describe('CLI usage', () => {
  test('exit code 2 for a missing or unknown command, missing options and bad versions', async () => {
    const err = [];
    const logError = (m) => err.push(m);
    assert.equal(await main([], () => {}, logError), 2);
    assert.match(err.join('\n'), /a command is required/);
    assert.equal(await main(['bogus'], () => {}, logError), 2);
    assert.equal(await main(['download', '--client-version', '1.0.0'], () => {}, logError), 2);
    assert.match(err.join('\n'), /--launcher-version is required for download/);
    assert.equal(await main(['assemble', '--version', 'x', '--from', 'a', '--out', 'b'], () => {}, logError), 2);
    assert.match(err.join('\n'), /--version 'x' is not a SemVer version/);
    assert.equal(await main(['manifest', '--version', '1.0.0', '--zip', 'a.zip'], () => {}, logError), 2);
    assert.equal(await main(['verify'], () => {}, logError), 2);
    assert.equal(await main(['verify', 'a.json', 'b.json'], () => {}, logError), 2);
    assert.equal(await main(['notes', '--version', '1.0.0'], () => {}, logError), 2);
    assert.equal(await main(['download', '--client-version', '1.0.0', '--launcher-version', '1.0.0', '--out', 'x', '--attempts', '0'], () => {}, logError), 2);
    assert.equal(await main(['download', '--client-version', '1.0.0', '--launcher-version', '1.0.0', '--out', 'x', '--bogus'], () => {}, logError), 2);
    const out = [];
    assert.equal(await main(['--help'], (m) => out.push(m)), 0);
    assert.match(out[0], /^Usage: node scripts\/release\/build-bundle\.mjs <command>/);
  });

  test('exit code 2 when a manifest file cannot be read, 1 when a check fails', async () => {
    const fixture = makeRoot();
    const err = [];
    assert.equal(await main(['verify', join(fixture.root, 'nope.json'), '--root', fixture.root], () => {}, (m) => err.push(m)), 2);
    assert.match(err.at(-1), /cannot read/);
    assert.equal(await main(['notes', '--version', '1.0.0', '--manifest', join(fixture.root, 'nope.json'), '--root', fixture.root], () => {}, (m) => err.push(m)), 2);
    // assemble with a missing input directory is a failed check.
    assert.equal(await main(['assemble', '--version', '1.0.0', '--from', join(fixture.root, 'nowhere'), '--out', join(fixture.root, 'out'), '--root', fixture.root], () => {}, (m) => err.push(m)), 1);
    assert.match(err.at(-1), /is not a directory/);
  });
});
