/**
 * Typed loader for the Local AI manifest `shared/local-ai/local-ai.json`.
 *
 * From VANTA Client 1.4.0 and VANTA Launcher 1.4.0 on, Vanta Nexus can run an assistant on the
 * player's PC. The runtime (`llama-server` from a llama.cpp GitHub release) and the model (a Qwen3
 * GGUF file from Hugging Face) are not part of any release file; both are downloaded once, only after
 * the player agrees, and verified against the sizes and SHA-256 digests of this manifest. The
 * manifest is resolved by CI (`node scripts/release/local-ai.mjs resolve`), never typed by hand, and
 * the website reads every number it shows about the Local AI from it: file names, sizes, hosts,
 * versions, licences and requirements.
 */

/** `<os>-<arch>` as the manifest and the client's platform detection write it. */
export type LocalAiPlatformKey =
  'windows-x64' | 'windows-arm64' | 'linux-x64' | 'linux-arm64' | 'macos-arm64' | 'macos-x64';

export const LOCAL_AI_PLATFORM_KEYS: readonly LocalAiPlatformKey[] = [
  'windows-x64',
  'windows-arm64',
  'linux-x64',
  'linux-arm64',
  'macos-arm64',
  'macos-x64',
];

/** One runtime archive of the manifest. */
export interface LocalAiPlatform {
  readonly key: LocalAiPlatformKey;
  readonly file: string;
  readonly url: string;
  /** Size in bytes; `0` while the manifest is the unresolved template. */
  readonly size: number;
  /** Lowercase hex SHA-256; empty while unresolved. */
  readonly sha256: string;
  /** Path of the server executable inside the extracted archive. */
  readonly serverPath: string;
}

export interface LocalAiRuntime {
  readonly name: string;
  readonly component: string;
  readonly tag: string;
  readonly license: string;
  readonly sourceUrl: string;
  readonly releaseUrl: string;
  /** In manifest order. */
  readonly platforms: readonly LocalAiPlatform[];
}

export interface LocalAiModel {
  readonly name: string;
  readonly quantization: string;
  readonly file: string;
  readonly url: string;
  /** Size in bytes; `0` while unresolved. */
  readonly size: number;
  /** Lowercase hex SHA-256; empty while unresolved. */
  readonly sha256: string;
  readonly license: string;
  readonly licenseUrl: string;
  readonly sourceUrl: string;
  readonly contextSize: number;
}

export interface LocalAiRequirements {
  readonly diskMb: number;
  readonly ramMb: number;
}

export interface LocalAiManifest {
  readonly schemaVersion: 1;
  /** ISO instant; empty while unresolved. */
  readonly resolvedAt: string;
  readonly runtime: LocalAiRuntime;
  readonly model: LocalAiModel;
  readonly requirements: LocalAiRequirements;
}

/** Thrown when the manifest does not match the shape the client and the launcher read. */
export class LocalAiManifestError extends Error {
  constructor(source: string, detail: string) {
    super(`Invalid Local AI manifest ${source}: ${detail}`);
    this.name = 'LocalAiManifestError';
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function requireString(obj: Record<string, unknown>, key: string, source: string): string {
  const value = obj[key];
  if (typeof value !== 'string')
    throw new LocalAiManifestError(source, `"${key}" must be a string`);
  return value;
}

function requireNumber(obj: Record<string, unknown>, key: string, source: string): number {
  const value = obj[key];
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
    throw new LocalAiManifestError(source, `"${key}" must be a non-negative number`);
  }
  return value;
}

function requireSha256(obj: Record<string, unknown>, source: string): string {
  const sha256 = requireString(obj, 'sha256', source).toLowerCase();
  if (sha256 !== '' && !/^[0-9a-f]{64}$/.test(sha256)) {
    throw new LocalAiManifestError(source, '"sha256" must be empty or 64 hex characters');
  }
  return sha256;
}

function requireHttpsUrl(obj: Record<string, unknown>, key: string, source: string): string {
  const url = requireString(obj, key, source);
  if (!url.startsWith('https://')) {
    throw new LocalAiManifestError(source, `"${key}" must be an https URL`);
  }
  return url;
}

function isPlatformKey(value: string): value is LocalAiPlatformKey {
  return (LOCAL_AI_PLATFORM_KEYS as readonly string[]).includes(value);
}

function parsePlatform(key: string, value: unknown, source: string): LocalAiPlatform {
  const where = `${source} runtime.platforms.${key}`;
  if (!isPlatformKey(key)) throw new LocalAiManifestError(where, 'unknown platform key');
  if (!isRecord(value)) throw new LocalAiManifestError(where, 'must be an object');
  return {
    key,
    file: requireString(value, 'file', where),
    url: requireHttpsUrl(value, 'url', where),
    size: requireNumber(value, 'size', where),
    sha256: requireSha256(value, where),
    serverPath: requireString(value, 'serverPath', where),
  };
}

function parseRuntime(value: unknown, source: string): LocalAiRuntime {
  const where = `${source} runtime`;
  if (!isRecord(value)) throw new LocalAiManifestError(where, 'must be an object');
  if (!isRecord(value.platforms)) {
    throw new LocalAiManifestError(where, '"platforms" must be an object');
  }
  const platforms = Object.entries(value.platforms).map(([key, platform]) =>
    parsePlatform(key, platform, source),
  );
  if (platforms.length === 0) throw new LocalAiManifestError(where, '"platforms" is empty');
  return {
    name: requireString(value, 'name', where),
    component: requireString(value, 'component', where),
    tag: requireString(value, 'tag', where),
    license: requireString(value, 'license', where),
    sourceUrl: requireHttpsUrl(value, 'sourceUrl', where),
    releaseUrl: requireHttpsUrl(value, 'releaseUrl', where),
    platforms,
  };
}

function parseModel(value: unknown, source: string): LocalAiModel {
  const where = `${source} model`;
  if (!isRecord(value)) throw new LocalAiManifestError(where, 'must be an object');
  return {
    name: requireString(value, 'name', where),
    quantization: requireString(value, 'quantization', where),
    file: requireString(value, 'file', where),
    url: requireHttpsUrl(value, 'url', where),
    size: requireNumber(value, 'size', where),
    sha256: requireSha256(value, where),
    license: requireString(value, 'license', where),
    licenseUrl: requireHttpsUrl(value, 'licenseUrl', where),
    sourceUrl: requireHttpsUrl(value, 'sourceUrl', where),
    contextSize: requireNumber(value, 'contextSize', where),
  };
}

function parseRequirements(value: unknown, source: string): LocalAiRequirements {
  const where = `${source} requirements`;
  if (!isRecord(value)) throw new LocalAiManifestError(where, 'must be an object');
  return {
    diskMb: requireNumber(value, 'diskMb', where),
    ramMb: requireNumber(value, 'ramMb', where),
  };
}

/**
 * Validates an untyped JSON value against the shape of `shared/schemas/local-ai.schema.json`. The
 * schema is stricter (it rejects the unresolved template: sizes of 0, empty digests); the website
 * parses the template too so a checkout before the resolve workflow ran still builds and shows that
 * the sizes are not known yet.
 * @throws LocalAiManifestError when a required field is missing or malformed.
 */
export function parseLocalAiManifest(input: unknown, source = '<inline>'): LocalAiManifest {
  if (!isRecord(input)) throw new LocalAiManifestError(source, 'root must be an object');
  if (input.schemaVersion !== 1) {
    throw new LocalAiManifestError(source, '"schemaVersion" must be 1');
  }
  return {
    schemaVersion: 1,
    resolvedAt: requireString(input, 'resolvedAt', source),
    runtime: parseRuntime(input.runtime, source),
    model: parseModel(input.model, source),
    requirements: parseRequirements(input.requirements, source),
  };
}

/** True when every size and digest is filled in, i.e. the resolve workflow has run. */
export function isLocalAiResolved(manifest: LocalAiManifest): boolean {
  return (
    manifest.resolvedAt !== '' &&
    manifest.model.size > 0 &&
    manifest.model.sha256 !== '' &&
    manifest.runtime.platforms.every((platform) => platform.size > 0 && platform.sha256 !== '')
  );
}

const PLATFORM_LABELS: Record<LocalAiPlatformKey, string> = {
  'windows-x64': 'Windows x64',
  'windows-arm64': 'Windows ARM64',
  'linux-x64': 'Linux x64',
  'linux-arm64': 'Linux ARM64',
  'macos-arm64': 'macOS Apple Silicon',
  'macos-x64': 'macOS Intel',
};

/** Human label of a platform key, e.g. `macos-arm64` becomes "macOS Apple Silicon". */
export function localAiPlatformLabel(key: LocalAiPlatformKey): string {
  return PLATFORM_LABELS[key];
}

/** Host name of an https URL (`github.com`), or the URL itself when it does not parse. */
function hostOf(url: string): string {
  try {
    return new URL(url).hostname;
  } catch {
    return url;
  }
}

/**
 * The hosts the client or the launcher contacts for the Local AI download, in manifest order and
 * without duplicates: the runtime archives first, then the model. For the committed manifest that is
 * `github.com` and `huggingface.co`, the two sites the client contacts besides Modrinth; each of them
 * redirects the download to its own file host, see `localAiRedirectTargets`.
 */
export function localAiDownloadHosts(manifest: LocalAiManifest): string[] {
  const hosts: string[] = [];
  for (const url of [...manifest.runtime.platforms.map((p) => p.url), manifest.model.url]) {
    const host = hostOf(url);
    if (!hosts.includes(host)) hosts.push(host);
  }
  return hosts;
}

/**
 * Where the two download sites send the request: GitHub answers a release asset URL with a redirect
 * to its release asset host `objects.githubusercontent.com` (documented by GitHub), Hugging Face
 * answers a resolve URL with a redirect to its CDN hosts, and the client and the launcher follow
 * those redirects. A statement that names the two sites as the only hosts has to name these targets
 * too. Only hosts the committed manifest can use are listed; an unknown host gets no entry, so the
 * site never invents a CDN name.
 */
const REDIRECT_TARGETS: ReadonlyMap<string, string> = new Map([
  ['github.com', "GitHub's release asset host objects.githubusercontent.com"],
  ['huggingface.co', "Hugging Face's CDN hosts"],
]);

/**
 * The file hosts the download hosts of the manifest redirect to, one phrase per known host in
 * `localAiDownloadHosts` order; a host this module does not know contributes nothing.
 */
export function localAiRedirectTargets(manifest: LocalAiManifest): string[] {
  return localAiDownloadHosts(manifest).flatMap((host) => {
    const target = REDIRECT_TARGETS.get(host);
    return target === undefined ? [] : [target];
  });
}

/**
 * The smallest and the largest runtime archive, so a sentence can say "11.5 MB (macOS Intel) to
 * 19.4 MB (Windows x64)" without naming a number that is not in the manifest. `undefined` while the
 * manifest is unresolved (every size is 0).
 */
export function localAiRuntimeSizeRange(
  manifest: LocalAiManifest,
): { readonly smallest: LocalAiPlatform; readonly largest: LocalAiPlatform } | undefined {
  const sized = manifest.runtime.platforms.filter((platform) => platform.size > 0);
  const [first] = sized;
  if (!first) return undefined;
  let smallest = first;
  let largest = first;
  for (const platform of sized) {
    if (platform.size < smallest.size) smallest = platform;
    if (platform.size > largest.size) largest = platform;
  }
  return { smallest, largest };
}

/** Bytes the Local AI downloads on one platform: its runtime archive plus the model. */
export function localAiDownloadBytes(
  manifest: LocalAiManifest,
  key: LocalAiPlatformKey,
): number | undefined {
  const platform = manifest.runtime.platforms.find((p) => p.key === key);
  if (!platform || platform.size === 0 || manifest.model.size === 0) return undefined;
  return platform.size + manifest.model.size;
}

/** "llama.cpp b11429" */
export function localAiRuntimeLabel(manifest: LocalAiManifest): string {
  return `${manifest.runtime.name} ${manifest.runtime.tag}`;
}

/** "Qwen3-1.7B Q8_0" */
export function localAiModelLabel(manifest: LocalAiManifest): string {
  return `${manifest.model.name} ${manifest.model.quantization}`;
}

/** Unwraps a Vite JSON module (`{ default: ... }`) or returns the value itself. */
function unwrapModule(module: unknown): unknown {
  if (isRecord(module) && 'default' in module) return module.default;
  return module;
}

/**
 * Parses the module returned by `import.meta.glob` for the manifest; `undefined` when the file does
 * not exist (a checkout without the Local AI manifest renders the pages without Local AI facts).
 * @throws LocalAiManifestError for an invalid manifest so a broken file fails the build.
 */
export function loadLocalAiManifest(
  modules: Readonly<Record<string, unknown>>,
): LocalAiManifest | undefined {
  const [entry] = Object.entries(modules);
  if (!entry) return undefined;
  const [path, module] = entry;
  return parseLocalAiManifest(unwrapModule(module), path);
}

/** The repository's Local AI manifest, loaded at build time (`undefined` without the file). */
export const localAi: LocalAiManifest | undefined = loadLocalAiManifest(
  import.meta.glob('../../../shared/local-ai/local-ai.json', { eager: true }),
);
