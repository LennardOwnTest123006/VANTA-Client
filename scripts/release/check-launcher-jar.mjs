#!/usr/bin/env node
/**
 * Checks that a launcher fat jar really is what its release name promises: the launcher, JavaFX classes and the
 * JavaFX native libraries of exactly one target platform, built for the right CPU.
 *
 *   node scripts/release/check-launcher-jar.mjs <jar> --platform <win|linux|mac-aarch64|mac|linux-aarch64> [--version 1.0.0]
 *
 * A fat jar only contains the JavaFX natives of the platform it was built for (the JavaFX Maven artifacts are
 * per platform), so a Windows jar cannot show its window on Linux or macOS. The check reads the jar's central
 * directory and the headers of the JavaFX natives at the jar root (glass, prism, ...; JNA's own natives under
 * com/sun/jna/ are not JavaFX and are ignored) and fails when:
 *   - dev/vanta/launcher/Main.class or the Main-Class manifest attribute is missing,
 *   - Implementation-Version differs from --version,
 *   - JavaFX classes (javafx.application.Application, javafx.scene.control.Control) are missing,
 *   - the platform's glass library (glass.dll / libglass.so / libglass.dylib) is missing,
 *   - a JavaFX native of another operating system is present,
 *   - a JavaFX native is not a PE / ELF / Mach-O binary for the expected CPU (a universal Mach-O binary passes when
 *     it contains the expected CPU).
 *
 * Exit codes: 0 ok, 1 check failed, 2 usage error or unreadable jar.
 */
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from './lib/args.mjs';
import { isSemVer } from './lib/repo.mjs';
import { openZip, parseJarManifest } from './lib/zip.mjs';

const USAGE = 'Usage: node scripts/release/check-launcher-jar.mjs <jar> --platform <win|linux|mac-aarch64|mac|linux-aarch64> [--version <semver>]';

/** JavaFX classifier -> expected native format and CPU. */
export const PLATFORMS = Object.freeze({
  win: { format: 'pe', arch: 'x86_64', extension: '.dll', glass: 'glass.dll', label: 'Windows x64' },
  linux: { format: 'elf', arch: 'x86_64', extension: '.so', glass: 'libglass.so', label: 'Linux x64' },
  'linux-aarch64': { format: 'elf', arch: 'arm64', extension: '.so', glass: 'libglass.so', label: 'Linux arm64' },
  mac: { format: 'macho', arch: 'x86_64', extension: '.dylib', glass: 'libglass.dylib', label: 'macOS x64' },
  'mac-aarch64': { format: 'macho', arch: 'arm64', extension: '.dylib', glass: 'libglass.dylib', label: 'macOS Apple Silicon' },
});

const NATIVE_EXTENSIONS = ['.dll', '.so', '.dylib', '.jnilib'];
const REQUIRED_CLASSES = ['dev/vanta/launcher/Main.class', 'javafx/application/Application.class', 'javafx/scene/control/Control.class'];

/**
 * Identifies the binary format and CPU of a native library from its header.
 * @param {Buffer} bytes at least the first 4 KB of the file
 * @returns {{ format: 'pe'|'elf'|'macho'|'unknown', archs: string[] }}
 */
export function nativeArchitecture(bytes) {
  if (bytes.length >= 64 && bytes[0] === 0x4d && bytes[1] === 0x5a) {
    const pe = bytes.readUInt32LE(0x3c);
    if (pe + 6 <= bytes.length && bytes.readUInt32LE(pe) === 0x00004550) {
      const machine = bytes.readUInt16LE(pe + 4);
      const arch = { 0x8664: 'x86_64', 0xaa64: 'arm64', 0x014c: 'x86' }[machine] ?? `pe-machine-0x${machine.toString(16)}`;
      return { format: 'pe', archs: [arch] };
    }
    return { format: 'unknown', archs: [] };
  }
  if (bytes.length >= 20 && bytes.readUInt32BE(0) === 0x7f454c46) {
    const littleEndian = bytes[5] === 1;
    const machine = littleEndian ? bytes.readUInt16LE(18) : bytes.readUInt16BE(18);
    const arch = { 0x3e: 'x86_64', 0xb7: 'arm64', 0x03: 'x86', 0x28: 'arm' }[machine] ?? `elf-machine-0x${machine.toString(16)}`;
    return { format: 'elf', archs: [arch] };
  }
  const machoCpu = (cpu) => ({ 0x01000007: 'x86_64', 0x0100000c: 'arm64', 0x00000007: 'x86', 0x0000000c: 'arm' }[cpu >>> 0] ?? `macho-cpu-0x${(cpu >>> 0).toString(16)}`);
  if (bytes.length >= 8) {
    const magicLE = bytes.readUInt32LE(0);
    if (magicLE === 0xfeedfacf || magicLE === 0xfeedface) return { format: 'macho', archs: [machoCpu(bytes.readUInt32LE(4))] };
    const magicBE = bytes.readUInt32BE(0);
    if (magicBE === 0xcafebabe || magicBE === 0xcafebabf) {
      // Universal binary: big-endian header, one fat_arch (20 bytes, or 32 for fat_arch_64) per slice.
      const count = bytes.readUInt32BE(4);
      const stride = magicBE === 0xcafebabf ? 32 : 20;
      if (count > 0 && count < 16 && 8 + count * stride <= bytes.length) {
        const archs = [];
        for (let i = 0; i < count; i += 1) archs.push(machoCpu(bytes.readUInt32BE(8 + i * stride)));
        return { format: 'macho', archs };
      }
    }
  }
  return { format: 'unknown', archs: [] };
}

/**
 * Runs every check on an opened jar.
 * @param {{ entries: Array<{name: string, directory: boolean}>, has: (n: string) => boolean, read: (n: string) => Buffer }} zip
 * @param {{ platform: string, version?: string }} options
 * @returns {{ ok: boolean, problems: string[], natives: Array<{ name: string, format: string, archs: string[] }> }}
 */
export function checkLauncherJar(zip, { platform, version }) {
  const spec = PLATFORMS[platform];
  if (!spec) throw new Error(`unknown platform '${platform}' (expected one of ${Object.keys(PLATFORMS).join(', ')})`);
  const problems = [];
  for (const cls of REQUIRED_CLASSES) {
    if (!zip.has(cls)) problems.push(`missing ${cls}${cls.startsWith('javafx/') ? ' (JavaFX is not inside the jar)' : ''}`);
  }
  if (!zip.has('META-INF/MANIFEST.MF')) {
    problems.push('missing META-INF/MANIFEST.MF');
  } else {
    const manifest = parseJarManifest(zip.read('META-INF/MANIFEST.MF').toString('utf8'));
    if (manifest['Main-Class'] !== 'dev.vanta.launcher.Main') problems.push(`Main-Class is ${JSON.stringify(manifest['Main-Class'])}, expected dev.vanta.launcher.Main`);
    if (version && manifest['Implementation-Version'] !== version) problems.push(`Implementation-Version is ${JSON.stringify(manifest['Implementation-Version'])}, expected ${version}`);
  }
  // JavaFX ships its natives at the jar root; anything nested (com/sun/jna/<os>/...) belongs to other libraries.
  const rootNatives = zip.entries
    .filter((e) => !e.directory && !e.name.includes('/') && NATIVE_EXTENSIONS.some((ext) => e.name.toLowerCase().endsWith(ext)))
    .map((e) => e.name);
  if (!rootNatives.includes(spec.glass)) problems.push(`missing the JavaFX ${spec.label} native ${spec.glass} at the jar root`);
  const natives = [];
  for (const name of rootNatives) {
    if (!name.toLowerCase().endsWith(spec.extension)) {
      problems.push(`${name} is a native library for another operating system (a ${spec.label} jar must not contain it)`);
      continue;
    }
    const info = nativeArchitecture(zip.read(name).subarray(0, 4096));
    natives.push({ name, ...info });
    if (info.format !== spec.format) problems.push(`${name} is not a ${spec.format.toUpperCase()} binary (detected: ${info.format})`);
    else if (!info.archs.includes(spec.arch)) problems.push(`${name} is built for ${info.archs.join('+') || 'an unknown CPU'}, expected ${spec.arch}`);
  }
  return { ok: problems.length === 0, problems, natives };
}

/** CLI entry point. */
export function main(argv, log = console.log, logError = console.error) {
  const parsed = parseArgs(argv, { values: ['platform', 'version'], flags: ['help'], aliases: { h: 'help' } });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  if (parsed.positional.length !== 1) parsed.errors.push('expected exactly one jar path');
  if (!PLATFORMS[parsed.options.platform]) parsed.errors.push(`--platform must be one of ${Object.keys(PLATFORMS).join(', ')}`);
  if (parsed.options.version !== undefined && !isSemVer(parsed.options.version)) parsed.errors.push('--version must be SemVer');
  if (parsed.errors.length > 0) {
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  const jarPath = parsed.positional[0];
  let zip;
  try {
    zip = openZip(jarPath);
  } catch (error) {
    logError(`error: cannot read ${jarPath}: ${error.message}`);
    return 2;
  }
  const { ok, problems, natives } = checkLauncherJar(zip, { platform: parsed.options.platform, version: parsed.options.version });
  log(`${jarPath}: ${zip.entries.length} entries, target ${PLATFORMS[parsed.options.platform].label}`);
  for (const n of natives) log(`  native ${n.name}: ${n.format} ${n.archs.join('+')}`);
  for (const problem of problems) logError(`  FAIL ${problem}`);
  log(ok ? 'Launcher jar OK.' : 'Launcher jar check FAILED.');
  return ok ? 0 : 1;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(main(process.argv.slice(2)));
}
