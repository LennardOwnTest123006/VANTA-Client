/**
 * Minimal command-line parser shared by the release scripts (no dependencies).
 *
 * Accepts `--name value`, `--name=value` and boolean `--flag`. Positional arguments are collected in
 * `positional`. Unknown options are reported through `errors` so every script can fail fast with a
 * helpful message instead of silently ignoring a typo.
 *
 * @param {string[]} argv            arguments without the node binary and script path
 * @param {{ values?: string[], flags?: string[], aliases?: Record<string, string> }} spec
 *        `values` take an argument, `flags` do not; `aliases` map short names to long ones
 * @returns {{ options: Record<string, string>, flags: Set<string>, positional: string[], errors: string[] }}
 */
export function parseArgs(argv, spec) {
  const values = new Set(spec.values ?? []);
  const flags = new Set(spec.flags ?? []);
  const aliases = spec.aliases ?? {};
  const result = { options: {}, flags: new Set(), positional: [], errors: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--') {
      result.positional.push(...argv.slice(i + 1));
      break;
    }
    if (!arg.startsWith('-') || arg === '-') {
      result.positional.push(arg);
      continue;
    }
    let name = arg.replace(/^-+/, '');
    let inline;
    const eq = name.indexOf('=');
    if (eq >= 0) {
      inline = name.slice(eq + 1);
      name = name.slice(0, eq);
    }
    name = aliases[name] ?? name;
    if (values.has(name)) {
      if (inline !== undefined) {
        result.options[name] = inline;
      } else if (i + 1 < argv.length && !argv[i + 1].startsWith('--')) {
        result.options[name] = argv[i + 1];
        i += 1;
      } else {
        result.errors.push(`Option --${name} requires a value`);
      }
    } else if (flags.has(name)) {
      if (inline !== undefined) result.errors.push(`Flag --${name} does not take a value`);
      result.flags.add(name);
    } else {
      result.errors.push(`Unknown option --${name}`);
    }
  }
  return result;
}

/**
 * Prints usage and errors, then exits with code 2 when a parse produced errors.
 * @param {{ errors: string[] }} parsed
 * @param {string} usage
 */
export function exitOnUsageErrors(parsed, usage) {
  if (parsed.errors.length === 0) return;
  for (const error of parsed.errors) console.error(`error: ${error}`);
  console.error('');
  console.error(usage);
  process.exit(2);
}
