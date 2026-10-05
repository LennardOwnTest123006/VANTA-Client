#!/usr/bin/env node
/**
 * Finds the Fabric API jar that the client build resolved into the Gradle cache and proves it is the genuine,
 * unmodified FabricMC artifact before the release ships it.
 *
 *   node scripts/release/fabric-api-jar.mjs [--version 0.141.6+1.21.11] [--gradle-user-home ~/.gradle] [--copy-to dist]
 *
 * Gradle stores every downloaded artifact as caches/modules-2/files-2.1/<group>/<module>/<version>/<sha1>/<file>,
 * where <sha1> is the SHA-1 of the file as published on the repository. The script requires exactly one
 * fabric-api-<version>.jar below caches/modules-2, recomputes its SHA-1 and compares it with that directory name,
 * and checks the jar itself (fabric.mod.json id "fabric-api", the expected version, license Apache-2.0, and the
 * bundled LICENSE-fabric-api). With --copy-to it copies the jar unchanged into that directory.
 * The version defaults to fabric_api_version in client/gradle.properties; the Gradle user home defaults to
 * $GRADLE_USER_HOME or ~/.gradle.
 *
 * Prints the path of the jar (or of the copy) on the last line. Exit codes: 0 ok, 1 not found or check failed,
 * 2 usage error.
 */
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { homedir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from './lib/args.mjs';
import { REPO_ROOT, readToolchain } from './lib/repo.mjs';
import { openZip } from './lib/zip.mjs';

const USAGE = 'Usage: node scripts/release/fabric-api-jar.mjs [--version <fabric api version>] [--gradle-user-home <dir>] [--copy-to <dir>] [--root <repo root>]';

/** Recursively collects files named `fileName` below `dir`. */
export function findFiles(dir, fileName) {
  const found = [];
  if (!existsSync(dir)) return found;
  const stack = [dir];
  while (stack.length > 0) {
    const current = stack.pop();
    let names;
    try {
      names = readdirSync(current);
    } catch {
      continue;
    }
    for (const name of names) {
      const path = join(current, name);
      let stat;
      try {
        stat = statSync(path);
      } catch {
        continue;
      }
      if (stat.isDirectory()) stack.push(path);
      else if (name === fileName && stat.isFile()) found.push(path);
    }
  }
  return found.sort();
}

/**
 * Locates and verifies the Fabric API jar.
 * @param {{ version: string, gradleUserHome: string }} params
 * @returns {{ path: string, sha1: string, sha256: string, size: number }}
 */
export function locateFabricApiJar({ version, gradleUserHome }) {
  const fileName = `fabric-api-${version}.jar`;
  const cache = join(gradleUserHome, 'caches', 'modules-2');
  const matches = findFiles(cache, fileName);
  if (matches.length === 0) throw new Error(`${fileName} is not in ${cache}; build the client first (it resolves Fabric API)`);
  if (matches.length > 1) throw new Error(`expected exactly one ${fileName} in ${cache}, found ${matches.length}:\n  ${matches.join('\n  ')}`);
  const path = matches[0];
  const bytes = readFileSync(path);
  const sha1 = createHash('sha1').update(bytes).digest('hex');
  const recorded = basename(dirname(path));
  if (!/^[0-9a-f]{40}$/.test(recorded)) throw new Error(`${path} is not inside a Gradle files-2.1 <sha1> directory`);
  if (sha1 !== recorded) throw new Error(`${path}: SHA-1 ${sha1} does not match the checksum Gradle recorded for the download (${recorded})`);
  const zip = openZip(path);
  if (!zip.has('fabric.mod.json')) throw new Error(`${path} has no fabric.mod.json`);
  const mod = JSON.parse(zip.read('fabric.mod.json').toString('utf8'));
  if (mod.id !== 'fabric-api') throw new Error(`${path}: fabric.mod.json id is ${JSON.stringify(mod.id)}, expected "fabric-api"`);
  if (mod.version !== version) throw new Error(`${path}: fabric.mod.json version is ${JSON.stringify(mod.version)}, expected ${JSON.stringify(version)}`);
  if (mod.license !== 'Apache-2.0') throw new Error(`${path}: license is ${JSON.stringify(mod.license)}, expected "Apache-2.0"`);
  if (!zip.has('LICENSE-fabric-api')) throw new Error(`${path} does not contain LICENSE-fabric-api`);
  return { path, sha1, sha256: createHash('sha256').update(bytes).digest('hex'), size: bytes.length };
}

/** CLI entry point. */
export function main(argv, log = console.log, logError = console.error, env = process.env) {
  const parsed = parseArgs(argv, { values: ['version', 'gradle-user-home', 'copy-to', 'root'], flags: ['help'], aliases: { h: 'help' } });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  if (parsed.positional.length > 0) parsed.errors.push(`unexpected argument '${parsed.positional[0]}'`);
  if (parsed.errors.length > 0) {
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  const o = parsed.options;
  const root = o.root ? resolve(o.root) : REPO_ROOT;
  const version = o.version ?? readToolchain(root).fabricApiVersion;
  const gradleUserHome = resolve(o['gradle-user-home'] ?? env.GRADLE_USER_HOME ?? join(homedir(), '.gradle'));
  try {
    const jar = locateFabricApiJar({ version, gradleUserHome });
    log(`Fabric API ${version}: ${jar.size} bytes, sha1 ${jar.sha1} (matches the Gradle cache record), sha256 ${jar.sha256}`);
    let out = jar.path;
    if (o['copy-to']) {
      const dest = resolve(o['copy-to']);
      mkdirSync(dest, { recursive: true });
      out = join(dest, basename(jar.path));
      copyFileSync(jar.path, out);
    }
    log(out);
    return 0;
  } catch (error) {
    logError(`error: ${error.message}`);
    return 1;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(main(process.argv.slice(2)));
}
