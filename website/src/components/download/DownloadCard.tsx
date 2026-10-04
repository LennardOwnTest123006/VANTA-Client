import { ArrowRight, Download, ExternalLink, type LucideIcon } from 'lucide-react';
import { type ReactNode } from 'react';
import { Link } from 'react-router';
import { findChangelog, markdownExcerpt } from '../../lib/content';
import { type DownloadResolution } from '../../lib/downloads';
import { formatBytes, formatDate, groupHash } from '../../lib/format';
import { type ReleaseManifest } from '../../lib/releases';
import { cn } from '../../lib/cn';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { CopyButton } from '../ui/CopyButton';
import { Tag } from '../ui/Pill';

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
 * download button or an explicitly disabled "Not published yet" state — never a dead link.
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
}: DownloadCardProps) {
  const file = resolution.state === 'missing' ? undefined : resolution.file;
  const size = file ? formatBytes(file.size) : undefined;
  const sha = file && file.sha256 !== '' ? file.sha256 : undefined;
  const notes = manifest ? findChangelog(manifest.product, manifest.version) : undefined;
  const excerpt = notes ? markdownExcerpt(notes.body, 4) : [];
  const date = manifest ? formatDate(manifest.releaseDate) : undefined;
  const available = resolution.state === 'available';

  return (
    <article
      className={cn(
        'surface-card flex flex-col p-6 sm:p-8',
        primary &&
          'ring-1 ring-accent-violet/30 shadow-[0_0_0_1px_rgba(124,92,255,0.15),0_24px_64px_-24px_rgba(124,92,255,0.45)]',
      )}
      aria-labelledby={`download-${eyebrow.toLowerCase()}`}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <span
            aria-hidden="true"
            className={cn(
              'inline-flex size-11 items-center justify-center rounded-lg border [&>svg]:size-5',
              primary
                ? 'border-accent-violet/40 bg-accent-violet/15 text-accent-violet-hover'
                : 'border-border-subtle bg-surface-2 text-text-secondary',
            )}
          >
            <Icon />
          </span>
          <div>
            <p className="eyebrow">{eyebrow}</p>
            <h2
              id={`download-${eyebrow.toLowerCase()}`}
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
          <div className="col-span-2 flex flex-col gap-1 sm:col-span-3">
            <dt className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
              SHA-256
            </dt>
            <dd className="flex flex-wrap items-center gap-3 text-sm">
              {sha ? (
                <>
                  <code className="rounded-sm border border-border-subtle bg-surface-2 px-2 py-1 font-mono text-[12px] leading-5 break-all text-text-primary">
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
            size="lg"
            variant={primary ? 'primary' : 'secondary'}
            leadingIcon={<Download />}
            trailingIcon={<ExternalLink />}
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
              aria-describedby={`download-${eyebrow.toLowerCase()}-pending`}
            >
              {cta}
            </Button>
            <p
              id={`download-${eyebrow.toLowerCase()}-pending`}
              role="status"
              className="text-center text-sm text-warning"
            >
              Not published yet — release pending
            </p>
          </>
        )}
        {footnote ? <p className="text-xs leading-relaxed text-text-muted">{footnote}</p> : null}
      </div>
    </article>
  );
}
