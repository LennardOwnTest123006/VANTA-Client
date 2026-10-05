import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { checkLauncherJar, main, nativeArchitecture } from './check-launcher-jar.mjs';
import { listZipEntries, openZip, parseJarManifest, readZipEntry } from './lib/zip.mjs';
import { buildZip, fakeNative } from './lib/zip-writer.mjs';

const MANIFEST = 'Manifest-Version: 1.0\r\nMain-Class: dev.vanta.launcher.Main\r\nImplementation-Title: VANTA Launcher\r\nImplementation-Version: 1.0.0\r\n\r\n';

/** A fat jar shaped like the real one: launcher classes, JavaFX classes, JavaFX natives at the root, JNA natives nested. */
function fatJar(natives, { manifest = MANIFEST, javafx = true } = {}) {
  const entries = [
    { name: 'META-INF/', data: '' },
    { name: 'META-INF/MANIFEST.MF', data: manifest, deflate: true },
    { name: 'dev/vanta/launcher/Main.class', data: 'class' },
    { name: 'com/sun/jna/linux-x86-64/libjnidispatch.so', data: fakeNative.elf() },
    { name: 'com/sun/jna/win32-x86-64/jnidispatch.dll', data: fakeNative.pe() },
    { name: 'com/sun/jna/darwin-aarch64/libjnidispatch.jnilib', data: fakeNative.macho() },
  ];
  if (javafx) {
    entries.push({ name: 'javafx/application/Application.class', data: 'class', deflate: true });
    entries.push({ name: 'javafx/scene/control/Control.class', data: 'class' });
  }
  for (const [name, data] of Object.entries(natives)) entries.push({ name, data, deflate: true });
  return buildZip(entries);
}

function writeJar(buffer) {
  const dir = mkdtempSync(join(tmpdir(), 'vanta-launcher-jar-'));
  const path = join(dir, 'vanta-launcher-1.0.0-all.jar');
  writeFileSync(path, buffer);
  return path;
}

function check(buffer, options) {
  return checkLauncherJar(openZip(writeJar(buffer)), options);
}

describe('lib/zip.mjs', () => {
  test('lists and reads stored and deflated entries, normalising backslashes, with a trailing comment', () => {
    const zip = buildZip([
      { name: 'a.txt', data: 'hello' },
      { name: 'dir\\b.txt', data: 'x'.repeat(5000), deflate: true },
      { name: 'empty/', data: '' },
    ], { comment: 'archive comment' });
    const entries = listZipEntries(zip);
    assert.deepEqual(entries.map((e) => e.name), ['a.txt', 'dir/b.txt', 'empty/']);
    assert.equal(entries[2].directory, true);
    assert.equal(readZipEntry(zip, entries[0]).toString(), 'hello');
    assert.equal(readZipEntry(zip, entries[1]).toString(), 'x'.repeat(5000));
    assert.ok(entries[1].compressedSize < 5000, 'deflated');
  });

  test('rejects data that is not a ZIP archive', () => {
    assert.throws(() => listZipEntries(Buffer.from('definitely not a zip file, just some text')), /not a ZIP archive/);
    assert.throws(() => listZipEntries(Buffer.alloc(3)), /too small/);
  });

  test('parses jar manifests with continuation lines', () => {
    const attrs = parseJarManifest('Manifest-Version: 1.0\r\nMain-Class: dev.vanta.launcher.Ma\r\n in\r\nX: y\r\n\r\nName: other\r\nZ: 1\r\n');
    assert.equal(attrs['Main-Class'], 'dev.vanta.launcher.Main');
    assert.equal(attrs.X, 'y');
    assert.equal(attrs.Z, undefined, 'only the main section');
  });
});

describe('nativeArchitecture()', () => {
  test('recognises PE, ELF, Mach-O and universal binaries', () => {
    assert.deepEqual(nativeArchitecture(fakeNative.pe(0x8664)), { format: 'pe', archs: ['x86_64'] });
    assert.deepEqual(nativeArchitecture(fakeNative.pe(0xaa64)), { format: 'pe', archs: ['arm64'] });
    assert.deepEqual(nativeArchitecture(fakeNative.elf(0x3e)), { format: 'elf', archs: ['x86_64'] });
    assert.deepEqual(nativeArchitecture(fakeNative.elf(0xb7)), { format: 'elf', archs: ['arm64'] });
    assert.deepEqual(nativeArchitecture(fakeNative.macho(0x0100000c)), { format: 'macho', archs: ['arm64'] });
    assert.deepEqual(nativeArchitecture(fakeNative.macho(0x01000007)), { format: 'macho', archs: ['x86_64'] });
    assert.deepEqual(nativeArchitecture(fakeNative.fat([0x01000007, 0x0100000c])), { format: 'macho', archs: ['x86_64', 'arm64'] });
    assert.equal(nativeArchitecture(Buffer.from('plain text, not a binary at all')).format, 'unknown');
  });
});

describe('checkLauncherJar()', () => {
  test('accepts a Windows x64 jar with JavaFX natives', () => {
    const result = check(fatJar({ 'glass.dll': fakeNative.pe(), 'prism_d3d.dll': fakeNative.pe(), 'vcruntime140.dll': fakeNative.pe() }), { platform: 'win', version: '1.0.0' });
    assert.deepEqual(result.problems, []);
    assert.equal(result.ok, true);
    assert.deepEqual(result.natives.map((n) => n.name).sort(), ['glass.dll', 'prism_d3d.dll', 'vcruntime140.dll'], 'JNA natives below com/sun/jna are ignored');
  });

  test('accepts Linux x64 and Apple Silicon jars (and a universal dylib that contains arm64)', () => {
    assert.equal(check(fatJar({ 'libglass.so': fakeNative.elf(), 'libglassgtk3.so': fakeNative.elf() }), { platform: 'linux' }).ok, true);
    assert.equal(check(fatJar({ 'libglass.dylib': fakeNative.macho(), 'libprism_es2.dylib': fakeNative.macho() }), { platform: 'mac-aarch64', version: '1.0.0' }).ok, true);
    assert.equal(check(fatJar({ 'libglass.dylib': fakeNative.fat([0x01000007, 0x0100000c]) }), { platform: 'mac-aarch64' }).ok, true);
  });

  test('fails a jar without JavaFX (the "-all.jar runs everywhere" mistake)', () => {
    const result = check(fatJar({}, { javafx: false }), { platform: 'win' });
    assert.equal(result.ok, false);
    assert.ok(result.problems.some((p) => /javafx\/application\/Application\.class \(JavaFX is not inside the jar\)/.test(p)));
    assert.ok(result.problems.some((p) => /missing the JavaFX Windows x64 native glass\.dll/.test(p)));
  });

  test('fails natives of another operating system or CPU', () => {
    const windowsJarCheckedAsMac = check(fatJar({ 'glass.dll': fakeNative.pe() }), { platform: 'mac-aarch64' });
    assert.equal(windowsJarCheckedAsMac.ok, false);
    assert.ok(windowsJarCheckedAsMac.problems.some((p) => /glass\.dll is a native library for another operating system/.test(p)));
    const intelMacJar = check(fatJar({ 'libglass.dylib': fakeNative.macho(0x01000007) }), { platform: 'mac-aarch64' });
    assert.ok(intelMacJar.problems.some((p) => /libglass\.dylib is built for x86_64, expected arm64/.test(p)));
    const armWindows = check(fatJar({ 'glass.dll': fakeNative.pe(0xaa64) }), { platform: 'win' });
    assert.ok(armWindows.problems.some((p) => /built for arm64, expected x86_64/.test(p)));
    const notElf = check(fatJar({ 'libglass.so': Buffer.from('#!/bin/sh not a library') }), { platform: 'linux' });
    assert.ok(notElf.problems.some((p) => /is not a ELF binary \(detected: unknown\)/.test(p)));
  });

  test('checks Main-Class and Implementation-Version', () => {
    const wrong = MANIFEST.replace('dev.vanta.launcher.Main', 'other.Main').replace('1.0.0', '0.9.0');
    const result = check(fatJar({ 'libglass.so': fakeNative.elf() }, { manifest: wrong }), { platform: 'linux', version: '1.0.0' });
    assert.ok(result.problems.some((p) => /Main-Class is "other\.Main"/.test(p)));
    assert.ok(result.problems.some((p) => /Implementation-Version is "0\.9\.0", expected 1\.0\.0/.test(p)));
  });

  test('rejects an unknown platform', () => {
    assert.throws(() => check(fatJar({}), { platform: 'solaris' }), /unknown platform 'solaris'/);
  });
});

describe('CLI', () => {
  test('exit codes: 0 ok, 1 failed check, 2 usage or unreadable file', () => {
    const out = [];
    const err = [];
    const log = (l) => out.push(l);
    const logError = (l) => err.push(l);
    const good = writeJar(fatJar({ 'glass.dll': fakeNative.pe() }));
    assert.equal(main([good, '--platform', 'win', '--version', '1.0.0'], log, logError), 0);
    assert.match(out.join('\n'), /native glass\.dll: pe x86_64/);
    assert.match(out.join('\n'), /Launcher jar OK\./);
    assert.equal(main([good, '--platform', 'linux'], log, logError), 1);
    assert.match(err.join('\n'), /FAIL missing the JavaFX Linux x64 native libglass\.so/);
    assert.equal(main([good], log, logError), 2);
    assert.equal(main([good, '--platform', 'win', '--version', 'one'], log, logError), 2);
    const bad = writeJar(Buffer.from('not a jar'));
    assert.equal(main([bad, '--platform', 'win'], log, logError), 2);
    assert.equal(main(['--help'], log, logError), 0);
  });
});
