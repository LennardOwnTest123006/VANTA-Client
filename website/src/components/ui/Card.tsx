import { type ElementType, type HTMLAttributes, type ReactNode } from 'react';
import { Link } from 'react-router';
import { cn } from '../../lib/cn';

export interface CardProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  /** Decorative icon shown in a tinted tile above the title. */
  readonly icon?: ReactNode;
  readonly eyebrow?: ReactNode;
  readonly title?: ReactNode;
  readonly children?: ReactNode;
  /** Enables hover lift and border glow (reduced-motion safe). Implied by `to`/`href`. */
  readonly interactive?: boolean;
  /** Makes the card title a link to an internal route. */
  readonly to?: string;
  readonly as?: ElementType;
  readonly padding?: 'sm' | 'md' | 'lg';
  readonly headingLevel?: 2 | 3 | 4;
}

const paddings = { sm: 'p-5', md: 'p-6 sm:p-7', lg: 'p-8 sm:p-10' } as const;

/**
 * Graphite surface with a 1px border. With `interactive` (or `to`) the card lifts and its border
 * glows on hover; the whole card is clickable through a stretched link while only the title is in
 * the tab order.
 */
export function Card({
  icon,
  eyebrow,
  title,
  children,
  interactive,
  to,
  as,
  padding = 'md',
  headingLevel = 3,
  className,
  ...rest
}: CardProps) {
  const Tag: ElementType = as ?? (to ? 'article' : 'div');
  const Heading: ElementType = `h${headingLevel}`;
  const isInteractive = interactive ?? to !== undefined;
  return (
    <Tag
      className={cn(
        'surface-card flex flex-col',
        isInteractive && 'surface-card-interactive',
        paddings[padding],
        className,
      )}
      {...rest}
    >
      {icon ? (
        <span
          aria-hidden="true"
          className="mb-5 inline-flex size-11 items-center justify-center rounded-lg border border-border-subtle bg-surface-2 text-accent-violet-hover shadow-[inset_0_1px_0_rgba(255,255,255,0.04)] [&>svg]:size-5"
        >
          {icon}
        </span>
      ) : null}
      {eyebrow ? <span className="eyebrow mb-2">{eyebrow}</span> : null}
      {title ? (
        <Heading className="font-display text-lg leading-snug font-semibold text-text-primary">
          {to ? (
            <Link
              to={to}
              className="rounded-xs outline-none after:absolute after:inset-0 after:rounded-xl after:content-[''] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-border-focus"
            >
              {title}
            </Link>
          ) : (
            title
          )}
        </Heading>
      ) : null}
      {children ? (
        <div className={cn('text-sm leading-relaxed text-text-secondary', title && 'mt-2')}>
          {children}
        </div>
      ) : null}
    </Tag>
  );
}
