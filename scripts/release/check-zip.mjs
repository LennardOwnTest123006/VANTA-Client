#!/usr/bin/env node
/**
 * Checks that a ZIP archive (zip or jar) opens and contains the given non-empty files.
 *
 *   node scripts/release/check-zip.mjs <archive.zip> <entry> [<entry>...]
 *
 * Entry names use '/' (names an archiver wrote with '\' are normalised). Used by the release workflow to prove the
 * Windows portable zip holds a runnable app image before it is published. Exit codes: 0 ok, 1 missing or empty
 * entries, 2 usage error or unreadable archive.
 */
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { openZip } from './lib/zip.mjs';

/**
 * @param {{ get: (name: string) => ({ size: number, directory: boolean } | undefined) }} zip
 * @param {string[]} required
 * @returns {string[]} problems
 */
export function missingEntries(zip, required) {
  const problems = [];
  for (const name of required) {
    const entry = zip.get(name.replace(/\\/g, '/'));
    if (!entry) problems.push(`missing: ${name}`);
    else if (entry.directory) problems.push(`is a directory: ${name}`);
    else if (entry.size === 0) problems.push(`empty: ${name}`);
  }
  return problems;
}

/** CLI entry point. */
export function main(argv, log = console.log, logError = console.error) {
  if (argv.includes('--help') || argv.includes('-h')) {
    log('Usage: node scripts/release/check-zip.mjs <archive.zip> <entry> [<entry>...]');
    return 0;
  }
  if (argv.length < 2 || argv.some((a) => a.startsWith('--'))) {
    logError('Usage: node scripts/release/check-zip.mjs <archive.zip> <entry> [<entry>...]');
    return 2;
  }
  const [archive, ...required] = argv;
  let zip;
  try {
    zip = openZip(archive);
  } catch (error) {
    logError(`error: cannot read ${archive}: ${error.message}`);
    return 2;
  }
  const problems = missingEntries(zip, required);
  for (const problem of problems) logError(`  ${problem}`);
  if (problems.length > 0) {
    logError(`${archive}: ${problems.length} of ${required.length} required entries missing or empty.`);
    return 1;
  }
  log(`${archive}: ${zip.entries.length} entries, all ${required.length} required files present.`);
  return 0;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(main(process.argv.slice(2)));
}
