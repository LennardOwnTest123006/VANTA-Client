import { ArrowRight, Download, ExternalLink } from 'lucide-react';
import { Link } from 'react-router';
import { type BundleManifest, bundleReleasePageUrl } from '../../lib/bundles';
import { findChangelog, productLabel } from '../../lib/content';
import { primaryFile } from '../../lib/downloads';
import { formatBytes, formatDate, groupHash } from '../../lib/format';
import { type ReleaseManifest, releasePageUrl } from '../../lib/releases';
import { Button } from '../ui/Button';
import { CopyButton } from '../ui/CopyButton';
import { Section } from '../ui/Section';

export interface OlderVersionsProps {
  /** Published launcher releases older than the one the launcher card offers, newest first (`olderReleases`). */
  readonly launcher: readonly ReleaseManifest[];
  /** Published client releases older than the one the client card offers, newest first (`olderReleases`). */
  readonly client: readonly ReleaseManifest[];
  /** Published full release zips older than the one the zip card offers, newest first (`olderBundles`). */
  readonly bundles: readonly BundleManifest[];
}

/** The file a row offers: the same facts for a release file and for a zip. */
interface RowFile {
  readonly name: string;
  readonly downloadUrl: string;
  readonly size: number;
  readonly sha256: string;
}

/** One listed version, whatever it is a version of: the facts the row shows and the links it offers. */
interface Row {
  readonly key: string;
  /** "VANTA Launcher 1.2.1": names the version in the accessible names of the row's links. */
  readonly name: string;
  readonly version: string;
  /** ISO date `YYYY-MM-DD`. */
  readonly releaseDate: string;
  /** The file to download; `undefined` when the manifest has no primary file with a URL. */
  readonly file: RowFile | undefined;
  readonly pageUrl: string | undefined;
  /** `/changelog#<id>` when release notes for the version exist. */
  readonly notesHref: string | undefined;
  /** One line under the file name, e.g. the versions inside a zip. */
  readonly detail: string | undefined;
}

interface Group {
  readonly id: string;
  readonly title: string;
  readonly rows: readonly Row[];
}

function releaseRow(manifest: ReleaseManifest): Row {
  const file = primaryFile(manifest);
  const notes = findChangelog(manifest.product, manifest.version);
  return {
    key: `${manifest.product}-${manifest.version}`,
    name: `${productLabel(manifest.product)} ${manifest.version}`,
    version: manifest.version,
    releaseDate: manifest.releaseDate,
    file: file && file.downloadUrl !== '' ? file : undefined,
    pageUrl: releasePageUrl(manifest),
    notesHref: notes ? `/changelog#${notes.id}` : undefined,
    detail: undefined,
  };
}

function bundleRow(bundle: BundleManifest): Row {
  return {
    key: `bundle-${bundle.version}`,
    name: `Full release zip ${bundle.version}`,
    version: bundle.version,
    releaseDate: bundle.releaseDate,
    file: bundle.file.downloadUrl !== '' ? bundle.file : undefined,
    pageUrl: bundleReleasePageUrl(bundle),
    notesHref: undefined,
    detail: `Holds client ${bundle.clientVersion} and launcher ${bundle.launcherVersion}.`,
  };
}

/** The groups with at least one version, launcher first like the cards above. */
function groupsOf({ launcher, client, bundles }: OlderVersionsProps): Group[] {
  const groups: Group[] = [
    {
      id: 'older-versions-launcher',
      title: productLabel('launcher'),
      rows: launcher.map(releaseRow),
    },
    { id: 'older-versions-client', title: productLabel('client'), rows: client.map(releaseRow) },
    { id: 'older-versions-bundle', title: 'Full release zip', rows: bundles.map(bundleRow) },
  ];
  return groups.filter((group) => group.rows.length > 0);
}

const inlineLink =
  'inline-flex items-center gap-1 font-medium text-accent-violet-hover transition-colors hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus';

function VersionRow({ row }: { row: Row }) {
  const date = formatDate(row.releaseDate) ?? row.releaseDate;
  const size = row.file ? formatBytes(row.file.size) : undefined;
  const sha = row.file && row.file.sha256 !== '' ? row.file.sha256 : undefined;
  return (
    <li className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between sm:gap-5">
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="font-mono text-sm font-semibold text-text-primary">{row.version}</span>
          <time dateTime={row.releaseDate} className="text-xs text-text-secondary">
            {date}
          </time>
        </p>
        {row.file ? (
          <p className="mt-1 flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
            <span className="font-mono text-[12px] leading-5 break-all text-text-secondary">
              {row.file.name}
            </span>
            {size ? <span className="text-xs text-text-muted tabular-nums">{size}</span> : null}
          </p>
        ) : (
          <p className="mt-1 text-xs leading-relaxed text-text-muted">
            No direct download in the release manifest; the files are on the release page.
          </p>
        )}
        {row.detail ? <p className="mt-1 text-xs text-text-muted">{row.detail}</p> : null}
        {sha ? (
          <div className="mt-2.5">
            <p className="text-[10px] font-semibold tracking-label text-text-muted uppercase">
              SHA-256
            </p>
            <div className="mt-1 flex items-start gap-2">
              <code className="min-w-0 flex-1 rounded-sm border border-border-subtle bg-surface-2 px-2 py-1 font-mono text-[11px] leading-[1.15rem] break-words text-text-secondary">
                {groupHash(sha)}
              </code>
              <CopyButton
                value={sha}
                label={`Copy SHA-256 checksum of ${row.file?.name ?? row.name}`}
                className="shrink-0 flex-col items-end gap-1 sm:flex-row sm:items-center"
              />
            </div>
          </div>
        ) : null}
        {row.pageUrl || row.notesHref ? (
          <p className="mt-2.5 flex flex-wrap gap-x-4 gap-y-1 text-sm">
            {row.pageUrl ? (
              <a
                href={row.pageUrl}
                target="_blank"
                rel="noopener noreferrer"
                className={inlineLink}
              >
                {/* The space before the hidden suffix keeps the accessible name readable. */}
                Release page on GitHub <span className="sr-only">for {row.name}</span>
                <ExternalLink className="size-3.5" aria-hidden="true" />
              </a>
            ) : null}
            {row.notesHref ? (
              <Link to={row.notesHref} className={inlineLink}>
                Release notes <span className="sr-only">for {row.name}</span>
                <ArrowRight className="size-3.5" aria-hidden="true" />
              </Link>
            ) : null}
          </p>
        ) : null}
      </div>
      {row.file ? (
        <div className="shrink-0 sm:pt-0.5">
          <Button
            href={row.file.downloadUrl}
            external={false}
            rel="noopener"
            download
            size="sm"
            variant="secondary"
            leadingIcon={<Download />}
            className="w-full sm:w-auto"
          >
            Download <span className="sr-only">{row.name}</span>
          </Button>
        </div>
      ) : null}
    </li>
  );
}

/**
 * "Older versions" on the download page: every published release of the launcher and the client
 * that is older than the one its card offers, and every published full release zip older than the
 * one the zip card offers, newest first. Each row has the version, its release date, the primary
 * file with size and SHA-256 and a direct download link, the GitHub release page and, when release
 * notes exist, the link to them on the changelog. A product without older published releases gets
 * no list, and without any older version the section is left out entirely, so the in-page link to
 * it is only rendered when it exists.
 */
export function OlderVersions(props: OlderVersionsProps) {
  const groups = groupsOf(props);
  if (groups.length === 0) return null;

  return (
    <Section
      id="older-versions"
      eyebrow="Older versions"
      title="Every earlier release stays available."
      lead="The cards above always offer the newest release, and the newest is the one to install. Earlier versions are listed here for anyone who needs one, for example to go back after a problem or to reproduce a report. Each file is the one on its release page, with the same SHA-256 checksum."
      className="scroll-mt-24 border-t border-border-subtle"
      spacing="md"
    >
      <div className="grid gap-4 lg:grid-cols-2">
        {groups.map((group) => (
          <div
            key={group.id}
            className={
              group.id === 'older-versions-bundle'
                ? 'surface-card min-w-0 p-6 sm:p-7 lg:col-span-2'
                : 'surface-card min-w-0 p-6 sm:p-7'
            }
          >
            <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
              <h3 id={group.id} className="font-display text-lg font-semibold text-text-primary">
                {group.title}
              </h3>
              <p className="text-xs text-text-muted">
                {group.rows.length === 1
                  ? '1 earlier version'
                  : `${group.rows.length} earlier versions`}
              </p>
            </div>
            <ul
              aria-labelledby={group.id}
              className="mt-4 divide-y divide-border-subtle overflow-hidden rounded-lg border border-border-subtle bg-bg-void/40"
            >
              {group.rows.map((row) => (
                <VersionRow key={row.key} row={row} />
              ))}
            </ul>
          </div>
        ))}
      </div>
    </Section>
  );
}
