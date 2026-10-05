/**
 * Text-level checks of .github/workflows/release.yml that the release depends on but no runner here can execute:
 * the Windows portable zip (and only the portable zip) carries VANTA Launcher/app/vanta-portable.marker, which the
 * launcher uses to pick the portable zip as its self-update.
 */
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { REPO_ROOT } from './lib/repo.mjs';

const WORKFLOW = readFileSync(join(REPO_ROOT, '.github', 'workflows', 'release.yml'), 'utf8');
const BUILD_GRADLE = readFileSync(join(REPO_ROOT, 'launcher', 'build.gradle'), 'utf8');
const MARKER = 'VANTA Launcher/app/vanta-portable.marker';

/** The text of one top-level job (from its "  <id>:" line to the next job). */
function job(id) {
  const start = WORKFLOW.indexOf(`\n  ${id}:\n`);
  assert.ok(start >= 0, `job ${id} exists`);
  const next = WORKFLOW.slice(start + 1).search(/\n {2}[a-z][a-z0-9-]*:\n/);
  return next < 0 ? WORKFLOW.slice(start) : WORKFLOW.slice(start, start + 1 + next);
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

  test('the MSI and EXE come from fresh jpackage runs after the zip and refuse a stray marker', () => {
    const zip = indexOfOrFail(windows, '7z a -tzip');
    for (const type of ['msi', 'exe']) {
      const build = indexOfOrFail(windows, `-PjpackageType=${type}`);
      assert.ok(build > zip, `${type} is built after the portable zip`);
      const guard = windows.indexOf('-name vanta-portable.marker', build);
      const copy = indexOfOrFail(windows, `dist/VANTA-Launcher-\${VERSION}.${type}"`);
      assert.ok(guard > build && guard < copy, `${type} step checks for a stray marker before staging`);
    }
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
