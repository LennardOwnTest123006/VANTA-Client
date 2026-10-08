/**
 * Text-level checks of the workflows that the releases depend on but no runner here can execute:
 * - release.yml: the Windows portable zip (and only the portable zip) carries VANTA Launcher/app/vanta-portable.marker,
 *   which the launcher uses to pick the portable zip as its self-update;
 * - bundle.yml: the full release zip is built from the published files only (no build tools), published under the tag
 *   v<version> without taking GitHub's "latest" badge from the launcher, verified after the upload and handed back.
 */
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { REPO_ROOT } from './lib/repo.mjs';

const WORKFLOW = readFileSync(join(REPO_ROOT, '.github', 'workflows', 'release.yml'), 'utf8');
const BUNDLE = readFileSync(join(REPO_ROOT, '.github', 'workflows', 'bundle.yml'), 'utf8');
const BUILD_GRADLE = readFileSync(join(REPO_ROOT, 'launcher', 'build.gradle'), 'utf8');
const MARKER = 'VANTA Launcher/app/vanta-portable.marker';

/** The text of one top-level job (from its "  <id>:" line to the next job). */
function job(id, workflow = WORKFLOW) {
  const start = workflow.indexOf(`\n  ${id}:\n`);
  assert.ok(start >= 0, `job ${id} exists`);
  const next = workflow.slice(start + 1).search(/\n {2}[a-z][a-z0-9-]*:\n/);
  return next < 0 ? workflow.slice(start) : workflow.slice(start, start + 1 + next);
}

function indexOfOrFail(text, needle) {
  const index = text.indexOf(needle);
  assert.ok(index >= 0, `missing: ${needle}`);
  return index;
}

describe('release.yml portable marker', () => {
  const windows = job('launcher-windows');

  test('the marker is written into the app image after the app-image build and right before zipping', () => {
    const appImage = indexOfOrFail(windows, '-PjpackageType=app-image');
    const marker = indexOfOrFail(windows, `printf 'portable\\n' > "$APP/app/vanta-portable.marker"`);
    const zip = indexOfOrFail(windows, '7z a -tzip');
    assert.ok(appImage < marker && marker < zip, 'app-image build, then marker, then 7z');
    assert.equal(WORKFLOW.split('> "$APP/app/vanta-portable.marker"').length - 1, 1, 'written exactly once');
  });

  test('the zip check requires the marker and its exact text', () => {
    const zip = indexOfOrFail(windows, '7z a -tzip');
    const check = indexOfOrFail(windows, 'node scripts/release/check-zip.mjs "dist/$ZIP"');
    assert.ok(zip < check);
    const checkCall = windows.slice(check, windows.indexOf('\n          node --input-type=module', check));
    assert.ok(checkCall.includes(`"${MARKER}"`), 'check-zip lists the marker');
    assert.match(windows, /if \(text !== "portable\\n"\)/);
  });

  test('the MSI comes from a fresh jpackage run after the zip and refuses a stray marker; no EXE is built from 1.4.0 on', () => {
    const zip = indexOfOrFail(windows, '7z a -tzip');
    const build = indexOfOrFail(windows, '-PjpackageType=msi');
    assert.ok(build > zip, 'msi is built after the portable zip');
    const guard = windows.indexOf('-name vanta-portable.marker', build);
    const copy = indexOfOrFail(windows, 'dist/VANTA-Launcher-${VERSION}.msi"');
    assert.ok(guard > build && guard < copy, 'msi step checks for a stray marker before staging');
    assert.doesNotMatch(windows, /-PjpackageType=exe/, 'the .exe installer was dropped with launcher 1.4.0');
    assert.doesNotMatch(windows, /VANTA-Launcher-\$\{VERSION\}\.exe/);
    // Exactly the three Windows files are staged: msi, portable zip, Windows fat jar.
    assert.match(windows, /\[ "\$\{#staged\[@\]\}" -eq 3 \] \|\| \{ echo "::error::expected 3 files in dist/);
    assert.doesNotMatch(windows, /expected 4 files in dist/);
    // jpackage deletes its destination before every run and takes the app from a Sync of the fat jar only.
    assert.match(BUILD_GRADLE, /tasks\.register\('prepareJpackageInput', Sync\) \{\s*dependsOn fatJar\s*from\(fatJar\.flatMap \{ it\.archiveFile \}\)\s*into\(jpackageInput\)\s*\}/);
    assert.match(BUILD_GRADLE, /doFirst \{\s*def dest = jpackageDest\.get\(\)\.asFile\s*if \(dest\.exists\(\)\) \{\s*delete\(dest\)/);
    assert.match(BUILD_GRADLE, /'--input', jpackageInput\.get\(\)\.asFile\.absolutePath/);
  });

  test('no other job writes the marker', () => {
    for (const id of ['client', 'launcher-linux', 'launcher-macos', 'publish']) {
      assert.doesNotMatch(job(id), /vanta-portable\.marker/, id);
    }
  });
});

describe('bundle.yml full release zip', () => {
  const prepare = job('prepare', BUNDLE);
  const publish = job('publish', BUNDLE);

  test('runs by hand only, builds nothing and reads only the committed, published manifests', () => {
    assert.match(BUNDLE, /^on:\n {2}workflow_dispatch:\n/m);
    assert.doesNotMatch(BUNDLE, /^\s+(push|pull_request|schedule):/m, 'no automatic trigger');
    for (const input of ['version', 'client_version', 'launcher_version', 'draft']) assert.match(BUNDLE, new RegExp(`^ {6}${input}:\\n`, 'm'), input);
    assert.doesNotMatch(BUNDLE, /gradlew|setup-java|setup-gradle/, 'the zip is made from the published files, never from a build');
    assert.match(BUNDLE, /^permissions:\n {2}contents: read\n/m);
    assert.match(publish, /^ {4}permissions:\n {6}contents: write\n/m);
    assert.match(prepare, /CLIENT_VERSION="\$\{INPUT_CLIENT_VERSION:-\$VERSION\}"/);
    assert.match(prepare, /LAUNCHER_VERSION="\$\{INPUT_LAUNCHER_VERSION:-\$VERSION\}"/);
    assert.match(prepare, /release-assets\.mjs check-manifest "\$MANIFEST" --published --repo "\$GITHUB_REPOSITORY" --tag "\$\{PRODUCT\}-v\$\{PRODUCT_VERSION\}"/);
    assert.match(prepare, /TAG="v\$\{VERSION\}"/);
    assert.match(prepare, /git ls-remote --tags origin "refs\/tags\/\$\{TAG\}"/, 'refuses a tag that exists at another commit');
  });

  test('download, assemble, manifest, verify and notes run in that order through build-bundle.mjs', () => {
    const steps = ['build-bundle.mjs download ', 'build-bundle.mjs assemble ', 'build-bundle.mjs manifest ', 'validate-json.mjs shared/schemas/bundle-manifest.schema.json', 'build-bundle.mjs verify "$MANIFEST" --local "$ZIP"', 'build-bundle.mjs notes ', 'softprops/action-gh-release@v2'];
    let last = -1;
    for (const step of steps) {
      const index = indexOfOrFail(publish, step);
      assert.ok(index > last, `${step} comes after the previous step`);
      last = index;
    }
    assert.match(publish, /--url "\$URL"/);
    assert.match(publish, /URL="https:\/\/github\.com\/\$\{GITHUB_REPOSITORY\}\/releases\/download\/\$\{TAG\}\/VantaClient-\$\{VERSION\}-Release\.zip"/);
    assert.match(publish, /--date "\$\(date -u \+%Y-%m-%d\)"/);
    assert.match(publish, /--out shared\/releases\/bundles\n/);
  });

  test('the GitHub Release is v<version>, never "latest", with the zip, its checksum and the manifest in order', () => {
    const release = publish.slice(publish.indexOf('softprops/action-gh-release@v2'), publish.indexOf('- name: Verify the public download link'));
    assert.match(release, /tag_name: \$\{\{ needs\.prepare\.outputs\.tag \}\}/);
    assert.match(release, /target_commitish: \$\{\{ github\.sha \}\}/);
    assert.match(release, /name: VANTA \$\{\{ needs\.prepare\.outputs\.version \}\} full release/);
    assert.match(release, /draft: \$\{\{ needs\.prepare\.outputs\.draft == 'true' \}\}/);
    assert.match(release, /prerelease: false/);
    assert.match(release, /make_latest: 'false'/, 'the launcher release keeps the latest badge');
    assert.match(release, /files: \|\n {12}dist\/VantaClient-\$\{\{ needs\.prepare\.outputs\.version \}\}-Release\.zip\n {12}dist\/SHA256SUMS\.txt\n {12}dist\/vanta-\$\{\{ needs\.prepare\.outputs\.version \}\}\.json\n/);
    assert.match(release, /preserve_order: true/);
    assert.match(release, /fail_on_unmatched_files: true/);
  });

  test('the public link is re-hashed with retries (skipped for drafts) and the manifest is handed back', () => {
    const verify = publish.slice(publish.indexOf('- name: Verify the public download link'), publish.indexOf('- name: Draft release'));
    assert.match(verify, /if: needs\.prepare\.outputs\.draft != 'true'/);
    assert.match(verify, /for attempt in 1 2 3 4 5; do\n\s+if node scripts\/release\/build-bundle\.mjs verify "\$MANIFEST"; then ok=1; break; fi/);
    assert.match(verify, /cmp "\$RUNNER_TEMP\/SHA256SUMS\.txt" dist\/SHA256SUMS\.txt/);
    assert.match(verify, /cmp "\$RUNNER_TEMP\/manifest\.json" "\$MANIFEST"/);
    const handBack = publish.slice(publish.indexOf('- name: Hand the manifest back'), publish.indexOf('- name: Job summary'));
    assert.match(handBack, /cp "shared\/releases\/bundles\/vanta-\$\{VERSION\}\.json" "\$OUT\/"/);
    assert.match(handBack, /echo "apply=cp vanta-\$\{VERSION\}\.json <repo>\/shared\/releases\/bundles\/"/);
    assert.match(handBack, /> "\$OUT\/BUNDLE-INFO\.txt"/);
    assert.match(handBack, /bash scripts\/ci\/publish-screenshots\.sh "\$OUT" "bundle-\$\{TAG\}"/);
    assert.match(publish, /- name: Job summary\n {8}if: always\(\)/);
  });

  test('no AI claims anywhere in the workflow texts', () => {
    assert.doesNotMatch(BUNDLE, /\bAI\b/);
  });
});
