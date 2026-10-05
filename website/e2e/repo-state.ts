import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * What the build under test was made from: the release manifests in `shared/releases/` and the
 * release notes in `content/changelog/`, read from disk. The tests derive their expectations from
 * these files, so they hold before and after the release workflow fills a manifest and while a newer
 * version is committed but not published yet.
 */

const releasesDir = fileURLToPath(new URL('../../shared/releases/', import.meta.url));
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

/** Ids (`<product>-<version>`) of every release notes file, as the changelog page renders them. */
export function changelogIds(): string[] {
  return readdirSync(changelogDir)
    .filter((file) => file.endsWith('.md') && !/^readme\.md$/i.test(file))
    .map((file) => file.replace(/\.md$/, ''));
}
