import { compareVersionsDesc, githubReleasePageUrl } from './releases';

/**
 * Typed loader for the bundle manifests in `shared/releases/bundles/*.json`.
 *
 * A bundle is the full release zip `VantaClient-<version>-Release.zip`: the published files of one
 * client release and one launcher release plus the documentation, built by the bundle workflow from
 * the files the release manifests name and uploaded to the GitHub Release `v<version>`. Its manifest
 * is the single source of truth for the zip's download URL, size, checksum and contents; it lives in
 * a subfolder so the loaders of `shared/releases/*.json` (the website's release manifests, the
 * launcher tooling) never mistake it for a product release. An empty `file.downloadUrl` means "not
 * published yet": the download page offers only a published bundle ({@link latestBundle}) and shows
 * no card at all while there is none.
 */

export type BundleChannel = 'stable' | 'beta';

/** The zip itself. */
export interface BundleFile {
  readonly name: string;
  /** Empty string while the bundle workflow has not published the zip yet. */
  readonly downloadUrl: string;
  /** Size in bytes; `0` while unpublished. */
  readonly size: number;
  /** Lowercase hex SHA-256; empty while unpublished. */
  readonly sha256: string;
}

/** One file inside the zip, path relative to its top-level folder (e.g. `client/vanta-client-1.3.0.jar`). */
export interface BundleEntry {
  readonly path: string;
  /** Size in bytes. */
  readonly size: number;
  /** Lowercase hex SHA-256. */
  readonly sha256: string;
}

export interface BundleManifest {
  readonly schemaVersion: 1;
  readonly kind: 'bundle';
  readonly version: string;
  /** Version of the client release inside the zip. */
  readonly clientVersion: string;
  /** Version of the launcher release inside the zip. */
  readonly launcherVersion: string;
  readonly minecraftVersion: string;
  /** ISO date `YYYY-MM-DD`. */
  readonly releaseDate: string;
  readonly channel: BundleChannel;
  readonly file: BundleFile;
  /**
   * Every file inside the zip except its SHA256SUMS.txt, in zip order. The bundle workflow writes the
   * manifest from the real zip, so a committed manifest always lists them (the schema requires at least
   * one entry); the parser tolerates an empty list.
   */
  readonly contents: readonly BundleEntry[];
}

/** Thrown when a bundle manifest does not match the schema. The message names the file and the field. */
export class BundleManifestError extends Error {
  constructor(source: string, detail: string) {
    super(`Invalid bundle manifest ${source}: ${detail}`);
    this.name = 'BundleManifestError';
  }
}

/** Same SemVer shape as the release manifests accept. */
const SEMVER = /^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function requireString(obj: Record<string, unknown>, key: string, source: string): string {
  const value = obj[key];
  if (typeof value !== 'string') throw new BundleManifestError(source, `"${key}" must be a string`);
  return value;
}

function requireNumber(obj: Record<string, unknown>, key: string, source: string): number {
  const value = obj[key];
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
    throw new BundleManifestError(source, `"${key}" must be a non-negative number`);
  }
  return value;
}

function requireSemVer(obj: Record<string, unknown>, key: string, source: string): string {
  const value = requireString(obj, key, source);
  if (!SEMVER.test(value))
    throw new BundleManifestError(source, `"${key}" is not SemVer: ${value}`);
  return value;
}

function requireSha256(obj: Record<string, unknown>, source: string): string {
  const sha256 = requireString(obj, 'sha256', source).toLowerCase();
  if (sha256 !== '' && !/^[0-9a-f]{64}$/.test(sha256)) {
    throw new BundleManifestError(source, '"sha256" must be empty or 64 hex characters');
  }
  return sha256;
}

function parseBundleFile(value: unknown, source: string): BundleFile {
  if (!isRecord(value)) throw new BundleManifestError(source, '"file" must be an object');
  const where = `${source} file`;
  const name = requireString(value, 'name', where);
  if (name.trim() === '') throw new BundleManifestError(where, '"name" must not be empty');
  const downloadUrl = requireString(value, 'downloadUrl', where);
  if (downloadUrl !== '' && !downloadUrl.startsWith('https://')) {
    throw new BundleManifestError(where, '"downloadUrl" must be empty or an https URL');
  }
  return {
    name,
    downloadUrl,
    size: requireNumber(value, 'size', where),
    sha256: requireSha256(value, where),
  };
}

function parseEntry(value: unknown, index: number, source: string): BundleEntry {
  if (!isRecord(value)) {
    throw new BundleManifestError(source, `contents[${index}] must be an object`);
  }
  const where = `${source} contents[${index}]`;
  const path = requireString(value, 'path', where);
  if (path.trim() === '') throw new BundleManifestError(where, '"path" must not be empty');
  return { path, size: requireNumber(value, 'size', where), sha256: requireSha256(value, where) };
}

/**
 * Validates an untyped JSON value along the lines of the bundle manifest schema
 * (`shared/schemas/bundle-manifest.schema.json`; the schema is stricter: it requires a non-empty
 * `contents` list and an entry size of at least 1).
 * @throws BundleManifestError when a required field is missing or malformed.
 */
export function parseBundleManifest(input: unknown, source = '<inline>'): BundleManifest {
  if (!isRecord(input)) throw new BundleManifestError(source, 'root must be an object');
  if (input.schemaVersion !== 1) throw new BundleManifestError(source, '"schemaVersion" must be 1');
  if (input.kind !== 'bundle') throw new BundleManifestError(source, '"kind" must be "bundle"');
  const version = requireSemVer(input, 'version', source);
  const clientVersion = requireSemVer(input, 'clientVersion', source);
  const launcherVersion = requireSemVer(input, 'launcherVersion', source);
  const minecraftVersion = requireString(input, 'minecraftVersion', source);
  if (minecraftVersion.trim() === '') {
    throw new BundleManifestError(source, '"minecraftVersion" must not be empty');
  }
  const releaseDate = requireString(input, 'releaseDate', source);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(releaseDate)) {
    throw new BundleManifestError(source, '"releaseDate" must be YYYY-MM-DD');
  }
  const channel = requireString(input, 'channel', source);
  if (channel !== 'stable' && channel !== 'beta') {
    throw new BundleManifestError(source, '"channel" must be stable or beta');
  }
  if (!Array.isArray(input.contents)) {
    throw new BundleManifestError(source, '"contents" must be an array');
  }
  return {
    schemaVersion: 1,
    kind: 'bundle',
    version,
    clientVersion,
    launcherVersion,
    minecraftVersion,
    releaseDate,
    channel,
    file: parseBundleFile(input.file, source),
    contents: input.contents.map((entry, index) => parseEntry(entry, index, source)),
  };
}

/** Unwraps a Vite JSON module (`{ default: ... }`) or returns the value itself. */
function unwrapModule(module: unknown): unknown {
  if (isRecord(module) && 'default' in module) return module.default;
  return module;
}

/**
 * Parses every module returned by `import.meta.glob` and sorts the result by version, newest first.
 * @throws BundleManifestError for the first invalid manifest so a broken bundle fails the build.
 */
export function loadBundleManifests(modules: Readonly<Record<string, unknown>>): BundleManifest[] {
  return Object.entries(modules)
    .map(([path, module]) => parseBundleManifest(unwrapModule(module), path))
    .sort((a, b) => compareVersionsDesc(a.version, b.version));
}

/** True when the zip has a real download URL. */
export function isBundlePublished(bundle: BundleManifest): boolean {
  return bundle.file.downloadUrl !== '';
}

/**
 * The bundle the website offers: the newest published one on the given channel (stable by default),
 * or `undefined` while none is published. Unlike the product releases there is no fallback to an
 * unpublished manifest: without a zip to download there is nothing to show.
 */
export function latestBundle(
  bundles: readonly BundleManifest[],
  channel: BundleChannel = 'stable',
): BundleManifest | undefined {
  return bundles
    .filter((bundle) => bundle.channel === channel && isBundlePublished(bundle))
    .sort((a, b) => compareVersionsDesc(a.version, b.version))[0];
}

/** True when the zip holds exactly this client version and this launcher version. */
export function bundleMatches(
  bundle: BundleManifest,
  clientVersion: string | undefined,
  launcherVersion: string | undefined,
): boolean {
  return bundle.clientVersion === clientVersion && bundle.launcherVersion === launcherVersion;
}

/**
 * The GitHub release page of a bundle, derived from its download URL (see `githubReleasePageUrl`);
 * `undefined` while unpublished or when the zip is hosted somewhere else.
 */
export function bundleReleasePageUrl(bundle: BundleManifest): string | undefined {
  return githubReleasePageUrl(bundle.file.downloadUrl);
}

/** All bundle manifests of the repository, loaded at build time (none until the first bundle is published). */
export const bundles: readonly BundleManifest[] = loadBundleManifests(
  import.meta.glob('../../../shared/releases/bundles/*.json', { eager: true }),
);
