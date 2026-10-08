import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * What the build under test was made from: the release manifests in `shared/releases/`, the bundle
 * manifests in `shared/releases/bundles/`, the Local AI manifest in `shared/local-ai/` and the
 * release notes in `content/changelog/`, read from disk. The tests derive their expectations from
 * these files, so they hold before and after the release workflow fills a manifest, while a newer
 * version is committed but not published yet, and with or without a published full release zip.
 */

const releasesDir = fileURLToPath(new URL('../../shared/releases/', import.meta.url));
const bundlesDir = fileURLToPath(new URL('../../shared/releases/bundles/', import.meta.url));
const localAiFile = fileURLToPath(new URL('../../shared/local-ai/local-ai.json', import.meta.url));
const changelogDir = fileURLToPath(new URL('../content/changelog/', import.meta.url));

export type Product = 'client' | 'launcher';

export interface ManifestSummary {
  readonly product: Product;
  readonly version: string;
  readonly channel: string;
  /** At least one file has a download URL. */
  readonly published: boolean;
  readonly fileNames: readonly string[];
}

interface RawManifest {
  readonly product: Product;
  readonly version: string;
  readonly channel: string;
  readonly files: readonly { readonly name: string; readonly downloadUrl: string }[];
}

/** Compares `x.y.z` versions (pre-release tags are not used by the repository manifests). */
function compareDesc(a: string, b: string): number {
  const pa = a.split(/[.-]/).map(Number);
  const pb = b.split(/[.-]/).map(Number);
  for (let i = 0; i < 3; i += 1) {
    const diff = (pb[i] ?? 0) - (pa[i] ?? 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

export function repositoryManifests(): ManifestSummary[] {
  return readdirSync(releasesDir)
    .filter((file) => /^(client|launcher)-.+\.json$/.test(file))
    .map((file) => JSON.parse(readFileSync(`${releasesDir}${file}`, 'utf8')) as RawManifest)
    .map((raw) => ({
      product: raw.product,
      version: raw.version,
      channel: raw.channel,
      published: raw.files.some((entry) => entry.downloadUrl !== ''),
      fileNames: raw.files.map((entry) => entry.name),
    }))
    .sort((a, b) => compareDesc(a.version, b.version));
}

/** Same rule as `latestRelease` in src/lib/releases.ts: newest published stable, else newest. */
export function offeredRelease(product: Product): ManifestSummary | undefined {
  const stable = repositoryManifests().filter(
    (m) => m.product === product && m.channel === 'stable',
  );
  return stable.find((m) => m.published) ?? stable[0];
}

/** Same rule as `upcomingRelease`: a newer stable manifest that is not published yet. */
export function upcomingRelease(product: Product): ManifestSummary | undefined {
  const newest = repositoryManifests().find((m) => m.product === product && m.channel === 'stable');
  const offered = offeredRelease(product);
  return newest && !newest.published && offered && offered !== newest ? newest : undefined;
}

/**
 * Same rule as `olderReleases` in src/lib/releases.ts: the published stable manifests older than the
 * offered one, newest first. Unpublished manifests are never listed.
 */
export function olderReleases(product: Product): ManifestSummary[] {
  const offered = offeredRelease(product)?.version;
  return repositoryManifests().filter(
    (m) => m.product === product && m.channel === 'stable' && m.published && m.version !== offered,
  );
}

/** Same rule as `primaryFile` in src/lib/downloads.ts: the file the download button of a release offers. */
export function primaryFileName(manifest: ManifestSummary): string | undefined {
  if (manifest.product === 'client') {
    const exact = `vanta-client-${manifest.version}.jar`;
    return manifest.fileNames.includes(exact) ? exact : undefined;
  }
  for (const extension of ['.msi', '.exe', '.jar']) {
    const match = manifest.fileNames.find((name) => name.toLowerCase().endsWith(extension));
    if (match) return match;
  }
  return manifest.fileNames[0];
}

/** Same rule as `isWindowsSetupFile` in src/lib/downloads.ts: the `.msi`, the `.exe` and the portable app. */
export function isWindowsSetupFile(name: string): boolean {
  const lower = name.toLowerCase();
  return (
    lower.endsWith('.msi') || lower.endsWith('.exe') || lower.endsWith('-windows-portable.zip')
  );
}

/** The launcher files the WINDOWS card lists (`launcherSetupFiles`). */
export function launcherSetupFileNames(manifest: ManifestSummary): string[] {
  return manifest.fileNames.filter(isWindowsSetupFile);
}

/** The launcher files the CROSS-PLATFORM jars card lists (`launcherCrossPlatformFiles`). */
export function launcherCrossPlatformFileNames(manifest: ManifestSummary): string[] {
  return manifest.fileNames.filter((name) => !isWindowsSetupFile(name));
}

export interface BundleSummary {
  readonly version: string;
  readonly clientVersion: string;
  readonly launcherVersion: string;
  readonly channel: string;
  /** The zip has a download URL. */
  readonly published: boolean;
  readonly fileName: string;
  readonly downloadUrl: string;
  /** Paths inside the zip as the manifest lists them (without SHA256SUMS.txt). */
  readonly contentPaths: readonly string[];
}

interface RawBundle {
  readonly version: string;
  readonly clientVersion: string;
  readonly launcherVersion: string;
  readonly channel: string;
  readonly file: { readonly name: string; readonly downloadUrl: string };
  readonly contents: readonly { readonly path: string }[];
}

/** Every bundle manifest, newest first; none until the bundle workflow has committed the first one. */
export function repositoryBundles(): BundleSummary[] {
  if (!existsSync(bundlesDir)) return [];
  return readdirSync(bundlesDir)
    .filter((file) => /^vanta-.+\.json$/.test(file))
    .map((file) => JSON.parse(readFileSync(`${bundlesDir}${file}`, 'utf8')) as RawBundle)
    .map((raw) => ({
      version: raw.version,
      clientVersion: raw.clientVersion,
      launcherVersion: raw.launcherVersion,
      channel: raw.channel,
      published: raw.file.downloadUrl !== '',
      fileName: raw.file.name,
      downloadUrl: raw.file.downloadUrl,
      contentPaths: raw.contents.map((entry) => entry.path),
    }))
    .sort((a, b) => compareDesc(a.version, b.version));
}

/** Same rule as `latestBundle` in src/lib/bundles.ts: the newest published stable bundle, else none. */
export function offeredBundle(): BundleSummary | undefined {
  return repositoryBundles().find((bundle) => bundle.channel === 'stable' && bundle.published);
}

/** Same rule as `olderBundles`: the published stable bundles older than the offered one, newest first. */
export function olderBundles(): BundleSummary[] {
  const offered = offeredBundle()?.version;
  return repositoryBundles().filter(
    (bundle) => bundle.channel === 'stable' && bundle.published && bundle.version !== offered,
  );
}

export interface LocalAiSummary {
  /** Every size and digest filled in (the resolve workflow has run). */
  readonly resolved: boolean;
  readonly runtimeTag: string;
  readonly runtimeComponent: string;
  readonly modelFile: string;
  /** Host names of the runtime archives and the model, without duplicates. */
  readonly hosts: readonly string[];
  readonly diskMb: number;
  readonly ramMb: number;
  /** Archive file names per platform, in manifest order. */
  readonly archiveFiles: readonly string[];
}

interface RawLocalAi {
  readonly resolvedAt: string;
  readonly runtime: {
    readonly tag: string;
    readonly component: string;
    readonly platforms: Readonly<
      Record<string, { readonly file: string; readonly url: string; readonly size: number }>
    >;
  };
  readonly model: { readonly file: string; readonly url: string; readonly size: number };
  readonly requirements: { readonly diskMb: number; readonly ramMb: number };
}

/** The Local AI manifest the Download page's note is built from; `undefined` without the file. */
export function localAiManifest(): LocalAiSummary | undefined {
  if (!existsSync(localAiFile)) return undefined;
  const raw = JSON.parse(readFileSync(localAiFile, 'utf8')) as RawLocalAi;
  const platforms = Object.values(raw.runtime.platforms);
  const hosts: string[] = [];
  for (const url of [...platforms.map((p) => p.url), raw.model.url]) {
    const host = new URL(url).hostname;
    if (!hosts.includes(host)) hosts.push(host);
  }
  return {
    resolved: raw.resolvedAt !== '' && raw.model.size > 0 && platforms.every((p) => p.size > 0),
    runtimeTag: raw.runtime.tag,
    runtimeComponent: raw.runtime.component,
    modelFile: raw.model.file,
    hosts,
    diskMb: raw.requirements.diskMb,
    ramMb: raw.requirements.ramMb,
    archiveFiles: platforms.map((p) => p.file),
  };
}

/** Ids (`<product>-<version>`) of every release notes file, as the changelog page renders them. */
export function changelogIds(): string[] {
  return readdirSync(changelogDir)
    .filter((file) => file.endsWith('.md') && !/^readme\.md$/i.test(file))
    .map((file) => file.replace(/\.md$/, ''));
}
