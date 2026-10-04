import { ArrowLeft, ArrowRight } from 'lucide-react';
import { Link } from 'react-router';
import { cn } from '../../lib/cn';

export interface PrevNextLink {
  readonly to: string;
  readonly title: string;
  /** Small label above the title, e.g. the category or date. */
  readonly hint?: string;
}

export interface PrevNextProps {
  readonly previous?: PrevNextLink | undefined;
  readonly next?: PrevNextLink | undefined;
  readonly previousLabel?: string;
  readonly nextLabel?: string;
  readonly className?: string;
}

function Cell({
  link,
  label,
  direction,
}: {
  link: PrevNextLink | undefined;
  label: string;
  direction: 'previous' | 'next';
}) {
  if (!link) return <div aria-hidden="true" className="hidden sm:block" />;
  const Icon = direction === 'previous' ? ArrowLeft : ArrowRight;
  return (
    <Link
      to={link.to}
      rel={direction === 'previous' ? 'prev' : 'next'}
      className={cn(
        'surface-card surface-card-interactive group flex flex-col gap-2 p-5 outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus',
        direction === 'next' && 'text-right sm:items-end',
      )}
    >
      <span
        className={cn(
          'flex items-center gap-1.5 text-[11px] font-semibold tracking-label text-text-muted uppercase',
          direction === 'next' && 'flex-row-reverse',
        )}
      >
        <Icon
          className="size-3.5 transition-transform motion-safe:group-hover:translate-x-0.5 motion-safe:group-hover:[.text-right_&]:translate-x-0.5"
          aria-hidden="true"
        />
        {label}
      </span>
      <span className="font-display text-base font-semibold text-text-primary">{link.title}</span>
      {link.hint ? <span className="text-xs text-text-muted">{link.hint}</span> : null}
    </Link>
  );
}

/** Previous/next pagination for documentation pages and news posts. */
export function PrevNext({
  previous,
  next,
  previousLabel = 'Previous',
  nextLabel = 'Next',
  className,
}: PrevNextProps) {
  if (!previous && !next) return null;
  return (
    <nav aria-label="Pagination" className={cn('grid gap-4 sm:grid-cols-2', className)}>
      <Cell link={previous} label={previousLabel} direction="previous" />
      <Cell link={next} label={nextLabel} direction="next" />
    </nav>
  );
}
