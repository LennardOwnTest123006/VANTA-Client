import { ArrowRight, Download, FolderArchive, type LucideIcon } from 'lucide-react';
import { type ReactNode } from 'react';
import { Link } from 'react-router';
import { findChangelog, markdownExcerpt } from '../../lib/content';
import { type DownloadResolution } from '../../lib/downloads';
import { formatBytes, formatDate, groupHash } from '../../lib/format';
import { type ReleaseFile, type ReleaseManifest, isPublished } from '../../lib/releases';
import { cn } from '../../lib/cn';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { CopyButton } from '../ui/CopyButton';
import { Tag } from '../ui/Pill';
import { ReleaseFileList } from './ReleaseFileList';

export interface DownloadCardProps {
  readonly manifest: ReleaseManifest | undefined;
  readonly resolution: DownloadResolution;
  readonly icon: LucideIcon;
  readonly eyebrow: string;
  readonly title: string;
  readonly description: ReactNode;
  readonly cta: string;
  readonly primary?: boolean;
  readonly footnote?: ReactNode;
  /**
   * A second file offered under the main button once it has a download URL, e.g. the client's mods
   * bundle. Nothing is rendered while the file is unpublished.
   */
  readonly secondaryDownload?: { readonly file: ReleaseFile | undefined; readonly label: string };
  /** Extra content between the actions and the file list, e.g. the ways to install. */
  readonly children?: ReactNode;
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
        {label}
      </dt>
      <dd className="text-sm text-text-primary">{children}</dd>
    </div>
  );
}

/**
 * Download card for one product. Shows the release facts from the manifest and either a real
 * download button or an explicitly disabled "Not published yet" state — never a dead link. Once the
 * manifest is published, every file of the release is listed with size, SHA-256 and its own link.
 */
export function DownloadCard({
  manifest,
  resolution,
  icon: Icon,
  eyebrow,
  title,
  description,
  cta,
  primary = false,
  footnote,
  secondaryDownload,
  children,
}: DownloadCardProps) {
  const idPrefix = `download-${eyebrow.toLowerCase()}`;
  const file = resolution.state === 'missing' ? undefined : resolution.file;
  const size = file ? formatBytes(file.size) : undefined;
  const sha = file && file.sha256 !== '' ? file.sha256 : undefined;
  const notes = manifest ? findChangelog(manifest.product, manifest.version) : undefined;
  const excerpt = notes ? markdownExcerpt(notes.body, 3) : [];
  const date = manifest ? formatDate(manifest.releaseDate) : undefined;
  const available = resolution.state === 'available';
  // The file list carries every checksum once the release is out; the facts then skip the SHA-256.
  const showFileList = manifest !== undefined && isPublished(manifest);
  const secondaryFile =
    secondaryDownload?.file && secondaryDownload.file.downloadUrl !== ''
      ? secondaryDownload.file
      : undefined;

  return (
    <article
      className={cn(
        'surface-card flex flex-col p-6 sm:p-8',
        primary &&
          'ring-1 ring-accent-violet/30 shadow-[0_0_0_1px_rgba(124,92,255,0.15),0_24px_64px_-24px_rgba(124,92,255,0.45)]',
      )}
      aria-labelledby={idPrefix}
    >
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
        <div className="flex min-w-0 items-center gap-3">
          <span
            aria-hidden="true"
            className={cn(
              'inline-flex size-11 shrink-0 items-center justify-center rounded-lg border [&>svg]:size-5',
              primary
                ? 'border-accent-violet/40 bg-accent-violet/15 text-accent-violet-hover'
                : 'border-border-subtle bg-surface-2 text-text-secondary',
            )}
          >
            <Icon />
          </span>
          <div className="min-w-0">
            <p className="eyebrow">{eyebrow}</p>
            <h2
              id={idPrefix}
              className="font-display text-xl font-semibold text-text-primary sm:text-2xl"
            >
              {title}
            </h2>
          </div>
        </div>
        {manifest ? (
          <Badge tone={available ? 'success' : 'warning'} dot>
            {available ? 'Published' : 'Release pending'}
          </Badge>
        ) : null}
      </div>

      <p className="mt-4 text-sm leading-relaxed text-text-secondary">{description}</p>

      {manifest ? (
        <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-5 border-t border-border-subtle pt-6 sm:grid-cols-3">
          <Fact label="Version">
            <span className="font-mono">{manifest.version}</span>{' '}
            <Tag className="ml-1 align-middle">{manifest.channel}</Tag>
          </Fact>
          <Fact label="Release date">{date ?? manifest.releaseDate}</Fact>
          <Fact label="File">
            <span className="font-mono text-[13px] break-all">{file?.name ?? '—'}</span>
          </Fact>
          <Fact label="Size">
            {size ?? <span className="text-text-muted">Published with the release</span>}
          </Fact>
          <Fact label="Minecraft">
            <span className="font-mono">{manifest.minecraftVersion}</span>
          </Fact>
          <Fact label="Fabric Loader">
            <span className="font-mono">{manifest.fabricVersion}</span>
          </Fact>
          {showFileList ? null : (
            <div className="col-span-2 flex flex-col gap-1 sm:col-span-3">
              <dt className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
                SHA-256
              </dt>
              <dd className="flex flex-wrap items-center gap-3 text-sm">
                {sha ? (
                  <>
                    <code className="rounded-sm border border-border-subtle bg-surface-2 px-2 py-1 font-mono text-[12px] leading-5 break-words text-text-primary">
                      {groupHash(sha)}
                    </code>
                    <CopyButton
                      value={sha}
                      label={`Copy SHA-256 checksum of ${file?.name ?? title}`}
                    />
                  </>
                ) : (
                  <span className="text-text-muted">Published with the release</span>
                )}
              </dd>
            </div>
          )}
        </dl>
      ) : (
        <p className="mt-6 border-t border-border-subtle pt-6 text-sm text-text-muted">
          No release manifest found for this product.
        </p>
      )}

      {excerpt.length > 0 ? (
        <div className="mt-6">
          <p className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
            In this release
          </p>
          <ul className="mt-2 flex flex-col gap-1.5 text-sm text-text-secondary">
            {excerpt.map((item) => (
              <li key={item} className="flex gap-2.5">
                <span
                  aria-hidden="true"
                  className="mt-2 size-1.5 shrink-0 rounded-full bg-accent-violet-hover"
                />
                <span>{item}</span>
              </li>
            ))}
          </ul>
          {notes ? (
            <Link
              to={`/changelog#${notes.id}`}
              className="mt-3 inline-flex items-center gap-1 text-sm font-medium text-accent-violet-hover transition-colors hover:text-text-primary"
            >
              Full release notes <ArrowRight className="size-3.5" aria-hidden="true" />
            </Link>
          ) : null}
        </div>
      ) : null}

      <div className="mt-8 flex flex-col gap-3">
        {available ? (
          <Button
            href={resolution.url}
            external={false}
            rel="noopener"
            download
            size="lg"
            variant={primary ? 'primary' : 'secondary'}
            leadingIcon={<Download />}
            fullWidth
          >
            {cta}
          </Button>
        ) : (
          <>
            <Button
              size="lg"
              variant={primary ? 'primary' : 'secondary'}
              leadingIcon={<Download />}
              fullWidth
              disabled
              aria-describedby={`${idPrefix}-pending`}
            >
              {cta}
            </Button>
            <p
              id={`${idPrefix}-pending`}
              role="status"
              className="text-center text-sm text-warning"
            >
              Not published yet — release pending
            </p>
          </>
        )}
        {secondaryDownload && secondaryFile ? (
          <Button
            href={secondaryFile.downloadUrl}
            external={false}
            rel="noopener"
            download
            variant="secondary"
            leadingIcon={<FolderArchive />}
            fullWidth
          >
            {secondaryDownload.label}
          </Button>
        ) : null}
        {footnote ? <p className="text-xs leading-relaxed text-text-muted">{footnote}</p> : null}
      </div>

      {children}

      {showFileList ? (
        <ReleaseFileList manifest={manifest} idPrefix={idPrefix} primaryName={file?.name} />
      ) : null}
    </article>
  );
}
