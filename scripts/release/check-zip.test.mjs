import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { main } from './check-zip.mjs';
import { buildZip } from './lib/zip-writer.mjs';

function archive(entries) {
  const path = join(mkdtempSync(join(tmpdir(), 'vanta-check-zip-')), 'portable.zip');
  writeFileSync(path, buildZip(entries));
  return path;
}

describe('check-zip.mjs', () => {
  const portable = () => archive([
    { name: 'VANTA Launcher/', data: '' },
    { name: 'VANTA Launcher/VANTA Launcher.exe', data: 'MZ', deflate: true },
    { name: 'VANTA Launcher\\app\\vanta-launcher-1.0.0-all.jar', data: 'PK' },
    { name: 'VANTA Launcher/runtime/release', data: '' },
  ]);

  test('passes when every required entry exists and is non-empty (backslash names normalised)', () => {
    const out = [];
    assert.equal(main([portable(), 'VANTA Launcher/VANTA Launcher.exe', 'VANTA Launcher/app/vanta-launcher-1.0.0-all.jar'], (l) => out.push(l), () => {}), 0);
    assert.match(out[0], /4 entries, all 2 required files present/);
  });

  test('fails for missing, empty and directory entries', () => {
    const err = [];
    const code = main([portable(), 'VANTA Launcher/runtime/bin/java.exe', 'VANTA Launcher/runtime/release', 'VANTA Launcher/'], () => {}, (l) => err.push(l));
    assert.equal(code, 1);
    assert.deepEqual(err.slice(0, 3), ['  missing: VANTA Launcher/runtime/bin/java.exe', '  empty: VANTA Launcher/runtime/release', '  is a directory: VANTA Launcher/']);
  });

  test('usage errors and unreadable archives exit 2', () => {
    assert.equal(main([], () => {}, () => {}), 2);
    assert.equal(main([portable()], () => {}, () => {}), 2);
    assert.equal(main([portable(), '--strict'], () => {}, () => {}), 2);
    const notZip = join(mkdtempSync(join(tmpdir(), 'vanta-check-zip-')), 'x.zip');
    writeFileSync(notZip, 'not a zip at all, sorry');
    assert.equal(main([notZip, 'a'], () => {}, () => {}), 2);
    assert.equal(main(['--help'], () => {}, () => {}), 0);
  });
});
