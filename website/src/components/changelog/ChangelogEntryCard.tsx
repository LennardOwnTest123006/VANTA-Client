import { CalendarDays, Hash } from 'lucide-react';
import { Link } from 'react-router';
import {
  type ChangelogEntry,
  type ChangelogProduct,
  productLabel,
  type SectionKind,
} from '../../lib/content';
import { formatDate } from '../../lib/format';
import { MarkdownBlock } from '../../lib/markdown';
import { Badge, type BadgeTone } from '../ui/Badge';
import { Tag } from '../ui/Pill';

const productTones: Record<ChangelogProduct, BadgeTone> = {
  client: 'violet',
  launcher: 'blue',
  website: 'neutral',
};

const sectionStyles: Record<SectionKind, { label: string; className: string }> = {
  added: { label: 'Added', className: 'border-success/40 bg-success/10 text-success' },
  improved: {
    label: 'Improved',
    className: 'border-accent-blue/40 bg-accent-blue/10 text-accent-blue',
  },
  fixed: { label: 'Fixed', className: 'border-warning/40 bg-warning/10 text-warning' },
  notes: { label: 'Notes', className: 'border-border-strong bg-surface-2 text-text-secondary' },
  other: { label: 'Other', className: 'border-border-strong bg-surface-2 text-text-secondary' },
};

export interface ChangelogEntryCardProps {
  readonly entry: ChangelogEntry;
  /**
   * The release manifest of this version exists but none of its files is published yet: the notes
   * are shown with a "Release pending" badge and a pointer to the download page.
   */
  readonly pending?: boolean;
  /**
   * While pending: the published version of the same product the download page offers in the
   * meantime. Omitted when nothing of the product is published, so the note does not promise a
   * previous version that does not exist.
   */
  readonly availableVersion?: string | undefined;
}

/** One release in the changelog: product, version, date and Minecraft pills, then the sections. */
export function ChangelogEntryCard({
  entry,
  pending = false,
  availableVersion,
}: ChangelogEntryCardProps) {
  const date = formatDate(entry.date) ?? entry.date;
  return (
    <article
      id={entry.id}
      aria-labelledby={`${entry.id}-title`}
      className="surface-card scroll-mt-24 p-6 sm:p-8"
    >
      <header className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={productTones[entry.product]} dot>
            {productLabel(entry.product)}
          </Badge>
          <Tag className="font-semibold text-text-primary">v{entry.version}</Tag>
          <Tag>
            <CalendarDays className="mr-1.5 size-3 text-text-muted" aria-hidden="true" />
            <time dateTime={entry.date}>{date}</time>
          </Tag>
          {entry.minecraftVersion ? <Tag>Minecraft {entry.minecraftVersion}</Tag> : null}
          {pending ? (
            <Badge tone="warning" dot>
              Release pending
            </Badge>
          ) : null}
        </div>
        <h2
          id={`${entry.id}-title`}
          className="font-display text-2xl font-semibold tracking-display text-text-primary sm:text-3xl"
        >
          <a
            href={`#${entry.id}`}
            className="group inline-flex items-start gap-2 rounded-xs outline-none focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-border-focus"
          >
            {entry.title}
            <Hash
              className="mt-2 size-4 shrink-0 text-text-muted opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100"
              aria-hidden="true"
            />
          </a>
        </h2>
        {pending ? (
          <p className="text-sm text-text-muted">
            Not published yet: the files of this version appear on the{' '}
            <Link
              to="/download"
              className="text-accent-violet-hover underline decoration-accent-violet/40 underline-offset-4 hover:text-text-primary"
            >
              Download page
            </Link>{' '}
            once the release workflow has published them.
            {availableVersion
              ? ` Until then version ${availableVersion} stays available there.`
              : null}
          </p>
        ) : null}
      </header>
      {entry.intro ? (
        <MarkdownBlock
          source={entry.intro}
          className="mt-4 [&_p]:my-0 [&_p]:text-base [&_p]:leading-relaxed"
        />
      ) : null}
      {entry.sections.length > 0 ? (
        <div className="mt-6 flex flex-col gap-6 border-t border-border-subtle pt-6">
          {entry.sections.map((section) => {
            const style = sectionStyles[section.kind];
            return (
              <section
                key={`${entry.id}-${section.heading}`}
                aria-label={`${section.heading} in ${entry.title}`}
                className="grid gap-3 sm:grid-cols-[7.5rem_minmax(0,1fr)] sm:gap-6"
              >
                <div>
                  <span
                    className={`inline-flex h-6 items-center rounded-sm border px-2 text-[11px] font-semibold tracking-label uppercase ${style.className}`}
                  >
                    {section.kind === 'other' ? section.heading : style.label}
                  </span>
                  {section.itemCount > 0 ? (
                    <span className="mt-1.5 block text-xs text-text-muted">
                      {section.itemCount} {section.itemCount === 1 ? 'entry' : 'entries'}
                    </span>
                  ) : null}
                </div>
                <MarkdownBlock
                  source={section.body}
                  className="[&_ul]:my-0 [&_ul]:space-y-2 [&_p]:my-0"
                />
              </section>
            );
          })}
        </div>
      ) : null}
    </article>
  );
}
