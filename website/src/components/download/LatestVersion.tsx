import { productLabel } from '../../lib/content';
import { formatDate } from '../../lib/format';
import { type ReleaseManifest, isPublished } from '../../lib/releases';

export interface LatestVersionProps {
  /** The client release the download page offers (`latestRelease`). */
  readonly client: ReleaseManifest | undefined;
  /** The launcher release the download page offers (`latestRelease`). */
  readonly launcher: ReleaseManifest | undefined;
}

/** "VANTA Client 1.3.0", with "(not published yet)" while the offered manifest has no files out. */
function versionOf(manifest: ReleaseManifest): string {
  const name = `${productLabel(manifest.product)} ${manifest.version}`;
  return isPublished(manifest) ? name : `${name} (not published yet)`;
}

/**
 * The "Latest version" block of the download hero: the client and launcher versions the cards below
 * offer, with their release date (one line when both share it). Rendered only when at least one
 * product has a manifest; the versions come from the manifests, never from prose.
 */
export function LatestVersion({ client, launcher }: LatestVersionProps) {
  const manifests = [client, launcher].filter(
    (manifest): manifest is ReleaseManifest => manifest !== undefined,
  );
  const [first] = manifests;
  if (!first) return null;
  const dates = new Set(manifests.map((manifest) => manifest.releaseDate));
  const dateLine =
    dates.size === 1
      ? `Released ${formatDate(first.releaseDate) ?? first.releaseDate}`
      : manifests
          .map(
            (manifest) =>
              `${productLabel(manifest.product)} released ${formatDate(manifest.releaseDate) ?? manifest.releaseDate}`,
          )
          .join(', ');

  return (
    <div
      className="surface-card mb-8 inline-flex max-w-full flex-col gap-1.5 px-5 py-4"
      role="group"
      aria-label="Latest version"
    >
      <p className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
        Latest version
      </p>
      <p className="font-display text-lg leading-snug font-semibold text-text-primary sm:text-xl">
        {manifests.map(versionOf).join(' and ')}
      </p>
      <p className="text-sm text-text-secondary">{dateLine}</p>
    </div>
  );
}
