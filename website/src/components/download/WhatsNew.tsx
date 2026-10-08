import { ArrowRight, Newspaper } from 'lucide-react';
import { Suspense } from 'react';
import { Link } from 'react-router';
import {
  type ChangelogEntry,
  findChangelog,
  productLabel,
  releaseHighlights,
} from '../../lib/content';
import { formatDate } from '../../lib/format';
import { type ReleaseManifest } from '../../lib/releases';
import { useNews } from '../../lib/use-content';
import { Section } from '../ui/Section';

export interface WhatsNewProps {
  /** The client release the download page offers. */
  readonly client: ReleaseManifest | undefined;
  /** The launcher release the download page offers. */
  readonly launcher: ReleaseManifest | undefined;
}

/** Bullets quoted per product; the full notes are one link away. */
const HIGHLIGHTS = 5;

interface Column {
  readonly manifest: ReleaseManifest;
  readonly notes: ChangelogEntry;
  readonly highlights: readonly string[];
}

/** The products that have release notes with at least one bullet to quote, client first. */
function columnsOf(manifests: readonly (ReleaseManifest | undefined)[]): Column[] {
  const columns: Column[] = [];
  for (const manifest of manifests) {
    if (!manifest) continue;
    const notes = findChangelog(manifest.product, manifest.version);
    if (!notes) continue;
    const highlights = releaseHighlights(notes, HIGHLIGHTS);
    if (highlights.length === 0) continue;
    columns.push({ manifest, notes, highlights });
  }
  return columns;
}

/** "1.3.0" when both products share the version, otherwise "client 1.3.0 and launcher 1.3.1". */
function versionsLabel(columns: readonly Column[]): string {
  const versions = new Set(columns.map((column) => column.manifest.version));
  if (versions.size === 1) return columns[0]?.manifest.version ?? '';
  return columns
    .map((column) => `${column.manifest.product} ${column.manifest.version}`)
    .join(' and ');
}

/** Link to the newest news post; suspends on the lazily loaded news chunk, nothing while there is no post. */
function LatestNewsLink() {
  const post = useNews()[0];
  if (!post) return null;
  const date = formatDate(post.date) ?? post.date;
  return (
    <p className="mt-8 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-text-secondary">
      <Newspaper className="size-4 shrink-0 text-text-muted" aria-hidden="true" />
      <span>
        Latest news post:{' '}
        <Link
          to={`/news/${post.slug}`}
          className="font-medium text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 transition-colors hover:text-text-primary"
        >
          {post.title}
        </Link>{' '}
        <span className="text-text-muted">({date})</span>
      </span>
    </p>
  );
}

/**
 * "What's new" on the download page: for the client and the launcher release on offer, up to five
 * bullets of their release notes (the fixes first, like the cards' excerpt), the link to the full
 * notes on the changelog and the newest news post. A product without notes gets no column, and
 * without any notes the section is left out entirely.
 */
export function WhatsNew({ client, launcher }: WhatsNewProps) {
  const columns = columnsOf([client, launcher]);
  if (columns.length === 0) return null;

  return (
    <Section
      id="whats-new"
      eyebrow="What's new"
      title={`What's new in ${versionsLabel(columns)}`}
      lead="From the release notes of the versions offered above. Each column quotes the fixes first, then what was added and improved; the full notes have every change."
      className="border-t border-border-subtle"
      spacing="md"
    >
      <div className="grid gap-4 lg:grid-cols-2">
        {columns.map(({ manifest, notes, highlights }) => {
          const headingId = `whats-new-${manifest.product}`;
          const label = productLabel(manifest.product);
          return (
            <article
              key={manifest.product}
              aria-labelledby={headingId}
              className="surface-card flex min-w-0 flex-col p-6 sm:p-7"
            >
              <p className="eyebrow">{label}</p>
              <h3
                id={headingId}
                className="mt-1 font-display text-xl font-semibold text-text-primary"
              >
                {notes.title}
              </h3>
              <p className="mt-1 text-xs text-text-muted">
                Released {formatDate(manifest.releaseDate) ?? manifest.releaseDate}
              </p>
              <ul className="mt-5 flex flex-col gap-2.5 text-sm leading-relaxed text-text-secondary">
                {highlights.map((item) => (
                  <li key={item} className="flex gap-2.5">
                    <span
                      aria-hidden="true"
                      className="mt-2 size-1.5 shrink-0 rounded-full bg-accent-violet-hover"
                    />
                    <span className="line-clamp-4 min-w-0 break-words">{item}</span>
                  </li>
                ))}
              </ul>
              <Link
                to={`/changelog#${notes.id}`}
                className="mt-5 inline-flex items-center gap-1 text-sm font-medium text-accent-violet-hover transition-colors hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
              >
                {/* The space before the hidden suffix keeps the accessible name readable. */}
                Full release notes{' '}
                <span className="sr-only">
                  for {label} {manifest.version}
                </span>
                <ArrowRight className="size-3.5" aria-hidden="true" />
              </Link>
            </article>
          );
        })}
      </div>
      <Suspense fallback={null}>
        <LatestNewsLink />
      </Suspense>
    </Section>
  );
}
