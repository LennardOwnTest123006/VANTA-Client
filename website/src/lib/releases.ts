/**
 * Typed loader for the release manifests in `shared/releases/*.json`.
 *
 * The manifests are the single source of truth for versions, release dates, file sizes, checksums
 * and download URLs (see RELEASE.md). They are read at build time through `import.meta.glob`, parsed
 * defensively and sorted so the newest release of each product comes first. A manifest whose
 * `downloadUrl` is empty means "not published yet" and is rendered exactly like that.
 */

export type Product = 'client' | 'launcher';
export type Channel = 'stable' | 'beta' | 'alpha';

export interface ReleaseFile {
  readonly name: string;
  /** Empty string while the release workflow has not published the file yet. */
  readonly downloadUrl: string;
  /** Size in bytes; `0` while unpublished. */
  readonly size: number;
  /** Lowercase hex SHA-256; empty while unpublished. */
  readonly sha256: string;
}

export interface ReleaseManifest {
  readonly schemaVersion: 1;
  readonly product: Product;
  readonly version: string;
  readonly minecraftVersion: string;
  readonly fabricVersion: string;
  readonly fabricApiVersion: string;
  readonly javaVersion: number;
  /** ISO date `YYYY-MM-DD`. */
  readonly releaseDate: string;
  readonly channel: Channel;
  readonly files: readonly ReleaseFile[];
  /** Repository-relative path of the release notes markdown, when present. */
  readonly changelog: string | undefined;
}

/** Thrown when a manifest does not match the schema. The message names the file and the field. */
export class ReleaseManifestError extends Error {
  constructor(source: string, detail: string) {
    super(`Invalid release manifest ${source}: ${detail}`);
    this.name = 'ReleaseManifestError';
  }
}

const SEMVER = /^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function requireString(obj: Record<string, unknown>, key: string, source: string): string {
  const value = obj[key];
  if (typeof value !== 'string')
    throw new ReleaseManifestError(source, `"${key}" must be a string`);
  return value;
}

function requireNumber(obj: Record<string, unknown>, key: string, source: string): number {
  const value = obj[key];
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
    throw new ReleaseManifestError(source, `"${key}" must be a non-negative number`);
  }
  return value;
}

function parseFile(value: unknown, index: number, source: string): ReleaseFile {
  if (!isRecord(value)) throw new ReleaseManifestError(source, `files[${index}] must be an object`);
  const where = `${source} files[${index}]`;
  const name = requireString(value, 'name', where);
  if (name.trim() === '') throw new ReleaseManifestError(where, '"name" must not be empty');
  const downloadUrl = requireString(value, 'downloadUrl', where);
  if (downloadUrl !== '' && !downloadUrl.startsWith('https://')) {
    throw new ReleaseManifestError(where, '"downloadUrl" must be empty or an https URL');
  }
  const sha256 = requireString(value, 'sha256', where).toLowerCase();
  if (sha256 !== '' && !/^[0-9a-f]{64}$/.test(sha256)) {
    throw new ReleaseManifestError(where, '"sha256" must be empty or 64 hex characters');
  }
  return { name, downloadUrl, size: requireNumber(value, 'size', where), sha256 };
}

/**
 * Validates an untyped JSON value against the release manifest schema.
 * @throws ReleaseManifestError when a required field is missing or malformed.
 */
export function parseReleaseManifest(input: unknown, source = '<inline>'): ReleaseManifest {
  if (!isRecord(input)) throw new ReleaseManifestError(source, 'root must be an object');
  if (input.schemaVersion !== 1)
    throw new ReleaseManifestError(source, '"schemaVersion" must be 1');
  const product = requireString(input, 'product', source);
  if (product !== 'client' && product !== 'launcher') {
    throw new ReleaseManifestError(source, '"product" must be "client" or "launcher"');
  }
  const version = requireString(input, 'version', source);
  if (!SEMVER.test(version))
    throw new ReleaseManifestError(source, `"version" is not SemVer: ${version}`);
  const releaseDate = requireString(input, 'releaseDate', source);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(releaseDate)) {
    throw new ReleaseManifestError(source, '"releaseDate" must be YYYY-MM-DD');
  }
  const channel = requireString(input, 'channel', source);
  if (channel !== 'stable' && channel !== 'beta' && channel !== 'alpha') {
    throw new ReleaseManifestError(source, '"channel" must be stable, beta or alpha');
  }
  if (!Array.isArray(input.files) || input.files.length === 0) {
    throw new ReleaseManifestError(source, '"files" must be a non-empty array');
  }
  const changelog = input.changelog;
  if (changelog !== undefined && typeof changelog !== 'string') {
    throw new ReleaseManifestError(source, '"changelog" must be a string when present');
  }
  return {
    schemaVersion: 1,
    product,
    version,
    minecraftVersion: requireString(input, 'minecraftVersion', source),
    fabricVersion: requireString(input, 'fabricVersion', source),
    fabricApiVersion: requireString(input, 'fabricApiVersion', source),
    javaVersion: requireNumber(input, 'javaVersion', source),
    releaseDate,
    channel,
    files: input.files.map((file, index) => parseFile(file, index, source)),
    changelog,
  };
}

/** Compares two SemVer strings; newer versions sort first (descending). */
export function compareVersionsDesc(a: string, b: string): number {
  const pa = SEMVER.exec(a);
  const pb = SEMVER.exec(b);
  if (!pa || !pb) return a < b ? 1 : a > b ? -1 : 0;
  for (let i = 1; i <= 3; i += 1) {
    const diff = Number(pb[i]) - Number(pa[i]);
    if (diff !== 0) return diff;
  }
  // A release without pre-release tag is newer than one with (1.0.0 > 1.0.0-beta.1).
  const preA = pa[4];
  const preB = pb[4];
  if (preA === preB) return 0;
  if (preA === undefined) return -1;
  if (preB === undefined) return 1;
  return preA < preB ? 1 : -1;
}

/** Unwraps a Vite JSON module (`{ default: ... }`) or returns the value itself. */
function unwrapModule(module: unknown): unknown {
  if (isRecord(module) && 'default' in module) return module.default;
  return module;
}

/**
 * Parses every module returned by `import.meta.glob` and sorts the result by product (client first)
 * and version (newest first).
 * @throws ReleaseManifestError for the first invalid manifest so a broken release fails the build.
 */
export function loadReleaseManifests(
  modules: Readonly<Record<string, unknown>>,
): ReleaseManifest[] {
  const manifests = Object.entries(modules).map(([path, module]) =>
    parseReleaseManifest(unwrapModule(module), path),
  );
  return manifests.sort((a, b) => {
    if (a.product !== b.product) return a.product === 'client' ? -1 : 1;
    return compareVersionsDesc(a.version, b.version);
  });
}

/** Newest manifest of the given product on the given channel (stable by default). */
export function latestRelease(
  manifests: readonly ReleaseManifest[],
  product: Product,
  channel: Channel = 'stable',
): ReleaseManifest | undefined {
  return manifests.find((m) => m.product === product && m.channel === channel);
}

/**
 * Picks the file to offer for download: the first file whose name ends with one of the preferred
 * extensions (in order), falling back to the first file of the manifest.
 */
export function pickFile(
  manifest: ReleaseManifest,
  preferredExtensions: readonly string[] = [],
): ReleaseFile | undefined {
  for (const extension of preferredExtensions) {
    const match = manifest.files.find((file) => file.name.toLowerCase().endsWith(extension));
    if (match) return match;
  }
  return manifest.files[0];
}

/** True when the manifest has at least one file with a real download URL. */
export function isPublished(manifest: ReleaseManifest): boolean {
  return manifest.files.some((file) => file.downloadUrl !== '');
}

/**
 * `https://github.com/<owner>/<repo>/releases/download/<tag>/<file>` — the only shape of release asset
 * URL the release workflow writes. Owner, repository, tag and file are single path segments.
 */
const GITHUB_RELEASE_ASSET =
  /^https:\/\/github\.com\/([A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)\/([A-Za-z0-9._-]+)\/releases\/download\/([^/?#\s]+)\/([^/?#\s]+)$/;

/**
 * Derives the GitHub release page from a release asset download URL:
 * `https://github.com/o/r/releases/download/<tag>/<file>` → `https://github.com/o/r/releases/tag/<tag>`.
 * Returns `undefined` for every URL that does not have exactly that shape (other hosts, mirrors, query
 * strings, extra path segments), so the website never links to a page it merely guessed.
 */
export function githubReleasePageUrl(downloadUrl: string): string | undefined {
  const match = GITHUB_RELEASE_ASSET.exec(downloadUrl);
  if (!match) return undefined;
  const [, owner, repo, tag] = match;
  if (!owner || !repo || !tag || repo === '.' || repo === '..' || tag === '.' || tag === '..') {
    return undefined;
  }
  return `https://github.com/${owner}/${repo}/releases/tag/${tag}`;
}

/**
 * The GitHub release page of a manifest, derived from the first file whose `downloadUrl` is a GitHub
 * release asset URL (see {@link githubReleasePageUrl}); `undefined` while nothing is published.
 */
export function releasePageUrl(manifest: ReleaseManifest): string | undefined {
  for (const file of manifest.files) {
    const page = githubReleasePageUrl(file.downloadUrl);
    if (page) return page;
  }
  return undefined;
}

/** All release manifests of the repository, loaded at build time. */
export const releases: readonly ReleaseManifest[] = loadReleaseManifests(
  import.meta.glob('../../../shared/releases/*.json', { eager: true }),
);
