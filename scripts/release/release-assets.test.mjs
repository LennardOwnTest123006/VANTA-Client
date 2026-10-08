import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
  LAUNCHER_EXE_DROPPED_FROM, assetsMarkdown, checkDirectory, checkManifest, extraAssetNames, formatSize, launcherHasExe, main,
  markdownCell, releaseAssetNames, releaseAssets, releaseDownloadUrl, releaseTag,
} from './release-assets.mjs';
import { REPO_ROOT, readToolchain } from './lib/repo.mjs';

const TOOLCHAIN = Object.freeze({ minecraftVersion: '1.21.11', fabricVersion: '0.19.5', fabricApiVersion: '0.141.6+1.21.11', javaVersion: 21 });
const REPO = 'LennardOwnTest123006/VANTA-Client';

const CLIENT_FILES = ['vanta-client-1.0.0.jar', 'vanta-client-1.0.0-mods.zip', 'fabric-api-0.141.6+1.21.11.jar'];
/** The launcher files of a release before 1.4.0 (with the .exe wrapper of the installer). */
const LAUNCHER_FILES = [
  'VANTA-Launcher-1.0.0.msi',
  'VANTA-Launcher-1.0.0.exe',
  'VANTA-Launcher-1.0.0-windows-portable.zip',
  'vanta-launcher-1.0.0-windows-all.jar',
  'VANTA-Launcher-1.0.0-linux-x64.tar.gz',
  'vanta-launcher-1.0.0-linux-all.jar',
  'vanta-launcher-1.0.0-macos-aarch64-all.jar',
];
/** The launcher files from 1.4.0 on: the .msi is the only Windows installer. */
const LAUNCHER_FILES_140 = [
  'VANTA-Launcher-1.4.0.msi',
  'VANTA-Launcher-1.4.0-windows-portable.zip',
  'vanta-launcher-1.4.0-windows-all.jar',
  'VANTA-Launcher-1.4.0-linux-x64.tar.gz',
  'vanta-launcher-1.4.0-linux-all.jar',
  'vanta-launcher-1.4.0-macos-aarch64-all.jar',
];

function manifest(product, names, filled = false, tag = `${product}-v1.0.0`) {
  return {
    schemaVersion: 1,
    product,
    version: '1.0.0',
    ...TOOLCHAIN,
    releaseDate: '2026-10-05',
    channel: 'stable',
    files: names.map((name) => (filled
      ? { name, downloadUrl: `https://github.com/${REPO}/releases/download/${tag}/${name}`, size: 10, sha256: 'a'.repeat(64) }
      : { name, downloadUrl: '', size: 0, sha256: '' })),
    changelog: `website/content/changelog/${product}-1.0.0.md`,
  };
}

/** Markdown with the single-backtick code spans removed (what GitHub would parse as inline HTML is what remains). */
function outsideCodeSpans(markdown) {
  return markdown.replace(/`[^`\n]*`/g, '');
}

/** A bare `<tag>`/`</tag>`/`<!...>` outside code spans; GitHub would treat it as raw HTML and may drop it. */
const BARE_HTML = /<[A-Za-z/!?]/;

function capture() {
  const out = [];
  const err = [];
  return { out, err, log: (l) => out.push(l), logError: (l) => err.push(l) };
}

describe('release asset names', () => {
  test('client: jar (primary), mods bundle, Fabric API', () => {
    assert.deepEqual(releaseAssetNames('client', '1.0.0', TOOLCHAIN), CLIENT_FILES);
  });

  test('launcher before 1.4.0: msi (primary), exe, portable zip, per-platform jars and the Linux app image', () => {
    assert.deepEqual(releaseAssetNames('launcher', '1.0.0', TOOLCHAIN), LAUNCHER_FILES);
    assert.deepEqual(releaseAssetNames('launcher', '1.3.0', TOOLCHAIN), LAUNCHER_FILES.map((name) => name.replace('1.0.0', '1.3.0')));
  });

  test('launcher from 1.4.0 on: the .exe installer is gone, the .msi is the Windows installer, everything else unchanged', () => {
    assert.equal(LAUNCHER_EXE_DROPPED_FROM, '1.4.0');
    assert.deepEqual(releaseAssetNames('launcher', '1.4.0', TOOLCHAIN), LAUNCHER_FILES_140);
    assert.deepEqual(releaseAssetNames('launcher', '1.4.0-beta.1', TOOLCHAIN), LAUNCHER_FILES_140.map((name) => name.replace('1.4.0', '1.4.0-beta.1')), 'a 1.4.0 pre-release has no exe either (the workflow builds none)');
    assert.deepEqual(releaseAssetNames('launcher', '2.0.0', TOOLCHAIN), LAUNCHER_FILES_140.map((name) => name.replace('1.4.0', '2.0.0')));
    assert.ok(!releaseAssetNames('launcher', '1.4.1', TOOLCHAIN).some((name) => name.endsWith('.exe')));
    assert.ok(!releaseAssetNames('launcher', '1.5.0', TOOLCHAIN).some((name) => name.endsWith('.exe')));
    for (const [version, expected] of [['1.0.0', true], ['1.3.0', true], ['1.3.9', true], ['1.4.0-beta.1', false], ['1.4.0', false], ['1.4.1', false], ['1.10.0', false], ['2.0.0', false], ['0.9.0', true]]) {
      assert.equal(launcherHasExe(version), expected, version);
    }
    assert.throws(() => launcherHasExe('v1'), /not a SemVer/);
    assert.ok(!releaseAssets('launcher', '1.4.0', TOOLCHAIN).some((asset) => /\.exe;|as an \.exe/.test(asset.description)), 'no description mentions the exe installer');
    assert.match(releaseAssets('launcher', '1.4.0', TOOLCHAIN)[0].description, /Windows x64 installer/);
  });

  test('every asset has a description and only uses manifest-safe characters', () => {
    for (const product of ['client', 'launcher']) {
      for (const asset of releaseAssets(product, '2.3.4-beta.1', TOOLCHAIN)) {
        assert.match(asset.name, /^[A-Za-z0-9][A-Za-z0-9._+-]*$/);
        assert.ok(asset.name.includes('2.3.4-beta.1') || asset.name.startsWith('fabric-api-'), asset.name);
        assert.ok(asset.description.length > 20);
      }
    }
    const fat = releaseAssets('launcher', '1.0.0', TOOLCHAIN).filter((a) => a.name.endsWith('-all.jar'));
    assert.equal(fat.length, 3);
    for (const asset of fat) {
      assert.match(asset.description, /Needs Java 21 installed/);
      // The real file name in a code span, not a placeholder such as <file> that GitHub would strip as an HTML tag.
      assert.ok(asset.description.includes(`\`java -jar ${asset.name}\``), asset.description);
    }
    assert.doesNotMatch(fat.map((a) => a.description).join(' '), /every|all platforms|Windows, Linux and macOS/i, 'no jar claims to run everywhere');
  });

  test('descriptions are Markdown without bare HTML-like text and with balanced code spans', () => {
    for (const product of ['client', 'launcher']) {
      for (const asset of releaseAssets(product, '1.0.0', TOOLCHAIN)) {
        assert.equal((asset.description.match(/`/g) ?? []).length % 2, 0, `unbalanced backticks: ${asset.description}`);
        assert.doesNotMatch(outsideCodeSpans(asset.description), BARE_HTML, asset.description);
        assert.doesNotMatch(asset.description, /[\r\n|]/, asset.description);
      }
    }
  });

  test('extras, tags and download URLs', () => {
    assert.deepEqual(extraAssetNames('client', '1.0.0'), ['SHA256SUMS.txt', 'client-1.0.0.json']);
    assert.equal(releaseTag('launcher', '1.0.0'), 'launcher-v1.0.0');
    assert.equal(
      releaseDownloadUrl(REPO, 'client-v1.0.0', 'fabric-api-0.141.6+1.21.11.jar'),
      'https://github.com/LennardOwnTest123006/VANTA-Client/releases/download/client-v1.0.0/fabric-api-0.141.6+1.21.11.jar',
    );
    assert.throws(() => releaseDownloadUrl('not a repo', 't', 'f'), /owner\/repo/);
  });

  test('rejects unknown products and versions', () => {
    assert.throws(() => releaseAssets('website', '1.0.0', TOOLCHAIN), /product must be one of/);
    assert.throws(() => releaseAssets('client', 'v1', TOOLCHAIN), /not a SemVer/);
  });
});

describe('checkDirectory()', () => {
  test('requires exactly the expected non-empty files', () => {
    const dir = mkdtempSync(join(tmpdir(), 'vanta-assets-'));
    for (const name of CLIENT_FILES) writeFileSync(join(dir, name), 'x');
    assert.deepEqual(checkDirectory(dir, CLIENT_FILES), { ok: true, problems: [] });
    writeFileSync(join(dir, 'vanta-client-1.0.0-sources.jar'), 'x');
    writeFileSync(join(dir, CLIENT_FILES[1]), '');
    mkdirSync(join(dir, 'nested'));
    const result = checkDirectory(dir, [...CLIENT_FILES, 'SHA256SUMS.txt']);
    assert.equal(result.ok, false);
    assert.deepEqual(result.problems.sort(), [
      'empty: vanta-client-1.0.0-mods.zip',
      'missing: SHA256SUMS.txt',
      'unexpected directory: nested',
      'unexpected file: vanta-client-1.0.0-sources.jar',
    ]);
    assert.equal(checkDirectory(join(dir, 'nope'), CLIENT_FILES).ok, false);
  });
});

describe('checkManifest()', () => {
  test('the committed templates list exactly the release files in order', () => {
    const toolchain = readToolchain(REPO_ROOT);
    for (const product of ['client', 'launcher']) {
      const committed = JSON.parse(readFileSync(join(REPO_ROOT, 'shared', 'releases', `${product}-1.0.0.json`), 'utf8'));
      const result = checkManifest(committed, { toolchain });
      assert.deepEqual(result.problems, [], `${product}-1.0.0.json`);
      assert.deepEqual(result.expected, product === 'client' ? CLIENT_FILES : LAUNCHER_FILES);
    }
  });

  test('unpublished and fully published manifests pass; published mode checks every URL', () => {
    assert.equal(checkManifest(manifest('client', CLIENT_FILES), { toolchain: TOOLCHAIN }).ok, true);
    assert.equal(checkManifest(manifest('launcher', LAUNCHER_FILES, true), { toolchain: TOOLCHAIN }).ok, true);
    assert.equal(checkManifest(manifest('launcher', LAUNCHER_FILES, true), { toolchain: TOOLCHAIN, published: true, repo: REPO }).ok, true);
    const unpublished = checkManifest(manifest('client', CLIENT_FILES), { toolchain: TOOLCHAIN, published: true, repo: REPO });
    assert.equal(unpublished.ok, false);
    assert.equal(unpublished.problems.length, CLIENT_FILES.length * 2, 'empty values and wrong URL for every file');
    const otherTag = checkManifest(manifest('client', CLIENT_FILES, true, 'client-v0.9.0'), { toolchain: TOOLCHAIN, published: true, repo: REPO });
    assert.ok(otherTag.problems.every((p) => /expected https:\/\/github\.com\/.+\/client-v1\.0\.0\//.test(p)));
  });

  test('stale, reordered, missing and partially filled entries fail', () => {
    const stale = manifest('launcher', ['VANTA-Launcher-1.0.0.msi', 'VANTA-Launcher-1.0.0.exe', 'vanta-launcher-1.0.0-all.jar']);
    assert.match(checkManifest(stale, { toolchain: TOOLCHAIN }).problems[0], /files\[\] must list exactly/);
    const reordered = manifest('client', [CLIENT_FILES[1], CLIENT_FILES[0], CLIENT_FILES[2]]);
    assert.equal(checkManifest(reordered, { toolchain: TOOLCHAIN }).ok, false);
    const extra = manifest('client', [...CLIENT_FILES, 'vanta-client-1.0.0-sources.jar']);
    assert.equal(checkManifest(extra, { toolchain: TOOLCHAIN }).ok, false);
    const partial = manifest('client', CLIENT_FILES);
    partial.files[0].size = 42;
    assert.match(checkManifest(partial, { toolchain: TOOLCHAIN }).problems.join('\n'), /partially filled entry/);
  });

  test('the toolchain must match the repository', () => {
    const other = { ...manifest('client', CLIENT_FILES), fabricApiVersion: '0.140.0+1.21.11' };
    const result = checkManifest(other, { toolchain: TOOLCHAIN });
    assert.ok(result.problems.some((p) => /fabricApiVersion is "0\.140\.0\+1\.21\.11", the repository pins "0\.141\.6\+1\.21\.11"/.test(p)));
    assert.equal(checkManifest({ ...manifest('client', CLIENT_FILES), product: 'website' }, { toolchain: TOOLCHAIN }).ok, false);
    assert.equal(checkManifest(null, { toolchain: TOOLCHAIN }).ok, false);
  });
});

describe('notes', () => {
  test('markdownCell escapes HTML outside code spans only, and pipes and line breaks everywhere', () => {
    assert.equal(markdownCell('Needs Java 21 installed: java -jar <file>.'), 'Needs Java 21 installed: java -jar &lt;file&gt;.');
    assert.equal(markdownCell('run `java -jar <file>` & <b>go</b>'), 'run `java -jar <file>` &amp; &lt;b&gt;go&lt;/b&gt;');
    assert.equal(markdownCell('a | b `c | d`'), 'a \\| b `c \\| d`');
    assert.equal(markdownCell('one\ntwo\r\nthree'), 'one two three');
    assert.equal(markdownCell('dangling ` <x>'), 'dangling ` &lt;x&gt;', 'an unclosed backtick is not a code span');
    assert.equal(markdownCell('plain text'), 'plain text');
  });

  test('the release notes tables have no bare HTML-like text outside code spans (GitHub would strip it)', () => {
    for (const product of ['client', 'launcher']) {
      for (const version of ['1.0.0', '1.1.0-beta.1']) {
        const c = capture();
        assert.equal(main(['notes', '--product', product, '--version', version], c.log, c.logError), 0);
        const table = c.out.join('\n');
        for (const line of table.split('\n')) assert.doesNotMatch(outsideCodeSpans(line), BARE_HTML, line);
        for (const name of releaseAssetNames(product, version, readToolchain(REPO_ROOT))) {
          assert.ok(table.includes(`| \`${name}\` |`), `${name} row`);
        }
      }
    }
    const launcher = assetsMarkdown(releaseAssets('launcher', '1.0.0', TOOLCHAIN));
    assert.match(launcher, /\| `vanta-launcher-1\.0\.0-windows-all\.jar` \| Single jar with JavaFX for Windows x64\. Needs Java 21 installed: `java -jar vanta-launcher-1\.0\.0-windows-all\.jar`\. \|/);
    assert.match(launcher, /Needs Java 21 installed: `java -jar vanta-launcher-1\.0\.0-linux-all\.jar`\./);
    assert.match(launcher, /Needs Java 21 installed: `java -jar vanta-launcher-1\.0\.0-macos-aarch64-all\.jar`\./);
    assert.doesNotMatch(launcher, /<file>/);
  });

  test('markdown table with and without sizes', () => {
    const assets = releaseAssets('client', '1.0.0', TOOLCHAIN);
    const plain = assetsMarkdown(assets);
    assert.match(plain, /^\| File \| What it is \|\n\| --- \| --- \|\n\| `vanta-client-1\.0\.0\.jar` \|/);
    const dir = mkdtempSync(join(tmpdir(), 'vanta-notes-'));
    writeFileSync(join(dir, CLIENT_FILES[0]), Buffer.alloc(1437322));
    writeFileSync(join(dir, CLIENT_FILES[1]), Buffer.alloc(3794024));
    writeFileSync(join(dir, CLIENT_FILES[2]), Buffer.alloc(2426039));
    const sized = assetsMarkdown(assets, dir);
    assert.match(sized, /\| `vanta-client-1\.0\.0\.jar` \| 1\.4 MB \|/);
    assert.match(sized, /\| `vanta-client-1\.0\.0-mods\.zip` \| 3\.8 MB \|/, 'decimal MB: 3,794,024 bytes');
    assert.match(sized, new RegExp(`\\| \`${CLIENT_FILES[2].replace(/[.+]/g, '\\$&')}\` \\| 2\\.4 MB \\|`));
  });

  test('formatSize uses decimal units with one decimal place, like the website', () => {
    assert.equal(formatSize(0), '0 B');
    assert.equal(formatSize(10), '10 B');
    assert.equal(formatSize(999), '999 B');
    assert.equal(formatSize(1000), '1.0 kB');
    assert.equal(formatSize(1024), '1.0 kB');
    assert.equal(formatSize(512_400), '512.4 kB');
    assert.equal(formatSize(1_000_000), '1.0 MB');
    assert.equal(formatSize(1_048_576), '1.0 MB');
    assert.equal(formatSize(1_437_322), '1.4 MB');
    assert.equal(formatSize(66_900_000), '66.9 MB');
    assert.equal(formatSize(70_160_000), '70.2 MB', 'decimal, not 66.9 MiB');
    assert.equal(formatSize(2_500_000_000), '2.5 GB');
    assert.throws(() => formatSize(-1), /not a byte count/);
    assert.throws(() => formatSize(Number.NaN), /not a byte count/);
    assert.throws(() => formatSize(1.5), /not a byte count/);
  });

  // The same vectors belong in website/src/lib/format.test.ts (formatBytes) and the launcher's ByteSizesTest, so a size
  // reads the same in the release notes, on the download page and in the launcher.
  test('formatSize rounds half up in integer arithmetic and never prints 1000.0 of a unit', () => {
    const vectors = [
      [1_049, '1.0 kB'],
      [1_050, '1.1 kB'],
      [1_150, '1.2 kB'], // 1.15 is 1.149999... as a double; toFixed(1) would print 1.1
      [1_450_000, '1.5 MB'], // 1.45 is 1.4499999... as a double; toFixed(1) would print 1.4
      [2_450_000, '2.5 MB'],
      [999_949, '999.9 kB'],
      [999_950, '1.0 MB'], // not "1000.0 kB"
      [999_999, '1.0 MB'],
      [1_000_000, '1.0 MB'],
      [1_437_322, '1.4 MB'],
      [66_900_000, '66.9 MB'],
      [999_949_999, '999.9 MB'],
      [999_950_000, '1.0 GB'],
      [999_950_000_000, '1.0 TB'],
      [1_000_000_000_000_000, '1000.0 TB'], // largest unit: no unit to move up to
      [Number.MAX_SAFE_INTEGER, '9007.2 TB'],
    ];
    for (const [bytes, expected] of vectors) assert.equal(formatSize(bytes), expected, `${bytes} bytes`);
  });
});

describe('CLI', () => {
  test('list prints the names in order', () => {
    const c = capture();
    assert.equal(main(['list', '--product', 'launcher', '--version', '1.0.0'], c.log, c.logError), 0);
    assert.deepEqual(c.out, LAUNCHER_FILES);
  });

  test('check-dir with and without extras', () => {
    const dir = mkdtempSync(join(tmpdir(), 'vanta-assets-cli-'));
    for (const name of CLIENT_FILES) writeFileSync(join(dir, name), 'x');
    const c = capture();
    assert.equal(main(['check-dir', dir, '--product', 'client', '--version', '1.0.0'], c.log, c.logError), 0);
    assert.equal(main(['check-dir', dir, '--product', 'client', '--version', '1.0.0', '--with-extras'], c.log, c.logError), 1);
    assert.match(c.err.join('\n'), /missing: SHA256SUMS\.txt/);
    writeFileSync(join(dir, 'SHA256SUMS.txt'), 'x');
    writeFileSync(join(dir, 'client-1.0.0.json'), '{}');
    assert.equal(main(['check-dir', dir, '--product', 'client', '--version', '1.0.0', '--with-extras'], c.log, c.logError), 0);
  });

  test('a 1.4.0 launcher manifest must not list the exe; an older one must', () => {
    const modern = { ...manifest('launcher', LAUNCHER_FILES_140), version: '1.4.0' };
    assert.deepEqual(checkManifest(modern, { toolchain: TOOLCHAIN }).problems, []);
    const withExe = { ...manifest('launcher', LAUNCHER_FILES.map((name) => name.replace('1.0.0', '1.4.0'))), version: '1.4.0' };
    assert.match(checkManifest(withExe, { toolchain: TOOLCHAIN }).problems[0], /files\[\] must list exactly .*VANTA-Launcher-1\.4\.0\.msi","VANTA-Launcher-1\.4\.0-windows-portable\.zip"/);
    const oldWithout = manifest('launcher', LAUNCHER_FILES.filter((name) => !name.endsWith('.exe')));
    assert.equal(checkManifest(oldWithout, { toolchain: TOOLCHAIN }).ok, false, 'the 1.0.0 release did ship the exe');
    // Every committed launcher manifest (all before 1.4.0) still passes.
    const toolchain = readToolchain(REPO_ROOT);
    for (const file of readdirSync(join(REPO_ROOT, 'shared', 'releases')).filter((name) => /^launcher-\d.*\.json$/.test(name))) {
      const committed = JSON.parse(readFileSync(join(REPO_ROOT, 'shared', 'releases', file), 'utf8'));
      assert.deepEqual(checkManifest(committed, { toolchain }).problems, [], file);
    }
  });

  test('check-manifest exit codes', () => {
    const dir = mkdtempSync(join(tmpdir(), 'vanta-assets-manifest-'));
    const path = join(dir, 'client-1.0.0.json');
    writeFileSync(path, JSON.stringify(manifest('client', CLIENT_FILES, true)));
    const c = capture();
    assert.equal(main(['check-manifest', path, '--published', '--repo', REPO, '--tag', 'client-v1.0.0'], c.log, c.logError), 0);
    assert.match(c.out.join('\n'), /\(all published\)/);
    assert.equal(main(['check-manifest', path, '--published', '--repo', 'other/repo'], c.log, c.logError), 1);
    writeFileSync(path, '{ not json');
    assert.equal(main(['check-manifest', path], c.log, c.logError), 2);
  });

  test('usage errors exit 2', () => {
    const c = capture();
    assert.equal(main([], c.log, c.logError), 2);
    assert.equal(main(['explode'], c.log, c.logError), 2);
    assert.equal(main(['list', '--product', 'client'], c.log, c.logError), 2);
    assert.equal(main(['list', '--product', 'client', '--version', '1.0.0', 'extra'], c.log, c.logError), 2);
    assert.equal(main(['check-dir', '--product', 'client', '--version', '1.0.0'], c.log, c.logError), 2);
    assert.equal(main(['list', '--product', 'client', '--version', '1.0.0', '--published'], c.log, c.logError), 2);
    assert.equal(main(['--help'], c.log, c.logError), 0);
  });

  test('notes prints a table', () => {
    const c = capture();
    assert.equal(main(['notes', '--product', 'client', '--version', '1.0.0'], c.log, c.logError), 0);
    assert.match(c.out.join('\n'), /fabric-api-0\.141\.6\+1\.21\.11\.jar/);
  });
});
