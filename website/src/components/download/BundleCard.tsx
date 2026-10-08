import { Download, ExternalLink, FileArchive, Info } from 'lucide-react';
import {
  type BundleEntry,
  type BundleManifest,
  bundleMatches,
  bundleReleasePageUrl,
} from '../../lib/bundles';
import { formatBytes, formatDate, groupHash } from '../../lib/format';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { CopyButton } from '../ui/CopyButton';
import { Tag } from '../ui/Pill';
import { Fact } from './Fact';

export interface BundleCardProps {
  /** A published bundle (`latestBundle`); the card is never rendered for an unpublished one. */
  readonly bundle: BundleManifest;
  /**
   * Versions the launcher and client cards next to this one offer. When the zip holds other
   * versions, the card says so instead of letting the reader assume they are the same.
   */
  readonly clientVersion: string | undefined;
  readonly launcherVersion: string | undefined;
}

function EntryRow({ entry }: { entry: BundleEntry }) {
  const size = formatBytes(entry.size);
  const sha = entry.sha256 !== '' ? entry.sha256 : undefined;
  return (
    <li className="flex flex-col gap-2 p-3 sm:flex-row sm:items-start sm:justify-between sm:gap-5">
      <div className="min-w-0 sm:w-2/5">
        <p className="font-mono text-[12px] leading-5 break-all text-text-primary">{entry.path}</p>
        <p className="mt-0.5 text-xs text-text-secondary tabular-nums">
          {size ?? <span className="text-text-muted">Size unknown</span>}
        </p>
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-[10px] font-semibold tracking-label text-text-muted uppercase">
          SHA-256
        </p>
        {sha ? (
          <div className="mt-1 flex items-start gap-2">
            <code className="min-w-0 flex-1 rounded-sm border border-border-subtle bg-surface-2 px-2 py-1 font-mono text-[11px] leading-[1.15rem] break-words text-text-secondary">
              {groupHash(sha)}
            </code>
            <CopyButton
              value={sha}
              label={`Copy SHA-256 checksum of ${entry.path}`}
              className="shrink-0 flex-col items-end gap-1 sm:flex-row sm:items-center"
            />
          </div>
        ) : (
          <p className="mt-1 text-xs text-text-muted">Not in the manifest</p>
        )}
      </div>
    </li>
  );
}

/**
 * Download card for the full release zip: one archive with the published files of a client release
 * and a launcher release plus the documentation, described by its bundle manifest. The card shows
 * the zip's facts and checksum, the download button, the GitHub release page and, folded away, every
 * file inside the zip with its size and SHA-256. It is rendered only for a published bundle, so it
 * never carries a dead link.
 */
export function BundleCard({ bundle, clientVersion, launcherVersion }: BundleCardProps) {
  const idPrefix = 'download-bundle';
  const { file } = bundle;
  const size = formatBytes(file.size);
  const sha = file.sha256 !== '' ? file.sha256 : undefined;
  const date = formatDate(bundle.releaseDate) ?? bundle.releaseDate;
  const pageUrl = bundleReleasePageUrl(bundle);
  const sameVersions = bundleMatches(bundle, clientVersion, launcherVersion);
  const contentsId = `${idPrefix}-contents`;

  return (
    <article
      className="surface-card flex min-w-0 flex-col p-6 sm:p-8 lg:col-span-2"
      aria-labelledby={idPrefix}
    >
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
        <div className="flex min-w-0 items-center gap-3">
          <span
            aria-hidden="true"
            className="inline-flex size-11 shrink-0 items-center justify-center rounded-lg border border-border-subtle bg-surface-2 text-text-secondary [&>svg]:size-5"
          >
            <FileArchive />
          </span>
          <div className="min-w-0">
            <p className="eyebrow">Full release</p>
            <h2
              id={idPrefix}
              className="font-display text-xl font-semibold text-text-primary sm:text-2xl"
            >
              Full release (zip)
            </h2>
          </div>
        </div>
        <Badge tone="success" dot>
          Published
        </Badge>
      </div>

      <p className="mt-4 text-sm leading-relaxed text-text-secondary">
        Everything of both releases in one archive: the Windows installer, the launcher apps and
        jars, the client jar, the mods bundle, Fabric API, the docs, the changelog and the
        checksums. Unpack it and read README.txt, which says which file to take on which system and
        how to verify it.
        {sameVersions
          ? ' The files are the ones the two cards above offer, downloaded once.'
          : null}
      </p>

      <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-5 border-t border-border-subtle pt-6 sm:grid-cols-3 lg:grid-cols-6">
        <Fact label="Version">
          <span className="font-mono">{bundle.version}</span>{' '}
          <Tag className="ml-1 align-middle">{bundle.channel}</Tag>
        </Fact>
        <Fact label="Release date">{date}</Fact>
        <Fact label="Client version">
          <span className="font-mono">{bundle.clientVersion}</span>
        </Fact>
        <Fact label="Launcher version">
          <span className="font-mono">{bundle.launcherVersion}</span>
        </Fact>
        <Fact label="Minecraft">
          <span className="font-mono">{bundle.minecraftVersion}</span>
        </Fact>
        <Fact label="Size">{size ?? <span className="text-text-muted">Unknown</span>}</Fact>
      </dl>

      {/* The button right after the facts, so it is on the first phone screen like on the other cards. */}
      <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        <Button
          href={file.downloadUrl}
          external={false}
          rel="noopener"
          download
          size="lg"
          variant="secondary"
          leadingIcon={<Download />}
          className="w-full sm:w-auto"
        >
          Download full release (.zip)
        </Button>
        {pageUrl ? (
          <Button href={pageUrl} variant="link" trailingIcon={<ExternalLink />} className="text-sm">
            Release page on GitHub
          </Button>
        ) : null}
      </div>

      <dl className="mt-6 grid gap-y-4 border-t border-border-subtle pt-6">
        <Fact label="File">
          <span className="font-mono text-[13px] break-all">{file.name}</span>
        </Fact>
        <div className="flex flex-col gap-1">
          <dt className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
            SHA-256
          </dt>
          <dd className="flex flex-wrap items-center gap-3 text-sm">
            {sha ? (
              <>
                <code className="rounded-sm border border-border-subtle bg-surface-2 px-2 py-1 font-mono text-[12px] leading-5 break-words text-text-primary">
                  {groupHash(sha)}
                </code>
                <CopyButton value={sha} label={`Copy SHA-256 checksum of ${file.name}`} />
              </>
            ) : (
              <span className="text-text-muted">Not in the manifest</span>
            )}
          </dd>
        </div>
      </dl>
      <p className="mt-3 text-xs leading-relaxed text-text-muted">
        Compare this SHA-256 with the downloaded zip before you unpack it, like any other file here.
        Inside, SHA256SUMS.txt lists the checksum of every other file for sha256sum -c.
      </p>

      {sameVersions ? null : (
        <div
          role="note"
          aria-label="Versions in this zip"
          className="mt-6 flex gap-3 rounded-lg border border-border-subtle bg-surface-2/60 p-4 text-sm leading-relaxed text-text-secondary"
        >
          <Info className="mt-0.5 size-4 shrink-0 text-accent-blue" aria-hidden="true" />
          <p>
            This zip holds client {bundle.clientVersion} and launcher {bundle.launcherVersion}
            {clientVersion && launcherVersion ? (
              <>
                ; the cards above offer client {clientVersion} and launcher {launcherVersion}. Take
                the single files when you want those versions.
              </>
            ) : (
              '.'
            )}
          </p>
        </div>
      )}

      {bundle.contents.length > 0 ? (
        <details className="group mt-6 border-t border-border-subtle pt-6">
          <summary
            id={contentsId}
            className="flex cursor-pointer list-none items-center justify-between gap-3 rounded-sm text-[11px] font-semibold tracking-label text-text-muted uppercase select-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
          >
            <span>Files in the zip ({bundle.contents.length})</span>
            <span aria-hidden="true" className="text-text-secondary group-open:hidden">
              Show
            </span>
            <span aria-hidden="true" className="hidden text-text-secondary group-open:inline">
              Hide
            </span>
          </summary>
          <p className="mt-2 text-xs leading-relaxed text-text-muted">
            Paths inside the folder {file.name.replace(/\.zip$/i, '')}, in zip order, with the
            SHA-256 of each file from the bundle manifest. SHA256SUMS.txt itself is not listed.
          </p>
          <ul
            aria-labelledby={contentsId}
            className="mt-3 divide-y divide-border-subtle overflow-hidden rounded-lg border border-border-subtle bg-bg-void/40"
          >
            {bundle.contents.map((entry) => (
              <EntryRow key={entry.path} entry={entry} />
            ))}
          </ul>
        </details>
      ) : null}
    </article>
  );
}
