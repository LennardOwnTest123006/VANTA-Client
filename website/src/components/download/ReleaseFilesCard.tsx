import { type LucideIcon } from 'lucide-react';
import { type ReactNode } from 'react';
import { formatDate } from '../../lib/format';
import { type ReleaseFile, type ReleaseManifest, isPublished } from '../../lib/releases';
import { Badge } from '../ui/Badge';
import { Tag } from '../ui/Pill';
import { Fact } from './Fact';
import { ReleaseFileList } from './ReleaseFileList';

export interface ReleaseFilesCardProps {
  /** The release whose files are listed; `undefined` when the product has no manifest at all. */
  readonly manifest: ReleaseManifest | undefined;
  /** The files of `manifest` to list, in order. */
  readonly files: readonly ReleaseFile[];
  readonly icon: LucideIcon;
  readonly eyebrow: string;
  readonly title: string;
  readonly description: ReactNode;
  /** Heading of the file list, e.g. "Launcher jars and the Linux app in this release". */
  readonly filesHeading: string;
  /** Prefix for element ids, unique per page. */
  readonly idPrefix: string;
  readonly footnote?: ReactNode;
}

/**
 * A card for a group of release files without a single main download, e.g. the launcher jars for
 * Windows, Linux and macOS: the release facts, then every file with size, SHA-256 and its own link
 * once the release is published, or the honest pending state while it is not. The files are a
 * subset of the manifest the launcher card next to it shows, so nothing here is ever a dead link.
 */
export function ReleaseFilesCard({
  manifest,
  files,
  icon: Icon,
  eyebrow,
  title,
  description,
  filesHeading,
  idPrefix,
  footnote,
}: ReleaseFilesCardProps) {
  const published = manifest !== undefined && isPublished(manifest);
  const date = manifest ? formatDate(manifest.releaseDate) : undefined;

  return (
    <article className="surface-card flex min-w-0 flex-col p-6 sm:p-8" aria-labelledby={idPrefix}>
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
        <div className="flex min-w-0 items-center gap-3">
          <span
            aria-hidden="true"
            className="inline-flex size-11 shrink-0 items-center justify-center rounded-lg border border-border-subtle bg-surface-2 text-text-secondary [&>svg]:size-5"
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
          <Badge tone={published ? 'success' : 'warning'} dot>
            {published ? 'Published' : 'Release pending'}
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
          <Fact label="Files">{files.length === 1 ? '1 file' : `${files.length} files`}</Fact>
        </dl>
      ) : (
        <p className="mt-6 border-t border-border-subtle pt-6 text-sm text-text-muted">
          No release manifest found for this product.
        </p>
      )}

      {manifest && !published ? (
        <p role="status" className="mt-6 text-sm text-warning">
          Not published yet. The files appear here once the release workflow has published them.
        </p>
      ) : null}

      {footnote ? <p className="mt-4 text-xs leading-relaxed text-text-muted">{footnote}</p> : null}

      {manifest && published ? (
        <ReleaseFileList
          manifest={manifest}
          idPrefix={idPrefix}
          files={files}
          heading={filesHeading}
        />
      ) : null}
    </article>
  );
}
