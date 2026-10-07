import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
  API_BASE, Client, DEFAULT_TIMEOUT_MS, FABRIC_API_PROJECT_ID, LICENSE_ALLOW_LIST, PackError, POLYFORM_SHIELD_ID,
  POLYFORM_SHIELD_URL, SPDX_TEXT_BASE, buildPack, downloadItem, fabricApiNotice, isSafeFilename, licenseTextUrls,
  looksLikeHtml, main, parsePackSlugs, rawLicenseUrl, readPackSlugs, resolvePack, selectVersion, userAgent,
} from './performance-pack.mjs';
import { REPO_ROOT } from './lib/repo.mjs';
import { buildZip } from './lib/zip-writer.mjs';
import { validateWithSchemaFile } from './validate-json.mjs';

const GAME = '1.21.11';
const SLUGS = ['sodium', 'lithium', 'ferrite-core', 'immediatelyfast', 'entityculling', 'iris'];
const CDN = 'https://cdn.modrinth.com/data/';
const SCHEMA = join(REPO_ROOT, 'shared', 'schemas', 'performance-pack.schema.json');

const sha512 = (bytes) => createHash('sha512').update(bytes).digest('hex');
const hasJq = () => spawnSync('jq', ['--version'], { encoding: 'utf8' }).status === 0;
const tmp = () => mkdtempSync(join(tmpdir(), 'vanta-performance-pack-'));

/** One version document in the shape of GET /v2/project/<id>/version. */
function version({ id, projectId, number, type = 'release', date, bytes, filename, deps = [], game = [GAME], loaders = ['fabric'], sha = sha512(bytes), size = bytes.length }) {
  return {
    id, project_id: projectId, version_number: number, version_type: type, date_published: date,
    game_versions: game, loaders, dependencies: deps,
    files: [{ url: `${CDN}${projectId}/versions/${id}/${filename}`, filename, primary: true, size, hashes: { sha512: sha, sha1: '' } }],
  };
}

/** The six pack members as Modrinth would describe them today, with fake versions and jar bytes. */
function fixture({ irisRequires = 'SODIUMV9', entityLicense = { id: 'LicenseRef-tr7zw-Protective-License', name: 'tr7zw Protective License', url: 'https://github.com/tr7zw/EntityCulling/blob/1.21/LICENSE' } } = {}) {
  const jar = (slug) => Buffer.from(`fake jar bytes of ${slug} ${'x'.repeat(40)}`);
  const projects = {
    sodium: { id: 'AANobbMI', slug: 'sodium', title: 'Sodium', source_url: 'https://github.com/CaffeineMC/sodium', license: { id: POLYFORM_SHIELD_ID, name: 'PolyForm Shield License 1.0.0', url: 'https://github.com/CaffeineMC/sodium/blob/dev/LICENSE.md' } },
    lithium: { id: 'gvQqBUqZ', slug: 'lithium', title: 'Lithium', source_url: 'https://github.com/CaffeineMC/lithium-fabric', license: { id: 'LGPL-3.0-only', name: 'GNU Lesser General Public License v3.0 only', url: null } },
    'ferrite-core': { id: 'uXXizFIs', slug: 'ferrite-core', title: 'FerriteCore', source_url: 'https://github.com/malte0811/FerriteCore', license: { id: 'MIT', name: 'MIT License', url: null } },
    immediatelyfast: { id: '5ZwdcRci', slug: 'immediatelyfast', title: 'ImmediatelyFast', source_url: 'https://github.com/RaphiMC/ImmediatelyFast', license: { id: 'LGPL-3.0-or-later', name: 'GNU Lesser General Public License v3.0 or later', url: 'https://github.com/RaphiMC/ImmediatelyFast/blob/main/LICENSE' } },
    entityculling: { id: 'NNAgCjsB', slug: 'entityculling', title: 'EntityCulling', source_url: 'https://github.com/tr7zw/EntityCulling', license: entityLicense },
    iris: { id: 'YL57xq9U', slug: 'iris', title: 'Iris Shaders', source_url: 'https://github.com/IrisShaders/Iris', license: { id: 'LGPL-3.0-only', name: 'GNU Lesser General Public License v3.0 only', url: null } },
  };
  const jars = Object.fromEntries(SLUGS.map((s) => [s, jar(s)]));
  const versions = {
    sodium: [
      version({ id: 'SODIUMV9', projectId: 'AANobbMI', number: '9.9.9-test', date: '2026-09-20T00:00:00Z', bytes: jars.sodium, filename: 'sodium-fabric-9.9.9-test.jar', deps: [{ version_id: null, project_id: FABRIC_API_PROJECT_ID, dependency_type: 'required' }] }),
      version({ id: 'SODIUMV8', projectId: 'AANobbMI', number: '9.9.8-test', date: '2026-08-01T00:00:00Z', bytes: Buffer.from('older sodium'), filename: 'sodium-fabric-9.9.8-test.jar' }),
    ],
    lithium: [version({ id: 'LITHIUMV1', projectId: 'gvQqBUqZ', number: '7.7.7-test', date: '2026-09-21T00:00:00Z', bytes: jars.lithium, filename: 'lithium-fabric-7.7.7-test.jar' })],
    'ferrite-core': [version({ id: 'FERRITEV1', projectId: 'uXXizFIs', number: '5.5.5-test', date: '2026-09-22T00:00:00Z', bytes: jars['ferrite-core'], filename: 'ferritecore-5.5.5-test-fabric.jar' })],
    immediatelyfast: [version({ id: 'IMMFASTV1', projectId: '5ZwdcRci', number: '3.3.3-test', date: '2026-09-23T00:00:00Z', bytes: jars.immediatelyfast, filename: 'ImmediatelyFast-Fabric-3.3.3-test.jar' })],
    entityculling: [version({ id: 'ENTCULLV1', projectId: 'NNAgCjsB', number: '2.2.2-test', date: '2026-09-24T00:00:00Z', bytes: jars.entityculling, filename: 'entityculling-fabric-2.2.2-test.jar' })],
    iris: [version({ id: 'IRISV1', projectId: 'YL57xq9U', number: '4.4.4-test', date: '2026-09-25T00:00:00Z', bytes: jars.iris, filename: 'iris-fabric-4.4.4-test.jar', deps: [{ version_id: irisRequires, project_id: 'AANobbMI', dependency_type: 'required' }, { version_id: null, project_id: FABRIC_API_PROJECT_ID, dependency_type: 'required' }] })],
  };
  const texts = {
    'https://raw.githubusercontent.com/CaffeineMC/sodium/dev/LICENSE.md': '# PolyForm Shield License 1.0.0\n\nfake sodium licence text',
    [`${SPDX_TEXT_BASE}LGPL-3.0-only.txt`]: 'GNU LESSER GENERAL PUBLIC LICENSE Version 3 (fake)',
    [`${SPDX_TEXT_BASE}GPL-3.0-only.txt`]: 'GNU GENERAL PUBLIC LICENSE Version 3 (fake)',
    [`${SPDX_TEXT_BASE}MIT.txt`]: 'MIT License (fake)',
    'https://raw.githubusercontent.com/RaphiMC/ImmediatelyFast/main/LICENSE': 'LGPL text of ImmediatelyFast (fake)',
  };
  const members = {
    AANobbMI: [{ role: 'Member', user: { username: 'other', name: null } }, { role: 'Owner', user: { username: 'jellysquid3', name: 'JellySquid' } }],
  };
  return { projects, versions, jars, texts, members };
}

/** A fetch over the fixture; `overrides` maps a URL to a Response factory, `calls` records every request. */
function fakeFetch(f, overrides = {}) {
  const calls = [];
  const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'content-type': 'application/json' } });
  const fetch = async (url, init) => {
    calls.push({ url, ua: init?.headers?.['User-Agent'] });
    if (overrides[url]) return overrides[url]();
    let m;
    if ((m = /^https:\/\/api\.modrinth\.com\/v2\/project\/([^/?]+)$/.exec(url))) {
      return f.projects[m[1]] ? json(f.projects[m[1]]) : json({ error: 'not_found' }, 404);
    }
    if ((m = /^https:\/\/api\.modrinth\.com\/v2\/project\/([^/?]+)\/members$/.exec(url))) {
      return f.members[m[1]] ? json(f.members[m[1]]) : json({ error: 'not_found' }, 404);
    }
    if ((m = /^https:\/\/api\.modrinth\.com\/v2\/project\/([^/?]+)\/version\?loaders=%5B%22fabric%22%5D&game_versions=%5B%22([^%]+)%22%5D$/.exec(url))) {
      const slug = Object.keys(f.projects).find((s) => f.projects[s].id === m[1]);
      const list = (f.versions[slug] ?? []).filter((v) => v.game_versions.includes(decodeURIComponent(m[2])));
      return json(list);
    }
    if ((m = /^https:\/\/api\.modrinth\.com\/v2\/version\/([^/?]+)$/.exec(url))) {
      for (const list of Object.values(f.versions)) for (const v of list) if (v.id === m[1]) return json(v);
      return json({ error: 'not_found' }, 404);
    }
    if (url.startsWith(CDN)) {
      for (const list of Object.values(f.versions)) {
        for (const v of list) {
          if (v.files[0].url === url) {
            const slug = Object.keys(f.projects).find((s) => f.projects[s].id === v.project_id);
            const bytes = v.id.endsWith('V8') ? Buffer.from('older sodium') : f.jars[slug];
            return new Response(bytes, { status: 200, headers: { 'content-type': 'application/java-archive' } });
          }
        }
      }
      return new Response('no such file', { status: 404 });
    }
    if (f.texts[url] !== undefined) return new Response(f.texts[url], { status: 200, headers: { 'content-type': 'text/plain' } });
    return new Response('not found', { status: 404 });
  };
  return { fetch, calls };
}

const client = (fetch) => new Client({ fetch, userAgent: 'test-agent', delayMs: 0 });

function fabricApiJar(dir) {
  const path = join(dir, 'fabric-api-0.0.0-test.jar');
  writeFileSync(path, buildZip([
    { name: 'fabric.mod.json', data: JSON.stringify({ schemaVersion: 1, id: 'fabric-api', version: '0.0.0-test', license: 'Apache-2.0' }) },
    { name: 'LICENSE-fabric-api', data: 'Apache License 2.0 (fake Fabric API notice)' },
  ]));
  return path;
}

describe('pack members', () => {
  test('parsePackSlugs() finds all six members of PerformancePack.java in pack order', () => {
    assert.deepEqual(readPackSlugs(REPO_ROOT), SLUGS);
    const source = readFileSync(join(REPO_ROOT, 'core', 'src', 'main', 'java', 'dev', 'vanta', 'core', 'modrinth', 'PerformancePack.java'), 'utf8');
    assert.equal(parsePackSlugs(source).length, 6);
    assert.throws(() => parsePackSlugs('class X {}'), /no `new Item\("<slug>"` lines/);
    assert.throws(() => parsePackSlugs('new Item("a", new Item("a"'), /duplicate slug/);
  });

  test('the User-Agent names the client version from client/gradle.properties and the repository', () => {
    assert.match(userAgent(REPO_ROOT), /^LennardOwnTest123006\/VANTA-Client\/\S+ \(https:\/\/github\.com\/LennardOwnTest123006\/VANTA-Client\)$/);
  });

  test('the licence allow-list has exactly the agreed entries', () => {
    assert.deepEqual([...LICENSE_ALLOW_LIST].sort(), ['Apache-2.0', 'BSD-2-Clause', 'BSD-3-Clause', 'LGPL-2.1-or-later', 'LGPL-3.0-only', 'LGPL-3.0-or-later', POLYFORM_SHIELD_ID, 'MIT'].sort());
    assert.ok(!LICENSE_ALLOW_LIST.has('LicenseRef-tr7zw-Protective-License'));
  });
});

describe('selectVersion()', () => {
  const v = (id, type, date, extra = {}) => version({ id, projectId: 'P', number: id, type, date, bytes: Buffer.from(id), filename: `${id}.jar`, ...extra });

  test('newest release wins over a newer beta; beta over alpha; newest of any type otherwise', () => {
    const release = v('r1', 'release', '2026-01-01T00:00:00Z');
    const olderRelease = v('r0', 'release', '2025-12-01T00:00:00Z');
    const beta = v('b1', 'beta', '2026-02-01T00:00:00Z');
    const alpha = v('a1', 'alpha', '2026-03-01T00:00:00Z');
    assert.equal(selectVersion([alpha, beta, olderRelease, release], GAME).id, 'r1');
    assert.equal(selectVersion([alpha, beta], GAME).id, 'b1');
    assert.equal(selectVersion([alpha, v('a0', 'alpha', '2026-02-15T00:00:00Z')], GAME).id, 'a1');
    assert.equal(selectVersion([], GAME), null);
  });

  test('ignores versions for other game versions or loaders and files without a SHA-512 or with an unsafe name', () => {
    assert.equal(selectVersion([v('x', 'release', '2026-01-01T00:00:00Z', { game: ['1.21.10'] })], GAME), null);
    assert.equal(selectVersion([v('x', 'release', '2026-01-01T00:00:00Z', { loaders: ['neoforge'] })], GAME), null);
    assert.equal(selectVersion([v('x', 'release', '2026-01-01T00:00:00Z', { sha: '' })], GAME), null);
    assert.equal(selectVersion([v('x', 'release', '2026-01-01T00:00:00Z', { filename: '../evil.jar' })], GAME), null);
    assert.equal(selectVersion([v('ok', 'release', '2026-01-01T00:00:00Z', { loaders: ['Fabric', 'quilt'] })], GAME).id, 'ok');
  });

  test('isSafeFilename() mirrors the core rule', () => {
    assert.ok(isSafeFilename('sodium-fabric-0.0.0.jar'));
    assert.ok(isSafeFilename('Iris Shaders 4.4.4.jar'));
    assert.ok(isSafeFilename('ferritecore-8.0.0 (fabric).jar'));
    for (const bad of ['', '.hidden.jar', 'a/b.jar', 'a\\b.jar', 'a:b.jar', 'a..b.jar', 'a?b.jar', `${'x'.repeat(201)}.jar`]) assert.ok(!isSafeFilename(bad), bad);
  });

  describe('scripts/ci/pick-version.jq (the CI game test) agrees with selectVersion()', { skip: hasJq() ? false : 'needs jq' }, () => {
    const JQ = join(REPO_ROOT, 'scripts', 'ci', 'pick-version.jq');
    const pick = (versions, game = GAME) => {
      const r = spawnSync('jq', ['-r', '--arg', 'game', game, '-f', JQ], { input: JSON.stringify(versions), encoding: 'utf8' });
      assert.equal(r.status, 0, r.stderr);
      return r.stdout.trim() === '' ? null : r.stdout.trim();
    };
    const expected = (versions, game = GAME) => {
      const best = selectVersion(versions, game);
      return best === null ? null : `${best.id} ${best.version_number}`;
    };
    const check = (versions, game = GAME) => assert.equal(pick(versions, game), expected(versions, game));

    test('channel and date ranking', () => {
      const release = v('r1', 'release', '2026-01-01T00:00:00Z');
      const olderRelease = v('r0', 'release', '2025-12-01T00:00:00Z');
      const beta = v('b1', 'beta', '2026-02-01T00:00:00Z');
      const alpha = v('a1', 'alpha', '2026-03-01T00:00:00Z');
      assert.equal(pick([alpha, beta, olderRelease, release]), 'r1 r1');
      check([alpha, beta, olderRelease, release]);
      check([alpha, beta]);
      check([alpha, v('a0', 'alpha', '2026-02-15T00:00:00Z')]);
      check([]);
    });

    test('skips the newest version when its primary file lacks a SHA-512 or has an unsafe name', () => {
      const sha = sha512(Buffer.from('ok'));
      const old = v('old-ok', 'release', '2026-03-01T00:00:00Z', { filename: 'Mod Name (fabric) 0.6.0.jar' });
      const noHash = v('new-nohash', 'release', '2026-03-02T00:00:00Z', { sha: '' });
      const sha1Only = { ...v('new-sha1', 'release', '2026-03-02T06:00:00Z'), files: [{ filename: 'mod.jar', primary: true, hashes: { sha1: 'abc' } }] };
      const shortHash = v('new-short', 'release', '2026-03-02T12:00:00Z', { sha: 'abc' });
      const traversal = v('new-traversal', 'release', '2026-03-03T00:00:00Z', { filename: '../mod.jar' });
      const hidden = v('new-hidden', 'release', '2026-03-03T06:00:00Z', { filename: '.mod.jar' });
      const control = v('new-control', 'release', '2026-03-03T12:00:00Z', { filename: 'mod\t0.6.4.jar' });
      const colon = v('new-colon', 'release', '2026-03-04T00:00:00Z', { filename: 'mod:0.6.4.jar' });
      const tooLong = v('new-long', 'release', '2026-03-04T06:00:00Z', { filename: `${'x'.repeat(197)}.jar` });
      const noFiles = { ...v('new-nofiles', 'release', '2026-03-04T12:00:00Z'), files: [] };
      const all = [old, noHash, sha1Only, shortHash, traversal, hidden, control, colon, tooLong, noFiles];
      assert.equal(pick(all), 'old-ok old-ok');
      check(all);
      for (const bad of all.slice(1)) {
        assert.equal(pick([bad]), null, bad.id);
        check([bad]);
      }
      // The first file counts when none is marked primary; a non-primary unsafe file does not matter.
      const unmarked = { ...v('unmarked', 'release', '2026-03-05T00:00:00Z'), files: [{ filename: 'first.jar', primary: false, hashes: { sha512: sha } }] };
      const extra = { ...v('extra', 'release', '2026-03-06T00:00:00Z'), files: [{ filename: 'sources.jar', primary: false, hashes: {} }, { filename: 'main.jar', primary: true, hashes: { sha512: sha } }] };
      assert.equal(pick([unmarked]), 'unmarked unmarked');
      assert.equal(pick([extra, unmarked]), 'extra extra');
      check([extra, unmarked]);
      // A beta is taken over a newer release that is not installable, and a newer beta over an alpha.
      check([v('b1', 'beta', '2026-02-01T00:00:00Z'), noHash, v('a1', 'alpha', '2026-03-01T00:00:00Z')]);
      assert.equal(pick([v('b1', 'beta', '2026-02-01T00:00:00Z'), noHash]), 'b1 b1');
    });

    test('game version and loader filter', () => {
      check([v('x', 'release', '2026-01-01T00:00:00Z', { game: ['1.21.10'] })]);
      check([v('x', 'release', '2026-01-01T00:00:00Z', { loaders: ['neoforge'] })]);
      check([v('ok', 'release', '2026-01-01T00:00:00Z', { loaders: ['Fabric', 'quilt'] })]);
      assert.equal(pick([v('ok', 'release', '2026-01-01T00:00:00Z', { loaders: ['Fabric', 'quilt'] })]), 'ok ok');
      assert.equal(pick([v('x', 'release', '2026-01-01T00:00:00Z')], '1.21.10'), null);
      assert.equal(pick([{ id: 'broken', version_number: '1', version_type: 'release', date_published: '2026-01-01T00:00:00Z' }]), null);
    });

    test('file names are measured and trimmed the way Java and JavaScript do (UTF-16 units, blank names, DEL)', () => {
      const sha = sha512(Buffer.from('ok'));
      const at = (id, filename, date = '2026-01-01T00:00:00Z') => v(id, 'release', date, { filename, sha });
      // 150 emoji are 150 code points (jq length) but 300 UTF-16 units (String.length), over the 200 limit; 97 fit.
      check([at('emoji-long', `${'😀'.repeat(150)}.jar`)]);
      assert.equal(pick([at('emoji-long', `${'😀'.repeat(150)}.jar`)]), null);
      check([at('emoji-ok', `${'😀'.repeat(97)}.jar`)]);
      assert.equal(pick([at('emoji-ok', `${'😀'.repeat(97)}.jar`)]), 'emoji-ok emoji-ok');
      check([at('unicode', 'Mod-ä-日本-0.1.jar')]);
      assert.equal(pick([at('unicode', 'Mod-ä-日本-0.1.jar')]), 'unicode unicode');
      for (const bad of ['   ', '', 'mod\x7f.jar', 'mod\u0000.jar', 'mod.jar\n']) {
        check([at('bad', bad)]);
        assert.equal(pick([at('bad', bad)]), null, JSON.stringify(bad));
      }
      // A SHA-512 followed by a line break is not a SHA-512 ($ must not match before the newline).
      check([v('nl', 'release', '2026-01-01T00:00:00Z', { sha: `${sha}\n` })]);
      assert.equal(pick([v('nl', 'release', '2026-01-01T00:00:00Z', { sha: `${sha}\n` })]), null);
    });

    test('version_type is compared trimmed and case-insensitively, an unknown type ranks after alpha', () => {
      const capital = v('R', 'Release', '2026-01-01T00:00:00Z');
      const padded = v('P', ' beta ', '2026-01-15T00:00:00Z');
      const beta = v('b', 'beta', '2026-02-01T00:00:00Z');
      const alpha = v('a', 'alpha', '2026-01-01T00:00:00Z');
      const weird = v('x', 'weird', '2026-02-01T00:00:00Z');
      const untyped = { ...v('u', 'release', '2026-03-01T00:00:00Z'), version_type: null };
      check([capital, beta]);
      assert.equal(pick([capital, beta]), 'R R');
      check([padded, alpha]);
      assert.equal(pick([padded, alpha]), 'P P');
      check([alpha, weird]);
      assert.equal(pick([alpha, weird]), 'a a');
      check([weird, untyped]);
      assert.equal(pick([weird, untyped]), 'u u');
    });

    test('date_published is compared as a time, so a fractional second and a whole second order correctly', () => {
      const whole = v('p', 'release', '2026-01-01T00:00:00Z');
      const fraction = v('q', 'release', '2026-01-01T00:00:00.500Z');
      const micro = v('m', 'release', '2026-01-01T00:00:00.123456Z');
      check([whole, fraction, micro]);
      assert.equal(pick([whole, fraction, micro]), 'q q');
      check([fraction, whole]);
      check([micro, whole]);
      assert.equal(pick([micro, whole]), 'm m');
      check([v('old', 'release', '2025-12-31T23:59:59.999999Z'), whole]);
      assert.equal(pick([v('old', 'release', '2025-12-31T23:59:59.999999Z'), whole]), 'p p');
      // A non-string loader entry is ignored, not an error.
      check([v('l', 'release', '2026-01-01T00:00:00Z', { loaders: [1, 'fabric'] })]);
      assert.equal(pick([v('l', 'release', '2026-01-01T00:00:00Z', { loaders: [1, 'fabric'] })]), 'l l');
    });

    test('ci.yml runs this file with the game version as $game', () => {
      const workflow = readFileSync(join(REPO_ROOT, '.github', 'workflows', 'ci.yml'), 'utf8');
      assert.match(workflow, /jq -r --arg game 1\.21\.11 -f scripts\/ci\/pick-version\.jq <<< "\$JSON"/);
      assert.doesNotMatch(workflow, /sort_by\(\.date_published\)/, 'the rule lives only in pick-version.jq');
    });
  });
});

describe('licence texts', () => {
  test('rawLicenseUrl() rewrites GitHub blob links and leaves other URLs alone', () => {
    assert.equal(rawLicenseUrl('https://github.com/CaffeineMC/sodium/blob/dev/LICENSE.md'), 'https://raw.githubusercontent.com/CaffeineMC/sodium/dev/LICENSE.md');
    assert.equal(rawLicenseUrl('https://www.gnu.org/licenses/lgpl-3.0.txt'), 'https://www.gnu.org/licenses/lgpl-3.0.txt');
    assert.equal(rawLicenseUrl(null), null);
  });

  test('licenseTextUrls() uses the project URL first, else the SPDX text, and appends the GPL for the LGPL', () => {
    assert.deepEqual(licenseTextUrls({ id: 'MIT', url: null }), [{ label: 'MIT', url: `${SPDX_TEXT_BASE}MIT.txt` }]);
    assert.deepEqual(licenseTextUrls({ id: 'LGPL-3.0-only', url: null }), [
      { label: 'LGPL-3.0-only', url: `${SPDX_TEXT_BASE}LGPL-3.0-only.txt` },
      { label: 'GPL-3.0-only', url: `${SPDX_TEXT_BASE}GPL-3.0-only.txt` },
    ]);
    assert.deepEqual(licenseTextUrls({ id: 'LGPL-3.0-or-later', url: 'https://github.com/o/r/blob/main/LICENSE' }), [
      { label: 'LGPL-3.0-or-later', url: 'https://raw.githubusercontent.com/o/r/main/LICENSE' },
      { label: 'GPL-3.0-only', url: `${SPDX_TEXT_BASE}GPL-3.0-only.txt` },
    ]);
    assert.deepEqual(licenseTextUrls({ id: 'LGPL-2.1-or-later', url: null }), [
      { label: 'LGPL-2.1-or-later', url: `${SPDX_TEXT_BASE}LGPL-2.1-or-later.txt` },
      { label: 'GPL-2.0-only', url: `${SPDX_TEXT_BASE}GPL-2.0-only.txt` },
    ]);
    assert.deepEqual(licenseTextUrls({ id: 'LicenseRef-Custom', url: null }), []);
  });
});

describe('resolvePack()', () => {
  test('bundles five members, excludes EntityCulling by licence with a reason and records the Iris -> Sodium pin', async () => {
    const f = fixture();
    const { fetch, calls } = fakeFetch(f);
    const log = [];
    const result = await resolvePack({ slugs: SLUGS, gameVersion: GAME, client: client(fetch), log: (l) => log.push(l) });
    assert.deepEqual(result.items.map((i) => i.slug), ['sodium', 'lithium', 'ferrite-core', 'immediatelyfast', 'iris']);
    assert.deepEqual(result.excluded.map((e) => [e.slug, e.title, e.license]), [['entityculling', 'EntityCulling', 'LicenseRef-tr7zw-Protective-License']]);
    assert.match(result.excluded[0].reason, /not in the redistribution allow-list/);
    assert.ok(log.some((l) => /excluded from the bundle: EntityCulling/.test(l)), log.join('\n'));
    assert.equal(result.items[0].versionId, 'SODIUMV9', 'newest Sodium release');
    assert.equal(result.items[0].sha512, sha512(f.jars.sodium));
    assert.deepEqual(result.items[0].authors, ['JellySquid', 'other'], 'owner first');
    assert.deepEqual(result.items[1].authors, [], 'missing team is not fatal');
    assert.ok(result.notes.some((n) => /Iris Shaders 4\.4\.4-test requires Sodium version SODIUMV9 \(9\.9\.9-test\); the bundled Sodium is exactly that version/.test(n)), result.notes.join('\n'));
    assert.ok(result.notes.some((n) => /Fabric API \(Apache-2\.0\) is in the bundle/.test(n)));
    assert.ok(calls.every((c) => c.ua === 'test-agent'), 'every request carries the User-Agent');
    assert.ok(!calls.some((c) => c.url.includes('/project/NNAgCjsB/version')), 'no version lookup for an excluded member');
    assert.ok(calls.some((c) => c.url === `${API_BASE}project/AANobbMI/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.11%22%5D`));
  });

  test('fails clearly when Iris requires a Sodium version other than the newest one', async () => {
    const { fetch } = fakeFetch(fixture({ irisRequires: 'SODIUMV8' }));
    await assert.rejects(resolvePack({ slugs: SLUGS, gameVersion: GAME, client: client(fetch) }), (error) => {
      assert.ok(error instanceof PackError);
      assert.match(error.message, /Iris Shaders 4\.4\.4-test requires Sodium version SODIUMV8 \(9\.9\.8-test\), but the newest Sodium for Minecraft 1\.21\.11 is 9\.9\.9-test \(SODIUMV9\)/);
      return true;
    });
  });

  test('fails when a member requires a project outside the pack or one excluded by licence', async () => {
    const f = fixture();
    f.versions.iris[0].dependencies.push({ version_id: null, project_id: 'OUTSIDE1', dependency_type: 'required' });
    await assert.rejects(resolvePack({ slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(f).fetch) }), /requires project OUTSIDE1, which is not part of the Performance pack/);
    const g = fixture();
    g.versions.lithium[0].dependencies = [{ version_id: null, project_id: 'NNAgCjsB', dependency_type: 'required' }];
    await assert.rejects(resolvePack({ slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(g).fetch) }), /requires project NNAgCjsB, which is EntityCulling, excluded from the bundle by its licence/);
  });

  test('a licence that is missing, null, differently cased or unknown excludes the member; it never ships', async () => {
    for (const license of [null, {}, { id: null, name: null, url: null }, { id: 'mit', name: 'MIT', url: null },
      { id: 'LICENSEREF-POLYFORM-SHIELD-1.0.0', name: 'PolyForm', url: null }, { url: 'https://example.invalid/LICENSE' }]) {
      const f = fixture({ entityLicense: license });
      const result = await resolvePack({ slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(f).fetch) });
      assert.deepEqual(result.items.map((i) => i.slug), ['sodium', 'lithium', 'ferrite-core', 'immediatelyfast', 'iris'], JSON.stringify(license));
      assert.deepEqual(result.excluded.map((e) => e.slug), ['entityculling'], JSON.stringify(license));
      assert.equal(result.excluded[0].license, license?.id ? license.id : '(none)');
    }
  });

  test('two members whose files share one name cannot be bundled', async () => {
    const f = fixture();
    f.versions.iris[0].files[0].filename = 'Sodium-Fabric-9.9.9-test.jar';
    await assert.rejects(resolvePack({ slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(f).fetch) }), /Iris Shaders 4\.4\.4-test and Sodium 9\.9\.9-test both publish their file as Sodium-Fabric-9\.9\.9-test\.jar/);
  });

  test('a member marked incompatible with another bundled member fails; a pin on another version of it does not', async () => {
    const f = fixture();
    f.versions.lithium[0].dependencies = [{ version_id: null, project_id: 'AANobbMI', dependency_type: 'incompatible' }];
    await assert.rejects(resolvePack({ slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(f).fetch) }), /Lithium 7\.7\.7-test is marked incompatible with Sodium 9\.9\.9-test; the two cannot be bundled together/);
    const g = fixture();
    g.versions.lithium[0].dependencies = [{ version_id: 'SODIUMV9', project_id: null, dependency_type: 'incompatible' }];
    await assert.rejects(resolvePack({ slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(g).fetch) }), /Lithium 7\.7\.7-test is marked incompatible with Sodium 9\.9\.9-test \(SODIUMV9\)/);
    const h = fixture();
    h.versions.lithium[0].dependencies = [{ version_id: 'SODIUMV8', project_id: 'AANobbMI', dependency_type: 'incompatible' }, { version_id: null, project_id: 'NNAgCjsB', dependency_type: 'incompatible' }, { version_id: null, project_id: 'OUTSIDE1', dependency_type: 'optional' }];
    const result = await resolvePack({ slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(h).fetch) });
    assert.equal(result.items.length, 5, 'incompatible with an older Sodium, an excluded member or an optional dependency is fine');
  });

  test('a member without a compatible version is an error, not a silent skip', async () => {
    const f = fixture();
    f.versions.lithium = [];
    await assert.rejects(resolvePack({ slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(f).fetch) }), /Lithium \(lithium\) has no Fabric version for Minecraft 1\.21\.11/);
  });

  test('every request has a time limit: a stalled connection fails the resolution instead of hanging the job', async () => {
    assert.equal(DEFAULT_TIMEOUT_MS, 60_000);
    let attempts = 0;
    const stalled = (url, init) => new Promise((_, reject) => {
      attempts += 1;
      assert.ok(init.signal instanceof AbortSignal, 'fetch receives the abort signal');
      init.signal.addEventListener('abort', () => reject(init.signal.reason));
    });
    const c = new Client({ fetch: stalled, userAgent: 'test-agent', delayMs: 0, timeoutMs: 20 });
    await assert.rejects(resolvePack({ slugs: ['sodium'], gameVersion: GAME, client: c }), (error) => {
      assert.ok(error instanceof PackError);
      assert.match(error.message, /project\/sodium: no answer within 20 ms/);
      return true;
    });
    assert.equal(attempts, 3, 'a timeout is retried like a network error');
    const { fetch } = fakeFetch(fixture());
    const seen = [];
    const open = new Client({ fetch: (url, init) => { seen.push(init.signal); return fetch(url, init); }, userAgent: 'test-agent', delayMs: 0, timeoutMs: 0 });
    await resolvePack({ slugs: ['lithium'], gameVersion: GAME, client: open });
    assert.ok(seen.length > 0 && seen.every((s) => s === undefined), 'timeoutMs 0 sends no signal');
  });

  test('retries a 5xx answer and gives up after three attempts', async () => {
    const f = fixture();
    let attempts = 0;
    const { fetch } = fakeFetch(f, { [`${API_BASE}project/sodium`]: () => { attempts += 1; return new Response('down', { status: 503 }); } });
    await assert.rejects(resolvePack({ slugs: ['sodium'], gameVersion: GAME, client: client(fetch) }), /project\/sodium: HTTP 503/);
    assert.equal(attempts, 3);
  });
});

describe('downloadItem()', () => {
  test('deletes a file whose SHA-512 or size does not match and fails', async () => {
    const f = fixture();
    const dir = tmp();
    const item = { title: 'Sodium', file: 'sodium-fabric-9.9.9-test.jar', url: f.versions.sodium[0].files[0].url, size: f.jars.sodium.length, sha512: '0'.repeat(128) };
    await assert.rejects(downloadItem(client(fakeFetch(f).fetch), item, join(dir, 'mods')), /SHA-512 .* the file was deleted/);
    assert.ok(!existsSync(join(dir, 'mods', item.file)));
    const wrongSize = { ...item, sha512: sha512(f.jars.sodium), size: f.jars.sodium.length + 1 };
    await assert.rejects(downloadItem(client(fakeFetch(f).fetch), wrongSize, join(dir, 'mods')), /downloaded \d+ bytes/);
    assert.ok(!existsSync(join(dir, 'mods', item.file)));
    const ok = await downloadItem(client(fakeFetch(f).fetch), { ...item, sha512: sha512(f.jars.sodium) }, join(dir, 'mods'));
    assert.equal(ok.size, f.jars.sodium.length);
    assert.deepEqual(readFileSync(ok.path), f.jars.sodium);
  });
});

describe('buildPack()', () => {
  test('writes the verified jars, the notices with every licence text, the JSON (valid against the schema) and the summary', async () => {
    const f = fixture();
    const out = join(tmp(), 'pack');
    const fapi = fabricApiJar(tmp());
    const log = [];
    const result = await buildPack({ out, slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(f).fetch), fabricApiJar: fapi, now: () => new Date('2026-10-06T12:34:56.789Z'), log: (l) => log.push(l) });
    assert.equal(result.resolvedAt, '2026-10-06T12:34:56Z');
    for (const slug of ['sodium', 'lithium', 'ferrite-core', 'immediatelyfast', 'iris']) {
      const item = result.items.find((i) => i.slug === slug);
      assert.deepEqual(readFileSync(join(out, 'mods', item.file)), f.jars[slug], slug);
    }
    assert.ok(!existsSync(join(out, 'mods', 'entityculling-fabric-2.2.2-test.jar')), 'EntityCulling is not downloaded');

    const json = JSON.parse(readFileSync(join(out, 'performance-pack.json'), 'utf8'));
    assert.deepEqual(validateWithSchemaFile(SCHEMA, json), { valid: true, errors: [] });
    assert.equal(json.schemaVersion, 1);
    assert.equal(json.gameVersion, GAME);
    assert.equal(json.resolvedAt, '2026-10-06T12:34:56Z');
    assert.deepEqual(Object.keys(json.items[0]), ['slug', 'title', 'projectId', 'versionId', 'versionNumber', 'file', 'size', 'sha512', 'license', 'sourceUrl']);
    assert.deepEqual(json.items[0], {
      slug: 'sodium', title: 'Sodium', projectId: 'AANobbMI', versionId: 'SODIUMV9', versionNumber: '9.9.9-test',
      file: 'sodium-fabric-9.9.9-test.jar', size: f.jars.sodium.length, sha512: sha512(f.jars.sodium),
      license: { id: POLYFORM_SHIELD_ID, name: 'PolyForm Shield License 1.0.0', url: 'https://github.com/CaffeineMC/sodium/blob/dev/LICENSE.md' },
      sourceUrl: 'https://github.com/CaffeineMC/sodium',
    });
    assert.deepEqual(json.excluded, [{ slug: 'entityculling', title: 'EntityCulling', license: 'LicenseRef-tr7zw-Protective-License', reason: json.excluded[0].reason }]);
    assert.match(json.excluded[0].reason, /not in the redistribution allow-list/);

    const notices = readFileSync(join(out, 'THIRD-PARTY-LICENSES.txt'), 'utf8');
    assert.match(notices, /^Third-party notices for the VANTA Client mods bundle\n/);
    assert.match(notices, /Sodium 9\.9\.9-test\nFile: {5}mods\/sodium-fabric-9\.9\.9-test\.jar\nLicence: {2}LicenseRef-Polyform-Shield-1\.0\.0 \(PolyForm Shield License 1\.0\.0\)\n {10}https:\/\/github\.com\/CaffeineMC\/sodium\/blob\/dev\/LICENSE\.md\n {10}https:\/\/polyformproject\.org\/licenses\/shield\/1\.0\.0\nSource: {3}https:\/\/github\.com\/CaffeineMC\/sodium\nAuthors: {2}JellySquid, other \(Modrinth project team\)\nModrinth: https:\/\/modrinth\.com\/mod\/sodium\n/);
    assert.match(notices, /fake sodium licence text/);
    assert.match(notices, /Lithium 7\.7\.7-test\n[^]*GNU LESSER GENERAL PUBLIC LICENSE Version 3 \(fake\)\n\n\[GPL-3\.0-only, referenced by LGPL-3\.0-only; from https:\/\/raw\.githubusercontent\.com\/spdx\/license-list-data\/main\/text\/GPL-3\.0-only\.txt\]\nGNU GENERAL PUBLIC LICENSE Version 3 \(fake\)/);
    assert.match(notices, /Authors: {2}see the source repository/);
    assert.match(notices, /MIT License \(fake\)/);
    assert.match(notices, /LGPL text of ImmediatelyFast \(fake\)/);
    assert.match(notices, /Fabric API 0\.0\.0-test\nFile: {5}mods\/fabric-api-0\.0\.0-test\.jar\nLicence: {2}Apache-2\.0 \(Apache License 2\.0\); LICENSE-fabric-api from the jar\n[^]*Apache License 2\.0 \(fake Fabric API notice\)/);
    assert.doesNotMatch(notices, /EntityCulling/);
    assert.equal((notices.match(/^Copyright: held by the authors/gm) ?? []).length, 5);

    const summary = readFileSync(join(out, 'PERFORMANCE-PACK.txt'), 'utf8');
    assert.match(summary, /^Performance pack for Minecraft 1\.21\.11 \(Fabric\), resolved on Modrinth at 2026-10-06T12:34:56Z\.\n/);
    assert.match(summary, /^- Sodium 9\.9\.9-test \(release, version id SODIUMV9\): mods\/sodium-fabric-9\.9\.9-test\.jar, \d+ B, licence LicenseRef-Polyform-Shield-1\.0\.0 \(PolyForm Shield License 1\.0\.0\)$/m);
    assert.match(summary, /^Not in the bundle:\n- EntityCulling: licence LicenseRef-tr7zw-Protective-License \(tr7zw Protective License\) is not in the redistribution allow-list[^\n]*\. It is downloaded in the game with one click under Mods & Shaders \(Performance pack card\)\.$/m);
    assert.match(summary, /^- Iris Shaders 4\.4\.4-test requires Sodium version SODIUMV9 \(9\.9\.9-test\); the bundled Sodium is exactly that version\.$/m);
    assert.match(summary, new RegExp(`^- Sodium: PolyForm Shield 1\\.0\\.0 \\(${POLYFORM_SHIELD_URL.replace(/[./]/g, '\\$&')}\\); redistribution is allowed`, 'm'));
    assert.match(summary, /THIRD-PARTY-LICENSES\.txt; the machine-readable list is performance-pack\.json\.\n$/);
    assert.ok(log.some((l) => /downloaded mods\/iris-fabric-4\.4\.4-test\.jar \(\d+ bytes, SHA-512 verified\)/.test(l)));
  });

  test('never writes outputs when a licence text cannot be fetched or is an HTML page', async () => {
    const f = fixture();
    delete f.texts[`${SPDX_TEXT_BASE}MIT.txt`];
    let out = join(tmp(), 'pack');
    await assert.rejects(buildPack({ out, slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(f).fetch) }), /FerriteCore: licence text MIT could not be fetched: .*MIT\.txt: HTTP 404/);
    assert.ok(!existsSync(join(out, 'performance-pack.json')));
    assert.ok(!existsSync(join(out, 'THIRD-PARTY-LICENSES.txt')));

    const g = fixture();
    g.texts['https://raw.githubusercontent.com/CaffeineMC/sodium/dev/LICENSE.md'] = '<!DOCTYPE html><html><body>Sign in to GitHub</body></html>';
    out = join(tmp(), 'pack');
    await assert.rejects(buildPack({ out, slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(g).fetch) }), /Sodium: licence text LicenseRef-Polyform-Shield-1\.0\.0 could not be fetched: .*HTML page/);
    assert.ok(!existsSync(join(out, 'performance-pack.json')));
    // A page that starts with a comment or BOM, or one that only the Content-Type header gives away.
    assert.ok(looksLikeHtml('<!-- moved -->\n<!DOCTYPE html><html>'));
    assert.ok(looksLikeHtml('\ufeff  <html lang="en">'));
    assert.ok(looksLikeHtml('Sign in', 'text/html; charset=utf-8'));
    assert.ok(!looksLikeHtml('Copyright (c) <year> <copyright holders>\n\nPermission is hereby granted', 'text/plain; charset=utf-8'));
    assert.ok(!looksLikeHtml('# PolyForm Shield License 1.0.0\n\n<https://polyformproject.org/licenses/shield/1.0.0>', 'text/plain'));
    const k = fixture();
    const { fetch: htmlFetch } = fakeFetch(k, { [`${SPDX_TEXT_BASE}MIT.txt`]: () => new Response('Not Found', { status: 200, headers: { 'content-type': 'text/html; charset=utf-8' } }) });
    await assert.rejects(buildPack({ out: join(tmp(), 'pack'), slugs: SLUGS, gameVersion: GAME, client: client(htmlFetch) }), /FerriteCore: licence text MIT could not be fetched: .*HTML page/);

    const h = fixture();
    h.projects.sodium.license = { id: 'LicenseRef-Unknown', name: 'Custom', url: null };
    await assert.rejects(resolvePack({ slugs: ['sodium'], gameVersion: GAME, client: client(fakeFetch(h).fetch) }).then((r) => {
      assert.equal(r.items.length, 0, 'an unknown licence excludes, it never ships');
      return buildPack({ out: join(tmp(), 'pack'), slugs: ['sodium'], gameVersion: GAME, client: client(fakeFetch(h).fetch) });
    }), /no Performance pack member may be bundled/);
  });

  test('a corrupt download fails the build and leaves no jar behind', async () => {
    const f = fixture();
    f.versions.lithium[0].files[0].hashes.sha512 = 'f'.repeat(128);
    const out = join(tmp(), 'pack');
    await assert.rejects(buildPack({ out, slugs: SLUGS, gameVersion: GAME, client: client(fakeFetch(f).fetch) }), /lithium-fabric-7\.7\.7-test\.jar: downloaded .* the file was deleted/);
    assert.ok(!existsSync(join(out, 'mods', 'lithium-fabric-7.7.7-test.jar')));
    assert.ok(!existsSync(join(out, 'performance-pack.json')));
  });

  test('fabricApiNotice() reads the notice and version from the jar and refuses a jar without it', () => {
    const notice = fabricApiNotice(fabricApiJar(tmp()));
    assert.equal(notice.versionNumber, '0.0.0-test');
    assert.equal(notice.file, 'fabric-api-0.0.0-test.jar');
    assert.match(notice.text, /fake Fabric API notice/);
    const bare = join(tmp(), 'fabric-api-x.jar');
    writeFileSync(bare, buildZip([{ name: 'fabric.mod.json', data: '{}' }]));
    assert.throws(() => fabricApiNotice(bare), /does not contain LICENSE-fabric-api/);
  });
});

describe('CLI', () => {
  test('resolves with the injected fetch, prints the out dir last and exits 0', async () => {
    const f = fixture();
    const out = join(tmp(), 'pack');
    const lines = [];
    const errors = [];
    const code = await main(['--out', out, '--fabric-api-jar', fabricApiJar(tmp())], { fetch: fakeFetch(f).fetch, delayMs: 0, log: (l) => lines.push(l), logError: (l) => errors.push(l) });
    assert.equal(code, 0, errors.join('\n'));
    assert.equal(lines.at(-1), out);
    assert.match(lines[0], /^Performance pack for Minecraft 1\.21\.11: sodium, lithium, ferrite-core, immediatelyfast, entityculling, iris$/);
    assert.ok(lines.some((l) => /^bundled 5 of 6 pack members into .*; excluded: entityculling$/.test(l)), lines.join('\n'));
    assert.ok(existsSync(join(out, 'PERFORMANCE-PACK.txt')));
    assert.ok(existsSync(join(out, 'THIRD-PARTY-LICENSES.txt')));
    assert.ok(existsSync(join(out, 'performance-pack.json')));
  });

  test('exits 1 with the reason when the API fails and 2 on usage errors', async () => {
    const f = fixture();
    f.versions.iris[0].dependencies[0].version_id = 'SODIUMV8';
    const errors = [];
    assert.equal(await main(['--out', join(tmp(), 'pack')], { fetch: fakeFetch(f).fetch, delayMs: 0, log: () => {}, logError: (l) => errors.push(l) }), 1);
    assert.match(errors.join('\n'), /error: Iris Shaders 4\.4\.4-test requires Sodium version SODIUMV8/);
    const usage = [];
    assert.equal(await main([], { fetch: fakeFetch(f).fetch, log: () => {}, logError: (l) => usage.push(l) }), 2);
    assert.match(usage.join('\n'), /--out is required/);
    assert.equal(await main(['--out', 'x', 'extra'], { fetch: fakeFetch(f).fetch, log: () => {}, logError: () => {} }), 2);
    assert.equal(await main(['--bogus'], { fetch: fakeFetch(f).fetch, log: () => {}, logError: () => {} }), 2);
    const help = [];
    assert.equal(await main(['--help'], { log: (l) => help.push(l) }), 0);
    assert.match(help[0], /^Usage: node scripts\/release\/performance-pack\.mjs --out <dir>/);
  });
});
