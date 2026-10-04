#!/usr/bin/env node
/**
 * Markdown link checker for docs/ and website/content/.
 *
 *   node scripts/release/check-links.mjs docs website/content [--external] [--quiet]
 *
 * Checks every `*.md` file below the given directories (or single files):
 *   - relative links and images must resolve to an existing file or directory,
 *   - `#fragment` links (same file or `other.md#fragment`) must match a heading in the target,
 *   - `http(s)` links must be syntactically valid; with --external they are also fetched (HEAD/GET),
 *   - `mailto:` links must contain an address; other schemes are rejected.
 * Links inside fenced or inline code are ignored. Exit code 1 when anything is broken.
 *
 * Heading anchors follow GitHub's algorithm (lower-case, punctuation removed, spaces to hyphens,
 * duplicates suffixed with -1, -2, ...), which is also what the website renderer produces.
 */
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from './lib/args.mjs';
import { parseFrontMatter } from './lib/front-matter.mjs';

const USAGE = `Usage: node scripts/release/check-links.mjs <dir-or-file> [more...] [--external] [--quiet] [--timeout <ms>]`;

/**
 * @typedef {{ file: string, line: number, target: string, kind: 'relative'|'anchor'|'external'|'mailto'|'other', message: string }} LinkProblem
 */

/** Recursively lists markdown files. */
export function listMarkdownFiles(paths) {
  const files = [];
  const visit = (path) => {
    const stat = statSync(path);
    if (stat.isDirectory()) {
      for (const entry of readdirSync(path).sort()) {
        if (entry === 'node_modules' || entry.startsWith('.')) continue;
        visit(join(path, entry));
      }
    } else if (extname(path).toLowerCase() === '.md') {
      files.push(path);
    }
  };
  for (const path of paths) {
    if (!existsSync(path)) throw new Error(`path not found: ${path}`);
    visit(path);
  }
  return files;
}

/** GitHub-style heading slug. */
export function slugify(heading) {
  return heading
    .trim()
    .toLowerCase()
    .replace(/<[^>]+>/g, '')
    .replace(/[`*_~]/g, '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .replace(/ /g, '-');
}

/** Returns the set of anchors (heading slugs plus explicit `<a id>`/`{#id}` anchors) of a markdown document. */
export function headingAnchors(markdown) {
  const anchors = new Set();
  const seen = new Map();
  const body = stripCode(parseFrontMatter(markdown).body);
  for (const line of body.split('\n')) {
    const match = /^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$/.exec(line);
    if (!match) continue;
    let text = match[1];
    const explicit = /\{#([A-Za-z0-9_-]+)\}\s*$/.exec(text);
    if (explicit) {
      anchors.add(explicit[1]);
      text = text.replace(explicit[0], '');
    }
    const base = slugify(text);
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    anchors.add(count === 0 ? base : `${base}-${count}`);
  }
  for (const match of body.matchAll(/<a\s+(?:name|id)="([^"]+)"/g)) anchors.add(match[1]);
  return anchors;
}

/** Replaces fenced and inline code with spaces so links inside code are ignored (line numbers kept). */
export function stripCode(markdown) {
  let out = markdown.replace(/```[\s\S]*?```|~~~[\s\S]*?~~~/g, (block) => block.replace(/[^\n]/g, ' '));
  out = out.replace(/`[^`\n]*`/g, (span) => ' '.repeat(span.length));
  return out;
}

/**
 * Extracts links from markdown.
 * @returns {Array<{ target: string, line: number, image: boolean }>}
 */
export function extractLinks(markdown) {
  const links = [];
  const text = stripCode(markdown);
  const lines = text.split('\n');
  lines.forEach((line, index) => {
    for (const match of line.matchAll(/(!?)\[(?:[^\]]|\\\])*\]\(\s*(<[^>]*>|[^\s)]+)(?:\s+"[^"]*")?\s*\)/g)) {
      let target = match[2];
      if (target.startsWith('<') && target.endsWith('>')) target = target.slice(1, -1);
      links.push({ target, line: index + 1, image: match[1] === '!' });
    }
    const reference = /^\s{0,3}\[[^\]]+\]:\s*(\S+)/.exec(line);
    if (reference) links.push({ target: reference[1], line: index + 1, image: false });
    for (const match of line.matchAll(/<(https?:\/\/[^\s>]+)>/g)) {
      links.push({ target: match[1], line: index + 1, image: false });
    }
  });
  return links;
}

/** Classifies a link target. */
export function classify(target) {
  if (/^https?:\/\//i.test(target)) return 'external';
  if (/^mailto:/i.test(target)) return 'mailto';
  if (/^[a-z][a-z0-9+.-]*:/i.test(target)) return 'other';
  if (target.startsWith('#')) return 'anchor';
  return 'relative';
}

/**
 * Checks every link of one file.
 * @param {string} file absolute path
 * @param {{ anchorsOf: (file: string) => Set<string> }} context
 * @returns {LinkProblem[]}
 */
export function checkFile(file, context) {
  const markdown = readFileSync(file, 'utf8');
  const problems = [];
  for (const link of extractLinks(markdown)) {
    const kind = classify(link.target);
    const problem = (message) => problems.push({ file, line: link.line, target: link.target, kind, message });
    if (kind === 'external') {
      try {
        new URL(link.target);
      } catch {
        problem('malformed URL');
      }
      continue;
    }
    if (kind === 'mailto') {
      if (!/^mailto:[^\s@]+@[^\s@]+\.[^\s@]+$/i.test(link.target)) problem('malformed mailto link');
      continue;
    }
    if (kind === 'other') {
      problem('unsupported URL scheme');
      continue;
    }
    if (kind === 'anchor') {
      const fragment = decodeURIComponent(link.target.slice(1));
      if (!context.anchorsOf(file).has(fragment)) problem(`no heading with anchor #${fragment} in this file`);
      continue;
    }
    // relative path
    if (link.target.startsWith('/')) {
      problem('root-absolute path; use a relative path or a full URL');
      continue;
    }
    const [pathPart, fragment] = link.target.split('#');
    const cleanPath = decodeURIComponent(pathPart.split('?')[0]);
    const resolved = resolve(dirname(file), cleanPath);
    if (!existsSync(resolved)) {
      problem(`target does not exist: ${relative(process.cwd(), resolved)}`);
      continue;
    }
    if (fragment !== undefined && fragment !== '') {
      if (extname(resolved).toLowerCase() !== '.md') {
        problem('fragment on a non-markdown target cannot be verified');
      } else if (!context.anchorsOf(resolved).has(decodeURIComponent(fragment))) {
        problem(`no heading with anchor #${fragment} in ${relative(process.cwd(), resolved)}`);
      }
    }
  }
  return problems;
}

/** Fetches an external URL (HEAD, falling back to GET) and returns a problem message or null. */
export async function probeExternal(url, { timeoutMs = 15_000, fetchImpl = fetch } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    let response = await fetchImpl(url, { method: 'HEAD', redirect: 'follow', signal: controller.signal });
    if (response.status === 405 || response.status === 403) {
      response = await fetchImpl(url, { method: 'GET', redirect: 'follow', signal: controller.signal });
    }
    return response.ok ? null : `HTTP ${response.status}`;
  } catch (error) {
    return error.name === 'AbortError' ? 'timed out' : error.message;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Checks all markdown files below `paths`.
 * @param {string[]} paths
 * @param {{ external?: boolean, timeoutMs?: number, fetchImpl?: typeof fetch }} options
 * @returns {Promise<{ files: number, links: number, problems: LinkProblem[], externalChecked: number }>}
 */
export async function checkLinks(paths, options = {}) {
  const files = listMarkdownFiles(paths.map((p) => resolve(p)));
  const anchorCache = new Map();
  const context = {
    anchorsOf(file) {
      if (!anchorCache.has(file)) anchorCache.set(file, headingAnchors(readFileSync(file, 'utf8')));
      return anchorCache.get(file);
    },
  };
  const problems = [];
  let links = 0;
  const externals = new Map();
  for (const file of files) {
    const fileLinks = extractLinks(readFileSync(file, 'utf8'));
    links += fileLinks.length;
    problems.push(...checkFile(file, context));
    if (options.external) {
      for (const link of fileLinks) {
        if (classify(link.target) === 'external') {
          if (!externals.has(link.target)) externals.set(link.target, []);
          externals.get(link.target).push({ file, line: link.line });
        }
      }
    }
  }
  let externalChecked = 0;
  for (const [url, usages] of externals) {
    externalChecked += 1;
    const message = await probeExternal(url, { timeoutMs: options.timeoutMs, fetchImpl: options.fetchImpl });
    if (message) {
      for (const usage of usages) problems.push({ ...usage, target: url, kind: 'external', message });
    }
  }
  return { files: files.length, links, problems, externalChecked };
}

/** CLI entry point. */
export async function main(argv, log = console.log, logError = console.error) {
  const parsed = parseArgs(argv, { values: ['timeout'], flags: ['external', 'quiet', 'help'], aliases: { h: 'help', q: 'quiet' } });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  if (parsed.positional.length === 0) parsed.errors.push('expected at least one directory or file');
  if (parsed.errors.length > 0) {
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  try {
    const result = await checkLinks(parsed.positional, {
      external: parsed.flags.has('external'),
      timeoutMs: parsed.options.timeout ? Number(parsed.options.timeout) : undefined,
    });
    for (const problem of result.problems) {
      logError(`${relative(process.cwd(), problem.file)}:${problem.line}: ${problem.message} (${problem.target})`);
    }
    const summary = `${result.files} file(s), ${result.links} link(s), ${result.problems.length} problem(s)` +
      (parsed.flags.has('external') ? `, ${result.externalChecked} external URL(s) fetched` : ', external URLs not fetched');
    if (!parsed.flags.has('quiet') || result.problems.length > 0) log(summary);
    return result.problems.length === 0 ? 0 : 1;
  } catch (error) {
    logError(`error: ${error.message}`);
    return 2;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(await main(process.argv.slice(2)));
}
