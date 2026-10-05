/**
 * Tests scripts/ci/publish-screenshots.sh, which the release workflow uses to hand manifests (and the client files)
 * back on the ci-artifacts branch. The GitHub remote URL is rewritten to a local bare repository with git's
 * url.<base>.insteadOf (GIT_CONFIG_* environment variables), so nothing leaves the machine.
 */
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { REPO_ROOT } from './lib/repo.mjs';
import { buildZip } from './lib/zip-writer.mjs';

const SCRIPT = join(REPO_ROOT, 'scripts', 'ci', 'publish-screenshots.sh');
const REPO = 'example/VANTA-Client';
const available = process.platform !== 'win32'
  && spawnSync('git', ['--version']).status === 0
  && spawnSync('bash', ['--version']).status === 0;

function setup() {
  const dir = mkdtempSync(join(tmpdir(), 'vanta-ci-publish-'));
  const bare = join(dir, 'remote.git');
  assert.equal(spawnSync('git', ['init', '-q', '--bare', bare]).status, 0);
  const env = {
    ...process.env,
    GITHUB_TOKEN: 'test-token',
    GITHUB_REPOSITORY: REPO,
    GITHUB_SHA: 'abc123',
    GITHUB_RUN_ID: '42',
    GIT_CONFIG_COUNT: '1',
    GIT_CONFIG_KEY_0: `url.file://${bare}.insteadOf`,
    GIT_CONFIG_VALUE_0: `https://x-access-token:test-token@github.com/${REPO}.git`,
  };
  return { dir, bare, env };
}

function publish(ctx, src, label) {
  return spawnSync('bash', [SCRIPT, src, label], { env: ctx.env, encoding: 'utf8' });
}

function git(ctx, ...args) {
  const r = spawnSync('git', ['-C', ctx.bare, ...args], { encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr);
  return r.stdout;
}

function files(ctx) {
  return git(ctx, 'ls-tree', '-r', '--name-only', 'ci-artifacts').trim().split('\n').sort();
}

function source(ctx, name, content) {
  const src = join(ctx.dir, name);
  for (const [path, data] of Object.entries(content)) {
    mkdirSync(join(src, path, '..'), { recursive: true });
    writeFileSync(join(src, path), data);
  }
  return src;
}

describe('scripts/ci/publish-screenshots.sh', { skip: available ? false : 'needs git and bash' }, () => {
  test('publishes a release label with JSON, jar, zip and a nested latest/ folder, keeps other labels, one commit', () => {
    const ctx = setup();
    let r = publish(ctx, source(ctx, 'gametest', { 'screenshots/a.png': 'png' }), 'gametest');
    assert.equal(r.status, 0, r.stderr);
    const release = source(ctx, 'release', {
      'client-1.0.0.json': '{"product":"client"}\n',
      'latest/client-latest.json': '{"product":"client"}\n',
      'SHA256SUMS.txt': `${'a'.repeat(64)}  vanta-client-1.0.0.jar\n`,
      'vanta-client-1.0.0.jar': buildZip([{ name: 'fabric.mod.json', data: '{}' }]),
      'vanta-client-1.0.0-mods.zip': buildZip([{ name: 'INSTALL.txt', data: 'x' }]),
      'fabric-api-0.141.6+1.21.11.jar': buildZip([{ name: 'LICENSE-fabric-api', data: 'x' }]),
    });
    r = publish(ctx, release, 'release-client-v1.0.0');
    assert.equal(r.status, 0, r.stderr);
    assert.match(r.stdout, /Published 6 files to branch ci-artifacts\/release-client-v1\.0\.0/);
    assert.deepEqual(files(ctx), [
      'README.md',
      'gametest/screenshots/a.png',
      'release-client-v1.0.0/SHA256SUMS.txt',
      'release-client-v1.0.0/client-1.0.0.json',
      'release-client-v1.0.0/fabric-api-0.141.6+1.21.11.jar',
      'release-client-v1.0.0/latest/client-latest.json',
      'release-client-v1.0.0/vanta-client-1.0.0-mods.zip',
      'release-client-v1.0.0/vanta-client-1.0.0.jar',
    ]);
    assert.equal(git(ctx, 'rev-list', '--count', 'ci-artifacts').trim(), '1', 'the branch is a single commit');
    const readme = git(ctx, 'show', 'ci-artifacts:README.md');
    assert.match(readme, /- `gametest`: 1 files/);
    assert.match(readme, /- `release-client-v1\.0\.0`: 6 files/);
    assert.match(readme, /label `release-client-v1\.0\.0` from commit abc123/);
    // Binary content survives byte for byte.
    const zip = spawnSync('git', ['-C', ctx.bare, 'show', 'ci-artifacts:release-client-v1.0.0/vanta-client-1.0.0-mods.zip']);
    assert.deepEqual(zip.stdout, buildZip([{ name: 'INSTALL.txt', data: 'x' }]));
  });

  test('re-publishing a label replaces it completely (stale files disappear)', () => {
    const ctx = setup();
    assert.equal(publish(ctx, source(ctx, 'one', { 'old.json': '{}', 'keep.json': '1' }), 'release-launcher-v1.0.0').status, 0);
    assert.equal(publish(ctx, source(ctx, 'two', { 'keep.json': '2' }), 'release-launcher-v1.0.0').status, 0);
    assert.deepEqual(files(ctx), ['README.md', 'release-launcher-v1.0.0/keep.json']);
    assert.equal(git(ctx, 'show', 'ci-artifacts:release-launcher-v1.0.0/keep.json'), '2');
  });

  test('an empty or missing source directory is a no-op', () => {
    const ctx = setup();
    const empty = join(ctx.dir, 'empty');
    mkdirSync(empty);
    const r = publish(ctx, empty, 'nothing');
    assert.equal(r.status, 0);
    assert.match(r.stdout, /nothing to publish/);
    assert.equal(publish(ctx, join(ctx.dir, 'missing'), 'nothing').status, 0);
    assert.notEqual(spawnSync('git', ['-C', ctx.bare, 'rev-parse', '--verify', 'ci-artifacts']).status, 0, 'no branch created');
  });
});
